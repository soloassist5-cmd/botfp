#!/usr/bin/env python3
"""
Картинка города сверху прямо из region-файлов.

Нужна, чтобы смотреть на застройку, не запуская игру: видно кварталы, крыши,
улицы и площади, а по теням читается высота. Цвет берём по верхнему видимому
блоку, яркость — по перепаду высот с соседом.

    python3 world/generator/preview.py world/los-santos --regions 0,0 --out /tmp/city.png
"""
from __future__ import annotations

import argparse
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from citygen import nbt  # noqa: E402
from citylife_png import Canvas  # noqa: E402

# Цвета верхних блоков. Всё, чего здесь нет, красится по «прочему» цвету.
COLOURS = {
    "minecraft:black_concrete": (34, 34, 38),
    "minecraft:gray_concrete": (86, 90, 96),
    "minecraft:light_gray_concrete": (150, 154, 160),
    "minecraft:white_concrete": (222, 226, 230),
    "minecraft:yellow_concrete": (226, 190, 64),
    "minecraft:red_concrete": (168, 62, 56),
    "minecraft:orange_concrete": (206, 122, 50),
    "minecraft:blue_concrete": (56, 78, 160),
    "minecraft:cyan_concrete": (40, 128, 148),
    "minecraft:green_concrete": (72, 118, 60),
    "minecraft:lime_concrete": (112, 176, 68),
    "minecraft:brown_concrete": (110, 78, 54),
    "minecraft:smooth_stone": (168, 168, 168),
    "minecraft:stone_bricks": (130, 130, 130),
    "minecraft:polished_andesite": (140, 140, 138),
    "minecraft:bricks": (150, 86, 70),
    "minecraft:quartz_block": (236, 232, 224),
    "minecraft:smooth_quartz": (232, 228, 220),
    "minecraft:calcite": (224, 224, 218),
    "minecraft:deepslate_tiles": (60, 62, 68),
    "minecraft:polished_deepslate": (72, 74, 80),
    "minecraft:cut_copper": (192, 112, 72),
    "minecraft:exposed_cut_copper": (160, 124, 96),
    "minecraft:weathered_cut_copper": (108, 152, 124),
    "minecraft:mud_bricks": (140, 110, 88),
    "minecraft:white_terracotta": (210, 196, 186),
    "minecraft:orange_terracotta": (162, 84, 50),
    "minecraft:light_gray_terracotta": (152, 138, 130),
    "minecraft:glass": (176, 208, 222),
    "minecraft:tinted_glass": (60, 60, 66),
    "minecraft:light_blue_stained_glass": (120, 176, 220),
    "minecraft:cyan_stained_glass": (78, 160, 176),
    "minecraft:gray_stained_glass": (110, 116, 124),
    "minecraft:black_stained_glass": (48, 50, 56),
    "minecraft:green_stained_glass": (92, 148, 92),
    "minecraft:water": (52, 96, 168),
    "minecraft:sand": (222, 208, 160),
    "minecraft:grass_block": (108, 152, 78),
    "minecraft:oak_leaves": (78, 128, 62),
    "minecraft:birch_leaves": (110, 150, 80),
    "minecraft:spruce_planks": (122, 92, 58),
    "minecraft:oak_planks": (168, 136, 86),
    "minecraft:dark_oak_planks": (84, 60, 36),
    "minecraft:iron_block": (208, 208, 212),
    "minecraft:iron_bars": (120, 124, 130),
    "minecraft:sea_lantern": (236, 244, 236),
    "minecraft:rail": (140, 132, 120),
    "minecraft:gravel": (140, 136, 132),
    "minecraft:dirt": (118, 88, 62),
    "minecraft:stone": (124, 124, 124),
}
FALLBACK = (128, 122, 116)
# Эти блоки не считаем поверхностью: сквозь них видно то, что под ними.
SKIP = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:light", "minecraft:chain", "minecraft:ladder"}


def colour_of(name: str) -> tuple[int, int, int]:
    base = name.split("[")[0]
    if base in COLOURS:
        return COLOURS[base]
    for suffix, colour in (("_slab", None), ("_stairs", None), ("_wall", None),
                           ("_fence", None)):
        if base.endswith(suffix):
            stem = base[: -len(suffix)]
            for candidate in (stem, stem + "s", stem.replace("smooth_", "")):
                if candidate in COLOURS:
                    return COLOURS[candidate]
    return FALLBACK


def read_region(path: str) -> dict[tuple[int, int], dict]:
    """Чанки региона: {(cx, cz): корневой тег}."""
    chunks: dict[tuple[int, int], dict] = {}
    with open(path, "rb") as fh:
        header = fh.read(4096)
        for index in range(1024):
            offset = int.from_bytes(header[index * 4:index * 4 + 3], "big")
            if offset == 0:
                continue
            fh.seek(offset * 4096)
            length = struct.unpack(">I", fh.read(4))[0]
            compression = fh.read(1)[0]
            payload = fh.read(length - 1)
            raw = zlib.decompress(payload) if compression == 2 else payload
            _, root = nbt.loads(raw)
            chunks[(index % 32, index // 32)] = root
    return chunks


def surface(chunk: dict) -> list[list[tuple[str, int]]]:
    """Верхний видимый блок каждой колонки чанка: (имя, высота)."""
    out = [[("minecraft:air", -64)] * 16 for _ in range(16)]
    sections = chunk.get("sections", [])
    ordered = sorted(sections, key=lambda s: s.get("Y", 0), reverse=True)
    for section in ordered:
        block_states = section.get("block_states", {})
        palette = block_states.get("palette", [])
        if not palette:
            continue
        names = [entry.get("Name", "minecraft:air") for entry in palette]
        props = [entry.get("Properties", {}) for entry in palette]
        data = block_states.get("data")
        base_y = section.get("Y", 0) * 16
        if data is None:
            if len(names) == 1 and names[0] in SKIP:
                continue
            for z in range(16):
                for x in range(16):
                    if out[z][x][1] == -64:
                        out[z][x] = (names[0], base_y + 15)
            continue
        bits = max(4, (len(names) - 1).bit_length())
        per_long = 64 // bits
        mask = (1 << bits) - 1
        for y in range(15, -1, -1):
            for z in range(16):
                for x in range(16):
                    if out[z][x][1] != -64:
                        continue
                    index = y * 256 + z * 16 + x
                    word = data[index // per_long]
                    value = (word >> (index % per_long * bits)) & mask
                    name = names[value] if value < len(names) else "minecraft:air"
                    if name in SKIP:
                        continue
                    state = name
                    if value < len(props) and props[value]:
                        state = name + "[" + ",".join(
                            f"{k}={v}" for k, v in sorted(props[value].items())) + "]"
                    out[z][x] = (state, base_y + y)
    return out


def block_at(chunks, cx, cz, x, y, z) -> str:
    """Имя блока в колонке чанка по локальным координатам."""
    chunk = chunks.get((cx, cz))
    if chunk is None:
        return "minecraft:air"
    for section in chunk.get("sections", []):
        if section.get("Y", 0) != (y >> 4):
            continue
        states = section.get("block_states", {})
        palette = states.get("palette", [])
        if not palette:
            return "minecraft:air"
        names = [e.get("Name", "minecraft:air") for e in palette]
        data = states.get("data")
        if data is None:
            return names[0]
        bits = max(4, (len(names) - 1).bit_length())
        per_long = 64 // bits
        mask = (1 << bits) - 1
        index = (y & 15) * 256 + z * 16 + x
        word = data[index // per_long]
        value = (word >> (index % per_long * bits)) & mask
        return names[value] if value < len(names) else "minecraft:air"
    return "minecraft:air"


def area(args) -> int:
    """Вид сверху на кусок города: удобно смотреть на один дом, а не на регион."""
    x0, z0, x1, z1 = (int(v) for v in args.area.split(","))
    scale = args.scale
    canvas = Canvas((x1 - x0) * scale, (z1 - z0) * scale)
    cache: dict[tuple[int, int], dict] = {}

    def chunks_for(x: int, z: int) -> dict:
        key = (x >> 9, z >> 9)
        if key not in cache:
            path = os.path.join(args.world, "region", f"r.{key[0]}.{key[1]}.mca")
            cache[key] = read_region(path) if os.path.isfile(path) else {}
        return cache[key]

    tops: dict[tuple[int, int], tuple[str, int]] = {}
    surfaces: dict[tuple[int, int, int, int], list] = {}
    for z in range(z0 - 1, z1):
        for x in range(x0, x1):
            key = (x >> 9, z >> 9, (x >> 4) & 31, (z >> 4) & 31)
            if key not in surfaces:
                chunk = chunks_for(x, z).get(((x >> 4) & 31, (z >> 4) & 31))
                surfaces[key] = surface(chunk) if chunk else None
            data = surfaces[key]
            tops[(x, z)] = data[z & 15][x & 15] if data else ("minecraft:air", 0)

    for z in range(z0, z1):
        for x in range(x0, x1):
            name, y = tops[(x, z)]
            # Тень по перепаду высоты с северным соседом — как в общем виде.
            delta = y - tops[(x, z - 1)][1]
            factor = 1.0 + max(-0.35, min(0.35, delta * 0.08))
            r, g, b = colour_of(name)
            shade = (min(255, int(r * factor)), min(255, int(g * factor)),
                     min(255, int(b * factor)))
            for dy in range(scale):
                for dx in range(scale):
                    canvas.set((x - x0) * scale + dx, (z - z0) * scale + dy,
                               (*shade, 255))
    canvas.write(args.out)
    print(f"Участок: {args.out} ({canvas.w}x{canvas.h})")
    return 0


def elevation(args) -> int:
    """Вид сбоку: срез мира по фиксированному z. Видно фасады, а не крыши."""
    z_line, x0, x1, y0, y1 = (int(v) for v in args.elevation.split(","))
    scale = args.scale
    canvas = Canvas((x1 - x0) * scale, (y1 - y0) * scale)
    cache: dict[tuple[int, int], dict] = {}
    for x in range(x0, x1):
        rx, rz = x >> 9, z_line >> 9
        key = (rx, rz)
        if key not in cache:
            path = os.path.join(args.world, "region", f"r.{rx}.{rz}.mca")
            cache[key] = read_region(path) if os.path.isfile(path) else {}
        chunks = cache[key]
        cx, cz = (x >> 4) & 31, (z_line >> 4) & 31
        for y in range(y0, y1):
            # Смотрим на здание с улицы: берём первый непустой блок вглубь,
            # иначе вместо фасада видно разрез по этажам.
            name = "minecraft:air"
            for depth in range(0, 26):
                probe_z = z_line + depth
                pcx, pcz = (x >> 4) & 31, (probe_z >> 4) & 31
                rz_probe = probe_z >> 9
                probe_chunks = chunks
                if rz_probe != (z_line >> 9):
                    key2 = (x >> 9, rz_probe)
                    if key2 not in cache:
                        path2 = os.path.join(args.world, "region",
                                             f"r.{key2[0]}.{key2[1]}.mca")
                        cache[key2] = read_region(path2) if os.path.isfile(path2) else {}
                    probe_chunks = cache[key2]
                candidate = block_at(probe_chunks, pcx, pcz, x & 15, y, probe_z & 15)
                if candidate not in SKIP:
                    name = candidate
                    break
            colour = (16, 18, 24) if name in SKIP else colour_of(name)
            px = (x - x0) * scale
            py = (y1 - 1 - y) * scale
            for dy in range(scale):
                for dx in range(scale):
                    canvas.set(px + dx, py + dy, (*colour, 255))
    canvas.write(args.out)
    print(f"Фасад: {args.out} ({canvas.w}x{canvas.h})")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("world")
    parser.add_argument("--regions", default="0,0", help="через ; например '0,0;-1,0'")
    parser.add_argument("--out", default="/tmp/city.png")
    parser.add_argument("--scale", type=int, default=1)
    parser.add_argument("--elevation", help="срез-фасад: 'z,x0,x1,y0,y1' в мировых координатах")
    parser.add_argument("--area", help="сверху, но по координатам: 'x0,z0,x1,z1'")
    args = parser.parse_args()

    if args.elevation:
        return elevation(args)
    if args.area:
        return area(args)

    wanted = []
    for pair in args.regions.split(";"):
        rx, rz = pair.split(",")
        wanted.append((int(rx), int(rz)))

    xs = [r[0] for r in wanted]
    zs = [r[1] for r in wanted]
    width = (max(xs) - min(xs) + 1) * 512
    height = (max(zs) - min(zs) + 1) * 512
    scale = args.scale
    canvas = Canvas(width * scale, height * scale)

    for rx, rz in wanted:
        path = os.path.join(args.world, "region", f"r.{rx}.{rz}.mca")
        if not os.path.isfile(path):
            print(f"нет файла {path}", file=sys.stderr)
            continue
        chunks = read_region(path)
        ox = (rx - min(xs)) * 512
        oz = (rz - min(zs)) * 512
        heights = [[0] * 512 for _ in range(512)]
        colours = [[FALLBACK] * 512 for _ in range(512)]
        for (cx, cz), chunk in chunks.items():
            top = surface(chunk)
            for z in range(16):
                for x in range(16):
                    name, y = top[z][x]
                    px = cx * 16 + x
                    pz = cz * 16 + z
                    heights[pz][px] = y
                    colours[pz][px] = colour_of(name)
        for z in range(512):
            for x in range(512):
                # Тень по перепаду высоты: город читается объёмным.
                north = heights[z - 1][x] if z > 0 else heights[z][x]
                delta = heights[z][x] - north
                factor = 1.0 + max(-0.35, min(0.35, delta * 0.08))
                r, g, b = colours[z][x]
                shade = (min(255, int(r * factor)), min(255, int(g * factor)),
                         min(255, int(b * factor)))
                for dy in range(scale):
                    for dx in range(scale):
                        canvas.set((ox + x) * scale + dx, (oz + z) * scale + dy,
                                   (*shade, 255))
    canvas.write(args.out)
    print(f"Картинка: {args.out} ({canvas.w}x{canvas.h})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
