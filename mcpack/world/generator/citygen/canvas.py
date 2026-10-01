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
        messages = []
        for index in range(4):
            text = lines[index] if index < len(lines) else ""
            messages.append('{"text":"' + text.replace('"', "'") + '"}')
        # Тип block entity у всех вариантов таблички один — minecraft:sign.
        # С именем блока (oak_wall_sign) игра молча выбрасывает запись при
        # загрузке чанка: "Skipping BlockEntity with id ...", и текст пропадает.
        self.block_entity(x, y, z, state, "minecraft:sign", {
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
        self.block_entity(x, y, z, state, entity_id, {"Items": nbt.List(nbt.TAG_COMPOUND)})

    def block_entity(self, x: int, y: int, z: int, state: str, entity_id: str,
                     data: dict) -> None:
        """
        Блок с block entity: полка с товаром, банка с печеньем, ящик для цветов.

        Запись о прежнем block entity в этой клетке снимается: иначе в чанке
        окажутся две записи на одно место, и игра возьмёт случайную.
        """
        if x < self.x0 or x > self.x1 or z < self.z0 or z > self.z1:
            return
        self.set(x, y, z, state)
        chunk = self._chunk(x, z)
        chunk.block_entities = [e for e in chunk.block_entities
                                if (e["x"], e["y"], e["z"]) != (x, y, z)]
        entry = {"id": entity_id, "keepPacked": nbt.Byte(0), "x": x, "y": y, "z": z,
                 "__block": state.split("[", 1)[0]}
        entry.update(data)
        chunk.block_entities.append(entry)

    def _prune_entities(self) -> None:
        """
        Убрать block entity, чей блок потом перезаписали: таблички под стеной,
        сундуки под лестницей. Игра такие записи не грузит и пишет в лог
        предупреждение о каждой, а их набегали сотни.
        """
        for chunk in self.chunks.values():
            keep = []
            seen = {}
            for entry in chunk.block_entities:
                block = entry.pop("__block", None)
                if block is not None:
                    name = self.registry.state_nbt(
                        chunk.get(entry["x"] & 15, entry["y"], entry["z"] & 15))["Name"]
                    if name != block:
                        continue
                seen[(entry["x"], entry["y"], entry["z"])] = len(keep)
                keep.append(entry)
            # Если на одно место пришлось две записи, остаётся последняя.
            chunk.block_entities = [keep[i] for i in sorted(set(seen.values()))]

    def write(self, directory: str) -> str | None:
        if not self.chunks:
            return None
        self._prune_entities()
        connect_blocks(self)
        writer = RegionWriter(self.rx, self.rz)
        for chunk in self.chunks.values():
            if not chunk.is_empty():
                writer.add(chunk, self.registry)
        return writer.write(directory)


# ---------------------------------------------------------------------------
#  Соединения заборов, стёкол-панелей, решёток и оград
# ---------------------------------------------------------------------------
#
# Игра пересчитывает, куда тянется забор, только когда рядом что-то меняется.
# Блоки, записанные прямо в чанк, так и остаются с теми соединениями, с какими
# их записали, — а генератор писал все стороны выключенными. В игре это
# выглядело как ряд одиночных столбиков вместо забора и как дыры вместо окон.
# Поэтому перед записью региона каждому такому блоку соединения считаем сами,
# ровно по правилам игры: к таким же блокам и к любой полной грани.

_SIDES = (("north", 0, -1), ("south", 0, 1), ("west", -1, 0), ("east", 1, 0))

# Всё, у чего нет полной грани: к такому забор и стекло не цепляются.
# Части имени проверяем подстрокой, короткие имена — только целиком:
# «light» подстрокой задело бы light_gray_concrete, «grass» — grass_block.
_NOT_SOLID_PARTS = ("_stairs", "_slab", "_fence", "_pane", "iron_bars", "_wall",
                    "_door", "_trapdoor", "_sign", "torch", "lantern", "_carpet",
                    "_button", "_pressure_plate", "rail", "_bed", "chest", "ladder",
                    "scaffolding", "flower_pot", "_sapling", "_banner", "tripwire",
                    "candle", "campfire", "_tulip", "_orchid", "_head", "_skull")
_NOT_SOLID_EXACT = {"air", "cave_air", "void_air", "light", "water", "lava", "grass",
                    "short_grass", "tall_grass", "fern", "large_fern", "dandelion",
                    "poppy", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
                    "lily_of_the_valley", "rose_bush", "peony", "lilac", "sunflower",
                    "snow", "lever", "cobweb", "vine", "chain", "end_rod",
                    "lightning_rod", "bell", "dead_bush", "sweet_berry_bush"}


def _family(name: str) -> str | None:
    """К какой группе соединяемых блоков относится блок, или None."""
    if name.endswith("_fence_gate"):
        return None
    if name.endswith("_fence"):
        return "nether_fence" if name == "minecraft:nether_brick_fence" else "fence"
    if name.endswith("_pane") or name == "minecraft:iron_bars":
        return "pane"
    if name.endswith("_wall") and "sign" not in name and "torch" not in name \
            and "banner" not in name and "head" not in name and "skull" not in name \
            and "fan" not in name:
        return "wall"
    return None


def furniture(name: str) -> bool:
    """
    Мебель и мелочи из модов интерьера: не полный куб. К ней не цепляются
    заборы, через неё проходит свет; так же её понимает проверка мира.
    Ящики с овощами и кирпичные крыши — обычные полные блоки.
    """
    ns, _, base = name.partition(":")
    if ns in ("mcwfurnitures", "another_furniture", "handcrafted", "mcwlights",
              "supplementaries", "citylife", "decorative_blocks"):
        return not base.endswith(("_bricks", "_tile", "lapis_bricks", "checker_block",
                                  "daub", "fine_wood", "timber_frame", "flax_block",
                                  "soap_block", "feather_block", "sugar_cube"))
    if ns == "farmersdelight":
        return not base.endswith(("_crate", "_bag", "_bale", "rich_soil", "organic_compost"))
    return ns == "mcwroofs" and "awning" in base


def _solid(name: str) -> bool:
    if furniture(name):
        return False
    base = name.split(":", 1)[-1]
    if base in _NOT_SOLID_EXACT:
        return False
    return not any(part in base for part in _NOT_SOLID_PARTS)


def connect_blocks(canvas: "RegionCanvas") -> None:
    registry = canvas.registry
    families: dict[int, str] = {}
    solid: dict[int, bool] = {}
    names: dict[int, str] = {}

    def info(block_id: int) -> tuple[str, str | None, bool]:
        name = names.get(block_id)
        if name is None:
            name = registry.state_nbt(block_id)["Name"]
            names[block_id] = name
            families[block_id] = _family(name)
            solid[block_id] = _solid(name)
        return name, families[block_id], solid[block_id]

    # Сначала узнаём, какие id вообще соединяемые, чтобы не перебирать
    # каждый блок региона: секции без заборов и стёкол пропускаются целиком.
    connectable = {i for i in range(len(registry)) if info(i)[1] is not None}
    if not connectable:
        return

    targets: list[tuple[int, int, int, int]] = []
    for (cx, cz), chunk in canvas.chunks.items():
        for sec_y, buf in chunk.sections.items():
            if connectable.isdisjoint(buf):
                continue
            for index, block_id in enumerate(buf):
                if block_id in connectable:
                    lx = index & 15
                    lz = (index >> 4) & 15
                    y = sec_y * 16 + (index >> 8)
                    targets.append((cx * 16 + lx, y, cz * 16 + lz, block_id))

    for x, y, z, block_id in targets:
        name, family, _ = info(block_id)
        state = registry.state_nbt(block_id)
        props = dict(state.get("Properties", {}))
        linked = {}
        for side, dx, dz in _SIDES:
            other = canvas.get(x + dx, y, z + dz)
            other_name, other_family, other_solid = info(other)
            gate = other_name.endswith("_fence_gate")
            if family == "pane":
                ok = other_family == "pane" or other_solid or other_name.endswith("glass")
            elif family == "wall":
                ok = other_family == "wall" or other_solid or gate
            else:
                ok = other_family == family or other_solid or gate
            linked[side] = ok

        if family == "wall":
            for side in linked:
                props[side] = "low" if linked[side] else "none"
            above = info(canvas.get(x, y + 1, z))
            straight = (linked["north"] and linked["south"] and not linked["east"]
                        and not linked["west"]) or \
                       (linked["east"] and linked["west"] and not linked["north"]
                        and not linked["south"])
            props["up"] = "false" if straight and not above[2] else "true"
        else:
            for side in linked:
                props[side] = "true" if linked[side] else "false"

        text = name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"
        canvas.set(x, y, z, text)
