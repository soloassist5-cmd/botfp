#!/usr/bin/env bash
# =============================================================================
#  Запуск сервера LS City Life (Linux / macOS)
#
#  Перед первым запуском:
#    1. открой eula.txt и поставь eula=true
#    2. убедись, что установлена Java 17
#
#  Сколько памяти дать серверу:  RAM=6G ./start.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

RAM="${RAM:-4G}"
MC="1.20.1"
FORGE="47.4.23"
ARGS="libraries/net/minecraftforge/forge/$MC-$FORGE/unix_args.txt"

if ! command -v java >/dev/null; then
  echo "Не найдена Java. Поставь Java 17 и запусти снова." >&2
  exit 1
fi
if [[ ! -f "$ARGS" ]]; then
  echo "Не установлен серверный Forge. Запусти установщик:" >&2
  echo "  ./install.sh --target server --path \"$(pwd)\"" >&2
  exit 1
fi
if grep -qi '^eula=false' eula.txt 2>/dev/null; then
  echo "Открой eula.txt и поставь eula=true (это согласие с лицензией Minecraft)." >&2
  exit 1
fi

# Флаги сборщика мусора, которые хорошо себя ведут на модовых сборках.
exec java \
  -Xmx"$RAM" -Xms1G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -Dfile.encoding=UTF-8 \
  "@$ARGS" nogui
