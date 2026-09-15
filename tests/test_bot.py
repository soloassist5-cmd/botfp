"""Цикл бота: дедупликация событий и устойчивость к ошибкам."""

from __future__ import annotations

from botfp import stock
from botfp.bot import Bot
from botfp.transport import NewMessageEvent, NewOrderEvent


def test_repeated_message_event_is_handled_once(conn, config, transport):
    bot = Bot(conn, config, transport)
    event = NewMessageEvent(chat_id="c1", author="bob", text="!помощь", message_id="m1")

    transport.feed(event, event)
    bot.run_once()

    assert len(transport.sent) == 1


def test_repeated_order_event_is_a_free_retry(conn, config, transport):
    """Повтор заказа не выдаёт второй товар, но дотягивает упавшую выдачу."""
    stock.add_items(conn, "starter", ["a", "b"])
    bot = Bot(conn, config, transport)
    event = NewOrderEvent(order_id="1", buyer="bob", lot_id="starter", chat_id="c1")

    transport.fail_on_send = True
    transport.feed(event)
    bot.run_once()
    assert stock.delivery_by_order(conn, "1").status == "failed"

    transport.fail_on_send = False
    transport.feed(event)
    bot.run_once()

    assert stock.delivery_by_order(conn, "1").status == "delivered"
    assert len(transport.sent) == 1
    assert stock.available_count(conn, "starter") == 1


def test_one_broken_event_does_not_stop_the_rest(conn, config, transport, monkeypatch):
    stock.add_items(conn, "starter", ["a"])
    bot = Bot(conn, config, transport)

    def explode(event):
        raise RuntimeError("boom")

    monkeypatch.setattr(bot.delivery, "handle_order", explode)
    transport.feed(
        NewOrderEvent(order_id="1", buyer="bob", lot_id="starter", chat_id="c1"),
        NewMessageEvent(chat_id="c2", author="alice", text="!помощь", message_id="m1"),
    )

    assert bot.run_once() == 2
    assert len(transport.sent) == 1


def test_bot_ignores_its_own_chatter(conn, config, transport):
    bot = Bot(conn, config, transport, self_username="shop-bot")

    transport.feed(
        NewMessageEvent(chat_id="c1", author="shop-bot", text="!помощь", message_id="m1")
    )
    bot.run_once()

    assert transport.sent == []


def test_run_once_reports_event_count(conn, config, transport):
    bot = Bot(conn, config, transport)

    assert bot.run_once() == 0
