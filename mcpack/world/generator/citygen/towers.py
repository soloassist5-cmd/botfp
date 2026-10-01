"""
Небоскрёбы Сантос-Сити: башня Старка, «Меридиан» и «Сансет Плаза».

Прежняя «высотка» была коробкой-стилобатом с пустой стеклянной трубой
сверху: ни лестницы, ни мебели выше вестибюля. Здесь башня собирается
поэтажно:

* план этажа любой формы — прямоугольник со срезанными углами, круг,
  эллипс; с высотой башня может сужаться уступами;
* фасад — стекло с вертикальными импостами и светлой кромкой перекрытий;
* в центре ядро: П-образная лестница на все этажи и лифт — панель
  citylife:elevator на каждом этаже, по ней можно уехать на любой этаж;
* этажи обставлены: вестибюль с ресепшеном, офисы, переговорные,
  лаборатории, ресторан и пентхаус наверху.

У каждой башни своё лицо: у Старка — светящиеся буквы STARK на фасаде,
посадочная площадка-консоль у вершины и лопасть-«плавник» над крышей;
у «Меридиана» — круглый план со спиральной лентой голубого стекла и
шпилем; «Сансет Плаза» — две башни-близнеца с переходом-мостом.
"""
from __future__ import annotations

import math
import random
from dataclasses import dataclass, field

from . import blocks as B
from . import furniture as F
from . import interiors as I
from .frame import Frame
from .plan import CITY_Y, Lot

STOREY = 4


@dataclass
class Tower:
    """Одна башня на участке: где стоит, какой формы и сколько этажей."""
    cu: int                      # центр плана (координаты участка)
    cv: int
    a: float                     # полуоси плана вдоль u и v
    b: float
    floors: int
    shape: str = "chamfer"       # chamfer | ellipse | round
    chamfer: int = 3
    glass: str = B.GLASS_BLUE
    mullion: str = B.CONCRETE_WHITE
    edge: str = B.CONCRETE_WHITE
    core_wall: str = B.QUARTZ_SMOOTH
    # Уступы: с какого этажа план уменьшается и на сколько блоков.
    setbacks: list[tuple[int, int]] = field(default_factory=list)
    plans: list[str] = field(default_factory=list)   # планировки этажей 1..N-1
    name: str = ""

    def shrink(self, k: int) -> int:
        return sum(d for start, d in self.setbacks if k >= start)

    def inside(self, u: int, v: int, k: int) -> bool:
        s = self.shrink(k)
        a, b = self.a - s, self.b - s
        du, dv = abs(u - self.cu), abs(v - self.cv)
        if self.shape == "chamfer":
            if du > a or dv > b:
                return False
            return du + dv <= a + b - self.chamfer
        if self.shape == "round":
            return du * du + dv * dv <= a * a + a * 0.8
        return (du / (a + 0.5)) ** 2 + (dv / (b + 0.5)) ** 2 <= 1.0

    def cells(self, k: int) -> set[tuple[int, int]]:
        s = int(math.ceil(max(self.a, self.b))) + 1
        return {(u, v) for u in range(self.cu - s, self.cu + s + 1)
                for v in range(self.cv - s, self.cv + s + 1) if self.inside(u, v, k)}

    def back_v(self, u: int, k: int) -> int:
        """Самая дальняя от улицы клетка плана в столбце u."""
        for v in range(self.cv + int(self.b) + 2, self.cv - 1, -1):
            if self.inside(u, v, k):
                return v
        return self.cv

    def front_v(self, u: int, k: int) -> int:
        """Самая передняя клетка плана в столбце u (там фасад со стороны улицы)."""
        for v in range(self.cv - int(self.b) - 2, self.cv + 1):
            if self.inside(u, v, k):
                return v
        return self.cv

    # Ядро: лестница слева от центра, лифт справа, площадка спереди.
    def core(self) -> tuple[int, int, int, int]:
        return self.cu - 4, self.cv - 1, self.cu + 4, self.cv + 2

    def top(self) -> int:
        return CITY_Y + STOREY * self.floors


def _perimeter(cells: set[tuple[int, int]]) -> set[tuple[int, int]]:
    out = set()
    for u, v in cells:
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (u + du, v + dv) not in cells:
                out.add((u, v))
                break
    return out


def shell(frame: Frame, t: Tower, rng: random.Random, band=None) -> None:
    """Перекрытия, стеклянные стены, импосты, кромки и кровля всех этажей."""
    for k in range(t.floors + 1):
        fy = CITY_Y + STOREY * k
        cells = t.cells(min(k, t.floors - 1))
        edge = _perimeter(cells)
        floor_block = B.QUARTZ_SMOOTH if k == 0 else I_FLOORS[k % len(I_FLOORS)]
        for u, v in cells:
            frame.set(u, fy, v, t.edge if (u, v) in edge else
                      (floor_block if k < t.floors else B.CONCRETE_GRAY))
        if k == t.floors:
            # Кровля: парапет по краю.
            for u, v in edge:
                frame.set(u, fy + 1, v, t.edge)
            break
        # Если этажом выше план уже — над уступом терраса с ограждением.
        for u, v in cells:
            for y in range(fy + 1, fy + 4):
                frame.set(u, y, v, B.AIR)
        for u, v in edge:
            post = (u + v) % 4 == 0 or _corner(cells, u, v)
            glass = t.glass
            if band is not None:
                glass = band(u, v, k) or t.glass
            for y in range(fy + 1, fy + 4):
                frame.set(u, y, v, t.mullion if post else glass)
        if k + 1 < t.floors:
            terrace = cells - t.cells(k + 1)
            for u, v in terrace:
                # Кровля уступа и ограждение по её краю.
                frame.set(u, fy + 4, v, B.CONCRETE_LIGHT)
                if (u, v) in edge:
                    frame.set(u, fy + 5, v, B.IRON_BARS)


I_FLOORS = (B.CONCRETE_LIGHT, "minecraft:polished_andesite", "minecraft:smooth_stone",
            B.BIRCH_PLANKS)


def _corner(cells, u, v) -> bool:
    n = sum((u + du, v + dv) in cells for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    return n <= 2


def core(frame: Frame, t: Tower) -> None:
    """Ядро башни: лестница на все этажи и лифтовая шахта с панелями вызова."""
    cu0, cv0, cu1, cv1 = t.core()
    floors = [CITY_Y + STOREY * k for k in range(t.floors)]
    frame.stairwell(cu0 + 1, cv0, floors, "smooth_quartz", B.QUARTZ_SMOOTH, t.core_wall,
                    B.IRON_BARS)
    # Шахта лифта пробивает перекрытия насквозь — это один сплошной ствол.
    frame.fill(cu0 + 5, CITY_Y + 1, cv0, cu1, floors[-1] + 3, cv1, t.core_wall)
    for fy in floors:
        # Двери лифта — тёмные створки на передней грани шахты, рядом кнопка.
        frame.fill(cu0 + 6, fy + 1, cv0, cu0 + 7, fy + 2, cv0, "minecraft:polished_deepslate")
        elevator_panel(frame, cu0 + 8, fy + 2, cv0 - 1, "front")
        frame.light(cu0 + 6, fy + 3, cv0 - 1)


def elevator_panel(frame: Frame, u: int, y: int, v: int, out: str) -> None:
    """Кнопка вызова лифта на стене (стена — за ней, со стороны, обратной out)."""
    frame.set(u, y, v, f"citylife:elevator[facing={frame.dir(out)}]")


def keep_out(t: Tower, k: int) -> set[tuple[int, int]]:
    """Клетки этажа, куда мебель не ставим: стены, ядро, проход к лестнице и лифту."""
    cells = t.cells(k)
    edge = _perimeter(cells)
    s = int(math.ceil(max(t.a, t.b))) + 1
    busy = {(u, v) for u in range(t.cu - s, t.cu + s + 1)
            for v in range(t.cv - s, t.cv + s + 1) if (u, v) not in cells or (u, v) in edge}
    cu0, cv0, cu1, cv1 = t.core()
    for u in range(cu0 - 1, cu1 + 2):
        for v in range(cv0 - 3, cv1 + 1):
            busy.add((u, v))
    # Прямой проход от входа к ядру.
    for u in range(t.cu - 1, t.cu + 3):
        for v in range(t.cv - int(t.b) - 2, cv0):
            busy.add((u, v))
    return busy


def floor_of(frame: Frame, t: Tower, k: int, rng: random.Random) -> I.Floor:
    s = int(math.ceil(max(t.a, t.b))) + 1
    fl = I.Floor(frame, t.cu - s, t.cv - s, t.cu + s, t.cv + s, CITY_Y + STOREY * k, rng)
    fl.busy = keep_out(t, k)
    return fl


# ---------------------------------------------------------------------------
#  Планировки этажей башни
# ---------------------------------------------------------------------------

def lobby(fl: I.Floor, t: Tower, statue: str | None = None) -> None:
    """Вестибюль: стойка ресепшена перед ядром, диваны у окон, растения."""
    f, y, rng = fl.frame, fl.y, fl.rng
    cu0, cv0, cu1, cv1 = t.core()
    rv = cv0 - 5
    for u in range(t.cu - 4, t.cu + 5):
        if abs(u - t.cu) <= 1:
            continue
        F.put(f, u, y, rv, "mcwfurnitures:birch_counter", "front")
        fl.take(u, rv, u, rv + 1)
    F.put(f, t.cu - 3, y + 1, rv, "citylife:monitor", "front")
    F.put(f, t.cu + 3, y + 1, rv, "citylife:monitor", "front")
    for u in (t.cu - 3, t.cu + 3):
        F.chair(f, u, y, rv + 1, "front", "mcwfurnitures:birch_modern_chair")
    fl.take(t.cu - 4, rv - 1, t.cu + 4, rv - 1)
    for side in (-1, 1):
        u0 = t.cu + side * 7 - 2
        I.lounge(fl, u0, t.cv - 2, u0 + 4, t.cv + 1, rng.choice(("white", "gray", "black")))
        I.lounge(fl, u0, t.cv + 4, u0 + 4, t.cv + 7, rng.choice(("white", "gray", "black")))
    if statue == "suit":
        _suit(fl, t.cu + 7, t.cv - 7)
        _suit(fl, t.cu - 7, t.cv - 7)
    I.plants_in_corners(fl)
    _plants_ring(fl, t, 0)


def _suit(fl: I.Floor, u: int, v: int) -> None:
    """Статуя бронекостюма на постаменте: красный с золотом, светящийся реактор."""
    f, y = fl.frame, fl.y
    if not fl.free(u - 1, v - 1, u + 1, v + 1):
        return
    f.set(u, y, v, B.QUARTZ_SMOOTH)
    f.set(u, y + 1, v, B.CONCRETE_RED)                 # ноги
    f.set(u, y + 2, v, "minecraft:sea_lantern")        # реактор на груди
    f.set(u, y + 3, v, "minecraft:yellow_concrete")    # шлем
    for du in (-1, 1):
        f.set(u + du, y + 2, v, B.slab("red_nether_brick", top=True))
    fl.take(u - 1, v - 1, u + 1, v + 1)


def _plants_ring(fl: I.Floor, t: Tower, k: int) -> None:
    """Кадки вдоль окон через равные промежутки."""
    edge = _perimeter(t.cells(k))
    for u, v in sorted(edge):
        if (u * 7 + v * 3) % 11:
            continue
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cu, cv = u + du, v + dv
            if fl.free(cu, cv, cu, cv):
                F.plant(fl.frame, cu, fl.y, cv, fl.rng)
                fl.take(cu, cv, cu, cv)
                break


def office(fl: I.Floor, t: Tower, k: int) -> None:
    """Офис вокруг ядра: переговорные в углах, кухня у ядра, островки столов."""
    rng = fl.rng
    cu0, cv0, cu1, cv1 = t.core()
    # Кухня у задней стены ядра.
    if fl.free(cu0, cv1 + 2, cu1, cv1 + 2):
        I.kitchenette(fl, cu0, cv1 + 1, cu1, "back", None)
    corners = [(t.cu - int(t.a) + 2, t.cv + int(t.b) - 6), (t.cu + int(t.a) - 10, t.cv + int(t.b) - 6)]
    rng.shuffle(corners)
    I.meeting_room(fl, corners[0][0], corners[0][1], corners[0][0] + 8, corners[0][1] + 4, "front")
    I.director(fl, corners[1][0], corners[1][1], corners[1][0] + 7, corners[1][1] + 4, "front")
    s = int(math.ceil(max(t.a, t.b)))
    I.desk_pods(fl, t.cu - s, t.cv - s, t.cu + s, t.cv + s, limit=6)
    I.breakout_tables(fl, t.cu - s, t.cv - s, t.cu + s, t.cv + s, limit=3)
    _plants_ring(fl, t, k)


def lab(fl: I.Floor, t: Tower, k: int) -> None:
    """Лаборатория: длинные верстаки с приборами и мониторами, сейфы-шкафы."""
    f, y, rng = fl.frame, fl.y, fl.rng
    s = int(math.ceil(max(t.a, t.b)))
    for v in range(t.cv - s, t.cv + s, 4):
        for u in range(t.cu - s, t.cu + s - 4, 7):
            if not fl.free(u, v - 1, u + 4, v + 1):
                continue
            for i in range(5):
                F.put(f, u + i, y, v, "mcwfurnitures:dark_oak_counter", "back")
                top = rng.random()
                if top < 0.3:
                    F.put(f, u + i, y + 1, v, "citylife:monitor", "back")
                elif top < 0.45:
                    f.set(u + i, y + 1, v, "minecraft:brewing_stand[has_bottle_0=true,"
                                           "has_bottle_1=false,has_bottle_2=true]")
                elif top < 0.55:
                    f.set(u + i, y + 1, v, "minecraft:end_rod[facing=up]")
                elif top < 0.65:
                    # Глобус рисует block entity: без него на столе пусто.
                    f.block_entity(u + i, y + 1, v, F.state(f, "supplementaries:globe", "back"),
                                   "supplementaries:globe", {})
            for i in range(0, 5, 2):
                F.stool(f, u + i, y, v + 1)
            fl.take(u, v - 1, u + 4, v + 1)
    # Реактор в стеклянной колбе — экспонат этажа.
    for side in (-1, 1):
        u, v = t.cu + side * 8, t.cv - 7
        if fl.free(u - 1, v - 1, u + 1, v + 1):
            f.set(u, y, v, "minecraft:polished_deepslate")
            f.set(u, y + 1, v, "minecraft:beacon")
            f.set(u, y + 2, v, "minecraft:light_blue_stained_glass")
            fl.take(u - 1, v - 1, u + 1, v + 1)
    _plants_ring(fl, t, k)


def restaurant(fl: I.Floor, t: Tower, k: int) -> None:
    """Ресторан с панорамой: столики у окон, бар у ядра."""
    f, y, rng = fl.frame, fl.y, fl.rng
    cu0, cv0, cu1, cv1 = t.core()
    bar_v = cv1 + 3
    for u in range(cu0, cu1 + 1):
        if fl.free(u, bar_v, u, bar_v + 1):
            F.put(f, u, y, bar_v, "mcwfurnitures:dark_oak_counter", "back")
            if (u - cu0) % 2 == 0:
                F.stool(f, u, y, bar_v + 1)
            fl.take(u, bar_v, u, bar_v + 1)
    s = int(math.ceil(max(t.a, t.b)))
    for v in range(t.cv - s, t.cv + s, 3):
        for u in range(t.cu - s, t.cu + s, 4):
            if not fl.free(u - 1, v - 1, u + 1, v + 1):
                continue
            F.put(f, u, y, v, "another_furniture:dark_oak_table", "front")
            F.chair(f, u - 1, y, v, "right", "another_furniture:dark_oak_chair")
            F.chair(f, u + 1, y, v, "left", "another_furniture:dark_oak_chair")
            f.set(u, y + 1, v, rng.choice(("handcrafted:white_plate[facing=north]",
                                           "minecraft:candle[candles=2,lit=true,"
                                           "waterlogged=false]")))
            fl.take(u - 1, v - 1, u + 1, v + 1)
    _plants_ring(fl, t, k)


def penthouse(fl: I.Floor, t: Tower, k: int) -> None:
    """Пентхаус: гостиная с диванами, бар, рояль, спальня и кабинет."""
    f, y, rng = fl.frame, fl.y, fl.rng
    cu0, cv0, cu1, cv1 = t.core()
    I.lounge(fl, t.cu - 9, t.cv - 7, t.cu - 4, t.cv - 4, "white")
    I.lounge(fl, t.cu + 4, t.cv - 7, t.cu + 9, t.cv - 4, "black")
    # Рояль: три нотных блока под крышкой из чёрного бетона.
    pu, pv = t.cu - 8, t.cv + 1
    if fl.free(pu, pv, pu + 2, pv + 1):
        for i in range(3):
            f.set(pu + i, y, pv, B.NOTE_BLOCK)
        f.set(pu + 1, y + 1, pv, B.slab("blackstone"))
        F.stool(f, pu + 1, y, pv + 1, tall=False)
        fl.take(pu, pv, pu + 2, pv + 1)
    I.director(fl, t.cu + 3, t.cv + 4, t.cu + 10, t.cv + 8, "front")
    bu, bv = t.cu - 9, t.cv + 5
    if fl.free(bu, bv, bu + 3, bv + 2):
        F.bed(f, bu + 1, y, bv + 1, "back", "black")
        F.bed(f, bu + 2, y, bv + 1, "back", "black")
        F.nightstand(f, bu, y, bv + 2, "front")
        F.nightstand(f, bu + 3, y, bv + 2, "front")
        fl.take(bu, bv, bu + 3, bv + 2)
    restaurant(fl, t, k)


TOWER_PLANS = {"office": office, "lab": lab, "restaurant": restaurant, "penthouse": penthouse}


def furnish(frame: Frame, t: Tower, rng: random.Random, statue: str | None = None) -> None:
    lobby(floor_of(frame, t, 0, rng), t, statue)
    for k in range(1, t.floors):
        plan = t.plans[min(k - 1, len(t.plans) - 1)] if t.plans else "office"
        if k == t.floors - 1 and "penthouse" in t.plans:
            plan = "penthouse"
        TOWER_PLANS[plan](floor_of(frame, t, k, rng), t, k)


def lights(frame: Frame, t: Tower) -> None:
    for k in range(t.floors):
        fy = CITY_Y + STOREY * k
        cells = t.cells(k)
        air = frame.canvas.registry.id_of(B.AIR)
        for u, v in cells:
            if (u - t.cu) % 5 == 0 and (v - t.cv) % 5 == 0:
                x, z = frame.world(u, v)
                if frame.canvas.get(x, fy + 3, z) in (0, air):
                    F.ceiling_lamp(frame, u, fy + 3, v)
        s = int(math.ceil(max(t.a, t.b)))
        frame.light_grid(t.cu - s, t.cv - s, t.cu + s, t.cv + s, fy + 3, 4)


def entrance(frame: Frame, t: Tower) -> None:
    """Двойная стеклянная дверь в фасаде по центру и козырёк над ней."""
    v = t.front_v(t.cu, 0)
    for u in (t.cu, t.cu + 1):
        if t.front_v(u, 0) != v:
            v = min(v, t.front_v(u, 0))
    for u in (t.cu - 1, t.cu + 2):
        frame.fill(u, CITY_Y + 1, v, u, CITY_Y + 3, v, t.mullion)
    frame.door(t.cu, CITY_Y + 1, v, "front", "birch", hinge="left")
    frame.door(t.cu + 1, CITY_Y + 1, v, "front", "birch", hinge="right")
    frame.fill(t.cu, CITY_Y + 3, v, t.cu + 1, CITY_Y + 3, v, t.glass)
    for u in range(t.cu - 2, t.cu + 4):
        frame.set(u, CITY_Y + 4, v - 1, B.slab("smooth_quartz", top=True))
        frame.set(u, CITY_Y + 4, v - 2, B.slab("smooth_quartz", top=True))


def plaza(frame: Frame, lot: Lot, rng: random.Random, towers: list[Tower], label: str) -> None:
    """Площадь у подножия: плитка, фонтан, деревья в кадках, фонари, табличка."""
    from .homes import tree
    W, D = frame.W, frame.D
    frame.fill(0, CITY_Y - 2, 0, W - 1, CITY_Y - 1, D - 1, B.CONCRETE_GRAY)
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.SIDEWALK)
    frame.fill(0, CITY_Y + 1, 0, W - 1, 300, D - 1, B.AIR)
    for u in range(0, W, 2):
        for v in range(0, D, 2):
            frame.set(u, CITY_Y, v, "minecraft:polished_andesite")
    busy = set()
    for t in towers:
        busy |= t.cells(0)
    for u in range(2, W - 2, 6):
        for v in (2, D - 3):
            if all((u + du, v + dv) not in busy for du in (-2, -1, 0, 1, 2)
                   for dv in (-2, -1, 0, 1, 2)):
                frame.set(u, CITY_Y, v, B.GRASS)
                tree(frame, u, v, rng)
    for u in range(4, W - 4, 8):
        if (u, 1) not in busy:
            frame.set(u, CITY_Y + 1, 1, "mcwlights:classic_street_lamp[part=base,lit=true]")
            frame.set(u, CITY_Y + 2, 1, "mcwlights:classic_street_lamp[part=middle,lit=true]")
            frame.set(u, CITY_Y + 3, 1, "mcwlights:classic_street_lamp[part=top,lit=true]")
    rotation = {"north": 8, "south": 0, "west": 4, "east": 12}[lot.facing]
    x, z = frame.world(W // 2 + 4, 1)
    frame.canvas.sign(x, CITY_Y + 1, z, B.SIGN_STANDING.format(r=rotation),
                      _lines(label), color="white", glowing=True)


def _lines(label: str) -> list[str]:
    from .commercial import split_label
    return split_label(label)


# ---------------------------------------------------------------------------
#  Башня Старка
# ---------------------------------------------------------------------------

FONT = {
    "S": [".###.", "#...#", "#....", ".###.", "....#", "#...#", ".###."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
}


def letters(frame: Frame, t: Tower, word: str, top_y: int, color: str = "minecraft:sea_lantern",
            back: str = "minecraft:black_stained_glass", scale: int = 2) -> None:
    """
    Буквы одна под другой на фасаде: пиксель буквы — светящийся блок,
    фон — тёмное стекло. scale — во сколько раз пиксель больше блока.
    """
    y = top_y
    half = 5 * scale // 2
    for ch in word:
        rows = FONT[ch]
        for r in range(7 * scale):
            row = rows[r // scale]
            for c in range(5 * scale):
                u = t.cu - half + c
                yy = y - r
                k = max(0, min(t.floors - 1, (yy - CITY_Y) // STOREY))
                v = t.front_v(u, k)
                frame.set(u, yy, v, color if row[c // scale] == "#" else back)
        y -= 7 * scale + 3


def build_stark(canvas, lot: Lot) -> None:
    rng = random.Random(lot.seed ^ 0x57A2C)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    t = Tower(cu=W // 2 - 1, cv=D // 2 + 2, a=13, b=10, floors=34, shape="ellipse",
              glass=B.GLASS_CYAN, mullion=B.CONCRETE_WHITE, edge=B.CONCRETE_WHITE,
              setbacks=[(24, 2)],
              plans=(["office", "office", "lab", "office", "lab", "office", "office", "lab"] * 4)
              [:31] + ["restaurant", "penthouse"],
              name="STARK TOWER")
    plaza(frame, lot, rng, [t], "STARK TOWER")
    shell(frame, t, rng)
    entrance(frame, t)
    core(frame, t)
    furnish(frame, t, rng, statue="suit")
    lights(frame, t)
    top = t.top()
    letters(frame, t, "STARK", top - 6)
    # Посадочная площадка-консоль у вершины, выход на неё — из ресторана.
    pk = t.floors - 3
    py = CITY_Y + STOREY * pk
    fv = t.front_v(t.cu, pk)
    pcu, pcv, r = t.cu + 6, fv - 6, 6
    for u in range(pcu - r, pcu + r + 1):
        for v in range(pcv - r, pcv + r + 1):
            d = math.hypot(u - pcu, v - pcv)
            if d <= r + 0.3 and not t.inside(u, v, pk):
                frame.set(u, py, v, "minecraft:smooth_stone" if d < r - 1 else B.CONCRETE_GRAY)
                door = abs(u - pcu) <= 1 and v >= fv - 2
                if r - 1 <= d <= r + 0.3 and not door:
                    frame.set(u, py + 1, v, "minecraft:sea_lantern" if (u + v) % 3 == 0
                              else B.IRON_BARS)
    # Разметка «H» и подкос под консолью.
    for dv in (-2, -1, 0, 1, 2):
        frame.set(pcu - 2, py, pcv + dv, B.CONCRETE_YELLOW)
        frame.set(pcu + 2, py, pcv + dv, B.CONCRETE_YELLOW)
    for du in (-1, 0, 1):
        frame.set(pcu + du, py, pcv, B.CONCRETE_YELLOW)
    # Подкос под консолью: ступенчатая балка от края площадки к фасаду.
    for i in range(1, 6):
        bv = fv - 6 + i
        frame.fill(pcu, py - i - 1, bv, pcu, py - i, bv, B.QUARTZ_SMOOTH)
    # Проём из этажа на площадку; в зале перед ним — свободный проход.
    for u in range(t.cu + 5, t.cu + 8):
        v = t.front_v(u, pk)
        frame.fill(u, py + 1, v, u, py + 2, v + 2, B.AIR)
        frame.set(u, py, v - 1, "minecraft:smooth_stone")
    # Лопасть над крышей: изогнутый белый «плавник» вдоль задней кромки.
    for i, u in enumerate(range(t.cu - 6, t.cu + 7)):
        h = int(20 * math.cos((i - 6) / 7.5)) + 2
        bv = t.back_v(u, t.floors - 1)
        frame.fill(u, top + 1, bv, u, top + h, bv, B.CONCRETE_WHITE)
    # Крыша: антенны и красные огни.
    frame.fill(t.cu, top + 1, t.cv, t.cu, top + 30, t.cv, B.IRON_BARS)
    frame.set(t.cu, top + 31, t.cv, B.REDSTONE_LAMP_ON)
    frame.set(t.cu, top + 32, t.cv, B.REDSTONE_BLOCK)


# ---------------------------------------------------------------------------
#  «Меридиан»: круглая башня со спиральной лентой
# ---------------------------------------------------------------------------

def build_meridian(canvas, lot: Lot) -> None:
    rng = random.Random(lot.seed ^ 0x3E21D)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    t = Tower(cu=W // 2, cv=D // 2 + 1, a=12, b=12, floors=30, shape="round",
              glass=B.GLASS_GRAY, mullion=B.CONCRETE_LIGHT, edge=B.CONCRETE_LIGHT,
              core_wall=B.CONCRETE_LIGHT, setbacks=[(22, 2), (27, 2)],
              plans=["office"] * 27 + ["lab", "restaurant"], name="MERIDIAN")

    def band(u: int, v: int, k: int) -> str | None:
        ang = math.degrees(math.atan2(v - t.cv, u - t.cu)) % 360
        target = (k * 18) % 360
        diff = min(abs(ang - target), 360 - abs(ang - target))
        return B.GLASS_BLUE if diff < 28 else None

    plaza(frame, lot, rng, [t], "MERIDIAN")
    shell(frame, t, rng, band)
    entrance(frame, t)
    core(frame, t)
    furnish(frame, t, rng)
    lights(frame, t)
    top = t.top()
    for i in range(24):
        frame.set(t.cu, top + 1 + i, t.cv, B.IRON_BARS if i > 4 else B.CONCRETE_LIGHT)
    frame.set(t.cu, top + 25, t.cv, B.REDSTONE_LAMP_ON)


# ---------------------------------------------------------------------------
#  «Сансет Плаза»: две башни и мост между ними
# ---------------------------------------------------------------------------

def build_sunset(canvas, lot: Lot) -> None:
    rng = random.Random(lot.seed ^ 0x5A5E7)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    left = Tower(cu=11, cv=D // 2, a=8, b=9, floors=28, shape="chamfer", chamfer=3,
                 glass=B.GLASS_BLUE, mullion=B.CALCITE, edge=B.CALCITE,
                 core_wall=B.CALCITE, plans=["office"] * 26 + ["restaurant"],
                 name="SUNSET PLAZA")
    right = Tower(cu=W - 12, cv=D // 2, a=8, b=9, floors=23, shape="chamfer", chamfer=3,
                  glass=B.GLASS_BLUE, mullion=B.CALCITE, edge=B.CALCITE,
                  core_wall=B.CALCITE, plans=["office"] * 21 + ["penthouse"],
                  name="SUNSET PLAZA")
    plaza(frame, lot, rng, [left, right], "SUNSET PLAZA")
    for t in (left, right):
        shell(frame, t, rng)
        entrance(frame, t)
        core(frame, t)
        furnish(frame, t, rng)
        lights(frame, t)
    # Мост на 15-м этаже: застеклённая галерея между башнями.
    k = 15
    fy = CITY_Y + STOREY * k
    u0 = left.cu + int(left.a)
    u1 = right.cu - int(right.a)
    cv = D // 2
    frame.fill(u0, fy, cv - 2, u1, fy, cv + 2, B.CALCITE)
    frame.fill(u0, fy + 4, cv - 2, u1, fy + 4, cv + 2, B.CALCITE)
    frame.fill(u0 + 1, fy + 1, cv - 2, u1 - 1, fy + 3, cv - 2, B.GLASS_BLUE)
    frame.fill(u0 + 1, fy + 1, cv + 2, u1 - 1, fy + 3, cv + 2, B.GLASS_BLUE)
    frame.fill(u0, fy + 1, cv - 1, u1, fy + 3, cv + 1, B.AIR)
    frame.light((u0 + u1) // 2, fy + 3, cv)
    for t in (left, right):
        for top in (t.top(),):
            for u in range(t.cu - 2, t.cu + 3):
                for v in range(t.cv - 2, t.cv + 3):
                    frame.set(u, top + 1, v, B.CONCRETE_BLACK)
            frame.set(t.cu, top + 1, t.cv, B.CONCRETE_WHITE)


def entrance_point(lot: Lot) -> tuple[int, int, int]:
    """Тротуар перед входом: середина фасадной границы участка, шаг на улицу."""
    frame = Frame(None, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    x, z = frame.world(frame.W // 2, -1)
    return x, CITY_Y + 1, z


TOWER_BUILDERS = {"STARK TOWER": build_stark, "MERIDIAN": build_meridian,
                  "SUNSET PLAZA": build_sunset}
