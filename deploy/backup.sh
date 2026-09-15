#!/bin/sh
# Резервная копия базы бота.
#
# Обёртка над `botfp backup`: своего sqlite3 в системе может не быть, а Python
# у бота есть всегда. Копия снимается штатным механизмом SQLite, поэтому
# останавливать бота не нужно.
#
# Раз в час через cron:
#   0 * * * * /opt/botfp/deploy/backup.sh >> /var/log/botfp-backup.log 2>&1
set -eu

ROOT="${BOTFP_ROOT:-/opt/botfp}"
PYTHON="${BOTFP_PYTHON:-$ROOT/.venv/bin/python}"
CONFIG="${BOTFP_CONFIG:-$ROOT/config.toml}"
DEST="${BOTFP_BACKUP_DIR:-$ROOT/backups}"
KEEP="${BOTFP_BACKUP_KEEP:-48}"

exec "$PYTHON" -m botfp -c "$CONFIG" backup --dir "$DEST" --keep "$KEEP"
