"""Сервис выдачи: что видит покупатель и что видит продавец."""

from __future__ import annotations

from botfp import stock
from botfp.delivery import DeliveryService
from botfp.stock import ClaimStatus
from botfp.transport import NewOrderEvent


def order(order_id="1", buyer="bob", lot_id="starter", chat_id="chat-1"):
    return NewOrderEvent(order_id=order_id, buyer=buyer, lot_id=lot_id, chat_id=chat_id)


def service(conn, config, transport):
    return DeliveryService(conn, config, transport)


def test_buyer_receives_payload_instructions_and_review_request(conn, config, transport):
    stock.add_items(conn, "starter", ["login:pass"])

    service(conn, config, transport).handle_order(order())

    chat_id, text = transport.sent[0]
    assert chat_id == "chat-1"
    assert "login:pass" in text
    assert "Смените пароль." in text
    assert "отзыв" in text
    assert stock.delivery_stats(conn)["delivered"] == 1


def test_review_request_can_be_switched_off(conn, config, transport):
    from dataclasses import replace

    stock.add_items(conn, "starter", ["login:pass"])
    quiet = replace(config, ask_for_review=False)

    service(conn, quiet, transport).handle_order(order())

    assert "отзыв" not in transport.sent[0][1]
    assert "login:pass" in transport.sent[0][1]


def test_duplicate_order_event_delivers_once(conn, config, transport):
    stock.add_items(conn, "starter", ["first", "second"])
    svc = service(conn, config, transport)

    svc.handle_order(order())
    status = svc.handle_order(order())

    assert status is ClaimStatus.ALREADY_DELIVERED
    assert len(transport.sent) == 1
    assert stock.available_count(conn, "starter") == 1


def test_out_of_stock_apologises_to_buyer_and_alerts_seller(conn, config, transport):
    status = service(conn, config, transport).handle_order(order())

    assert status is ClaimStatus.OUT_OF_STOCK
    assert "закончился" in transport.sent[0][1]
    assert transport.dms[0][0] == "seller"
    assert "#1" in transport.dms[0][1]


def test_failed_send_is_recorded_and_item_stays_reserved(conn, config, transport):
    stock.add_items(conn, "starter", ["login:pass"])
    transport.fail_on_send = True

    service(conn, config, transport).handle_order(order())

    delivery = stock.delivery_by_order(conn, "1")
    assert delivery.status == "failed"
    assert delivery.stock_item_id is not None
    assert stock.available_count(conn, "starter") == 0


def test_retry_after_failure_sends_the_same_item(conn, config, transport):
    stock.add_items(conn, "starter", ["login:pass", "other:one"])
    transport.fail_on_send = True
    svc = service(conn, config, transport)
    svc.handle_order(order())

    transport.fail_on_send = False
    assert svc.retry("1") is True

    assert "login:pass" in transport.sent[-1][1]
    assert stock.delivery_by_order(conn, "1").status == "delivered"
    assert stock.available_count(conn, "starter") == 1


def test_retry_all_pending_reports_counts(conn, config, transport):
    stock.add_items(conn, "starter", ["a", "b"])
    transport.fail_on_send = True
    svc = service(conn, config, transport)
    svc.handle_order(order(order_id="1", buyer="bob"))
    svc.handle_order(order(order_id="2", buyer="alice", chat_id="chat-2"))

    transport.fail_on_send = False
    assert svc.retry_all_pending() == (2, 2)


def test_retry_of_unknown_order_fails(conn, config, transport):
    assert service(conn, config, transport).retry("404") is False


def test_low_stock_warns_the_seller(conn, config, transport):
    # Порог в фикстуре — 2, поэтому первое предупреждение ждём на остатке 2.
    stock.add_items(conn, "starter", ["a", "b", "c", "d"])

    svc = service(conn, config, transport)
    svc.handle_order(order(order_id="1"))
    assert transport.dms == [], "на остатке 3 продавца дёргать рано"

    svc.handle_order(order(order_id="2", buyer="alice", chat_id="chat-2"))
    assert "Заканчивается товар" in transport.dms[0][1]
    assert "осталось 2" in transport.dms[0][1]


def test_unknown_lot_is_escalated_not_guessed(conn, config, transport):
    status = service(conn, config, transport).handle_order(order(lot_id="mystery"))

    assert status is ClaimStatus.OUT_OF_STOCK
    assert transport.sent == []
    assert "нет в конфиге" in transport.dms[0][1]


def test_delivery_falls_back_to_direct_message_without_chat_id(conn, config, transport):
    stock.add_items(conn, "starter", ["login:pass"])

    service(conn, config, transport).handle_order(order(chat_id=None))

    assert transport.sent == []
    assert transport.dms[0][0] == "bob"
    assert "login:pass" in transport.dms[0][1]
