"""Синхронизация витрины: главное — не размножить лоты."""

from __future__ import annotations

from dataclasses import replace

import pytest

from botfp import stock
from botfp.config import Config, ListingConfig, LotConfig
from botfp.listings import ListingManager, ListingSpec, RemoteLot, get_link, save_link
from botfp.transport import TransportError


class FakeLotsAPI:
    """Витрина FunPay в памяти."""

    def __init__(self, existing: list[RemoteLot] | None = None):
        self.lots = {lot.funpay_lot_id: lot for lot in (existing or [])}
        self.created: list[ListingSpec] = []
        self.updated: list[tuple[str, ListingSpec]] = []
        self.activity: list[tuple[str, bool]] = []
        self.fail_on: set[str] = set()
        self._next_id = 1000

    def list_my_lots(self):
        if "list" in self.fail_on:
            raise TransportError("список недоступен")
        return list(self.lots.values())

    def create_lot(self, spec: ListingSpec) -> str:
        if "create" in self.fail_on:
            raise TransportError("создание отклонено")
        self._next_id += 1
        lot_id = str(self._next_id)
        self.lots[lot_id] = RemoteLot(lot_id, spec.node_id, spec.title, spec.active)
        self.created.append(spec)
        return lot_id

    def update_lot(self, funpay_lot_id: str, spec: ListingSpec) -> None:
        if "update" in self.fail_on:
            raise TransportError("обновление отклонено")
        self.updated.append((funpay_lot_id, spec))

    def set_lot_active(self, funpay_lot_id: str, active: bool) -> None:
        if "toggle" in self.fail_on:
            raise TransportError("переключение отклонено")
        self.activity.append((funpay_lot_id, active))
        lot = self.lots[funpay_lot_id]
        self.lots[funpay_lot_id] = replace(lot, active=active)


def make_config(**overrides) -> Config:
    listing = ListingConfig(
        node_id=42, price=1.0, short_description="Стартовый аккаунт", description="Описание"
    )
    lots = {
        "starter": LotConfig(
            lot_id="starter",
            title="Стартовый аккаунт",
            match=("Стартовый аккаунт",),
            listing=listing,
        )
    }
    base = dict(
        golden_key="k",
        user_agent="u",
        db_path=":memory:",
        admins=("seller",),
        lots=lots,
        listing_delay=0.0,
    )
    base.update(overrides)
    return Config(**base)


@pytest.fixture()
def api() -> FakeLotsAPI:
    return FakeLotsAPI()


def manager(conn, api, cfg=None) -> ListingManager:
    return ListingManager(conn, cfg or make_config(), api)


def test_sync_creates_a_missing_listing(conn, api):
    result = manager(conn, api).sync()

    assert result.created == ["starter"]
    assert api.created[0].title == "Стартовый аккаунт"
    assert api.created[0].price == 1.0
    assert get_link(conn, "starter").funpay_lot_id == "1001"


def test_second_sync_updates_instead_of_duplicating(conn, api):
    mgr = manager(conn, api)
    mgr.sync()
    result = mgr.sync()

    assert result.created == []
    assert result.updated == ["starter"]
    assert len(api.created) == 1, "второй прогон не должен создавать второй лот"
    assert len(api.lots) == 1


def test_existing_listing_is_adopted_not_duplicated(conn, api):
    """База потеряна, а лот на витрине есть — привязываемся к нему."""
    api.lots["777"] = RemoteLot("777", 42, "Стартовый аккаунт", True)

    result = manager(conn, api).sync()

    assert result.adopted == ["starter"]
    assert api.created == []
    assert get_link(conn, "starter").funpay_lot_id == "777"


def test_adoption_ignores_same_title_in_another_subcategory(conn, api):
    api.lots["777"] = RemoteLot("777", 99, "Стартовый аккаунт", True)

    result = manager(conn, api).sync()

    assert result.created == ["starter"]
    assert result.adopted == []


def test_adoption_ignores_case_and_spacing(conn, api):
    api.lots["777"] = RemoteLot("777", 42, "  стартовый АККАУНТ ", True)

    assert manager(conn, api).sync().adopted == ["starter"]


def test_dry_run_changes_nothing(conn, api):
    result = manager(conn, api).sync(dry_run=True)

    assert result.created == ["starter"]
    assert api.created == []
    assert api.lots == {}
    assert get_link(conn, "starter") is None


def test_create_failure_is_recorded_not_raised(conn, api):
    api.fail_on.add("create")

    result = manager(conn, api).sync()

    assert result.ok is False
    assert result.failed[0][0] == "starter"
    link = get_link(conn, "starter")
    assert link.funpay_lot_id is None
    assert "отклонено" in link.last_error


def test_failure_on_one_lot_does_not_block_others(conn, api):
    cfg = make_config()
    second = LotConfig(
        lot_id="premium",
        title="Премиум",
        match=("Премиум",),
        listing=ListingConfig(node_id=42, price=2.0, short_description="Премиум"),
    )
    cfg = replace(cfg, lots={**cfg.lots, "premium": second})

    calls = {"n": 0}
    original = api.create_lot

    def flaky(spec):
        calls["n"] += 1
        if calls["n"] == 1:
            raise TransportError("первый упал")
        return original(spec)

    api.create_lot = flaky
    result = manager(conn, api, cfg).sync()

    assert [f[0] for f in result.failed] == ["starter"]
    assert result.created == ["premium"]


def test_unreachable_funpay_reports_one_error(conn, api):
    api.fail_on.add("list")

    result = manager(conn, api).sync()

    assert result.ok is False
    assert result.failed == [("*", "список недоступен")]


def test_listing_is_created_inactive_when_stock_is_empty(conn, api):
    """Пустой склад — лот сразу не на витрине, чтобы не продать воздух."""
    result = manager(conn, api).sync()

    assert api.created[0].active is False
    assert get_link(conn, "starter").active is False


def test_listing_is_active_when_stock_exists(conn, api):
    stock.add_items(conn, "starter", ["a"])

    manager(conn, api).sync()

    assert api.created[0].active is True


def test_empty_stock_pulls_the_listing_from_the_shelf(conn, api):
    stock.add_items(conn, "starter", ["only"])
    mgr = manager(conn, api)
    mgr.sync()
    api.activity.clear()

    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)
    changed = mgr.refresh_availability()

    assert changed == ["starter"]
    assert api.activity == [("1001", False)]
    assert get_link(conn, "starter").active is False


def test_restock_returns_the_listing(conn, api):
    mgr = manager(conn, api)
    mgr.sync()  # склад пуст -> лот неактивен
    api.activity.clear()

    stock.add_items(conn, "starter", ["fresh"])
    changed = mgr.refresh_availability()

    assert changed == ["starter"]
    assert api.activity == [("1001", True)]


def test_refresh_is_idempotent(conn, api):
    stock.add_items(conn, "starter", ["a"])
    mgr = manager(conn, api)
    mgr.sync()
    api.activity.clear()

    assert mgr.refresh_availability() == []
    assert api.activity == [], "без изменения состояния сеть дёргать незачем"


def test_toggle_failure_leaves_state_untouched(conn, api):
    stock.add_items(conn, "starter", ["only"])
    mgr = manager(conn, api)
    mgr.sync()
    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)
    api.fail_on.add("toggle")

    assert mgr.refresh_availability() == []
    assert get_link(conn, "starter").active is True, "состояние в базе не должно расходиться"


def test_unlimited_listing_is_never_deactivated(conn, api):
    cfg = make_config(
        lots={
            "guide": LotConfig(
                lot_id="guide",
                title="Гайд",
                kind="unlimited",
                payload="https://example.com/guide",
                match=("Гайд",),
                listing=ListingConfig(node_id=42, price=1.0, short_description="Гайд"),
            )
        }
    )
    mgr = manager(conn, api, cfg)
    mgr.sync()

    assert api.created[0].active is True, "безлимитный товар не кончается"
    assert mgr.refresh_availability() == []


def test_auto_deactivate_off_keeps_the_listing_up(conn, api):
    cfg = make_config()
    lot = cfg.lots["starter"]
    cfg = replace(
        cfg,
        lots={"starter": replace(lot, listing=replace(lot.listing, auto_deactivate=False))},
    )
    mgr = manager(conn, api, cfg)
    mgr.sync()

    assert api.created[0].active is True
    assert mgr.refresh_availability() == []


def test_lots_without_listing_section_are_skipped(conn, api):
    cfg = make_config(
        lots={"bare": LotConfig(lot_id="bare", title="Без объявления", match=("Без",))}
    )

    result = manager(conn, api, cfg).sync()

    assert result.created == []
    assert api.created == []
