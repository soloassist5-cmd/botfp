"""Главный цикл бота."""

from __future__ import annotations

import logging
import signal
import sqlite3
import time
from types import FrameType

from . import db, templates
from .commands import CommandRouter
from .config import Config
from .delivery import DeliveryService
from .listings import ListingManager
from .transport import (
    Event,
    NewMessageEvent,
    NewOrderEvent,
    Transport,
    looks_like_auth_failure,
)

log = logging.getLogger(__name__)

# Ключ в runtime_state: бот пишет, панель Telegram читает.
STATE_CONNECTION = "funpay_connection"


class Bot:
    """Связывает транспорт, склад и команды."""

    def __init__(
        self,
        conn: sqlite3.Connection,
        config: Config,
        transport: Transport,
        self_username: str = "",
        listings: ListingManager | None = None,
        notifiers=(),
    ):
        self.conn = conn
        self.config = config
        self.transport = transport
        self.listings = listings
        self.delivery = DeliveryService(conn, config, transport, notifiers=notifiers)
        self.router = CommandRouter(conn, config, transport, self.delivery, self_username)
        self._running = False
        self._poll_errors = 0
        self._connection_lost = False
        self._lost_since = 0.0

    # ------------------------------------------------------------------

    @property
    def connection_lost(self) -> bool:
        """Потеряна ли связь с FunPay. Читает health-эндпоинт."""
        return self._connection_lost

    def handle_event(self, event: Event) -> None:
        """Обрабатывает одно событие.

        Заказы не фильтруются по ``seen_events``: их идемпотентность
        обеспечивает сама база, зато повторное событие служит бесплатной
        попыткой довыдать зависший заказ.
        """
        if isinstance(event, NewOrderEvent):
            self.delivery.handle_order(event)
            self._refresh_listings()
        elif isinstance(event, NewMessageEvent):
            if db.mark_event_seen(self.conn, event.key):
                self.router.handle_message(event)
        else:  # pragma: no cover - на случай новых типов событий
            log.debug("Неизвестное событие: %r", event)

    def run_once(self) -> int:
        """Один проход опроса. Возвращает число обработанных событий."""
        events = list(self.transport.poll())
        for event in events:
            try:
                self.handle_event(event)
            except Exception:
                # Один плохой заказ не должен останавливать выдачу остальных.
                log.exception("Ошибка при обработке события %r", event)
        return len(events)

    def run_forever(self) -> None:
        """Крутит опрос до SIGINT/SIGTERM."""
        self._running = True
        self._install_signal_handlers()

        log.info(
            "Бот запущен. Лотов: %d, интервал опроса %.1f с.",
            len(self.config.lots),
            self.config.poll_interval,
        )
        self._report_pending_on_start()
        self._sync_listings_on_start()

        ticks = 0
        while self._running:
            try:
                self.run_once()
                self._on_poll_ok()
            except Exception as exc:
                self._on_poll_error(exc)
                delay = min(self.config.poll_interval * 2**self._poll_errors, 300)
                log.exception("Опрос упал (попытка %d), пауза %.0f с.", self._poll_errors, delay)
                self._sleep(delay)
                continue

            ticks += 1
            if ticks % 500 == 0:
                db.prune_events(self.conn)

            self._sleep(self.config.poll_interval)

        log.info("Бот остановлен.")

    def stop(self) -> None:
        self._running = False

    # ------------------------------------------------------------------

    # ------------------------------------------------------------------
    # Состояние связи с FunPay
    # ------------------------------------------------------------------

    def _on_poll_error(self, exc: BaseException) -> None:
        """Первый сбой подряд — повод написать продавцу, дальше молчим.

        Без этого бот на хостинге отваливался бы беззвучно: заказы оплачены,
        выдачи нет, а продавец узнаёт об этом от покупателей.
        """
        self._poll_errors += 1
        if self._connection_lost:
            return  # уже сообщили, не спамим на каждой попытке

        self._connection_lost = True
        self._lost_since = time.monotonic()
        self._record_connection("lost", str(exc))

        template = (
            templates.ADMIN_KEY_EXPIRED
            if looks_like_auth_failure(exc)
            else templates.ADMIN_CONNECTION_LOST
        )
        self.delivery.notify_admins(template.format(reason=exc))

    def _on_poll_ok(self) -> None:
        self._poll_errors = 0
        if not self._connection_lost:
            return

        downtime = _humanize(time.monotonic() - self._lost_since)
        self._connection_lost = False
        self._record_connection("ok", "")
        log.info("Связь с FunPay восстановлена, перерыв %s", downtime)

        # После обрыва добираем то, что не доехало: заказы могли оплатить,
        # пока связи не было.
        ok, total = self.delivery.retry_all_pending()
        pending = (
            templates.ADMIN_RECOVERY_RETRIED.format(ok=ok, total=total)
            if total
            else templates.ADMIN_RECOVERY_CLEAN
        )
        self.delivery.notify_admins(
            templates.ADMIN_CONNECTION_RESTORED.format(downtime=downtime, pending=pending)
        )

    def _record_connection(self, state: str, reason: str) -> None:
        try:
            with db.transaction(self.conn):
                db.set_state(self.conn, STATE_CONNECTION, state)
                db.set_state(self.conn, f"{STATE_CONNECTION}_reason", reason[:300])
        except Exception:
            log.exception("Не смог записать состояние связи")

    def _sync_listings_on_start(self) -> None:
        """Приводит витрину в соответствие с конфигом при активации бота."""
        if self.listings is None or not self.config.sync_listings_on_start:
            return

        log.info("Синхронизирую объявления...")
        result = self.listings.sync()
        log.info("Объявления: %s", result.summary())
        for lot_id, error in result.failed:
            log.error("Лот %s: %s", lot_id, error)
        if not result.ok:
            self.delivery.notify_admins(
                f"⚠️ Синхронизация объявлений прошла с ошибками: {result.summary()}. "
                f"Подробности в логе бота."
            )

    def _refresh_listings(self) -> None:
        """Прячет с витрины то, чего не осталось на складе.

        Сетевой вызов происходит только когда состояние действительно
        изменилось: сам метод сначала сверяется с локальной базой.
        """
        if self.listings is None:
            return
        try:
            self.listings.refresh_availability()
        except Exception:
            log.exception("Не удалось обновить состояние объявлений")

    def _report_pending_on_start(self) -> None:
        from . import stock

        pending = stock.pending_deliveries(self.conn)
        if pending:
            log.warning(
                "Незавершённых выдач: %d. Посмотреть: botfp pending, повторить: botfp retry --all",
                len(pending),
            )

    def _install_signal_handlers(self) -> None:
        def handler(signum: int, frame: FrameType | None) -> None:
            log.info("Получен сигнал %s, завершаюсь...", signal.Signals(signum).name)
            self.stop()

        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                signal.signal(sig, handler)
            except ValueError:  # pragma: no cover - не главный поток
                pass

    def _sleep(self, seconds: float) -> None:
        """Сон, прерываемый остановкой бота."""
        deadline = time.monotonic() + seconds
        while self._running and time.monotonic() < deadline:
            time.sleep(min(0.5, deadline - time.monotonic()))


def _humanize(seconds: float) -> str:
    """Длительность по-русски: «3 мин.», «2 ч. 15 мин.»."""
    minutes = int(seconds // 60)
    if minutes < 1:
        return "меньше минуты"
    if minutes < 60:
        return f"{minutes} мин."
    hours, rest = divmod(minutes, 60)
    return f"{hours} ч. {rest} мин." if rest else f"{hours} ч."
