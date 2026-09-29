#!/usr/bin/env python3
"""
Каталог оружия TaCZ для оружейной: стволы, калибры и режимы огня.

Оружейные паки (встроенный TaCZ и Elite X Quality Guns) описывают стволы
в JSON с комментариями. Разбирать их при каждой сборке датапака значит
держать рядом распакованные паки, поэтому каталог снимается один раз и
лежит в build/tacz_catalog.json. Перегенерировать после обновления паков:

    python3 build/scan_tacz.py <папка tacz с распакованными паками>

Папка tacz появляется в каталоге игры/сервера после первого запуска с TaCZ.
"""
from __future__ import annotations

import json
import os
import re
import sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tacz_catalog.json")


def load(path: str):
    """JSON с // и /* */ комментариями и висячими запятыми."""
    text = open(path, encoding="utf-8-sig").read()
    out, i, in_str = [], 0, False
    while i < len(text):
        ch = text[i]
        if in_str:
            out.append(ch)
            if ch == "\\":
                out.append(text[i + 1]); i += 2; continue
            if ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True; out.append(ch)
        elif text.startswith("//", i):
            while i < len(text) and text[i] != "\n":
                i += 1
            continue
        elif text.startswith("/*", i):
            i = text.index("*/", i) + 2; continue
        else:
            out.append(ch)
        i += 1
    clean = re.sub(r",(\s*[}\]])", r"\1", "".join(out))
    return json.loads(clean)


def lang(pack: str, namespace: str) -> dict:
    names = {}
    for code in ("en_us", "ru_ru"):
        path = os.path.join(pack, "assets", namespace, "lang", f"{code}.json")
        if os.path.exists(path):
            names.update(load(path))
    return names


def main() -> int:
    root = sys.argv[1]
    guns, ammo = [], []
    for pack in sorted(os.listdir(root)):
        base = os.path.join(root, pack)
        data_root = os.path.join(base, "data")
        if not os.path.isdir(data_root):
            continue
        for namespace in sorted(os.listdir(data_root)):
            names = lang(base, namespace)
            index = os.path.join(data_root, namespace, "index")
            for name in sorted(os.listdir(os.path.join(index, "guns"))) \
                    if os.path.isdir(os.path.join(index, "guns")) else []:
                entry = load(os.path.join(index, "guns", name))
                data_ns, data_id = entry["data"].split(":")
                data = load(os.path.join(base, "data", data_ns, "data", "guns",
                                         f"{data_id}.json"))
                modes = [m.upper() for m in data.get("fire_mode", ["semi"])]
                guns.append({
                    "id": f"{namespace}:{name[:-5]}",
                    "pack": pack,
                    "type": entry.get("type", "rifle"),
                    "name": names.get(entry.get("name", ""), name[:-5]),
                    "ammo": data.get("ammo"),
                    "magazine": data.get("ammo_amount", 0),
                    "fire_mode": modes[0],
                })
            for name in sorted(os.listdir(os.path.join(index, "ammo"))) \
                    if os.path.isdir(os.path.join(index, "ammo")) else []:
                entry = load(os.path.join(index, "ammo", name))
                ammo.append({
                    "id": f"{namespace}:{name[:-5]}",
                    "pack": pack,
                    "name": names.get(entry.get("name", ""), name[:-5]),
                    "stack": entry.get("stack_size", 64),
                })
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"guns": guns, "ammo": ammo}, fh, ensure_ascii=False, indent=1)
    print(f"стволов {len(guns)}, патронов {len(ammo)} -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
