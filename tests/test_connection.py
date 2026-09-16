"""Обрыв связи с FunPay: продавец должен узнать об этом сам."""

from __future__ import annotations

import json
import urllib.request

import pytest

from botfp import db, stock
from botfp.bot import Bot, _humanize
from botfp.health import HealthServer
from botfp.transport import TransportError, looks_like_auth_failure


class BreakingTransport:
    """Транспорт, у которого опрос падает по команде."""

    def __init__(self):
        self.error: Exception | None = None
        self.down = False
        self.dms: list[tuple[str, str]] = []

    def poll(self):
        if self.error:
            raise self.error
        return []

    def send_message(self, chat_id, text):
        raise TransportError("связи нет")

    def send_to_user(self, username, text):
        if self.down:
            raise TransportError("связи нет")
        self.dms.append((username, text))


class Collector:
    def __init__(self):
        self.messages: list[str] = []

    def notify(self, text: str) -> None:
        self.messages.append(text)


@pytest.fixture()
def wiring(conn, config):
    transport = BreakingTransport()
    notifier = Collector()
    bot = Bot(conn, config, transport, notifiers=(notifier,))
    return bot, transport, notifier


def test_first_failure_alerts_the_seller(wiring):
    bot, transport, notifier = wiring

    bot._on_poll_error(TransportError("соединение сброшено"))

    assert len(notifier.messages) == 1
    assert "Связь с FunPay потеряна" in notifier.messages[0]
    assert "соединение сброшено" in notifier.messages[0]


def test_repeated_failures_do_not_spam(wiring):
    bot, transport, notifier = wiring

    for _ in range(10):
        bot._on_poll_error(TransportError("всё ещё лежит"))

    assert len(notifier.messages) == 1, "сообщаем один раз на обрыв, а не на попытку"
    assert bot._poll_errors == 10


def test_expired_key_gets_its_own_message(wiring):
    """Сеть починится сама, ключ — нет. Продавцу надо сказать по-разному."""
    bot, transport, notifier = wiring

    bot._on_poll_error(TransportError("401 Unauthorized"))

    assert "Ключ FunPay больше не действует" in notifier.messages[0]
    assert "golden_key" in notifier.messages[0]


def test_recovery_is_announced(wiring):
    bot, transport, notifier = wiring
    bot._on_poll_error(TransportError("обрыв"))

    bot._on_poll_ok()

    assert any("восстановлена" in m for m in notifier.messages)
    assert bot.connection_lost is False


def test_recovery_retries_pending_orders(wiring, conn):
    """Пока связи не было, заказы могли оплатить."""
    bot, transport, notifier = wiring
    stock.add_items(conn, "starter", ["login:pass"])
    stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    bot._on_poll_error(TransportError("обрыв"))

    bot._on_poll_ok()

    restored = next(m for m in notifier.messages if "восстановлена" in m)
    assert "Незавершённые заказы: выдано 1 из 1" in restored


def test_no_announcement_when_nothing_broke(wiring):
    bot, transport, notifier = wiring

    bot._on_poll_ok()

    assert notifier.messages == []


def test_connection_state_is_visible_to_the_panel(wiring, conn):
    """Панель живёт в другом потоке и читает состояние из базы."""
    bot, transport, notifier = wiring

    bot._on_poll_error(TransportError("обрыв"))
    assert db.get_state(conn, "funpay_connection")[0] == "lost"

    bot._on_poll_ok()
    assert db.get_state(conn, "funpay_connection")[0] == "ok"


def test_alert_reaches_telegram_even_though_funpay_is_down(wiring):
    """Уведомление об обрыве FunPay нельзя слать через сам FunPay."""
    bot, transport, notifier = wiring
    transport.down = True  # личка FunPay недоступна вместе со всем остальным

    bot._on_poll_error(TransportError("обрыв"))

    assert transport.dms == [], "канал FunPay лежит"
    assert len(notifier.messages) == 1, "но Telegram сработал"
    assert "Связь с FunPay потеряна" in notifier.messages[0]


@pytest.mark.parametrize(
    ("error", "expected"),
    [
        ("401 Unauthorized", True),
        ("403 Forbidden", True),
        ("golden_key устарел", True),
        ("Ошибка авторизации", True),
        ("Connection reset by peer", False),
        ("timed out", False),
        ("502 Bad Gateway", False),
    ],
)
def test_auth_failure_detection(error, expected):
    assert looks_like_auth_failure(error) is expected


@pytest.mark.parametrize(
    ("seconds", "expected"),
    [(30, "меньше минуты"), (90, "1 мин."), (3600, "1 ч."), (7500, "2 ч. 5 мин.")],
)
def test_downtime_is_readable(seconds, expected):
    assert _humanize(seconds) == expected


# --- HTTP-заглушка -----------------------------------------------------------


def test_health_endpoint_answers():
    server = HealthServer(8123, lambda: {"status": "ok", "pending_deliveries": 2})
    server.start()
    try:
        body = urllib.request.urlopen("http://127.0.0.1:8123/", timeout=5).read()
    finally:
        server.stop()

    assert json.loads(body) == {"status": "ok", "pending_deliveries": 2}


def test_health_endpoint_reports_a_broken_connection():
    state = {"lost": False}
    server = HealthServer(
        8124,
        lambda: {"funpay": "disconnected" if state["lost"] else "connected"},
    )
    server.start()
    try:
        first = json.loads(urllib.request.urlopen("http://127.0.0.1:8124/", timeout=5).read())
        state["lost"] = True
        second = json.loads(urllib.request.urlopen("http://127.0.0.1:8124/", timeout=5).read())
    finally:
        server.stop()

    assert first["funpay"] == "connected"
    assert second["funpay"] == "disconnected"
