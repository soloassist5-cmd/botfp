"""Панель управления в Telegram.

Управление ботом с телефона: остатки, зависшие заказы, повтор выдачи,
состояние объявлений, проверка и бэкап. Сюда же приходят уведомления,
которые бот шлёт продавцу.

Доступ только у тех, чьи числовые Telegram ID перечислены в
``telegram.admin_ids``. Всё остальное игнорируется молча: отвечать
незнакомцам «у вас нет доступа» — значит подтверждать, что бот жив.

Клиент написан на стандартной библиотеке: панель не должна тянуть
зависимость ради четырёх HTTP-вызовов.
"""

from __future__ import annotations

import html
import json
import logging
import sqlite3
import threading
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any

from . import backup as backup_module
from . import db
from . import doctor as doctor_module
from . import listings as listings_module
from . import stock
from .config import Config, TelegramConfig

log = logging.getLogger(__name__)

API = "https://api.telegram.org/bot{token}/{method}"
MAX_MESSAGE = 3900  # лимит Telegram 4096, оставляем запас на разметку


class TelegramError(Exception):
    """Telegram не принял запрос."""


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------


class TelegramClient:
    """Минимальный клиент Bot API."""

    def __init__(self, token: str, *, timeout: float = 35.0):
        self.token = token
        self.timeout = timeout

    def call(self, method: str, **params: Any) -> Any:
        url = API.format(token=self.token, method=method)
        payload = json.dumps(
            {k: v for k, v in params.items() if v is not None}
        ).encode("utf-8")
        request = urllib.request.Request(
            url, data=payload, headers={"Content-Type": "application/json"}
        )

        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", "replace")[:300]
            raise TelegramError(f"{method}: HTTP {exc.code} {detail}") from exc
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise TelegramError(f"{method}: {exc}") from exc
        except json.JSONDecodeError as exc:
            raise TelegramError(f"{method}: Telegram вернул не JSON") from exc

        if not body.get("ok"):
            raise TelegramError(f"{method}: {body.get('description', 'неизвестная ошибка')}")
        return body.get("result")

    def get_updates(self, offset: int | None, timeout: int) -> list[dict]:
        return self.call(
            "getUpdates",
            offset=offset,
            timeout=timeout,
            allowed_updates=["message", "callback_query"],
        )

    def send_message(self, chat_id: int | str, text: str, keyboard: dict | None = None):
        return self.call(
            "sendMessage",
            chat_id=chat_id,
            text=_clip(text),
            parse_mode="HTML",
            disable_web_page_preview=True,
            reply_markup=keyboard,
        )

    def edit_message(
        self, chat_id: int | str, message_id: int, text: str, keyboard: dict | None = None
    ):
        return self.call(
            "editMessageText",
            chat_id=chat_id,
            message_id=message_id,
            text=_clip(text),
            parse_mode="HTML",
            disable_web_page_preview=True,
            reply_markup=keyboard,
        )

    def answer_callback(self, callback_id: str, text: str = "") -> None:
        self.call("answerCallbackQuery", callback_query_id=callback_id, text=text or None)


def _clip(text: str) -> str:
    if len(text) <= MAX_MESSAGE:
        return text
    return text[: MAX_MESSAGE - 40] + "\n\n… список обрезан, полностью — в CLI"


def esc(value: object) -> str:
    """Экранирует текст для parse_mode=HTML.

    Через панель проходят названия лотов, ники и тексты ошибок — любой из них
    может содержать «<» и сломать разметку сообщения.
    """
    return html.escape(str(value), quote=False)


# --------------------------------------------------------------------------
# Клавиатуры
# --------------------------------------------------------------------------


def _button(text: str, data: str) -> dict:
    return {"text": text, "callback_data": data}


MAIN_KEYBOARD = {
    "inline_keyboard": [
        [_button("📦 Склад", "stock"), _button("⏳ Зависшие", "pending")],
        [_button("📊 Статистика", "stats"), _button("🏷 Объявления", "listings")],
        [_button("🩺 Проверка", "doctor"), _button("💾 Бэкап", "backup")],
    ]
}

BACK_KEYBOARD = {"inline_keyboard": [[_button("‹ Меню", "menu")]]}


def _with_back(rows: list[list[dict]]) -> dict:
    return {"inline_keyboard": [*rows, [_button("‹ Меню", "menu")]]}


# --------------------------------------------------------------------------
# Панель
# --------------------------------------------------------------------------


@dataclass
class Reply:
    """Ответ панели: текст и клавиатура под ним."""

    text: str
    keyboard: dict | None = None
    alert: str = ""


class TelegramPanel:
    """Разбор команд и кнопок панели."""

    def __init__(
        self,
        conn: sqlite3.Connection,
        config: Config,
        client: TelegramClient,
        *,
        delivery=None,
        listings=None,
        config_path: str = "config.toml",
        backup_dir: str = "backups",
    ):
        self.conn = conn
        self.config = config
        self.client = client
        self.delivery = delivery
        self.listings = listings
        self.config_path = config_path
        self.backup_dir = backup_dir
        self._offset: int | None = None
        self._stop = threading.Event()

    @property
    def telegram(self) -> TelegramConfig:
        assert self.config.telegram is not None
        return self.config.telegram

    # ------------------------------------------------------------------
    # Приём обновлений
    # ------------------------------------------------------------------

    def handle_update(self, update: dict) -> Reply | None:
        """Возвращает ответ на обновление или ``None``, если отвечать не надо."""
        if "callback_query" in update:
            return self._handle_callback(update["callback_query"])
        if "message" in update:
            return self._handle_message(update["message"])
        return None

    def _handle_message(self, message: dict) -> Reply | None:
        user_id = message.get("from", {}).get("id")
        if not self.telegram.is_admin(user_id):
            log.warning("Сообщение от постороннего Telegram ID %s — игнорирую", user_id)
            return None

        text = str(message.get("text", "")).strip()
        command = text.split()[0].lstrip("/").split("@")[0].casefold() if text else ""

        handlers = {
            "start": self._menu,
            "menu": self._menu,
            "help": self._menu,
            "stock": self._stock,
            "склад": self._stock,
            "pending": self._pending,
            "stats": self._stats,
            "listings": self._listings,
            "doctor": self._doctor,
            "backup": self._backup,
        }
        handler = handlers.get(command)
        return handler() if handler else self._menu()

    def _handle_callback(self, query: dict) -> Reply | None:
        user_id = query.get("from", {}).get("id")
        if not self.telegram.is_admin(user_id):
            log.warning("Нажатие от постороннего Telegram ID %s — игнорирую", user_id)
            return None

        data = str(query.get("data", ""))
        action, _, argument = data.partition(":")

        simple = {
            "menu": self._menu,
            "stock": self._stock,
            "pending": self._pending,
            "stats": self._stats,
            "listings": self._listings,
            "doctor": self._doctor,
            "backup": self._backup,
            "retry_all": self._retry_all,
            "refresh_listings": self._refresh_listings,
        }
        if action in simple:
            return simple[action]()
        if action == "retry":
            return self._retry(argument)
        if action == "release":
            return self._release_confirm(argument)
        if action == "release_yes":
            return self._release(argument)
        return self._menu()

    # ------------------------------------------------------------------
    # Экраны
    # ------------------------------------------------------------------

    def _menu(self) -> Reply:
        pending = len(stock.pending_deliveries(self.conn))
        alarm = f"\n⏳ Требуют внимания: <b>{pending}</b>" if pending else ""
        return Reply(
            f"<b>Панель управления botfp</b>\n{self._connection_line()}{alarm}"
            "\n\nВыберите раздел:",
            MAIN_KEYBOARD,
        )

    def _connection_line(self) -> str:
        """Связь с FunPay. Без этой строки панель бодро отвечает, пока выдача стоит."""
        state = db.get_state(self.conn, "funpay_connection")
        if state is None:
            return "🔌 Связь с FunPay: неизвестно"
        value, updated = state
        if value == "ok":
            return "🟢 Связь с FunPay: есть"
        reason = db.get_state(self.conn, "funpay_connection_reason")
        detail = f"\n    <i>{esc(reason[0])}</i>" if reason and reason[0] else ""
        return f"🔴 Связь с FunPay: потеряна с {esc(updated)}{detail}"

    def _stock(self) -> Reply:
        counts = stock.available_counts(self.conn)
        lines = []
        for lot in self.config.lots.values():
            if lot.is_unlimited:
                lines.append(f"• {esc(lot.title)} — безлимитный")
                continue
            left = counts.get(lot.lot_id, 0)
            mark = "🔴" if left == 0 else ("🟡" if left <= self.config.low_stock_threshold else "🟢")
            lines.append(f"{mark} {esc(lot.title)} — <b>{left}</b> шт.")

        body = "\n".join(lines) if lines else "Лотов нет."
        hint = "\n\nПополнение: <code>botfp stock add --lot ID --file items.txt</code>"
        return Reply(f"<b>📦 Склад</b>\n\n{body}{hint}", BACK_KEYBOARD)

    def _stats(self) -> Reply:
        stats = stock.delivery_stats(self.conn)
        counts = stock.available_counts(self.conn)
        total_stock = sum(counts.values())
        text = (
            "<b>📊 Статистика</b>\n\n"
            f"Выдано успешно: <b>{stats['delivered']}</b>\n"
            f"В процессе: {stats['sending']}\n"
            f"С ошибкой: {stats['failed']}\n"
            f"Не хватило товара: {stats['out_of_stock']}\n\n"
            f"Единиц на складе: <b>{total_stock}</b>"
        )
        return Reply(text, BACK_KEYBOARD)

    def _pending(self) -> Reply:
        pending = stock.pending_deliveries(self.conn)
        if not pending:
            return Reply("<b>⏳ Зависшие заказы</b>\n\nВсё выдано. ✅", BACK_KEYBOARD)

        lines, rows = [], []
        for delivery in pending[:10]:
            reason = esc(delivery.error or delivery.status)
            lines.append(
                f"• <b>#{esc(delivery.order_id)}</b> — {esc(delivery.buyer)}\n"
                f"  лот {esc(delivery.lot_id)}, {reason}"
            )
            rows.append(
                [
                    _button(f"↻ Повторить #{delivery.order_id}", f"retry:{delivery.order_id}"),
                    _button("↩ Вернуть", f"release:{delivery.order_id}"),
                ]
            )

        tail = f"\n\n…и ещё {len(pending) - 10}" if len(pending) > 10 else ""
        if len(pending) > 1:
            rows.append([_button("↻ Повторить все", "retry_all")])

        return Reply(
            f"<b>⏳ Зависшие заказы: {len(pending)}</b>\n\n" + "\n".join(lines) + tail,
            _with_back(rows),
        )

    def _listings(self) -> Reply:
        if not self.config.listable_lots:
            return Reply(
                "<b>🏷 Объявления</b>\n\nНи у одного лота нет секции listing — "
                "автосоздание не настроено.",
                BACK_KEYBOARD,
            )

        links = {link.lot_id: link for link in listings_module.all_links(self.conn)}
        lines = []
        for lot in self.config.listable_lots:
            link = links.get(lot.lot_id)
            if link is None or not link.funpay_lot_id:
                lines.append(f"⚪ {esc(lot.title)} — не создано")
            elif link.active:
                lines.append(f"🟢 {esc(lot.title)} — на витрине (#{esc(link.funpay_lot_id)})")
            else:
                lines.append(f"🔴 {esc(lot.title)} — снято (#{esc(link.funpay_lot_id)})")

        rows = [[_button("↻ Обновить витрину", "refresh_listings")]] if self.listings else []
        return Reply("<b>🏷 Объявления</b>\n\n" + "\n".join(lines), _with_back(rows))

    def _doctor(self) -> Reply:
        from pathlib import Path

        checks = doctor_module.run_checks(self.conn, self.config, Path(self.config_path))
        lines = []
        for check in checks:
            lines.append(f"{doctor_module.MARKS[check.level]} {esc(check.title)}")
            if check.detail and check.level != doctor_module.OK:
                lines.append(f"    <i>{esc(check.detail)}</i>")

        level = doctor_module.worst_level(checks)
        verdict = {
            doctor_module.OK: "Всё в порядке.",
            doctor_module.WARN: "Работать можно, но посмотрите на предупреждения.",
            doctor_module.FAIL: "Есть блокирующие проблемы.",
        }[level]

        return Reply(f"<b>🩺 Проверка</b>\n\n" + "\n".join(lines) + f"\n\n<b>{verdict}</b>", BACK_KEYBOARD)

    def _backup(self) -> Reply:
        try:
            result = backup_module.create(self.conn, self.backup_dir)
        except OSError as exc:
            return Reply(f"<b>💾 Бэкап</b>\n\n✗ Не удалось: {esc(exc)}", BACK_KEYBOARD)

        removed = f"\nУдалено старых: {len(result.removed)}" if result.removed else ""
        return Reply(
            f"<b>💾 Бэкап</b>\n\n✓ Копия готова:\n<code>{esc(result.path)}</code>\n"
            f"Размер: {result.size} байт{removed}",
            BACK_KEYBOARD,
        )

    # ------------------------------------------------------------------
    # Действия
    # ------------------------------------------------------------------

    def _retry(self, order_id: str) -> Reply:
        if self.delivery is None:
            return Reply("Повтор недоступен: нет связи с FunPay.", BACK_KEYBOARD, alert="Нет связи")

        ok = self.delivery.retry(order_id)
        mark = "✓ Выдан" if ok else "✗ Не получилось"
        reply = self._pending()
        reply.text = f"<b>{mark}: заказ #{esc(order_id)}</b>\n\n" + reply.text
        reply.alert = mark
        return reply

    def _retry_all(self) -> Reply:
        if self.delivery is None:
            return Reply("Повтор недоступен: нет связи с FunPay.", BACK_KEYBOARD, alert="Нет связи")

        ok, total = self.delivery.retry_all_pending()
        reply = self._pending()
        reply.text = f"<b>Повторено {ok} из {total}</b>\n\n" + reply.text
        reply.alert = f"{ok} из {total}"
        return reply

    def _release_confirm(self, order_id: str) -> Reply:
        return Reply(
            f"<b>Вернуть товар заказа #{esc(order_id)} на склад?</b>\n\n"
            "Единица снова пойдёт в продажу. Делайте это, только если уверены, "
            "что покупатель ею не воспользовался.",
            {
                "inline_keyboard": [
                    [_button("Да, вернуть", f"release_yes:{order_id}")],
                    [_button("Отмена", "pending")],
                ]
            },
        )

    def _release(self, order_id: str) -> Reply:
        ok = stock.release_order(self.conn, order_id)
        mark = "✓ Товар возвращён на склад" if ok else "✗ Заказ не найден"
        reply = self._pending()
        reply.text = f"<b>{mark}: #{esc(order_id)}</b>\n\n" + reply.text
        reply.alert = mark
        return reply

    def _refresh_listings(self) -> Reply:
        if self.listings is None:
            return Reply("Управление витриной недоступно.", BACK_KEYBOARD, alert="Недоступно")

        try:
            changed = self.listings.refresh_availability()
        except Exception as exc:  # noqa: BLE001 - сеть может отдать что угодно
            log.exception("Обновление витрины из панели упало")
            return Reply(f"✗ Не получилось: {esc(exc)}", BACK_KEYBOARD, alert="Ошибка")

        reply = self._listings()
        note = f"Изменено объявлений: {len(changed)}" if changed else "Всё уже в нужном состоянии"
        reply.text = f"<b>{note}</b>\n\n" + reply.text
        reply.alert = note
        return reply

    # ------------------------------------------------------------------
    # Цикл
    # ------------------------------------------------------------------

    def run_once(self) -> int:
        """Один проход длинного опроса. Возвращает число обработанных обновлений."""
        updates = self.client.get_updates(self._offset, self.telegram.poll_timeout)
        for update in updates:
            self._offset = int(update["update_id"]) + 1
            try:
                self._dispatch(update)
            except Exception:
                log.exception("Ошибка при обработке обновления Telegram")
        return len(updates)

    def _dispatch(self, update: dict) -> None:
        reply = self.handle_update(update)
        if reply is None:
            return

        if "callback_query" in update:
            query = update["callback_query"]
            message = query.get("message", {})
            try:
                self.client.answer_callback(query["id"], reply.alert)
            except TelegramError as exc:
                log.debug("answerCallbackQuery не прошёл: %s", exc)
            try:
                self.client.edit_message(
                    message["chat"]["id"], message["message_id"], reply.text, reply.keyboard
                )
                return
            except TelegramError as exc:
                # Telegram отказывает, если текст не изменился, — тогда просто шлём новое.
                log.debug("Правка сообщения не прошла: %s", exc)
                chat_id = message["chat"]["id"]
        else:
            chat_id = update["message"]["chat"]["id"]

        self.client.send_message(chat_id, reply.text, reply.keyboard)

    def skip_backlog(self) -> None:
        """Пропускает накопившиеся обновления.

        Иначе после простоя бот отработает все нажатия, сделанные в его
        отсутствие, — включая повторы выдач.
        """
        try:
            updates = self.client.get_updates(-1, 0)
        except TelegramError as exc:
            log.warning("Не удалось пропустить очередь Telegram: %s", exc)
            return
        if updates:
            self._offset = int(updates[-1]["update_id"]) + 1

    def run_forever(self) -> None:
        self.skip_backlog()
        log.info("Панель Telegram запущена, админов: %d", len(self.telegram.admin_ids))

        errors = 0
        while not self._stop.is_set():
            try:
                self.run_once()
                errors = 0
            except TelegramError as exc:
                errors += 1
                delay = min(5 * 2**errors, 300)
                log.error("Telegram недоступен (%s), пауза %d с.", exc, delay)
                self._stop.wait(delay)

        log.info("Панель Telegram остановлена.")

    def stop(self) -> None:
        self._stop.set()


# --------------------------------------------------------------------------
# Уведомления
# --------------------------------------------------------------------------


class TelegramNotifier:
    """Дублирует уведомления продавцу в Telegram."""

    def __init__(self, client: TelegramClient, config: TelegramConfig):
        self.client = client
        self.config = config

    def notify(self, text: str) -> None:
        for admin_id in self.config.admin_ids:
            try:
                self.client.send_message(admin_id, esc(text))
            except TelegramError as exc:
                log.error("Не смог написать в Telegram %s: %s", admin_id, exc)
