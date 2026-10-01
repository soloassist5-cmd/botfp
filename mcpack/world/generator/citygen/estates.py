"""
Прибрежные районы для богатых: Береговой и Приморский.

Оба лежат за пределами городской сетки, у западного берега:

* Береговой — на север от города, по холмам над океаном. Дорога
  продолжает 1-ю улицу и плавно поднимается в гору, усадьбы стоят по обе
  стороны на выровненных террасах, с них виден океан;
* Приморский — на юг, у моря: продолжение той же улицы и шоссе вдоль
  берега на восток, участки ровные, на уровне города.

Усадьба — это участок 40×44 за высоким каменным забором: ворота для
машин и калитка, подъездная аллея с фонтаном, вилла в два-три этажа с
обставленными комнатами, гараж, бассейн с шезлонгами и пальмы.

Раскладка — чистая функция от сида, её зовут и план города (участки),
и рельеф (площадки под домами и дорогами), и отрисовка дорог.
"""
from __future__ import annotations

import random
from dataclasses import dataclass, field
from functools import lru_cache

from . import blocks as B
from . import furniture as F
from .frame import Frame
from .plan import CITY_Y, Lot

ROAD_X = -512           # улица, которую продолжают оба района (1-я улица)
ROAD_HALF = 5
SIDEWALK = 2
LOT_W = 40              # вдоль дороги
LOT_D = 44              # вглубь
WEST = (-566, -523)     # участки к океану
EAST = (-501, -458)
NORTH_END = -1000
SOUTH_END = 1000
SHORE_ROAD_Z = 904      # шоссе Приморского на восток
SHORE_ROAD_X1 = -280

DISTRICTS = {"beregovoy": "Береговой", "primorsky": "Приморский"}
STREETS = {"beregovoy": "Береговая ул.", "primorsky": "Приморское ш."}


@dataclass
class Road:
    """Отрезок дороги: вдоль x (z постоянна) или вдоль z, высота по точкам."""
    vertical: bool       # True — идёт вдоль z (x постоянна)
    fixed: int           # x для вертикальной, z для горизонтальной
    a: int               # от
    b: int               # до (включительно, a <= b)
    heights: dict[int, int] = field(default_factory=dict)

    def h(self, t: int) -> int:
        return self.heights.get(t, CITY_Y)


@dataclass
class Layout:
    roads: list[Road]
    lots: list[Lot]
    pads: list[tuple[int, int, int, int, int, int]]   # x0, z0, x1, z1, высота, откос


def _profile(terrain, z_from: int, z_to: int) -> dict[int, int]:
    """
    Высота дороги на север от города: тянется за рельефом, но не круче
    блока на восемь — по такой дороге въедет любая машина.
    """
    heights = {}
    h = CITY_Y
    step = -1 if z_to < z_from else 1
    since = 0
    for z in range(z_from, z_to + step, step):
        target = sum(terrain.natural(ROAD_X + dx, z + dz)
                     for dx in (-24, 0, 24) for dz in (-24, 0, 24)) / 9
        target = max(CITY_Y, min(CITY_Y + 40, int(round(target))))
        since += 1
        if since >= 8 and target != h:
            h += 1 if target > h else -1
            since = 0
        heights[z] = h
    return heights


@lru_cache(maxsize=4)
def layout(seed: int) -> Layout:
    from .plan import CITY_BOUNDS
    from .terrain import Terrain
    natural = Terrain(seed, CITY_BOUNDS, estates=False)
    roads: list[Road] = []
    lots: list[Lot] = []
    pads = []
    rng = random.Random(seed ^ 0xE57A7E)

    north = Road(True, ROAD_X, NORTH_END, -457, _profile(natural, -457, NORTH_END))
    south = Road(True, ROAD_X, 780, SOUTH_END, {z: CITY_Y for z in range(780, SOUTH_END + 1)})
    shore = Road(False, SHORE_ROAD_Z, ROAD_X + ROAD_HALF + 1, SHORE_ROAD_X1,
                 {x: CITY_Y for x in range(ROAD_X, SHORE_ROAD_X1 + 1)})
    roads += [north, south, shore]
    # Площадки под дорогами: по куску на каждые восемь блоков.
    for road in roads:
        for t in range(road.a, road.b + 1, 8):
            t1 = min(road.b, t + 7)
            hh = road.h(t)
            w = ROAD_HALF + SIDEWALK
            if road.vertical:
                pads.append((road.fixed - w, t, road.fixed + w, t1, hh, 8))
            else:
                pads.append((t, road.fixed - w, t1, road.fixed + w, hh, 8))

    def add(district: str, x0: int, z0: int, x1: int, z1: int, facing: str, ground: int):
        lot = Lot(ix=900 + len(lots), iz=900, x0=x0, z0=z0, x1=x1, z1=z1, kind="mansion",
                  district=district, facing=facing, seed=rng.randrange(1 << 30))
        lot.ground = ground
        lots.append(lot)
        pads.append((x0, z0, x1, z1, ground, 12))

    # Береговой: два ряда вдоль дороги в гору.
    zc = -480
    while zc - LOT_W // 2 >= NORTH_END + 4:
        z0, z1 = zc - LOT_W // 2, zc + LOT_W // 2 - 1
        g = north.h(zc)
        add("beregovoy", WEST[0], z0, WEST[1], z1, "east", g)
        add("beregovoy", EAST[0], z0, EAST[1], z1, "west", g)
        zc -= LOT_W + 6
    # Приморский: ряд к морю вдоль улицы, ряд напротив и два ряда вдоль шоссе.
    for zc in (822, 868, 940, 986):
        z0, z1 = zc - LOT_W // 2, zc + LOT_W // 2 - 1
        if z1 > SOUTH_END + 10:
            continue
        add("primorsky", WEST[0], z0, WEST[1], z1, "east", CITY_Y)
        add("primorsky", EAST[0], z0, EAST[1], z1, "west", CITY_Y)
    for j in range(4):
        x0 = EAST[1] + 10 + j * (LOT_W + 6)
        x1 = x0 + LOT_W - 1
        if x1 > SHORE_ROAD_X1:
            break
        w = ROAD_HALF + SIDEWALK
        add("primorsky", x0, SHORE_ROAD_Z - w - LOT_D, x1, SHORE_ROAD_Z - w - 1, "south", CITY_Y)
        add("primorsky", x0, SHORE_ROAD_Z + w + 1, x1, SHORE_ROAD_Z + w + LOT_D, "north", CITY_Y)

    # Адреса: чётные по одной стороне, нечётные по другой — от города.
    for district in DISTRICTS:
        mine = [l for l in lots if l.district == district]
        mine.sort(key=lambda l: (abs(l.z0) if l.facing in ("east", "west") else 10000 + l.x0,
                                 l.facing))
        for i, lot in enumerate(mine):
            lot.address = f"{STREETS[district]}, {i + 1}"
            lot.label = f"УСАДЬБА {i + 1}"
    return Layout(roads, lots, pads)


# ---------------------------------------------------------------------------
#  Дороги
# ---------------------------------------------------------------------------

def draw_roads(canvas, seed: int) -> None:
    """Асфальт, тротуары, разметка и фонари дорог прибрежных районов."""
    for road in layout(seed).roads:
        w = ROAD_HALF + SIDEWALK
        if road.vertical:
            if not canvas.covers(road.fixed - w, road.a, road.fixed + w, road.b):
                continue
        elif not canvas.covers(road.a, road.fixed - w, road.b, road.fixed + w):
            continue
        for t in range(road.a, road.b + 1):
            h = road.h(t)
            for o in range(-w, w + 1):
                x, z = (road.fixed + o, t) if road.vertical else (t, road.fixed + o)
                edge = abs(o) > ROAD_HALF
                canvas.column(x, z, h - 3, h - 1, B.STONE)
                canvas.set(x, h, z, B.SIDEWALK if edge else B.ASPHALT)
                canvas.column(x, z, h + 1, h + 7, B.AIR)
                if abs(o) == ROAD_HALF + 1:
                    canvas.set(x, h, z, B.CURB)
                if not edge and o == 0 and t % 4 < 2:
                    canvas.set(x, h, z, B.ROAD_LINE)
                # Полублок перед подъёмом: дорога поднимается на полблока за раз.
                if road.h(t + 1) > h or road.h(t - 1) > h:
                    canvas.set(x, h + 1, z, B.slab("smooth_stone" if edge else "blackstone"))
            if t % 24 == 0:
                for side in (-1, 1):
                    o = side * (ROAD_HALF + 2)
                    x, z = (road.fixed + o, t) if road.vertical else (t, road.fixed + o)
                    canvas.fill(x, h + 1, z, x, h + 4, z, B.CONCRETE_BLACK)
                    canvas.set(x, h + 5, z, B.LAMP)


# ---------------------------------------------------------------------------
#  Усадьба
# ---------------------------------------------------------------------------

WALLS = [("minecraft:white_concrete", "minecraft:quartz_pillar[axis=y]", "smooth_quartz"),
         ("minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", "stone_brick"),
         ("minecraft:smooth_sandstone", "minecraft:cut_sandstone", "smooth_sandstone"),
         ("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone",
          "polished_blackstone_brick"),
         ("minecraft:mud_bricks", "minecraft:packed_mud", "mud_brick")]


def mansion_box(lot: Lot):
    """Габарит виллы: тот же расчёт нужен риелтору."""
    from .homes import Box
    rng = random.Random(lot.seed ^ 0x3A7)
    # Дверь — левее середины: справа от прихожей гостиная, дальше кухня.
    floors = 3 if rng.random() < 0.4 else 2
    return Box(4, 12, 24, 14, floors), 4 + 12


def build_mansion(canvas, lot: Lot) -> None:
    from . import homes as H
    rng = random.Random(lot.seed)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing, dy=lot.ground - CITY_Y)
    W, D = frame.W, frame.D
    wall, pillar, cap = rng.choice(WALLS)
    style = rng.choice(H.MODERN_STYLES)
    box, door_u = mansion_box(lot)
    gate_u = W // 2

    # Земля, газон, плитка.
    frame.fill(0, CITY_Y - 3, 0, W - 1, CITY_Y - 1, D - 1, B.DIRT)
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.GRASS)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 20, D - 1, B.AIR)

    # Высокий забор с колоннами и фонарями на них.
    for u in range(W):
        for v in (0, D - 1):
            frame.fill(u, CITY_Y + 1, v, u, CITY_Y + 4, v, wall)
    for v in range(D):
        for u in (0, W - 1):
            frame.fill(u, CITY_Y + 1, v, u, CITY_Y + 4, v, wall)
    for u in list(range(0, W, 6)) + [W - 1]:
        for v in (0, D - 1):
            frame.fill(u, CITY_Y + 1, v, u, CITY_Y + 5, v, pillar)
            frame.set(u, CITY_Y + 6, v, B.LANTERN)
    for v in list(range(6, D - 1, 6)):
        for u in (0, W - 1):
            frame.fill(u, CITY_Y + 1, v, u, CITY_Y + 5, v, pillar)
            frame.set(u, CITY_Y + 6, v, B.LANTERN)
    # Ворота для машин: створки-калитки, над проездом арка.
    gate = f"minecraft:dark_oak_fence_gate[facing={frame.dir('front')},in_wall=false," \
           f"open=false,powered=false]"
    for u in range(gate_u - 2, gate_u + 3):
        frame.set(u, CITY_Y + 1, 0, gate)
        frame.fill(u, CITY_Y + 2, 0, u, CITY_Y + 3, 0, B.AIR)
    for u in (gate_u - 3, gate_u + 3):
        frame.fill(u, CITY_Y + 1, 0, u, CITY_Y + 5, 0, pillar)
        frame.set(u, CITY_Y + 6, 0, B.LANTERN)
    # Калитка рядом с воротами.
    frame.door(gate_u + 5, CITY_Y + 1, 0, "front", "dark_oak")
    frame.wall_sign(gate_u - 5, CITY_Y + 2, 0, "front",
                    [lot.label, DISTRICTS[lot.district], lot.address.split(", ")[0],
                     "частное владение"], color="white", glowing=True)

    # Аллея от ворот: к дому, к гаражу и фонтан по пути.
    path = "minecraft:polished_andesite"
    frame.fill(gate_u - 2, CITY_Y, 1, gate_u + 2, CITY_Y, 9, path)
    frame.fill(door_u - 1, CITY_Y, 9, door_u + 1, CITY_Y, box.v0 - 1, path)
    frame.fill(gate_u + 5, CITY_Y, 1, gate_u + 5, CITY_Y, 3, path)
    fu, fv = gate_u, 6
    frame.fill(fu - 3, CITY_Y, fv - 3, fu + 3, CITY_Y, fv + 3, path)
    frame.fill(fu - 1, CITY_Y + 1, fv - 1, fu + 1, CITY_Y + 1, fv + 1, B.QUARTZ_SMOOTH)
    frame.set(fu, CITY_Y + 1, fv, B.WATER)
    frame.set(fu, CITY_Y + 2, fv, B.QUARTZ)
    frame.set(fu, CITY_Y + 3, fv, B.WATER)
    g0, g1 = box.u1 + 3, W - 3
    frame.fill(g0, CITY_Y, 1, g1, CITY_Y, box.v0 + 6, path)
    # Деревья и фонари во дворе перед домом.
    for u in (3, W - 4):
        H.tree(frame, u, 4, rng)
    for u in (gate_u - 4, gate_u + 4):
        for v in (3, 9):
            frame.set(u, CITY_Y + 1, v, "mcwlights:classic_street_lamp[part=base,lit=true]")
            frame.set(u, CITY_Y + 2, v, "mcwlights:classic_street_lamp[part=middle,lit=true]")
            frame.set(u, CITY_Y + 3, v, "mcwlights:classic_street_lamp[part=top,lit=true]")

    # Дом.
    H.shell(frame, box, style, door_u)
    _big_windows(frame, box, style)
    _mansion_interior(frame, box, style, door_u, rng)
    T = H.flat_roof(frame, box, style)
    # Терраса на крыше со стеклянным ограждением и шезлонгами.
    for u in range(box.u0, box.u1 + 1):
        for v in (box.v0, box.v1):
            frame.set(u, T, v, B.GLASS_PANE)
    for v in range(box.v0, box.v1 + 1):
        for u in (box.u0, box.u1):
            frame.set(u, T, v, B.GLASS_PANE)
    for u in range(box.u0 + 6, box.u1 - 2, 2):
        frame.set(u, T, box.v1 - 2, "mcwfurnitures:white_chaise")
    for u, v in ((box.u0 + 6, box.v0 + 2), (box.u1 - 2, box.v0 + 2)):
        F.plant(frame, u, T, v, rng)
    H.light_all(frame, box)

    # Гараж справа от дома.
    gv0, gv1 = box.v0, box.v0 + 8
    frame.fill(g0, CITY_Y, gv0, g1, CITY_Y, gv1, B.CONCRETE_GRAY)
    frame.outline(g0, CITY_Y + 1, gv0, g1, CITY_Y + 4, gv1, style.wall)
    frame.fill(g0, CITY_Y + 5, gv0, g1, CITY_Y + 5, gv1, style.trim)
    frame.fill(g0 + 1, CITY_Y + 1, gv0, g1 - 1, CITY_Y + 3, gv0, B.AIR)
    frame.fill(g0 + 1, CITY_Y + 4, gv0, g1 - 1, CITY_Y + 4, gv0, style.trim)
    frame.light((g0 + g1) // 2, CITY_Y + 4, (gv0 + gv1) // 2)
    frame.light((g0 + g1) // 2, CITY_Y + 2, gv0 + 2)
    F.wall_shelf(frame, g1 - 1, CITY_Y + 2, gv1 - 1, "front", "minecraft:iron_shovel")

    # Задний двор: бассейн, настил, шезлонги, пальмы, барбекю.
    pv0, pv1 = box.v1 + 4, min(D - 6, box.v1 + 11)
    pu0, pu1 = 6, W - 7
    frame.fill(pu0 - 2, CITY_Y, pv0 - 2, pu1 + 2, CITY_Y, pv1 + 3, B.QUARTZ_SMOOTH)
    frame.fill(pu0, CITY_Y - 3, pv0, pu1, CITY_Y - 3, pv1, "minecraft:light_blue_concrete")
    frame.fill(pu0 - 1, CITY_Y - 3, pv0 - 1, pu1 + 1, CITY_Y - 1, pv1 + 1, B.QUARTZ_SMOOTH)
    frame.fill(pu0, CITY_Y - 2, pv0, pu1, CITY_Y, pv1, B.WATER)
    for u in range(pu0, pu1 + 1, 4):
        frame.set(u, CITY_Y - 2, pv0 - 1, "minecraft:sea_lantern")
    for u in range(pu0, pu1 + 1, 3):
        frame.set(u, CITY_Y + 1, pv1 + 2, "mcwfurnitures:white_chaise")
    for u, v in ((4, pv0 - 2), (W - 5, pv0 - 2), (4, D - 4), (W - 5, D - 4)):
        H.palm(frame, u, v, rng)
    bu, bv = W - 4, box.v1 + 2
    F.put(frame, bu, CITY_Y + 1, bv, "farmersdelight:stove", "back", lit=False)
    F.put(frame, bu - 1, CITY_Y + 1, bv, "mcwfurnitures:oak_counter", "back")
    F.put(frame, bu - 2, CITY_Y + 1, bv, "mcwfurnitures:oak_counter", "back")
    F.dining(frame, bu - 2, CITY_Y + 1, bv + 3, bu - 1, bv + 3, rng, "mcwfurnitures:oak_table",
             "another_furniture:oak_chair")
    # Живая изгородь вдоль забора на заднем дворе.
    for v in range(box.v1 + 1, D - 1):
        for u in (1, W - 2):
            if frame.get_name(u, CITY_Y + 1, v) == "minecraft:air":
                frame.set(u, CITY_Y + 1, v, B.OAK_LEAVES)


def _big_windows(frame: Frame, box, style) -> None:
    """Панорамное остекление: на задний фасад окна от пола до потолка."""
    for k in range(box.floors):
        fy = CITY_Y + 1 + 4 * k
        for u in range(box.u0 + 2, box.u1 - 1):
            if (u - box.u0) % 5 == 0:
                continue
            frame.fill(u, fy + 1, box.v1, u, fy + 3, box.v1, B.GLASS_PANE)


def _mansion_interior(frame: Frame, box, style, door_u: int, rng: random.Random) -> None:
    """
    Первый этаж: прихожая, кабинет, гостиная с диванами и кухня-столовая.
    Второй: спальни хозяев и детей, гардероб, холл. Третий — гостиная с баром.
    Лестница — П-образная клетка у задней левой стены.
    """
    from . import homes as H
    from . import interiors as I
    u0, v0, u1, v1 = box.u0, box.v0, box.u1, box.v1
    FY = CITY_Y + 1
    inner = style.floor
    wood = H._plank_mat(style.floor)
    wood = wood if wood in ("oak", "spruce", "birch", "dark_oak") else "oak"
    floors = [FY + 4 * k for k in range(box.floors)]
    stair = (u0 + 1, v1 - 5, u0 + 5, v1 - 1)

    # --- первый этаж ---
    fy = FY
    # Кабинет за перегородкой в левом переднем углу.
    H._partition(frame, u0 + 7, v0 + 1, u0 + 7, v0 + 5, fy, inner)
    H._partition(frame, u0 + 1, v0 + 6, u0 + 7, v0 + 6, fy, inner)
    frame.door(u0 + 7, fy + 1, v0 + 3, "right", "dark_oak")
    frame.set(u0 + 7, fy + 3, v0 + 3, inner)
    study = I.Floor(frame, u0 + 1, v0 + 1, u0 + 6, v0 + 5, fy, rng, [(u0 + 6, v0 + 3, u0 + 6, v0 + 3)])
    H._study(study, rng, wood)
    I.plants_in_corners(study)
    keep = [stair, (door_u - 1, v0 + 1, door_u + 1, v0 + 3), (u0 + 8, v0 + 3, u0 + 8, v0 + 3),
            (u0 + 1, v0 + 7, u0 + 6, v1 - 5)]
    living = I.Floor(frame, u0 + 8, v0 + 1, u1 - 9, v1 - 1, fy, rng, keep)
    H._living(living, rng, wood)
    I.lounge(living, u0 + 9, v1 - 4, u0 + 13, v1 - 1, rng.choice(F.SOFA_COLORS))
    kitchen = I.Floor(frame, u1 - 8, v0 + 1, u1 - 1, v1 - 1, fy, rng, keep)
    F.kitchen(frame, u1 - 6, fy + 1, v1 - 1, u1 - 1, "front", rng, wood=wood)
    F.fridge(frame, u1 - 7, fy + 1, v1 - 1, "front")
    kitchen.take(u1 - 7, v1 - 2, u1 - 1, v1 - 1)
    # Остров со стульями-табуретами.
    for u in range(u1 - 6, u1 - 2):
        F.put(frame, u, fy + 1, v1 - 4, f"mcwfurnitures:{wood}_counter", "front")
        F.stool(frame, u, fy + 1, v1 - 5)
    kitchen.take(u1 - 6, v1 - 5, u1 - 3, v1 - 3)
    if kitchen.free(u1 - 6, v0 + 1, u1 - 3, v0 + 3):
        F.dining(frame, u1 - 6, fy + 1, v0 + 2, u1 - 3, v0 + 2, rng,
                 f"mcwfurnitures:{wood}_table", f"another_furniture:{wood}_chair")
    frame.light((u0 + u1) // 2, fy + 3, (v0 + v1) // 2)

    # --- второй этаж ---
    fy = FY + 4
    H._partition(frame, u1 - 9, v0 + 1, u1 - 9, v1 - 1, fy, inner)
    frame.door(u1 - 9, fy + 1, v1 - 3, "left", "dark_oak")
    frame.set(u1 - 9, fy + 3, v1 - 3, inner)
    master = I.Floor(frame, u1 - 8, v0 + 1, u1 - 1, v1 - 1, fy, rng,
                     [(u1 - 8, v1 - 3, u1 - 8, v1 - 3)])
    H._bedroom(master, rng, wood, double=True)
    if master.free(u1 - 4, v0 + 1, u1 - 4, v0 + 1):
        F.tv(frame, u1 - 4, fy + 1, v0 + 1, "back")
        master.take(u1 - 4, v0 + 1, u1 - 4, v0 + 1)
    H._partition(frame, u0 + 7, v0 + 6, u1 - 10, v0 + 6, fy, inner)
    frame.door(u0 + 10, fy + 1, v0 + 6, "back", "dark_oak")
    frame.set(u0 + 10, fy + 3, v0 + 6, inner)
    kids = I.Floor(frame, u0 + 7, v0 + 1, u1 - 10, v0 + 5, fy, rng,
                   [(u0 + 10, v0 + 5, u0 + 10, v0 + 5)])
    H._bedroom(kids, rng, wood, double=True)
    hall = I.Floor(frame, u0 + 1, v0 + 1, u1 - 10, v1 - 1, fy, rng,
                   [stair, (u0 + 10, v0 + 7, u0 + 10, v0 + 7), (u1 - 10, v1 - 3, u1 - 10, v1 - 3),
                    (u0 + 7, v0 + 1, u1 - 10, v0 + 6)])
    I.lounge(hall, u0 + 8, v1 - 4, u0 + 12, v1 - 1, rng.choice(F.SOFA_COLORS))
    for u in range(u0 + 1, u0 + 6):
        if hall.free(u, v0 + 1, u, v0 + 1):
            F.wardrobe(frame, u, fy + 1, v0 + 1, "back", wood, modern=True)
            hall.take(u, v0 + 1, u, v0 + 1)
    I.plants_in_corners(hall)
    frame.light((u0 + u1) // 2, fy + 3, (v0 + v1) // 2)

    # --- третий этаж: гостиная с баром ---
    if box.floors >= 3:
        fy = FY + 8
        top = I.Floor(frame, u0 + 1, v0 + 1, u1 - 1, v1 - 1, fy, rng, [stair])
        I.lounge_floor(top, u0 + 5)

    frame.stairwell(u0 + 1, v1 - 3, floors, wood, f"minecraft:{wood}_planks", inner,
                    B.fence("dark_oak"))


# ---------------------------------------------------------------------------
#  Недвижимость и навигация
# ---------------------------------------------------------------------------

def entrance_point(lot: Lot) -> tuple[int, int, int]:
    """Тротуар перед калиткой."""
    frame = Frame(None, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    x, z = frame.world(frame.W // 2 + 5, -4)
    return x, lot.ground + 1, z


def estate_unit(lot: Lot) -> dict:
    from .homes import DISTRICT_PRICE
    frame = Frame(None, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    box, door_u = mansion_box(lot)
    ax, az = frame.world(box.u0, box.v0)
    bx, bz = frame.world(box.u1, box.v1)
    dy = lot.ground - CITY_Y
    area = box.w * box.d
    rate = DISTRICT_PRICE.get(lot.district, 900)
    price = max(10, round(area * box.floors * rate * 2.2 / 1000)) * 1000
    dx, dz = frame.world(door_u, box.v0)
    return {
        "id": f"m_{lot.x0}_{lot.z0}",
        "kind": "mansion",
        "title": "Усадьба",
        "address": lot.address,
        "district": lot.district,
        "price": price,
        "rooms": f"{box.floors} эт., {area} м², бассейн, гараж",
        "box": [min(ax, bx), CITY_Y - 2 + dy, min(az, bz), max(ax, bx), box.top + 9 + dy,
                max(az, bz)],
        "plot": [lot.x0, CITY_Y - 4 + dy, lot.z0, lot.x1, box.top + 12 + dy, lot.z1],
        "door": [dx, CITY_Y + 2 + dy, dz],
    }
