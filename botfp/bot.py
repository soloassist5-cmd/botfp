"""Главный цикл бота."""

from __future__ import annotations

import logging
import signal
import sqlite3
import time
from types import FrameType

from . import db
from .commands import CommandRouter
from .config import Config
from .delivery import DeliveryService
from .listings import ListingManager
from .transport import Event, NewMessageEvent, NewOrderEvent, Transport

log = logging.getLogger(__name__)


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

    # ------------------------------------------------------------------

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
                self._poll_errors = 0
            except Exception:
                self._poll_errors += 1
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
