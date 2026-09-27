# Как пересобрать сборку с нуля

Всё, кроме самих jar-файлов модов, лежит в репозитории и собирается скриптами.
Нужны Python 3.11+ (только стандартная библиотека) и, для самописного мода,
Java 17 с Gradle.

## Порядок сборки

```bash
cd mcpack

# 1. Разрешить версии модов и зафиксировать ссылки с хэшами.
python3 build/resolve_mods.py            # -> mods.lock.json

# 2. Проверить, что моды совместимы между собой.
python3 build/check_deps.py              # скачает jar-ы в build/.cache/jars

# 3. Собрать самописный мод.
cd citylife && gradle build && cd ..
cp citylife/build/libs/citylife-1.20.1-1.0.0.jar mods-local/

# 4. Сгенерировать датапак (NPC, сценарий, правила города).
python3 build/gen_datapack.py            # -> datapack/citylife

# 5. Сгенерировать мир вместе с датапаком.
python3 world/generator/generate.py \
    --out world/los-santos \
    --datapack datapack/citylife

# 6. Проверить мир, не запуская игру.
python3 world/generator/verify_world.py world/los-santos

# 7. Упаковать мир и собрать дистрибутивы.
(cd world && zip -qr9 los-santos.zip los-santos)
python3 build/build_pack.py              # -> dist/ и install/mods.list
```

## Из чего что берётся

| Файл | Роль |
|---|---|
| `pack.toml` | единственное место, где меняется список модов и версия Forge |
| `mods.lock.json` | результат разрешения версий: ссылки, размеры, sha1/sha512 |
| `install/mods.list` | то же в плоском виде для установщиков на shell и PowerShell |
| `dist/*.mrpack` | пак для Modrinth App, Prism, MultiMC, ATLauncher |
| `dist/*-curseforge.zip` | профиль для CurseForge App |
| `dist/*-server.zip` | каркас серверной сборки |

## Добавить мод

1. Дописать блок `[[mods]]` в `pack.toml`: `slug` с Modrinth, `side`
   (`both`/`client`/`server`), `group`, `note`.
2. `python3 build/resolve_mods.py` — обязательные зависимости подтянутся сами и
   унаследуют сторону родителя.
3. `python3 build/check_deps.py` — покажет конфликты диапазонов версий.
4. `python3 build/build_pack.py` — пересоберёт `.mrpack` и `install/mods.list`.

Если у мода нет стабильного релиза под 1.20.1 или он отстаёт от требований
зависимых модов, добавь в его блок `beta = true` (так сделано для JEI: Polymorph
требует версию новее последнего релиза).

## Поменять город

Планировка живёт в `world/generator/citygen/plan.py`:

- `LANDMARKS` — что стоит в конкретном квартале;
- `DISTRICT_MIX` — чем застраиваются остальные кварталы и какая доля участков
  остаётся свободной (вес `empty`);
- `CELL`, `IX_MIN…IZ_MAX` — шаг сетки улиц и границы города;
- `SPAWN`, `WORLD_BORDER` — точка появления и размер карты.

Генераторы зданий — `citygen/buildings.py`, улицы и эстакада — `citygen/render.py`,
рельеф — `citygen/terrain.py`.

Быстрая проверка правок без полной генерации:

```bash
python3 world/generator/generate.py --out /tmp/probe --regions "0,0"
python3 world/generator/verify_world.py /tmp/probe
```

Полная генерация 16 регионов занимает примерно 5–6 минут и даёт 64 МБ
region-файлов (9 МБ в архиве).

## Как устроен генератор мира

Мир пишется напрямую в формате Anvil, без Minecraft:

- `citygen/nbt.py` — чтение и запись NBT;
- `citygen/region.py` — чанки, палитры блоков, упаковка в long-массивы,
  запись `.mca`;
- `citygen/canvas.py` — полотно одного региона: всё, что выходит за его
  границы, отбрасывается, поэтому здание на стыке рисуется по разу в каждом
  регионе и стык всегда сходится;
- `citygen/level.py` — `level.dat`: граница мира, геймрулы, датапаки.

Heightmaps и освещение намеренно не пишутся: Minecraft пересчитывает их при
загрузке чанка сам (`isLightOn = 0`), и это надёжнее, чем считать их вручную.

## Самописный мод

`citylife/` — обычный проект ForgeGradle 6 для 1.20.1. Полезные команды:

```bash
cd citylife
gradle build          # jar в build/libs
gradle runServer      # выделенный сервер с модом (для проверки регистрации)
gradle runClient      # клиент с модом
```

Мод собирается под Java 17; если системная Java другая, укажи путь:

```bash
gradle build -Dorg.gradle.java.home=/путь/к/jdk17
```
