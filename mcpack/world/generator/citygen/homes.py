"""
Жильё: частные дома, виллы, таунхаусы и многоквартирные дома.

Прошлая версия ставила на жилой участок 48×48 одну коробку с «пирамидой»
из ступеней сверху: крыша висела над стенами, фронтоны оставались дырявыми,
а внутри была пустая бетонная коробка. Здесь дом собирается так, как его
строят руками:

* цоколь из камня и пол из досок на блок выше земли, крыльцо со ступенькой;
* стены с угловыми столбами, межэтажный пояс, окна-пары из стеклянных
  панелей с подоконниками;
* двускатная крыша вдоль длинной стороны: свес на блок, закрытые фронтоны
  с окошком, конёк плитой, иногда труба с дымком;
* комнаты: гостиная, кухня, спальня, лестница на второй этаж, свет в каждой;
* двор: газон, дорожка к двери, живая изгородь или забор, деревья, клумбы.

Всё рисуется в координатах участка (см. frame.Frame), поэтому один и тот же
код работает для домов, повёрнутых на любую сторону улицы.
"""
from __future__ import annotations

import random
from dataclasses import dataclass

from . import blocks as B
from .frame import Frame, LOCAL_VEC
from .plan import CITY_Y, Lot

FY = CITY_Y + 1           # уровень пола первого этажа: дом стоит на цоколе
STOREY = 4                # пол + три блока высоты


@dataclass(frozen=True)
class Style:
    wall: str
    trim: str             # углы, пояс, перемычки
    floor: str            # доски пола
    roof: str             # материал ступеней/плит крыши
    base: str = "minecraft:stone_bricks"
    door: str = "oak"
    sill: str = "spruce"  # люк-подоконник
    hedge: bool = True
    flat: bool = False    # плоская крыша с парапетом (современный стиль)


HOUSE_STYLES = [
    # Белая штукатурка и черепица — калифорнийская классика.
    Style(B.CONCRETE_WHITE, "minecraft:stripped_dark_oak_log[axis=y]", B.SPRUCE_PLANKS,
          "brick", door="dark_oak", sill="dark_oak"),
    Style(B.TERRACOTTA_WHITE, "minecraft:stripped_spruce_log[axis=y]", B.OAK_PLANKS,
          "dark_oak", door="spruce"),
    Style(B.BRICKS, "minecraft:stone_bricks", B.DARK_OAK_PLANKS, "deepslate_tile",
          door="dark_oak", sill="spruce", hedge=False),
    Style(B.BIRCH_PLANKS, "minecraft:stripped_oak_log[axis=y]", B.OAK_PLANKS, "spruce",
          door="spruce", sill="spruce", hedge=False),
    Style("minecraft:smooth_sandstone", "minecraft:cut_sandstone", B.BIRCH_PLANKS,
          "acacia", door="acacia", sill="acacia"),
    Style("minecraft:light_blue_terracotta", "minecraft:white_concrete", B.OAK_PLANKS,
          "dark_oak", door="birch", sill="birch"),
    Style(B.MUD_BRICKS, "minecraft:packed_mud", B.SPRUCE_PLANKS, "mud_brick",
          door="spruce", sill="spruce"),
    Style("minecraft:yellow_terracotta", "minecraft:white_terracotta", B.OAK_PLANKS,
          "spruce", door="oak", sill="oak"),
]

MODERN_STYLES = [
    Style(B.CONCRETE_WHITE, "minecraft:gray_concrete", B.BIRCH_PLANKS, "smooth_stone",
          base="minecraft:polished_deepslate", door="dark_oak", sill="dark_oak", flat=True),
    Style(B.CALCITE, "minecraft:polished_deepslate", B.SPRUCE_PLANKS, "smooth_stone",
          base="minecraft:polished_deepslate", door="spruce", sill="spruce", flat=True),
    Style("minecraft:smooth_quartz", "minecraft:stripped_warped_stem[axis=y]",
          B.DARK_OAK_PLANKS, "quartz", base="minecraft:polished_andesite", door="warped",
          sill="warped", flat=True),
]

# Цвета таунхаусов: соседние секции отличаются, как на настоящей улице.
ROW_WALLS = ["minecraft:white_terracotta", "minecraft:light_blue_terracotta",
             "minecraft:yellow_terracotta", "minecraft:pink_terracotta",
             "minecraft:smooth_sandstone", "minecraft:light_gray_terracotta",
             B.CONCRETE_WHITE]

FLOWERS = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower",
           "minecraft:oxeye_daisy", "minecraft:allium", "minecraft:azure_bluet"]


def _rng(lot: Lot, salt: int = 0) -> random.Random:
    return random.Random(lot.seed ^ (salt * 0x9E3779B1) ^ 0x51AB)


def _furnace(frame: Frame, u: int, y: int, v: int, face: str, kind: str = "furnace") -> None:
    frame.set(u, y, v, f"minecraft:{kind}[facing={frame.dir(face)},lit=false]")


def _bed(frame: Frame, u: int, y: int, v: int, head: str, color: str = "red") -> None:
    """Кровать: ноги в (u, v), изголовье — в сторону head."""
    du, dv = LOCAL_VEC[head]
    facing = frame.dir(head)
    frame.set(u, y, v, f"minecraft:{color}_bed[facing={facing},occupied=false,part=foot]")
    frame.set(u + du, y, v + dv,
              f"minecraft:{color}_bed[facing={facing},occupied=false,part=head]")


def _chest(frame: Frame, u: int, y: int, v: int, face: str) -> None:
    frame.container(u, y, v,
                    f"minecraft:chest[facing={frame.dir(face)},type=single,waterlogged=false]",
                    "minecraft:chest")


def _segments(length: int, blocked: set[int]) -> list[tuple[int, int]]:
    """Свободные отрезки стены между углами, дверями и перегородками."""
    out = []
    start = None
    for i in range(1, length - 1):
        free = i not in blocked
        if free and start is None:
            start = i
        if not free and start is not None:
            out.append((start, i - 1))
            start = None
    if start is not None:
        out.append((start, length - 2))
    return out


def window_cells(length: int, blocked: set[int]) -> list[int]:
    """
    Где на стене длиной length прорезать окна.

    Окна идут парами с простенком в блок, по краям отрезка — тоже простенок:
    «.ОО.ОО.». Узкий отрезок в три блока получает одно окно посередине.
    """
    cells: list[int] = []
    for a, b in _segments(length, blocked):
        span = b - a + 1
        if span >= 4:
            pairs = (span - 1) // 3
            used = 3 * pairs + 1
            first = a + (span - used) // 2 + 1
            for j in range(pairs):
                cells += [first + 3 * j, first + 3 * j + 1]
        elif span == 3:
            cells.append(a + 1)
    return cells


# ---------------------------------------------------------------------------
#  Коробка дома
# ---------------------------------------------------------------------------

@dataclass
class Box:
    """Габарит дома в координатах участка: левый передний угол и размеры."""
    u0: int
    v0: int
    w: int
    d: int
    floors: int

    @property
    def u1(self) -> int:
        return self.u0 + self.w - 1

    @property
    def v1(self) -> int:
        return self.v0 + self.d - 1

    @property
    def top(self) -> int:
        """Уровень потолка последнего этажа (он же пол чердака)."""
        return FY + STOREY * self.floors


def shell(frame: Frame, box: Box, style: Style, door_u: int,
          windows_on: tuple[str, ...] = ("front", "back", "left", "right")) -> None:
    """Цоколь, полы, стены, пояса, углы, окна и входная дверь."""
    u0, v0, u1, v1 = box.u0, box.v0, box.u1, box.v1
    # Фундамент в землю и цоколь на уровне пола.
    frame.fill(u0, CITY_Y - 2, v0, u1, CITY_Y, v1, B.STONE)
    frame.fill(u0, FY, v0, u1, FY, v1, style.floor)
    frame.outline(u0, FY, v0, u1, FY, v1, style.base)

    for k in range(box.floors):
        fy = FY + STOREY * k
        frame.outline(u0, fy + 1, v0, u1, fy + 3, v1, style.wall)
        # Перекрытие: доски внутри, пояс из отделки по периметру.
        frame.fill(u0, fy + 4, v0, u1, fy + 4, v1, style.floor)
        frame.outline(u0, fy + 4, v0, u1, fy + 4, v1, style.trim)
        # Окна.
        blocked_front = {door_u - u0 - 1, door_u - u0, door_u - u0 + 1} if k == 0 else set()
        for side in windows_on:
            if side in ("front", "back"):
                length = box.w
                cells = window_cells(length, blocked_front if side == "front" else set())
                v = v0 if side == "front" else v1
                for i in cells:
                    _window(frame, u0 + i, fy, v, side, style)
            else:
                length = box.d
                cells = window_cells(length, set())
                u = u0 if side == "left" else u1
                for i in cells:
                    _window(frame, u, fy, v0 + i, side, style)

    # Угловые столбы на всю высоту.
    for u, v in ((u0, v0), (u1, v0), (u0, v1), (u1, v1)):
        frame.fill(u, FY + 1, v, u, box.top, v, style.trim)

    # Вход: дверь, ступенька, козырёк.
    frame.door(door_u, FY + 1, v0, "front", style.door)
    frame.set(door_u, FY + 3, v0, style.trim)
    frame.set(door_u, CITY_Y + 1, v0 - 1, frame.stairs(_stair_mat(style.base), "back"))
    for du in (-1, 0, 1):
        frame.set(door_u + du, FY + 3, v0 - 1, B.slab(style.roof, top=True))


def _stair_mat(block: str) -> str:
    name = block.split("[")[0].replace("minecraft:", "")
    return {
        "stone_bricks": "stone_brick", "polished_deepslate": "polished_deepslate",
        "polished_andesite": "polished_andesite", "cobblestone": "cobblestone",
    }.get(name, "stone_brick")


def _window(frame: Frame, u: int, fy: int, v: int, side: str, style: Style) -> None:
    """Окно в два блока высотой с подоконником снаружи."""
    frame.set(u, fy + 2, v, B.GLASS_PANE)
    frame.set(u, fy + 3, v, B.GLASS_PANE)
    du, dv = LOCAL_VEC[side]
    frame.set(u + du, fy + 1, v + dv,
              f"minecraft:{style.sill}_trapdoor[facing={frame.dir(side)},half=top,"
              f"open=false,powered=false,waterlogged=false]")


def gable_roof(frame: Frame, box: Box, style: Style, rng: random.Random,
               chimney: bool = True) -> int:
    """
    Двускатная крыша вдоль длинной стороны. Возвращает высоту конька.

    Ступени идут от свеса над стеной к коньку; под ними фронтоны закрыты
    стеной с окошком. Высота ограничена шириной короткой стороны, поэтому
    огромных «пирамид» больше нет: дом шире 13 блоков получает плоскую
    крышу (см. flat_roof).
    """
    along_u = box.w >= box.d
    span = box.d if along_u else box.w
    length = box.w if along_u else box.d
    T = box.top
    stair = style.roof
    slab = B.slab(style.roof)

    def put(a: int, y: int, b: int, state: str) -> None:
        """a — вдоль конька (от -1 до length), b — поперёк (от -1 до span)."""
        if along_u:
            frame.set(box.u0 + a, y, box.v0 + b, state)
        else:
            frame.set(box.u0 + b, y, box.v0 + a, state)

    rise_lo, rise_hi = ("back", "front") if along_u else ("right", "left")
    ridge_y = T
    for k in range(span // 2 + 2):
        y = T + k
        b_lo, b_hi = -1 + k, span - k
        if b_lo > b_hi:
            break
        ridge_y = y
        for a in range(-1, length + 1):
            if b_lo == b_hi:
                put(a, y, b_lo, slab)
            else:
                put(a, y, b_lo, frame.stairs(stair, rise_lo))
                put(a, y, b_hi, frame.stairs(stair, rise_hi))
        # Фронтоны: стена между скатами на обоих торцах.
        if k >= 1:
            for b in range(b_lo + 1, b_hi):
                put(0, y, b, style.wall)
                put(length - 1, y, b, style.wall)
        if b_hi - b_lo <= 1:
            break

    # Окошко во фронтоне, если там хватает места.
    mid = (span - 1) // 2
    if span >= 7:
        put(0, T + 2, mid, B.GLASS_PANE)
        put(length - 1, T + 2, mid, B.GLASS_PANE)
        if span % 2 == 0:
            put(0, T + 2, mid + 1, B.GLASS_PANE)
            put(length - 1, T + 2, mid + 1, B.GLASS_PANE)

    if chimney and rng.random() < 0.55 and span >= 7:
        # Труба снаружи у торцевой стены, рядом с окошком фронтона.
        a = length
        b = mid - 1
        for y in range(CITY_Y + 1, ridge_y + 2):
            put(a, y, b, B.BRICKS)
        put(a, ridge_y + 2, b, B.slab("brick"))
    return ridge_y


def flat_roof(frame: Frame, box: Box, style: Style) -> int:
    """Плоская кровля с парапетом: для современных вилл и широких домов."""
    T = box.top
    frame.fill(box.u0, T, box.v0, box.u1, T, box.v1, style.trim)
    frame.fill(box.u0 + 1, T, box.v0 + 1, box.u1 - 1, T, box.v1 - 1, B.CONCRETE_LIGHT)
    frame.outline(box.u0, T + 1, box.v0, box.u1, T + 1, box.v1, style.trim)
    return T + 1


# ---------------------------------------------------------------------------
#  Интерьер
# ---------------------------------------------------------------------------

def light_all(frame: Frame, box: Box) -> None:
    """Сетка ламп на каждом этаже поверх точечных: в доме не остаётся тёмных углов."""
    for k in range(box.floors):
        frame.light_grid(box.u0, box.v0, box.u1, box.v1, FY + STOREY * k + 3, 4)
    # Чердак тоже освещён: иначе в тёмной пустоте под крышей заводятся мобы.
    frame.light_grid(box.u0, box.v0, box.u1, box.v1, box.top + 1, 3)


def _partition(frame: Frame, u0: int, v0: int, u1: int, v1: int, fy: int, wall: str) -> None:
    frame.fill(u0, fy + 1, v0, u1, fy + 3, v1, wall)


def interior(frame: Frame, box: Box, style: Style, door_u: int, rng: random.Random) -> None:
    """
    Комнаты. Одноэтажный дом: гостиная спереди, сзади спальня и кухня.
    Двухэтажный: внизу гостиная-кухня и лестница, наверху холл и спальня.
    """
    u0, v0, u1, v1 = box.u0, box.v0, box.u1, box.v1
    inner = style.floor

    if box.floors == 1:
        mid = v0 + box.d // 2
        _partition(frame, u0 + 1, mid, u1 - 1, mid, FY, inner)
        split = min(max(door_u, u0 + 3), u1 - 3)
        _partition(frame, split, mid + 1, split, v1 - 1, FY, inner)
        for du in (-1, 1):
            frame.door(split + du, FY + 1, mid, "front", "oak",
                       hinge="left" if du < 0 else "right")
            frame.set(split + du, FY + 3, mid, inner)
        # Гостиная.
        _living(frame, u0 + 1, v0 + 1, u1 - 1, mid - 1, door_u, rng)
        frame.light((u0 + u1) // 2, FY + 3, (v0 + mid) // 2)
        # Спальня слева, кухня справа.
        if v1 - 2 > mid:
            _bed(frame, u0 + 1, FY + 1, v1 - 2, "back")
        if u0 + 2 < split:
            _chest(frame, u0 + 2, FY + 1, v1 - 1, "front")
        frame.light((u0 + split) // 2, FY + 3, (mid + v1) // 2)
        _kitchen(frame, split + 1, mid + 1, u1 - 1, v1 - 1)
        frame.light((split + u1) // 2, FY + 3, (mid + v1) // 2)
        return

    # Первый этаж: лестница у левой стены, дальше открытая гостиная-кухня.
    frame.staircase(u0 + 1, v0 + 2, FY, "back", _plank_mat(style.floor), rail="right")
    _living(frame, u0 + 3, v0 + 1, u1 - 1, v0 + box.d // 2, door_u, rng)
    _kitchen(frame, u0 + 3, v1 - 1, u1 - 1, v1 - 1)
    frame.light((u0 + u1) // 2, FY + 3, v0 + box.d // 3)
    frame.light((u0 + u1) // 2, FY + 3, v1 - 2)

    # Второй этаж: холл с лестницей слева, спальня справа за перегородкой.
    fy = FY + STOREY
    split = u0 + max(4, box.w // 2)
    _partition(frame, split, v0 + 1, split, v1 - 1, fy, inner)
    frame.door(split, fy + 1, v1 - 2, "left", "oak")
    frame.set(split, fy + 3, v1 - 2, inner)
    _bed(frame, u1 - 2, fy + 1, v0 + 2, "front", rng.choice(("red", "blue", "white")))
    _chest(frame, u1 - 1, fy + 1, v1 - 1, "left")
    frame.set(u0 + 2, fy + 1, v1 - 1, B.BOOKSHELF)
    frame.set(u0 + 3, fy + 1, v1 - 1, B.CRAFTING)
    frame.light((u0 + split) // 2, fy + 3, (v0 + v1) // 2)
    frame.light((split + u1) // 2, fy + 3, (v0 + v1) // 2)
    for k in range(box.floors - 2):
        frame.light((u0 + u1) // 2, FY + STOREY * (k + 2) + 3, (v0 + v1) // 2)


def _plank_mat(floor: str) -> str:
    name = floor.split("[")[0].replace("minecraft:", "")
    return name.replace("_planks", "") if name.endswith("_planks") else "oak"


def _living(frame: Frame, u0: int, v0: int, u1: int, v1: int, door_u: int,
            rng: random.Random) -> None:
    """Гостиная: ковёр, диван у стены, книжный шкаф."""
    if u1 - u0 < 3 or v1 - v0 < 2:
        return
    color = rng.choice(("red", "gray", "cyan", "brown", "light_gray"))
    for u in range(u0 + 1, u1):
        for v in range(v0 + 1, v1 + 1):
            if u != door_u:
                frame.set(u, FY + 1, v, B.carpet(color))
    # Диван спинкой к правой стене.
    for v in (v1 - 1, v1):
        if v > v0:
            frame.set(u1, FY + 1, v, frame.stairs("spruce", "right"))
    frame.set(u0, FY + 1, v1, B.BOOKSHELF)
    frame.set(u0, FY + 2, v1, B.FLOWER_POT)


def _kitchen(frame: Frame, u0: int, v0: int, u1: int, v1: int) -> None:
    """Кухня вдоль дальней стены: плита, раковина, стол, бочка."""
    items = [lambda u, v: _furnace(frame, u, FY + 1, v, "front", "smoker"),
             lambda u, v: frame.set(u, FY + 1, v, B.CAULDRON),
             lambda u, v: frame.set(u, FY + 1, v, B.CRAFTING),
             lambda u, v: frame.set(u, FY + 1, v, B.BARREL)]
    u = u1
    for put in items:
        if u < u0:
            break
        put(u, v1)
        u -= 1


# ---------------------------------------------------------------------------
#  Двор
# ---------------------------------------------------------------------------

def garden(frame: Frame, lot: Lot, box: Box, style: Style, door_u: int, rng: random.Random,
           drive_u: int | None = None) -> None:
    """Газон, дорожка, клумбы, деревья, изгородь по границе участка."""
    W, D = frame.W, frame.D
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.GRASS)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 12, D - 1, B.AIR)
    path = "minecraft:dirt_path" if not style.flat else "minecraft:polished_andesite"
    frame.fill(door_u, CITY_Y, 0, door_u, CITY_Y, box.v0 - 2, path)
    if drive_u is not None:
        frame.fill(drive_u, CITY_Y, 0, drive_u + 2, CITY_Y, box.v0 + 4, B.CONCRETE_GRAY)

    # Клумба вдоль фасада.
    for u in range(box.u0, box.u1 + 1):
        if abs(u - door_u) <= 1:
            continue
        if rng.random() < 0.6:
            frame.set(u, CITY_Y + 1, box.v0 - 1, rng.choice(FLOWERS))

    # Ограда: спереди низкая изгородь или штакетник, сбоку и сзади — забор.
    front = B.OAK_LEAVES if style.hedge else B.fence("spruce" if "spruce" in style.door
                                                   else "oak")
    side = B.fence("spruce" if "spruce" in style.door else "oak")
    open_cells = {door_u}
    if drive_u is not None:
        open_cells |= {drive_u, drive_u + 1, drive_u + 2}
    for u in range(0, W):
        if u not in open_cells:
            frame.set(u, CITY_Y + 1, 0, front)
    boundary_fence(frame, lot, side)

    # Фонарь у калитки.
    lamp_u = door_u + 1 if door_u + 1 not in open_cells else door_u - 1
    frame.set(lamp_u, CITY_Y + 1, 1, side)
    frame.set(lamp_u, CITY_Y + 2, 1, side)
    frame.set(lamp_u, CITY_Y + 3, 1, B.LANTERN)

    # Деревья на заднем дворе.
    back = D - 2 - box.v1
    if back >= 4:
        for _ in range(rng.randint(1, 2)):
            tu = rng.randint(2, max(2, W - 3))
            tv = rng.randint(box.v1 + 3, max(box.v1 + 3, D - 3))
            tree(frame, tu, tv, rng)


def boundary_fence(frame: Frame, lot: Lot, material: str) -> None:
    """
    Забор по боковым и задней границам участка.

    Соседние участки делят общую границу, и если каждый поставит свой
    забор, получится два ряда штакетника вплотную. Поэтому правую сторону
    огораживает сам участок, левую — только если за ней улица, а заднюю —
    тот из двух участков, что смотрит на север или запад.
    """
    W, D = frame.W, frame.D
    sides = lot_sides(frame, lot)
    columns = [W - 1] + ([0] if "left" in sides else [])
    for u in columns:
        for v in range(1, D):
            frame.set(u, CITY_Y + 1, v, material)
    if lot.facing in ("north", "west") or "back" in sides:
        for u in range(0, W):
            frame.set(u, CITY_Y + 1, D - 1, material)


def lot_sides(frame: Frame, lot: Lot) -> set[str]:
    """Какие локальные стороны участка выходят на улицу."""
    local = {}
    for name in ("left", "right", "back", "front"):
        local[frame.dir(name)] = name
    return {local[s] for s in lot.street_sides if s in local}


def tree(frame: Frame, u: int, v: int, rng: random.Random) -> None:
    """Небольшое дерево: ствол и крона шаром, чтобы не выглядело кубом."""
    log, leaves = rng.choice(((B.OAK_LOG, B.OAK_LEAVES),
                              (B.OAK_LOG, B.BIRCH_LEAVES),
                              ("minecraft:birch_log[axis=y]", B.BIRCH_LEAVES),
                              (B.JUNGLE_LOG, B.JUNGLE_LEAVES)))
    height = rng.randint(4, 6)
    top = CITY_Y + height
    for du in range(-2, 3):
        for dv in range(-2, 3):
            if abs(du) == 2 and abs(dv) == 2:
                continue
            frame.fill(u + du, top - 1, v + dv, u + du, top, v + dv, leaves)
    for du, dv in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        frame.set(u + du, top + 1, v + dv, leaves)
    frame.fill(u, CITY_Y + 1, v, u, top, v, log)


def palm(frame: Frame, u: int, v: int, rng: random.Random) -> None:
    """Пальма: тонкий высокий ствол и листья крестом — это всё-таки Лос-Сантос."""
    height = rng.randint(6, 9)
    top = CITY_Y + height
    frame.fill(u, CITY_Y + 1, v, u, top, v, B.JUNGLE_LOG)
    frame.set(u, top + 1, v, B.JUNGLE_LEAVES)
    frame.set(u, top + 2, v, B.JUNGLE_LEAVES)
    # Листья крестом: два блока наверху и свисающие концы под ними —
    # каждый лист касается соседа гранью, ничего не висит в воздухе.
    for du, dv in LOCAL_VEC.values():
        for r in (1, 2):
            frame.set(u + du * r, top + 1, v + dv * r, B.JUNGLE_LEAVES)
        frame.set(u + du * 2, top, v + dv * 2, B.JUNGLE_LEAVES)
        frame.set(u + du * 3, top, v + dv * 3, B.JUNGLE_LEAVES)


def house_sign(frame: Frame, lot: Lot, u: int, v: int) -> None:
    """Адрес на стене у двери."""
    if lot.address:
        street, number = lot.address.rsplit(", ", 1)
        frame.wall_sign(u, FY + 2, v, "front", [street, f"дом {number}"], color="black")


# ---------------------------------------------------------------------------
#  Типы жилья
# ---------------------------------------------------------------------------

def _house_layout(frame: Frame, rng: random.Random):
    """
    Габарит частного дома. Вынесен отдельно: тот же расчёт нужен риелтору
    (границы дома, который продаётся), поэтому генератор и выгрузка
    недвижимости тянут случайные числа в одном и том же порядке.
    """
    W, D = frame.W, frame.D
    style = rng.choice(HOUSE_STYLES)
    setback = rng.randint(4, 6)
    w = max(7, min(W - 4, rng.randint(9, 13)))
    d = max(7, min(D - setback - 4, rng.randint(8, 11)))
    drive = W - w >= 8
    u0 = 2 if not drive or rng.random() < 0.5 else W - w - 2
    drive_u = (u0 + w + 1) if drive and u0 == 2 else (1 if drive else None)
    floors = 2 if w >= 9 and rng.random() < 0.45 else 1
    box = Box(u0, setback, w, d, floors)
    door_u = u0 + w // 2 if floors == 1 else u0 + max(3, w // 2 + 1)
    if floors == 2 and door_u > box.u1 - 2:
        door_u = box.u1 - 2
    return style, box, door_u, drive_u


def _villa_layout(frame: Frame, rng: random.Random):
    """Габарит виллы — по той же причине, что и _house_layout."""
    W, D = frame.W, frame.D
    modern = rng.random() < 0.5
    style = rng.choice(MODERN_STYLES if modern else HOUSE_STYLES[:2] + HOUSE_STYLES[4:5])
    setback = 3
    w = max(9, min(W - 10, rng.randint(12, 15)))
    d = max(8, min(D - setback - 7, rng.randint(8, 10)))
    box = Box(2, setback, w, d, 2)
    door_u = box.u0 + max(3, w // 2 + 1)
    return style, box, door_u


def _row_layout(frame: Frame) -> tuple[int, int, int, int]:
    """Таунхаусы: число секций, ширина секции, длина ряда, глубина."""
    W, D = frame.W, frame.D
    setback = 3
    count = max(2, min(5, (W - 2) // 7))
    unit = (W - 3) // count
    total = unit * count + 1
    d = max(8, min(D - setback - 4, 10))
    return count, unit, total, d


def build_house(canvas, lot: Lot) -> None:
    """Частный дом на своём участке."""
    rng = _rng(lot)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    style, box, door_u, drive_u = _house_layout(frame, rng)
    w, d = box.w, box.d

    garden(frame, lot, box, style, door_u, rng, drive_u)
    shell(frame, box, style, door_u)
    interior(frame, box, style, door_u, rng)
    if style.flat or min(w, d) > 13:
        flat_roof(frame, box, style)
    else:
        gable_roof(frame, box, style, rng)
    house_sign(frame, lot, door_u + 1, box.v0)
    light_all(frame, box)


def build_villa(canvas, lot: Lot) -> None:
    """Вилла на холмах: два этажа, гараж, бассейн, терраса."""
    rng = _rng(lot)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    style, box, door_u = _villa_layout(frame, rng)
    drive_u = box.u1 + 2

    garden(frame, lot, box, style, door_u, rng, drive_u)
    shell(frame, box, style, door_u, windows_on=("front", "back", "left"))
    interior(frame, box, style, door_u, rng)
    if style.flat:
        flat_roof(frame, box, style)
    else:
        gable_roof(frame, box, style, rng)
    house_sign(frame, lot, door_u + 1, box.v0)
    light_all(frame, box)

    # Гараж с плоской крышей сбоку от дома.
    g0, g1 = box.u1 + 1, min(box.u1 + 5, W - 2)
    if g1 - g0 >= 3:
        gv0, gv1 = box.v0 + 1, box.v0 + 7
        frame.fill(g0, CITY_Y, gv0, g1, CITY_Y, gv1, B.CONCRETE_GRAY)
        frame.outline(g0, CITY_Y + 1, gv0, g1, CITY_Y + 4, gv1, style.wall)
        frame.fill(g0, CITY_Y + 5, gv0, g1, CITY_Y + 5, gv1, style.trim)
        frame.fill(g0 + 1, CITY_Y + 1, gv0, g1 - 1, CITY_Y + 3, gv0, B.AIR)
        frame.fill(g0 + 1, CITY_Y + 4, gv0, g1 - 1, CITY_Y + 4, gv0, style.trim)
        frame.light((g0 + g1) // 2, CITY_Y + 4, (gv0 + gv1) // 2)
        frame.set(g1 - 1, CITY_Y + 1, gv1 - 1, B.BARREL)

    # Бассейн и шезлонги на заднем дворе.
    pv0, pv1 = box.v1 + 3, D - 3
    pu0, pu1 = 3, min(W - 4, 3 + 9)
    if pv1 - pv0 >= 2 and pu1 - pu0 >= 5:
        frame.fill(pu0 - 1, CITY_Y, pv0 - 1, pu1 + 1, CITY_Y, pv1 + 1, B.QUARTZ_SMOOTH)
        frame.fill(pu0, CITY_Y - 3, pv0, pu1, CITY_Y - 3, pv1, "minecraft:light_blue_concrete")
        frame.fill(pu0, CITY_Y - 2, pv0, pu1, CITY_Y, pv1, B.WATER)
        frame.fill(pu0 - 1, CITY_Y - 2, pv0 - 1, pu0 - 1, CITY_Y - 1, pv1 + 1, B.QUARTZ_SMOOTH)
        frame.fill(pu1 + 1, CITY_Y - 2, pv0 - 1, pu1 + 1, CITY_Y - 1, pv1 + 1, B.QUARTZ_SMOOTH)
        frame.fill(pu0 - 1, CITY_Y - 2, pv0 - 1, pu1 + 1, CITY_Y - 1, pv0 - 1, B.QUARTZ_SMOOTH)
        frame.fill(pu0 - 1, CITY_Y - 2, pv1 + 1, pu1 + 1, CITY_Y - 1, pv1 + 1, B.QUARTZ_SMOOTH)
        for u in range(pu1 + 2, min(pu1 + 4, W - 2)):
            frame.set(u, CITY_Y + 1, pv0, frame.stairs("birch", "front"))
    for u in (1, W - 3):
        if D - 3 > box.v1 + 1:
            palm(frame, u, D - 3, rng)


def build_rowhouses(canvas, lot: Lot) -> None:
    """
    Таунхаусы: несколько узких двухэтажных секций под общей крышей.

    У каждой секции своя дверь, свой цвет фасада, лестница и спальня
    наверху; конёк идёт вдоль улицы через весь ряд.
    """
    rng = _rng(lot)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    setback = 3
    count, unit, total, d = _row_layout(frame)
    base = rng.choice(HOUSE_STYLES[:4])
    row = Box(1, setback, total, d, 2)

    # Общий двор, дорожки к каждой двери.
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.GRASS)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 12, D - 1, B.AIR)
    walls = rng.sample(ROW_WALLS, k=min(count, len(ROW_WALLS)))
    for i in range(count):
        u0 = 1 + i * unit
        style = Style(walls[i % len(walls)], base.trim, base.floor, base.roof,
                      base=base.base, door=base.door, sill=base.sill)
        box = Box(u0, setback, unit + 1, d, 2)
        door_u = u0 + unit // 2 + 1
        frame.fill(door_u, CITY_Y, 0, door_u, CITY_Y, setback - 2, "minecraft:dirt_path")
        shell(frame, box, style, door_u, windows_on=("front", "back"))
        # Секция внутри: лестница у левой стены, свет, спальня наверху.
        frame.staircase(u0 + 1, setback + 2, FY, "back", _plank_mat(base.floor), rail="right")
        frame.light(u0 + unit // 2, FY + 3, setback + d // 2)
        fy = FY + STOREY
        _bed(frame, u0 + unit - 1, fy + 1, box.v1 - 2, "back",
             rng.choice(("red", "blue", "white", "yellow")))
        frame.light(u0 + unit // 2, fy + 3, setback + d // 2)
        _kitchen(frame, u0 + 3, box.v1 - 1, u0 + unit - 1, box.v1 - 1)
        if lot.address:
            street, number = lot.address.rsplit(", ", 1)
            frame.wall_sign(door_u + 1, FY + 2, setback, "front",
                            [street, f"дом {number}", f"секция {i + 1}"])
        for u in range(u0 + 1, u0 + unit):
            if abs(u - door_u) > 1 and rng.random() < 0.5:
                frame.set(u, CITY_Y + 1, setback - 1, rng.choice(FLOWERS))
    gable_roof(frame, row, base, rng, chimney=False)
    light_all(frame, row)
    boundary_fence(frame, lot, B.fence("oak"))
    if D - 2 - row.v1 >= 4:
        tree(frame, W // 2, D - 3, rng)


# ---------------------------------------------------------------------------
#  Многоквартирный дом
# ---------------------------------------------------------------------------

APARTMENT_FACADES = [
    (B.BRICKS, "minecraft:stone_bricks", B.OAK_PLANKS),
    (B.TERRACOTTA_WHITE, "minecraft:light_gray_concrete", B.SPRUCE_PLANKS),
    ("minecraft:smooth_sandstone", "minecraft:cut_sandstone", B.BIRCH_PLANKS),
    (B.CONCRETE_LIGHT, B.CONCRETE_GRAY, B.OAK_PLANKS),
    (B.MUD_BRICKS, "minecraft:packed_mud", B.SPRUCE_PLANKS),
    ("minecraft:light_blue_terracotta", B.CONCRETE_WHITE, B.BIRCH_PLANKS),
]


def apartment_box(lot: Lot) -> tuple[Box, int]:
    """Габарит многоквартирного дома и позиция входа — нужна и NPC, и навигатору."""
    rng = _rng(lot, 7)
    W = lot.width if lot.facing in ("north", "south") else lot.depth
    D = lot.depth if lot.facing in ("north", "south") else lot.width
    w = W - 4
    d = max(12, min(D - 5, 16))
    floors = rng.randint(3, 4) if lot.district != "downtown" else rng.randint(4, 6)
    box = Box(2, 2, w, d, floors)
    return box, box.u0 + 2


@dataclass
class Flat:
    """Квартира в многоквартирном доме, координаты участка."""
    floor: int
    number: int
    side: str
    a: int
    b: int
    rv0: int
    rv1: int
    wall_v: int      # стена с дверью в коридор
    out: str         # куда открывается дверь (в коридор)
    last: bool


def apartment_flats(box: Box) -> list[Flat]:
    """
    Нарезка этажей на квартиры: общий расчёт для стройки и для риелтора.
    Номера сквозные по дому, как на табличках «КВ. N».
    """
    u0, v0, u1, v1 = box.u0, box.v0, box.u1, box.v1
    corridor = v0 + box.d // 2
    span = u1 - 1 - (u0 + 5) + 1
    count = max(1, span // 6)
    width = span // count
    flats: list[Flat] = []
    number = 0
    for k in range(box.floors):
        for side, wall_v, out in (("front", corridor - 1, "back"),
                                  ("back", corridor + 2, "front")):
            for i in range(count):
                a = u0 + 5 + i * width
                last = i == count - 1
                b = u1 - 1 if last else a + width - 2
                rv0, rv1 = (v0 + 1, corridor - 2) if side == "front" else (corridor + 3, v1 - 1)
                number += 1
                flats.append(Flat(k, number, side, a, b, rv0, rv1, wall_v, out, last))
    return flats


def build_apartment(canvas, lot: Lot) -> None:
    """
    Жилой дом: подъезд с лестницей, коридор, квартиры с обеих сторон.

    Квартиры пронумерованы по порядку, табличка «КВ. N» висит в коридоре
    на стене у двери — на стене, а не вместо стены.
    """
    rng = _rng(lot, 7)
    frame = Frame(canvas, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)
    W, D = frame.W, frame.D
    wall, trim, floor = rng.choice(APARTMENT_FACADES)
    style = Style(wall, trim, floor, "stone_brick", base="minecraft:stone_bricks",
                  door="dark_oak", sill="spruce", flat=True)
    box, door_u = apartment_box(lot)
    u0, v0, u1, v1 = box.u0, box.v0, box.u1, box.v1

    # Двор: плитка, газон по краям, лавочки.
    frame.fill(0, CITY_Y, 0, W - 1, CITY_Y, D - 1, B.SIDEWALK)
    frame.fill(0, CITY_Y + 1, 0, W - 1, CITY_Y + 30, D - 1, B.AIR)
    frame.fill(0, CITY_Y, v1 + 2, W - 1, CITY_Y, D - 1, B.GRASS)
    for u in range(3, W - 3, 6):
        if D - 2 > v1 + 3:
            tree(frame, u, D - 3, rng)

    shell(frame, box, style, door_u)
    # Над дверью подъезда — табличка с адресом и названием.
    lines = [lot.label or "ЖИЛОЙ ДОМ"]
    if lot.address:
        lines = [lot.address.rsplit(", ", 1)[0], "дом " + lot.address.rsplit(", ", 1)[1]]
    frame.wall_sign(door_u + 1, FY + 2, v0, "front", lines, color="white", glowing=True)

    corridor = v0 + box.d // 2          # коридор: две клетки corridor и corridor+1
    for k in range(box.floors):
        fy = FY + STOREY * k
        # Подъезд: лестница вдоль левой стены, проход рядом с ней.
        if k < box.floors - 1:
            frame.staircase(u0 + 1, v0 + 2, fy, "back", "stone_brick", rail="right")
        # Стены квартир вдоль коридора.
        _partition(frame, u0 + 4, corridor - 1, u1 - 1, corridor - 1, fy, wall)
        _partition(frame, u0 + 4, corridor + 2, u1 - 1, corridor + 2, fy, wall)
        # Отделяем подъезд от квартир, оставляя выход в коридор.
        _partition(frame, u0 + 4, v0 + 1, u0 + 4, corridor - 2, fy, wall)
        _partition(frame, u0 + 4, corridor + 3, u0 + 4, v1 - 1, fy, wall)
        frame.light(u0 + 2, fy + 3, corridor)
        for u in range(u0 + 7, u1, 6):
            frame.light(u, fy + 3, corridor)

        # Квартиры: нарезаем обе стороны на секции по 6-7 блоков.
        for flat in apartment_flats(box):
            if flat.floor != k:
                continue
            a, b, rv0, rv1 = flat.a, flat.b, flat.rv0, flat.rv1
            side, wall_v, out, number = flat.side, flat.wall_v, flat.out, flat.number
            if not flat.last:
                _partition(frame, b + 1, rv0, b + 1, rv1, fy, wall)
            door = a + 1
            frame.door(door, fy + 1, wall_v, out, "spruce")
            frame.set(door, fy + 3, wall_v, wall)
            frame.wall_sign(door + 1, fy + 2, wall_v, out, [f"КВ. {number}"],
                            color="black")
            # Обстановка у дальней стены и свет под потолком.
            if b - a >= 2 and rv1 - rv0 >= 2:
                far, head, step = (rv0, "front", 1) if side == "front" \
                    else (rv1, "back", -1)
                _bed(frame, b, fy + 1, far + step, head,
                     rng.choice(("red", "blue", "white", "green", "yellow")))
                _chest(frame, a, fy + 1, far, out)
                if b - 1 > a:
                    frame.set(b - 1, fy + 1, far, B.CRAFTING)
            frame.light((a + b) // 2, fy + 3, (rv0 + rv1) // 2)

    light_all(frame, box)
    T = flat_roof(frame, box, style)
    # Выход на крышу не делаем, зато на ней стоят баки и вентиляция.
    if box.w >= 12:
        frame.fill(u1 - 4, T, v1 - 4, u1 - 2, T + 2, v1 - 2, B.CONCRETE_LIGHT)
        frame.fill(u1 - 4, T + 3, v1 - 4, u1 - 2, T + 3, v1 - 2, B.slab("smooth_stone"))


# ---------------------------------------------------------------------------
#  Недвижимость: что продаёт риелтор
# ---------------------------------------------------------------------------

# Цена за квадратный блок жилой площади по районам: вилла на холмах
# дороже домика в промзоне. Итог округляется до тысячи.
DISTRICT_PRICE = {
    "downtown": 480, "hills": 650, "beach": 550, "midtown": 380,
    "suburbs": 300, "eastside": 230, "industrial": 200,
}
KIND_TITLE = {"house": "Дом", "villa": "Вилла", "rowhouse": "Таунхаус", "flat": "Квартира"}


def _price(lot: Lot, area: int, floors: int, bonus: float = 1.0) -> int:
    rate = DISTRICT_PRICE.get(lot.district, 300)
    return max(10, round(area * floors * rate * bonus / 1000)) * 1000


def estate_units(lot: Lot) -> list[dict]:
    """
    Жильё на участке, которое можно купить: дом, вилла, секция таунхауса
    или квартира. Координаты мировые; box — сам дом (в нём запираются
    двери и сундуки), plot — всё, что принадлежит владельцу (там нельзя
    ломать и строить чужим), door — входная дверь, куда ведёт навигатор.

    Геометрию считают те же функции, что строят дом, поэтому границы
    совпадают с постройкой блок в блок.
    """
    if not lot.address or lot.kind not in ("house", "villa", "rowhouse", "apartment"):
        return []
    frame = Frame(None, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing)

    def rect(u0: int, v0: int, u1: int, v1: int) -> tuple[int, int, int, int]:
        ax, az = frame.world(u0, v0)
        bx, bz = frame.world(u1, v1)
        return min(ax, bx), min(az, bz), max(ax, bx), max(az, bz)

    def point(u: int, y: int, v: int) -> list[int]:
        x, z = frame.world(u, v)
        return [x, y, z]

    def unit(kind: str, suffix: str, address: str, box_rect, y0: int, y1: int,
             plot_rect, door: list[int], price: int, rooms: str) -> dict:
        bx0, bz0, bx1, bz1 = box_rect
        px0, pz0, px1, pz1 = plot_rect
        return {
            "id": f"{lot.ix}_{lot.iz}_{lot.x0}_{lot.z0}{suffix}",
            "kind": kind,
            "title": KIND_TITLE[kind],
            "address": address,
            "district": lot.district,
            "price": price,
            "rooms": rooms,
            "box": [bx0, y0, bz0, bx1, y1, bz1],
            "plot": [px0, CITY_Y - 3, pz0, px1, y1 + 2, pz1],
            "door": door,
        }

    units: list[dict] = []
    lot_rect = (lot.x0, lot.z0, lot.x1, lot.z1)
    if lot.kind in ("house", "villa"):
        rng = _rng(lot)
        if lot.kind == "house":
            _, box, door_u, _ = _house_layout(frame, rng)
            bonus = 1.0
        else:
            _, box, door_u = _villa_layout(frame, rng)
            bonus = 1.6          # гараж, бассейн и вид с холма
        area = box.w * box.d
        units.append(unit(lot.kind, "", lot.address,
                          rect(box.u0, box.v0, box.u1, box.v1),
                          CITY_Y - 2, box.top + 9, lot_rect,
                          point(door_u, FY + 1, box.v0),
                          _price(lot, area, box.floors, bonus),
                          f"{box.floors} эт., {area} м²"))
    elif lot.kind == "rowhouse":
        count, unit_w, _, d = _row_layout(frame)
        setback = 3
        for i in range(count):
            u0 = 1 + i * unit_w
            box = Box(u0, setback, unit_w + 1, d, 2)
            door_u = u0 + unit_w // 2 + 1
            # Участок секции: её полоса от улицы до задней границы.
            plot = rect(u0 + (1 if i else 0), 0, box.u1 - (0 if i == count - 1 else 1),
                        frame.D - 1)
            area = box.w * box.d
            units.append(unit("rowhouse", f"_s{i + 1}", f"{lot.address}, секция {i + 1}",
                              rect(box.u0, box.v0, box.u1, box.v1),
                              CITY_Y - 2, box.top + 7, plot,
                              point(door_u, FY + 1, box.v0),
                              _price(lot, area, 2, 0.9), f"2 эт., {area} м²"))
    else:
        box, _ = apartment_box(lot)
        for flat in apartment_flats(box):
            fy = FY + STOREY * flat.floor
            v0, v1 = (flat.rv0, flat.wall_v) if flat.side == "front" \
                else (flat.wall_v, flat.rv1)
            r = rect(flat.a, v0, flat.b, v1)
            area = (flat.b - flat.a + 1) * (flat.rv1 - flat.rv0 + 1)
            units.append(unit("flat", f"_f{flat.number}", f"{lot.address}, кв. {flat.number}",
                              r, fy, fy + 3, r,
                              point(flat.a + 1, fy + 1, flat.wall_v),
                              _price(lot, area, 1, 1.2),
                              f"{flat.floor + 1} этаж, {area} м²"))
            # Квартира не залезает на чужой этаж: plot = сам блок квартиры.
            units[-1]["plot"] = [r[0], fy, r[1], r[2], fy + 3, r[3]]
    return units
