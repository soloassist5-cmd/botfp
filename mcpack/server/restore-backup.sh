#!/usr/bin/env bash
# =============================================================================
#  Откат мира LS City Life к резервной копии (Linux / macOS)
#
#  Копии делает сам сервер: раз в час в папку backups/ (настройка [backup]
#  в config/citylife-common.toml), вручную — командой /citylife backup.
#
#  Сначала останови сервер (stop), потом:
#    ./restore-backup.sh            — выбрать копию из списка
#    ./restore-backup.sh файл.zip   — вернуть конкретную
#
#  Текущий мир не удаляется: он переименовывается в <мир>-before-restore-<дата>.
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

WORLD="$(grep -E '^level-name=' server.properties 2>/dev/null | cut -d= -f2- || true)"
WORLD="${WORLD:-world}"

if [[ $# -ge 1 ]]; then
  ZIP="$1"
else
  mapfile -t LIST < <(ls -1 backups/*.zip 2>/dev/null | sort -r)
  if [[ ${#LIST[@]} -eq 0 ]]; then
    echo "В папке backups/ нет копий." >&2
    exit 1
  fi
  for i in "${!LIST[@]}"; do
    echo "  [$((i + 1))] $(basename "${LIST[$i]}")"
  done
  read -rp "Какую копию вернуть? Номер (Enter — самая свежая): " N
  N="${N:-1}"
  ZIP="${LIST[$((N - 1))]}"
fi
[[ -f "$ZIP" ]] || { echo "Нет файла: $ZIP" >&2; exit 1; }

read -rp "Сервер остановлен? Вернуть $(basename "$ZIP") вместо мира «$WORLD»? [y/N] " OK
[[ "$OK" == "y" || "$OK" == "Y" || "$OK" == "д" ]] || { echo "Отменено."; exit 0; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -q "$ZIP" -d "$TMP"
SRC="$(find "$TMP" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
[[ -f "$SRC/level.dat" ]] || { echo "В архиве нет мира (level.dat)." >&2; exit 1; }

if [[ -d "$WORLD" ]]; then
  OLD="$WORLD-before-restore-$(date +%Y-%m-%d_%H-%M-%S)"
  mv "$WORLD" "$OLD"
  echo "Текущий мир сохранён как $OLD"
fi
mv "$SRC" "$WORLD"
echo "Готово: мир «$WORLD» возвращён из $(basename "$ZIP"). Запускай ./start.sh"
