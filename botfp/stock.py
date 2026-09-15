"""Склад товаров и учёт выдач.

Главные инварианты, ради которых здесь всё построено на явных транзакциях:

1. Одна единица товара уходит ровно одному заказу.
2. Один заказ оплачивается ровно одной выдачей (FunPay может прислать
   событие о заказе повторно — например, после переподключения).
3. Если отправка сообщения упала с неизвестным исходом, товар остаётся
   закреплён за этим заказом. Возврат товара на склад в этой ситуации мог бы
   выдать один и тот же аккаунт второму покупателю, поэтому освобождение
   сделано только вручную (``release_order``).
"""

from __future__ import annotations

import enum
import hashlib
import sqlite3
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from .db import transaction, utcnow


def payload_hash(payload: str) -> str:
    return hashlib.sha256(payload.strip().encode("utf-8")).hexdigest()


@dataclass(frozen=True)
class StockItem:
    id: int
    lot_id: str
    payload: str
    status: str
    order_id: str | None = None

    @classmethod
    def from_row(cls, row: sqlite3.Row) -> StockItem:
        return cls(
            id=row["id"],
            lot_id=row["lot_id"],
            payload=row["payload"],
            status=row["status"],
            order_id=row["order_id"],
        )


@dataclass(frozen=True)
class Delivery:
    id: int
    order_id: str
    buyer: str
    chat_id: str | None
    lot_id: str
    stock_item_id: int | None
    status: str
    attempts: int
    error: str | None = None
    payload_snapshot: str | None = None

    @property
    def has_goods(self) -> bool:
        """За заказом уже закреплён товар — уникальный или безлимитный."""
        return self.stock_item_id is not None or self.payload_snapshot is not None

    @classmethod
    def from_row(cls, row: sqlite3.Row) -> Delivery:
        return cls(
            id=row["id"],
            order_id=row["order_id"],
            buyer=row["buyer"],
            chat_id=row["chat_id"],
            lot_id=row["lot_id"],
            stock_item_id=row["stock_item_id"],
            status=row["status"],
            attempts=row["attempts"],
            error=row["error"],
            payload_snapshot=row["payload_snapshot"],
        )


@dataclass(frozen=True)
class AddResult:
    added: int
    duplicates: int
    blank: int

    @property
    def total(self) -> int:
        return self.added + self.duplicates + self.blank


class ClaimStatus(str, enum.Enum):
    CLAIMED = "claimed"
    RESUME = "resume"
    ALREADY_DELIVERED = "already_delivered"
    OUT_OF_STOCK = "out_of_stock"


@dataclass(frozen=True)
class Claim:
    status: ClaimStatus
    delivery: Delivery
    item: StockItem | None = None

    @property
    def needs_send(self) -> bool:
        return self.status in (ClaimStatus.CLAIMED, ClaimStatus.RESUME)

    @property
    def payload(self) -> str | None:
        """Что уходит покупателю: строка склада или безлимитный товар."""
        if self.item is not None:
            return self.item.payload
        return self.delivery.payload_snapshot


# --------------------------------------------------------------------------
# Пополнение склада
# --------------------------------------------------------------------------


def add_items(conn: sqlite3.Connection, lot_id: str, payloads: Iterable[str]) -> AddResult:
    """Добавляет товары на склад, молча пропуская пустые строки и дубликаты."""
    added = duplicates = blank = 0
    now = utcnow()

    with transaction(conn):
        for raw in payloads:
            payload = raw.strip()
            if not payload:
                blank += 1
                continue
            cur = conn.execute(
                """
                INSERT OR IGNORE INTO stock_items
                    (lot_id, payload, payload_hash, status, added_at)
                VALUES (?, ?, ?, 'available', ?)
                """,
                (lot_id, payload, payload_hash(payload), now),
            )
            if cur.rowcount:
                added += 1
            else:
                duplicates += 1

    return AddResult(added=added, duplicates=duplicates, blank=blank)


def available_count(conn: sqlite3.Connection, lot_id: str) -> int:
    row = conn.execute(
        "SELECT COUNT(*) AS n FROM stock_items WHERE lot_id = ? AND status = 'available'",
        (lot_id,),
    ).fetchone()
    return int(row["n"])


def available_counts(conn: sqlite3.Connection) -> dict[str, int]:
    rows = conn.execute(
        """
        SELECT lot_id, COUNT(*) AS n
          FROM stock_items
         WHERE status = 'available'
         GROUP BY lot_id
        """
    ).fetchall()
    return {row["lot_id"]: int(row["n"]) for row in rows}


# --------------------------------------------------------------------------
# Выдача
# --------------------------------------------------------------------------


def claim_for_order(
    conn: sqlite3.Connection,
    *,
    order_id: str,
    buyer: str,
    lot_id: str,
    chat_id: str | None = None,
    unlimited_payload: str | None = None,
) -> Claim:
    """Закрепляет товар за заказом — атомарно и идемпотентно.

    ``unlimited_payload`` задан для безлимитных лотов (гайд, ссылка, файл):
    такой товар не списывается со склада, но его текст сохраняется в заказе,
    чтобы «!повтор» и разбор спорной выдачи показывали ровно то, что ушло.

    Повторный вызов с тем же ``order_id`` не выдаёт второй товар: он вернёт
    ``ALREADY_DELIVERED`` либо ``RESUME`` с уже закреплённым товаром.
    """
    order_id = str(order_id)
    unlimited = unlimited_payload is not None

    with transaction(conn):
        row = conn.execute(
            "SELECT * FROM deliveries WHERE order_id = ?", (order_id,)
        ).fetchone()

        if row is not None:
            delivery = Delivery.from_row(row)

            if delivery.status == "delivered":
                return Claim(ClaimStatus.ALREADY_DELIVERED, delivery, _item(conn, delivery))

            if delivery.has_goods:
                # Прошлая попытка не дошла до конца — отправляем тот же товар.
                conn.execute(
                    """
                    UPDATE deliveries
                       SET status = 'sending', attempts = attempts + 1, updated_at = ?
                     WHERE id = ?
                    """,
                    (utcnow(), delivery.id),
                )
                refreshed = _delivery_by_id(conn, delivery.id)
                return Claim(ClaimStatus.RESUME, refreshed, _item(conn, refreshed))

            # Была нехватка товара — пробуем снова, склад мог пополниться.
            item = None if unlimited else _take_available(conn, lot_id, order_id)
            if item is None and not unlimited:
                conn.execute(
                    "UPDATE deliveries SET attempts = attempts + 1, updated_at = ? WHERE id = ?",
                    (utcnow(), delivery.id),
                )
                return Claim(ClaimStatus.OUT_OF_STOCK, _delivery_by_id(conn, delivery.id))

            conn.execute(
                """
                UPDATE deliveries
                   SET status = 'sending', stock_item_id = ?, payload_snapshot = ?,
                       attempts = attempts + 1, chat_id = COALESCE(?, chat_id),
                       error = NULL, updated_at = ?
                 WHERE id = ?
                """,
                (
                    item.id if item else None,
                    unlimited_payload,
                    chat_id,
                    utcnow(),
                    delivery.id,
                ),
            )
            return Claim(ClaimStatus.CLAIMED, _delivery_by_id(conn, delivery.id), item)

        # Заказ видим впервые.
        item = None if unlimited else _take_available(conn, lot_id, order_id)
        served = unlimited or item is not None
        now = utcnow()
        cur = conn.execute(
            """
            INSERT INTO deliveries
                (order_id, buyer, chat_id, lot_id, stock_item_id, payload_snapshot,
                 status, attempts, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
            """,
            (
                order_id,
                buyer,
                chat_id,
                lot_id,
                item.id if item else None,
                unlimited_payload,
                "sending" if served else "out_of_stock",
                now,
                now,
            ),
        )
        delivery = _delivery_by_id(conn, int(cur.lastrowid))

    return Claim(
        ClaimStatus.CLAIMED if served else ClaimStatus.OUT_OF_STOCK, delivery, item
    )


def _take_available(
    conn: sqlite3.Connection, lot_id: str, order_id: str
) -> StockItem | None:
    """Забирает со склада самую старую доступную единицу (FIFO)."""
    row = conn.execute(
        """
        SELECT * FROM stock_items
         WHERE lot_id = ? AND status = 'available'
         ORDER BY id
         LIMIT 1
        """,
        (lot_id,),
    ).fetchone()
    if row is None:
        return None

    cur = conn.execute(
        """
        UPDATE stock_items
           SET status = 'reserved', order_id = ?, reserved_at = ?
         WHERE id = ? AND status = 'available'
        """,
        (order_id, utcnow(), row["id"]),
    )
    if cur.rowcount == 0:  # pragma: no cover - защита от гонки внутри транзакции
        return None

    item = StockItem.from_row(row)
    return StockItem(item.id, item.lot_id, item.payload, "reserved", order_id)


def mark_delivered(conn: sqlite3.Connection, delivery_id: int) -> None:
    """Фиксирует успешную отправку: товар списан, заказ закрыт."""
    now = utcnow()
    with transaction(conn):
        conn.execute(
            """
            UPDATE stock_items
               SET status = 'issued', issued_at = ?
             WHERE id = (SELECT stock_item_id FROM deliveries WHERE id = ?)
            """,
            (now, delivery_id),
        )
        conn.execute(
            """
            UPDATE deliveries
               SET status = 'delivered', error = NULL, updated_at = ?
             WHERE id = ?
            """,
            (now, delivery_id),
        )


def mark_failed(conn: sqlite3.Connection, delivery_id: int, error: str) -> None:
    """Фиксирует неудачу отправки.

    Товар намеренно остаётся зарезервированным: отправка могла дойти, и
    возврат на склад рискует выдать тот же товар второму покупателю.
    """
    with transaction(conn):
        conn.execute(
            "UPDATE deliveries SET status = 'failed', error = ?, updated_at = ? WHERE id = ?",
            (error[:500], utcnow(), delivery_id),
        )


def release_order(conn: sqlite3.Connection, order_id: str) -> bool:
    """Возвращает товар заказа на склад (отмена или возврат средств).

    Вызывается только вручную продавцом.
    """
    with transaction(conn):
        row = conn.execute(
            "SELECT * FROM deliveries WHERE order_id = ?", (str(order_id),)
        ).fetchone()
        if row is None or row["stock_item_id"] is None:
            return False

        conn.execute(
            """
            UPDATE stock_items
               SET status = 'available', order_id = NULL, reserved_at = NULL, issued_at = NULL
             WHERE id = ?
            """,
            (row["stock_item_id"],),
        )
        conn.execute(
            """
            UPDATE deliveries
               SET status = 'failed', stock_item_id = NULL,
                   error = 'released by seller', updated_at = ?
             WHERE id = ?
            """,
            (utcnow(), row["id"]),
        )
    return True


# --------------------------------------------------------------------------
# Чтение
# --------------------------------------------------------------------------


def _delivery_by_id(conn: sqlite3.Connection, delivery_id: int) -> Delivery:
    row = conn.execute("SELECT * FROM deliveries WHERE id = ?", (delivery_id,)).fetchone()
    return Delivery.from_row(row)


def _item(conn: sqlite3.Connection, delivery: Delivery) -> StockItem | None:
    if delivery.stock_item_id is None:
        return None
    row = conn.execute(
        "SELECT * FROM stock_items WHERE id = ?", (delivery.stock_item_id,)
    ).fetchone()
    return StockItem.from_row(row) if row else None


def delivery_by_order(conn: sqlite3.Connection, order_id: str) -> Delivery | None:
    row = conn.execute(
        "SELECT * FROM deliveries WHERE order_id = ?", (str(order_id),)
    ).fetchone()
    return Delivery.from_row(row) if row else None


def last_delivery_for_buyer(
    conn: sqlite3.Connection, buyer: str
) -> tuple[Delivery, str] | None:
    """Последняя успешная выдача покупателю и её содержимое — для «!повтор»."""
    row = conn.execute(
        """
        SELECT d.*
          FROM deliveries d
         WHERE d.buyer = ? COLLATE NOCASE
           AND d.status = 'delivered'
           AND (d.stock_item_id IS NOT NULL OR d.payload_snapshot IS NOT NULL)
         ORDER BY d.id DESC
         LIMIT 1
        """,
        (buyer,),
    ).fetchone()
    if row is None:
        return None

    delivery = Delivery.from_row(row)
    item = _item(conn, delivery)
    payload = item.payload if item else delivery.payload_snapshot
    return (delivery, payload) if payload else None


def pending_deliveries(conn: sqlite3.Connection) -> Sequence[Delivery]:
    """Заказы, требующие внимания продавца."""
    rows = conn.execute(
        """
        SELECT * FROM deliveries
         WHERE status IN ('sending', 'failed', 'out_of_stock')
         ORDER BY id
        """
    ).fetchall()
    return [Delivery.from_row(row) for row in rows]


def delivery_stats(conn: sqlite3.Connection) -> dict[str, int]:
    rows = conn.execute(
        "SELECT status, COUNT(*) AS n FROM deliveries GROUP BY status"
    ).fetchall()
    stats = {"delivered": 0, "sending": 0, "failed": 0, "out_of_stock": 0}
    for row in rows:
        stats[row["status"]] = int(row["n"])
    return stats
