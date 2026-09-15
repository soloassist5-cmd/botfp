"""Выдача заказа: событие -> склад -> сообщение покупателю."""

from __future__ import annotations

import logging
import sqlite3
from collections.abc import Sequence
from typing import Protocol

from . import stock, templates
from .config import Config, LotConfig
from .stock import ClaimStatus, Delivery
from .transport import NewOrderEvent, Transport, TransportError

log = logging.getLogger(__name__)


class Notifier(Protocol):
    """Дополнительный канал уведомлений продавцу."""

    def notify(self, text: str) -> None: ...


class DeliveryService:
    """Обрабатывает оплаченные заказы и повторные попытки выдачи."""

    def __init__(
        self,
        conn: sqlite3.Connection,
        config: Config,
        transport: Transport,
        notifiers: Sequence[Notifier] = (),
    ):
        self.conn = conn
        self.config = config
        self.transport = transport
        # Дополнительные каналы уведомлений (Telegram) — в дополнение к личке
        # на FunPay, а не вместо неё.
        self.notifiers = tuple(notifiers)

    # ------------------------------------------------------------------
    # Публичное API
    # ------------------------------------------------------------------

    def handle_order(self, event: NewOrderEvent) -> ClaimStatus:
        """Выдаёт товар по заказу. Повторный вызов с тем же заказом безопасен."""
        lot = self.config.lot(event.lot_id)
        if lot is None:
            log.warning("Заказ #%s по неизвестному лоту %s", event.order_id, event.lot_id)
            self.notify_admins(
                f"⚠️ Заказ #{event.order_id} от {event.buyer} по лоту {event.lot_id}, "
                f"которого нет в конфиге. Выдайте вручную."
            )
            return ClaimStatus.OUT_OF_STOCK

        claim = stock.claim_for_order(
            self.conn,
            order_id=event.order_id,
            buyer=event.buyer,
            lot_id=event.lot_id,
            chat_id=event.chat_id,
            unlimited_payload=lot.payload if lot.is_unlimited else None,
        )

        if claim.status is ClaimStatus.ALREADY_DELIVERED:
            log.info("Заказ #%s уже выдан, пропускаю", event.order_id)
            return claim.status

        if claim.status is ClaimStatus.OUT_OF_STOCK:
            log.warning("Нет товара для лота %s (заказ #%s)", lot.lot_id, event.order_id)
            self._safe_send(claim.delivery, templates.OUT_OF_STOCK_BUYER)
            self.notify_admins(
                templates.ADMIN_OUT_OF_STOCK.format(
                    lot_title=lot.display, order_id=event.order_id, buyer=event.buyer
                )
            )
            return claim.status

        assert claim.payload is not None  # гарантировано CLAIMED/RESUME
        self._deliver(claim.delivery, claim.payload, lot)
        return claim.status

    def retry(self, order_id: str) -> bool:
        """Повторяет выдачу зависшего заказа. Товар берётся тот же самый."""
        delivery = stock.delivery_by_order(self.conn, order_id)
        if delivery is None:
            log.error("Заказ #%s не найден", order_id)
            return False
        if delivery.status == "delivered":
            log.info("Заказ #%s уже выдан", order_id)
            return True

        lot = self.config.lot(delivery.lot_id)
        if lot is None:
            log.error("Лот %s отсутствует в конфиге", delivery.lot_id)
            return False

        event = NewOrderEvent(
            order_id=delivery.order_id,
            buyer=delivery.buyer,
            lot_id=delivery.lot_id,
            chat_id=delivery.chat_id,
        )
        status = self.handle_order(event)
        return status in (ClaimStatus.CLAIMED, ClaimStatus.RESUME, ClaimStatus.ALREADY_DELIVERED)

    def retry_all_pending(self) -> tuple[int, int]:
        """Повторяет все незавершённые выдачи. Возвращает (успешно, всего)."""
        pending = stock.pending_deliveries(self.conn)
        ok = sum(1 for d in pending if self.retry(d.order_id))
        return ok, len(pending)

    def notify_admins(self, text: str) -> None:
        for admin in self.config.admins:
            try:
                self.transport.send_to_user(admin, text)
            except TransportError as exc:
                log.error("Не смог уведомить админа %s: %s", admin, exc)

        for notifier in self.notifiers:
            try:
                notifier.notify(text)
            except Exception:
                # Падение стороннего канала не должно ронять выдачу.
                log.exception("Уведомление через %s не ушло", type(notifier).__name__)

    # ------------------------------------------------------------------
    # Внутреннее
    # ------------------------------------------------------------------

    def _deliver(self, delivery: Delivery, payload: str, lot: LotConfig) -> None:
        text = self.render_delivery(lot, payload)

        try:
            self._send_to_buyer(delivery, text)
        except TransportError as exc:
            # Товар остаётся закреплён за заказом: сообщение могло уйти.
            stock.mark_failed(self.conn, delivery.id, str(exc))
            log.error("Не удалось выдать заказ #%s: %s", delivery.order_id, exc)
            self.notify_admins(
                templates.ADMIN_DELIVERY_FAILED.format(
                    order_id=delivery.order_id,
                    lot_title=lot.display,
                    buyer=delivery.buyer,
                    error=exc,
                )
            )
            return

        stock.mark_delivered(self.conn, delivery.id)
        log.info("Заказ #%s выдан покупателю %s", delivery.order_id, delivery.buyer)
        self._warn_if_low(lot)

    def render_delivery(self, lot: LotConfig, payload: str) -> str:
        text = templates.DELIVERY.format(lot_title=lot.title, payload=payload)
        if lot.instructions:
            text += templates.DELIVERY_INSTRUCTIONS.format(instructions=lot.instructions)
        if self.config.ask_for_review:
            text += templates.REVIEW_REQUEST
        return text

    def _send_to_buyer(self, delivery: Delivery, text: str) -> None:
        if delivery.chat_id:
            self.transport.send_message(delivery.chat_id, text)
        else:
            self.transport.send_to_user(delivery.buyer, text)

    def _safe_send(self, delivery: Delivery, text: str) -> None:
        """Отправка, ошибка которой не должна ронять обработку заказа."""
        try:
            self._send_to_buyer(delivery, text)
        except TransportError as exc:
            log.error("Не смог написать покупателю %s: %s", delivery.buyer, exc)

    def _warn_if_low(self, lot: LotConfig) -> None:
        if lot.is_unlimited:
            return  # безлимитный товар не кончается
        left = stock.available_count(self.conn, lot.lot_id)
        if left <= self.config.low_stock_threshold:
            self.notify_admins(
                templates.ADMIN_LOW_STOCK.format(
                    lot_title=lot.display, count=left, lot_id=lot.lot_id
                )
            )
