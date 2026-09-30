#!/usr/bin/env python3
"""
Каталог недвижимости для риелтора: дома, виллы, таунхаусы и квартиры.

Границы жилья считает тот же код генератора, что строит дома
(homes.estate_units), и кладёт в ресурс мода data/citylife/estate.json.
Мод читает файл при старте сервера: кто купил дом — хранится в сохранении
мира, а сам список домов идёт вместе с картой.

    python3 citylife/tools/gen_estate.py
"""
from __future__ import annotations

import json
import os
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.dirname(ROOT)
sys.path.insert(0, os.path.join(PACK, "world", "generator"))

from citygen import plan as P  # noqa: E402
from citygen import homes as H  # noqa: E402
from citygen import buildings as B  # noqa: E402
from citygen import commercial as C  # noqa: E402
from citygen.frame import Frame  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "data", "citylife", "estate.json")
SEED = 20260927


def jail(city) -> dict:
    """
    Камера в полицейском участке: генератор ставит её из решёток в дальнем
    углу здания (buildings.build_civic). Сюда сажают задержанных, а выпускают
    на тротуар у входа.
    """
    lot = next(l for l in city.lots if l.kind == "police")
    lay = C.layout(lot)
    frame = Frame(None, *lay.frame_rect, lay.facing)
    x, z = frame.world(lay.u1 - 2, lay.v1 - 2)
    ex, ey, ez = B.entrance_point(lot)
    return {"cell": [x, P.CITY_Y + 1, z], "exit": [ex, ey, ez]}


def main() -> int:
    city = P.build_plan(SEED)
    units = []
    for lot in city.lots:
        units += H.estate_units(lot)
    ids = Counter(unit["id"] for unit in units)
    dup = [key for key, n in ids.items() if n > 1]
    if dup:
        print("повторяются id:", dup[:5])
        return 1
    units.sort(key=lambda u: (u["kind"], u["address"]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"seed": SEED, "jail": jail(city), "units": units}, fh, ensure_ascii=False,
                  separators=(",", ":"))
        fh.write("\n")
    kinds = Counter(unit["kind"] for unit in units)
    prices = sorted(unit["price"] for unit in units)
    print(f"{len(units)} объектов: {dict(kinds)}; цены {prices[0]}–{prices[-1]} ₽, "
          f"медиана {prices[len(prices) // 2]} ₽ -> {os.path.relpath(OUT, PACK)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
