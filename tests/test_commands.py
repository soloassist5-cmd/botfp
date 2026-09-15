"""Команды в чате: вежливость, права и молчание там, где надо."""

from __future__ import annotations

import pytest

from botfp import stock
from botfp.commands import CommandRouter, parse
from botfp.delivery import DeliveryService
from botfp.transport import NewMessageEvent

_counter = iter(range(1, 10_000))


def message(text, author="bob", chat_id="chat-1"):
    return NewMessageEvent(
        chat_id=chat_id, author=author, text=text, message_id=f"m{next(_counter)}"
    )


@pytest.fixture()
def router(conn, config, transport):
    delivery = DeliveryService(conn, config, transport)
    return CommandRouter(conn, config, transport, delivery, self_username="shop-bot")


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("!помощь", "help"),
        ("/help", "help"),
        ("!Наличие", "stock"),
        ("!товары", "lots"),
        ("!повтор", "repeat"),
        ("!человек", "human"),
        ("!чтототакое", ""),
        ("привет", None),
        ("", None),
        ("!", None),
    ],
)
def test_parse_recognises_commands(text, expected):
    assert parse(text)[0] == expected


def test_parse_keeps_arguments():
    assert parse("!retry 42 extra") == ("", ["42", "extra"])


def test_help_lists_commands(router, transport):
    router.handle_message(message("!помощь"))

    assert "!наличие" in transport.sent[0][1]
    assert "Команды продавца" not in transport.sent[0][1]


def test_help_shows_extra_section_to_seller(router, transport):
    router.handle_message(message("!помощь", author="seller"))

    assert "Команды продавца" in transport.sent[0][1]


def test_stock_reports_counts(router, conn, transport):
    stock.add_items(conn, "starter", ["a", "b"])

    router.handle_message(message("!наличие"))

    text = transport.sent[0][1]
    assert "Стартовый аккаунт — 2 шт." in text
    assert "Премиум-аккаунт — нет в наличии" in text


def test_stock_says_so_when_everything_is_gone(router, transport):
    router.handle_message(message("!наличие"))

    assert "склад пуст" in transport.sent[0][1]


def test_lots_lists_titles(router, transport):
    router.handle_message(message("!товары"))

    assert "Стартовый аккаунт" in transport.sent[0][1]
    assert "Премиум-аккаунт" in transport.sent[0][1]


def test_repeat_resends_the_last_delivery(router, conn, transport):
    stock.add_items(conn, "starter", ["login:pass"])
    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)

    router.handle_message(message("!повтор"))

    assert "login:pass" in transport.sent[0][1]


def test_repeat_is_polite_when_there_is_nothing(router, transport):
    router.handle_message(message("!повтор"))

    assert "Не нашёл" in transport.sent[0][1]


def test_repeat_does_not_leak_another_buyers_item(router, conn, transport):
    stock.add_items(conn, "starter", ["alice-secret"])
    claim = stock.claim_for_order(conn, order_id="1", buyer="alice", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)

    router.handle_message(message("!повтор", author="bob"))

    assert "alice-secret" not in transport.sent[0][1]
    assert "Не нашёл" in transport.sent[0][1]


def test_human_pings_the_seller(router, transport):
    router.handle_message(message("!человек"))

    assert "Передал сообщение продавцу" in transport.sent[0][1]
    assert transport.dms[0][0] == "seller"
    assert "bob" in transport.dms[0][1]


def test_admin_commands_are_hidden_from_buyers(router, transport):
    router.handle_message(message("!стат", author="bob"))

    assert "Не знаю такой команды" in transport.sent[0][1]


def test_admin_commands_work_for_the_seller(router, conn, transport):
    stock.add_items(conn, "starter", ["a"])

    router.handle_message(message("!стат", author="seller"))

    assert "Склад:" in transport.sent[0][1]


def test_pending_command_for_seller(router, conn, transport):
    stock.claim_for_order(conn, order_id="7", buyer="bob", lot_id="starter")

    router.handle_message(message("!ожидают", author="seller"))

    assert "#7" in transport.sent[0][1]


def test_unknown_command_points_to_help(router, transport):
    router.handle_message(message("!абракадабра"))

    assert "!помощь" in transport.sent[0][1]


def test_greets_once_then_stays_quiet(router, transport):
    router.handle_message(message("здравствуйте"))
    router.handle_message(message("а есть аккаунты?"))

    assert len(transport.sent) == 1
    assert "Здравствуйте" in transport.sent[0][1]


def test_greets_each_chat_separately(router, transport):
    router.handle_message(message("привет", chat_id="chat-1"))
    router.handle_message(message("привет", author="alice", chat_id="chat-2"))

    assert len(transport.sent) == 2


def test_ignores_its_own_messages(router, transport):
    assert router.handle_message(message("!помощь", author="shop-bot")) is None
    assert transport.sent == []


def test_send_failure_does_not_raise(router, transport):
    transport.fail_on_send = True

    assert router.handle_message(message("!помощь")) is None
