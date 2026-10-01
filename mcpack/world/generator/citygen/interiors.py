"""
Планировки этажей: офис, переговорные, кухня, кабинет директора,
больничные палаты, склад, VIP-зал, общежитие пожарных.

Раньше все этажи выше первого заполнялись одним и тем же рядом столов
из плит со «стульями» из ступеней. Теперь этаж нарезается на зоны, и в
каждой своя мебель из модов интерьера (см. furniture.py):

* open space — островки по четыре стола с мониторами и креслами;
* у задней стены — переговорная за стеклом, кухня и кабинет руководителя;
* в углах — зона отдыха с диваном, кадки с растениями, кулер и шкафы.

Floor следит, какие клетки уже заняты: мебель не встаёт на лестницу,
в проход к ней и друг на друга.
"""
from __future__ import annotations

import random

from . import blocks as B
from . import furniture as F
from .frame import Frame


class Floor:
    """Свободное место на одном этаже: внутренний прямоугольник без стен."""

    def __init__(self, frame: Frame, u0: int, v0: int, u1: int, v1: int, fy: int,
                 rng: random.Random, keep_out: list[tuple[int, int, int, int]] = ()):
        self.frame = frame
        self.u0, self.v0, self.u1, self.v1 = u0, v0, u1, v1
        self.fy = fy
        self.y = fy + 1
        self.rng = rng
        self.busy: set[tuple[int, int]] = set()
        for rect in keep_out:
            self.take(*rect)

    def inside(self, u: int, v: int) -> bool:
        return self.u0 <= u <= self.u1 and self.v0 <= v <= self.v1

    def free(self, u0: int, v0: int, u1: int, v1: int) -> bool:
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for v in range(min(v0, v1), max(v0, v1) + 1):
                if not self.inside(u, v) or (u, v) in self.busy:
                    return False
        return True

    def take(self, u0: int, v0: int, u1: int, v1: int) -> None:
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for v in range(min(v0, v1), max(v0, v1) + 1):
                self.busy.add((u, v))

    def corners(self) -> list[tuple[int, int]]:
        return [(self.u0, self.v0), (self.u1, self.v0), (self.u0, self.v1), (self.u1, self.v1)]


# ---------------------------------------------------------------------------
#  Мелкие зоны
# ---------------------------------------------------------------------------

def plants_in_corners(fl: Floor) -> None:
    for u, v in fl.corners():
        if fl.free(u, v, u, v):
            F.plant(fl.frame, u, fl.y, v, fl.rng)
            fl.take(u, v, u, v)


def lounge(fl: Floor, u0: int, v0: int, u1: int, v1: int, color: str) -> bool:
    """Зона отдыха: диван у стены, журнальный столик, кресла, ковёр."""
    if u1 - u0 < 3 or v1 - v0 < 2 or not fl.free(u0, v0, u1, v1):
        return False
    f, y = fl.frame, fl.y
    F.rug(f, u0, y, v0, u1, v1 - 1, fl.rng.choice(("gray", "light_gray", "white", "brown")))
    length = min(4, u1 - u0 + 1)
    F.sofa(f, u0, y, v1, length, "right", "front", color)
    F.coffee_table(f, u0 + length // 2, y, v1 - 2)
    if u1 - u0 >= 4:
        F.chair(f, u1, y, v1 - 2, "left", "another_furniture:dark_oak_chair")
    F.plant(f, u1, y, v1, fl.rng)
    fl.take(u0, v0, u1, v1)
    return True


def kitchenette(fl: Floor, u0: int, v: int, u1: int, face: str, table_v: int | None) -> bool:
    """Кухня-уголок: гарнитур у стены, холодильник, стол со стульями."""
    if u1 - u0 < 3 or not fl.free(u0, v, u1, v):
        return False
    f, y = fl.frame, fl.y
    F.kitchen(f, u0 + 1, y, v, u1, face, fl.rng)
    fu, fv = _ahead(f, u0, v, face)
    if fl.free(fu, fv, fu, fv):
        F.fridge(f, u0, y, v, face)
        fl.take(fu, fv, fu, fv)
    else:
        F.put(f, u0, y, v, "mcwfurnitures:oak_counter", face)
    fl.take(u0, v, u1, v)
    if table_v is not None and u1 - u0 >= 4:
        a, b = u0 + 1, min(u0 + 3, u1 - 1)
        rows = (table_v - 1, table_v + 1)
        if fl.free(a, min(rows), b, max(rows)):
            F.dining(f, a, y, table_v, b, table_v, fl.rng, "mcwfurnitures:oak_table",
                     "another_furniture:birch_chair")
            fl.take(a, min(rows), b, max(rows))
    return True


def _ahead(frame: Frame, u: int, v: int, side: str) -> tuple[int, int]:
    from .frame import LOCAL_VEC
    du, dv = LOCAL_VEC[side]
    return u + du, v + dv


def meeting_room(fl: Floor, u0: int, v0: int, u1: int, v1: int, door_side: str = "front") -> bool:
    """
    Переговорная за стеклянной перегородкой: длинный стеклянный стол,
    кресла с двух сторон, телевизор на стене.
    """
    if u1 - u0 < 5 or v1 - v0 < 4 or not fl.free(u0, v0, u1, v1):
        return False
    f, y = fl.frame, fl.y
    wall_v = v0 if door_side == "front" else v1
    pane = B.GLASS_PANE
    f.fill(u0, y, wall_v, u1, y + 2, wall_v, pane)
    door_u = (u0 + u1) // 2
    f.door(door_u, y, wall_v, door_side, "birch")
    f.set(door_u, y + 2, wall_v, pane)
    f.fill(u0, y, v0, u0, y + 2, v1, pane)
    f.fill(u1, y, v0, u1, y + 2, v1, pane)
    inner0, inner1 = (v0 + 1, v1) if door_side == "front" else (v0, v1 - 1)
    tv0, tv1 = inner0 + 1, inner1 - 1
    mid = (tv0 + tv1) // 2
    if inner1 - inner0 >= 3:
        F.table(f, u0 + 2, y, mid, u1 - 2, mid, "mcwfurnitures:oak_glass_table")
        behind = inner0 if door_side == "front" else inner1
        for u in range(u0 + 2, u1 - 1):
            for cv, look in ((mid - 1, "back"), (mid + 1, "front")):
                if u == door_u and cv == behind:
                    continue
                F.chair(f, u, y, cv, look, "mcwfurnitures:dark_oak_modern_chair")
    far = inner1 if door_side == "front" else inner0
    F.plant(f, u0 + 1, y, far, fl.rng)
    F.plant(f, u1 - 1, y, far, fl.rng)
    fl.take(u0, v0, u1, v1)
    fu, fv = _ahead(f, door_u, wall_v, door_side)
    fl.take(fu, fv, fu, fv)
    return True


def director(fl: Floor, u0: int, v0: int, u1: int, v1: int, door_side: str = "front") -> bool:
    """Кабинет руководителя: большой стол, кресло, шкафы с книгами, диван."""
    if u1 - u0 < 4 or v1 - v0 < 4 or not fl.free(u0, v0, u1, v1):
        return False
    f, y = fl.frame, fl.y
    wall_v = v0 if door_side == "front" else v1
    wall = B.QUARTZ_SMOOTH
    f.fill(u0, y, wall_v, u1, y + 2, wall_v, wall)
    f.fill(u0, y, v0, u0, y + 2, v1, wall)
    f.fill(u1, y, v0, u1, y + 2, v1, wall)
    door_u = u0 + 1
    f.door(door_u, y, wall_v, door_side, "dark_oak")
    f.set(door_u, y + 2, wall_v, wall)
    inner0, inner1 = (v0 + 1, v1) if door_side == "front" else (v0, v1 - 1)
    far, sit = (inner1, "back") if door_side == "front" else (inner0, "front")
    cu = (u0 + u1) // 2 + 1
    desk_v = far - 1 if door_side == "front" else far + 1
    F.desk(f, cu, y, desk_v, sit, fl.rng, computer=True,
           chair_kind="handcrafted:dark_oak_chair")
    F.put(f, cu - 1, y, desk_v, "handcrafted:dark_oak_desk", sit, color="none")
    F.table_lamp(f, cu - 1, y + 1, desk_v)
    for u in range(u0 + 1, u1):
        if u not in (cu, cu - 1) and abs(u - door_u) > 0:
            F.bookshelf(f, u, y, far, "front" if door_side == "front" else "back", "dark_oak")
    near = inner0 + 1 if door_side == "front" else inner1 - 1
    if u1 - u0 >= 6 and near != desk_v:
        F.sofa(f, u1 - 1, y, near, 1, "front", "left", "black")
    fl.take(u0, v0, u1, v1)
    fu, fv = _ahead(f, door_u, wall_v, door_side)
    fl.take(fu, fv, fu, fv)
    return True


def desk_pods(fl: Floor, u0: int, v0: int, u1: int, v1: int, computers: bool = True,
              limit: int = 8) -> int:
    """
    Открытое пространство: островки 2×2 стола лицом друг к другу, кресла
    снаружи, проходы по три блока; между островками — растения и кулеры.
    Возвращает число рабочих мест.
    """
    f, y, rng = fl.frame, fl.y, fl.rng
    count = 0
    pods = 0
    for v in range(v0 + 1, v1 - 1, 7):
        for u in range(u0, u1 - 1, 5):
            if pods >= limit:
                return count
            if v + 2 > v1 or u + 1 > u1 or not fl.free(u, v - 1, u + 1, v + 2):
                continue
            for du in (0, 1):
                F.desk(f, u + du, y, v, "front", rng, computer=computers)
                F.desk(f, u + du, y, v + 1, "back", rng, computer=computers)
            fl.take(u, v - 1, u + 1, v + 2)
            count += 4
            pods += 1
            # Рядом с островком — растение или кулер с водой.
            pu = u + 3
            if fl.free(pu, v, pu, v) and pu <= u1:
                if rng.random() < 0.6:
                    F.plant(f, pu, y, v, rng)
                else:
                    f.set(pu, y, v, "minecraft:quartz_pillar[axis=y]")
                    f.set(pu, y + 1, v, "minecraft:light_blue_stained_glass")
                fl.take(pu, v, pu, v)
    return count


def breakout_tables(fl: Floor, u0: int, v0: int, u1: int, v1: int, limit: int = 4) -> None:
    """Столики для разговоров на свободном месте: стол, два стула, растение рядом."""
    f, y, rng = fl.frame, fl.y, fl.rng
    placed = 0
    for v in range(v0 + 1, v1, 4):
        for u in range(u0 + 1, u1, 5):
            if placed >= limit:
                return
            if not fl.free(u - 1, v - 1, u + 2, v + 1):
                continue
            F.put(f, u, y, v, "another_furniture:oak_table", "front")
            F.chair(f, u - 1, y, v, "right", "another_furniture:birch_chair")
            F.chair(f, u + 1, y, v, "left", "another_furniture:birch_chair")
            if rng.random() < 0.5:
                F.plant(f, u + 2, y, v + 1, rng)
            fl.take(u - 1, v - 1, u + 2, v + 1)
            placed += 1


def storage_racks(fl: Floor, u0: int, v0: int, u1: int, v1: int) -> None:
    """Склад: стеллажи из бочек и ящиков рядами, проходы между ними."""
    f, y, rng = fl.frame, fl.y, fl.rng
    for v in range(v0 + 1, v1, 3):
        for u in range(u0 + 1, u1):
            if not fl.free(u, v, u, v):
                continue
            pick = rng.random()
            if pick < 0.35:
                f.set(u, y, v, B.BARREL)
                f.set(u, y + 1, v, B.BARREL if rng.random() < 0.6 else "minecraft:brown_wool")
            elif pick < 0.7:
                f.set(u, y, v, rng.choice(("farmersdelight:carrot_crate",
                                            "farmersdelight:potato_crate",
                                            "farmersdelight:beetroot_crate",
                                            "farmersdelight:cabbage_crate",
                                            "farmersdelight:tomato_crate",
                                            "farmersdelight:onion_crate")))
            else:
                f.set(u, y, v, "supplementaries:sack")
            fl.take(u, v, u, v)


# ---------------------------------------------------------------------------
#  Этажи целиком
# ---------------------------------------------------------------------------

def office_floor(fl: Floor, stair_u1: int, theme: str = "office") -> None:
    """
    Офисный этаж. stair_u1 — правая граница зоны лестницы у задней стены:
    правее неё вдоль задней стены идут переговорная, кухня и кабинет.
    """
    rng = fl.rng
    W = fl.u1 - fl.u0 + 1
    D = fl.v1 - fl.v0 + 1
    back_band = D >= 13
    if back_band:
        b0 = fl.v1 - 4
        u = stair_u1 + 2
        rooms = ["meeting", "kitchen", "director"]
        rng.shuffle(rooms)
        if theme == "police":
            rooms = ["meeting", "kitchen", "director"]
        widths = {"meeting": rng.randint(8, 10), "kitchen": rng.randint(6, 7),
                  "director": rng.randint(7, 8)}
        for room in rooms * 2:
            w = widths[room]
            if u + w - 1 > fl.u1:
                w = fl.u1 - u + 1
            if w < 5:
                break
            a, b = u, u + w - 1
            if room == "meeting":
                meeting_room(fl, a, b0, b, fl.v1)
            elif room == "kitchen":
                kitchenette(fl, a, fl.v1, b, "front", None)
                if fl.free(a + 1, fl.v1 - 3, b - 1, fl.v1 - 1) and b - a >= 4:
                    F.dining(fl.frame, a + 2, fl.y, fl.v1 - 2, b - 2, fl.v1 - 2, rng,
                             "mcwfurnitures:oak_table", "another_furniture:birch_chair")
                    fl.take(a + 1, fl.v1 - 3, b - 1, fl.v1 - 1)
                fl.take(a, b0, b, fl.v1)
            else:
                director(fl, a, b0, b, fl.v1)
            u = b + 2
        pods_v1 = b0 - 2
    else:
        pods_v1 = fl.v1 - 1
        # Кухня-уголок у задней стены правее лестницы.
        kitchenette(fl, stair_u1 + 2, fl.v1, min(stair_u1 + 7, fl.u1), "front", None)

    # Зона отдыха в переднем левом углу у окна, если этаж широкий.
    if W >= 20 and lounge(fl, fl.u0, fl.v0, fl.u0 + 4, fl.v0 + 3,
                          rng.choice(F.SOFA_COLORS)):
        pods_u0 = fl.u0 + 6
    else:
        pods_u0 = fl.u0 + 1
    desk_pods(fl, pods_u0, fl.v0 + 1, fl.u1 - 1, pods_v1, computers=theme != "hall")
    breakout_tables(fl, fl.u0, fl.v0, fl.u1, pods_v1)
    # Шкафы с документами вдоль правой стены, где свободно.
    for v in range(fl.v0 + 1, pods_v1, 2):
        if fl.free(fl.u1, v, fl.u1, v) and rng.random() < 0.5:
            F.put(fl.frame, fl.u1, fl.y, v, "mcwfurnitures:oak_double_drawer", "left",
                  connection="single")
            fl.take(fl.u1, v, fl.u1, v)
    plants_in_corners(fl)


def hall_floor(fl: Floor, stair_u1: int) -> None:
    """Зал заседаний: ряды стульев лицом к трибуне у правой стены."""
    f, y, rng = fl.frame, fl.y, fl.rng
    podium_u = fl.u1 - 1
    mid = (fl.v0 + fl.v1) // 2
    f.fill(podium_u, fl.fy, fl.v0 + 2, fl.u1, fl.fy, fl.v1 - 2, B.DARK_OAK_PLANKS)
    F.put(f, podium_u, y, mid, "handcrafted:dark_oak_desk", "left", color="red")
    F.chair(f, fl.u1, y, mid, "left", "handcrafted:dark_oak_chair")
    fl.take(podium_u - 1, fl.v0, fl.u1, fl.v1)
    for u in range(max(fl.u0 + 1, stair_u1 + 2), podium_u - 2, 2):
        for v in range(fl.v0 + 1, fl.v1):
            if v == mid or not fl.free(u, v, u, v):
                continue
            F.chair(f, u, y, v, "right", "another_furniture:dark_oak_chair")
            fl.take(u, v, u, v)
    plants_in_corners(fl)
    _ = rng


def ward_floor(fl: Floor, stair_u1: int) -> None:
    """Больничный этаж: койки с тумбочками вдоль стен, пост медсестры."""
    f, y, rng = fl.frame, fl.y, fl.rng
    for v in range(fl.v0 + 1, fl.v1 - 1, 3):
        for u, head in ((fl.u0, "left"), (fl.u1, "right")):
            foot = u + (2 if head == "left" else -2)
            cells = (min(u, foot), v, max(u, foot), v + 1)
            if not fl.free(*cells):
                continue
            bu = u + (1 if head == "left" else -1)
            F.bed(f, bu, y, v, head, "white")
            F.nightstand(f, u, y, v + 1, "right" if head == "left" else "left")
            fl.take(*cells)
    # Пост медсестры по центру.
    cu, cv = (fl.u0 + fl.u1) // 2, (fl.v0 + fl.v1) // 2
    if fl.free(cu - 2, cv - 1, cu + 2, cv + 1):
        for u in range(cu - 2, cu + 3):
            F.put(f, u, y, cv, "mcwfurnitures:birch_counter", "front")
        F.put(f, cu, y + 1, cv, "citylife:monitor", "back")
        F.chair(f, cu, y, cv + 1, "front", "mcwfurnitures:birch_modern_chair")
        fl.take(cu - 2, cv - 1, cu + 2, cv + 1)
    plants_in_corners(fl)
    _ = stair_u1, rng


def dorm_floor(fl: Floor, stair_u1: int) -> None:
    """Общежитие пожарной части: койки, шкафчики, кухня и диван у телевизора."""
    f, y = fl.frame, fl.y
    for u in range(fl.u0 + 1, fl.u1, 3):
        if fl.free(u, fl.v0, u + 1, fl.v0 + 2):
            F.bed(f, u, y, fl.v0 + 1, "front", "red")
            F.wardrobe(f, u + 1, y, fl.v0, "back")
            fl.take(u, fl.v0, u + 1, fl.v0 + 2)
    kitchenette(fl, stair_u1 + 2, fl.v1, min(stair_u1 + 7, fl.u1), "front", fl.v1 - 2)
    cu = (fl.u0 + fl.u1) // 2
    lounge(fl, cu, fl.v0 + 4, cu + 4, fl.v0 + 7, "red")
    plants_in_corners(fl)


def lounge_floor(fl: Floor, stair_u1: int) -> None:
    """VIP-зал клуба: барная стойка с табуретами, диваны по стенам, танцпол."""
    f, y, rng = fl.frame, fl.y, fl.rng
    bar_v = fl.v1 - 2
    a, b = max(fl.u0 + 1, stair_u1 + 2), fl.u1 - 1
    if b - a >= 4:
        for u in range(a, b + 1):
            F.put(f, u, y, bar_v, "mcwfurnitures:dark_oak_counter", "front")
            if (u - a) % 2 == 0:
                F.stool(f, u, y, bar_v - 1)
            if rng.random() < 0.3:
                f.set(u, y + 1, bar_v, "supplementaries:goblet")
        for u in range(a, b + 1, 2):
            F.wall_shelf(f, u, y + 1, fl.v1, "front",
                         rng.choice(("minecraft:honey_bottle", "minecraft:potion",
                                     "minecraft:experience_bottle", "minecraft:dragon_breath")))
        fl.take(a, bar_v - 1, b, fl.v1)
    colors = ("magenta", "purple", "blue", "cyan", "pink")
    cu, cv = (fl.u0 + fl.u1) // 2, (fl.v0 + bar_v) // 2
    for u in range(cu - 3, cu + 4):
        for v in range(cv - 2, cv + 3):
            if fl.free(u, v, u, v):
                f.set(u, fl.fy, v, B.concrete(colors[(u + v) % len(colors)]))
    fl.take(cu - 3, cv - 2, cu + 3, cv + 2)
    for u in range(fl.u0 + 1, fl.u1 - 3, 6):
        if fl.free(u, fl.v0, u + 3, fl.v0 + 1):
            F.sofa(f, u, y, fl.v0, 4, "right", "back", rng.choice(("black", "purple", "red")))
            F.coffee_table(f, u + 1, y, fl.v0 + 1)
            fl.take(u, fl.v0, u + 3, fl.v0 + 1)
    plants_in_corners(fl)


def storage_floor(fl: Floor, stair_u1: int) -> None:
    storage_racks(fl, fl.u0, fl.v0, fl.u1, fl.v1)
    _ = stair_u1


FLOOR_PLANS = {
    "office": office_floor,
    "hall": hall_floor,
    "ward": ward_floor,
    "dorm": dorm_floor,
    "lounge": lounge_floor,
    "storage": storage_floor,
}
