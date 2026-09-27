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
import json
import os
import shutil
import subprocess
import sys
import tomllib
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
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
    root_name = f"{p['id']}-{p['version']}"
    written = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(names):
            if name.endswith(skip_suffix):
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
     docs/curseforge.md        CurseForge App
     docs/modrinth-prism.md    Modrinth App, Prism, MultiMC, ATLauncher
     docs/server-radmin.md     сервер для игры с друзьями через Radmin VPN

БЫСТРО (Windows)
----------------
  cd install
  powershell -ExecutionPolicy Bypass -File install.ps1 -Target client -Path "C:\\Games\\ls-city-life"

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
  install/            установщики клиента и сервера
  world/los-santos.zip готовый мир (установщик распакует сам)
  mods-local/         самописный мод: телефоны и умные замки
  datapack/citylife/  NPC, сценарий, правила города
  overrides/          конфиги и KubeJS-скрипты
  server/             скрипты запуска сервера
  dist/               .mrpack и профиль CurseForge
  docs/               все инструкции
"""


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
            for m in lock["mods"] if not m.get("optional")
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


def build_curseforge(pack: dict, lock: dict) -> str:
    p = pack["pack"]
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
        # Пусто намеренно: моды берутся с Modrinth установщиком, а не по CF-ID.
        "files": [],
        "overrides": "overrides",
    }
    out = os.path.join(DIST, f"{p['id']}-{p['version']}-curseforge.zip")
    os.makedirs(DIST, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2, ensure_ascii=False))
        z.writestr("modlist.html", modlist_html(pack, lock))
        z.writestr("overrides/READ-ME-FIRST.txt",
                   "Этот профиль импортируется в CurseForge App без модов.\n"
                   "После импорта открой папку профиля (Profile -> Open Folder)\n"
                   "и запусти установщик из mcpack/install:\n\n"
                   "  Windows:  .\\install.ps1 -Target client -Path \"<папка профиля>\"\n"
                   "  Linux:    ./install.sh --target client --path \"<папка профиля>\"\n")
        for src, rel in iter_overrides():
            z.write(src, f"overrides/{rel.replace(os.sep, '/')}")
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
                    choices=["all", "mrpack", "curseforge", "server", "full"])
    args = ap.parse_args()
    pack, lock = load()

    built = [write_mods_list(pack, lock)]
    if args.target in ("all", "mrpack"):
        built.append(build_mrpack(pack, lock))
    if args.target in ("all", "curseforge"):
        built.append(build_curseforge(pack, lock))
    if args.target in ("all", "server"):
        built.append(build_server(pack, lock))
    if args.target in ("all", "full"):
        built.append(build_full(pack, lock))

    for path in built:
        print(f"  {os.path.relpath(path, ROOT)}  ({os.path.getsize(path) / 1024:.0f} КБ)")
    print(f"Готово. Модов в паке: {lock['mod_count']}, "
          f"скачивание займёт ~{lock['total_size'] / 1048576:.0f} МБ.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
