#!/usr/bin/env python3
"""
Таблицы модов в README — из mods.lock.json.

Список модов меняется чаще, чем на него смотрят: руками таблицы уже разошлись
с паком (в них стояли моды, которых в сборке нет). Поэтому README собирает их
сам между метками <!-- mods:begin --> и <!-- mods:end -->.

    python3 build/gen_readme_mods.py
"""
from __future__ import annotations

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCK = os.path.join(ROOT, "mods.lock.json")
README = os.path.join(ROOT, "README.md")

BEGIN = "<!-- mods:begin -->"
END = "<!-- mods:end -->"

GROUPS = [
    ("core", "Ядро геймплея"),
    ("deco", "Строительство и декор"),
    ("qol", "Удобства и атмосфера"),
    ("perf", "Производительность"),
    ("lib", "Библиотеки"),
]

SIDE_RU = {"both": "клиент+сервер", "client": "клиент", "server": "сервер"}


def rows(mods: list[dict], group: str) -> list[str]:
    out = []
    for mod in sorted(mods, key=lambda m: m["title"].lower()):
        if mod.get("group") != group:
            continue
        page = mod.get("page") or f"https://modrinth.com/mod/{mod['slug']}"
        side = SIDE_RU.get(mod.get("side", "both"), mod.get("side", ""))
        note = mod.get("note") or ""
        out.append(f"| [{mod['title']}]({page}) | `{mod['version_number']}` | "
                   f"{side} | {note} |")
    return out


def build(lock: dict) -> str:
    mods = lock["mods"]
    lines = [BEGIN, ""]
    for group, title in GROUPS:
        body = rows(mods, group)
        if not body:
            continue
        lines += [f"### {title} ({len(body)})", "",
                  "| Мод | Версия | Сторона | Зачем |", "|---|---|---|---|"]
        lines += body
        lines.append("")
    total = sum(len(rows(mods, group)) for group, _ in GROUPS)
    lines += [f"Всего {total} модов с Modrinth плюс самописный `citylife`.", "", END]
    return "\n".join(lines)


def main() -> int:
    with open(LOCK, encoding="utf-8") as fh:
        lock = json.load(fh)
    with open(README, encoding="utf-8") as fh:
        text = fh.read()
    if BEGIN not in text or END not in text:
        print("В README нет меток mods:begin / mods:end")
        return 1
    head, rest = text.split(BEGIN, 1)
    _, tail = rest.split(END, 1)
    with open(README, "w", encoding="utf-8") as fh:
        fh.write(head + build(lock) + tail)
    print(f"Таблицы модов обновлены: {lock['mod_count']} модов")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
