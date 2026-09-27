"""
Генераторы застройки: от частного дома до небоскрёба.

Все постройки рисуются ванильными блоками и вписываются в свой квартал,
оставляя тротуары свободными. Каждая функция получает уже обрезанное по
региону полотно, поэтому может писать в мировых координатах не думая о
границах файлов региона.
"""
from __future__ import annotations

import random

from . import blocks as B
from .canvas import RegionCanvas
from .plan import CITY_Y, Lot

FLOOR_HEIGHT = 4
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}


# ---------------------------------------------------------------------------
#  Общие помощники
# ---------------------------------------------------------------------------

def _rng(lot: Lot, salt: int = 0) -> random.Random:
    return random.Random(lot.seed ^ (salt * 0x9E3779B1))


def pad(lot: Lot, margin: int) -> tuple[int, int, int, int]:
    """Уменьшить участок со всех сторон."""
    return lot.x0 + margin, lot.z0 + margin, lot.x1 - margin, lot.z1 - margin


def front_center(x0: int, z0: int, x1: int, z1: int, facing: str) -> tuple[int, int]:
    """Точка на фасадной стене, где будет вход."""
    if facing == "north":
        return (x0 + x1) // 2, z0
    if facing == "south":
        return (x0 + x1) // 2, z1
    if facing == "west":
        return x0, (z0 + z1) // 2
    return x1, (z0 + z1) // 2


def place_door(canvas: RegionCanvas, x: int, y: int, z: int, facing: str,
               iron: bool = False) -> None:
    """Дверь, открывающаяся внутрь здания."""
    top = B.IRON_DOOR_TOP if iron else B.DOOR_TOP
    bottom = B.IRON_DOOR_BOTTOM if iron else B.DOOR_BOTTOM
    inward = OPPOSITE[facing]
    canvas.set(x, y, z, bottom.format(f=inward))
    canvas.set(x, y + 1, z, top.format(f=inward))


def wall_sign(canvas: RegionCanvas, x: int, y: int, z: int, facing: str,
              lines: list[str], color: str = "white", glowing: bool = True) -> None:
    canvas.sign(x, y, z, B.SIGN_WALL.format(f=facing), lines, color=color, glowing=glowing)


def signboard(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
              facing: str, label: str, accent: str) -> None:
    """Вывеска над входом: полоса акцентного цвета и табличка с названием."""
    if not label:
        return
    if facing in ("north", "south"):
        z = z0 - 1 if facing == "north" else z1 + 1
        canvas.fill(x0 + 2, y, z, x1 - 2, y + 1, z, accent)
        cx = (x0 + x1) // 2
        wall_sign(canvas, cx, y, z, facing, _split_label(label))
    else:
        x = x0 - 1 if facing == "west" else x1 + 1
        canvas.fill(x, y, z0 + 2, x, y + 1, z1 - 2, accent)
        cz = (z0 + z1) // 2
        wall_sign(canvas, x, y, cz, facing, _split_label(label))


def _split_label(label: str) -> list[str]:
    words = label.split()
    lines: list[str] = []
    current = ""
    for word in words:
        if len(current) + len(word) + 1 <= 15:
            current = f"{current} {word}".strip()
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines[:4]


def lamp_ceiling(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
                 step: int = 6) -> None:
    for x in range(x0 + 2, x1 - 1, step):
        for z in range(z0 + 2, z1 - 1, step):
            canvas.set(x, y, z, B.GLOWSTONE)


def window_band(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
                glass: str, step: int = 3) -> None:
    """Лента окон по периметру на высоте y."""
    for x in range(x0 + 1, x1):
        if (x - x0) % step != 0:
            canvas.set(x, y, z0, glass)
            canvas.set(x, y, z1, glass)
    for z in range(z0 + 1, z1):
        if (z - z0) % step != 0:
            canvas.set(x0, y, z, glass)
            canvas.set(x1, y, z, glass)


def parapet(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
            material: str) -> None:
    canvas.outline(x0, y, z0, x1, y, z1, material)
    canvas.outline(x0, y + 1, z0, x1, y + 1, z1, B.slab(_slab_name(material), top=False))


def _slab_name(material: str) -> str:
    """Подобрать плиту к материалу стены."""
    if "concrete" in material:
        return "smooth_stone"
    if "terracotta" in material:
        return "sandstone"
    if material == B.BRICKS:
        return "brick"
    if material == B.QUARTZ:
        return "quartz"
    return "stone"


def roof_units(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
               rng: random.Random) -> None:
    """Вентиляция и надстройки на плоской крыше."""
    for _ in range(rng.randrange(2, 5)):
        x = rng.randrange(x0 + 3, max(x0 + 4, x1 - 4))
        z = rng.randrange(z0 + 3, max(z0 + 4, z1 - 4))
        size = rng.choice((1, 1, 2))
        canvas.fill(x, y, z, x + size, y + 1, z + size, B.CONCRETE_LIGHT)
        canvas.fill(x, y + 2, z, x + size, y + 2, z + size, B.IRON_BARS)


def stair_shaft(canvas: RegionCanvas, x: int, z: int, y_from: int, y_to: int) -> None:
    """Лестничная шахта: колодец с лестницей по стене."""
    canvas.fill(x, y_from, z, x + 1, y_to, z + 1, B.AIR)
    for y in range(y_from, y_to + 1):
        canvas.set(x, y, z, B.LADDER_N.replace("facing=north", "facing=south"))


def interior_floor(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
                   material: str) -> None:
    canvas.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, material)


def counter(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
            facing: str, material: str) -> None:
    """Прилавок вдоль дальней от входа стены — за ним встанет NPC."""
    if facing in ("north", "south"):
        z = z1 - 3 if facing == "north" else z0 + 3
        canvas.fill(x0 + 3, y, z, x1 - 3, y, z, material)
        canvas.fill(x0 + 3, y + 1, z, x1 - 3, y + 1, z,
                    B.slab(_slab_name(material), top=False))
    else:
        x = x1 - 3 if facing == "west" else x0 + 3
        canvas.fill(x, y, z0 + 3, x, y, z1 - 3, material)
        canvas.fill(x, y + 1, z0 + 3, x, y + 1, z1 - 3,
                    B.slab(_slab_name(material), top=False))


def shell(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y_base: int,
          floors: int, wall: str, glass: str, accent: str, facing: str,
          rng: random.Random, floor_height: int = FLOOR_HEIGHT,
          interior: str = B.CONCRETE_LIGHT) -> int:
    """
    Каркас здания: фундамент, стены, окна, межэтажные перекрытия, крыша.
    Возвращает высоту крыши.
    """
    height = floors * floor_height
    # Фундамент и первый пол.
    canvas.fill(x0, y_base - 2, z0, x1, y_base - 1, z1, B.CONCRETE_GRAY)
    interior_floor(canvas, x0, z0, x1, z1, y_base, interior)

    # Стены на всю высоту.
    canvas.outline(x0, y_base + 1, z0, x1, y_base + height, z1, wall)

    for level in range(floors):
        floor_y = y_base + level * floor_height
        # Окна: две ленты на этаж.
        window_band(canvas, x0, z0, x1, z1, floor_y + 2, glass)
        window_band(canvas, x0, z0, x1, z1, floor_y + 3, glass)
        if level > 0:
            interior_floor(canvas, x0, z0, x1, z1, floor_y, interior)
            canvas.outline(x0, floor_y, z0, x1, floor_y, z1, accent)
        lamp_ceiling(canvas, x0, z0, x1, z1, floor_y + floor_height - 1)

    roof_y = y_base + height + 1
    canvas.fill(x0, roof_y, z0, x1, roof_y, z1, B.CONCRETE_GRAY)
    parapet(canvas, x0, z0, x1, z1, roof_y + 1, wall)
    roof_units(canvas, x0, z0, x1, z1, roof_y + 1, rng)

    # Вход.
    dx, dz = front_center(x0, z0, x1, z1, facing)
    canvas.fill(dx - 1, y_base + 1, dz, dx + 1, y_base + 3, dz, B.AIR)
    canvas.fill(dx - 1, y_base + 3, dz, dx + 1, y_base + 3, dz, accent)
    place_door(canvas, dx, y_base + 1, dz, facing)
    canvas.set(dx - 1, y_base + 1, dz, glass)
    canvas.set(dx - 1, y_base + 2, dz, glass)
    canvas.set(dx + 1, y_base + 1, dz, glass)
    canvas.set(dx + 1, y_base + 2, dz, glass)

    # Лестница между этажами.
    if floors > 1:
        stair_shaft(canvas, x0 + 2, z0 + 2, y_base + 1, y_base + height)
    return roof_y


# ---------------------------------------------------------------------------
#  Конкретные типы застройки
# ---------------------------------------------------------------------------

def build_generic(canvas: RegionCanvas, lot: Lot, floors: int, palette_index: int | None = None,
                  interior_kind: str = "counter") -> None:
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 1)
    wall, accent, glass, _ = B.FACADE_SETS[
        rng.randrange(len(B.FACADE_SETS)) if palette_index is None else palette_index]
    roof_y = shell(canvas, x0, z0, x1, z1, CITY_Y, floors, wall, glass, accent,
                   lot.facing, rng)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 4, lot.facing, lot.label, accent)
    if interior_kind == "counter":
        counter(canvas, x0, z0, x1, z1, CITY_Y + 1, lot.facing, B.QUARTZ_SMOOTH)
    elif interior_kind == "tables":
        _tables(canvas, x0, z0, x1, z1, CITY_Y + 1, rng)
    if floors >= 3:
        canvas.set((x0 + x1) // 2, roof_y + 2, (z0 + z1) // 2, B.REDSTONE_LAMP_ON)


def _tables(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
            rng: random.Random) -> None:
    for x in range(x0 + 4, x1 - 3, 5):
        for z in range(z0 + 4, z1 - 3, 5):
            canvas.set(x, y, z, B.slab("spruce", top=False).replace("spruce", "spruce"))
            canvas.set(x, y + 1, z, B.slab("spruce", top=False))
            canvas.set(x + 1, y, z, B.stairs("spruce", facing="west"))
            canvas.set(x - 1, y, z, B.stairs("spruce", facing="east"))
            if rng.random() < 0.4:
                canvas.set(x, y + 2, z, B.FLOWER_POT)


def build_tower(canvas: RegionCanvas, lot: Lot) -> None:
    """Небоскрёб со сужением и стеклянным фасадом."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 2)
    floors_low = rng.randrange(9, 12)
    floors_high = rng.randrange(5, 8)
    wall = rng.choice((B.CONCRETE_LIGHT, B.QUARTZ, B.CONCRETE_WHITE))
    glass = rng.choice((B.GLASS_BLUE, B.GLASS_CYAN, B.GLASS_GRAY, B.GLASS_TINTED))
    accent = B.CONCRETE_GRAY

    roof_low = shell(canvas, x0, z0, x1, z1, CITY_Y, floors_low, wall, glass, accent,
                     lot.facing, rng, interior=B.CONCRETE_GRAY)

    # Сужение верхней части.
    sx0, sz0, sx1, sz1 = x0 + 4, z0 + 4, x1 - 4, z1 - 4
    canvas.fill(sx0, roof_low, sz0, sx1, roof_low, sz1, B.CONCRETE_GRAY)
    top_y = roof_low
    for level in range(floors_high):
        floor_y = top_y + level * FLOOR_HEIGHT
        canvas.outline(sx0, floor_y + 1, sz0, sx1, floor_y + FLOOR_HEIGHT, sz1, wall)
        window_band(canvas, sx0, sz0, sx1, sz1, floor_y + 2, glass)
        window_band(canvas, sx0, sz0, sx1, sz1, floor_y + 3, glass)
        canvas.fill(sx0 + 1, floor_y, sz0 + 1, sx1 - 1, floor_y, sz1 - 1, B.CONCRETE_GRAY)
        lamp_ceiling(canvas, sx0, sz0, sx1, sz1, floor_y + FLOOR_HEIGHT - 1, 5)
    crown_y = top_y + floors_high * FLOOR_HEIGHT + 1
    canvas.fill(sx0, crown_y, sz0, sx1, crown_y, sz1, B.CONCRETE_GRAY)
    parapet(canvas, sx0, sz0, sx1, sz1, crown_y + 1, wall)

    # Антенна с проблесковым огнём.
    cx, cz = (sx0 + sx1) // 2, (sz0 + sz1) // 2
    canvas.fill(cx, crown_y + 1, cz, cx, crown_y + 8, cz, B.IRON_BARS)
    canvas.set(cx, crown_y + 9, cz, B.REDSTONE_LAMP_ON)
    stair_shaft(canvas, sx0 + 2, sz0 + 2, roof_low, crown_y)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 5, lot.facing, lot.label, accent)


def build_mall(canvas: RegionCanvas, lot: Lot) -> None:
    """Двухэтажный торговый центр с 12 арендными точками и атриумом."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 1)
    wall, accent, glass = B.CONCRETE_WHITE, B.CONCRETE_CYAN, B.GLASS_BLUE

    canvas.fill(x0, CITY_Y - 2, z0, x1, CITY_Y - 1, z1, B.CONCRETE_GRAY)
    interior_floor(canvas, x0, z0, x1, z1, CITY_Y, B.QUARTZ_SMOOTH)
    canvas.outline(x0, CITY_Y + 1, z0, x1, CITY_Y + 10, z1, wall)

    for level, base in enumerate((CITY_Y, CITY_Y + 5)):
        window_band(canvas, x0, z0, x1, z1, base + 2, glass, step=2)
        window_band(canvas, x0, z0, x1, z1, base + 3, glass, step=2)
        if level == 1:
            # Второй уровень — галерея по периметру, в центре атриум.
            canvas.fill(x0 + 1, base, z0 + 1, x1 - 1, base, z1 - 1, B.QUARTZ_SMOOTH)
            canvas.fill(x0 + 8, base, z0 + 8, x1 - 8, base, z1 - 8, B.AIR)
            for x in range(x0 + 8, x1 - 7):
                canvas.set(x, base + 1, z0 + 8, B.IRON_BARS)
                canvas.set(x, base + 1, z1 - 8, B.IRON_BARS)
            for z in range(z0 + 8, z1 - 7):
                canvas.set(x0 + 8, base + 1, z, B.IRON_BARS)
                canvas.set(x1 - 8, base + 1, z, B.IRON_BARS)
        lamp_ceiling(canvas, x0, z0, x1, z1, base + 4, 5)

    # Арендные точки: по 3 на каждой стороне на первом этаже, и столько же выше.
    unit = 0
    for level_base in (CITY_Y, CITY_Y + 5):
        for side in ("north", "south", "west", "east"):
            for slot in range(3):
                unit += 1
                _mall_unit(canvas, x0, z0, x1, z1, level_base, side, slot, unit, rng)

    # Эскалатор из атриума на второй уровень.
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for step in range(5):
        canvas.fill(cx - 1, CITY_Y + 1 + step, cz + step, cx + 1, CITY_Y + 1 + step, cz + step,
                    B.stairs("quartz", facing="north"))

    # Крыша с фонарями верхнего света.
    roof_y = CITY_Y + 11
    canvas.fill(x0, roof_y, z0, x1, roof_y, z1, B.CONCRETE_GRAY)
    canvas.fill(x0 + 9, roof_y, z0 + 9, x1 - 9, roof_y, z1 - 9, B.GLASS)
    parapet(canvas, x0, z0, x1, z1, roof_y + 1, wall)
    roof_units(canvas, x0, z0, x1, z1, roof_y + 1, rng)

    dx, dz = front_center(x0, z0, x1, z1, lot.facing)
    canvas.fill(dx - 2, CITY_Y + 1, dz, dx + 2, CITY_Y + 4, dz, B.AIR)
    canvas.fill(dx - 2, CITY_Y + 1, dz, dx + 2, CITY_Y + 3, dz, B.GLASS)
    place_door(canvas, dx, CITY_Y + 1, dz, lot.facing)
    place_door(canvas, dx + 1, CITY_Y + 1, dz, lot.facing)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 6, lot.facing, lot.label, accent)


def _mall_unit(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, base: int,
               side: str, slot: int, unit: int, rng: random.Random) -> None:
    """Одна арендная точка: прилавок, витрина и номер на табличке."""
    span = 5
    if side in ("north", "south"):
        width = x1 - x0
        start = x0 + 4 + slot * (width - 8) // 3
        z = z0 + 2 if side == "north" else z1 - 2
        canvas.fill(start, base + 1, z, start + span - 1, base + 1, z, B.QUARTZ_SMOOTH)
        canvas.fill(start, base + 2, z, start + span - 1, base + 2, z,
                    B.slab("quartz", top=False))
        canvas.sign(start + 2, base + 3, z,
                    B.SIGN_WALL.format(f="south" if side == "north" else "north"),
                    [f"ТОЧКА #{unit}", "СВОБОДНА"], color="blue", glowing=True)
        canvas.container(start + span - 1, base + 1, z + (1 if side == "north" else -1),
                         B.BARREL, "minecraft:barrel")
    else:
        depth = z1 - z0
        start = z0 + 4 + slot * (depth - 8) // 3
        x = x0 + 2 if side == "west" else x1 - 2
        canvas.fill(x, base + 1, start, x, base + 1, start + span - 1, B.QUARTZ_SMOOTH)
        canvas.fill(x, base + 2, start, x, base + 2, start + span - 1,
                    B.slab("quartz", top=False))
        canvas.sign(x, base + 3, start + 2,
                    B.SIGN_WALL.format(f="east" if side == "west" else "west"),
                    [f"ТОЧКА #{unit}", "СВОБОДНА"], color="blue", glowing=True)
        canvas.container(x + (1 if side == "west" else -1), base + 1, start + span - 1,
                         B.BARREL, "minecraft:barrel")


def build_house(canvas: RegionCanvas, lot: Lot, villa: bool = False) -> None:
    """Частный дом с двускатной крышей, гаражом и двориком."""
    rng = _rng(lot)
    margin = 4 if not villa else 3
    x0, z0, x1, z1 = pad(lot, margin)
    # Дом занимает не весь участок: остаётся место под двор и постройки.
    depth = (z1 - z0) * (3 if villa else 2) // 5
    width = (x1 - x0) * (3 if villa else 2) // 5
    hx0, hz0 = x0, z0
    hx1, hz1 = x0 + max(9, width), z0 + max(9, depth)

    wall, planks, roof_mat, accent = B.HOUSE_SETS[rng.randrange(len(B.HOUSE_SETS))]
    floors = 2 if (villa or rng.random() < 0.45) else 1

    canvas.fill(hx0, CITY_Y - 2, hz0, hx1, CITY_Y - 1, hz1, B.CONCRETE_GRAY)
    interior_floor(canvas, hx0, hz0, hx1, hz1, CITY_Y, planks)
    height = floors * FLOOR_HEIGHT
    canvas.outline(hx0, CITY_Y + 1, hz0, hx1, CITY_Y + height, hz1, wall)
    for level in range(floors):
        base = CITY_Y + level * FLOOR_HEIGHT
        window_band(canvas, hx0, hz0, hx1, hz1, base + 2, B.GLASS_PANE, step=4)
        window_band(canvas, hx0, hz0, hx1, hz1, base + 3, B.GLASS_PANE, step=4)
        if level > 0:
            interior_floor(canvas, hx0, hz0, hx1, hz1, base, planks)
        lamp_ceiling(canvas, hx0, hz0, hx1, hz1, base + FLOOR_HEIGHT - 1, 5)
    if floors > 1:
        stair_shaft(canvas, hx0 + 2, hz0 + 2, CITY_Y + 1, CITY_Y + height)

    _gable_roof(canvas, hx0, hz0, hx1, hz1, CITY_Y + height + 1, roof_mat)

    # Вход со стороны улицы.
    dx, dz = front_center(hx0, hz0, hx1, hz1, lot.facing)
    canvas.fill(dx, CITY_Y + 1, dz, dx, CITY_Y + 2, dz, B.AIR)
    place_door(canvas, dx, CITY_Y + 1, dz, lot.facing)
    canvas.set(dx, CITY_Y + 3, dz, B.LANTERN_HANGING)

    # Мебель: кровать, сундук, верстак.
    canvas.set(hx1 - 2, CITY_Y + 1, hz1 - 2, B.BED_RED)
    canvas.set(hx1 - 2, CITY_Y + 1, hz1 - 3, B.BED_RED_HEAD)
    canvas.container(hx1 - 4, CITY_Y + 1, hz1 - 2, B.CHEST_N, "minecraft:chest")
    canvas.set(hx0 + 3, CITY_Y + 1, hz1 - 2, B.CRAFTING)

    # Гараж с подъездом к улице.
    _garage(canvas, lot, hx1, hz0, hz1, rng)

    # Двор: газон, дерево, забор по границе участка.
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.GRASS)
    canvas.fill(hx0, CITY_Y, hz0, hx1, CITY_Y, hz1, planks)
    _fence_yard(canvas, x0, z0, x1, z1, lot.facing)
    _tree(canvas, x1 - 3, z1 - 3, rng)
    if villa:
        _pool(canvas, x1 - 12, z1 - 10, x1 - 3, z1 - 3)


def _gable_roof(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
                material: str) -> None:
    """Двускатная крыша из ступеней."""
    width = min(x1 - x0, z1 - z0)
    along_x = (x1 - x0) >= (z1 - z0)
    for step in range((width // 2) + 1):
        level = y + step
        if along_x:
            zz0, zz1 = z0 + step, z1 - step
            if zz0 > zz1:
                break
            canvas.fill(x0 - 1, level, zz0, x1 + 1, level, zz0,
                        material_stairs(material, "south"))
            canvas.fill(x0 - 1, level, zz1, x1 + 1, level, zz1,
                        material_stairs(material, "north"))
            canvas.fill(x0, level, zz0 + 1, x1, level, zz1 - 1, B.AIR)
            if zz1 - zz0 <= 2:
                canvas.fill(x0 - 1, level + 1, zz0, x1 + 1, level + 1, zz1,
                            B.slab(_roof_slab(material), top=False))
                break
        else:
            xx0, xx1 = x0 + step, x1 - step
            if xx0 > xx1:
                break
            canvas.fill(xx0, level, z0 - 1, xx0, level, z1 + 1,
                        material_stairs(material, "east"))
            canvas.fill(xx1, level, z0 - 1, xx1, level, z1 + 1,
                        material_stairs(material, "west"))
            canvas.fill(xx0 + 1, level, z0, xx1 - 1, level, z1, B.AIR)
            if xx1 - xx0 <= 2:
                canvas.fill(xx0, level + 1, z0 - 1, xx1, level + 1, z1 + 1,
                            B.slab(_roof_slab(material), top=False))
                break


def material_stairs(material: str, facing: str) -> str:
    return B.stairs(_roof_slab(material), facing=facing)


def _roof_slab(material: str) -> str:
    if material in ("brick", "sandstone", "spruce", "oak", "quartz"):
        return material
    if "terracotta" in material:
        return "sandstone"
    if "concrete" in material:
        return "smooth_stone"
    return "brick"


def _garage(canvas: RegionCanvas, lot: Lot, x_from: int, z0: int, z1: int,
            rng: random.Random) -> None:
    """Пристроенный гараж с воротами и бетонным подъездом."""
    gx0 = x_from + 1
    gx1 = min(gx0 + 7, lot.x1 - 1)
    gz0 = z0
    gz1 = min(z0 + 7, z1)
    if gx1 - gx0 < 4:
        return
    canvas.fill(gx0, CITY_Y, gz0, gx1, CITY_Y, gz1, B.CONCRETE_GRAY)
    canvas.outline(gx0, CITY_Y + 1, gz0, gx1, CITY_Y + 4, gz1, B.CONCRETE_LIGHT)
    canvas.fill(gx0, CITY_Y + 5, gz0, gx1, CITY_Y + 5, gz1, B.CONCRETE_GRAY)
    # Ворота смотрят на улицу.
    if lot.facing == "north":
        canvas.fill(gx0 + 1, CITY_Y + 1, gz0, gx1 - 1, CITY_Y + 3, gz0, B.AIR)
        canvas.fill(gx0 + 1, CITY_Y + 4, gz0, gx1 - 1, CITY_Y + 4, gz0, B.IRON_BLOCK)
    else:
        canvas.fill(gx0 + 1, CITY_Y + 1, gz1, gx1 - 1, CITY_Y + 3, gz1, B.AIR)
        canvas.fill(gx0 + 1, CITY_Y + 4, gz1, gx1 - 1, CITY_Y + 4, gz1, B.IRON_BLOCK)
    canvas.set(gx0 + 1, CITY_Y + 1, gz1 - 1, B.BARREL)


def _fence_yard(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int,
                facing: str) -> None:
    fence = B.fence("oak")
    for x in range(x0, x1 + 1):
        canvas.set(x, CITY_Y + 1, z0, fence)
        canvas.set(x, CITY_Y + 1, z1, fence)
    for z in range(z0, z1 + 1):
        canvas.set(x0, CITY_Y + 1, z, fence)
        canvas.set(x1, CITY_Y + 1, z, fence)
    # Калитка со стороны улицы.
    gx, gz = front_center(x0, z0, x1, z1, facing)
    canvas.set(gx, CITY_Y + 1, gz, B.AIR)
    canvas.set(gx + 1 if facing in ("north", "south") else gx,
               CITY_Y + 1,
               gz if facing in ("north", "south") else gz + 1, B.AIR)


def _tree(canvas: RegionCanvas, x: int, z: int, rng: random.Random) -> None:
    height = rng.randrange(4, 7)
    log, leaves = rng.choice(((B.OAK_LOG, B.OAK_LEAVES),
                              (B.JUNGLE_LOG, B.JUNGLE_LEAVES),
                              (B.OAK_LOG, B.BIRCH_LEAVES)))
    canvas.fill(x, CITY_Y + 1, z, x, CITY_Y + height, z, log)
    top = CITY_Y + height
    canvas.fill(x - 2, top - 1, z - 2, x + 2, top, z + 2, leaves)
    canvas.fill(x - 1, top + 1, z - 1, x + 1, top + 1, z + 1, leaves)
    canvas.fill(x, CITY_Y + 1, z, x, top, z, log)


def _pool(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int) -> None:
    canvas.fill(x0, CITY_Y - 2, z0, x1, CITY_Y, z1, B.QUARTZ)
    canvas.fill(x0 + 1, CITY_Y - 1, z0 + 1, x1 - 1, CITY_Y, z1 - 1, B.WATER)
    canvas.fill(x0, CITY_Y + 1, z0, x1, CITY_Y + 1, z1, B.AIR)


def build_apartment(canvas: RegionCanvas, lot: Lot) -> None:
    """Доходный дом: четыре этажа, восемь квартир с отдельными входами."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 2)
    wall, accent, glass, _ = B.FACADE_SETS[rng.randrange(len(B.FACADE_SETS))]
    floors = 4
    shell(canvas, x0, z0, x1, z1, CITY_Y, floors, wall, glass, accent, lot.facing, rng,
          interior=B.CONCRETE_LIGHT)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 4, lot.facing, lot.label, accent)

    # Коридор и по две квартиры на этаж: перегородки и двери.
    for level in range(floors):
        base = CITY_Y + level * FLOOR_HEIGHT
        mid = (z0 + z1) // 2
        canvas.fill(x0 + 1, base + 1, mid, x1 - 1, base + 3, mid, wall)
        for slot, x in enumerate((x0 + (x1 - x0) // 3, x0 + 2 * (x1 - x0) // 3)):
            canvas.fill(x, base + 1, mid, x, base + 2, mid, B.AIR)
            place_door(canvas, x, base + 1, mid, "north")
            canvas.sign(x + 1, base + 3, mid, B.SIGN_WALL.format(f="north"),
                        [f"КВ. {level * 2 + slot + 1}"], color="gray", glowing=False)
        # Мебель в квартирах.
        canvas.set(x0 + 3, base + 1, z0 + 3, B.BED_RED)
        canvas.set(x0 + 3, base + 1, z0 + 2, B.BED_RED_HEAD)
        canvas.container(x1 - 3, base + 1, z0 + 3, B.CHEST_N, "minecraft:chest")
        canvas.set(x1 - 4, base + 1, z1 - 3, B.CRAFTING)


def build_parking(canvas: RegionCanvas, lot: Lot) -> None:
    """Открытая многоуровневая парковка с пандусами."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 1)
    levels = 3
    canvas.fill(x0, CITY_Y - 2, z0, x1, CITY_Y - 1, z1, B.CONCRETE_GRAY)
    for level in range(levels):
        base = CITY_Y + level * 5
        canvas.fill(x0, base, z0, x1, base, z1, B.CONCRETE_GRAY)
        # Разметка парковочных мест.
        for x in range(x0 + 2, x1 - 1, 3):
            canvas.fill(x, base, z0 + 1, x, base, z0 + 5, B.ROAD_LINE_WHITE)
            canvas.fill(x, base, z1 - 5, x, base, z1 - 1, B.ROAD_LINE_WHITE)
        # Колонны и ограждение.
        for x in range(x0, x1 + 1, 6):
            for z in range(z0, z1 + 1, 6):
                canvas.fill(x, base + 1, z, x, base + 4, z, B.CONCRETE_LIGHT)
        for x in range(x0, x1 + 1):
            canvas.set(x, base + 1, z0, B.wall("stone_brick"))
            canvas.set(x, base + 1, z1, B.wall("stone_brick"))
        for z in range(z0, z1 + 1):
            canvas.set(x0, base + 1, z, B.wall("stone_brick"))
            canvas.set(x1, base + 1, z, B.wall("stone_brick"))
        lamp_ceiling(canvas, x0, z0, x1, z1, base + 4, 8)
        # Пандус на следующий уровень.
        if level < levels - 1:
            for step in range(5):
                canvas.fill(x1 - 8, base + step, z0 + 2 + step, x1 - 3, base + step,
                            z0 + 2 + step, B.CONCRETE_LIGHT)
    top = CITY_Y + levels * 5
    canvas.fill(x0, top, z0, x1, top, z1, B.CONCRETE_GRAY)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 3, lot.facing, lot.label, B.CONCRETE_BLUE)
    _ = rng


def build_warehouse(canvas: RegionCanvas, lot: Lot) -> None:
    """Склад: один высокий объём, рольворота, поддоны с ящиками."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 2)
    wall = rng.choice((B.CONCRETE_LIGHT, B.CONCRETE_GRAY, B.TERRACOTTA_LIGHT))
    accent = rng.choice((B.CONCRETE_BLUE, B.CONCRETE_RED, B.CONCRETE_ORANGE))
    canvas.fill(x0, CITY_Y - 2, z0, x1, CITY_Y, z1, B.CONCRETE_GRAY)
    canvas.outline(x0, CITY_Y + 1, z0, x1, CITY_Y + 10, z1, wall)
    canvas.fill(x0, CITY_Y + 8, z0, x1, CITY_Y + 8, z1, accent)
    canvas.fill(x0, CITY_Y + 11, z0, x1, CITY_Y + 11, z1, B.CONCRETE_GRAY)
    for x in range(x0 + 2, x1 - 1, 6):
        canvas.fill(x, CITY_Y + 9, z0 + 1, x, CITY_Y + 9, z1 - 1, B.GLASS)
    # Рольворота на фасаде.
    dx, dz = front_center(x0, z0, x1, z1, lot.facing)
    if lot.facing in ("north", "south"):
        canvas.fill(dx - 3, CITY_Y + 1, dz, dx + 3, CITY_Y + 5, dz, B.AIR)
        canvas.fill(dx - 3, CITY_Y + 6, dz, dx + 3, CITY_Y + 6, dz, accent)
    else:
        canvas.fill(dx, CITY_Y + 1, dz - 3, dx, CITY_Y + 5, dz + 3, B.AIR)
        canvas.fill(dx, CITY_Y + 6, dz - 3, dx, CITY_Y + 6, dz + 3, accent)
    lamp_ceiling(canvas, x0, z0, x1, z1, CITY_Y + 10, 7)
    for _ in range(rng.randrange(6, 12)):
        x = rng.randrange(x0 + 2, max(x0 + 3, x1 - 2))
        z = rng.randrange(z0 + 2, max(z0 + 3, z1 - 2))
        height = rng.randrange(1, 4)
        canvas.fill(x, CITY_Y + 1, z, x + 1, CITY_Y + height, z + 1, B.SPRUCE_PLANKS)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 7, lot.facing, lot.label, accent)


def build_gas(canvas: RegionCanvas, lot: Lot) -> None:
    """АЗС: навес, колонки, магазинчик."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 2)
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.CONCRETE_GRAY)
    # Магазин в углу.
    sx1, sz1 = x0 + 11, z0 + 9
    canvas.outline(x0, CITY_Y + 1, z0, sx1, CITY_Y + 4, sz1, B.CONCRETE_WHITE)
    canvas.fill(x0 + 1, CITY_Y, z0 + 1, sx1 - 1, CITY_Y, sz1 - 1, B.QUARTZ_SMOOTH)
    canvas.fill(x0, CITY_Y + 5, z0, sx1, CITY_Y + 5, sz1, B.CONCRETE_RED)
    window_band(canvas, x0, z0, sx1, sz1, CITY_Y + 2, B.GLASS, step=2)
    window_band(canvas, x0, z0, sx1, sz1, CITY_Y + 3, B.GLASS, step=2)
    place_door(canvas, (x0 + sx1) // 2, CITY_Y + 1, sz1, "south")
    counter(canvas, x0, z0, sx1, sz1, CITY_Y + 1, "south", B.QUARTZ_SMOOTH)
    lamp_ceiling(canvas, x0, z0, sx1, sz1, CITY_Y + 4, 4)

    # Навес над колонками.
    cx0, cz0 = sx1 + 3, z0 + 2
    cx1, cz1 = min(cx0 + 14, x1), min(cz0 + 10, z1)
    canvas.fill(cx0, CITY_Y + 6, cz0, cx1, CITY_Y + 6, cz1, B.CONCRETE_WHITE)
    canvas.fill(cx0, CITY_Y + 7, cz0, cx1, CITY_Y + 7, cz1, B.CONCRETE_RED)
    for x in (cx0, cx1):
        for z in (cz0, cz1):
            canvas.fill(x, CITY_Y + 1, z, x, CITY_Y + 5, z, B.IRON_BLOCK)
    lamp_ceiling(canvas, cx0, cz0, cx1, cz1, CITY_Y + 5, 4)
    for offset in (3, 9):
        px = cx0 + offset
        canvas.fill(px, CITY_Y + 1, cz0 + 4, px, CITY_Y + 2, cz0 + 4, B.CONCRETE_LIGHT)
        canvas.set(px, CITY_Y + 3, cz0 + 4, B.slab("smooth_stone", top=False))
        canvas.set(px + 1, CITY_Y + 1, cz0 + 4, B.CONCRETE_RED)
    signboard(canvas, x0, z0, sx1, sz1, CITY_Y + 6, "south", lot.label, B.CONCRETE_RED)
    _ = rng


def build_park(canvas: RegionCanvas, lot: Lot) -> None:
    """Парк: газон, дорожки, деревья, лавки, фонтан или площадка."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 0)
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.GRASS)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # Крестовые дорожки.
    canvas.fill(x0, CITY_Y, cz - 1, x1, CITY_Y, cz + 1, B.SIDEWALK)
    canvas.fill(cx - 1, CITY_Y, z0, cx + 1, CITY_Y, z1, B.SIDEWALK)

    if rng.random() < 0.5:
        # Фонтан.
        canvas.fill(cx - 3, CITY_Y, cz - 3, cx + 3, CITY_Y, cz + 3, B.SIDEWALK_EDGE)
        canvas.fill(cx - 2, CITY_Y, cz - 2, cx + 2, CITY_Y, cz + 2, B.WATER)
        canvas.fill(cx, CITY_Y + 1, cz, cx, CITY_Y + 2, cz, B.QUARTZ)
        canvas.set(cx, CITY_Y + 3, cz, B.WATER)
    else:
        # Баскетбольная площадка.
        canvas.fill(cx - 7, CITY_Y, cz - 5, cx + 7, CITY_Y, cz + 5, B.CONCRETE_ORANGE)
        canvas.outline(cx - 7, CITY_Y, cz - 5, cx + 7, CITY_Y, cz + 5, B.ROAD_LINE_WHITE)
        for side in (-1, 1):
            hx = cx + side * 7
            canvas.fill(hx, CITY_Y + 1, cz, hx, CITY_Y + 4, cz, B.IRON_BARS)
            canvas.set(hx, CITY_Y + 5, cz, B.CONCRETE_WHITE)

    for _ in range(rng.randrange(6, 11)):
        tx = rng.randrange(x0 + 2, max(x0 + 3, x1 - 2))
        tz = rng.randrange(z0 + 2, max(z0 + 3, z1 - 2))
        if abs(tx - cx) < 4 and abs(tz - cz) < 4:
            continue
        _tree(canvas, tx, tz, rng)
    # Лавки и фонари вдоль дорожек.
    for x in range(x0 + 4, x1 - 3, 8):
        canvas.set(x, CITY_Y + 1, cz - 2, B.stairs("spruce", facing="north"))
        canvas.set(x, CITY_Y + 1, cz + 2, B.stairs("spruce", facing="south"))
    for z in range(z0 + 6, z1 - 5, 12):
        canvas.fill(cx - 3, CITY_Y + 1, z, cx - 3, CITY_Y + 3, z, B.CONCRETE_BLACK)
        canvas.set(cx - 3, CITY_Y + 4, z, B.LAMP)
    if lot.label:
        canvas.sign(cx + 2, CITY_Y + 1, z0 + 1, B.SIGN_STANDING.format(r=8),
                    _split_label(lot.label), color="green", glowing=False)


def build_empty(canvas: RegionCanvas, lot: Lot) -> None:
    """Свободный участок под постройки игроков: газон, забор, табличка."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 0)
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.GRASS)
    fence = B.fence(rng.choice(("oak", "spruce", "birch")))
    for x in range(x0, x1 + 1):
        canvas.set(x, CITY_Y + 1, z0, fence)
        canvas.set(x, CITY_Y + 1, z1, fence)
    for z in range(z0, z1 + 1):
        canvas.set(x0, CITY_Y + 1, z, fence)
        canvas.set(x1, CITY_Y + 1, z, fence)
    gx, gz = front_center(x0, z0, x1, z1, lot.facing)
    canvas.set(gx, CITY_Y + 1, gz, B.AIR)
    canvas.set(gx + 1, CITY_Y + 1, gz, B.AIR)
    # Табличка «продаётся» смотрит на улицу.
    rotation = {"north": 8, "south": 0, "west": 4, "east": 12}[lot.facing]
    sx = gx - 2 if lot.facing in ("north", "south") else gx
    sz = gz if lot.facing in ("north", "south") else gz - 2
    canvas.set(sx, CITY_Y + 1, sz, B.fence("oak"))
    canvas.sign(sx, CITY_Y + 2, sz, B.SIGN_STANDING.format(r=rotation),
                ["ПРОДАЁТСЯ", "УЧАСТОК", f"{lot.width}x{lot.depth}", "мэрия"],
                color="red", glowing=False)
    for _ in range(rng.randrange(0, 3)):
        _tree(canvas, rng.randrange(x0 + 3, max(x0 + 4, x1 - 3)),
              rng.randrange(z0 + 3, max(z0 + 4, z1 - 3)), rng)


def build_construction(canvas: RegionCanvas, lot: Lot) -> None:
    """Стройплощадка: котлован, каркас, строительные леса, забор."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 1)
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.COARSE_DIRT)
    canvas.fill(x0 + 4, CITY_Y - 4, z0 + 4, x1 - 4, CITY_Y, z1 - 4, B.AIR)
    canvas.fill(x0 + 4, CITY_Y - 5, z0 + 4, x1 - 4, CITY_Y - 5, z1 - 4, B.CONCRETE_GRAY)
    # Каркас будущего здания.
    for x in range(x0 + 5, x1 - 4, 5):
        for z in range(z0 + 5, z1 - 4, 5):
            canvas.fill(x, CITY_Y - 4, z, x, CITY_Y + rng.randrange(4, 10), z, B.IRON_BLOCK)
    # Леса вдоль одной стороны.
    canvas.fill(x0 + 4, CITY_Y, z0 + 3, x1 - 4, CITY_Y + 6, z0 + 3, B.SCAFFOLD)
    # Забор с предупреждением.
    for x in range(x0, x1 + 1):
        canvas.set(x, CITY_Y + 1, z0, B.CONCRETE_ORANGE if x % 4 else B.CONCRETE_WHITE)
        canvas.set(x, CITY_Y + 1, z1, B.CONCRETE_ORANGE if x % 4 else B.CONCRETE_WHITE)
    for z in range(z0, z1 + 1):
        canvas.set(x0, CITY_Y + 1, z, B.CONCRETE_ORANGE if z % 4 else B.CONCRETE_WHITE)
        canvas.set(x1, CITY_Y + 1, z, B.CONCRETE_ORANGE if z % 4 else B.CONCRETE_WHITE)
    gx, gz = front_center(x0, z0, x1, z1, lot.facing)
    canvas.fill(gx - 1, CITY_Y + 1, gz, gx + 1, CITY_Y + 1, gz, B.AIR)
    canvas.sign(gx + 2, CITY_Y + 2, gz, B.SIGN_WALL.format(f=lot.facing),
                _split_label(lot.label or "СТРОЙКА"), color="orange", glowing=True)


def build_dealership(canvas: RegionCanvas, lot: Lot) -> None:
    """Автосалон: стеклянный шоурум и открытая площадка с местами."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 1)
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.CONCRETE_GRAY)
    # Шоурум занимает половину участка.
    sx1 = x0 + (x1 - x0) // 2
    canvas.fill(x0, CITY_Y, z0, sx1, CITY_Y, z1, B.QUARTZ_SMOOTH)
    canvas.outline(x0, CITY_Y + 1, z0, sx1, CITY_Y + 7, z1, B.GLASS_TINTED)
    canvas.fill(x0, CITY_Y + 8, z0, sx1, CITY_Y + 8, z1, B.CONCRETE_WHITE)
    canvas.fill(x0, CITY_Y + 1, z0, x0, CITY_Y + 7, z1, B.CONCRETE_WHITE)
    lamp_ceiling(canvas, x0, z0, sx1, z1, CITY_Y + 7, 5)
    place_door(canvas, (x0 + sx1) // 2, CITY_Y + 1, z1, "south")
    counter(canvas, x0, z0, sx1, z1, CITY_Y + 1, "south", B.QUARTZ_SMOOTH)
    # Открытая площадка: разметка мест и флажки.
    for x in range(sx1 + 3, x1 - 1, 4):
        canvas.fill(x, CITY_Y, z0 + 2, x, CITY_Y, z1 - 2, B.ROAD_LINE_WHITE)
    for z in (z0 + 1, z1 - 1):
        for x in range(sx1 + 2, x1, 6):
            canvas.fill(x, CITY_Y + 1, z, x, CITY_Y + 3, z, B.CONCRETE_BLACK)
            canvas.set(x, CITY_Y + 4, z, B.LAMP)
    signboard(canvas, x0, z0, sx1, z1, CITY_Y + 9, "south", lot.label, B.CONCRETE_BLUE)
    _ = rng


def build_club(canvas: RegionCanvas, lot: Lot) -> None:
    """Ночной клуб: тёмный фасад, неон, танцпол, бар."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 2)
    wall, accent, glass = B.CONCRETE_BLACK, B.CONCRETE_PURPLE, B.GLASS_BLACK
    shell(canvas, x0, z0, x1, z1, CITY_Y, 2, wall, glass, accent, lot.facing, rng,
          interior=B.CONCRETE_BLACK)
    # Танцпол из цветного бетона.
    colors = ("magenta", "purple", "blue", "cyan", "pink")
    for x in range(x0 + 4, x1 - 3):
        for z in range(z0 + 4, z1 - 3):
            canvas.set(x, CITY_Y + 1, z, B.concrete(colors[(x + z) % len(colors)]))
    # Бар и музыка.
    counter(canvas, x0, z0, x1, z1, CITY_Y + 1, lot.facing, B.DARK_OAK_PLANKS)
    canvas.set(x0 + 2, CITY_Y + 1, z1 - 2, B.JUKEBOX)
    canvas.set(x0 + 3, CITY_Y + 1, z1 - 2, B.NOTE_BLOCK)
    for x in range(x0 + 2, x1 - 1, 4):
        canvas.set(x, CITY_Y + 7, z0 + 2, B.REDSTONE_LAMP_ON)
        canvas.set(x, CITY_Y + 7, z1 - 2, B.REDSTONE_LAMP_ON)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 5, lot.facing, lot.label, accent)


def build_metro(canvas: RegionCanvas, lot: Lot) -> None:
    """Станция метро: павильон на улице и платформа под землёй."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 6)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # Павильон.
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.SIDEWALK)
    canvas.outline(cx - 5, CITY_Y + 1, cz - 5, cx + 5, CITY_Y + 4, cz + 5, B.CONCRETE_BLUE)
    canvas.fill(cx - 5, CITY_Y + 5, cz - 5, cx + 5, CITY_Y + 5, cz + 5, B.QUARTZ)
    window_band(canvas, cx - 5, cz - 5, cx + 5, cz + 5, CITY_Y + 3, B.GLASS, step=2)
    place_door(canvas, cx, CITY_Y + 1, cz + 5, "south")
    canvas.sign(cx + 1, CITY_Y + 4, cz + 5, B.SIGN_WALL.format(f="south"),
                _split_label(lot.label or "МЕТРО"), color="blue", glowing=True)

    # Спуск на платформу.
    from .plan import METRO_Y
    shaft_x, shaft_z = cx - 2, cz - 2
    canvas.fill(shaft_x, METRO_Y, shaft_z, shaft_x + 3, CITY_Y, shaft_z + 3, B.AIR)
    canvas.fill(shaft_x - 1, METRO_Y - 1, shaft_z - 1, shaft_x + 4, METRO_Y - 1,
                shaft_z + 4, B.SIDEWALK_EDGE)
    for y in range(METRO_Y, CITY_Y + 1):
        canvas.set(shaft_x, y, shaft_z, B.LADDER_N.replace("facing=north", "facing=south"))
    # Платформа и путь.
    canvas.fill(cx - 12, METRO_Y, cz - 4, cx + 12, METRO_Y + 5, cz + 4, B.AIR)
    canvas.fill(cx - 12, METRO_Y - 1, cz - 4, cx + 12, METRO_Y - 1, cz + 4, B.SIDEWALK_EDGE)
    canvas.fill(cx - 12, METRO_Y + 6, cz - 4, cx + 12, METRO_Y + 6, cz + 4, B.STONE)
    canvas.fill(cx - 12, METRO_Y, cz, cx + 12, METRO_Y, cz, B.GRAVEL)
    for x in range(cx - 12, cx + 13):
        canvas.set(x, METRO_Y + 1, cz,
                   B.POWERED_RAIL_EW if x % 8 == 0 else B.RAIL_EW)
        if x % 8 == 0:
            canvas.set(x, METRO_Y, cz, B.REDSTONE_BLOCK)
    for x in range(cx - 10, cx + 11, 5):
        canvas.set(x, METRO_Y + 5, cz - 3, B.LAMP)
        canvas.set(x, METRO_Y + 5, cz + 3, B.LAMP)
    _ = rng


KIND_BUILDERS = {
    "tower": build_tower,
    "mall": build_mall,
    "house": build_house,
    "villa": lambda canvas, lot: build_house(canvas, lot, villa=True),
    "apartment": build_apartment,
    "parking": build_parking,
    "warehouse": build_warehouse,
    "gas": build_gas,
    "park": build_park,
    "empty": build_empty,
    "construction": build_construction,
    "dealership": build_dealership,
    "club": build_club,
    "metro": build_metro,
    # Общие здания различаются числом этажей, палитрой и начинкой.
    "office": lambda canvas, lot: build_generic(canvas, lot, floors=4),
    "shop": lambda canvas, lot: build_generic(canvas, lot, floors=1),
    "diner": lambda canvas, lot: build_generic(canvas, lot, floors=1,
                                               interior_kind="tables"),
    "bank": lambda canvas, lot: build_generic(canvas, lot, floors=3, palette_index=5),
    "police": lambda canvas, lot: build_generic(canvas, lot, floors=2, palette_index=2),
    "hospital": lambda canvas, lot: build_generic(canvas, lot, floors=3, palette_index=2),
    "city_hall": lambda canvas, lot: build_generic(canvas, lot, floors=3, palette_index=5),
    "fire_station": lambda canvas, lot: build_generic(canvas, lot, floors=2, palette_index=3),
    "gun_shop": lambda canvas, lot: build_generic(canvas, lot, floors=1, palette_index=6),
    "phone_shop": lambda canvas, lot: build_generic(canvas, lot, floors=1, palette_index=2),
}


def build_lot(canvas: RegionCanvas, lot: Lot) -> None:
    builder = KIND_BUILDERS.get(lot.kind)
    if builder is None:
        build_generic(canvas, lot, floors=2)
        return
    builder(canvas, lot)
