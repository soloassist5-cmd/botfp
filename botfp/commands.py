"""Разбор команд из чата и вежливые ответы."""

from __future__ import annotations

import logging
import sqlite3

from . import db, stock, templates
from .config import Config
from .delivery import DeliveryService
from .transport import NewMessageEvent, Transport, TransportError

log = logging.getLogger(__name__)

PREFIXES = ("!", "/")

# Синонимы -> каноническое имя команды. Покупатель пишет как ему удобно.
ALIASES = {
    "помощь": "help",
    "help": "help",
    "start": "help",
    "команды": "help",
    "наличие": "stock",
    "stock": "stock",
    "склад": "stock",
    "товары": "lots",
    "lots": "lots",
    "лоты": "lots",
    "повтор": "repeat",
    "repeat": "repeat",
    "повторить": "repeat",
    "человек": "human",
    "human": "human",
    "админ": "human",
    "support": "human",
    "продавец": "human",
    "стат": "stats",
    "stats": "stats",
    "статистика": "stats",
    "ожидают": "pending",
    "pending": "pending",
}

ADMIN_ONLY = {"stats", "pending"}


def parse(text: str) -> tuple[str | None, list[str]]:
    """Разбирает строку в ``(команда, аргументы)``.

    ``(None, [])`` — обычное сообщение. ``("", [])`` — префикс есть, но
    команда неизвестна.
    """
    text = text.strip()
    if not text or text[0] not in PREFIXES:
        return None, []

    parts = text[1:].split()
    if not parts:
        return None, []

    name = parts[0].casefold().lstrip("!/")
    return ALIASES.get(name, ""), parts[1:]


class CommandRouter:
    """Отвечает на сообщения покупателей."""

    def __init__(
        self,
        conn: sqlite3.Connection,
        config: Config,
        transport: Transport,
        delivery: DeliveryService,
        self_username: str = "",
    ):
        self.conn = conn
        self.config = config
        self.transport = transport
        self.delivery = delivery
        self.self_username = self_username

    def handle_message(self, event: NewMessageEvent) -> str | None:
        """Обрабатывает сообщение и возвращает отправленный ответ (или ``None``)."""
        if self.self_username and event.author.casefold() == self.self_username.casefold():
            return None  # своё же сообщение вернулось в ленте событий

        command, args = parse(event.text)

        if command is None:
            # Обычный текст: здороваемся один раз и дальше не мешаем.
            if db.mark_greeted(self.conn, event.chat_id):
                return self._reply(event, templates.GREETING)
            return None

        if command == "":
            return self._reply(event, templates.UNKNOWN_COMMAND)

        if command in ADMIN_ONLY and not self.config.is_admin(event.author):
            return self._reply(event, templates.UNKNOWN_COMMAND)

        handler = getattr(self, f"_cmd_{command}")
        return self._reply(event, handler(event, args))

    # ------------------------------------------------------------------
    # Команды
    # ------------------------------------------------------------------

    def _cmd_help(self, event: NewMessageEvent, args: list[str]) -> str:
        text = templates.HELP
        if self.config.is_admin(event.author):
            text += "\n\n" + templates.ADMIN_HELP
        return text

    def _cmd_stock(self, event: NewMessageEvent, args: list[str]) -> str:
        counts = stock.available_counts(self.conn)
        lines = []
        anything_available = False

        for lot in self.config.lots.values():
            if lot.is_unlimited:
                # Безлимитный товар не кончается — счётчик покупателю не нужен.
                lines.append(templates.STOCK_UNLIMITED_LINE.format(lot_title=lot.title))
                anything_available = True
                continue

            count = counts.get(lot.lot_id, 0)
            template = templates.STOCK_LINE if count else templates.STOCK_EMPTY_LINE
            lines.append(template.format(lot_title=lot.title, count=count))
            anything_available = anything_available or count > 0

        if not anything_available:
            return templates.STOCK_ALL_EMPTY
        return templates.STOCK_HEADER.format(lines="\n".join(lines))

    def _cmd_lots(self, event: NewMessageEvent, args: list[str]) -> str:
        lines = "\n".join(
            templates.LOTS_LINE.format(lot_title=lot.title)
            for lot in self.config.lots.values()
        )
        return templates.LOTS_HEADER.format(lines=lines)

    def _cmd_repeat(self, event: NewMessageEvent, args: list[str]) -> str:
        found = stock.last_delivery_for_buyer(self.conn, event.author)
        if found is None:
            return templates.REPEAT_NOT_FOUND

        delivery, payload = found
        lot = self.config.lot(delivery.lot_id)
        title = lot.title if lot else delivery.lot_id
        return templates.REPEAT_OK.format(lot_title=title, payload=payload)

    def _cmd_human(self, event: NewMessageEvent, args: list[str]) -> str:
        self.delivery.notify_admins(
            templates.ADMIN_CALL_HUMAN.format(buyer=event.author, chat_id=event.chat_id)
        )
        return templates.CALL_HUMAN

    def _cmd_stats(self, event: NewMessageEvent, args: list[str]) -> str:
        counts = stock.available_counts(self.conn)
        lines = "\n".join(
            templates.STOCK_UNLIMITED_LINE.format(lot_title=lot.title)
            if lot.is_unlimited
            else templates.STOCK_LINE.format(
                lot_title=lot.title, count=counts.get(lot.lot_id, 0)
            )
            for lot in self.config.lots.values()
        )
        stats = stock.delivery_stats(self.conn)
        return templates.ADMIN_STATS.format(
            stock=lines,
            delivered=stats["delivered"],
            pending=stats["sending"] + stats["out_of_stock"],
            failed=stats["failed"],
        )

    def _cmd_pending(self, event: NewMessageEvent, args: list[str]) -> str:
        pending = stock.pending_deliveries(self.conn)
        if not pending:
            return "Зависших заказов нет — всё выдано. ✅"

        lines = [
            f"• #{d.order_id} — {d.buyer}, лот {d.lot_id}, статус {d.status}"
            for d in pending[:20]
        ]
        if len(pending) > 20:
            lines.append(f"…и ещё {len(pending) - 20}")
        return "Заказы, требующие внимания:\n" + "\n".join(lines)

    # ------------------------------------------------------------------

    def _reply(self, event: NewMessageEvent, text: str) -> str | None:
        try:
            self.transport.send_message(event.chat_id, text)
        except TransportError as exc:
            log.error("Не смог ответить в чат %s: %s", event.chat_id, exc)
            return None
        return text
