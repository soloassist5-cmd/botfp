#!/usr/bin/env python3
"""
Проверка сгенерированного мира без запуска Minecraft.

Читает region-файлы обратно и сверяет ключевые точки города: асфальт на
улицах, тротуары, настил пирса, полотно эстакады, рельсы метро и наличие
блок-сущностей у табличек.

    python3 verify_world.py ../los-santos
"""
from __future__ import annotations

import gzip
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from citygen import nbt  # noqa: E402
from citygen.plan import CITY_Y, FREEWAY_Y, METRO_Y, SPAWN  # noqa: E402
from citygen.terrain import SEA_LEVEL  # noqa: E402


class WorldReader:
    """Чтение блоков из готового мира по мировым координатам."""

    def __init__(self, world: str):
        self.region_dir = os.path.join(world, "region")
        self._cache: dict[tuple[int, int], dict] = {}

    def chunk(self, cx: int, cz: int) -> dict | None:
        key = (cx, cz)
        if key in self._cache:
            return self._cache[key]
        rx, rz = cx >> 5, cz >> 5
        path = os.path.join(self.region_dir, f"r.{rx}.{rz}.mca")
        if not os.path.exists(path):
            self._cache[key] = None
            return None
        with open(path, "rb") as fh:
            header = fh.read(4096)
            index = ((cz & 31) * 32 + (cx & 31)) * 4
            offset = int.from_bytes(header[index:index + 3], "big")
            sectors = header[index + 3]
            if offset == 0 or sectors == 0:
                self._cache[key] = None
                return None
            fh.seek(offset * 4096)
            length, compression = struct.unpack(">IB", fh.read(5))
            payload = fh.read(length - 1)
        if compression != 2:
            raise ValueError(f"неожиданный тип сжатия {compression}")
        _, data = nbt.loads(zlib.decompress(payload))
        self._cache[key] = data
        return data

    def block(self, x: int, y: int, z: int) -> str:
        data = self.chunk(x >> 4, z >> 4)
        if data is None:
            return "<нет чанка>"
        for section in data.get("sections", []):
            if section.get("Y") != (y >> 4):
                continue
            states = section.get("block_states") or {}
            palette = states.get("palette") or []
            if not palette:
                return "minecraft:air"
            if "data" not in states:
                return palette[0]["Name"]
            bits = max(4, (len(palette) - 1).bit_length())
            per_long = 64 // bits
            position = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
            longs = states["data"]
            value = longs[position // per_long]
            shift = (position % per_long) * bits
            return palette[(value >> shift) & ((1 << bits) - 1)]["Name"]
        return "minecraft:air"

    def block_entities(self, x: int, z: int) -> list[dict]:
        data = self.chunk(x >> 4, z >> 4)
        return list(data.get("block_entities", [])) if data else []

    def biome(self, x: int, y: int, z: int) -> str:
        data = self.chunk(x >> 4, z >> 4)
        if data is None:
            return "<нет чанка>"
        for section in data.get("sections", []):
            if section.get("Y") == (y >> 4):
                palette = (section.get("biomes") or {}).get("palette") or []
                return palette[0] if palette else "?"
        return "?"


CHECKS = [
    # (описание, x, y, z, ожидаемые варианты)
    ("тротуар у точки появления", SPAWN[0], CITY_Y, SPAWN[2], ["minecraft:smooth_stone"]),
    ("асфальт проспекта", 0, CITY_Y, 100, ["minecraft:black_concrete",
                                           "minecraft:yellow_concrete",
                                           "minecraft:white_concrete"]),
    ("разметка проспекта", -1, CITY_Y, 100, ["minecraft:yellow_concrete"]),
    ("асфальт обычной улицы", 64, CITY_Y, 100, ["minecraft:black_concrete",
                                                "minecraft:yellow_concrete",
                                                "minecraft:white_concrete"]),
    ("перекрёсток без разметки", 0, CITY_Y, 0, ["minecraft:black_concrete"]),
    ("зебра у перекрёстка", 0, CITY_Y, 10, ["minecraft:white_concrete",
                                            "minecraft:black_concrete"]),
    ("полотно эстакады", 448, FREEWAY_Y, 100, ["minecraft:gray_concrete",
                                               "minecraft:yellow_concrete"]),
    ("опора эстакады", 444, CITY_Y + 3, 96, ["minecraft:gray_concrete",
                                             "minecraft:air"]),
    ("настил пирса", -600, SEA_LEVEL + 2, 32, ["minecraft:spruce_planks"]),
    ("вода океана", -900, SEA_LEVEL, 0, ["minecraft:water"]),
    ("рельсы метро (центр)", 33, METRO_Y + 1, 158, ["minecraft:rail",
                                                    "minecraft:powered_rail"]),
    ("бедрок на дне", 0, -64, 0, ["minecraft:bedrock"]),
    ("камень под городом", 0, 20, 0, ["minecraft:stone"]),
    ("глубинный сланец", 0, -30, 0, ["minecraft:deepslate"]),
    ("воздух над городом", 0, CITY_Y + 40, 0, ["minecraft:air"]),
]


def main() -> int:
    if len(sys.argv) < 2:
        print("укажи каталог мира", file=sys.stderr)
        return 2
    world = sys.argv[1]
    reader = WorldReader(world)

    failures = 0
    for label, x, y, z, expected in CHECKS:
        got = reader.block(x, y, z)
        ok = got in expected
        if not ok:
            failures += 1
        print(f"  {'OK ' if ok else 'НЕТ'} {label:28} ({x},{y},{z}) = {got}")

    # level.dat: поля версии. Клиент читает "version" как целое (19133 — формат
    # Anvil) и, если там лежит что-то другое, молча прячет мир из списка
    # одиночных — выделенный сервер при этом грузит его как ни в чём не бывало.
    level_path = os.path.join(world, "level.dat")
    if not os.path.isfile(level_path):
        print("  НЕТ level.dat не найден")
        failures += 1
    else:
        with gzip.open(level_path, "rb") as fh:
            _, level_root = nbt.loads(fh.read())
        info = level_root.get("Data", {})
        storage = info.get("version")
        game = info.get("Version")
        ok = storage == 19133 and isinstance(game, dict) and game.get("Name") == "1.20.1"
        print(f"  {'OK ' if ok else 'НЕТ'} level.dat: version={storage} (нужно 19133), "
              f"Version.Name={game.get('Name') if isinstance(game, dict) else game}")
        if not ok:
            failures += 1

    # Таблички: у свободных участков должны быть блок-сущности с текстом.
    signs = 0
    bad_sign_ids: set[str] = set()
    sale_signs = 0
    for cx in range(0, 8):
        for cz in range(0, 8):
            for entity in reader.block_entities(cx * 16, cz * 16):
                if "sign" in entity.get("id", ""):
                    signs += 1
                    # Тип block entity обязан быть minecraft:sign: с именем блока
                    # игра выбрасывает запись при загрузке и текст пропадает.
                    if entity.get("id") != "minecraft:sign":
                        bad_sign_ids.add(entity.get("id"))
                    text = " ".join(entity.get("front_text", {}).get("messages", []))
                    if "ПРОДА" in text:
                        sale_signs += 1
    print(f"  {'OK ' if signs else 'НЕТ'} табличек в 64 чанках центра: {signs} "
          f"(из них «продаётся»: {sale_signs})")
    if bad_sign_ids:
        print(f"  НЕТ у табличек неверный тип block entity: {sorted(bad_sign_ids)} — "
              f"игра выбросит их текст")
        failures += 1
    if not signs:
        failures += 1

    # Биомы.
    for label, x, z, expected in [("центр города", 0, 0, "minecraft:plains"),
                                  ("океан", -900, 0, "minecraft:ocean"),
                                  ("пустыня", 700, 0, "minecraft:desert")]:
        got = reader.biome(x, CITY_Y, z)
        ok = got == expected
        if not ok:
            failures += 1
        print(f"  {'OK ' if ok else 'НЕТ'} биом {label:22} = {got}")

    regions = len([n for n in os.listdir(os.path.join(world, "region"))
                   if n.endswith(".mca")])
    size = sum(os.path.getsize(os.path.join(world, "region", n))
               for n in os.listdir(os.path.join(world, "region")))
    print(f"\nрегионов: {regions}, размер region/: {size / 1048576:.1f} МБ")
    print("ВСЁ СОШЛОСЬ" if failures == 0 else f"ПРОБЛЕМ: {failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
