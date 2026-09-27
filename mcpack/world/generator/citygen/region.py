"""
Запись чанков и region-файлов (.mca) формата Anvil для Minecraft 1.20.1.

Сознательные упрощения, безопасные для игры:
  * Heightmaps не пишем — Minecraft сам пересчитает их при загрузке чанка;
  * освещение не считаем: isLightOn = 0, движок света пересчитает сам;
  * биом задаётся один на секцию (разрешение 16x16 вместо 4x4) — этого
    достаточно для города и сильно упрощает упаковку.
"""
from __future__ import annotations

import math
import os
import re
import struct
from array import array

from . import nbt

DATA_VERSION = 3465          # 1.20.1
SECTION_MIN = -4             # y = -64
SECTION_MAX = 19             # y = 319
SECTION_COUNT = SECTION_MAX - SECTION_MIN + 1

_STATE_RE = re.compile(r"^([a-z0-9_.:/]+)(?:\[(.*)\])?$")


class BlockRegistry:
    """Глобальный реестр состояний блоков: строка -> числовой id."""

    def __init__(self):
        self._ids: dict[str, int] = {}
        self._states: list[dict] = []
        self.air = self.id_of("minecraft:air")

    def id_of(self, state: str) -> int:
        existing = self._ids.get(state)
        if existing is not None:
            return existing
        match = _STATE_RE.match(state)
        if not match:
            raise ValueError(f"не разобрать состояние блока: {state!r}")
        name, props = match.group(1), match.group(2)
        if ":" not in name:
            name = "minecraft:" + name
        entry: dict = {"Name": name}
        if props:
            parsed = {}
            for pair in props.split(","):
                key, _, value = pair.partition("=")
                parsed[key.strip()] = value.strip()
            entry["Properties"] = parsed
        new_id = len(self._states)
        self._ids[state] = new_id
        self._states.append(entry)
        return new_id

    def state_nbt(self, block_id: int) -> dict:
        return self._states[block_id]

    def __len__(self):
        return len(self._states)


def pack_palette(indices, bits: int) -> list[int]:
    """Упаковать индексы палитры в long-массив без переноса через границу."""
    per_long = 64 // bits
    longs = []
    value = 0
    used = 0
    for index in indices:
        value |= (index & ((1 << bits) - 1)) << (used * bits)
        used += 1
        if used == per_long:
            longs.append(value)
            value = 0
            used = 0
    if used:
        longs.append(value)
    return longs


class ChunkBuf:
    """Чанк в процессе генерации: разреженный набор секций по 4096 блоков."""

    __slots__ = ("cx", "cz", "sections", "biome", "block_entities", "entities")

    def __init__(self, cx: int, cz: int, biome: str = "minecraft:plains"):
        self.cx = cx
        self.cz = cz
        self.biome = biome
        self.sections: dict[int, array] = {}
        self.block_entities: list[dict] = []
        self.entities: list[dict] = []

    def _section(self, sec_y: int) -> array:
        buf = self.sections.get(sec_y)
        if buf is None:
            buf = array("H", bytes(8192))   # 4096 нулей = air (id 0)
            self.sections[sec_y] = buf
        return buf

    def set(self, lx: int, y: int, lz: int, block_id: int) -> None:
        sec_y = y >> 4
        if sec_y < SECTION_MIN or sec_y > SECTION_MAX:
            return
        self._section(sec_y)[((y & 15) << 8) | (lz << 4) | lx] = block_id

    def get(self, lx: int, y: int, lz: int) -> int:
        buf = self.sections.get(y >> 4)
        if buf is None:
            return 0
        return buf[((y & 15) << 8) | (lz << 4) | lx]

    def is_empty(self) -> bool:
        return not self.sections and not self.block_entities and not self.entities

    def to_nbt(self, registry: BlockRegistry) -> dict:
        sections = []
        for sec_y in range(SECTION_MIN, SECTION_MAX + 1):
            buf = self.sections.get(sec_y)
            section: dict = {"Y": nbt.Byte(sec_y)}
            if buf is None:
                section["block_states"] = {
                    "palette": [{"Name": "minecraft:air"}],
                }
            else:
                local: dict[int, int] = {}
                indices = array("H", bytes(8192))
                for pos in range(4096):
                    gid = buf[pos]
                    local_index = local.get(gid)
                    if local_index is None:
                        local_index = len(local)
                        local[gid] = local_index
                    indices[pos] = local_index
                palette = [registry.state_nbt(gid) for gid in local]
                block_states: dict = {"palette": palette}
                if len(palette) > 1:
                    bits = max(4, (len(palette) - 1).bit_length())
                    block_states["data"] = nbt.LongArray(pack_palette(indices, bits))
                section["block_states"] = block_states
            section["biomes"] = {"palette": [self.biome]}
            sections.append(section)

        chunk: dict = {
            "DataVersion": nbt.Int(DATA_VERSION),
            "xPos": nbt.Int(self.cx),
            "zPos": nbt.Int(self.cz),
            "yPos": nbt.Int(SECTION_MIN),
            "Status": "minecraft:full",
            "LastUpdate": nbt.Long(0),
            "InhabitedTime": nbt.Long(0),
            "isLightOn": nbt.Byte(0),
            "sections": sections,
            "block_entities": (self.block_entities if self.block_entities
                               else nbt.List(nbt.TAG_COMPOUND)),
            "block_ticks": nbt.List(nbt.TAG_COMPOUND),
            "fluid_ticks": nbt.List(nbt.TAG_COMPOUND),
            "PostProcessing": nbt.List(nbt.TAG_LIST,
                                       [nbt.List(nbt.TAG_SHORT) for _ in range(SECTION_COUNT)]),
            "structures": {"starts": {}, "References": {}},
        }
        return chunk


class RegionWriter:
    """Сборка одного файла r.X.Z.mca."""

    def __init__(self, region_x: int, region_z: int):
        self.rx = region_x
        self.rz = region_z
        self.payloads: dict[int, bytes] = {}   # индекс в заголовке -> сжатый чанк

    def add(self, chunk: ChunkBuf, registry: BlockRegistry) -> None:
        local_x = chunk.cx - self.rx * 32
        local_z = chunk.cz - self.rz * 32
        if not (0 <= local_x < 32 and 0 <= local_z < 32):
            raise ValueError(f"чанк {chunk.cx},{chunk.cz} не из региона {self.rx},{self.rz}")
        self.payloads[local_z * 32 + local_x] = nbt.zlib_bytes(chunk.to_nbt(registry))

    def write(self, directory: str) -> str:
        path = os.path.join(directory, f"r.{self.rx}.{self.rz}.mca")
        locations = bytearray(4096)
        timestamps = bytearray(4096)
        body = bytearray()
        sector = 2   # два сектора заголовка
        for index in sorted(self.payloads):
            data = self.payloads[index]
            block = struct.pack(">IB", len(data) + 1, 2) + data   # 2 = zlib
            pad = (-len(block)) % 4096
            block += b"\x00" * pad
            sectors = len(block) // 4096
            if sectors > 255:
                raise ValueError("чанк не влезает в 255 секторов")
            off = index * 4
            locations[off:off + 3] = sector.to_bytes(3, "big")
            locations[off + 3] = sectors
            timestamps[off:off + 4] = (0).to_bytes(4, "big")
            body += block
            sector += sectors
        os.makedirs(directory, exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(locations)
            fh.write(timestamps)
            fh.write(body)
        return path


def bits_for(palette_size: int) -> int:
    """Сколько бит на запись нужно для палитры такого размера."""
    return max(4, math.ceil(math.log2(palette_size)) if palette_size > 1 else 4)
