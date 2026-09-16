"""Транспортный слой: события площадки и способ отправки сообщений.

Логика бота не знает, откуда приходят события. Это позволяет гонять её в
тестах и вживую крутить в консоли, не трогая боевой аккаунт FunPay.
"""

from __future__ import annotations

import sys
from collections.abc import Iterable, Iterator
from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable


@dataclass(frozen=True)
class NewOrderEvent:
    """Оплаченный заказ."""

    order_id: str
    buyer: str
    lot_id: str
    chat_id: str | None = None
    title: str = ""

    @property
    def key(self) -> str:
        return f"order:{self.order_id}"


@dataclass(frozen=True)
class NewMessageEvent:
    """Сообщение в чате."""

    chat_id: str
    author: str
    text: str
    message_id: str

    @property
    def key(self) -> str:
        return f"message:{self.message_id}"


Event = NewOrderEvent | NewMessageEvent


class TransportError(Exception):
    """Не удалось отправить сообщение или получить события."""


# Признаки того, что площадка не приняла нашу сессию, а не просто «сеть легла».
# Разница важная: сеть починится сама, протухший ключ — нет, его надо менять
# руками, и продавцу надо сказать об этом прямо.
_AUTH_MARKERS = (
    "unauthorized",
    "unauthenticated",
    "401",
    "403",
    "forbidden",
    "golden_key",
    "не авторизован",
    "авторизаци",
    "unauthorised",
    "session expired",
    "войдите",
)


def looks_like_auth_failure(error: object) -> bool:
    """Похоже ли, что дело в ключе, а не в связи."""
    lowered = str(error).casefold()
    return any(marker in lowered for marker in _AUTH_MARKERS)


@runtime_checkable
class Transport(Protocol):
    """Контракт площадки."""

    def poll(self) -> Iterable[Event]:
        """Возвращает новые события. Может быть пустым."""

    def send_message(self, chat_id: str, text: str) -> None:
        """Отправляет сообщение в чат. Бросает :class:`TransportError`."""

    def send_to_user(self, username: str, text: str) -> None:
        """Отправляет личное сообщение пользователю по нику."""


@dataclass
class RecordingTransport:
    """Транспорт-заглушка: копит исходящие сообщения вместо отправки.

    Используется в тестах и как база для консольного режима.
    """

    events: list[Event] = field(default_factory=list)
    sent: list[tuple[str, str]] = field(default_factory=list)
    dms: list[tuple[str, str]] = field(default_factory=list)
    fail_on_send: bool = False

    def poll(self) -> Iterable[Event]:
        pending, self.events = self.events, []
        return pending

    def send_message(self, chat_id: str, text: str) -> None:
        if self.fail_on_send:
            raise TransportError("отправка отключена (fail_on_send)")
        self.sent.append((str(chat_id), text))

    def send_to_user(self, username: str, text: str) -> None:
        if self.fail_on_send:
            raise TransportError("отправка отключена (fail_on_send)")
        self.dms.append((username, text))

    def feed(self, *events: Event) -> None:
        self.events.extend(events)


class ConsoleTransport(RecordingTransport):
    """Ручной прогон бота в терминале.

    Команды ввода::

        order <id> <покупатель> <лот>   — сымитировать оплаченный заказ
        msg   <покупатель> <текст>      — сымитировать сообщение в чат
        quit                            — выход
    """

    HELP = (
        "Команды: order <id> <покупатель> <лот> | msg <покупатель> <текст> | quit"
    )

    def poll(self) -> Iterable[Event]:
        return list(self._read_once())

    def _read_once(self) -> Iterator[Event]:
        try:
            line = input("botfp> ").strip()
        except (EOFError, KeyboardInterrupt):
            raise SystemExit(0) from None

        if not line:
            return
        if line in ("quit", "exit"):
            raise SystemExit(0)

        parts = line.split(maxsplit=3)
        verb = parts[0]

        if verb == "order" and len(parts) >= 4:
            order_id, buyer, lot_id = parts[1], parts[2], parts[3].split()[0]
            yield NewOrderEvent(
                order_id=order_id, buyer=buyer, lot_id=lot_id, chat_id=f"chat-{buyer}"
            )
        elif verb == "msg" and len(parts) >= 3:
            buyer = parts[1]
            text = " ".join(parts[2:])
            self._counter = getattr(self, "_counter", 0) + 1
            yield NewMessageEvent(
                chat_id=f"chat-{buyer}",
                author=buyer,
                text=text,
                message_id=f"console-{self._counter}",
            )
        else:
            print(self.HELP, file=sys.stderr)

    def send_message(self, chat_id: str, text: str) -> None:
        super().send_message(chat_id, text)
        print(f"\n--- в чат {chat_id} ---\n{text}\n", flush=True)

    def send_to_user(self, username: str, text: str) -> None:
        super().send_to_user(username, text)
        print(f"\n--- лично {username} ---\n{text}\n", flush=True)
