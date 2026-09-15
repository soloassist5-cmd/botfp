"""Резервное копирование базы."""

from __future__ import annotations

import sqlite3
import stat as stat_module

from botfp import backup, stock


def test_backup_contains_the_data(conn, tmp_path):
    stock.add_items(conn, "starter", ["a", "b"])
    claim = stock.claim_for_order(conn, order_id="1", buyer="bob", lot_id="starter")
    stock.mark_delivered(conn, claim.delivery.id)

    result = backup.create(conn, tmp_path / "backups")

    copy = sqlite3.connect(result.path)
    assert copy.execute("SELECT COUNT(*) FROM stock_items").fetchone()[0] == 2
    assert copy.execute("SELECT COUNT(*) FROM deliveries").fetchone()[0] == 1
    assert copy.execute(
        "SELECT payload FROM stock_items WHERE status = 'issued'"
    ).fetchone()[0] == "a"


def test_backup_is_readable_only_by_owner(conn, tmp_path):
    result = backup.create(conn, tmp_path / "backups")

    mode = stat_module.S_IMODE(result.path.stat().st_mode)
    assert mode & 0o077 == 0, "в копии лежит товар в открытом виде"


def test_backup_directory_is_created_and_locked_down(conn, tmp_path):
    dest = tmp_path / "nested" / "backups"

    backup.create(conn, dest)

    assert dest.exists()
    assert stat_module.S_IMODE(dest.stat().st_mode) & 0o077 == 0


def test_same_second_backups_do_not_overwrite(conn, tmp_path):
    first = backup.create(conn, tmp_path / "b")
    second = backup.create(conn, tmp_path / "b")

    assert first.path != second.path
    assert first.path.exists() and second.path.exists()


def test_old_copies_are_pruned(conn, tmp_path):
    dest = tmp_path / "b"
    for _ in range(5):
        backup.create(conn, dest, keep=3)

    assert len(list(dest.glob("botfp-*.db"))) == 3


def test_prune_keeps_the_newest(conn, tmp_path):
    dest = tmp_path / "b"
    dest.mkdir()
    for stamp in ("20260101-000000", "20260102-000000", "20260103-000000"):
        (dest / f"botfp-{stamp}.db").write_bytes(b"x")

    removed = backup.prune(dest, keep=1)

    assert [p.name for p in removed] == [
        "botfp-20260102-000000.db",
        "botfp-20260101-000000.db",
    ]
    assert [p.name for p in dest.glob("botfp-*.db")] == ["botfp-20260103-000000.db"]


def test_prune_ignores_foreign_files(conn, tmp_path):
    dest = tmp_path / "b"
    dest.mkdir()
    (dest / "важное.txt").write_text("не трогать")
    for stamp in ("20260101-000000", "20260102-000000"):
        (dest / f"botfp-{stamp}.db").write_bytes(b"x")

    backup.prune(dest, keep=1)

    assert (dest / "важное.txt").exists()


def test_keep_zero_prunes_nothing(conn, tmp_path):
    dest = tmp_path / "b"
    backup.create(conn, dest)

    assert backup.prune(dest, keep=0) == []
    assert len(list(dest.glob("botfp-*.db"))) == 1


def test_backup_works_while_the_bot_writes(conn, tmp_path):
    """Копию можно снимать не останавливая бота."""
    stock.add_items(conn, "starter", ["a"])
    result = backup.create(conn, tmp_path / "b")
    stock.add_items(conn, "starter", ["b"])  # запись уже после снятия копии

    copy = sqlite3.connect(result.path)
    assert copy.execute("SELECT COUNT(*) FROM stock_items").fetchone()[0] == 1
    assert stock.available_count(conn, "starter") == 2
