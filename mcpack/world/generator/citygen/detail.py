"""
Детали зданий: то, из-за чего дом перестаёт быть коробкой с дырками.

Раньше фасад собирался из двух лент стекла в голой стене — отсюда ощущение
«ужасного» города. Здесь собраны приёмы, которые дают зданию читаемый низ,
середину и верх: цоколь, оконные проёмы с откосами и подоконниками, угловые
лопатки, межэтажные тяги, карниз с выносом, козырёк над входом, балконы,
витрины первого этажа и обжитая крыша.

Все функции работают с RegionCanvas и молча игнорируют то, что выходит за
границы региона: здание на стыке рисуется по разу в каждом регионе.
"""
from __future__ import annotations

import random

from . import blocks as B
from .canvas import RegionCanvas

SIDE_FACINGS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def _slab_name(material: str) -> str:
    """Имя материала для плиты по блоку стены."""
    name = material.split("[")[0].replace("minecraft:", "")
    if name.endswith("_concrete"):
        # У бетона нет плит, поэтому карнизы и подоконники делаем камнем.
        return "smooth_stone"
    if name in ("bricks",):
        return "brick"
    if name in ("quartz_block",):
        return "quartz"
    if name.endswith("_terracotta"):
        return "smooth_stone"
    if name in ("deepslate_tiles", "polished_deepslate", "cut_copper",
                "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper",
                "mud_bricks", "stone_bricks", "sandstone", "smooth_sandstone",
                "smooth_quartz", "smooth_stone"):
        return name
    return "smooth_stone"


def plinth(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
           material: str, cap: str) -> None:
    """Цоколь: тёмная полоса у земли и плита-отлив над ней."""
    canvas.outline(x0, y, z0, x1, y + 1, z1, material)
    slab = B.slab(_slab_name(cap), top=False)
    for x in range(x0, x1 + 1):
        canvas.set(x, y + 2, z0 - 1, slab)
        canvas.set(x, y + 2, z1 + 1, slab)
    for z in range(z0, z1 + 1):
        canvas.set(x0 - 1, y + 2, z, slab)
        canvas.set(x1 + 1, y + 2, z, slab)


def pilasters(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int,
              y0: int, y1: int, material: str) -> None:
    """Угловые лопатки: вертикали, которые собирают фасад."""
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        canvas.column(x, z, y0, y1, material)


def window_strip(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
                 height: int, glass: str, frame: str, step: int = 4) -> None:
    """
    Лента окон с простенками.

    Окно — проём в два-три блока по высоте с откосом из акцента по бокам и
    подоконником-плитой снизу. Простенки идут с шагом step, поэтому фасад
    получает ритм вместо сплошной стеклянной полосы.
    """
    sill = B.slab(_slab_name(frame), top=True)

    def bay(x: int, z: int) -> None:
        """Одно окно: стекло по высоте, подоконник снизу, перемычка сверху."""
        for level in range(height):
            canvas.set(x, y + level, z, glass)
        canvas.set(x, y - 1, z, sill)
        canvas.set(x, y + height, z, frame)

    for x in range(x0 + 2, x1 - 1):
        if (x - x0) % step in (0,):
            continue
        bay(x, z0)
        bay(x, z1)
    for z in range(z0 + 2, z1 - 1):
        if (z - z0) % step in (0,):
            continue
        bay(x0, z)
        bay(x1, z)


def band(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
         material: str) -> None:
    """Межэтажная тяга: тонкая горизонталь по всему периметру."""
    canvas.outline(x0, y, z0, x1, y, z1, material)


def cornice(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
            material: str) -> None:
    """Карниз с выносом: плиты на блок за габарит, здание получает «шляпу»."""
    slab = B.slab(_slab_name(material), top=False)
    for x in range(x0 - 1, x1 + 2):
        canvas.set(x, y, z0 - 1, slab)
        canvas.set(x, y, z1 + 1, slab)
    for z in range(z0 - 1, z1 + 2):
        canvas.set(x0 - 1, y, z, slab)
        canvas.set(x1 + 1, y, z, slab)


def parapet(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
            material: str, cap: str) -> None:
    """Парапет с каменной кромкой — крыша перестаёт обрываться в никуда."""
    canvas.outline(x0, y, z0, x1, y, z1, material)
    slab = B.slab(_slab_name(cap), top=False)
    canvas.outline(x0, y + 1, z0, x1, y + 1, z1, slab)


def canopy(canvas: RegionCanvas, x: int, y: int, z: int, facing: str,
           material: str, width: int = 3, depth: int = 2) -> None:
    """Козырёк над входом: плиты с выносом и подвес на цепях."""
    dx, dz = SIDE_FACINGS[facing]
    slab = B.slab(_slab_name(material), top=True)
    for step in range(1, depth + 1):
        for side in range(-width // 2, width // 2 + 1):
            px = x + dx * step + (side if dx == 0 else 0)
            pz = z + dz * step + (side if dz == 0 else 0)
            canvas.set(px, y, pz, slab)
    for side in (-width // 2, width // 2):
        px = x + dx * depth + (side if dx == 0 else 0)
        pz = z + dz * depth + (side if dz == 0 else 0)
        canvas.set(px, y + 1, pz, B.CHAIN)


def entrance_steps(canvas: RegionCanvas, x: int, y: int, z: int, facing: str,
                   material: str, width: int = 3) -> None:
    """Ступени и площадка перед дверью."""
    dx, dz = SIDE_FACINGS[facing]
    stair = B.stairs(_slab_name(material), facing=facing)
    for side in range(-width // 2, width // 2 + 1):
        px = x + dx + (side if dx == 0 else 0)
        pz = z + dz + (side if dz == 0 else 0)
        canvas.set(px, y, pz, stair)


def storefront(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
               facing: str, glass: str, accent: str, rng: random.Random) -> None:
    """
    Витрина первого этажа: стекло от пола до потолка и полосатый маркиз.

    Делаем только со стороны улицы: с торцов магазин остаётся глухим, как
    в жизни, и не просвечивает насквозь.
    """
    if facing in ("north", "south"):
        z = z0 if facing == "north" else z1
        span = range(x0 + 2, x1 - 1)
        for x in span:
            for level in range(3):
                canvas.set(x, y + level, z, glass)
            canvas.set(x, y + 3, z, accent)
        # Маркиз: полоса плит с выносом на блок от витрины.
        awning = B.slab(rng.choice(("smooth_stone", "brick", "sandstone")), top=True)
        dz = -1 if facing == "north" else 1
        for x in span:
            canvas.set(x, y + 4, z + dz, awning)
    else:
        x = x0 if facing == "west" else x1
        span = range(z0 + 2, z1 - 1)
        for z in span:
            for level in range(3):
                canvas.set(x, y + level, z, glass)
            canvas.set(x, y + 3, z, accent)
        awning = B.slab(rng.choice(("smooth_stone", "brick", "sandstone")), top=True)
        dx = -1 if facing == "west" else 1
        for z in span:
            canvas.set(x + dx, y + 4, z, awning)


def balconies(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
              facing: str, material: str) -> None:
    """Балконы с ограждением по уличному фасаду."""
    dx, dz = SIDE_FACINGS[facing]
    slab = B.slab(_slab_name(material), top=True)
    rail = B.fence("oak") if "planks" in material else B.wall("stone_brick")
    if dz != 0:
        z = (z0 if dz < 0 else z1) + dz
        for x in range(x0 + 2, x1 - 1, 5):
            for side in range(3):
                canvas.set(x + side, y, z, slab)
                canvas.set(x + side, y + 1, z, rail)
    else:
        x = (x0 if dx < 0 else x1) + dx
        for z in range(z0 + 2, z1 - 1, 5):
            for side in range(3):
                canvas.set(x, y, z + side, slab)
                canvas.set(x, y + 1, z + side, rail)


def rooftop(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
            rng: random.Random) -> None:
    """
    Оборудование крыши: вентиляция, бак, выход на кровлю, антенна.

    Именно эти мелочи читаются сверху и с соседних крыш, поэтому город
    перестаёт выглядеть набором коробок.
    """
    width = x1 - x0
    depth = z1 - z0
    if width < 8 or depth < 8:
        return

    # Вентиляционные короба.
    for _ in range(rng.randint(2, 4)):
        bx = rng.randint(x0 + 2, x1 - 4)
        bz = rng.randint(z0 + 2, z1 - 4)
        canvas.fill(bx, y, bz, bx + 1, y + 1, bz + 1, B.CONCRETE_LIGHT)
        canvas.fill(bx, y + 2, bz, bx + 1, y + 2, bz + 1, B.slab("smooth_stone", top=False))

    # Бак с водой на подставке.
    if rng.random() < 0.6:
        tx = rng.randint(x0 + 3, x1 - 5)
        tz = rng.randint(z0 + 3, z1 - 5)
        canvas.fill(tx, y, tz, tx + 2, y, tz + 2, B.IRON_BARS)
        canvas.fill(tx, y + 1, tz, tx + 2, y + 3, tz + 2, B.SPRUCE_PLANKS)
        canvas.fill(tx, y + 4, tz, tx + 2, y + 4, tz + 2, B.slab("spruce", top=False))

    # Выход на кровлю.
    hx = x0 + 2
    hz = z0 + 2
    canvas.fill(hx, y, hz, hx + 2, y + 2, hz + 2, B.CONCRETE_GRAY)
    canvas.fill(hx, y + 3, hz, hx + 2, y + 3, hz + 2, B.slab("smooth_stone", top=False))
    canvas.set(hx + 1, y + 1, hz + 3, B.LANTERN)

    # Антенна с красным огнём — ориентир ночью.
    ax = x1 - 3
    az = z1 - 3
    canvas.column(ax, az, y, y + rng.randint(3, 6), B.IRON_BARS)
    canvas.set(ax, y + rng.randint(4, 7), az, B.REDSTONE_LAMP_ON)


def ac_units(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
             facing: str, rng: random.Random) -> None:
    """Кондиционеры на фасаде: мелочь, которая оживляет стену."""
    dx, dz = SIDE_FACINGS[facing]
    if dz != 0:
        z = (z0 if dz < 0 else z1) + dz
        for x in range(x0 + 3, x1 - 2, 6):
            if rng.random() < 0.5:
                canvas.set(x, y, z, B.CONCRETE_LIGHT)
    else:
        x = (x0 if dx < 0 else x1) + dx
        for z in range(z0 + 3, z1 - 2, 6):
            if rng.random() < 0.5:
                canvas.set(x, y, z, B.CONCRETE_LIGHT)


def indoor_light(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
                 step: int = 6) -> None:
    """
    Свет в помещении без источника на виду.

    minecraft:light светит как светокамень, но невидим: потолки перестают
    быть в жёлтых пятнах, а внутри остаётся светло.
    """
    for x in range(x0 + 2, x1 - 1, step):
        for z in range(z0 + 2, z1 - 1, step):
            canvas.set(x, y, z, B.LIGHT)


def planters(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
             facing: str, rng: random.Random) -> None:
    """Кадки с кустами вдоль фасада — граница между тротуаром и зданием."""
    dx, dz = SIDE_FACINGS[facing]
    if dz != 0:
        z = (z0 if dz < 0 else z1) + dz
        for x in range(x0 + 1, x1, 4):
            if rng.random() < 0.55:
                canvas.set(x, y, z, B.slab("stone_brick", top=False))
                canvas.set(x, y + 1, z, B.OAK_LEAVES)
    else:
        x = (x0 if dx < 0 else x1) + dx
        for z in range(z0 + 1, z1, 4):
            if rng.random() < 0.55:
                canvas.set(x, y, z, B.slab("stone_brick", top=False))
                canvas.set(x, y + 1, z, B.OAK_LEAVES)
