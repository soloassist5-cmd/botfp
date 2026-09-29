#!/usr/bin/env python3
"""
Сборка дистрибутивов пака из pack.toml + mods.lock.json.

Цели (--target):
  mrpack      .mrpack для Modrinth App, Prism Launcher, ATLauncher, MultiMC
  curseforge  zip-профиль для CurseForge App (моды доливает установщик)
  server      каркас серверной сборки (без клиентских модов)
  all         всё сразу

Файлы модов в дистрибутивы не кладутся: .mrpack ссылается на Modrinth,
остальное доливает install.sh / install.ps1 по mods.lock.json.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tomllib
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = "ls-city-life-packbuilder/1.0"
DIST = os.path.join(ROOT, "dist")
OVERRIDES = os.path.join(ROOT, "overrides")

# Что из overrides/ идёт на сервер (остальное — клиентское).
SERVER_OVERRIDE_DIRS = ("config", "kubejs", "defaultconfigs", "datapacks", "world_datapacks")


def load() -> tuple[dict, dict]:
    with open(os.path.join(ROOT, "pack.toml"), "rb") as fh:
        pack = tomllib.load(fh)
    with open(os.path.join(ROOT, "mods.lock.json"), encoding="utf-8") as fh:
        lock = json.load(fh)
    if lock["minecraft"] != pack["pack"]["minecraft"]:
        raise SystemExit("mods.lock.json устарел: версия MC не совпадает с pack.toml. "
                         "Запусти build/resolve_mods.py")
    return pack, lock


def iter_overrides(server: bool = False):
    """Пары (абсолютный путь, путь внутри архива) для содержимого overrides/."""
    if not os.path.isdir(OVERRIDES):
        return
    for dirpath, _dirnames, filenames in os.walk(OVERRIDES):
        rel_dir = os.path.relpath(dirpath, OVERRIDES)
        top = rel_dir.split(os.sep)[0]
        if server and rel_dir != "." and top not in SERVER_OVERRIDE_DIRS:
            continue
        for name in filenames:
            if name.startswith("."):
                continue
            src = os.path.join(dirpath, name)
            rel = os.path.relpath(src, OVERRIDES)
            yield src, rel


def write_mods_list(pack: dict, lock: dict) -> str:
    """Плоский список модов для установщиков на shell/PowerShell (без jq и Python)."""
    out = os.path.join(ROOT, "install", "mods.list")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    lines = [
        "# LS City Life — список модов для install.sh / install.ps1",
        f"# MC {lock['minecraft']} / {lock['loader']} {lock['loader_version']}",
        f"# сгенерировано build/build_pack.py из mods.lock.json ({lock['generated']})",
        "# поля: side<TAB>group<TAB>optional<TAB>filename<TAB>sha512<TAB>size<TAB>url",
    ]
    for m in lock["mods"]:
        lines.append("\t".join([
            m["side"], m["group"], "1" if m.get("optional") else "0",
            m["filename"], m["sha512"], str(m["size"]), m["url"],
        ]))
    with open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    return out


BOM = b"\xef\xbb\xbf"


def check_scripts() -> None:
    """
    Проверка скриптов, которые уходят пользователю.

    Windows PowerShell 5.1 читает .ps1 без BOM в системной кодировке (cp1251
    на русской системе). Одной русской буквы в исходнике хватает, чтобы все
    строки превратились в мусор, а кавычки-«ёлочки» из cp1251 сломали разбор
    файла ещё до первой строки кода. Поэтому в .ps1 разрешён только ASCII,
    а русский текст живёт в install/messages.ru.txt и читается как UTF-8.

    cmd.exe наоборот BOM не понимает и печатает его как символы, а .bat читает
    в OEM-кодировке — там тоже только ASCII. Переводы строк везде CRLF:
    с одиночным LF ломается многострочный `(` в .bat.
    """
    tracked = subprocess.run(["git", "ls-files", "-z", "*.ps1", "*.bat"], cwd=ROOT,
                             capture_output=True, text=True, check=True)
    bad: list[str] = []
    for name in sorted(n for n in tracked.stdout.split("\0") if n):
        raw = open(os.path.join(ROOT, name), "rb").read()
        body = raw[3:] if raw.startswith(BOM) else raw
        if name.endswith(".ps1"):
            if not raw.startswith(BOM):
                bad.append(f"{name}: нет UTF-8 BOM (PowerShell 5.1 прочтёт как cp1251)")
        elif raw.startswith(BOM):
            bad.append(f"{name}: BOM в .bat (cmd.exe напечатает его как символы)")
        try:
            text = body.decode("ascii")
        except UnicodeDecodeError as exc:
            bad.append(f"{name}: не-ASCII в исходнике ({exc.reason}, байт {exc.start}) — "
                       f"текст должен лежать в install/messages.ru.txt")
        else:
            if text.count("\n") != text.count("\r\n"):
                bad.append(f"{name}: не все переводы строк CRLF")
    bad += check_messages()
    if bad:
        raise SystemExit("Скрипты установщика:\n  " + "\n  ".join(bad))
    print("  скрипты .ps1/.bat: ASCII, CRLF, тексты на месте")


def check_messages() -> list[str]:
    """
    Каждый ключ из T '...' должен быть и в каталоге, и в английском запасном
    наборе внутри скрипта: каталог может не доехать, и тогда установщик обязан
    остаться читаемым, а не печатать имена ключей.
    """
    catalog = os.path.join(ROOT, "install", "messages.ru.txt")
    if not os.path.isfile(catalog):
        return ["install/messages.ru.txt: нет файла с текстами"]
    keys: set[str] = set()
    with open(catalog, encoding="utf-8-sig") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                keys.add(line.split("=", 1)[0].strip())
    problems: list[str] = []
    used: set[str] = set()
    for name in ("install/setup.ps1", "install/install.ps1"):
        text = open(os.path.join(ROOT, name), encoding="utf-8-sig").read()
        mine = set(re.findall(r"T '([a-z0-9_.]+)'", text))
        fallback = set(re.findall(r"'((?:setup|install)\.[a-z0-9_]+)'\s*=", text))
        used |= mine
        problems += [f"{name}: ключа {k} нет в messages.ru.txt" for k in sorted(mine - keys)]
        problems += [f"{name}: у ключа {k} нет запасного текста" for k in sorted(mine - fallback)]
    problems += [f"messages.ru.txt: ключ {k} никем не используется" for k in sorted(keys - used)]
    return problems


def build_full(pack: dict, lock: dict) -> str:
    """
    Один архив со всей сборкой: установщики, конфиги, датапак, самописный мод,
    готовый мир и документация.

    Файлы модов внутрь не кладутся (342 МБ и чужие лицензии) — их скачивает
    установщик по mods.lock.json. Состав берётся из того, что лежит в git,
    поэтому в архив не попадает мусор сборки.
    """
    p = pack["pack"]
    out = os.path.join(DIST, f"{p['id']}-{p['version']}-full.zip")
    os.makedirs(DIST, exist_ok=True)

    tracked = subprocess.run(["git", "ls-files", "-z", "."], cwd=ROOT,
                             capture_output=True, text=True, check=True)
    names = [name for name in tracked.stdout.split("\0") if name]
    if not names:
        raise SystemExit("git ls-files ничего не вернул: запусти из рабочей копии")

    skip_suffix = ("-full.zip",)
    # Собранная страница сайта и готовые дистрибутивы в пак не нужны: архив
    # иначе попал бы внутрь самого себя (или утащил .mrpack) и удвоил вес.
    skip_prefix = ("site/dist/", "dist/")
    root_name = f"{p['id']}-{p['version']}"
    written = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(names):
            if name.endswith(skip_suffix) or name.startswith(skip_prefix):
                continue
            source = os.path.join(ROOT, name)
            if not os.path.isfile(source):
                continue
            z.write(source, f"{root_name}/{name}")
            written += 1
        z.writestr(f"{root_name}/НАЧНИ_ОТСЮДА.txt", start_here(pack, lock))
    print(f"  файлов в полном архиве: {written}")
    return out


def start_here(pack: dict, lock: dict) -> str:
    p = pack["pack"]
    return f"""{p['name']} {p['version']}
{'=' * 60}

Minecraft {p['minecraft']} + Forge {p['loader_version']}, Java 17, 6 ГБ ОЗУ.
Модов: {lock['mod_count']} (скачиваются установщиком, {lock['total_size'] / 1048576:.0f} МБ).

С ЧЕГО НАЧАТЬ
-------------
1. Прочитай README.md — там общее описание и таблица модов.
2. Выбери инструкцию под свой лаунчер в папке docs:
     docs/legacy-launcher.md   Legacy Launcher, TLauncher и прочие
     docs/curseforge.md        CurseForge App (импорт профиля + установщик)
     docs/modrinth-prism.md    Modrinth App, Prism, MultiMC, ATLauncher
     docs/server-radmin.md     сервер для игры с друзьями через Radmin VPN

БЫСТРО (Windows)
----------------
  Запусти УСТАНОВИТЬ.bat двойным кликом — он сам найдёт профиль
  лаунчера и поставит сборку. Больше ничего делать не нужно.

БЫСТРО (Linux / macOS)
----------------------
  cd install
  ./install.sh --target client --path ~/.minecraft-ls-city

Дальше в лаунчере выбери Forge {p['minecraft']}-{p['loader_version']} и укажи эту
папку как папку игры. Мир появится в списке одиночных миров как «Los Santos».

ПОСЛЕ ПЕРВОГО ВХОДА
-------------------
Один раз выполни в чате, чтобы заселить город:
  /function citylife:npc/spawn_all

ЧТО ГДЕ ЛЕЖИТ
-------------
  УСТАНОВИТЬ.bat      установка в один клик (Windows)
  install/            установщики клиента и сервера
  world/los-santos.zip готовый мир (установщик распакует сам)
  mods-local/         самописный мод: телефоны и умные замки
  datapack/citylife/  NPC, сценарий, правила города
  overrides/          конфиги и KubeJS-скрипты
  server/             скрипты запуска сервера
  dist/               .mrpack и профиль CurseForge
  docs/               все инструкции
"""


def collect_jars(lock: dict, mods_dir: str | None) -> list[tuple[str, dict]]:
    """
    Найти или скачать jar каждого мода и сверить sha512.

    Возвращает пары (путь, запись из lock). Необязательные моды пропускаются.
    """
    cache = os.path.join(ROOT, "build", ".cache", "jars")
    os.makedirs(cache, exist_ok=True)
    result = []
    for mod in lock["mods"]:
        if mod.get("optional"):
            continue
        candidates = []
        if mods_dir:
            candidates.append(os.path.join(mods_dir, mod["filename"]))
        candidates.append(os.path.join(cache, mod["filename"]))
        path = next((c for c in candidates if os.path.exists(c)), None)
        if path is None:
            path = os.path.join(cache, mod["filename"])
            print(f"  скачиваю {mod['filename']}")
            request = urllib.request.Request(mod["url"], headers={"User-Agent": UA})
            with urllib.request.urlopen(request, timeout=180) as response, \
                    open(path, "wb") as out:
                shutil.copyfileobj(response, out)
        digest = hashlib.sha512()
        with open(path, "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                digest.update(chunk)
        if digest.hexdigest() != mod["sha512"]:
            raise SystemExit(f"{mod['filename']}: sha512 не совпал, файл битый")
        result.append((path, mod))
    return result


def build_bundle(pack: dict, lock: dict, mods_dir: str | None) -> str:
    """
    Полностью готовая к игре сборка: моды, конфиги, распакованный мир.

    Такую папку достаточно скопировать и указать лаунчеру как папку игры —
    ничего скачивать уже не нужно.
    """
    p = pack["pack"]
    out = os.path.join(DIST, f"{p['id']}-{p['version']}-ready.zip")
    os.makedirs(DIST, exist_ok=True)
    root_name = f"{p['id']}-{p['version']}-ready"

    jars = collect_jars(lock, mods_dir)
    client_jars = [(path, mod) for path, mod in jars if mod["side"] != "server"]

    world_zip = os.path.join(ROOT, "world", "los-santos.zip")
    if not os.path.exists(world_zip):
        raise SystemExit("нет world/los-santos.zip — сгенерируй мир")

    total = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        # jar-ы уже сжаты внутри: пережимать их бессмысленно, только время терять.
        for path, mod in client_jars:
            z.write(path, f"{root_name}/mods/{mod['filename']}",
                    compress_type=zipfile.ZIP_STORED)
            total += 1
        for jar in sorted(_local_jars()):
            z.write(jar, f"{root_name}/mods/{os.path.basename(jar)}",
                    compress_type=zipfile.ZIP_STORED)
            total += 1

        for src, rel in iter_overrides():
            z.write(src, f"{root_name}/{rel.replace(os.sep, '/')}")
            total += 1

        # Мир кладём распакованным, чтобы папку можно было просто скопировать.
        with zipfile.ZipFile(world_zip) as world:
            for entry in world.infolist():
                if entry.is_dir():
                    continue
                z.writestr(f"{root_name}/saves/{entry.filename}", world.read(entry))
                total += 1

        for name in ("README.md",):
            z.write(os.path.join(ROOT, name), f"{root_name}/{name}")
            total += 1
        docs = os.path.join(ROOT, "docs")
        for name in sorted(os.listdir(docs)):
            z.write(os.path.join(docs, name), f"{root_name}/docs/{name}")
            total += 1
        server_src = os.path.join(ROOT, "server")
        for dirpath, _dirs, files in os.walk(server_src):
            for name in files:
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, server_src).replace(os.sep, "/")
                z.write(full, f"{root_name}/server/{rel}")
                total += 1
        z.write(os.path.join(ROOT, "install", "install.sh"),
                f"{root_name}/install/install.sh")
        z.write(os.path.join(ROOT, "install", "install.ps1"),
                f"{root_name}/install/install.ps1")
        z.write(os.path.join(ROOT, "install", "mods.list"),
                f"{root_name}/install/mods.list")
        z.write(os.path.join(ROOT, "mods.lock.json"), f"{root_name}/mods.lock.json")
        total += 4

        z.writestr(f"{root_name}/CHITAY-MENYA.txt", ready_readme(pack, lock, len(client_jars)))

    print(f"  модов в сборке: {len(client_jars)} + самописный, файлов всего: {total}")
    return out


def _local_jars() -> list[str]:
    local = os.path.join(ROOT, "mods-local")
    if not os.path.isdir(local):
        return []
    return [os.path.join(local, name) for name in os.listdir(local)
            if name.endswith(".jar")]


def ready_readme(pack: dict, lock: dict, mod_count: int) -> str:
    p = pack["pack"]
    return f"""{p['name']} {p['version']} — ГОТОВАЯ СБОРКА
{'=' * 60}

Скачивать больше ничего не нужно: моды, конфиги и мир уже внутри.
Модов: {mod_count} + самописный citylife. Мир: saves/los-santos.

ЧТО НУЖНО ПОСТАВИТЬ ОТДЕЛЬНО
----------------------------
1. Java 17 — https://adoptium.net/temurin/releases/?version=17
   (именно 17: на 21 Forge 1.20.1 работает нестабильно)
2. Forge {p['minecraft']}-{p['loader_version']} — ставится из твоего лаунчера
   (Legacy Launcher, TLauncher, CurseForge, Prism — любой)

КАК ЗАПУСТИТЬ
-------------
1. Скопируй эту папку куда-нибудь, например в C:\\Games\\ls-city-life
2. В лаунчере создай профиль с версией Forge {p['minecraft']}-{p['loader_version']}
3. В настройках профиля укажи папку игры (game directory) — путь из пункта 1
4. Выдели 6 ГБ памяти: аргумент -Xmx6G
5. Запускай. Мир «Los Santos» уже в списке одиночных миров

ПОСЛЕ ПЕРВОГО ВХОДА
-------------------
Один раз выполни в чате, чтобы заселить город жителями:
  /function citylife:npc/spawn_all

Полезное:
  /work                  подработка (раз в 5 минут)
  /citylife balance      баланс банковского счёта
  /phonehelp             рецепты телефона и замков

СЕРВЕР ДЛЯ ДРУЗЕЙ
-----------------
Папка server/ содержит скрипты запуска и конфиги, но без серверного Forge и
серверных модов — их ставит установщик:
  install/install.sh --target server --path ~/ls-city-server        (Linux/macOS)
  install/install.ps1 -Target server -Path C:\\ls-city-server        (Windows)
Инструкция по Radmin VPN: docs/server-radmin.md

ЧТО ГДЕ
-------
  mods/            {mod_count} мода + citylife
  config/, kubejs/ настройки и скрипты
  options.txt      настройки клиента (прорисовка 12, русский язык)
  saves/los-santos готовый город
  docs/            все инструкции
  server/          серверная часть
  install/         установщики (нужны только для сервера или обновления)
  mods.lock.json   что именно за версии стоят, со ссылками и хэшами

ШЕЙДЕРЫ
-------
Oculus в сборку не включён: он конфликтует с Distant Horizons на части
шейдерпаков. Захочешь — поставь вручную и уменьши дистанцию Distant Horizons.
"""


def add_client_extras(z: zipfile.ZipFile, prefix: str = "overrides/") -> tuple[int, int]:
    """
    Кладёт в пак то, что лаунчер сам не скачает: самописный мод и готовый мир.

    Это наши собственные файлы, поэтому их можно распространять свободно —
    в отличие от 26 модов из 58, которые запрещают перевыкладывание jar-ов
    и приходят с авторских страниц силами лаунчера.

    Мир разворачивается из world/los-santos.zip прямо в saves/, чтобы после
    импорта пак сразу запускался в нужный город, без ручного копирования.
    """
    jars = 0
    local = os.path.join(ROOT, "mods-local")
    if os.path.isdir(local):
        for name in sorted(os.listdir(local)):
            if name.endswith(".jar"):
                z.write(os.path.join(local, name), f"{prefix}mods/{name}")
                jars += 1

    saves = 0
    world_zip = os.path.join(ROOT, "world", "los-santos.zip")
    if os.path.isfile(world_zip):
        with zipfile.ZipFile(world_zip) as src:
            for info in src.infolist():
                if info.is_dir() or os.path.basename(info.filename) == "session.lock":
                    continue
                z.writestr(f"{prefix}saves/{info.filename}", src.read(info.filename))
                saves += 1
    return jars, saves


def build_mrpack(pack: dict, lock: dict) -> str:
    p = pack["pack"]
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": p["version"],
        "name": p["name"],
        "summary": p["summary"],
        "files": [
            {
                "path": f"mods/{m['filename']}",
                "hashes": {"sha1": m["sha1"], "sha512": m["sha512"]},
                "env": m["env"],
                "downloads": [m["url"]],
                "fileSize": m["size"],
            }
            for m in lock["mods"]
            if not m.get("optional") and m.get("source") != "curseforge"
        ],
        "dependencies": {
            "minecraft": p["minecraft"],
            "forge": p["loader_version"],
        },
    }
    out = os.path.join(DIST, f"{p['id']}-{p['version']}.mrpack")
    os.makedirs(DIST, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2, ensure_ascii=False))
        for src, rel in iter_overrides():
            z.write(src, f"overrides/{rel.replace(os.sep, '/')}")
        jars, saves = add_client_extras(z)
        # Лаунчеры Modrinth качают только с разрешённых доменов, CurseForge в их
        # число не входит. Такие моды кладём в архив целиком — это допустимо
        # лишь при лицензии, разрешающей раздачу, и pack.toml её указывает.
        foreign = [m for m in lock["mods"]
                   if m.get("source") == "curseforge" and not m.get("optional")]
        for path, mod in collect_jars({"mods": foreign}, None):
            z.write(path, f"overrides/mods/{mod['filename']}")
        print(f"  .mrpack: самописных модов {jars}, файлов мира {saves}, "
              f"модов с CurseForge внутри {len(foreign)}")
        # Необязательные моды — отдельным списком, чтобы лаунчер их не тянул,
        # но пользователь знал, что можно добавить.
        opt = [m for m in lock["mods"] if m.get("optional")]
        if opt:
            # Имя латиницей: некоторые лаунчеры спотыкаются на кириллице в именах
            # файлов внутри архива.
            z.writestr("overrides/OPTIONAL-MODS.txt", "\n".join(
                ["Необязательные моды (поставить вручную при желании):", ""] +
                [f"  {m['title']} {m['version_number']} — {m['note']}\n    {m['page']}" for m in opt]
            ) + "\n")
    return out


def load_curseforge() -> dict | None:
    """curseforge.lock.json: пары projectID/fileID, собранные resolve_curseforge.py."""
    path = os.path.join(ROOT, "curseforge.lock.json")
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def build_curseforge(pack: dict, lock: dict) -> str:
    """
    Профиль для CurseForge App.

    Если известны CF-идентификаторы всех модов, пишем настоящий manifest.json —
    тогда CurseForge App скачивает моды сам с авторских страниц, и установщик
    не нужен. jar-ы в архив не кладутся и не могут: 26 модов из 58 запрещают
    перевыкладывание своих файлов.

    Если хотя бы один мод не сопоставлен, манифест остаётся пустым. Неполный
    манифест хуже пустого: CurseForge App молча соберёт профиль без части
    библиотек, и игра упадёт при запуске. В этом случае профиль ставится
    установщиком, как и раньше.
    """
    p = pack["pack"]
    cf = load_curseforge()
    by_file = {m["filename"]: m for m in cf["mods"]} if cf else {}
    for m in lock["mods"]:
        if m.get("source") == "curseforge":
            by_file[m["filename"]] = {"projectID": m["cf_project"], "fileID": m["cf_file"]}

    wanted = [m for m in lock["mods"] if not m.get("optional") and m["side"] != "server"]
    matched = [by_file[m["filename"]] for m in wanted if m["filename"] in by_file]
    absent = [m for m in wanted if m["filename"] not in by_file]
    complete = bool(matched) and not absent

    manifest = {
        "minecraft": {
            "version": p["minecraft"],
            "modLoaders": [{"id": f"{p['loader']}-{p['loader_version']}", "primary": True}],
        },
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": p["name"],
        "version": p["version"],
        "author": "soloassist5-cmd",
        "files": ([{"projectID": m["projectID"], "fileID": m["fileID"], "required": True}
                   for m in matched] if complete else []),
        "overrides": "overrides",
    }
    out = os.path.join(DIST, f"{p['id']}-{p['version']}-curseforge.zip")
    os.makedirs(DIST, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2, ensure_ascii=False))
        z.writestr("modlist.html", modlist_html(pack, lock))
        for src, rel in iter_overrides():
            z.write(src, f"overrides/{rel.replace(os.sep, '/')}")
        jars, saves = add_client_extras(z)
        if not complete:
            z.writestr("overrides/READ-ME-FIRST.txt",
                       "Этот профиль импортируется в CurseForge App без модов:\n"
                       "для части модов нет идентификаторов файлов CurseForge.\n\n"
                       "Моды доливает установщик из архива сборки — распакуй его\n"
                       "и запусти УСТАНОВИТЬ.bat, указав папку этого профиля\n"
                       "(в CurseForge App: Profile -> Open Folder).\n\n"
                       "Либо поставь Prism Launcher или Modrinth App и импортируй\n"
                       "ls-city-life-1.0.0.mrpack — там всё ставится одним файлом.\n")
    if complete:
        print(f"  curseforge: манифест на {len(matched)} модов — CurseForge App "
              f"скачает их сам")
    else:
        print(f"  curseforge: манифест пуст, нет CF-идентификаторов у "
              f"{len(absent)} модов из {len(wanted)} — профиль ставится установщиком")
    print(f"  curseforge: самописных модов {jars}, файлов мира {saves}")
    return out


def modlist_html(pack: dict, lock: dict) -> str:
    groups = {"core": "Ядро геймплея", "deco": "Строительство и декор",
              "qol": "Удобства", "perf": "Производительность", "lib": "Библиотеки"}
    rows = []
    for key, title in groups.items():
        mods = [m for m in lock["mods"] if m["group"] == key]
        if not mods:
            continue
        rows.append(f"<h3>{title}</h3><ul>")
        for m in mods:
            note = f" — {m['note']}" if m["note"] else ""
            rows.append(f'<li><a href="{m["page"]}">{m["title"]}</a> '
                        f'{m["version_number"]}{note}</li>')
        rows.append("</ul>")
    return ("<html><head><meta charset='utf-8'></head><body>"
            f"<h2>{pack['pack']['name']} {pack['pack']['version']} — "
            f"{lock['mod_count']} модов</h2>" + "".join(rows) + "</body></html>")


def build_server(pack: dict, lock: dict) -> str:
    """Каркас серверной сборки: конфиги, скрипты запуска, список модов."""
    p = pack["pack"]
    out_dir = os.path.join(DIST, f"{p['id']}-{p['version']}-server")
    if os.path.isdir(out_dir):
        shutil.rmtree(out_dir)
    os.makedirs(out_dir, exist_ok=True)

    server_mods = [m for m in lock["mods"]
                   if m["side"] != "client" and not m.get("optional")]
    with open(os.path.join(out_dir, "server.mods.json"), "w", encoding="utf-8") as fh:
        json.dump({"minecraft": p["minecraft"], "loader_version": p["loader_version"],
                   "mod_count": len(server_mods), "mods": server_mods},
                  fh, indent=2, ensure_ascii=False)

    for src, rel in iter_overrides(server=True):
        dst = os.path.join(out_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)

    src_server = os.path.join(ROOT, "server")
    if os.path.isdir(src_server):
        for name in os.listdir(src_server):
            s = os.path.join(src_server, name)
            d = os.path.join(out_dir, name)
            shutil.copytree(s, d, dirs_exist_ok=True) if os.path.isdir(s) else shutil.copy2(s, d)

    out_zip = out_dir + ".zip"
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _d, files in os.walk(out_dir):
            for name in files:
                full = os.path.join(dirpath, name)
                z.write(full, os.path.relpath(full, out_dir).replace(os.sep, "/"))
    shutil.rmtree(out_dir)
    return out_zip


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default="all",
                    choices=["all", "mrpack", "curseforge", "server", "full", "bundle"])
    ap.add_argument("--mods-dir", help="где взять уже скачанные jar-ы для --target bundle")
    args = ap.parse_args()
    pack, lock = load()
    check_scripts()

    built = [write_mods_list(pack, lock)]
    if args.target in ("all", "mrpack"):
        built.append(build_mrpack(pack, lock))
    if args.target in ("all", "curseforge"):
        built.append(build_curseforge(pack, lock))
    if args.target in ("all", "server"):
        built.append(build_server(pack, lock))
    if args.target in ("all", "full"):
        built.append(build_full(pack, lock))
    if args.target == "bundle":
        built.append(build_bundle(pack, lock, args.mods_dir))

    for path in built:
        print(f"  {os.path.relpath(path, ROOT)}  ({os.path.getsize(path) / 1024:.0f} КБ)")
    print(f"Готово. Модов в паке: {lock['mod_count']}, "
          f"скачивание займёт ~{lock['total_size'] / 1048576:.0f} МБ.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
