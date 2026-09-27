#!/usr/bin/env bash
# =============================================================================
#  LS City Life — установщик для Linux и macOS.
#
#  Клиент:  ./install.sh --target client --path ~/.minecraft-ls-city
#  Сервер:  ./install.sh --target server --path ~/ls-city-server
#
#  Скачивает моды с Modrinth по install/mods.list и проверяет sha512.
#  Повторный запуск докачивает только недостающее.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACK_DIR="$(dirname "$SCRIPT_DIR")"
MODS_LIST="$SCRIPT_DIR/mods.list"

TARGET="client"
DEST=""
WITH_WORLD=1
INCLUDE_OPTIONAL=0
CLEAN=0
JOBS=4

RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; BLD=$'\033[1m'; RST=$'\033[0m'
say()  { printf '%s\n' "$*"; }
ok()   { printf '%s✔%s %s\n' "$GRN" "$RST" "$*"; }
warn() { printf '%s!%s %s\n' "$YEL" "$RST" "$*"; }
die()  { printf '%s✘ %s%s\n' "$RED" "$*" "$RST" >&2; exit 1; }
head1(){ printf '\n%s== %s ==%s\n' "$BLD" "$*" "$RST"; }

usage() {
  cat <<TXT
Использование: install.sh [параметры]

  --target client|server   что устанавливаем (по умолчанию client)
  --path DIR               куда устанавливаем (обязательно)
  --no-world               не распаковывать готовый мир Los Santos
  --with-optional          поставить и необязательные моды (шейдеры Oculus)
  --clean                  удалить из mods/ посторонние jar-файлы
  --jobs N                 параллельных загрузок (по умолчанию 4)
  -h, --help               эта справка
TXT
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="${2:?}"; shift 2 ;;
    --path)   DEST="${2:?}";   shift 2 ;;
    --no-world) WITH_WORLD=0;  shift ;;
    --with-optional) INCLUDE_OPTIONAL=1; shift ;;
    --clean)  CLEAN=1; shift ;;
    --jobs)   JOBS="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "неизвестный параметр: $1 (--help для справки)" ;;
  esac
done

[[ "$TARGET" == "client" || "$TARGET" == "server" ]] || die "--target: client или server"
[[ -n "$DEST" ]] || { usage; die "--path не задан"; }
[[ -f "$MODS_LIST" ]] || die "не найден $MODS_LIST"

command -v curl >/dev/null || die "нужен curl"
if command -v sha512sum >/dev/null; then SHA512() { sha512sum "$1" | cut -d' ' -f1; }
elif command -v shasum  >/dev/null; then SHA512() { shasum -a 512 "$1" | cut -d' ' -f1; }
else die "нужен sha512sum или shasum"; fi

MC_VERSION="$(sed -n 's/^# MC \([0-9.]*\) .*/\1/p' "$MODS_LIST" | head -1)"
FORGE_VERSION="$(sed -n 's/^# MC .*forge \([0-9.]*\)$/\1/p' "$MODS_LIST" | head -1)"
: "${MC_VERSION:=1.20.1}" ; : "${FORGE_VERSION:=47.4.23}"

mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"
MODS_DIR="$DEST/mods"
mkdir -p "$MODS_DIR"

head1 "LS City Life — установка ($TARGET)"
say "Minecraft $MC_VERSION + Forge $FORGE_VERSION"
say "Каталог: $DEST"

# --- 1. Загрузка модов -------------------------------------------------------
head1 "Моды"
expected_files=()
download_one() {
  local filename="$1" sha="$2" url="$3" dest="$MODS_DIR/$1"
  if [[ -f "$dest" ]] && [[ "$(SHA512 "$dest")" == "$sha" ]]; then
    printf '  = %s\n' "$filename"; return 0
  fi
  rm -f "$dest"
  if ! curl -fL --retry 4 --retry-delay 2 --retry-connrefused -s -o "$dest.part" "$url"; then
    printf '  %s✘ не скачался: %s%s\n' "$RED" "$filename" "$RST"; rm -f "$dest.part"; return 1
  fi
  local got; got="$(SHA512 "$dest.part")"
  if [[ "$got" != "$sha" ]]; then
    printf '  %s✘ хэш не совпал: %s%s\n' "$RED" "$filename" "$RST"; rm -f "$dest.part"; return 1
  fi
  mv "$dest.part" "$dest"
  printf '  + %s\n' "$filename"
}

fail_list="$(mktemp)"; : > "$fail_list"
running=0
while IFS=$'\t' read -r side group optional filename sha size url; do
  [[ "$side" == \#* || -z "${side:-}" ]] && continue
  [[ "$TARGET" == "server" && "$side" == "client" ]] && continue
  [[ "$optional" == "1" && "$INCLUDE_OPTIONAL" == "0" ]] && continue
  expected_files+=("$filename")
  ( download_one "$filename" "$sha" "$url" || echo "$filename" >> "$fail_list" ) &
  running=$((running+1))
  if (( running >= JOBS )); then wait -n 2>/dev/null || wait; running=$((running-1)); fi
done < "$MODS_LIST"
wait

if [[ -s "$fail_list" ]]; then
  warn "Не удалось поставить: $(tr '\n' ' ' < "$fail_list")"
  warn "Запусти установщик снова — докачает только их."
fi
rm -f "$fail_list"

# Самописный мод из репозитория.
for local_jar in "$PACK_DIR"/mods-local/*.jar; do
  [[ -e "$local_jar" ]] || continue
  cp -f "$local_jar" "$MODS_DIR/"
  expected_files+=("$(basename "$local_jar")")
  printf '  + %s (самописный)\n' "$(basename "$local_jar")"
done

if (( CLEAN )); then
  for existing in "$MODS_DIR"/*.jar; do
    [[ -e "$existing" ]] || continue
    base="$(basename "$existing")"
    keep=0
    for want in "${expected_files[@]}"; do [[ "$base" == "$want" ]] && keep=1 && break; done
    (( keep )) || { rm -f "$existing"; printf '  - %s (лишний)\n' "$base"; }
  done
fi
ok "Модов в mods/: $(find "$MODS_DIR" -maxdepth 1 -name '*.jar' | wc -l | tr -d ' ')"

# --- 2. Конфиги и скрипты ----------------------------------------------------
head1 "Конфиги"
copy_tree() { # src dst
  [[ -d "$1" ]] || return 0
  mkdir -p "$2"
  ( cd "$1" && tar cf - . ) | ( cd "$2" && tar xf - )
  printf '  + %s/\n' "$(basename "$1")"
}
if [[ "$TARGET" == "client" ]]; then
  copy_tree "$PACK_DIR/overrides" "$DEST"
else
  for sub in config kubejs defaultconfigs; do
    copy_tree "$PACK_DIR/overrides/$sub" "$DEST/$sub"
  done
  copy_tree "$PACK_DIR/server" "$DEST"
  chmod +x "$DEST"/*.sh 2>/dev/null || true
fi

# --- 3. Мир ------------------------------------------------------------------
if (( WITH_WORLD )); then
  head1 "Мир Los Santos"
  world_zip="$(find "$PACK_DIR/world" -maxdepth 1 -name 'los-santos*.zip' 2>/dev/null | sort | head -1)"
  if [[ -z "$world_zip" ]]; then
    warn "архив мира не найден в $PACK_DIR/world — пропускаю"
  elif ! command -v unzip >/dev/null; then
    warn "нет unzip — распакуй $world_zip вручную"
  elif [[ "$TARGET" == "client" ]]; then
    mkdir -p "$DEST/saves"
    if [[ -d "$DEST/saves/los-santos" ]]; then
      warn "мир уже есть: $DEST/saves/los-santos (не перезаписываю)"
    else
      unzip -q "$world_zip" -d "$DEST/saves"; ok "мир распакован в saves/los-santos"
    fi
  else
    if [[ -d "$DEST/world" ]]; then
      warn "мир сервера уже есть: $DEST/world (не перезаписываю)"
    else
      tmp="$(mktemp -d)"; unzip -q "$world_zip" -d "$tmp"
      mv "$tmp/los-santos" "$DEST/world"; rm -rf "$tmp"
      ok "мир распакован в world/"
    fi
  fi
fi

# --- 4. Forge ----------------------------------------------------------------
head1 "Forge"
if [[ "$TARGET" == "server" ]]; then
  if [[ -f "$DEST/libraries/net/minecraftforge/forge/$MC_VERSION-$FORGE_VERSION/unix_args.txt" ]]; then
    ok "серверный Forge уже установлен"
  else
    command -v java >/dev/null || die "нужна Java 17 для установки серверного Forge"
    inst="$DEST/forge-installer.jar"
    url="https://maven.minecraftforge.net/net/minecraftforge/forge/$MC_VERSION-$FORGE_VERSION/forge-$MC_VERSION-$FORGE_VERSION-installer.jar"
    say "  скачиваю установщик Forge..."
    curl -fL --retry 4 -s -o "$inst" "$url" || die "не скачался установщик Forge"
    say "  устанавливаю серверный Forge (это долго, 1-3 минуты)..."
    ( cd "$DEST" && java -jar "$inst" --installServer >/dev/null 2>&1 ) \
      || die "установка Forge не удалась — запусти вручную: cd $DEST && java -jar forge-installer.jar --installServer"
    rm -f "$inst" "$inst.log"
    ok "серверный Forge установлен"
  fi
  say ""
  say "Дальше: прочитай eula.txt, поставь eula=true и запусти ./start.sh"
else
  say "  Forge для клиента ставит сам лаунчер."
  say "  Legacy Launcher / TLauncher: выбери версию Forge $MC_VERSION-$FORGE_VERSION"
  say "  и укажи этот каталог как папку игры:"
  say "     $DEST"
fi

head1 "Готово"
say "Не забудь выделить игре 6 ГБ ОЗУ (аргумент -Xmx6G) и поставить Java 17."
