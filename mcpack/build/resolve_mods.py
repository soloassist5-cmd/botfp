#!/usr/bin/env python3
"""
Разрешение списка модов из pack.toml в mods.lock.json.

Что делает:
  * для каждого мода берёт последнюю версию под нужные MC и загрузчик;
  * рекурсивно подтягивает обязательные зависимости;
  * фиксирует ссылку, размер и хэши (sha1 + sha512), чтобы установщик
    мог проверить целостность файла.

Запуск:  python3 build/resolve_mods.py [--offline] [--beta]
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import modrinth  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "pack.toml")
LOCK = os.path.join(ROOT, "mods.lock.json")

# Порядок предпочтения канала выпуска.
CHANNELS = {"release": 0, "beta": 1, "alpha": 2}


def pick_version(entry_slug: str, vers: list[dict], allow_beta: bool) -> dict:
    """Выбрать версию: сначала стабильные, внутри канала — самую свежую."""
    if not vers:
        raise RuntimeError(f"{entry_slug}: нет версий под заданные MC/загрузчик")
    candidates = [v for v in vers if allow_beta or v["version_type"] == "release"] or vers
    candidates.sort(key=lambda v: (CHANNELS.get(v["version_type"], 9), v["date_published"]),
                    reverse=False)
    # Свежайшая внутри лучшего доступного канала.
    best_channel = CHANNELS.get(candidates[0]["version_type"], 9)
    same = [v for v in candidates if CHANNELS.get(v["version_type"], 9) == best_channel]
    same.sort(key=lambda v: v["date_published"], reverse=True)
    return same[0]


def primary_file(ver: dict) -> dict:
    files = ver.get("files") or []
    if not files:
        raise RuntimeError(f"{ver.get('name')}: у версии нет файлов")
    for f in files:
        if f.get("primary"):
            return f
    return files[0]


def env_for(side: str) -> dict:
    """Поле env для modrinth.index.json."""
    return {
        "both": {"client": "required", "server": "required"},
        "client": {"client": "required", "server": "unsupported"},
        "server": {"client": "unsupported", "server": "required"},
    }[side]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--beta", action="store_true", help="разрешить beta/alpha версии модов")
    ap.add_argument("--offline", action="store_true", help="только кэш, без сети")
    args = ap.parse_args()

    with open(PACK, "rb") as fh:
        pack = tomllib.load(fh)
    mc = pack["pack"]["minecraft"]
    loader = pack["pack"]["loader"]

    resolved: dict[str, dict] = {}   # project_id -> запись
    problems: list[str] = []
    # Очередь: (идентификатор, side, group, note, кто затребовал)
    queue = [(m["slug"], m.get("side", "both"), m.get("group", "core"),
              m.get("note", ""), None, m.get("optional", False))
             for m in pack["mods"]]

    while queue:
        ident, side, group, note, required_by, optional = queue.pop(0)
        try:
            proj = modrinth.project(ident)
        except Exception as exc:
            problems.append(f"{ident}: проект не найден ({exc})")
            continue
        pid = proj["id"]
        if pid in resolved:
            # Зависимость уже в списке: расширяем side до both, если нужно.
            cur = resolved[pid]
            if cur["side"] != side and side == "both":
                cur["side"] = "both"
                cur["env"] = env_for("both")
            continue

        try:
            vers = modrinth.versions(pid, mc, loader)
            ver = pick_version(proj["slug"], vers, args.beta)
            f = primary_file(ver)
        except Exception as exc:
            problems.append(f"{proj['slug']}: {exc}")
            continue

        resolved[pid] = {
            "slug": proj["slug"],
            "title": proj["title"],
            "project_id": pid,
            "version_id": ver["id"],
            "version_number": ver["version_number"],
            "version_type": ver["version_type"],
            "filename": f["filename"],
            "url": f["url"],
            "size": f["size"],
            "sha1": f["hashes"]["sha1"],
            "sha512": f["hashes"]["sha512"],
            "side": side,
            "env": env_for(side),
            "group": group,
            "note": note,
            "optional": bool(optional),
            "required_by": required_by,
            "page": f"https://modrinth.com/mod/{proj['slug']}",
        }

        for dep in ver.get("dependencies") or []:
            if dep.get("dependency_type") != "required":
                continue
            dep_id = dep.get("project_id")
            if not dep_id and dep.get("version_id"):
                dep_id = modrinth.version(dep["version_id"])["project_id"]
            if not dep_id:
                continue
            queue.append((dep_id, "both", "lib", "Библиотека-зависимость",
                          proj["slug"], False))

    if problems:
        print("ОШИБКИ РАЗРЕШЕНИЯ:", file=sys.stderr)
        for p in problems:
            print("  -", p, file=sys.stderr)

    mods = sorted(resolved.values(), key=lambda m: (m["group"], m["slug"]))
    lock = {
        "generated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "pack_version": pack["pack"]["version"],
        "minecraft": mc,
        "loader": loader,
        "loader_version": pack["pack"]["loader_version"],
        "mod_count": len(mods),
        "total_size": sum(m["size"] for m in mods),
        "mods": mods,
    }
    with open(LOCK, "w", encoding="utf-8") as fh:
        json.dump(lock, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    print(f"Зафиксировано модов: {len(mods)} "
          f"({lock['total_size'] / 1048576:.1f} МБ) -> mods.lock.json")
    libs = [m["slug"] for m in mods if m["group"] == "lib"]
    if libs:
        print("Подтянутые библиотеки:", ", ".join(libs))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
