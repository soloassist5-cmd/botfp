"""Резервное копирование базы.

В базе лежит товар и история выдач: потеря означает, что вы больше не знаете,
кому что уже отправили, и рискуете выдать один товар дважды.

Копия снимается штатным механизмом SQLite (``Connection.backup``), поэтому
её можно делать на работающем боте — в отличие от простого ``cp``, который
на активной базе даёт битый файл.
"""

from __future__ import annotations

import os
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

PREFIX = "botfp-"
SUFFIX = ".db"


@dataclass(frozen=True)
class BackupResult:
    path: Path
    size: int
    removed: list[Path]


def create(
    conn: sqlite3.Connection, dest_dir: str | Path, *, keep: int = 48
) -> BackupResult:
    """Снимает копию базы и подчищает старые, оставляя последние ``keep``."""
    dest_dir = Path(dest_dir).expanduser()
    dest_dir.mkdir(parents=True, exist_ok=True)
    os.chmod(dest_dir, 0o700)

    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    target = dest_dir / f"{PREFIX}{stamp}{SUFFIX}"

    # Если за ту же секунду уже была копия — не затираем её.
    counter = 1
    while target.exists():
        target = dest_dir / f"{PREFIX}{stamp}-{counter}{SUFFIX}"
        counter += 1

    with sqlite3.connect(str(target)) as backup_conn:
        conn.backup(backup_conn)
    os.chmod(target, 0o600)

    return BackupResult(
        path=target, size=target.stat().st_size, removed=prune(dest_dir, keep=keep)
    )


def prune(dest_dir: str | Path, *, keep: int) -> list[Path]:
    """Удаляет всё, кроме последних ``keep`` копий."""
    dest_dir = Path(dest_dir)
    if keep < 1:
        return []

    copies = sorted(
        dest_dir.glob(f"{PREFIX}*{SUFFIX}"), key=lambda p: p.name, reverse=True
    )
    removed = []
    for old in copies[keep:]:
        old.unlink()
        removed.append(old)
    return removed
