"""Панель управления в Telegram."""

from __future__ import annotations

import pytest

from botfp import stock
from botfp.config import Config, ListingConfig, LotConfig, TelegramConfig
from botfp.delivery import DeliveryService
from botfp.listings import ListingManager, save_link
from botfp.telegram_panel import TelegramNotifier, TelegramPanel, esc
from tests.test_listings import FakeLotsAPI

ADMIN = 111
STRANGER = 999


class FakeTelegramClient:
    def __init__(self):
        self.sent: list[tuple] = []
        self.edited: list[tuple] = []
        self.answered: list[tuple] = []
        self.updates: list[dict] = []
        self.backlog_offset: int | None = None

    def get_updates(self, offset, timeout):
        if offset == -1:
            self.backlog_offset = offset
            pending, self.updates = self.updates, []
            return pending
        pending, self.updates = self.updates, []
        return pending

    def send_message(self, chat_id, text, keyboard=None):
        self.sent.append((chat_id, text, keyboard))

    def edit_message(self, chat_id, message_id, text, keyboard=None):
        self.edited.append((chat_id, message_id, text, keyboard))

    def answer_callback(self, callback_id, text=""):
        self.answered.append((callback_id, text))


def message(text: str, user_id: int = ADMIN, update_id: int = 1) -> dict:
    return {
        "update_id": update_id,
        "message": {"chat": {"id": 555}, "from": {"id": user_id}, "text": text},
    }


def callback(data: str, user_id: int = ADMIN, update_id: int = 1) -> dict:
    return {
        "update_id": update_id,
        "callback_query": {
            "id": "cb1",
            "from": {"id": user_id},
            "data": data,
            "message": {"chat": {"id": 555}, "message_id": 42},
        },
    }


@pytest.fixture()
def tg_config() -> Config:
    return Config(
        golden_key="k", user_agent="u", db_path=":memory:", admins=("seller",),
        low_stock_threshold=2, listing_delay=0.0,
        telegram=TelegramConfig(token="t", admin_ids=(ADMIN,)),
        lots={
            "starter": LotConfig(
                "starter", "Стартовый аккаунт", match=("Стартовый аккаунт",),
                listing=ListingConfig(node_id=42, price=1.0, short_description="Стартовый"),
            ),
            "guide": LotConfig(
                "guide", "Гайд", kind="unlimited", payload="https://x", match=("Гайд",)
            ),
        },
    )


@pytest.fixture()
def client() -> FakeTelegramClient:
    return FakeTelegramClient()


@pytest.fixture()
def panel(conn, tg_config, client, transport, tmp_path) -> TelegramPanel:
    return TelegramPanel(
        conn, tg_config, client,
        delivery=DeliveryService(conn, tg_config, transport),
        listings=ListingManager(conn, tg_config, FakeLotsAPI()),
        backup_dir=str(tmp_path / "backups"),
    )


# --- доступ ------------------------------------------------------------------


def test_stranger_message_is_ignored_silently(panel, client):
    panel._dispatch(message("/start", user_id=STRANGER))

    assert client.sent == [], "постороннему нельзя даже подтверждать, что бот жив"
    assert client.edited == []


def test_stranger_button_press_is_ignored(panel, client):
    panel._dispatch(callback("backup", user_id=STRANGER))

    assert client.sent == []
    assert client.edited == []
    assert client.answered == []


def test_stranger_cannot_retry_an_order(panel, conn, client):
    stock.add_items(conn, "starter", ["a"])
    stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert panel.handle_update(callback("retry:1", user_id=STRANGER)) is None


def test_admin_gets_the_menu(panel, client):
    panel._dispatch(message("/start"))

    chat_id, text, keyboard = client.sent[0]
    assert chat_id == 555
    assert "Панель управления" in text
    assert keyboard is not None


def test_several_admins_are_supported(conn, tg_config, client, tmp_path):
    from dataclasses import replace

    cfg = replace(tg_config, telegram=replace(tg_config.telegram, admin_ids=(ADMIN, 222)))
    panel = TelegramPanel(conn, cfg, client, backup_dir=str(tmp_path))

    assert panel.handle_update(message("/start", user_id=222)) is not None


# --- экраны ------------------------------------------------------------------


def test_stock_screen_shows_counts_and_marks_unlimited(panel, conn):
    stock.add_items(conn, "starter", ["a", "b", "c"])

    reply = panel.handle_update(message("/stock"))

    assert "Стартовый аккаунт" in reply.text
    assert "<b>3</b>" in reply.text
    assert "Гайд — безлимитный" in reply.text


def test_empty_stock_is_marked_red(panel):
    assert "🔴" in panel.handle_update(message("/stock")).text


def test_low_stock_is_marked_yellow(panel, conn):
    stock.add_items(conn, "starter", ["a"])

    assert "🟡" in panel.handle_update(message("/stock")).text


def test_menu_highlights_pending_orders(panel, conn):
    stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")

    assert "Требуют внимания" in panel.handle_update(message("/menu")).text


def test_pending_screen_lists_orders_with_buttons(panel, conn):
    stock.claim_for_order(conn, order_id="77", buyer="bob", lot_id="starter")

    reply = panel.handle_update(callback("pending"))

    assert "#77" in reply.text
    buttons = [b["callback_data"] for row in reply.keyboard["inline_keyboard"] for b in row]
    assert "retry:77" in buttons
    assert "release:77" in buttons


def test_pending_screen_is_calm_when_empty(panel):
    assert "Всё выдано" in panel.handle_update(callback("pending")).text


def test_stats_screen(panel, conn):
    stock.add_items(conn, "starter", ["a"])
    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)

    reply = panel.handle_update(callback("stats"))

    assert "Выдано успешно: <b>1</b>" in reply.text


def test_listings_screen_reports_state(panel, conn):
    save_link(conn, "starter", "900", active=False)

    reply = panel.handle_update(callback("listings"))

    assert "снято" in reply.text
    assert "#900" in reply.text


def test_doctor_screen_runs_checks(panel):
    reply = panel.handle_update(callback("doctor"))

    assert "Проверка" in reply.text
    assert "Стартовый аккаунт" in reply.text


def test_backup_button_creates_a_copy(panel, tmp_path):
    reply = panel.handle_update(callback("backup"))

    assert "Копия готова" in reply.text
    assert list((tmp_path / "backups").glob("botfp-*.db"))


# --- действия ----------------------------------------------------------------


def test_retry_button_redelivers(panel, conn, transport):
    stock.add_items(conn, "starter", ["login:pass"])
    transport.fail_on_send = True
    panel.delivery.handle_order(
        __import__("botfp.transport", fromlist=["NewOrderEvent"]).NewOrderEvent(
            order_id="5", buyer="bob", lot_id="starter", chat_id="c"
        )
    )
    transport.fail_on_send = False

    reply = panel.handle_update(callback("retry:5"))

    assert "✓ Выдан" in reply.text
    assert stock.delivery_by_order(conn, "5").status == "delivered"


def test_release_asks_for_confirmation_first(panel, conn):
    stock.add_items(conn, "starter", ["a"])
    stock.claim_for_order(conn, order_id="9", buyer="bob", lot_id="starter")

    reply = panel.handle_update(callback("release:9"))

    assert "Вернуть товар" in reply.text
    assert stock.available_count(conn, "starter") == 0, "до подтверждения ничего не меняется"
    buttons = [b["callback_data"] for row in reply.keyboard["inline_keyboard"] for b in row]
    assert "release_yes:9" in buttons


def test_confirmed_release_returns_the_item(panel, conn):
    stock.add_items(conn, "starter", ["a"])
    stock.claim_for_order(conn, order_id="9", buyer="bob", lot_id="starter")

    reply = panel.handle_update(callback("release_yes:9"))

    assert "возвращён" in reply.text
    assert stock.available_count(conn, "starter") == 1


def test_refresh_listings_button(conn, tg_config, client, transport, tmp_path):
    from botfp.listings import RemoteLot

    api = FakeLotsAPI([RemoteLot("1001", 42, "Стартовый", True)])
    panel = TelegramPanel(
        conn, tg_config, client,
        delivery=DeliveryService(conn, tg_config, transport),
        listings=ListingManager(conn, tg_config, api),
        backup_dir=str(tmp_path),
    )
    save_link(conn, "starter", "1001", active=True)
    stock.add_items(conn, "starter", ["a"])
    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)  # склад опустел

    reply = panel.handle_update(callback("refresh_listings"))

    assert "Изменено объявлений: 1" in reply.text
    assert api.activity == [("1001", False)], "пустой лот снят с витрины"


def test_refresh_listings_survives_a_broken_api(conn, tg_config, client, tmp_path):
    """Панель не должна падать, если FunPay отдал неожиданное."""
    panel = TelegramPanel(
        conn, tg_config, client,
        listings=ListingManager(conn, tg_config, FakeLotsAPI()),
        backup_dir=str(tmp_path),
    )
    save_link(conn, "starter", "нет-такого", active=True)

    reply = panel.handle_update(callback("refresh_listings"))

    assert "Не получилось" in reply.text


def test_actions_without_funpay_say_so(conn, tg_config, client, tmp_path):
    panel = TelegramPanel(conn, tg_config, client, backup_dir=str(tmp_path))

    assert "нет связи" in panel.handle_update(callback("retry:1")).text.casefold()


# --- разметка и цикл ---------------------------------------------------------


def test_html_is_escaped_in_lot_titles(conn, tg_config, client, tmp_path):
    from dataclasses import replace

    cfg = replace(
        tg_config,
        lots={"x": LotConfig("x", "Аккаунт <b>VIP</b> & co", match=("Аккаунт",))},
    )
    panel = TelegramPanel(conn, cfg, client, backup_dir=str(tmp_path))

    text = panel.handle_update(message("/stock")).text

    assert "&lt;b&gt;VIP&lt;/b&gt;" in text
    assert "&amp; co" in text


def test_buyer_names_are_escaped(panel, conn):
    stock.claim_for_order(conn, order_id="1", buyer="<script>", lot_id="starter")

    assert "&lt;script&gt;" in panel.handle_update(callback("pending")).text


def test_esc_helper():
    assert esc("a < b & c") == "a &lt; b &amp; c"


def test_callback_edits_the_message_and_answers(panel, client):
    panel._dispatch(callback("stock"))

    assert client.answered[0][0] == "cb1"
    assert client.edited[0][1] == 42
    assert client.sent == [], "кнопка правит сообщение, а не плодит новые"


def test_offset_advances_past_processed_updates(panel, client):
    client.updates = [message("/start", update_id=10), message("/stock", update_id=11)]

    assert panel.run_once() == 2
    assert panel._offset == 12


def test_broken_update_does_not_stop_the_rest(panel, client, monkeypatch):
    calls = {"n": 0}
    original = panel.handle_update

    def flaky(update):
        calls["n"] += 1
        if calls["n"] == 1:
            raise RuntimeError("бум")
        return original(update)

    monkeypatch.setattr(panel, "handle_update", flaky)
    client.updates = [message("/start", update_id=1), message("/stock", update_id=2)]

    assert panel.run_once() == 2
    assert len(client.sent) == 1


def test_backlog_is_skipped_on_start(panel, client):
    client.updates = [message("/backup", update_id=50)]

    panel.skip_backlog()

    assert panel._offset == 51
    assert client.sent == [], "нажатия, сделанные без бота, не отыгрываются"


def test_long_messages_are_clipped(panel, conn, client):
    for i in range(300):
        stock.claim_for_order(conn, order_id=str(i), buyer=f"buyer{i}", lot_id="starter")

    panel._dispatch(message("/pending"))

    assert len(client.sent[0][1]) <= 3900


# --- уведомления -------------------------------------------------------------


def test_notifier_writes_to_every_admin(client):
    notifier = TelegramNotifier(client, TelegramConfig(token="t", admin_ids=(111, 222)))

    notifier.notify("товар кончился")

    assert [chat for chat, _, _ in client.sent] == [111, 222]


def test_notifier_escapes_html(client):
    notifier = TelegramNotifier(client, TelegramConfig(token="t", admin_ids=(111,)))

    notifier.notify("лот <b>X</b> кончился")

    assert "&lt;b&gt;" in client.sent[0][1]
