#!/usr/bin/env bash
# =============================================================================
#  LS City Life — установщик для Linux и macOS.
#
#  Клиент:  ./install.sh --target client --path ~/.minecraft-ls-city
#  Сервер:  ./install.sh --target server --path ~/ls-city-server
#  Слабый ПК: добавь --quality low (есть ещё normal и high). Выбор
#  запоминается в .lscity-quality.txt и повторяется при обновлении.
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
# Папка с уже скачанными jar-ами: если она есть, сеть не понадобится.
MODS_SOURCE=""
WITH_WORLD=1
INCLUDE_OPTIONAL=0
CLEAN=0
JOBS=4
QUALITY=""

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
  --mods-dir DIR           брать моды из этой папки вместо загрузки
                           (по умолчанию mods-bundle рядом с паком, если есть)
  --jobs N                 параллельных загрузок (по умолчанию 4)
  --quality low|normal|high  профиль под ПК: графика, дальность, прохожие
                           (слабый — без Distant Horizons и шейдеров)
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
    --mods-dir) MODS_SOURCE="${2:?}"; shift 2 ;;
    --jobs)   JOBS="${2:?}"; shift 2 ;;
    --quality) QUALITY="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "неизвестный параметр: $1 (--help для справки)" ;;
  esac
done

[[ "$TARGET" == "client" || "$TARGET" == "server" ]] || die "--target: client или server"
[[ -n "$DEST" ]] || { usage; die "--path не задан"; }
[[ -f "$MODS_LIST" ]] || die "не найден $MODS_LIST"

if [[ -z "$MODS_SOURCE" && -d "$PACK_DIR/mods-bundle" ]]; then
  MODS_SOURCE="$PACK_DIR/mods-bundle"
fi

command -v curl >/dev/null || die "нужен curl"
if command -v sha512sum >/dev/null; then SHA512() { sha512sum "$1" | cut -d' ' -f1; }
elif command -v shasum  >/dev/null; then SHA512() { shasum -a 512 "$1" | cut -d' ' -f1; }
else die "нужен sha512sum или shasum"; fi

MC_VERSION="$(sed -n 's/^# MC \([0-9.]*\) .*/\1/p' "$MODS_LIST" | head -1)"
FORGE_VERSION="$(sed -n 's/^# MC .*forge \([0-9.]*\)$/\1/p' "$MODS_LIST" | head -1)"
: "${MC_VERSION:=1.20.1}" ; : "${FORGE_VERSION:=47.4.23}"

mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"

# Профиль производительности: явный --quality, иначе прошлый выбор, иначе normal.
QUALITY_FILE="$DEST/.lscity-quality.txt"
QUALITY_GIVEN=0
if [[ -n "$QUALITY" ]]; then QUALITY_GIVEN=1
elif [[ -f "$QUALITY_FILE" ]]; then QUALITY="$(head -n 1 "$QUALITY_FILE" | tr -d '[:space:]')"; fi
QUALITY="${QUALITY:-normal}"
[[ "$QUALITY" == "low" || "$QUALITY" == "normal" || "$QUALITY" == "high" ]] \
  || die "--quality: low, normal или high"
MODS_DIR="$DEST/mods"
mkdir -p "$MODS_DIR"

head1 "LS City Life — установка ($TARGET)"
say "Minecraft $MC_VERSION + Forge $FORGE_VERSION"
say "Каталог: $DEST"
if [[ -n "$MODS_SOURCE" ]]; then
  say "Моды берутся из комплекта: $MODS_SOURCE (сеть не нужна)"
fi

# --- 1. Загрузка модов -------------------------------------------------------
head1 "Моды"
expected_files=()
download_one() {
  local filename="$1" sha="$2" url="$3" dest="$MODS_DIR/$1"
  if [[ -f "$dest" ]] && [[ "$(SHA512 "$dest")" == "$sha" ]]; then
    printf '  = %s\n' "$filename"; return 0
  fi
  rm -f "$dest"
  # Локальная копия рядом с паком: проверяем хэш и копируем без сети.
  if [[ -n "$MODS_SOURCE" && -f "$MODS_SOURCE/$filename" ]]; then
    if [[ "$(SHA512 "$MODS_SOURCE/$filename")" == "$sha" ]]; then
      cp -f "$MODS_SOURCE/$filename" "$dest"
      printf '  * %s (из комплекта)\n' "$filename"; return 0
    fi
    printf '  %s! хэш локальной копии не совпал, качаю: %s%s\n' "$YEL" "$filename" "$RST"
  fi
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
  # Слабому ПК не нужны дальняя прорисовка и шейдеры: они съедают FPS.
  [[ "$QUALITY" == "low" && "$filename" =~ ^(DistantHorizons|oculus) ]] && continue
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

# Моды, которые пак ставил в прошлый раз, записаны в манифест в папке mods.
# Всё из него, чего в паке больше нет, удаляем; моды, поставленные игроком
# вручную, в манифест не попадают и остаются. Для папок от старых
# установщиков без манифеста есть retired.list — моды, убранные из пака.
manifest="$MODS_DIR/.lscity-installed.txt"
retired=()
if [[ -f "$SCRIPT_DIR/retired.list" ]]; then
  while IFS= read -r pattern; do
    pattern="${pattern%$'\r'}"
    [[ -z "$pattern" || "$pattern" == \#* ]] && continue
    retired+=("$pattern")
  done < "$SCRIPT_DIR/retired.list"
fi
for existing in "$MODS_DIR"/*.jar; do
  [[ -e "$existing" ]] || continue
  base="$(basename "$existing")"
  wanted=0
  for want in "${expected_files[@]}"; do [[ "$base" == "$want" ]] && wanted=1 && break; done
  (( wanted )) && continue
  ours=0
  [[ -f "$manifest" ]] && grep -Fxq -- "$base" "$manifest" && ours=1
  if (( ! ours )); then
    for pattern in "${retired[@]}"; do
      # shellcheck disable=SC2053  # шаблон нарочно без кавычек: это glob
      [[ "$base" == $pattern ]] && ours=1 && break
    done
  fi
  (( ours )) && { rm -f "$existing"; printf '  - %s (убран из пака)\n' "$base"; }
done
printf '%s\n' "${expected_files[@]}" > "$manifest"

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
copy_tree() { # src dst [файлы, которые не перезаписывать, если уже есть]
  [[ -d "$1" ]] || return 0
  local src="$1" dst="$2" keep_dir; shift 2
  mkdir -p "$dst"
  keep_dir="$(mktemp -d)"
  for f in "$@"; do [[ -f "$dst/$f" ]] && cp -p "$dst/$f" "$keep_dir/$f"; done
  ( cd "$src" && tar cf - . ) | ( cd "$dst" && tar xf - )
  for f in "$@"; do [[ -f "$keep_dir/$f" ]] && cp -p "$keep_dir/$f" "$dst/$f"; done
  rm -rf "$keep_dir"
  printf '  + %s/\n' "$(basename "$src")"
}
if [[ "$TARGET" == "client" ]]; then
  # options.txt игрока меняем, только если профиль выбран явно.
  if (( QUALITY_GIVEN )); then copy_tree "$PACK_DIR/overrides" "$DEST"
  else copy_tree "$PACK_DIR/overrides" "$DEST" options.txt; fi
else
  for sub in config kubejs defaultconfigs; do
    copy_tree "$PACK_DIR/overrides/$sub" "$DEST/$sub"
  done
  # Обновление не должно сбрасывать принятую EULA и настройки админа.
  copy_tree "$PACK_DIR/server" "$DEST" eula.txt server.properties
  chmod +x "$DEST"/*.sh 2>/dev/null || true
fi

# --- 2б. Профиль производительности ------------------------------------------
head1 "Профиль производительности"
# set_settings файл разделитель ключ=значение... — меняет строки «ключ<разд>…».
set_settings() {
  local file="$1" sep="$2"; shift 2
  [[ -f "$file" ]] || return 0
  local pair key value tmp
  for pair in "$@"; do
    key="${pair%%=*}"; value="${pair#*=}"; tmp="$(mktemp)"
    awk -v k="$key" -v v="$value" -v s="$sep" '
      BEGIN { t = s; gsub(/ /, "", t); found = 0 }
      {
        line = $0; lead = line; sub(/[^ \t].*$/, "", lead); rest = substr(line, length(lead) + 1)
        if (index(rest, k) == 1) {
          after = substr(rest, length(k) + 1); sub(/^[ \t]*/, "", after)
          if (index(after, t) == 1) { print lead k s v; found = 1; next }
        }
        print line
      }
      END { if (!found && s != " = ") print k s v }' "$file" > "$tmp" && cat "$tmp" > "$file"
    rm -f "$tmp"
  done
}
case "$QUALITY" in
  low)    OPTS=(renderDistance=6 simulationDistance=5 maxFps=60 graphicsMode=0 ao=false
                entityShadows=false entityDistanceScaling=0.5 particles=2 mipmapLevels=0 biomeBlendRadius=0)
          CITY=(pedestrians=2 pedestriansMax=16); PROPS=(view-distance=6 simulation-distance=5)
          NOTE="слабый ПК: короткая дальность, простая графика, меньше прохожих, без Distant Horizons и шейдеров" ;;
  high)   OPTS=(renderDistance=16 simulationDistance=10 maxFps=260 graphicsMode=1 ao=true
                entityShadows=true entityDistanceScaling=1.0 particles=0 mipmapLevels=4 biomeBlendRadius=3)
          CITY=(pedestrians=10 pedestriansMax=80); PROPS=(view-distance=12 simulation-distance=8)
          NOTE="мощный ПК: большая дальность, красивая графика, больше прохожих" ;;
  *)      OPTS=(renderDistance=12 simulationDistance=8 maxFps=120 graphicsMode=1 ao=true
                entityShadows=true entityDistanceScaling=0.75 particles=1 mipmapLevels=4 biomeBlendRadius=2)
          CITY=(pedestrians=6 pedestriansMax=48); PROPS=(view-distance=8 simulation-distance=6)
          NOTE="обычный ПК: настройки сборки как есть" ;;
esac
set_settings "$DEST/config/citylife-common.toml" " = " "${CITY[@]}"
if [[ "$TARGET" == "client" ]]; then
  if (( QUALITY_GIVEN )); then set_settings "$DEST/options.txt" ":" "${OPTS[@]}"
  else say "  ваши настройки игры (options.txt) сохранены, профиль применён только к городу"; fi
elif (( QUALITY_GIVEN )); then
  set_settings "$DEST/server.properties" "=" "${PROPS[@]}"
fi
echo "$QUALITY" > "$QUALITY_FILE"
ok "$QUALITY — $NOTE"

# --- 3. Мир ------------------------------------------------------------------
# Заменить datapacks/citylife в уже созданном мире на свежий из архива:
# регионы, игроки, дома и счета остаются как были.
update_datapack() { # zip мир
  local tmp; tmp="$(mktemp -d)"
  unzip -q -o "$1" 'los-santos/datapacks/citylife/*' -d "$tmp"
  rm -rf "$2/datapacks/citylife"; mkdir -p "$2/datapacks"
  mv "$tmp/los-santos/datapacks/citylife" "$2/datapacks/citylife"
  rm -rf "$tmp"
}
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
      update_datapack "$world_zip" "$DEST/saves/los-santos"
      ok "мир сохранён, датапак города обновлён (жители, справка, правила)"
    else
      unzip -q "$world_zip" -d "$DEST/saves"; ok "мир распакован в saves/los-santos"
    fi
  else
    if [[ -d "$DEST/world" ]]; then
      update_datapack "$world_zip" "$DEST/world"
      ok "мир сохранён, датапак города обновлён (жители, справка, правила)"
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
