#!/usr/bin/env python3
"""
Точки для прохожих: тротуар перед входом в каждый участок города.

Прохожий идёт от одной такой точки к соседней, а дорогу между ними ищет
обычный поиск пути Minecraft — так люди ходят по тротуарам и переходам,
заходят к дверям магазинов и домов, а не бродят по крышам.

    python3 citylife/tools/gen_walk.py
"""
from __future__ import annotations

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.dirname(ROOT)
sys.path.insert(0, os.path.join(PACK, "world", "generator"))

from citygen import plan as P  # noqa: E402
from citygen import buildings as B  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "data", "citylife", "walk.json")

# Тип участка -> место работы.
PLACE_KIND = {"diner": "diner", "mall": "mall", "warehouse": "warehouse", "gas": "gas",
              "pickup": "pickup", "club": "club", "shop": "shop", "construction": "construction"}


def main() -> int:
    city = P.build_plan(20260927)
    points = set()
    for lot in city.lots:
        if lot.kind in ("construction",):
            continue
        points.add(tuple(B.entrance_point(lot)))
    rows = sorted(points)
    # Места работы по типам: куда ехать на смену, откуда забирать еду,
    # куда везти мусор.
    places: dict[str, list] = {}
    for lot in city.lots:
        kind = PLACE_KIND.get(lot.kind)
        if lot.kind == "shop" and lot.shop_role == "cook":
            kind = "diner"          # кафе на улицах — тоже кухня для доставки
        if kind:
            places.setdefault(kind, []).append(list(B.entrance_point(lot)))
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"points": [list(p) for p in rows],
                   "places": {k: sorted(v) for k, v in sorted(places.items())}},
                  fh, separators=(",", ":"))
        fh.write("\n")
    print(f"{len(rows)} точек, мест работы: "
          + ", ".join(f"{k} {len(v)}" for k, v in sorted(places.items()))
          + f" -> {os.path.relpath(OUT, PACK)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
