"""
Общественные и коммерческие здания: лавки, кафе, банк, мэрия, полиция,
больница, офисы и небоскрёбы.

Что было не так в прошлой версии и как сделано теперь:

* вывеска вставлялась в полосу акцентных блоков, выступающую из стены,
  и заменяла один из них — выглядело криво. Теперь табличка висит на
  стене над козырьком, а стена за ней всегда цельная;
* перед дверью лежали ступени и плиты на уровне земли — ямы в тротуаре.
  Пол магазина вровень с тротуаром, ступеней нет;
* над дверью оставалась дыра, цепи козырька висели в воздухе. Над дверью
  фрамуга из стекла, козырёк лежит на стене;
* лестница на этажи была приставной и висела в воздухе. Теперь это
  настоящий марш с проёмом в перекрытии и перилами;
* на каждом этаже равномерный свет, прилавок стоит так, что продавцу
  есть где стоять, а покупателю — куда подойти.
"""
from __future__ import annotations

import random
from dataclasses import dataclass

from . import blocks as B
from . import furniture as F
from . import interiors as I
from . import shops as S
from .frame import Frame
from .homes import window_cells
from .interiors import Floor
from .plan import CITY_Y, Lot

STOREY = 4

DISTRICT_FLOORS = {
    "downtown": (4, 8),
    "midtown": (2, 4),
    "beach": (1, 3),
    "suburbs": (1, 2),
    "hills": (1, 2),
    "industrial": (1, 2),
    "eastside": (1, 3),
}

# Без гравия: крыша в один блок, и гравий осыпался внутрь от любого касания.
# Туф выглядит так же, но не падает.
ROOF_FINISH = [B.CONCRETE_GRAY, "minecraft:tuff", B.CONCRETE_LIGHT, "minecraft:smooth_stone",
               B.DEEPSLATE_TILES]


@dataclass
class Layout:
    """Всё, что нужно знать о здании снаружи: габарит, дверь, прилавок."""
    frame_rect: tuple[int, int, int, int]
    facing: str
    u0: int
    v0: int
    w: int
    d: int
    floors: int
    door_u: int          # левая створка двойной двери
    counter_v: int
    facade: tuple[str, str, str, str]

    @property
    def u1(self) -> int:
        return self.u0 + self.w - 1

    @property
    def v1(self) -> int:
        return self.v0 + self.d - 1

    @property
    def top(self) -> int:
        return CITY_Y + STOREY * self.floors


def _rng(lot: Lot) -> random.Random:
    return random.Random(lot.seed ^ 0xC0FFEE)


def layout(lot: Lot, floors: int = 1, facade_index: int | None = None,
           facade: tuple[str, str, str, str] | None = None,
           exact_floors: int | None = None) -> Layout:
    """Габарит здания на участке. Чистая функция: её зовут и NPC, и навигатор."""
    rng = _rng(lot)
    along = lot.facing in ("north", "south")
    W = lot.width if along else lot.depth
    D = lot.depth if along else lot.width
    big = W >= 36
    side = rng.randint(2, 4) if big else 1
    front = 2 if big else 1
    back = rng.randint(4, 8) if big else max(3, min(8, D - 16))
    w = W - 2 * side
    d = max(8, min(D - front - back, 34))
    low, high = DISTRICT_FLOORS.get(lot.district, (1, 3))
    count = max(1, min(high, rng.randint(low, high) + (floors - 2) // 2))
    if W < 20:
        count = min(count, 3)
    if floors == 1:
        count = min(count, 2) if big else 1
    picked = B.FACADE_SETS[rng.randrange(len(B.FACADE_SETS))
                           if facade_index is None else facade_index]
    facade = facade or picked
    if exact_floors is not None:
        count = exact_floors
    door_u = side + w // 2 - 1
    counter_v = front + min(d - 4, 7)
    return Layout((lot.x0, lot.z0, lot.x1, lot.z1), lot.facing, side, front, w, d,
                  count, door_u, counter_v, facade)


def frame_of(canvas, lay: Layout) -> Frame:
    x0, z0, x1, z1 = lay.frame_rect
    return Frame(canvas, x0, z0, x1, z1, lay.facing)


def door_point(lay: Layout) -> tuple[int, int]:
    """Мировые координаты клетки тротуара прямо перед дверью."""
    frame = Frame(None, *lay.frame_rect, lay.facing)
    return frame.world(lay.door_u, lay.v0 - 1)


def npc_cells(lay: Layout, count: int) -> list[tuple[int, int]]:
    """Где стоят продавцы: за прилавком, по центру и с шагом в 3 блока."""
    frame = Frame(None, *lay.frame_rect, lay.facing)
    centre = lay.door_u
    cells = []
    for i in range(count):
        offset = int((i - (count - 1) / 2) * 3)
        cells.append(frame.world(centre + offset, lay.counter_v + 1))
    return cells


# ---------------------------------------------------------------------------
#  Сборка
# ---------------------------------------------------------------------------

def body(frame: Frame, lay: Layout, rng: random.Random, storefront: bool = True,
         floor_block: str = B.QUARTZ_SMOOTH, canopy: bool = True) -> None:
    """Коробка: перекрытия, стены, окна, витрина, вход и кровля. Лестница — отдельно."""
    wall, accent, glass, cap = lay.facade
    u0, v0, u1, v1 = lay.u0, lay.v0, lay.u1, lay.v1
    frame.fill(u0, CITY_Y - 2, v0, u1, CITY_Y - 1, v1, B.CONCRETE_GRAY)
    frame.fill(u0, CITY_Y + 1, v0, u1, lay.top + 2, v1, B.AIR)

    for k in range(lay.floors):
        fy = CITY_Y + STOREY * k
        frame.fill(u0, fy, v0, u1, fy, v1, floor_block if k == 0 else UPPER_FLOORS[k % 3])
        frame.outline(u0, fy, v0, u1, fy, v1, accent)
        frame.outline(u0, fy + 1, v0, u1, fy + 3, v1, wall)
        for side in ("front", "back", "left", "right"):
            if k == 0 and side == "front" and storefront:
                continue
            length = lay.w if side in ("front", "back") else lay.d
            for i in window_cells(length, set()):
                if side in ("front", "back"):
                    v = v0 if side == "front" else v1
                    frame.fill(u0 + i, fy + 2, v, u0 + i, fy + 3, v, glass)
                else:
                    u = u0 if side == "left" else u1
                    frame.fill(u, fy + 2, v0 + i, u, fy + 3, v0 + i, glass)

    # Потолок верхнего этажа — кровля.
    T = lay.top
    frame.fill(u0, T, v0, u1, T, v1, rng.choice(ROOF_FINISH))
    frame.outline(u0, T, v0, u1, T, v1, accent)
    frame.outline(u0, T + 1, v0, u1, T + 1, v1, wall)
    frame.outline(u0, T + 2, v0, u1, T + 2, v1, B.slab(cap))
    # Угловые лопатки.
    for u, v in ((u0, v0), (u1, v0), (u0, v1), (u1, v1)):
        frame.fill(u, CITY_Y + 1, v, u, T + 1, v, accent)

    # Витрина первого этажа и вход.
    du = lay.door_u
    if storefront:
        frame.fill(u0 + 1, CITY_Y + 1, v0, u1 - 1, CITY_Y + 3, v0, glass)
    frame.door(du, CITY_Y + 1, v0, "front", "dark_oak" if not storefront else "birch",
               hinge="left")
    frame.door(du + 1, CITY_Y + 1, v0, "front", "dark_oak" if not storefront else "birch",
               hinge="right")
    frame.fill(du, CITY_Y + 3, v0, du + 1, CITY_Y + 3, v0, glass if storefront else wall)
    # Козырёк — верхние плиты по всей витрине, лежат на поясе.
    if canopy:
        for u in range(u0 + 1, u1):
            frame.set(u, CITY_Y + 4, v0 - 1, B.slab(cap, top=True))


# Полы верхних этажей чередуются: в офисах ковролин, плитка и паркет.
UPPER_FLOORS = (B.CONCRETE_LIGHT, "minecraft:polished_andesite", "minecraft:spruce_planks")


def has_stairs(lay: Layout) -> bool:
    return lay.floors > 1 and lay.d >= 10 and lay.w >= 11


def stair_keep_out(lay: Layout) -> tuple[int, int, int, int]:
    """Клетки у задней левой стены, где лестница и проход к ней: мебель туда не ставим."""
    return lay.u0 + 1, lay.v1 - 5, lay.u0 + 5, lay.v1 - 1


def stairs(frame: Frame, lay: Layout) -> None:
    """Лестница на все этажи у задней левой стены (см. Frame.stairwell)."""
    if not has_stairs(lay):
        return
    floors = [CITY_Y + STOREY * k for k in range(lay.floors)]
    frame.stairwell(lay.u0 + 1, lay.v1 - 3, floors, "smooth_quartz", B.QUARTZ_SMOOTH,
                    lay.facade[0], B.IRON_BARS)


def sign(frame: Frame, lay: Layout, label: str, subtitle: list[str] | None = None,
         color: str = "white") -> None:
    """
    Вывеска на стене над козырьком: название над левой створкой двери,
    часы работы или род занятий — над правой.
    """
    if not label:
        return
    frame.wall_sign(lay.door_u, CITY_Y + 5, lay.v0, "front", split_label(label),
                    color=color, glowing=True)
    frame.wall_sign(lay.door_u + 1, CITY_Y + 5, lay.v0, "front",
                    subtitle or ["ОТКРЫТО", "ежедневно"], color="yellow", glowing=True)
    # На длинном фасаде название повторяется ближе к углам — его видно
    # с любого конца улицы, а не только напротив двери.
    if lay.w >= 24:
        for u in (lay.u0 + 3, lay.u1 - 3):
            frame.wall_sign(u, CITY_Y + 5, lay.v0, "front", split_label(label),
                            color=color, glowing=True)


def split_label(label: str) -> list[str]:
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


def rooftop(frame: Frame, lay: Layout, rng: random.Random) -> None:
    """Вентиляция, бак на опорах и антенна — всё стоит на кровле, ничего не висит."""
    T = lay.top
    if lay.w < 9 or lay.d < 9:
        return
    for _ in range(rng.randint(1, 3)):
        u = rng.randint(lay.u0 + 2, lay.u1 - 3)
        v = rng.randint(lay.v0 + 2, lay.v1 - 3)
        frame.fill(u, T + 1, v, u + 1, T + 2, v + 1, B.CONCRETE_LIGHT)
        frame.fill(u, T + 3, v, u + 1, T + 3, v + 1, B.slab("smooth_stone"))
    if rng.random() < 0.5 and lay.w >= 12 and lay.d >= 12:
        u, v = lay.u1 - 5, lay.v1 - 5
        for du in (0, 2):
            for dv in (0, 2):
                frame.set(u + du, T + 1, v + dv, B.fence("spruce"))
        frame.fill(u, T + 2, v, u + 2, T + 4, v + 2, B.SPRUCE_PLANKS)
        frame.fill(u, T + 5, v, u + 2, T + 5, v + 2, B.slab("spruce"))


def surroundings(frame: Frame, lay: Layout, rng: random.Random) -> None:
    """Участок вокруг: плитка, парковка сзади, деревья в кадках у входа."""
    W, D = frame.W, frame.D
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.SIDEWALK)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 6, D - 1, B.AIR)
    if D - 1 - lay.v1 >= 6:
        for u in range(1, W - 1, 3):
            frame.fill(u, CITY_Y, lay.v1 + 2, u, CITY_Y, D - 2, B.ROAD_LINE_WHITE)
    if lay.v0 >= 1:
        for u in (lay.u0 - 1, lay.u1 + 1):
            if 0 <= u < W:
                frame.set(u, CITY_Y + 1, lay.v0, "minecraft:potted_azalea_bush")


# ---------------------------------------------------------------------------
#  Начинка по типу здания
# ---------------------------------------------------------------------------

def counter(frame: Frame, lay: Layout, material: str = B.QUARTZ_SMOOTH) -> tuple[int, int]:
    """
    Прилавок поперёк зала: за ним стоит продавец, по краям — проходы в
    торговый зал. Если сзади лестница, прилавок начинается правее неё.
    Возвращает крайние клетки прилавка.
    """
    v = lay.counter_v
    a, b = lay.u0 + 3, lay.u1 - 3
    if b - a < 2:
        a, b = lay.u0 + 2, lay.u1 - 2
    if has_stairs(lay) and stair_keep_out(lay)[1] <= v + 2:
        a = max(a, stair_keep_out(lay)[2] + 1)
    frame.fill(a, CITY_Y + 1, v, b, CITY_Y + 1, v, material)
    # Проход за прилавок сбоку.
    frame.set(b, CITY_Y + 1, v, B.AIR)
    return a, b - 1


def ground_floor(frame: Frame, lay: Layout, rng: random.Random, keep: list) -> Floor:
    """Первый этаж как Floor: заняты проход от двери, прилавок и место продавца."""
    fl = Floor(frame, lay.u0 + 1, lay.v0 + 1, lay.u1 - 1, lay.v1 - 1, CITY_Y, rng, keep)
    cv = lay.counter_v
    fl.take(lay.door_u - 1, lay.v0 + 1, lay.door_u + 2, cv - 1)
    fl.take(lay.u0 + 1, cv - 1, lay.u1 - 1, cv + 1)
    return fl


def upper_floor(frame: Frame, lay: Layout, k: int, rng: random.Random) -> Floor:
    keep = [stair_keep_out(lay)] if has_stairs(lay) else []
    return Floor(frame, lay.u0 + 1, lay.v0 + 1, lay.u1 - 1, lay.v1 - 1,
                 CITY_Y + STOREY * k, rng, keep)


# ---------------------------------------------------------------------------
#  Первый этаж по профилю
# ---------------------------------------------------------------------------

def furnish_ground(frame: Frame, lay: Layout, rng: random.Random, profile: str) -> None:
    """
    Зал первого этажа: витрина до прилавка и торговый зал за ним.
    profile — роль продавца лавки или тип здания (bank, police, …).
    """
    keep = [stair_keep_out(lay)] if has_stairs(lay) else []
    fl = ground_floor(frame, lay, rng, keep)
    cv = lay.counter_v
    front = (fl.u0, fl.v0, fl.u1, cv - 2)
    back = (fl.u0, cv + 2, fl.u1, fl.v1)
    y = fl.y

    if profile in S.SHOP_PROFILES:
        S.front_display(fl, *front, profile) if profile != "cook" else \
            S.cafe_tables(fl, *front)
        if profile == "cook":
            S.kitchen_line(fl, fl.u0 + 1, fl.u1 - 1, fl.v1, "front")
            if back[3] - back[1] >= 3:
                S.cafe_tables(fl, back[0], back[1], back[2], back[3] - 2)
        else:
            if back[3] - back[1] >= 4:
                S.aisles(fl, back[0] + 1, back[1] + 1, back[2] - 1, back[3] - 2, profile)
            if back[3] >= back[1]:
                S.back_wall(fl, back[0], back[2], back[3], profile)
            if profile == "trader_clothes":
                S.fitting_rooms(fl, fl.u1 - 5, fl.v1, 2)
        I.plants_in_corners(fl)
        return

    if profile == "diner":
        S.cafe_tables(fl, *front)
        if back[3] >= back[1]:
            S.kitchen_line(fl, fl.u0 + 1, fl.u1 - 1, fl.v1, "front")
            for u in range(fl.u0 + 2, fl.u1 - 1, 3):
                if fl.free(u, fl.v1 - 2, u, fl.v1 - 2) and fl.v1 - 2 > cv + 1:
                    F.put(frame, u, y, fl.v1 - 2, "mcwfurnitures:spruce_counter", "back")
                    F.put(frame, u, y + 1, fl.v1 - 2, "farmersdelight:cutting_board", "back")
                    fl.take(u, fl.v1 - 2, u, fl.v1 - 2)
        _counter_top(frame, lay, rng, ("minecraft:cake", "farmersdelight:apple_pie"))
        I.plants_in_corners(fl)
        return

    if profile == "bank":
        _waiting(fl, *front, "front")
        # Хранилище у задней стены и кабинеты.
        frame.fill(lay.u1 - 5, y, lay.v1 - 3, lay.u1 - 2, y + 2, lay.v1 - 1, B.IRON_BLOCK)
        frame.set(lay.u1 - 4, y, lay.v1 - 3, "minecraft:gold_block")
        fl.take(lay.u1 - 6, lay.v1 - 4, lay.u1 - 1, lay.v1 - 1)
        I.desk_pods(fl, back[0] + 1, back[1], back[2] - 7, back[3] - 1)
    elif profile == "police":
        _waiting(fl, *front, "front")
        # Камера из решёток в дальнем углу.
        frame.fill(lay.u1 - 5, y, lay.v1 - 4, lay.u1 - 1, y + 2, lay.v1 - 4, B.IRON_BARS)
        frame.fill(lay.u1 - 5, y, lay.v1 - 4, lay.u1 - 5, y + 2, lay.v1 - 1, B.IRON_BARS)
        F.bed(frame, lay.u1 - 1, y, lay.v1 - 2, "back", "gray")
        fl.take(lay.u1 - 6, lay.v1 - 5, lay.u1 - 1, lay.v1 - 1)
        I.desk_pods(fl, back[0] + 1, back[1], back[2] - 7, back[3] - 1)
    elif profile == "hospital":
        _waiting(fl, *front, "front")
        sub = I.Floor(frame, back[0], back[1], back[2], back[3], CITY_Y, rng)
        sub.busy = fl.busy
        I.ward_floor(sub, 0)
    elif profile == "city_hall":
        _waiting(fl, *front, "front")
        for u in range(fl.u0, fl.u1 + 1, 4):
            if fl.free(u, fl.v0, u, fl.v0):
                F.put(frame, u, y + 1, lay.v0 + 1, "supplementaries:notice_board", "back")
        I.desk_pods(fl, back[0] + 1, back[1], back[2] - 1, back[3] - 1, computers=True)
    elif profile == "club":
        colors = ("magenta", "purple", "blue", "cyan", "pink")
        for u in range(fl.u0 + 2, fl.u1 - 1):
            for v in range(fl.v0 + 1, cv - 2):
                if fl.free(u, v, u, v):
                    frame.set(u, CITY_Y, v, B.concrete(colors[(u + v) % len(colors)]))
        frame.set(fl.u0, y, fl.v1, B.JUKEBOX)
        fl.take(fl.u0, fl.v1, fl.u0, fl.v1)
        for v in range(fl.v0, cv - 2, 4):
            if fl.free(fl.u0, v, fl.u0, v + 2):
                F.sofa(frame, fl.u0, y, v, 3, "back", "right", rng.choice(("black", "purple")))
                fl.take(fl.u0, v, fl.u0, v + 2)
        for u in range(fl.u0 + 1, fl.u1, 2):
            F.wall_shelf(frame, u, y + 1, fl.v1, "front",
                         rng.choice(("minecraft:honey_bottle", "minecraft:potion",
                                     "minecraft:experience_bottle")))
        if back[3] - back[1] >= 3:
            I.lounge(fl, back[0] + 6, back[1], back[0] + 10, back[1] + 3, "black")
    elif profile in ("office", "tower"):
        I.lounge(fl, fl.u0, fl.v0, fl.u0 + 4, fl.v0 + 3, rng.choice(F.SOFA_COLORS))
        I.lounge(fl, fl.u1 - 4, fl.v0, fl.u1, fl.v0 + 3, rng.choice(F.SOFA_COLORS))
        if back[3] - back[1] >= 4:
            I.lounge(fl, back[0] + 6, back[1] + 1, back[0] + 10, back[1] + 4, "gray")
    elif profile == "fire_station":
        for u in range(fl.u0, fl.u0 + 6):
            if fl.free(u, fl.v1, u, fl.v1):
                F.wardrobe(frame, u, y, fl.v1, "front", "spruce")
                fl.take(u, fl.v1, u, fl.v1)
    I.plants_in_corners(fl)


def _waiting(fl: Floor, u0: int, v0: int, u1: int, v1: int, look: str) -> None:
    """Зал ожидания: по два коротких ряда кресел у каждой стены, между ними растения."""
    for side in (0, 1):
        a = u0 + 1 if side == 0 else max(u0 + 1, u1 - 6)
        b = min(a + 5, u1 - 1)
        for v in (v1, v1 - 2):
            if v < v0:
                continue
            for u in range(a, b + 1):
                if fl.free(u, v, u, v):
                    F.chair(fl.frame, u, fl.y, v, "back", "another_furniture:oak_chair")
                    fl.take(u, v, u, v)
        if fl.free(b + 1, v1, b + 1, v1):
            F.plant(fl.frame, b + 1, fl.y, v1, fl.rng)
            fl.take(b + 1, v1, b + 1, v1)


def _counter_top(frame: Frame, lay: Layout, rng: random.Random, items: tuple) -> None:
    """Выпечка на прилавке (кроме клеток у продавца: там встанет касса)."""
    a, b = lay.u0 + 3, lay.u1 - 4
    for u in range(a, b + 1, 3):
        if abs(u - lay.door_u) <= 2:
            continue
        F.holder(frame, u, CITY_Y + 2, lay.counter_v, "supplementaries:pedestal",
                 "supplementaries:pedestal", [(rng.choice(items), 1)])


def furnish_upper(frame: Frame, lay: Layout, rng: random.Random, plans: list[str]) -> None:
    """Этажи выше первого: каждый по своей планировке из interiors.FLOOR_PLANS."""
    stair_u1 = stair_keep_out(lay)[2] - 1 if has_stairs(lay) else lay.u0
    for k in range(1, lay.floors):
        plan = plans[min(k - 1, len(plans) - 1)] if plans else "office"
        fl = upper_floor(frame, lay, k, rng)
        I.FLOOR_PLANS[plan](fl, stair_u1)


def lights(frame: Frame, lay: Layout) -> None:
    """Свет на каждом этаже: видимые светильники и невидимая сетка между ними."""
    for k in range(lay.floors):
        fy = CITY_Y + STOREY * k
        for u in range(lay.u0 + 3, lay.u1 - 1, 6):
            for v in range(lay.v0 + 3, lay.v1 - 1, 6):
                x, z = frame.world(u, v)
                if frame.canvas.get(x, fy + 3, z) in (0, frame.canvas.registry.id_of(B.AIR)) \
                        and frame.canvas.get(x, fy + 2, z) in \
                        (0, frame.canvas.registry.id_of(B.AIR)):
                    F.ceiling_lamp(frame, u, fy + 3, v)
        frame.light_grid(lay.u0, lay.v0, lay.u1, lay.v1, fy + 3, 4)


def build(canvas, lot: Lot, floors: int = 1, facade_index: int | None = None,
          inside: str = "counter", label: str | None = None,
          subtitle: list[str] | None = None,
          facade: tuple[str, str, str, str] | None = None,
          exact_floors: int | None = None, profile: str | None = None,
          plans: list[str] | None = None) -> Layout:
    rng = _rng(lot)
    lay = layout(lot, floors, facade_index, facade, exact_floors)
    frame = frame_of(canvas, lay)
    profile = profile or lot.shop_role or S.KIND_PROFILE.get(lot.kind, lot.kind)
    awning = S.AWNING.get(profile) if inside != "none" or profile == "pickup" else None
    surroundings(frame, lay, rng)
    body(frame, lay, rng, canopy=awning is None)
    stairs(frame, lay)
    if inside in ("counter", "tables"):
        counter(frame, lay)
    furnish_ground(frame, lay, rng, "diner" if inside == "tables" else profile)
    furnish_upper(frame, lay, rng, plans or ["office"])
    lights(frame, lay)
    rooftop(frame, lay, rng)
    if awning:
        F.awning(frame, lay.u0 + 1, lay.u1 - 1, CITY_Y + 4, lay.v0, "front", awning)
        S.street_stall(frame, lay, rng, profile)
    sign(frame, lay, lot.label if label is None else label, subtitle)
    return lay
