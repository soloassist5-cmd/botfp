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
