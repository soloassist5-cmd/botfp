"""Подключение к SQLite и схема базы."""

from __future__ import annotations

import contextlib
import os
import sqlite3
from collections.abc import Iterator
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 2

_SCHEMA = """
CREATE TABLE IF NOT EXISTS stock_items (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    lot_id        TEXT    NOT NULL,
    payload       TEXT    NOT NULL,
    payload_hash  TEXT    NOT NULL,
    status        TEXT    NOT NULL CHECK (status IN ('available', 'reserved', 'issued')),
    added_at      TEXT    NOT NULL,
    reserved_at   TEXT,
    issued_at     TEXT,
    order_id      TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_lot_payload
    ON stock_items (lot_id, payload_hash);

CREATE INDEX IF NOT EXISTS ix_stock_pick
    ON stock_items (lot_id, status, id);

CREATE TABLE IF NOT EXISTS deliveries (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id       TEXT    NOT NULL UNIQUE,
    buyer          TEXT    NOT NULL,
    chat_id        TEXT,
    lot_id         TEXT    NOT NULL,
    stock_item_id  INTEGER REFERENCES stock_items (id),
    status         TEXT    NOT NULL
                   CHECK (status IN ('sending', 'delivered', 'out_of_stock', 'failed')),
    -- Что именно ушло покупателю у безлимитных лотов: у них нет строки на
    -- складе, а «!повтор» и разбор спорной выдачи требуют точного текста.
    payload_snapshot TEXT,
    attempts       INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT    NOT NULL,
    updated_at     TEXT    NOT NULL,
    error          TEXT
);

CREATE INDEX IF NOT EXISTS ix_deliveries_buyer
    ON deliveries (buyer, status, id);

CREATE INDEX IF NOT EXISTS ix_deliveries_status
    ON deliveries (status, id);

-- Связь лота из конфига с объявлением на FunPay: чтобы повторная
-- синхронизация обновляла существующее объявление, а не плодила дубликаты.
CREATE TABLE IF NOT EXISTS lot_links (
    lot_id         TEXT PRIMARY KEY,
    funpay_lot_id  TEXT,
    active         INTEGER NOT NULL DEFAULT 1,
    synced_at      TEXT,
    last_error     TEXT
);

-- Чаты, в которых бот уже поздоровался: приветствие шлём ровно один раз,
-- чтобы не влезать в живую переписку продавца с покупателем.
CREATE TABLE IF NOT EXISTS greeted_chats (
    chat_id     TEXT PRIMARY KEY,
    greeted_at  TEXT NOT NULL
);

-- Защита от повторной обработки одного и того же события FunPay
-- (при переподключении рантайм отдаёт часть событий заново).
CREATE TABLE IF NOT EXISTS seen_events (
    event_key  TEXT PRIMARY KEY,
    seen_at    TEXT NOT NULL
);
"""


def utcnow() -> str:
    """Текущее время в ISO-8601 (UTC). Одинаковый формат по всей базе."""
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def connect(path: str | Path) -> sqlite3.Connection:
    """Открывает базу, создаёт схему и возвращает соединение.

    ``isolation_level=None`` отключает неявные транзакции sqlite3 — все
    транзакции открываются явно через :func:`transaction`.
    """
    path = Path(path)
    if path.parent != Path(""):
        path.parent.mkdir(parents=True, exist_ok=True)

    is_new = not path.exists() or path.stat().st_size == 0
    conn = sqlite3.connect(str(path), isolation_level=None, timeout=30.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA busy_timeout = 30000")
    conn.execute("PRAGMA synchronous = FULL")
    conn.executescript(_SCHEMA)
    _migrate(conn)
    conn.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")

    if is_new and str(path) != ":memory:":
        # В базе лежат выданные товары в открытом виде — закрываем от чужих.
        with contextlib.suppress(OSError):
            os.chmod(path, 0o600)

    return conn


def _migrate(conn: sqlite3.Connection) -> None:
    """Догоняет схему на базах, созданных прежними версиями бота.

    ``CREATE TABLE IF NOT EXISTS`` создаёт недостающие таблицы, но не
    добавляет колонки в уже существующие — их добавляем здесь.
    """
    _ensure_column(conn, "deliveries", "payload_snapshot", "TEXT")


def _ensure_column(
    conn: sqlite3.Connection, table: str, column: str, ddl: str
) -> None:
    existing = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
    if column not in existing:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {ddl}")


@contextlib.contextmanager
def transaction(conn: sqlite3.Connection) -> Iterator[sqlite3.Connection]:
    """Транзакция на запись.

    ``BEGIN IMMEDIATE`` берёт блокировку записи сразу, а не на первом UPDATE,
    поэтому два процесса не могут выбрать один и тот же товар со склада.
    """
    conn.execute("BEGIN IMMEDIATE")
    try:
        yield conn
    except BaseException:
        conn.execute("ROLLBACK")
        raise
    conn.execute("COMMIT")


def mark_event_seen(conn: sqlite3.Connection, event_key: str) -> bool:
    """Помечает событие обработанным.

    Возвращает ``True``, если событие новое, и ``False``, если оно уже
    встречалось — тогда обработку надо пропустить.
    """
    cur = conn.execute(
        "INSERT OR IGNORE INTO seen_events (event_key, seen_at) VALUES (?, ?)",
        (event_key, utcnow()),
    )
    return cur.rowcount > 0


def mark_greeted(conn: sqlite3.Connection, chat_id: str) -> bool:
    """Возвращает ``True``, если в этом чате бот здоровается впервые."""
    cur = conn.execute(
        "INSERT OR IGNORE INTO greeted_chats (chat_id, greeted_at) VALUES (?, ?)",
        (str(chat_id), utcnow()),
    )
    return cur.rowcount > 0


def prune_events(conn: sqlite3.Connection, keep: int = 5000) -> int:
    """Оставляет только последние ``keep`` записей о событиях."""
    cur = conn.execute(
        """
        DELETE FROM seen_events
         WHERE rowid NOT IN (
               SELECT rowid FROM seen_events ORDER BY rowid DESC LIMIT ?
         )
        """,
        (keep,),
    )
    return cur.rowcount
