#!/usr/bin/env python3
"""
Проверка мира на дефекты, которые видит игрок.

Скриншоты сверху не показывают, что табличка висит в воздухе, дверь ведёт
в стену, а в коридоре темно. Этот скрипт читает регионы и ищет такие вещи
по правилам самой игры:

  sign_unsupported   настенная табличка без опоры за спиной
  sign_floating      стоячая табличка без опоры снизу
  lantern_floating   фонарь без опоры (висячий — сверху, стоячий — снизу)
  door_blocked       перед дверью или за ней не пройти
  door_hole          дыра в стене рядом с дверью (сбоку или над ней)
  door_no_floor      под дверью нет пола
  pit                у фасада ступенька или плита утоплены в землю
  floating_block     блок, у которого все шесть соседей — воздух
  dark_room          в помещении темно: блочный свет ниже 7

Нужен numpy (только для этой проверки, генератору он не нужен).

    python3 world/generator/lint_world.py world/los-santos [--regions 0,0;-1,0] [--examples 5]
"""
from __future__ import annotations

import argparse
import collections
import os
import struct
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from citygen import nbt  # noqa: E402
from citygen.canvas import furniture  # noqa: E402
from citygen.plan import CITY_Y  # noqa: E402

Y0 = CITY_Y - 4          # от земли чуть вниз: ямы и полы
Y1 = CITY_Y + 172        # до шпиля самой высокой башни
H = Y1 - Y0


# --- чтение регионов -------------------------------------------------------------

class Palette:
    """Глобальная палитра: строка состояния -> номер, и свойства каждого номера."""

    def __init__(self):
        self.index: dict[str, int] = {}
        self.names: list[str] = []
        self.props: list[dict] = []
        self.id_of("minecraft:air", {})

    def id_of(self, name: str, props: dict) -> int:
        key = name + "|" + ",".join(f"{k}={v}" for k, v in sorted(props.items()))
        found = self.index.get(key)
        if found is None:
            found = len(self.names)
            self.index[key] = found
            self.names.append(name)
            self.props.append(props)
        return found


def read_region(path: str, palette: Palette) -> np.ndarray:
    """Регион 512x512 по x,z и H по высоте: массив [y, z, x] номеров палитры."""
    out = np.zeros((H, 512, 512), dtype=np.uint16)
    with open(path, "rb") as fh:
        data = fh.read()
    for index in range(1024):
        offset = int.from_bytes(data[index * 4:index * 4 + 3], "big")
        if offset == 0:
            continue
        cx, cz = index % 32, index // 32
        length, compression = struct.unpack(">IB", data[offset * 4096:offset * 4096 + 5])
        payload = data[offset * 4096 + 5:offset * 4096 + 4 + length]
        _, chunk = nbt.loads(zlib.decompress(payload))
        for section in chunk.get("sections", []):
            sy = section.get("Y")
            base = sy * 16
            if base + 16 <= Y0 or base >= Y1:
                continue
            states = section.get("block_states") or {}
            pal = states.get("palette") or []
            if not pal:
                continue
            ids = np.array([palette.id_of(e["Name"], dict(e.get("Properties", {}))) for e in pal],
                           dtype=np.uint16)
            if "data" not in states:
                local = np.zeros(4096, dtype=np.int64)
            else:
                bits = max(4, (len(pal) - 1).bit_length())
                per = 64 // bits
                longs = np.array(states["data"], dtype=np.int64).view(np.uint64)
                shifts = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
                mask = np.uint64((1 << bits) - 1)
                local = ((longs[:, None] >> shifts[None, :]) & mask).reshape(-1)[:4096]
                local = local.astype(np.int64)
            block = ids[local].reshape(16, 16, 16)          # y, z, x
            y_lo = max(base, Y0)
            y_hi = min(base + 16, Y1)
            out[y_lo - Y0:y_hi - Y0, cz * 16:cz * 16 + 16, cx * 16:cx * 16 + 16] = \
                block[y_lo - base:y_hi - base]
    return out


# --- свойства блоков ---------------------------------------------------------------

NON_SOLID_PARTS = ("_stairs", "_slab", "_fence", "_pane", "iron_bars", "_wall", "_door",
                   "_trapdoor", "_sign", "torch", "lantern", "_carpet", "_button",
                   "_pressure_plate", "rail", "_bed", "chest", "ladder", "scaffolding",
                   "flower_pot", "_sapling", "_banner", "candle", "campfire", "chain",
                   "_head", "_skull", "lever", "vine", "cobweb", "end_rod", "lightning_rod",
                   "bell", "tripwire", "barrel_x")
NON_SOLID_EXACT = {"air", "cave_air", "void_air", "light", "water", "lava", "grass",
                   "short_grass", "tall_grass", "fern", "large_fern", "dandelion", "poppy",
                   "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley",
                   "rose_bush", "peony", "lilac", "sunflower", "snow", "dead_bush"}
PASSABLE_PARTS = ("_door", "_sign", "torch", "_carpet", "_pressure_plate", "rail",
                  "_button", "_sapling")
PASSABLE_EXACT = {"light", "short_grass", "tall_grass", "grass", "fern", "large_fern",
                  "lily_pad", "vine", "ladder"}
LIGHT_LEVEL = {"light": 15, "glowstone": 15, "sea_lantern": 15, "lantern": 15,
               "soul_lantern": 10, "torch": 14, "wall_torch": 14, "redstone_lamp": 15,
               "shroomlight": 15, "jack_o_lantern": 15, "end_rod": 14, "campfire": 15,
               "ochre_froglight": 15, "verdant_froglight": 15, "pearlescent_froglight": 15,
               "beacon": 15, "sea_pickle": 6}


def classify(palette: Palette):
    n = len(palette.names)
    solid = np.zeros(n, dtype=bool)
    air = np.zeros(n, dtype=bool)
    passable = np.zeros(n, dtype=bool)
    light = np.zeros(n, dtype=np.int8)
    opaque = np.zeros(n, dtype=bool)
    for i, name in enumerate(palette.names):
        base = name.split(":", 1)[-1]
        props = palette.props[i]
        is_air = base in ("air", "cave_air", "void_air", "light")
        air[i] = is_air
        mod_furniture = furniture(name)
        non_solid = (base in NON_SOLID_EXACT or any(p in base for p in NON_SOLID_PARTS)
                     or mod_furniture)
        solid[i] = not non_solid
        passable[i] = (is_air or any(p in base for p in PASSABLE_PARTS)
                       or base in PASSABLE_EXACT or base in NON_SOLID_EXACT
                       or base in ("canvas_rug", "doormat"))
        level = LIGHT_LEVEL.get(base, 0) if not mod_furniture else 0
        if mod_furniture and props.get("lit") == "true":
            level = 15
        if base == "redstone_lamp" and props.get("lit") != "true":
            level = 0
        if base == "light":
            level = int(props.get("level", 15))
        light[i] = level
        opaque[i] = solid[i] and "glass" not in base and "leaves" not in base
    return solid, air, passable, light, opaque


# --- проверки -----------------------------------------------------------------------

FACING_VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def shift(arr: np.ndarray, dy: int, dz: int, dx: int, fill=False) -> np.ndarray:
    """Сосед: shift(a, 0, 0, 1)[y,z,x] == a[y,z,x+1]."""
    out = np.full_like(arr, fill)
    ys = slice(max(0, -dy), H - max(0, dy))
    zs = slice(max(0, -dz), 512 - max(0, dz))
    xs = slice(max(0, -dx), 512 - max(0, dx))
    yd = slice(max(0, dy), H - max(0, -dy))
    zd = slice(max(0, dz), 512 - max(0, -dz))
    xd = slice(max(0, dx), 512 - max(0, -dx))
    out[ys, zs, xs] = arr[yd, zd, xd]
    return out


def lint_region(blocks: np.ndarray, palette: Palette, ox: int, oz: int,
                found: dict[str, list]) -> None:
    solid_t, air_t, pass_t, light_t, opaque_t = classify(palette)
    solid = solid_t[blocks]
    air = air_t[blocks]
    passable = pass_t[blocks]

    def report(kind: str, mask: np.ndarray) -> None:
        ys, zs, xs = np.nonzero(mask)
        for y, z, x in zip(ys.tolist(), zs.tolist(), xs.tolist()):
            found[kind].append((ox + x, Y0 + y, oz + z))
            found["@" + kind].append(palette.names[blocks[y, z, x]])

    names = palette.names
    props = palette.props

    # Таблички и фонари: смотрим по каждому варианту состояния.
    for i, name in enumerate(names):
        base = name.split(":", 1)[-1]
        if not (base.endswith("_sign") or base in ("lantern", "soul_lantern")):
            continue
        where = blocks == i
        if not where.any():
            continue
        p = props[i]
        if base.endswith("_wall_sign") and "hanging" not in base:
            dx, dz = FACING_VEC[p["facing"]]
            back = shift(solid, 0, -dz, -dx)
            report("sign_unsupported", where & ~back)
        elif base.endswith("_sign") and "hanging" not in base:
            below = shift(solid | (np.isin(blocks, fence_ids(palette))), -1, 0, 0)
            report("sign_floating", where & ~below)
        elif base in ("lantern", "soul_lantern"):
            if p.get("hanging") == "true":
                support = shift(~air, 1, 0, 0)
            else:
                support = shift(~air, -1, 0, 0)
            report("lantern_floating", where & ~support)

    # Двери: нижние половины.
    for i, name in enumerate(names):
        if not name.endswith("_door") or props[i].get("half") != "lower":
            continue
        where = blocks == i
        if not where.any():
            continue
        dx, dz = FACING_VEC[props[i]["facing"]]
        # Дверь смотрит туда, куда открыта; проход — вдоль оси facing.
        front = shift(passable, 0, dz, dx) & shift(passable, 1, dz, dx)
        back = shift(passable, 0, -dz, -dx) & shift(passable, 1, -dz, -dx)
        report("door_blocked", where & ~(front & back))
        report("door_no_floor", where & ~shift(solid, -1, 0, 0))
        # Стена вокруг двери: по бокам (перпендикулярно проходу) и над ней.
        side_a = shift(air, 0, dx, dz) | shift(air, 1, dx, dz)
        side_b = shift(air, 0, -dx, -dz) | shift(air, 1, -dx, -dz)
        above = shift(air, 2, 0, 0)
        report("door_hole", where & (side_a | side_b | above))

    # Ямы: ступенька или нижняя плита на уровне земли, а сверху воздух.
    ground = CITY_Y - Y0
    layer = blocks[ground]
    pit_ids = [i for i, n in enumerate(names)
               if (n.endswith("_stairs") and props[i].get("half") == "bottom")
               or (n.endswith("_slab") and props[i].get("type") == "bottom")]
    # Ямы ищем только под открытым небом: под крышей ступень в полу — верх
    # лестницы из подвала (башня Старка), а не яма у фасада.
    covered = np.any(~air[ground + 2:ground + 9], axis=0)
    pit = np.isin(layer, pit_ids) & air[ground + 1] & ~covered
    mask = np.zeros_like(air)
    mask[ground] = pit
    report("pit", mask)

    # Сыпучие блоки над пустотой: гравий, песок, бетонный порошок упадут
    # от первого же касания (так рушились потолки).
    falling = [i for i, n in enumerate(names)
               if n.split(":", 1)[-1] in ("gravel", "sand", "red_sand")
               or n.endswith("_concrete_powder")]
    if falling:
        report("falling_over_air", np.isin(blocks, falling) & shift(air, -1, 0, 0))

    # Висящие блоки: все шесть соседей — воздух. Голо-экраны Stark висят
    # в воздухе нарочно: это голограммы.
    alone = ~air & ~np.isin(blocks, [i for i, n in enumerate(names) if n == "citylife:holo_screen"])
    for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        alone &= shift(air, *d, fill=True)
    report("floating_block", alone)

    # Свет: распространяем блочный свет, как игра, на 15 шагов.
    level = light_t[blocks].astype(np.int8)
    opaque = opaque_t[blocks]
    for _ in range(15):
        spread = level.copy()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            spread = np.maximum(spread, shift(level, *d, fill=0) - 1)
        level = np.where(opaque, 0, np.maximum(level, spread))
    # Помещение: воздух, до которого нельзя дойти с неба, не открывая дверей.
    # Раньше «помещением» считалось всё, над чем есть крыша, и под эстакадой,
    # навесами и деревьями набегали тысячи ложных тёмных клеток.
    from scipy import ndimage
    doors = np.isin(blocks, [i for i, n in enumerate(names) if n.endswith("_door")])
    open_cells = passable & ~doors
    labels, _ = ndimage.label(open_cells)
    outside = np.unique(labels[-1][labels[-1] > 0])
    inside = open_cells & ~np.isin(labels, outside) & (labels > 0)
    standing = inside & shift(passable, 1, 0, 0) & shift(solid, -1, 0, 0)
    dark = standing & (level < 7)
    # Ниже уровня улицы — тоннели метро и подвалы, их считаем отдельно не будем.
    dark[:ground + 1] = False
    report("dark_room", dark)


def fence_ids(palette: Palette) -> list[int]:
    return [i for i, n in enumerate(palette.names) if n.endswith("_fence") or n.endswith("_wall")]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("world")
    parser.add_argument("--regions", default="")
    parser.add_argument("--examples", type=int, default=3)
    args = parser.parse_args()

    region_dir = os.path.join(args.world, "region")
    if args.regions:
        wanted = [tuple(int(v) for v in pair.split(",")) for pair in args.regions.split(";")]
    else:
        wanted = []
        for name in sorted(os.listdir(region_dir)):
            parts = name.split(".")
            if len(parts) == 4 and parts[3] == "mca":
                wanted.append((int(parts[1]), int(parts[2])))

    found: dict[str, list] = collections.defaultdict(list)
    for rx, rz in wanted:
        path = os.path.join(region_dir, f"r.{rx}.{rz}.mca")
        if not os.path.exists(path):
            continue
        palette = Palette()
        blocks = read_region(path, palette)
        lint_region(blocks, palette, rx * 512, rz * 512, found)
        print(f"  r.{rx}.{rz}: " + ", ".join(f"{k} {len(v)}" for k, v in sorted(found.items())
                                         if not k.startswith("@")),
              flush=True)

    print()
    total = 0
    for kind in ("sign_unsupported", "sign_floating", "lantern_floating", "door_blocked",
                 "door_hole", "door_no_floor", "pit", "floating_block", "falling_over_air",
                 "dark_room"):
        items = found.get(kind, [])
        total += len(items) if kind != "dark_room" else 0
        example = "  ".join(f"{x},{y},{z}" for x, y, z in items[:args.examples])
        print(f"{kind:18s} {len(items):7d}   {example}")
        names = collections.Counter(found.get("@" + kind, [])).most_common(4)
        if names:
            print(" " * 28 + ", ".join(f"{n.split(':')[-1]}×{c}" for n, c in names))
    print(f"\nдефектов (без тёмных клеток): {total}")
    return 0 if total == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
