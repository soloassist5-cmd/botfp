"""Безлимитные лоты: гайд, ссылка, файл — продаётся сколько угодно раз."""

from __future__ import annotations

from dataclasses import replace

import pytest

from botfp import stock
from botfp.commands import CommandRouter
from botfp.config import Config, LotConfig
from botfp.delivery import DeliveryService
from botfp.stock import ClaimStatus
from botfp.transport import NewMessageEvent, NewOrderEvent

GUIDE = "https://docs.google.com/document/d/guide-standoff-2"


@pytest.fixture()
def guide_config() -> Config:
    return Config(
        golden_key="k",
        user_agent="u",
        db_path=":memory:",
        admins=("seller",),
        lots={
            "guide": LotConfig(
                lot_id="guide",
                title="Гайд по Standoff 2",
                kind="unlimited",
                payload=GUIDE,
                instructions="Ссылка вечная, доступ по ней не пропадёт.",
                match=("Гайд по Standoff 2",),
            )
        },
        low_stock_threshold=2,
        listing_delay=0.0,
    )


@pytest.fixture()
def svc(conn, guide_config, transport) -> DeliveryService:
    return DeliveryService(conn, guide_config, transport)


def order(order_id, buyer="bob"):
    return NewOrderEvent(
        order_id=order_id, buyer=buyer, lot_id="guide", chat_id=f"chat-{buyer}"
    )


def test_same_guide_goes_to_every_buyer(svc, transport):
    for i in range(1, 4):
        svc.handle_order(order(str(i), buyer=f"buyer{i}"))

    assert len(transport.sent) == 3
    assert all(GUIDE in text for _, text in transport.sent)


def test_unlimited_lot_never_runs_out(svc, transport):
    for i in range(1, 51):
        status = svc.handle_order(order(str(i), buyer=f"buyer{i}"))
        assert status is ClaimStatus.CLAIMED

    assert len(transport.sent) == 50
    assert transport.dms == [], "продавца не надо дёргать про остатки"


def test_duplicate_order_still_delivers_once(svc, transport):
    svc.handle_order(order("1"))
    status = svc.handle_order(order("1"))

    assert status is ClaimStatus.ALREADY_DELIVERED
    assert len(transport.sent) == 1


def test_instructions_and_review_request_are_included(svc, transport):
    svc.handle_order(order("1"))

    text = transport.sent[0][1]
    assert "Ссылка вечная" in text
    assert "отзыв" in text


def test_no_stock_rows_are_consumed(svc, conn):
    svc.handle_order(order("1"))

    assert stock.available_count(conn, "guide") == 0
    assert conn.execute("SELECT COUNT(*) FROM stock_items").fetchone()[0] == 0


def test_delivered_payload_is_recorded_for_disputes(svc, conn):
    svc.handle_order(order("1"))

    delivery = stock.delivery_by_order(conn, "1")
    assert delivery.payload_snapshot == GUIDE
    assert delivery.stock_item_id is None


def test_repeat_command_resends_the_guide(conn, guide_config, transport):
    svc = DeliveryService(conn, guide_config, transport)
    svc.handle_order(order("1"))
    transport.sent.clear()

    router = CommandRouter(conn, guide_config, transport, svc)
    router.handle_message(
        NewMessageEvent(chat_id="chat-bob", author="bob", text="!повтор", message_id="m1")
    )

    assert GUIDE in transport.sent[0][1]


def test_failed_send_retries_with_the_same_payload(conn, guide_config, transport):
    svc = DeliveryService(conn, guide_config, transport)
    transport.fail_on_send = True
    svc.handle_order(order("1"))
    assert stock.delivery_by_order(conn, "1").status == "failed"

    transport.fail_on_send = False
    assert svc.retry("1") is True
    assert GUIDE in transport.sent[-1][1]


def test_changing_the_link_affects_only_new_orders(conn, guide_config, transport):
    """Старый заказ помнит, что именно ему отправили."""
    svc = DeliveryService(conn, guide_config, transport)
    svc.handle_order(order("1"))

    moved = replace(
        guide_config,
        lots={"guide": replace(guide_config.lots["guide"], payload="https://new.link")},
    )
    DeliveryService(conn, moved, transport).handle_order(order("2", buyer="alice"))

    assert stock.delivery_by_order(conn, "1").payload_snapshot == GUIDE
    assert stock.delivery_by_order(conn, "2").payload_snapshot == "https://new.link"


def test_stock_command_marks_unlimited_lots(conn, guide_config, transport):
    svc = DeliveryService(conn, guide_config, transport)
    router = CommandRouter(conn, guide_config, transport, svc)

    router.handle_message(
        NewMessageEvent(chat_id="c", author="bob", text="!наличие", message_id="m1")
    )

    text = transport.sent[0][1]
    assert "склад пуст" not in text, "безлимитный товар всегда в наличии"
    assert "Гайд по Standoff 2 — в наличии" in text
