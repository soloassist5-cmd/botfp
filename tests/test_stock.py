"""Склад: FIFO, атомарность и идемпотентность выдачи."""

from __future__ import annotations

import pytest

from botfp import stock
from botfp.stock import ClaimStatus


def test_add_items_skips_duplicates_and_blanks(conn):
    result = stock.add_items(conn, "starter", ["a:1", "b:2", "", "  ", "a:1"])

    assert (result.added, result.duplicates, result.blank) == (2, 1, 2)
    assert stock.available_count(conn, "starter") == 2


def test_same_payload_allowed_in_different_lots(conn):
    stock.add_items(conn, "starter", ["a:1"])
    result = stock.add_items(conn, "premium", ["a:1"])

    assert result.added == 1


def test_claim_hands_out_oldest_item_first(conn):
    stock.add_items(conn, "starter", ["first", "second"])

    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert claim.status is ClaimStatus.CLAIMED
    assert claim.item.payload == "first"
    assert stock.available_count(conn, "starter") == 1


def test_two_orders_never_receive_the_same_item(conn):
    stock.add_items(conn, "starter", ["first", "second"])

    one = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    two = stock.claim_for_order(conn, order_id="2", buyer="alice", lot_id="starter")

    assert one.item.id != two.item.id
    assert {one.item.payload, two.item.payload} == {"first", "second"}
    assert stock.available_count(conn, "starter") == 0


def test_repeated_order_event_does_not_consume_second_item(conn):
    stock.add_items(conn, "starter", ["first", "second"])
    first = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, first.delivery.id)

    again = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert again.status is ClaimStatus.ALREADY_DELIVERED
    assert again.needs_send is False
    assert stock.available_count(conn, "starter") == 1


def test_interrupted_delivery_resumes_with_the_same_item(conn):
    stock.add_items(conn, "starter", ["first", "second"])
    first = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    # Процесс упал до подтверждения: заказ остался в статусе sending.
    resumed = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert resumed.status is ClaimStatus.RESUME
    assert resumed.needs_send is True
    assert resumed.item.id == first.item.id
    assert resumed.delivery.attempts == 2
    assert stock.available_count(conn, "starter") == 1


def test_out_of_stock_records_the_order(conn):
    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert claim.status is ClaimStatus.OUT_OF_STOCK
    assert claim.item is None
    assert claim.delivery.status == "out_of_stock"


def test_order_is_served_after_restock(conn):
    stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.add_items(conn, "starter", ["fresh"])

    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert claim.status is ClaimStatus.CLAIMED
    assert claim.item.payload == "fresh"


def test_failed_send_keeps_the_item_reserved(conn):
    """Товар не возвращается на склад: сообщение могло дойти."""
    stock.add_items(conn, "starter", ["only"])
    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    stock.mark_failed(conn, claim.delivery.id, "сеть отвалилась")

    assert stock.available_count(conn, "starter") == 0
    other = stock.claim_for_order(conn, order_id="2", buyer="alice", lot_id="starter")
    assert other.status is ClaimStatus.OUT_OF_STOCK


def test_release_returns_the_item_to_stock(conn):
    stock.add_items(conn, "starter", ["only"])
    stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert stock.release_order(conn, "1") is True
    assert stock.available_count(conn, "starter") == 1

    reused = stock.claim_for_order(conn, order_id="2", buyer="alice", lot_id="starter")
    assert reused.item.payload == "only"


def test_release_unknown_order_is_noop(conn):
    assert stock.release_order(conn, "404") is False


def test_last_delivery_lookup_is_case_insensitive(conn):
    stock.add_items(conn, "starter", ["secret"])
    claim = stock.claim_for_order(conn, order_id="1", buyer="Bob", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)

    found = stock.last_delivery_for_buyer(conn, "bob")

    assert found is not None
    assert found[1] == "secret"


def test_last_delivery_ignores_undelivered_orders(conn):
    stock.add_items(conn, "starter", ["secret"])
    stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert stock.last_delivery_for_buyer(conn, "bob") is None


def test_pending_lists_everything_unfinished(conn):
    stock.add_items(conn, "starter", ["a", "b"])
    done = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, done.delivery.id)
    stock.claim_for_order(conn, order_id="2", buyer="alice", lot_id="starter")
    stock.claim_for_order(conn, order_id="3", buyer="carol", lot_id="starter")

    pending = stock.pending_deliveries(conn)

    assert [d.order_id for d in pending] == ["2", "3"]
    assert stock.delivery_stats(conn)["delivered"] == 1


@pytest.mark.parametrize("order_id", ["42", 42])
def test_order_id_is_normalised_to_text(conn, order_id):
    stock.add_items(conn, "starter", ["a"])
    stock.claim_for_order(conn, order_id=order_id, buyer="bob", lot_id="starter")

    assert stock.delivery_by_order(conn, "42") is not None
