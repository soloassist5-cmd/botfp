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


def main() -> int:
    city = P.build_plan(20260927)
    points = set()
    for lot in city.lots:
        if lot.kind in ("construction",):
            continue
        points.add(tuple(B.entrance_point(lot)))
    rows = sorted(points)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"points": [list(p) for p in rows]}, fh, separators=(",", ":"))
        fh.write("\n")
    print(f"{len(rows)} точек -> {os.path.relpath(OUT, PACK)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
