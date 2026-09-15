from __future__ import annotations

import sqlite3

import pytest

from botfp import db
from botfp.config import Config, LotConfig
from botfp.transport import RecordingTransport


@pytest.fixture()
def conn() -> sqlite3.Connection:
    connection = db.connect(":memory:")
    yield connection
    connection.close()


@pytest.fixture()
def config() -> Config:
    return Config(
        golden_key="test-key",
        user_agent="test-agent",
        db_path=":memory:",
        admins=("seller",),
        lots={
            "starter": LotConfig(
                lot_id="starter",
                title="Стартовый аккаунт",
                instructions="Смените пароль.",
                match=("Стартовый аккаунт",),
            ),
            "premium": LotConfig(
                lot_id="premium",
                title="Премиум-аккаунт",
                match=("Премиум-аккаунт", "Стартовый аккаунт премиум"),
            ),
        },
        poll_interval=4.0,
        low_stock_threshold=2,
        ask_for_review=True,
    )


@pytest.fixture()
def transport() -> RecordingTransport:
    return RecordingTransport()
