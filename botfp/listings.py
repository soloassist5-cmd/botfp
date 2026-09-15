"""Синхронизация объявлений на FunPay с конфигом.

Главный риск здесь — размножить лоты на витрине. Синхронизация идёт по двум
рубежам защиты:

1. Таблица ``lot_links`` помнит, какому лоту из конфига какое объявление
   соответствует. Повторный запуск обновляет его, а не создаёт новое.
2. Если связи нет (первый запуск, потеря базы, лот заведён руками), бот
   сначала ищет среди уже существующих объявлений подходящее по названию и
   подкатегории и **усыновляет** его. Создание — только если не нашлось.
"""

from __future__ import annotations

import logging
import sqlite3
import time
from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import Protocol

from . import stock
from .config import Config, LotConfig
from .db import transaction, utcnow
from .transport import TransportError

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class ListingSpec:
    """Объявление, каким мы хотим его видеть на FunPay."""

    lot_id: str
    node_id: int
    title: str
    description: str
    price: float
    amount: int | None = None
    active: bool = True

    @classmethod
    def from_lot(cls, lot: LotConfig, *, active: bool = True) -> ListingSpec:
        listing = lot.listing
        assert listing is not None
        return cls(
            lot_id=lot.lot_id,
            node_id=listing.node_id,
            title=listing.short_description or lot.title,
            description=listing.description,
            price=listing.price,
            amount=listing.amount,
            active=active,
        )


@dataclass(frozen=True)
class RemoteLot:
    """Объявление, которое уже есть на FunPay."""

    funpay_lot_id: str
    node_id: int
    title: str
    active: bool = True


class LotsAPI(Protocol):
    """Операции с объявлениями. Реализуется транспортом FunPay."""

    def list_my_lots(self) -> Sequence[RemoteLot]: ...

    def create_lot(self, spec: ListingSpec) -> str: ...

    def update_lot(self, funpay_lot_id: str, spec: ListingSpec) -> None: ...

    def set_lot_active(self, funpay_lot_id: str, active: bool) -> None: ...


@dataclass
class SyncResult:
    created: list[str] = field(default_factory=list)
    adopted: list[str] = field(default_factory=list)
    updated: list[str] = field(default_factory=list)
    failed: list[tuple[str, str]] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.failed

    def summary(self) -> str:
        parts = [
            f"создано {len(self.created)}",
            f"привязано к существующим {len(self.adopted)}",
            f"обновлено {len(self.updated)}",
        ]
        if self.failed:
            parts.append(f"с ошибкой {len(self.failed)}")
        return ", ".join(parts)


# --------------------------------------------------------------------------
# Хранение связи «лот в конфиге -> объявление на FunPay»
# --------------------------------------------------------------------------


@dataclass(frozen=True)
class LotLink:
    lot_id: str
    funpay_lot_id: str | None
    active: bool
    last_error: str | None = None


def get_link(conn: sqlite3.Connection, lot_id: str) -> LotLink | None:
    row = conn.execute("SELECT * FROM lot_links WHERE lot_id = ?", (lot_id,)).fetchone()
    if row is None:
        return None
    return LotLink(
        lot_id=row["lot_id"],
        funpay_lot_id=row["funpay_lot_id"],
        active=bool(row["active"]),
        last_error=row["last_error"],
    )


def all_links(conn: sqlite3.Connection) -> list[LotLink]:
    rows = conn.execute("SELECT * FROM lot_links ORDER BY lot_id").fetchall()
    return [
        LotLink(
            lot_id=r["lot_id"],
            funpay_lot_id=r["funpay_lot_id"],
            active=bool(r["active"]),
            last_error=r["last_error"],
        )
        for r in rows
    ]


def save_link(
    conn: sqlite3.Connection,
    lot_id: str,
    funpay_lot_id: str | None,
    *,
    active: bool = True,
    error: str | None = None,
) -> None:
    with transaction(conn):
        conn.execute(
            """
            INSERT INTO lot_links (lot_id, funpay_lot_id, active, synced_at, last_error)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (lot_id) DO UPDATE SET
                funpay_lot_id = excluded.funpay_lot_id,
                active        = excluded.active,
                synced_at     = excluded.synced_at,
                last_error    = excluded.last_error
            """,
            (lot_id, funpay_lot_id, int(active), utcnow(), error),
        )


# --------------------------------------------------------------------------


class ListingManager:
    """Приводит витрину FunPay в соответствие с конфигом."""

    def __init__(self, conn: sqlite3.Connection, config: Config, api: LotsAPI):
        self.conn = conn
        self.config = config
        self.api = api

    def sync(self, *, dry_run: bool = False) -> SyncResult:
        """Создаёт недостающие объявления и обновляет существующие."""
        result = SyncResult()
        lots = self.config.listable_lots
        if not lots:
            log.info("Ни у одного лота нет секции listing — синхронизировать нечего.")
            return result

        try:
            remote = list(self.api.list_my_lots())
        except TransportError as exc:
            log.error("Не удалось получить список объявлений: %s", exc)
            result.failed.append(("*", str(exc)))
            return result

        by_key = {(r.node_id, r.title.casefold().strip()): r for r in remote}
        known_ids = {r.funpay_lot_id for r in remote}

        for index, lot in enumerate(lots):
            if index:
                self._pause(dry_run)
            self._sync_one(lot, by_key, known_ids, result, dry_run=dry_run)

        return result

    def _sync_one(
        self,
        lot: LotConfig,
        by_key: dict[tuple[int, str], RemoteLot],
        known_ids: set[str],
        result: SyncResult,
        *,
        dry_run: bool,
    ) -> None:
        spec = ListingSpec.from_lot(lot, active=self._should_be_active(lot))
        link = get_link(self.conn, lot.lot_id)

        # 1. Связь известна и объявление на месте — обновляем его.
        if link and link.funpay_lot_id and link.funpay_lot_id in known_ids:
            self._update(lot, spec, link.funpay_lot_id, result, dry_run=dry_run)
            return

        # 2. Связи нет, но похожее объявление уже висит — усыновляем,
        #    иначе каждый запуск на чистой базе плодил бы дубликаты.
        existing = by_key.get((spec.node_id, spec.title.casefold().strip()))
        if existing is not None:
            log.info(
                "Лот %s уже есть на FunPay (id %s) — привязываю, не создаю новый.",
                lot.lot_id,
                existing.funpay_lot_id,
            )
            if not dry_run:
                save_link(self.conn, lot.lot_id, existing.funpay_lot_id, active=existing.active)
            result.adopted.append(lot.lot_id)
            return

        # 3. Ничего похожего — создаём.
        self._create(lot, spec, result, dry_run=dry_run)

    def _update(
        self,
        lot: LotConfig,
        spec: ListingSpec,
        funpay_lot_id: str,
        result: SyncResult,
        *,
        dry_run: bool,
    ) -> None:
        if dry_run:
            log.info("[сухой прогон] обновил бы объявление «%s» (id %s)", spec.title, funpay_lot_id)
            result.updated.append(lot.lot_id)
            return

        try:
            self.api.update_lot(funpay_lot_id, spec)
        except TransportError as exc:
            log.error("Не обновил объявление для %s: %s", lot.lot_id, exc)
            result.failed.append((lot.lot_id, str(exc)))
            return

        save_link(self.conn, lot.lot_id, funpay_lot_id, active=spec.active)
        result.updated.append(lot.lot_id)

    def _create(
        self, lot: LotConfig, spec: ListingSpec, result: SyncResult, *, dry_run: bool
    ) -> None:
        if dry_run:
            log.info("[сухой прогон] создал бы объявление «%s» за %.2f ₽", spec.title, spec.price)
            result.created.append(lot.lot_id)
            return

        try:
            funpay_lot_id = self.api.create_lot(spec)
        except TransportError as exc:
            log.error("Не создал объявление для %s: %s", lot.lot_id, exc)
            save_link(self.conn, lot.lot_id, None, active=False, error=str(exc))
            result.failed.append((lot.lot_id, str(exc)))
            return

        save_link(self.conn, lot.lot_id, funpay_lot_id, active=spec.active)
        log.info("Создано объявление «%s» (id %s)", spec.title, funpay_lot_id)
        result.created.append(lot.lot_id)

    # ------------------------------------------------------------------
    # Снятие с публикации при нулевом остатке
    # ------------------------------------------------------------------

    def refresh_availability(self) -> list[str]:
        """Прячет объявления с пустым складом и возвращает пополненные.

        Так покупатель не оформляет заказ на то, чего нет: извинение и возврат
        хуже, чем отсутствие лота на витрине несколько минут.
        """
        changed: list[str] = []

        for lot in self.config.listable_lots:
            if lot.is_unlimited or not lot.listing.auto_deactivate:
                continue

            link = get_link(self.conn, lot.lot_id)
            if link is None or not link.funpay_lot_id:
                continue

            should_be_active = stock.available_count(self.conn, lot.lot_id) > 0
            if should_be_active == link.active:
                continue

            try:
                self.api.set_lot_active(link.funpay_lot_id, should_be_active)
            except TransportError as exc:
                log.error("Не смог переключить объявление %s: %s", lot.lot_id, exc)
                continue

            save_link(self.conn, lot.lot_id, link.funpay_lot_id, active=should_be_active)
            state = "вернул на витрину" if should_be_active else "снял с витрины"
            log.info("%s: %s", lot.display, state)
            changed.append(lot.lot_id)
            self._pause(False)

        return changed

    # ------------------------------------------------------------------

    def _should_be_active(self, lot: LotConfig) -> bool:
        if lot.is_unlimited or not lot.listing.auto_deactivate:
            return True
        return stock.available_count(self.conn, lot.lot_id) > 0

    def _pause(self, dry_run: bool) -> None:
        """Пауза между изменениями лотов — пачка правок без пауз выглядит как флуд."""
        if not dry_run:
            time.sleep(self.config.listing_delay)
