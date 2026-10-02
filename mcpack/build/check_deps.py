#!/usr/bin/env python3
"""
Проверка совместимости модов в паке.

Читает META-INF/mods.toml из каждого jar (включая вложенные jar-in-jar),
собирает версии всех модов и сверяет диапазоны зависимостей. Ловит именно
те поломки, которые иначе видно только при запуске игры: «мод A требует
мод B версии не ниже X, а в паке лежит Y».

    python3 build/check_deps.py --mods-dir /путь/к/mods
    python3 build/check_deps.py            # скачает jar-ы по mods.lock.json
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import tomllib
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "build", ".cache", "jars")
UA = "ls-city-life-packbuilder/1.0"


# ---------------------------------------------------------------------------
#  Сравнение версий и разбор диапазонов Maven
# ---------------------------------------------------------------------------

def version_key(version: str) -> tuple:
    """Разбить версию на сравнимые части: 1.20.1-47.4.23 -> (1,20,1,47,4,23)."""
    parts = [int(p) for p in re.findall(r"\d+", version)]
    return tuple(parts) if parts else (0,)


def compare(left: str, right: str) -> int:
    a, b = version_key(left), version_key(right)
    length = max(len(a), len(b))
    a += (0,) * (length - len(a))
    b += (0,) * (length - len(b))
    return (a > b) - (a < b)


def in_range(version: str, spec: str) -> bool:
    """
    Проверить версию по диапазону Maven: [1,2), [1.5,], [1.20.1], (1,).
    Несколько диапазонов через запятую вне скобок — логическое ИЛИ.
    """
    spec = spec.strip()
    if not spec or spec == "*":
        return True
    # Разбиваем только запятые между диапазонами, а не внутри них.
    ranges = []
    depth = 0
    current = ""
    for char in spec:
        if char in "[(":
            depth += 1
        elif char in "])":
            depth -= 1
        if char == "," and depth == 0:
            ranges.append(current)
            current = ""
            continue
        current += char
    ranges.append(current)

    for part in ranges:
        part = part.strip()
        if not part:
            continue
        if not part[0] in "[(":
            # Простая версия означает «эта или новее» (как в Maven).
            if compare(version, part) >= 0:
                return True
            continue
        low_inclusive = part[0] == "["
        high_inclusive = part[-1] == "]"
        body = part[1:-1]
        if "," not in body:
            # [1.20.1] — точное совпадение; не совпало — смотрим следующий диапазон.
            if compare(version, body.strip()) == 0:
                return True
            continue
        low, high = (piece.strip() for piece in body.split(",", 1))
        if low:
            check = compare(version, low)
            if check < 0 or (check == 0 and not low_inclusive):
                continue
        if high:
            check = compare(version, high)
            if check > 0 or (check == 0 and not high_inclusive):
                continue
        return True
    return False


# ---------------------------------------------------------------------------
#  Чтение mods.toml из jar (включая вложенные)
# ---------------------------------------------------------------------------

def read_mods_toml(data: bytes, source: str, found: list[dict]) -> None:
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        print(f"  ! {source}: не похоже на jar", file=sys.stderr)
        return
    names = archive.namelist()
    if "META-INF/mods.toml" in names:
        try:
            parsed = tomllib.loads(archive.read("META-INF/mods.toml")
                                   .decode("utf-8", "replace"))
        except tomllib.TOMLDecodeError as exc:
            print(f"  ! {source}: mods.toml не читается ({exc})", file=sys.stderr)
            parsed = {}
        version_hint = ""
        if "META-INF/MANIFEST.MF" in names:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
            match = re.search(r"Implementation-Version:\s*(\S+)", manifest)
            if match:
                version_hint = match.group(1)
        for mod in parsed.get("mods", []):
            version = str(mod.get("version", "")).strip()
            if version in ("${file.jarVersion}", "", "$"):
                version = version_hint or "0"
            found.append({
                "modId": mod.get("modId", "?"),
                "version": version,
                "source": source,
                "dependencies": parsed.get("dependencies", {}).get(mod.get("modId"), []),
            })
    # Вложенные библиотеки (jar-in-jar). Обычно лежат в META-INF/jarjar/, но
    # путь задаёт metadata.json, и Palladium, например, кладёт их в
    # META-INF/jars/ — поэтому берём пути и оттуда.
    nested = {name for name in names
              if name.startswith("META-INF/jarjar/") and name.endswith(".jar")}
    if "META-INF/jarjar/metadata.json" in names:
        try:
            meta = json.loads(archive.read("META-INF/jarjar/metadata.json"))
            nested |= {entry["path"] for entry in meta.get("jars", [])
                       if entry.get("path") in names}
        except (ValueError, KeyError):
            pass
    for name in sorted(nested):
        read_mods_toml(archive.read(name), f"{source} > {os.path.basename(name)}", found)


def load_from_dir(directory: str, skip: set[str] | None = None) -> list[dict]:
    found: list[dict] = []
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".jar") or (skip and name in skip):
            continue
        with open(os.path.join(directory, name), "rb") as fh:
            read_mods_toml(fh.read(), name, found)
    return found


def download_lock(lock_path: str) -> str:
    with open(lock_path, encoding="utf-8") as fh:
        lock = json.load(fh)
    os.makedirs(CACHE, exist_ok=True)
    for mod in lock["mods"]:
        target = os.path.join(CACHE, mod["filename"])
        if os.path.exists(target) and os.path.getsize(target) == mod["size"]:
            continue
        print(f"  скачиваю {mod['filename']}")
        request = urllib.request.Request(mod["url"], headers={"User-Agent": UA})
        with urllib.request.urlopen(request, timeout=120) as response, \
                open(target, "wb") as out:
            out.write(response.read())
    return CACHE


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods-dir", help="каталог с jar-файлами модов")
    parser.add_argument("--local-dir", default=os.path.join(ROOT, "mods-local"),
                        help="каталог самописных модов")
    args = parser.parse_args()

    with open(os.path.join(ROOT, "pack.toml"), "rb") as fh:
        pack = tomllib.load(fh)

    mods_dir = args.mods_dir or download_lock(os.path.join(ROOT, "mods.lock.json"))
    found = load_from_dir(mods_dir)
    if os.path.isdir(args.local_dir):
        # Самописные моды установщик уже скопировал в mods/, поэтому
        # одноимённые файлы не считаем второй раз.
        already = {name for name in os.listdir(mods_dir) if name.endswith(".jar")}
        found += load_from_dir(args.local_dir, skip=already)

    versions = {
        "minecraft": pack["pack"]["minecraft"],
        "forge": pack["pack"]["loader_version"],
    }
    duplicates: dict[str, list[str]] = {}
    for mod in found:
        mod_id = mod["modId"]
        duplicates.setdefault(mod_id, []).append(mod["source"])
        # Из нескольких копий берём самую свежую версию.
        if mod_id not in versions or compare(mod["version"], versions[mod_id]) > 0:
            versions[mod_id] = mod["version"]

    problems: list[str] = []
    warnings: list[str] = []

    for mod in found:
        for dep in mod["dependencies"]:
            dep_id = dep.get("modId")
            spec = str(dep.get("versionRange", "")).strip()
            mandatory = bool(dep.get("mandatory", False))
            side = str(dep.get("side", "BOTH")).upper()
            if not dep_id or dep_id not in versions:
                if mandatory and side in ("BOTH", "SERVER", "CLIENT"):
                    problems.append(f"{mod['modId']}: нет обязательной зависимости "
                                    f"{dep_id} {spec}")
                continue
            have = versions[dep_id]
            if spec and not in_range(have, spec):
                message = (f"{mod['modId']} {mod['version']} требует "
                           f"{dep_id} {spec}, а в паке {have}")
                if mandatory:
                    problems.append(message)
                else:
                    # Forge падает и на неподходящей необязательной зависимости,
                    # если этот мод всё-таки установлен.
                    problems.append(message + "  (необязательная, но установлена)")

    for mod_id, sources in duplicates.items():
        real = [s for s in sources if ">" not in s]
        if len(real) > 1:
            problems.append(f"{mod_id} установлен дважды: {', '.join(real)}")
        elif len(sources) > 1:
            warnings.append(f"{mod_id} есть и отдельно, и внутри другого мода: "
                            f"{', '.join(sources)}")

    print(f"\nПроверено jar-ов: {len({m['source'].split(' > ')[0] for m in found})}, "
          f"модов с учётом вложенных: {len(found)}, уникальных id: {len(duplicates)}")
    for warning in warnings:
        print(f"  ~ {warning}")
    if problems:
        print("\nПРОБЛЕМЫ СОВМЕСТИМОСТИ:")
        for problem in problems:
            print(f"  ✘ {problem}")
        return 1
    print("Конфликтов версий не найдено.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
