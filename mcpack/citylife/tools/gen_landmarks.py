#!/usr/bin/env python3
"""
Городские метки для навигатора в телефоне.

Координаты достопримечательностей живут в генераторе города (plan.LANDMARKS),
и держать их второй раз руками в Java — верный способ разойтись. Поэтому
список собирается отсюда в готовый класс CityLandmarks.java.

    python3 citylife/tools/gen_landmarks.py
"""
from __future__ import annotations

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.dirname(ROOT)
sys.path.insert(0, os.path.join(PACK, "world", "generator"))

from citygen import plan as P  # noqa: E402
from citygen import buildings as B  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "java", "dev", "lscity", "citylife", "data",
                   "CityLandmarks.java")

# Как называть метку в телефоне и какой значок ей показывать.
KIND_RU = {
    "tower": ("Небоскрёб", "tower"),
    "mall": ("Торговый центр", "mall"),
    "city_hall": ("Мэрия", "civic"),
    "bank": ("Городской банк", "bank"),
    "police": ("Полиция", "police"),
    "hospital": ("Больница", "hospital"),
    "school": ("Школа", "civic"),
    "stadium": ("Стадион", "civic"),
    "park": ("Парк", "park"),
    "metro": ("Метро", "metro"),
    "gas": ("АЗС", "gas"),
    "club": ("Ночной клуб", "club"),
    "phone_shop": ("Салон связи", "shop"),
    "gun_shop": ("Оружейный магазин", "shop"),
    "diner": ("Закусочная", "food"),
}


# Шаг наружу от фасада: куда смотрит вход.
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def entrance(lot) -> tuple[int, int, int]:
    """Точка метки: тротуар перед входом, на уровне ног.

    Раньше метка стояла в центре участка — внутри здания, где маркер
    навигатора не увидеть и в него не войти.
    """
    x0, z0, x1, z1 = B.pad(lot, 1)
    fx, fz = B.front_center(x0, z0, x1, z1, lot.facing)
    dx, dz = STEP[lot.facing]
    return fx + dx * 3, P.CITY_Y + 1, fz + dz * 3


def title(kind: str, label: str) -> str:
    """Человеческое имя метки: у башен своё имя, у остальных — тип объекта."""
    pretty = label.strip()
    # «ТОРГОВЫЙ ЦЕНТР» -> «Торговый центр»; латинские названия башен и короткие
    # аббревиатуры вроде АЗС оставляем заглавными.
    if pretty.isupper() and any("А" <= ch <= "я" for ch in pretty) and len(pretty) > 4:
        pretty = pretty.capitalize()
        for acronym in ("It-", "Ls-", "Мчс", "Гибдд"):
            pretty = pretty.replace(acronym, acronym.upper())
    known = KIND_RU.get(kind)
    if known and pretty.lower() == known[0].lower():
        return known[0]
    return pretty


def main() -> int:
    city = P.build_plan(20260927)
    centre = {(lot.ix, lot.iz): lot for lot in city.lots}

    rows = []
    for (ix, iz), (kind, label) in sorted(P.LANDMARKS.items()):
        lot = centre.get((ix, iz))
        if lot is None:
            continue
        x, y, z = entrance(lot)
        icon = KIND_RU.get(kind, ("", "pin"))[1]
        rows.append((title(kind, label), icon, x, y, z))

    # Банкоматы: наличные нужны каждому, а искать их по городу вручную —
    # худшее, что можно предложить игроку. Координаты считает тот же код,
    # который ставит банкомат в мир, поэтому метка всегда попадает в блок.
    atms = []
    for lot in city.lots:
        if lot.kind not in B.ATM_KINDS:
            continue
        x, y, z = B.atm_pos(lot)
        dx, dz = STEP[lot.facing]
        # Метка — на шаг перед банкоматом: сам он стоит в блоке, в него не войти.
        atms.append((f"Банкомат — {title(lot.kind, lot.label or lot.kind)}", "atm",
                     x + dx, y, z + dz))
    rows += sorted(atms, key=lambda row: (row[2], row[4]))

    lines = [
        "package dev.lscity.citylife.data;",
        "",
        "import java.util.List;",
        "",
        "/**",
        " * Городские метки для навигатора.",
        " *",
        " * Файл собран из плана генератора города (citylife/tools/gen_landmarks.py),",
        " * править руками не нужно: при изменении города достаточно перегенерировать.",
        " */",
        "public final class CityLandmarks {",
        "",
        "    public static final List<Waypoint> ALL = List.of(",
    ]
    for i, (name, icon, x, y, z) in enumerate(rows):
        comma = "," if i < len(rows) - 1 else ""
        safe = name.replace('"', "'")
        lines.append(f'            new Waypoint("{safe}", {x}, {y}, {z}, "{icon}", true){comma}')
    lines += [
        "    );",
        "",
        "    private CityLandmarks() {",
        "    }",
        "}",
        "",
    ]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    print(f"Меток города: {len(rows)} -> {os.path.relpath(OUT, PACK)}")
    for name, icon, x, _y, z in rows:
        print(f"  {name:22s} {icon:9s} ({x}, {z})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
