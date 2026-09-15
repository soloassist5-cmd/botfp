"""Конфигурация: сопоставление лотов и понятные ошибки."""

from __future__ import annotations

import pytest

from botfp import config as config_module
from botfp.config import ConfigError

BASE = """
[funpay]
golden_key = "key"

[bot]
admins = ["seller"]
db_path = "test.db"

[[lots]]
id = "starter"
title = "Стартовый аккаунт"
"""


def write(tmp_path, text):
    path = tmp_path / "config.toml"
    path.write_text(text, encoding="utf-8")
    return path


def test_loads_minimal_config(tmp_path):
    cfg = config_module.load(write(tmp_path, BASE))

    assert cfg.admins == ("seller",)
    assert cfg.lot("starter").title == "Стартовый аккаунт"
    assert cfg.lot("starter").match == ("Стартовый аккаунт",)


def test_env_variable_overrides_file_key(tmp_path, monkeypatch):
    monkeypatch.setenv("FUNPAY_GOLDEN_KEY", "from-env")

    assert config_module.load(write(tmp_path, BASE)).golden_key == "from-env"


def test_missing_file_names_the_path(tmp_path):
    with pytest.raises(ConfigError, match="не найден"):
        config_module.load(tmp_path / "nope.toml")


def test_missing_golden_key_is_rejected(tmp_path):
    with pytest.raises(ConfigError, match="golden_key"):
        config_module.load(write(tmp_path, BASE.replace('golden_key = "key"', "")))


def test_console_mode_may_skip_the_key(tmp_path):
    cfg = config_module.load(
        write(tmp_path, BASE.replace('golden_key = "key"', "")), require_golden_key=False
    )

    assert cfg.golden_key == ""


def test_no_admins_is_rejected(tmp_path):
    with pytest.raises(ConfigError, match="админ"):
        config_module.load(write(tmp_path, BASE.replace('admins = ["seller"]', "admins = []")))


def test_no_lots_is_rejected(tmp_path):
    text = BASE.split("[[lots]]")[0]
    with pytest.raises(ConfigError, match="лот"):
        config_module.load(write(tmp_path, text))


def test_duplicate_lot_id_is_rejected(tmp_path):
    with pytest.raises(ConfigError, match="дважды"):
        config_module.load(write(tmp_path, BASE + '\n[[lots]]\nid = "starter"\ntitle = "X"\n'))


def test_too_fast_polling_is_rejected(tmp_path):
    with pytest.raises(ConfigError, match="429"):
        config_module.load(write(tmp_path, BASE.replace("db_path", "poll_interval = 0.5\ndb_path")))


def test_is_admin_ignores_case(tmp_path):
    cfg = config_module.load(write(tmp_path, BASE))

    assert cfg.is_admin("SELLER") is True
    assert cfg.is_admin("bob") is False


def test_lot_matching_prefers_the_longest_rule(config):
    """«Стартовый аккаунт премиум» не должен уходить в лот «Стартовый аккаунт»."""
    assert config.lot_for_description("Куплен Стартовый аккаунт премиум").lot_id == "premium"
    assert config.lot_for_description("Куплен Стартовый аккаунт").lot_id == "starter"


def test_unmatched_description_returns_none(config):
    assert config.lot_for_description("Что-то совсем другое") is None


def test_matching_ignores_case(config):
    assert config.lot_for_description("КУПЛЕН ПРЕМИУМ-АККАУНТ").lot_id == "premium"


# --- типы лотов и объявления -------------------------------------------------

UNLIMITED = """
[funpay]
golden_key = "key"

[bot]
admins = ["seller"]

[[lots]]
id = "guide"
title = "Гайд"
kind = "unlimited"
payload = "https://example.com/guide"
"""


def test_unlimited_lot_carries_its_payload(tmp_path):
    lot = config_module.load(write(tmp_path, UNLIMITED)).lot("guide")

    assert lot.is_unlimited is True
    assert lot.payload == "https://example.com/guide"


def test_unlimited_lot_without_payload_is_rejected(tmp_path):
    text = UNLIMITED.replace('payload = "https://example.com/guide"', "")
    with pytest.raises(ConfigError, match="нечего выдавать"):
        config_module.load(write(tmp_path, text))


def test_unique_lot_with_inline_payload_is_rejected(tmp_path):
    """Товар уникальных лотов грузится на склад, а не пишется в конфиг."""
    text = UNLIMITED.replace('kind = "unlimited"\n', "")
    with pytest.raises(ConfigError, match="stock add"):
        config_module.load(write(tmp_path, text))


def test_unknown_kind_is_rejected(tmp_path):
    text = UNLIMITED.replace('kind = "unlimited"', 'kind = "магический"')
    with pytest.raises(ConfigError, match="неизвестный kind"):
        config_module.load(write(tmp_path, text))


def test_payload_can_come_from_a_file(tmp_path):
    (tmp_path / "guide.txt").write_text("содержимое гайда\n", encoding="utf-8")
    text = UNLIMITED.replace(
        'payload = "https://example.com/guide"', 'payload_file = "guide.txt"'
    )

    assert config_module.load(write(tmp_path, text)).lot("guide").payload == "содержимое гайда"


def test_payload_and_payload_file_together_are_rejected(tmp_path):
    text = UNLIMITED + '\npayload_file = "guide.txt"\n'
    with pytest.raises(ConfigError, match="либо payload"):
        config_module.load(write(tmp_path, text))


def test_missing_payload_file_is_reported(tmp_path):
    text = UNLIMITED.replace(
        'payload = "https://example.com/guide"', 'payload_file = "нет-такого.txt"'
    )
    with pytest.raises(ConfigError, match="не найден"):
        config_module.load(write(tmp_path, text))


LISTING = BASE + """
[lots.listing]
node_id = 1234
price = 1.0
short_description = "Стартовый аккаунт за 1 рубль"
description = "Полное описание"
"""


def test_listing_section_is_parsed(tmp_path):
    cfg = config_module.load(write(tmp_path, LISTING))
    listing = cfg.lot("starter").listing

    assert listing.node_id == 1234
    assert listing.price == 1.0
    assert listing.auto_deactivate is True
    assert cfg.listable_lots == (cfg.lot("starter"),)


def test_lot_without_listing_is_not_listable(tmp_path):
    assert config_module.load(write(tmp_path, BASE)).listable_lots == ()


def test_listing_without_node_id_is_rejected(tmp_path):
    with pytest.raises(ConfigError, match="node_id"):
        config_module.load(write(tmp_path, LISTING.replace("node_id = 1234", "")))


def test_zero_price_is_rejected(tmp_path):
    with pytest.raises(ConfigError, match="price"):
        config_module.load(write(tmp_path, LISTING.replace("price = 1.0", "price = 0")))


def test_unlimited_lot_is_never_auto_deactivated(tmp_path):
    text = UNLIMITED + """
[lots.listing]
node_id = 1234
price = 1.0
auto_deactivate = true
"""
    listing = config_module.load(write(tmp_path, text)).lot("guide").listing

    assert listing.auto_deactivate is False, "безлимитный товар не кончается"


def test_too_fast_listing_updates_are_rejected(tmp_path):
    text = LISTING.replace('admins = ["seller"]', 'admins = ["seller"]\nlisting_delay = 0.1')
    with pytest.raises(ConfigError, match="флуд"):
        config_module.load(write(tmp_path, text))
