"""
Генераторы застройки: от частного дома до небоскрёба.

Жильё собирается в homes.py, общественные и коммерческие здания —
в commercial.py; здесь раздача по типам участка и то, что не укладывается
в общий каркас: торговый центр, парковка, склад, АЗС, автосалон, метро,
парки, скверы, стройка, свободные участки и пункты выдачи заказов.

Каждая функция получает уже обрезанное по региону полотно, поэтому может
писать в мировых координатах не думая о границах файлов региона.
"""
from __future__ import annotations

import random

from . import blocks as B
from . import commercial as C
from . import homes as H
from .canvas import RegionCanvas
from .frame import Frame
from .plan import CITY_Y, Lot

FLOOR_HEIGHT = 4
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}
OUTWARD = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


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


def signboard(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
              facing: str, label: str, color: str = "white") -> None:
    """
    Табличка с названием на фасадной стене: (x0..x1, z0..z1) — габарит стен,
    y — высота, на которой стена в этой точке цельная. Табличка встаёт
    снаружи перед стеной, а не вместо её блока.
    """
    if not label:
        return
    wx, wz = front_center(x0, z0, x1, z1, facing)
    dx, dz = OUTWARD[facing]
    canvas.sign(wx + dx, y, wz + dz, B.SIGN_WALL.format(f=facing), C.split_label(label),
                color=color, glowing=True)


def lamp_ceiling(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
                 step: int = 6) -> None:
    """Свет под потолком невидимым блоком: светло, но без жёлтых пятен."""
    for x in range(x0 + 2, x1 - 1, step):
        for z in range(z0 + 2, z1 - 1, step):
            canvas.set(x, y, z, B.LIGHT)


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
    canvas.outline(x0, y + 1, z0, x1, y + 1, z1, B.slab("smooth_stone", top=False))


def counter(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, y: int,
            facing: str, material: str) -> None:
    """Прилавок вдоль дальней от входа стены — за ним встанет NPC."""
    if facing in ("north", "south"):
        z = z1 - 3 if facing == "north" else z0 + 3
        canvas.fill(x0 + 3, y, z, x1 - 3, y, z, material)
    else:
        x = x1 - 3 if facing == "west" else x0 + 3
        canvas.fill(x, y, z0 + 3, x, y, z1 - 3, material)


def street_lamp(frame: Frame, u: int, v: int, post: str = "minecraft:dark_oak_fence") -> None:
    """Садовый фонарь: столбик из ограды и фонарь сверху — стоит, а не висит."""
    state = post + "[east=false,north=false,south=false,waterlogged=false,west=false]"
    frame.set(u, CITY_Y + 1, v, state)
    frame.set(u, CITY_Y + 2, v, state)
    frame.set(u, CITY_Y + 3, v, B.LANTERN)


def bench(frame: Frame, u: int, v: int, back: str) -> None:
    """Лавочка из двух ступеней спинкой в сторону back."""
    du, dv = (1, 0) if back in ("front", "back") else (0, 1)
    for i in range(2):
        frame.set(u + du * i, CITY_Y + 1, v + dv * i, frame.stairs("spruce", back))


# ---------------------------------------------------------------------------
#  Общественные и коммерческие здания на общем каркасе
# ---------------------------------------------------------------------------

SUBTITLES = {
    "bank": ["вклады", "переводы", "банкомат"],
    "city_hall": ["приём граждан", "документы", "недвижимость"],
    "police": ["дежурная часть", "круглосуточно"],
    "hospital": ["приёмный покой", "24/7"],
    "fire_station": ["служба 112"],
    "gun_shop": ["оружие", "патроны", "лицензия"],
    "phone_shop": ["телефоны", "SIM-карты", "гаджеты"],
    "diner": ["кухня", "с 8:00 до 23:00"],
    "club": ["с 22:00", "до утра"],
    "office": ["деловой", "центр"],
}


def build_civic(canvas: RegionCanvas, lot: Lot, floors: int, facade_index: int | None = None,
                inside: str = "counter") -> C.Layout:
    lay = C.build(canvas, lot, floors, facade_index, inside=inside,
                  subtitle=SUBTITLES.get(lot.kind))
    frame = C.frame_of(canvas, lay)
    rng = _rng(lot, 3)
    y = CITY_Y + 1
    if lot.kind == "hospital":
        # Палата: койки вдоль левой стены за прилавком.
        for v in range(lay.counter_v + 3, lay.v1 - 1, 3):
            frame.set(lay.u0 + 4, y, v, f"minecraft:white_bed[facing={frame.dir('left')},"
                                       f"occupied=false,part=foot]")
            frame.set(lay.u0 + 3, y, v, f"minecraft:white_bed[facing={frame.dir('left')},"
                                       f"occupied=false,part=head]")
    elif lot.kind == "bank":
        # Хранилище у задней стены.
        frame.fill(lay.u1 - 5, y, lay.v1 - 3, lay.u1 - 2, y + 2, lay.v1 - 1, B.IRON_BLOCK)
        frame.set(lay.u1 - 4, y, lay.v1 - 3, "minecraft:gold_block")
    elif lot.kind == "police":
        # Камера из решёток в дальнем углу.
        frame.fill(lay.u1 - 5, y, lay.v1 - 4, lay.u1 - 1, y + 2, lay.v1 - 4, B.IRON_BARS)
        frame.fill(lay.u1 - 5, y, lay.v1 - 4, lay.u1 - 5, y + 2, lay.v1 - 1, B.IRON_BARS)
    elif lot.kind == "gun_shop":
        # Мишени в тире за прилавком.
        for u in range(lay.u0 + 3, lay.u1 - 2, 3):
            frame.set(u, y + 1, lay.v1 - 1, B.TARGET)
    elif lot.kind == "fire_station":
        # Гаражные ворота для машин справа от входа.
        g0 = lay.door_u + 4
        g1 = min(g0 + 4, lay.u1 - 2)
        if g1 - g0 >= 3:
            frame.fill(g0, CITY_Y + 1, lay.v0, g1, CITY_Y + 3, lay.v0, B.AIR)
            frame.fill(g0, CITY_Y + 4, lay.v0, g1, CITY_Y + 4, lay.v0, B.CONCRETE_RED)
    elif lot.kind == "club":
        colors = ("magenta", "purple", "blue", "cyan", "pink")
        for u in range(lay.u0 + 3, lay.u1 - 2):
            for v in range(lay.v0 + 2, lay.counter_v - 1):
                frame.set(u, CITY_Y, v, B.concrete(colors[(u + v) % len(colors)]))
        frame.set(lay.u0 + 2, y, lay.v1 - 1, B.JUKEBOX)
    _ = rng
    return lay


def build_tower(canvas: RegionCanvas, lot: Lot) -> None:
    """Небоскрёб: стилобат с вестибюлем и узкая стеклянная башня над ним."""
    rng = _rng(lot)
    glass = rng.choice((B.GLASS_BLUE, B.GLASS_CYAN, B.GLASS_GRAY, B.GLASS_TINTED))
    wall = rng.choice((B.CONCRETE_LIGHT, B.QUARTZ, B.CONCRETE_WHITE))
    facade = (wall, B.CONCRETE_GRAY, glass, "smooth_stone")
    lay = C.build(canvas, lot, facade=facade, exact_floors=rng.randint(8, 11),
                  inside="none", subtitle=["бизнес-центр", "класса А"])
    frame = C.frame_of(canvas, lay)
    # Верхняя часть: на 4 блока уже с каждой стороны, сплошное остекление.
    u0, v0, u1, v1 = lay.u0 + 4, lay.v0 + 4, lay.u1 - 4, lay.v1 - 4
    if u1 - u0 < 8 or v1 - v0 < 8:
        return
    base = lay.top
    floors = rng.randint(5, 8)
    for k in range(floors):
        fy = base + FLOOR_HEIGHT * k
        frame.fill(u0, fy, v0, u1, fy, v1, B.CONCRETE_LIGHT)
        frame.outline(u0, fy, v0, u1, fy, v1, B.CONCRETE_GRAY)
        frame.outline(u0, fy + 1, v0, u1, fy + 3, v1, glass)
        for u, v in ((u0, v0), (u1, v0), (u0, v1), (u1, v1)):
            frame.fill(u, fy + 1, v, u, fy + 3, v, wall)
        frame.light_grid(u0, v0, u1, v1, fy + 3, 4)
    crown = base + FLOOR_HEIGHT * floors
    frame.fill(u0, crown, v0, u1, crown, v1, B.CONCRETE_GRAY)
    frame.outline(u0, crown + 1, v0, u1, crown + 1, v1, wall)
    frame.outline(u0, crown + 2, v0, u1, crown + 2, v1, B.slab("smooth_stone"))
    cu, cv = (u0 + u1) // 2, (v0 + v1) // 2
    frame.fill(cu, crown + 1, cv, cu, crown + 8, cv, B.IRON_BARS)
    frame.set(cu, crown + 9, cv, B.REDSTONE_LAMP_ON)
    frame.set(cu, crown + 10, cv, B.REDSTONE_BLOCK)


def build_office(canvas: RegionCanvas, lot: Lot) -> None:
    """Офисное здание: вестибюль с ресепшеном и этажи со столами."""
    lay = C.build(canvas, lot, floors=6, inside="counter",
                  subtitle=SUBTITLES["office"])
    frame = C.frame_of(canvas, lay)
    # Вертолётная площадка или солнечные панели на широкой кровле.
    rng = _rng(lot, 5)
    if lay.w >= 16 and lay.d >= 16:
        cu, cv = (lay.u0 + lay.u1) // 2, (lay.v0 + lay.v1) // 2
        y = lay.top
        if rng.random() < 0.5:
            frame.fill(cu - 4, y, cv - 4, cu + 4, y, cv + 4, B.CONCRETE_BLACK)
            frame.fill(cu - 2, y, cv - 2, cu - 2, y, cv + 2, B.CONCRETE_WHITE)
            frame.fill(cu + 2, y, cv - 2, cu + 2, y, cv + 2, B.CONCRETE_WHITE)
            frame.fill(cu - 2, y, cv, cu + 2, y, cv, B.CONCRETE_WHITE)
        else:
            for u in range(cu - 4, cu + 5, 2):
                frame.fill(u, y + 1, cv - 4, u, y + 1, cv + 4,
                           "minecraft:daylight_detector[inverted=false,power=0]")


def build_pickup(canvas: RegionCanvas, lot: Lot) -> None:
    """
    Пункт выдачи заказов маркетплейса.

    Белый павильон с фиолетовой отделкой, стойка выдачи из блоков
    citylife:pickup_point — любой из них отдаёт игроку готовые заказы —
    и стеллажи с коробками за стойкой.
    """
    facade = (B.CONCRETE_WHITE, B.CONCRETE_PURPLE, B.GLASS, "smooth_quartz")
    lay = C.build(canvas, lot, floors=1, facade=facade, exact_floors=1, inside="none",
                  label="ПУНКТ ВЫДАЧИ", subtitle=["заказы", "маркетплейса", "LS MARKET"])
    frame = C.frame_of(canvas, lay)
    v = lay.counter_v
    y = CITY_Y + 1
    a, b = lay.u0 + 2, lay.u1 - 2
    for u in range(a, b + 1):
        if u in (lay.door_u, lay.door_u + 1):
            frame.set(u, y, v, f"citylife:pickup_point[facing={frame.dir('front')}]")
        else:
            frame.set(u, y, v, B.QUARTZ_SMOOTH)
    frame.set(b, y, v, B.AIR)
    # Стеллажи с посылками.
    for u in range(a, b):
        if v + 2 < lay.v1:
            frame.set(u, y, v + 2, B.BARREL)
            frame.set(u, y + 1, v + 2, "minecraft:brown_wool" if u % 2 else B.BARREL)
    frame.wall_sign(lay.door_u - 1, y, v, "front",
                    ["ВЫДАЧА", "ЗАКАЗОВ", "нажмите на", "терминал"], color="purple")


def build_mall(canvas: RegionCanvas, lot: Lot) -> None:
    """Двухэтажный торговый центр с 24 арендными точками, атриумом и пунктом выдачи."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 1)
    wall, accent, glass = B.CONCRETE_WHITE, B.CONCRETE_CYAN, B.GLASS_BLUE

    canvas.fill(x0, CITY_Y - 2, z0, x1, CITY_Y - 1, z1, B.CONCRETE_GRAY)
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.QUARTZ_SMOOTH)
    canvas.fill(x0, CITY_Y + 1, z0, x1, CITY_Y + 12, z1, B.AIR)
    canvas.outline(x0, CITY_Y + 1, z0, x1, CITY_Y + 10, z1, wall)

    for level, base in enumerate((CITY_Y, CITY_Y + 5)):
        window_band(canvas, x0, z0, x1, z1, base + 2, glass, step=2)
        window_band(canvas, x0, z0, x1, z1, base + 3, glass, step=2)
        if level == 1:
            # Второй уровень — галерея по периметру, в центре атриум.
            canvas.fill(x0 + 1, base, z0 + 1, x1 - 1, base, z1 - 1, B.QUARTZ_SMOOTH)
            canvas.fill(x0 + 9, base, z0 + 9, x1 - 9, base, z1 - 9, B.AIR)
            for x in range(x0 + 8, x1 - 7):
                canvas.set(x, base + 1, z0 + 8, B.IRON_BARS)
                canvas.set(x, base + 1, z1 - 8, B.IRON_BARS)
            for z in range(z0 + 8, z1 - 7):
                canvas.set(x0 + 8, base + 1, z, B.IRON_BARS)
                canvas.set(x1 - 8, base + 1, z, B.IRON_BARS)
        lamp_ceiling(canvas, x0, z0, x1, z1, base + 4, 5)

    unit = 0
    for level_base in (CITY_Y, CITY_Y + 5):
        for side in ("north", "south", "west", "east"):
            for slot in range(3):
                unit += 1
                _mall_unit(canvas, x0, z0, x1, z1, level_base, side, slot, unit, rng)

    # Эскалатор из атриума на второй уровень: пять ступеней и площадка.
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for step in range(5):
        canvas.fill(cx - 1, CITY_Y + 1 + step, cz + step, cx + 1, CITY_Y + 1 + step, cz + step,
                    B.stairs("quartz", facing="south"))
        canvas.fill(cx - 1, CITY_Y + 2 + step, cz + step, cx + 1, CITY_Y + 4 + step, cz + step,
                    B.AIR)
    canvas.fill(cx - 1, CITY_Y + 5, cz + 5, cx + 1, CITY_Y + 5, z1 - 9, B.QUARTZ_SMOOTH)
    canvas.fill(cx - 1, CITY_Y + 6, z1 - 8, cx + 1, CITY_Y + 6, z1 - 8, B.AIR)

    # Пункт выдачи маркетплейса в центре первого этажа.
    for dx in (-3, 3):
        canvas.set(cx + dx, CITY_Y + 1, cz - 3, f"citylife:pickup_point[facing={lot.facing}]")
    canvas.sign(cx, CITY_Y + 2, cz - 3, B.SIGN_STANDING.format(r=0),
                ["ПУНКТ ВЫДАЧИ", "LS MARKET"], color="purple", glowing=True)
    canvas.set(cx, CITY_Y + 1, cz - 3, B.QUARTZ_SMOOTH)

    roof_y = CITY_Y + 11
    canvas.fill(x0, roof_y, z0, x1, roof_y, z1, B.CONCRETE_GRAY)
    canvas.fill(x0 + 9, roof_y, z0 + 9, x1 - 9, roof_y, z1 - 9, B.GLASS)
    parapet(canvas, x0, z0, x1, z1, roof_y + 1, wall)

    dx, dz = front_center(x0, z0, x1, z1, lot.facing)
    along = lot.facing in ("north", "south")
    if along:
        canvas.fill(dx - 2, CITY_Y + 1, dz, dx + 2, CITY_Y + 3, dz, B.GLASS)
    else:
        canvas.fill(dx, CITY_Y + 1, dz - 2, dx, CITY_Y + 3, dz + 2, B.GLASS)
    place_door(canvas, dx, CITY_Y + 1, dz, lot.facing)
    if along:
        place_door(canvas, dx + 1, CITY_Y + 1, dz, lot.facing)
    else:
        place_door(canvas, dx, CITY_Y + 1, dz + 1, lot.facing)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 6, lot.facing, lot.label)


def _mall_unit(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int, base: int,
               side: str, slot: int, unit: int, rng: random.Random) -> None:
    """Одна арендная точка: прилавок у стены и номер на табличке над ним."""
    span = 5
    if side in ("north", "south"):
        width = x1 - x0
        start = x0 + 4 + slot * (width - 8) // 3
        z = z0 + 2 if side == "north" else z1 - 2
        wall_z = z0 + 1 if side == "north" else z1 - 1
        canvas.fill(start, base + 1, wall_z, start + span - 1, base + 3, wall_z, B.QUARTZ)
        canvas.fill(start, base + 1, z, start + span - 1, base + 1, z, B.QUARTZ_SMOOTH)
        canvas.sign(start + 2, base + 3, z,
                    B.SIGN_WALL.format(f="south" if side == "north" else "north"),
                    [f"ТОЧКА #{unit}", "СВОБОДНА"], color="blue", glowing=True)
    else:
        depth = z1 - z0
        start = z0 + 4 + slot * (depth - 8) // 3
        x = x0 + 2 if side == "west" else x1 - 2
        wall_x = x0 + 1 if side == "west" else x1 - 1
        canvas.fill(wall_x, base + 1, start, wall_x, base + 3, start + span - 1, B.QUARTZ)
        canvas.fill(x, base + 1, start, x, base + 1, start + span - 1, B.QUARTZ_SMOOTH)
        canvas.sign(x, base + 3, start + 2,
                    B.SIGN_WALL.format(f="east" if side == "west" else "west"),
                    [f"ТОЧКА #{unit}", "СВОБОДНА"], color="blue", glowing=True)
    _ = rng


def build_parking(canvas: RegionCanvas, lot: Lot) -> None:
    """Открытая многоуровневая парковка с пандусами."""
    x0, z0, x1, z1 = pad(lot, 1)
    levels = 3
    canvas.fill(x0, CITY_Y - 2, z0, x1, CITY_Y - 1, z1, B.CONCRETE_GRAY)
    canvas.fill(x0, CITY_Y + 1, z0, x1, CITY_Y + levels * 5 + 1, z1, B.AIR)
    for level in range(levels):
        base = CITY_Y + level * 5
        canvas.fill(x0, base, z0, x1, base, z1, B.CONCRETE_GRAY)
        for x in range(x0 + 2, x1 - 1, 3):
            canvas.fill(x, base, z0 + 1, x, base, z0 + 5, B.ROAD_LINE_WHITE)
            canvas.fill(x, base, z1 - 5, x, base, z1 - 1, B.ROAD_LINE_WHITE)
        for x in range(x0, x1 + 1, 6):
            for z in range(z0, z1 + 1, 6):
                canvas.fill(x, base + 1, z, x, base + 4, z, B.CONCRETE_LIGHT)
        if level > 0:
            for x in range(x0, x1 + 1):
                canvas.set(x, base + 1, z0, B.wall("stone_brick"))
                canvas.set(x, base + 1, z1, B.wall("stone_brick"))
            for z in range(z0, z1 + 1):
                canvas.set(x0, base + 1, z, B.wall("stone_brick"))
                canvas.set(x1, base + 1, z, B.wall("stone_brick"))
        lamp_ceiling(canvas, x0, z0, x1, z1, base + 4, 6)
    top = CITY_Y + levels * 5
    canvas.fill(x0, top, z0, x1, top, z1, B.CONCRETE_GRAY)
    # Пандусы рисуем после всех перекрытий: иначе следующий этаж
    # закрывал вырез над подъёмом.
    for level in range(levels - 1):
        base = CITY_Y + level * 5
        canvas.fill(x1 - 8, base + 5, z0 + 7, x1 - 3, base + 5, z0 + 11, B.AIR)
        for step in range(4):
            canvas.fill(x1 - 8, base + 1 + step, z0 + 7 + step, x1 - 3, base + 1 + step,
                        z0 + 7 + step, B.CONCRETE_LIGHT)
            canvas.fill(x1 - 8, base + 2 + step, z0 + 7 + step, x1 - 3, base + 4 + step,
                        z0 + 7 + step, B.AIR)
    # Въезд с табличкой на колонне у улицы.
    fx, fz = front_center(x0, z0, x1, z1, lot.facing)
    canvas.fill(fx, CITY_Y + 1, fz, fx, CITY_Y + 4, fz, B.CONCRETE_BLUE)
    dx, dz = OUTWARD[lot.facing]
    canvas.sign(fx + dx, CITY_Y + 3, fz + dz, B.SIGN_WALL.format(f=lot.facing),
                C.split_label(lot.label or "ПАРКОВКА"), color="white", glowing=True)


def build_warehouse(canvas: RegionCanvas, lot: Lot) -> None:
    """Склад: высокий объём, рольворота, окна под крышей, поддоны с ящиками."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 2)
    wall = rng.choice((B.CONCRETE_LIGHT, B.CONCRETE_GRAY, B.TERRACOTTA_LIGHT))
    accent = rng.choice((B.CONCRETE_BLUE, B.CONCRETE_RED, B.CONCRETE_ORANGE))
    lx0, lz0, lx1, lz1 = pad(lot, 0)
    canvas.fill(lx0, CITY_Y, lz0, lx1, CITY_Y, lz1, B.ASPHALT_WORN)
    canvas.fill(lx0, CITY_Y + 1, lz0, lx1, CITY_Y + 12, lz1, B.AIR)
    canvas.fill(x0, CITY_Y - 2, z0, x1, CITY_Y, z1, B.CONCRETE_GRAY)
    canvas.outline(x0, CITY_Y + 1, z0, x1, CITY_Y + 10, z1, wall)
    canvas.outline(x0, CITY_Y + 8, z0, x1, CITY_Y + 8, z1, accent)
    canvas.fill(x0, CITY_Y + 11, z0, x1, CITY_Y + 11, z1, B.CONCRETE_GRAY)
    window_band(canvas, x0, z0, x1, z1, CITY_Y + 9, B.GLASS, step=3)
    dx, dz = front_center(x0, z0, x1, z1, lot.facing)
    if lot.facing in ("north", "south"):
        canvas.fill(dx - 3, CITY_Y + 1, dz, dx + 3, CITY_Y + 5, dz, B.AIR)
        canvas.fill(dx - 3, CITY_Y + 6, dz, dx + 3, CITY_Y + 6, dz, accent)
    else:
        canvas.fill(dx, CITY_Y + 1, dz - 3, dx, CITY_Y + 5, dz + 3, B.AIR)
        canvas.fill(dx, CITY_Y + 6, dz - 3, dx, CITY_Y + 6, dz + 3, accent)
    lamp_ceiling(canvas, x0, z0, x1, z1, CITY_Y + 10, 6)
    lamp_ceiling(canvas, x0, z0, x1, z1, CITY_Y + 3, 7)
    for _ in range(rng.randrange(6, 12)):
        x = rng.randrange(x0 + 2, max(x0 + 3, x1 - 2))
        z = rng.randrange(z0 + 2, max(z0 + 3, z1 - 2))
        if abs(x - dx) <= 4 and abs(z - dz) <= 4:
            continue
        height = rng.randrange(1, 4)
        canvas.fill(x, CITY_Y + 1, z, x + 1, CITY_Y + height, z + 1, B.SPRUCE_PLANKS)
    signboard(canvas, x0, z0, x1, z1, CITY_Y + 7, lot.facing, lot.label)


def build_gas(canvas: RegionCanvas, lot: Lot) -> None:
    """АЗС: навес, колонки, магазинчик."""
    x0, z0, x1, z1 = pad(lot, 2)
    lx0, lz0, lx1, lz1 = pad(lot, 0)
    canvas.fill(lx0, CITY_Y, lz0, lx1, CITY_Y, lz1, B.CONCRETE_GRAY)
    canvas.fill(lx0, CITY_Y + 1, lz0, lx1, CITY_Y + 10, lz1, B.AIR)
    sx1, sz1 = x0 + 11, z0 + 9
    canvas.outline(x0, CITY_Y + 1, z0, sx1, CITY_Y + 4, sz1, B.CONCRETE_WHITE)
    canvas.fill(x0 + 1, CITY_Y, z0 + 1, sx1 - 1, CITY_Y, sz1 - 1, B.QUARTZ_SMOOTH)
    canvas.fill(x0, CITY_Y + 5, z0, sx1, CITY_Y + 5, sz1, B.CONCRETE_RED)
    window_band(canvas, x0, z0, sx1, sz1, CITY_Y + 2, B.GLASS, step=2)
    window_band(canvas, x0, z0, sx1, sz1, CITY_Y + 3, B.GLASS, step=2)
    door_x = (x0 + sx1) // 2
    place_door(canvas, door_x, CITY_Y + 1, sz1, "south")
    canvas.set(door_x, CITY_Y + 3, sz1, B.CONCRETE_WHITE)
    counter(canvas, x0, z0, sx1, sz1, CITY_Y + 1, "south", B.QUARTZ_SMOOTH)
    lamp_ceiling(canvas, x0, z0, sx1, sz1, CITY_Y + 4, 3)

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
    canvas.sign(door_x + 2, CITY_Y + 5, sz1 + 1, B.SIGN_WALL.format(f="south"),
                C.split_label(lot.label), color="white", glowing=True)


def build_dealership(canvas: RegionCanvas, lot: Lot) -> None:
    """Автосалон: стеклянный шоурум на половину участка и открытая площадка."""
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.CONCRETE_GRAY)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 10, D - 1, B.AIR)
    u0, v0, u1, v1 = 1, 2, W // 2, D - 3
    frame.fill(u0, CITY_Y, v0, u1, CITY_Y, v1, B.QUARTZ_SMOOTH)
    frame.outline(u0, CITY_Y + 1, v0, u1, CITY_Y + 6, v1, B.GLASS)
    for u, v in ((u0, v0), (u1, v0), (u0, v1), (u1, v1)):
        frame.fill(u, CITY_Y + 1, v, u, CITY_Y + 6, v, B.CONCRETE_WHITE)
    frame.fill(u0, CITY_Y + 7, v0, u1, CITY_Y + 7, v1, B.CONCRETE_WHITE)
    frame.outline(u0, CITY_Y + 8, v0, u1, CITY_Y + 8, v1, B.CONCRETE_BLUE)
    door = (u0 + u1) // 2
    frame.door(door, CITY_Y + 1, v0, "front", "birch", hinge="left")
    frame.door(door + 1, CITY_Y + 1, v0, "front", "birch", hinge="right")
    frame.fill(door, CITY_Y + 3, v0, door + 1, CITY_Y + 6, v0, B.CONCRETE_WHITE)
    frame.fill(u0 + 3, CITY_Y + 1, v0 + 6, u1 - 3, CITY_Y + 1, v0 + 6, B.QUARTZ_SMOOTH)
    frame.set(u1 - 3, CITY_Y + 1, v0 + 6, B.AIR)
    for fy in (CITY_Y + 3, CITY_Y + 6):
        frame.light_grid(u0, v0, u1, v1, fy, 4)
    frame.wall_sign(door, CITY_Y + 7, v0, "front", C.split_label(lot.label),
                    color="white", glowing=True)
    frame.wall_sign(door + 1, CITY_Y + 7, v0, "front", ["машины", "мотоциклы", "самолёты"],
                    color="yellow", glowing=True)
    # Площадка: разметка мест, фонари по краю.
    for u in range(u1 + 3, W - 1, 4):
        frame.fill(u, CITY_Y, 2, u, CITY_Y, D - 3, B.ROAD_LINE_WHITE)
    for v in (1, D - 2):
        for u in range(u1 + 2, W - 1, 6):
            frame.fill(u, CITY_Y + 1, v, u, CITY_Y + 3, v, B.CONCRETE_BLACK)
            frame.set(u, CITY_Y + 4, v, B.LAMP)


def dealership_counter(lot: Lot) -> tuple[int, int]:
    """Место автодилера за стойкой шоурума."""
    frame = Frame(None, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    u1 = frame.W // 2
    return frame.world((1 + u1) // 2, 2 + 7)


def build_metro(canvas: RegionCanvas, lot: Lot) -> None:
    """Станция метро: павильон на улице и платформа под землёй."""
    x0, z0, x1, z1 = pad(lot, 6)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    lx0, lz0, lx1, lz1 = pad(lot, 0)
    canvas.fill(lx0, CITY_Y, lz0, lx1, CITY_Y, lz1, B.SIDEWALK)
    canvas.fill(lx0, CITY_Y + 1, lz0, lx1, CITY_Y + 8, lz1, B.AIR)
    canvas.outline(cx - 5, CITY_Y + 1, cz - 5, cx + 5, CITY_Y + 4, cz + 5, B.CONCRETE_BLUE)
    canvas.fill(cx - 5, CITY_Y + 5, cz - 5, cx + 5, CITY_Y + 5, cz + 5, B.QUARTZ)
    window_band(canvas, cx - 5, cz - 5, cx + 5, cz + 5, CITY_Y + 3, B.GLASS, step=2)
    place_door(canvas, cx, CITY_Y + 1, cz + 5, "south")
    canvas.set(cx, CITY_Y + 3, cz + 5, B.CONCRETE_BLUE)
    canvas.set(cx - 1, CITY_Y + 3, cz + 5, B.CONCRETE_BLUE)
    canvas.set(cx + 1, CITY_Y + 3, cz + 5, B.CONCRETE_BLUE)
    canvas.sign(cx + 1, CITY_Y + 3, cz + 6, B.SIGN_WALL.format(f="south"),
                C.split_label(lot.label or "МЕТРО"), color="blue", glowing=True)
    lamp_ceiling(canvas, cx - 5, cz - 5, cx + 5, cz + 5, CITY_Y + 4, 3)

    from .plan import METRO_Y
    shaft_x, shaft_z = cx - 2, cz - 2
    canvas.fill(shaft_x, METRO_Y, shaft_z, shaft_x + 3, CITY_Y, shaft_z + 3, B.AIR)
    # Лестница-стремянка крепится к стене шахты, а не к воздуху.
    canvas.fill(shaft_x, METRO_Y, shaft_z - 1, shaft_x + 3, CITY_Y - 1, shaft_z - 1, B.STONE)
    for y in range(METRO_Y, CITY_Y + 1):
        canvas.set(shaft_x + 1, y, shaft_z, B.LADDER_N.replace("facing=north", "facing=south"))
    canvas.fill(cx - 12, METRO_Y, cz - 4, cx + 12, METRO_Y + 5, cz + 4, B.AIR)
    canvas.fill(cx - 12, METRO_Y - 1, cz - 4, cx + 12, METRO_Y - 1, cz + 4, B.SIDEWALK_EDGE)
    canvas.fill(cx - 12, METRO_Y + 6, cz - 4, cx + 12, METRO_Y + 6, cz + 4, B.STONE)
    canvas.fill(cx - 12, METRO_Y, cz, cx + 12, METRO_Y, cz, B.GRAVEL)
    for x in range(cx - 12, cx + 13):
        canvas.set(x, METRO_Y + 1, cz, B.POWERED_RAIL_EW if x % 8 == 0 else B.RAIL_EW)
        if x % 8 == 0:
            canvas.set(x, METRO_Y, cz, B.REDSTONE_BLOCK)
    for x in range(cx - 10, cx + 11, 5):
        canvas.set(x, METRO_Y + 5, cz - 3, B.LAMP)
        canvas.set(x, METRO_Y + 5, cz + 3, B.LAMP)


def build_construction(canvas: RegionCanvas, lot: Lot) -> None:
    """Стройплощадка: котлован, каркас, строительные леса, забор."""
    rng = _rng(lot)
    x0, z0, x1, z1 = pad(lot, 1)
    canvas.fill(x0, CITY_Y, z0, x1, CITY_Y, z1, B.COARSE_DIRT)
    canvas.fill(x0, CITY_Y + 1, z0, x1, CITY_Y + 12, z1, B.AIR)
    canvas.fill(x0 + 4, CITY_Y - 4, z0 + 4, x1 - 4, CITY_Y, z1 - 4, B.AIR)
    canvas.fill(x0 + 4, CITY_Y - 5, z0 + 4, x1 - 4, CITY_Y - 5, z1 - 4, B.CONCRETE_GRAY)
    for x in range(x0 + 5, x1 - 4, 5):
        for z in range(z0 + 5, z1 - 4, 5):
            canvas.fill(x, CITY_Y - 4, z, x, CITY_Y + rng.randrange(4, 10), z, B.IRON_BLOCK)
    canvas.fill(x0 + 4, CITY_Y - 4, z0 + 3, x1 - 4, CITY_Y + 6, z0 + 3, B.SCAFFOLD)
    for x in range(x0, x1 + 1):
        canvas.set(x, CITY_Y + 1, z0, B.CONCRETE_ORANGE if x % 4 else B.CONCRETE_WHITE)
        canvas.set(x, CITY_Y + 1, z1, B.CONCRETE_ORANGE if x % 4 else B.CONCRETE_WHITE)
    for z in range(z0, z1 + 1):
        canvas.set(x0, CITY_Y + 1, z, B.CONCRETE_ORANGE if z % 4 else B.CONCRETE_WHITE)
        canvas.set(x1, CITY_Y + 1, z, B.CONCRETE_ORANGE if z % 4 else B.CONCRETE_WHITE)
    gx, gz = front_center(x0, z0, x1, z1, lot.facing)
    along = lot.facing in ("north", "south")
    if along:
        canvas.fill(gx - 1, CITY_Y + 1, gz, gx + 1, CITY_Y + 1, gz, B.AIR)
    else:
        canvas.fill(gx, CITY_Y + 1, gz - 1, gx, CITY_Y + 1, gz + 1, B.AIR)
    sx, sz = (gx + 3, gz) if along else (gx, gz + 3)
    rotation = {"north": 8, "south": 0, "west": 4, "east": 12}[lot.facing]
    canvas.sign(sx, CITY_Y + 2, sz, B.SIGN_STANDING.format(r=rotation),
                C.split_label(lot.label or "СТРОЙКА"), color="orange", glowing=True)
    lamp_ceiling(canvas, x0, z0, x1, z1, CITY_Y + 4, 8)


# ---------------------------------------------------------------------------
#  Зелень: большие парки, скверы, свободные участки
# ---------------------------------------------------------------------------

PATH = "minecraft:polished_andesite"
PATH_EDGE = "minecraft:stone_bricks"


def build_park(canvas: RegionCanvas, lot: Lot) -> None:
    """
    Парк на целый квартал: кольцевая аллея, дорожки от входов к центру,
    пруд, фонтан или спортплощадка, клумбы, лавочки и фонари вдоль аллей.
    """
    rng = _rng(lot)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.GRASS)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 14, D - 1, B.AIR)
    cu, cv = W // 2, D // 2
    ring = 6
    # Кольцевая аллея и дорожки-лучи ко всем четырём сторонам.
    for u in range(ring, W - ring):
        for v in (ring, ring + 1, D - ring - 2, D - ring - 1):
            frame.set(u, CITY_Y, v, PATH)
    for v in range(ring, D - ring):
        for u in (ring, ring + 1, W - ring - 2, W - ring - 1):
            frame.set(u, CITY_Y, v, PATH)
    frame.fill(cu - 1, CITY_Y, 0, cu + 1, CITY_Y, D - 1, PATH)
    frame.fill(0, CITY_Y, cv - 1, W - 1, CITY_Y, cv + 1, PATH)

    sport = "СПОРТ" in (lot.label or "")
    pu0, pv0 = ring + 4, cv + 4
    pu1, pv1 = cu - 4, D - ring - 5
    qu0, qv0 = cu + 4, ring + 4
    if sport:
        _court(frame, cu, cv)
    else:
        # Фонтан в центре: чаша, вода и струя.
        frame.fill(cu - 4, CITY_Y, cv - 4, cu + 4, CITY_Y, cv + 4, PATH)
        frame.fill(cu - 3, CITY_Y + 1, cv - 3, cu + 3, CITY_Y + 1, cv + 3, PATH_EDGE)
        frame.fill(cu - 2, CITY_Y, cv - 2, cu + 2, CITY_Y + 1, cv + 2, B.WATER)
        frame.fill(cu - 2, CITY_Y - 1, cv - 2, cu + 2, CITY_Y - 1, cv + 2, PATH_EDGE)
        frame.fill(cu, CITY_Y, cv, cu, CITY_Y + 2, cv, B.QUARTZ)
        frame.set(cu, CITY_Y + 3, cv, B.WATER)
        # Пруд в дальней четверти с песчаным берегом и кувшинками.
        if pu1 - pu0 >= 5 and pv1 - pv0 >= 4:
            frame.fill(pu0 - 1, CITY_Y, pv0 - 1, pu1 + 1, CITY_Y, pv1 + 1, B.SAND)
            frame.fill(pu0, CITY_Y - 2, pv0, pu1, CITY_Y, pv1, B.WATER)
            frame.fill(pu0, CITY_Y - 3, pv0, pu1, CITY_Y - 3, pv1, B.CLAY)
            for _ in range((pu1 - pu0) * (pv1 - pv0) // 12):
                frame.set(rng.randint(pu0, pu1), CITY_Y + 1, rng.randint(pv0, pv1),
                          "minecraft:lily_pad")
        # Детская площадка в соседней четверти.
        if W - ring - 5 - qu0 >= 6 and cv - 4 - qv0 >= 5:
            frame.fill(qu0, CITY_Y, qv0, qu0 + 6, CITY_Y, qv0 + 5, B.SAND)
            for i in range(3):
                frame.fill(qu0 + 1 + i, CITY_Y + 1, qv0 + 2, qu0 + 1 + i, CITY_Y + i, qv0 + 2,
                           B.OAK_PLANKS)
                frame.set(qu0 + 1 + i, CITY_Y + 1 + i, qv0 + 2, frame.stairs("oak", "right"))
            frame.fill(qu0 + 4, CITY_Y + 1, qv0 + 2, qu0 + 4, CITY_Y + 3, qv0 + 2,
                       B.OAK_PLANKS)
            frame.set(qu0 + 1, CITY_Y + 1, qv0 + 4, B.slab("oak"))

    # Деревья и клумбы на газонах, вдали от дорожек. Занятость считаем по
    # геометрии, а не читая полотно: парк на стыке регионов рисуется
    # дважды, и деревья в обеих половинах должны совпасть.
    busy = [(ring - 1, ring - 1, W - ring, ring + 2), (ring - 1, D - ring - 3, W - ring, D - ring),
            (ring - 1, ring - 1, ring + 2, D - ring), (W - ring - 3, ring - 1, W - ring, D - ring),
            (cu - 2, 0, cu + 2, D - 1), (0, cv - 2, W - 1, cv + 2),
            (cu - 6, cv - 6, cu + 6, cv + 6)]
    if not sport:
        busy += [(pu0 - 2, pv0 - 2, pu1 + 2, pv1 + 2), (qu0 - 1, qv0 - 1, qu0 + 7, qv0 + 6)]
    for _ in range(W * D // 8):
        u = rng.randint(2, W - 3)
        v = rng.randint(2, D - 3)
        if any(a - 1 <= u <= c + 1 and b - 1 <= v <= d + 1 for a, b, c, d in busy):
            continue
        busy.append((u - 2, v - 2, u + 2, v + 2))
        if rng.random() < 0.8:
            H.tree(frame, u, v, rng)
        else:
            for du in (-1, 0, 1):
                for dv in (-1, 0, 1):
                    frame.set(u + du, CITY_Y + 1, v + dv, rng.choice(H.FLOWERS))

    # Лавочки и фонари вдоль лучей.
    for u in list(range(3, cu - 5, 7)) + list(range(cu + 6, W - 3, 7)):
        bench(frame, u, cv - 2, "back")
        bench(frame, u, cv + 2, "front")
        street_lamp(frame, u + 3, cv - 2)
    for v in list(range(3, cv - 5, 7)) + list(range(cv + 6, D - 3, 7)):
        street_lamp(frame, cu + 2, v)

    rotation = {"north": 8, "south": 0, "west": 4, "east": 12}[lot.facing]
    x, z = frame.world(cu + 3, 0)
    canvas.sign(x, CITY_Y + 1, z, B.SIGN_STANDING.format(r=rotation),
                C.split_label(lot.label or "ПАРК"), color="green", glowing=True)


def _court(frame: Frame, cu: int, cv: int) -> None:
    """Баскетбольная площадка с кольцами на столбах."""
    frame.fill(cu - 7, CITY_Y, cv - 5, cu + 7, CITY_Y, cv + 5, B.CONCRETE_ORANGE)
    frame.outline(cu - 7, CITY_Y, cv - 5, cu + 7, CITY_Y, cv + 5, B.ROAD_LINE_WHITE)
    frame.fill(cu, CITY_Y, cv - 5, cu, CITY_Y, cv + 5, B.ROAD_LINE_WHITE)
    for side in (-1, 1):
        hu = cu + side * 7
        frame.fill(hu, CITY_Y + 1, cv, hu, CITY_Y + 4, cv, B.IRON_BARS)
        frame.set(hu, CITY_Y + 5, cv, B.CONCRETE_WHITE)


def build_square(canvas: RegionCanvas, lot: Lot) -> None:
    """Сквер между домами: дорожки, деревья, лавочки, фонарь, клумба."""
    rng = _rng(lot)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.GRASS)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 12, D - 1, B.AIR)
    cu, cv = W // 2, D // 2
    frame.fill(cu, CITY_Y, 0, cu, CITY_Y, D - 2, PATH)
    frame.fill(1, CITY_Y, cv, W - 2, CITY_Y, cv, PATH)
    frame.fill(cu - 2, CITY_Y, cv - 2, cu + 2, CITY_Y, cv + 2, PATH)
    if W >= 14 and D >= 14 and rng.random() < 0.5:
        frame.set(cu, CITY_Y + 1, cv, PATH_EDGE)
        frame.set(cu, CITY_Y + 2, cv, "minecraft:potted_azalea_bush")
    else:
        for du in (-1, 0, 1):
            for dv in (-1, 0, 1):
                if du or dv:
                    frame.set(cu + du, CITY_Y + 1, cv + dv, rng.choice(H.FLOWERS))
        frame.set(cu, CITY_Y, cv, B.GRASS)
    if cu - 4 >= 1:
        bench(frame, cu - 4, cv - 1, "back")
    if cu + 4 <= W - 2:
        bench(frame, cu + 3, cv + 1, "front")
    street_lamp(frame, cu + 1, 1)
    for u, v in ((2, 2), (W - 3, 2), (2, D - 3), (W - 3, D - 3)):
        if rng.random() < 0.8:
            H.tree(frame, u, v, rng)


def build_empty(canvas: RegionCanvas, lot: Lot) -> None:
    """
    Свободный участок под стройку игрока: подстриженный газон, межевые
    столбики по углам и табличка «ПРОДАЁТСЯ» с размером и адресом.
    """
    rng = _rng(lot)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.GRASS)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 12, D - 1, B.AIR)
    # Межевые столбики по углам, чтобы границы участка было видно.
    for u, v in ((0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)):
        frame.set(u, CITY_Y + 1, v, B.fence("oak"))
    if rng.random() < 0.3:
        H.tree(frame, W - 4, D - 4, rng)
    cu = W // 2
    frame.set(cu, CITY_Y + 1, 1, B.fence("oak"))
    lines = ["ПРОДАЁТСЯ", f"участок {lot.width}x{lot.depth}"]
    if lot.address:
        street, number = lot.address.rsplit(", ", 1)
        lines += [street, f"№ {number}"]
    rotation = {"north": 8, "south": 0, "west": 4, "east": 12}[lot.facing]
    x, z = frame.world(cu, 1)
    canvas.sign(x, CITY_Y + 2, z, B.SIGN_STANDING.format(r=rotation), lines,
                color="black", glowing=False)


# ---------------------------------------------------------------------------
#  Раздача по типам
# ---------------------------------------------------------------------------

KIND_BUILDERS = {
    "tower": build_tower,
    "mall": build_mall,
    "house": H.build_house,
    "villa": H.build_villa,
    "rowhouse": H.build_rowhouses,
    "apartment": H.build_apartment,
    "parking": build_parking,
    "warehouse": build_warehouse,
    "gas": build_gas,
    "park": build_park,
    "square": build_square,
    "empty": build_empty,
    "construction": build_construction,
    "dealership": build_dealership,
    "metro": build_metro,
    "pickup": build_pickup,
    "office": build_office,
    "club": lambda canvas, lot: build_civic(canvas, lot, 2, 8),
    "shop": lambda canvas, lot: build_civic(canvas, lot, 1),
    "diner": lambda canvas, lot: build_civic(canvas, lot, 1, inside="tables"),
    "bank": lambda canvas, lot: build_civic(canvas, lot, 3, 5),
    "police": lambda canvas, lot: build_civic(canvas, lot, 2, 2),
    "hospital": lambda canvas, lot: build_civic(canvas, lot, 3, 2),
    "city_hall": lambda canvas, lot: build_civic(canvas, lot, 3, 5),
    "fire_station": lambda canvas, lot: build_civic(canvas, lot, 2, 3),
    "gun_shop": lambda canvas, lot: build_civic(canvas, lot, 1, 6),
    "phone_shop": lambda canvas, lot: build_civic(canvas, lot, 1, 2),
}

# Типы, построенные на общем каркасе commercial.py: у них дверь, прилавок
# и место продавца считаются одной функцией.
COMMERCIAL_KINDS = {"shop", "diner", "bank", "police", "hospital", "city_hall",
                    "fire_station", "gun_shop", "phone_shop", "club", "office", "tower",
                    "pickup"}

# Банкоматы стоят там, где их ищут: у банка, мэрии, торгового центра, метро
# и на заправках. Ставим снаружи у входа, лицом на улицу.
ATM_KINDS = {"bank", "city_hall", "mall", "metro", "gas"}

YAW = {"north": 180, "south": 0, "west": 90, "east": 270}


def atm_pos(lot: Lot) -> tuple[int, int, int]:
    """Где у здания стоит банкомат: сбоку от двери, лицом на улицу.

    Считается отдельно от установки, потому что те же координаты нужны
    навигатору в телефоне — метки «Банкомат» строятся из этой функции.
    """
    if lot.kind in COMMERCIAL_KINDS:
        lay = C.layout(lot)
        frame = Frame(None, *lay.frame_rect, lay.facing)
        x, z = frame.world(lay.door_u - 2, lay.v0 - 1)
        return x, CITY_Y + 1, z
    x0, z0, x1, z1 = pad(lot, 1)
    fx, fz = front_center(x0, z0, x1, z1, lot.facing)
    if lot.facing in ("north", "south"):
        x = fx + 3
        z = fz - 1 if lot.facing == "north" else fz + 1
    else:
        x = fx - 1 if lot.facing == "west" else fx + 1
        z = fz + 3
    return x, CITY_Y + 1, z


def place_atm(canvas: RegionCanvas, lot: Lot) -> None:
    x, y, z = atm_pos(lot)
    canvas.set(x, y, z, f"citylife:atm[facing={lot.facing}]")


def entrance_point(lot: Lot) -> tuple[int, int, int]:
    """Точка для навигатора: тротуар перед входом, на уровне ног."""
    dx, dz = OUTWARD[lot.facing]
    if lot.kind in COMMERCIAL_KINDS:
        lay = C.layout(lot)
        x, z = C.door_point(lay)
        steps = lay.v0 + 1           # от двери до края участка и ещё шаг на тротуар
        return x + dx * steps, CITY_Y + 1, z + dz * steps
    if lot.kind == "apartment":
        box, door_u = H.apartment_box(lot)
        frame = Frame(None, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
        x, z = frame.world(door_u, -1)
        return x, CITY_Y + 1, z
    x0, z0, x1, z1 = pad(lot, 1)
    fx, fz = front_center(x0, z0, x1, z1, lot.facing)
    return fx + dx * 3, CITY_Y + 1, fz + dz * 3


def npc_spots(lot: Lot, count: int) -> list[tuple]:
    """Где стоят жители здания: (x, z, поворот головы[, высота ног])."""
    yaw = YAW[lot.facing]
    if lot.kind in COMMERCIAL_KINDS:
        lay = C.layout(lot)
        return [(x, z, yaw) for x, z in C.npc_cells(lay, count)]
    if lot.kind == "apartment":
        box, door_u = H.apartment_box(lot)
        frame = Frame(None, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
        x, z = frame.world(box.u0 + 3, box.v0 + 3)
        # Пол жилого дома на цоколе — на блок выше тротуара.
        return [(x, z, yaw, H.FY + 1)] * count
    if lot.kind == "dealership":
        x, z = dealership_counter(lot)
        return [(x, z, yaw)] * count
    cx, cz = lot.center()
    spots = []
    for index in range(count):
        offset = (index - (count - 1) / 2) * 4
        if lot.facing in ("north", "south"):
            x = int(cx + offset)
            z = lot.z0 + 4 if lot.facing == "north" else lot.z1 - 4
        else:
            z = int(cz + offset)
            x = lot.x0 + 4 if lot.facing == "west" else lot.x1 - 4
        spots.append((x, z, yaw))
    return spots


def build_lot(canvas: RegionCanvas, lot: Lot) -> None:
    builder = KIND_BUILDERS.get(lot.kind)
    if builder is None:
        build_civic(canvas, lot, 2)
    else:
        builder(canvas, lot)
    if lot.kind in ATM_KINDS:
        place_atm(canvas, lot)
