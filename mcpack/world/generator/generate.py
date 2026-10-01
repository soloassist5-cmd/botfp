#!/usr/bin/env python3
"""
Сборка мира Los Santos.

    python3 generate.py --out ../los-santos
    python3 generate.py --out /tmp/test --regions 0,0 --no-buildings   # быстрый тест

Мир пишется сразу в формате Anvil: готовый каталог сохранения, который
достаточно положить в saves/ (клиент) или назвать world/ (сервер).
"""
from __future__ import annotations

import argparse
import os
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from citygen import buildings, estates, level, plan, render  # noqa: E402
from citygen.canvas import RegionCanvas                      # noqa: E402
from citygen.region import BlockRegistry                     # noqa: E402
from citygen.terrain import Terrain                          # noqa: E402

DEFAULT_SEED = 20260927
WORLD_NAME = "Los Santos"


def parse_regions(text: str | None) -> list[tuple[int, int]] | None:
    if not text:
        return None
    result = []
    for pair in text.split(";"):
        rx, rz = pair.split(",")
        result.append((int(rx), int(rz)))
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="каталог мира (будет перезаписан)")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--regions", help="только эти регионы, напр. '0,0;-1,0'")
    parser.add_argument("--no-buildings", action="store_true",
                        help="только рельеф и улицы (быстрая проверка)")
    parser.add_argument("--datapack", help="каталог датапака, который положить в мир")
    parser.add_argument("--keep", action="store_true",
                        help="не удалять существующий каталог мира")
    args = parser.parse_args()

    city = plan.build_plan(args.seed)
    terrain = Terrain(args.seed, plan.CITY_BOUNDS)
    registry = BlockRegistry()

    half = plan.WORLD_BORDER // 2
    region_from = (-half) // 512
    region_to = (half - 1) // 512
    regions = parse_regions(args.regions) or [
        (rx, rz)
        for rx in range(region_from, region_to + 1)
        for rz in range(region_from, region_to + 1)
    ]

    world = args.out
    if os.path.isdir(world) and not args.keep:
        shutil.rmtree(world)
    os.makedirs(os.path.join(world, "region"), exist_ok=True)
    os.makedirs(os.path.join(world, "datapacks"), exist_ok=True)

    print(f"Los Santos: сид {args.seed}, участков {len(city.lots)}, "
          f"регионов {len(regions)}")
    started = time.time()
    total_bytes = 0

    for number, (rx, rz) in enumerate(regions, start=1):
        step = time.time()
        canvas = RegionCanvas(registry, rx, rz, terrain)
        render.draw_terrain(canvas, terrain)
        render.draw_roads(canvas)
        estates.draw_roads(canvas, args.seed)
        render.draw_freeway(canvas)
        render.draw_pier(canvas, terrain)
        render.draw_metro_tunnels(canvas, city)
        built = 0
        if not args.no_buildings:
            for lot in city.lots_touching(canvas.x0 - 8, canvas.z0 - 8,
                                          canvas.x1 + 8, canvas.z1 + 8):
                buildings.build_lot(canvas, lot)
                built += 1
        render.draw_bus_stop(canvas)
        path = canvas.write(os.path.join(world, "region"))
        size = os.path.getsize(path) if path else 0
        total_bytes += size
        print(f"  [{number}/{len(regions)}] r.{rx}.{rz}  "
              f"чанков {len(canvas.chunks):4d}  зданий {built:3d}  "
              f"{size / 1048576:5.1f} МБ  {time.time() - step:5.1f} c")

    datapacks = []
    if args.datapack and os.path.isdir(args.datapack):
        name = os.path.basename(args.datapack.rstrip("/"))
        shutil.copytree(args.datapack, os.path.join(world, "datapacks", name),
                        dirs_exist_ok=True)
        datapacks.append(name)

    level.write_level_dat(os.path.join(world, "level.dat"),
                          name=WORLD_NAME, seed=args.seed, spawn=plan.SPAWN,
                          border=plan.WORLD_BORDER, datapacks=datapacks)
    level.write_session_lock(world)

    print(f"Готово за {time.time() - started:.0f} c, "
          f"итого {total_bytes / 1048576:.1f} МБ, блоков в реестре {len(registry)}")
    print(f"Точка появления: {plan.SPAWN}, граница карты {plan.WORLD_BORDER} блоков")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
