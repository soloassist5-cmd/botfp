"""Предполётная проверка."""

from __future__ import annotations

from dataclasses import replace
from pathlib import Path

import pytest

from botfp import doctor, stock
from botfp.config import Config, ListingConfig, LotConfig
from botfp.doctor import FAIL, OK, WARN
from botfp.listings import save_link
from botfp.transport import TransportError
from tests.test_listings import FakeLotsAPI


def levels(checks, needle: str) -> list[str]:
    return [c.level for c in checks if needle in c.title]


@pytest.fixture()
def cfg_path(tmp_path) -> Path:
    path = tmp_path / "config.toml"
    path.write_text("", encoding="utf-8")
    path.chmod(0o600)
    return path


def test_empty_stock_is_a_warning_not_a_failure(conn, config, cfg_path):
    checks = doctor.run_checks(conn, config, cfg_path)

    assert levels(checks, "Стартовый аккаунт") == [WARN]
    assert doctor.worst_level(checks) == WARN


def test_stocked_lot_passes(conn, config, cfg_path):
    stock.add_items(conn, "starter", [f"a{i}" for i in range(10)])
    stock.add_items(conn, "premium", [f"p{i}" for i in range(10)])

    checks = doctor.run_checks(conn, config, cfg_path)

    assert levels(checks, "Стартовый аккаунт") == [OK]


def test_low_stock_is_flagged(conn, config, cfg_path):
    stock.add_items(conn, "starter", ["a"])  # порог в фикстуре — 2

    assert levels(doctor.run_checks(conn, config, cfg_path), "Стартовый аккаунт") == [WARN]


def test_unlimited_lot_never_looks_empty(conn, cfg_path):
    cfg = Config(
        golden_key="k", user_agent="u", db_path=":memory:", admins=("seller",),
        lots={"guide": LotConfig("guide", "Гайд", kind="unlimited",
                                 payload="https://x", match=("Гайд",))},
    )

    assert levels(doctor.run_checks(conn, cfg, cfg_path), "Гайд") == [OK]


def test_golden_key_in_file_is_flagged(conn, config, cfg_path, monkeypatch):
    monkeypatch.delenv("FUNPAY_GOLDEN_KEY", raising=False)

    assert levels(doctor.run_checks(conn, config, cfg_path), "golden_key") == [WARN]


def test_golden_key_from_env_passes(conn, config, cfg_path, monkeypatch):
    monkeypatch.setenv("FUNPAY_GOLDEN_KEY", "secret")

    assert levels(doctor.run_checks(conn, config, cfg_path), "golden_key") == [OK]


def test_loose_config_permissions_are_flagged(conn, config, cfg_path):
    cfg_path.chmod(0o644)

    assert levels(doctor.run_checks(conn, config, cfg_path), "config.toml") == [WARN]


def test_pending_deliveries_are_surfaced(conn, config, cfg_path):
    stock.claim_for_order(conn, order_id="7", buyer="bob", lot_id="starter")

    assert levels(doctor.run_checks(conn, config, cfg_path), "незавершённых") == [WARN]


def test_overlapping_match_rules_are_a_blocker(conn, cfg_path):
    """Короткое правило одного лота перехватывает заказ другого."""
    cfg = Config(
        golden_key="k", user_agent="u", db_path=":memory:", admins=("seller",),
        lots={
            "premium": LotConfig("premium", "Премиум", match=("Premium",)),
            "greedy": LotConfig("greedy", "Жадный", match=("rem", "очень длинное правило")),
        },
    )

    checks = doctor.run_checks(conn, cfg, cfg_path)

    assert doctor.worst_level(checks) == FAIL
    assert any("перехватывает" in c.title for c in checks)


def test_clean_match_rules_pass(conn, config, cfg_path):
    assert not any("перехватывает" in c.title for c in doctor.run_checks(conn, config, cfg_path))


# --- проверки по сети --------------------------------------------------------


def listing_config() -> Config:
    return Config(
        golden_key="k", user_agent="u", db_path=":memory:", admins=("seller",),
        listing_delay=0.0,
        lots={
            "starter": LotConfig(
                "starter", "Стартовый аккаунт", match=("Стартовый аккаунт",),
                listing=ListingConfig(node_id=42, price=1.0, short_description="Стартовый"),
            )
        },
    )


def test_listing_not_created_yet_is_a_warning(conn, cfg_path):
    """Объявления ещё нет — это повод запустить sync, а не блокер."""
    checks = doctor.run_checks(conn, listing_config(), cfg_path, lots_api=FakeLotsAPI())

    assert doctor.worst_level(checks) != FAIL
    assert any("ещё не создано" in c.title for c in checks)


def test_template_present_but_listing_not_created_is_a_warning(conn, cfg_path):
    from botfp.listings import RemoteLot

    api = FakeLotsAPI([RemoteLot("900", 42, "Другой лот", True)])

    checks = doctor.run_checks(conn, listing_config(), cfg_path, lots_api=api)

    assert levels(checks, "объявление ещё не создано") == [WARN]


def test_created_listing_reports_its_state(conn, cfg_path):
    from botfp.listings import RemoteLot

    api = FakeLotsAPI([RemoteLot("900", 42, "Стартовый", True)])
    save_link(conn, "starter", "900", active=True)

    checks = doctor.run_checks(conn, listing_config(), cfg_path, lots_api=api)

    assert any("на витрине" in c.title for c in checks)
    assert doctor.worst_level(checks) != FAIL


def test_unreachable_funpay_is_a_blocker(conn, cfg_path):
    api = FakeLotsAPI()
    api.fail_on.add("list")

    checks = doctor.run_checks(conn, listing_config(), cfg_path, lots_api=api)

    assert doctor.worst_level(checks) == FAIL
