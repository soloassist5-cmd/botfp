"""Адаптер к FunPay поверх библиотеки FunPayAPI.

Официального API у FunPay нет, поэтому библиотека работает через сессию
продавца (cookie ``golden_key``). Отсюда два следствия:

* ``golden_key`` — это полный доступ к аккаунту. Держите его в переменной
  окружения, а не в репозитории.
* Слушатель живёт в отдельном потоке: ``Runner.listen()`` блокирующий, а
  остальному боту нужен неблокирующий :meth:`poll`.

Импорты FunPayAPI намеренно ленивые — без установленной библиотеки пакет
остаётся рабочим (консольный режим, тесты, работа со складом).
"""

from __future__ import annotations

import logging
import queue
import threading
from collections.abc import Iterable

from .config import Config
from .transport import Event, NewMessageEvent, NewOrderEvent, TransportError

log = logging.getLogger(__name__)


class FunPayTransport:
    """Транспорт поверх боевого аккаунта FunPay."""

    def __init__(self, config: Config):
        self.config = config
        self._queue: queue.Queue[Event] = queue.Queue()
        self._account = None
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()
        self._listener_error: BaseException | None = None

    # ------------------------------------------------------------------
    # Подключение
    # ------------------------------------------------------------------

    def connect(self) -> str:
        """Авторизуется на FunPay. Возвращает ник продавца."""
        try:
            from FunPayAPI import Account
        except ImportError as exc:  # pragma: no cover - зависит от окружения
            raise TransportError(
                "Не установлена библиотека FunPayAPI. "
                "Поставьте её: pip install -r requirements.txt"
            ) from exc

        try:
            self._account = Account(
                self.config.golden_key, user_agent=self.config.user_agent
            ).get()
        except Exception as exc:  # pragma: no cover - сеть
            raise TransportError(f"Не удалось войти на FunPay: {exc}") from exc

        log.info("Вошёл на FunPay как %s (id %s)", self._account.username, self._account.id)
        return str(self._account.username)

    def start(self) -> None:
        """Запускает фоновый слушатель событий."""
        if self._account is None:
            raise TransportError("Сначала вызовите connect().")
        if self._thread is not None:
            return

        self._thread = threading.Thread(
            target=self._listen, name="funpay-listener", daemon=True
        )
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    # ------------------------------------------------------------------
    # Transport
    # ------------------------------------------------------------------

    def poll(self) -> Iterable[Event]:
        if self._listener_error is not None:
            error, self._listener_error = self._listener_error, None
            raise TransportError(f"Слушатель FunPay упал: {error}")

        events: list[Event] = []
        while True:
            try:
                events.append(self._queue.get_nowait())
            except queue.Empty:
                return events

    def send_message(self, chat_id: str, text: str) -> None:
        if self._account is None:
            raise TransportError("Нет подключения к FunPay.")
        try:
            self._account.send_message(int(chat_id), text)
        except Exception as exc:
            raise TransportError(f"Не удалось отправить сообщение в чат {chat_id}: {exc}") from exc

    def send_to_user(self, username: str, text: str) -> None:
        if self._account is None:
            raise TransportError("Нет подключения к FunPay.")
        try:
            chat = self._account.get_chat_by_name(username, make_request=True)
            if chat is None:
                raise TransportError(f"Пользователь {username} не найден на FunPay.")
            self._account.send_message(chat.id, text)
        except TransportError:
            raise
        except Exception as exc:
            raise TransportError(f"Не удалось написать пользователю {username}: {exc}") from exc

    # ------------------------------------------------------------------
    # Фоновый слушатель
    # ------------------------------------------------------------------

    def _listen(self) -> None:
        try:
            from FunPayAPI import Runner
            from FunPayAPI.updater.events import EventTypes

            runner = Runner(self._account)
            for raw in runner.listen(requests_delay=self.config.request_delay):
                if self._stop.is_set():
                    return
                event = self._convert(raw, EventTypes)
                if event is not None:
                    self._queue.put(event)
        except BaseException as exc:  # noqa: BLE001 - поднимаем в основной поток
            log.exception("Слушатель FunPay остановлен из-за ошибки")
            self._listener_error = exc

    def _convert(self, raw: object, event_types: object) -> Event | None:
        """Переводит событие FunPayAPI во внутреннее представление."""
        raw_type = getattr(raw, "type", None)

        if raw_type is getattr(event_types, "NEW_MESSAGE", None):
            message = raw.message
            return NewMessageEvent(
                chat_id=str(message.chat_id),
                author=str(message.author or ""),
                text=str(message.text or ""),
                message_id=str(message.id),
            )

        if raw_type is getattr(event_types, "NEW_ORDER", None):
            order = raw.order
            description = str(getattr(order, "description", "") or "")
            lot = self.config.lot_for_description(description)
            if lot is None:
                log.warning(
                    "Заказ #%s («%s») не сопоставлен ни с одним лотом — "
                    "проверьте поле match в конфиге.",
                    order.id,
                    description,
                )
                return None
            return NewOrderEvent(
                order_id=str(order.id),
                buyer=str(getattr(order, "buyer_username", "") or ""),
                lot_id=lot.lot_id,
                chat_id=self._chat_id_for(getattr(order, "buyer_username", "")),
                title=description,
            )

        return None

    def _chat_id_for(self, username: str) -> str | None:
        if not username or self._account is None:
            return None
        try:
            chat = self._account.get_chat_by_name(username, make_request=True)
        except Exception as exc:  # pragma: no cover - сеть
            log.warning("Не нашёл чат с %s: %s", username, exc)
            return None
        return str(chat.id) if chat is not None else None
