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
from .frame import Frame
from .homes import window_cells
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

ROOF_FINISH = [B.CONCRETE_GRAY, B.GRAVEL, B.CONCRETE_LIGHT, "minecraft:smooth_stone",
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
         floor_block: str = B.QUARTZ_SMOOTH) -> None:
    """Коробка: перекрытия, стены, окна, витрина, вход, лестницы и свет."""
    wall, accent, glass, cap = lay.facade
    u0, v0, u1, v1 = lay.u0, lay.v0, lay.u1, lay.v1
    frame.fill(u0, CITY_Y - 2, v0, u1, CITY_Y - 1, v1, B.CONCRETE_GRAY)
    frame.fill(u0, CITY_Y + 1, v0, u1, lay.top + 2, v1, B.AIR)

    for k in range(lay.floors):
        fy = CITY_Y + STOREY * k
        frame.fill(u0, fy, v0, u1, fy, v1, floor_block if k == 0 else B.CONCRETE_LIGHT)
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
        if k < lay.floors - 1 and lay.d >= 9:
            frame.staircase(u0 + 1, v1 - 5, fy, "back", "stone_brick", rail="right")

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
    for u in range(u0 + 1, u1):
        frame.set(u, CITY_Y + 4, v0 - 1, B.slab(cap, top=True))


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

def counter(frame: Frame, lay: Layout, material: str = B.QUARTZ_SMOOTH) -> None:
    """Прилавок поперёк зала, за ним место продавца и стеллажи у стены."""
    v = lay.counter_v
    a, b = lay.u0 + 3, lay.u1 - 3
    if b - a < 2:
        a, b = lay.u0 + 2, lay.u1 - 2
    frame.fill(a, CITY_Y + 1, v, b, CITY_Y + 1, v, material)
    # Проход за прилавок сбоку.
    frame.set(b, CITY_Y + 1, v, B.AIR)
    for u in range(a, b):
        if v + 2 <= lay.v1 - 1 and (u - a) % 2 == 0:
            frame.set(u, CITY_Y + 1, v + 2 if v + 2 < lay.v1 else lay.v1 - 1, B.BARREL)


def tables(frame: Frame, lay: Layout) -> None:
    """Столики со стульями в зале перед прилавком."""
    for u in range(lay.u0 + 3, lay.u1 - 2, 4):
        for v in range(lay.v0 + 2, lay.counter_v - 1, 3):
            if abs(u - lay.door_u) <= 1:
                continue
            frame.set(u, CITY_Y + 1, v, B.fence("spruce"))
            frame.set(u, CITY_Y + 2, v, "minecraft:oak_pressure_plate[powered=false]")
            frame.set(u - 1, CITY_Y + 1, v, frame.stairs("spruce", "left"))
            frame.set(u + 1, CITY_Y + 1, v, frame.stairs("spruce", "right"))


def desks(frame: Frame, lay: Layout, floor: int, rng: random.Random) -> None:
    """Офисный этаж: ряды столов с креслами."""
    fy = CITY_Y + STOREY * floor
    for u in range(lay.u0 + 4, lay.u1 - 2, 4):
        for v in range(lay.v0 + 2, lay.v1 - 1, 3):
            frame.set(u, fy + 1, v, B.slab("spruce", top=True))
            frame.set(u + 1, fy + 1, v, B.slab("spruce", top=True))
            frame.set(u, fy + 1, v + 1, frame.stairs("dark_oak", "back"))
            if rng.random() < 0.4:
                frame.set(u + 1, fy + 2, v, B.FLOWER_POT)


def build(canvas, lot: Lot, floors: int = 1, facade_index: int | None = None,
          inside: str = "counter", label: str | None = None,
          subtitle: list[str] | None = None,
          facade: tuple[str, str, str, str] | None = None,
          exact_floors: int | None = None) -> Layout:
    rng = _rng(lot)
    lay = layout(lot, floors, facade_index, facade, exact_floors)
    frame = frame_of(canvas, lay)
    surroundings(frame, lay, rng)
    body(frame, lay, rng)
    if inside in ("counter", "tables"):
        counter(frame, lay)
    if inside == "tables":
        tables(frame, lay)
    for k in range(1, lay.floors):
        desks(frame, lay, k, rng)
    for k in range(lay.floors):
        frame.light_grid(lay.u0, lay.v0, lay.u1, lay.v1, CITY_Y + STOREY * k + 3, 4)
    rooftop(frame, lay, rng)
    sign(frame, lay, lot.label if label is None else label, subtitle)
    return lay
