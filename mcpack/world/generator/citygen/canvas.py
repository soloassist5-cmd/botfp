"""
Полотно для рисования: принимает блоки в мировых координатах и складывает
их в чанки одного региона. Всё, что выходит за границы региона, молча
отбрасывается — поэтому здание, лежащее на стыке, можно рисовать
несколько раз, по разу на каждый затронутый регион.
"""
from __future__ import annotations

from . import nbt
from .region import BlockRegistry, ChunkBuf, RegionWriter


class RegionCanvas:
    """Один регион 512x512 блоков."""

    def __init__(self, registry: BlockRegistry, region_x: int, region_z: int, terrain):
        self.registry = registry
        self.rx = region_x
        self.rz = region_z
        self.terrain = terrain
        self.chunks: dict[tuple[int, int], ChunkBuf] = {}
        self.x0 = region_x * 512
        self.z0 = region_z * 512
        self.x1 = self.x0 + 511
        self.z1 = self.z0 + 511

    def covers(self, x0: int, z0: int, x1: int, z1: int) -> bool:
        """Пересекается ли прямоугольник с этим регионом."""
        return not (x1 < self.x0 or x0 > self.x1 or z1 < self.z0 or z0 > self.z1)

    def _chunk(self, x: int, z: int) -> ChunkBuf:
        key = (x >> 4, z >> 4)
        chunk = self.chunks.get(key)
        if chunk is None:
            cx, cz = key
            biome = self.terrain.biome(cx * 16 + 8, cz * 16 + 8)
            chunk = ChunkBuf(cx, cz, biome)
            self.chunks[key] = chunk
        return chunk

    def set(self, x: int, y: int, z: int, state: str) -> None:
        if x < self.x0 or x > self.x1 or z < self.z0 or z > self.z1:
            return
        if y < -64 or y > 319:
            return
        self._chunk(x, z).set(x & 15, y, z & 15, self.registry.id_of(state))

    def get(self, x: int, y: int, z: int) -> int:
        if x < self.x0 or x > self.x1 or z < self.z0 or z > self.z1:
            return 0
        return self._chunk(x, z).get(x & 15, y, z & 15)

    def column(self, x: int, z: int, y0: int, y1: int, state: str) -> None:
        """Вертикальная колонна одного блока — основной инструмент рельефа."""
        if x < self.x0 or x > self.x1 or z < self.z0 or z > self.z1:
            return
        self._chunk(x, z).column(x & 15, z & 15, y0, y1, self.registry.id_of(state))

    def fill(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, state: str) -> None:
        block_id = self.registry.id_of(state)
        for x in range(max(x0, self.x0), min(x1, self.x1) + 1):
            for z in range(max(z0, self.z0), min(z1, self.z1) + 1):
                chunk = self._chunk(x, z)
                lx, lz = x & 15, z & 15
                for y in range(max(y0, -64), min(y1, 319) + 1):
                    chunk.set(lx, y, lz, block_id)

    def outline(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, state: str) -> None:
        """Только стены коробки (без пола и потолка)."""
        self.fill(x0, y0, z0, x1, y1, z0, state)
        self.fill(x0, y0, z1, x1, y1, z1, state)
        self.fill(x0, y0, z0, x0, y1, z1, state)
        self.fill(x1, y0, z0, x1, y1, z1, state)

    def sign(self, x: int, y: int, z: int, state: str, lines: list[str],
             color: str = "black", glowing: bool = False) -> None:
        """Табличка с текстом (нужен block entity)."""
        if x < self.x0 or x > self.x1 or z < self.z0 or z > self.z1:
            return
        self.set(x, y, z, state)
        chunk = self._chunk(x, z)
        messages = []
        for index in range(4):
            text = lines[index] if index < len(lines) else ""
            messages.append('{"text":"' + text.replace('"', "'") + '"}')
        # Тип block entity у всех вариантов таблички один — minecraft:sign.
        # С именем блока (oak_wall_sign) игра молча выбрасывает запись при
        # загрузке чанка: "Skipping BlockEntity with id ...", и текст пропадает.
        chunk.block_entities.append({
            "id": "minecraft:sign",
            "keepPacked": nbt.Byte(0),
            "x": x, "y": y, "z": z,
            "is_waxed": nbt.Byte(0),
            "front_text": {
                "has_glowing_text": nbt.Byte(1 if glowing else 0),
                "color": color,
                "messages": messages,
            },
            "back_text": {
                "has_glowing_text": nbt.Byte(0),
                "color": color,
                "messages": ['{"text":""}'] * 4,
            },
        })

    def container(self, x: int, y: int, z: int, state: str, entity_id: str) -> None:
        """Сундук, бочка, печь и т.п. — пустой block entity нужного типа."""
        if x < self.x0 or x > self.x1 or z < self.z0 or z > self.z1:
            return
        self.set(x, y, z, state)
        self._chunk(x, z).block_entities.append({
            "id": entity_id,
            "keepPacked": nbt.Byte(0),
            "x": x, "y": y, "z": z,
            "Items": nbt.List(nbt.TAG_COMPOUND),
        })

    def write(self, directory: str) -> str | None:
        if not self.chunks:
            return None
        writer = RegionWriter(self.rx, self.rz)
        for chunk in self.chunks.values():
            if not chunk.is_empty():
                writer.add(chunk, self.registry)
        return writer.write(directory)
