"""
Отрисовка мира: рельеф, улицы, эстакада, пирс и тоннели метро.

Каждая функция получает полотно одного региона и рисует только то, что
в него попадает: порядок регионов не важен, стыки всегда совпадают.
"""
from __future__ import annotations

from . import blocks as B
from .canvas import RegionCanvas
from .plan import (CELL, CITY_Y, FREEWAY_HALF, FREEWAY_X, FREEWAY_Y, IX_MAX, IX_MIN,
                   IZ_MAX, IZ_MIN, METRO_Y, PIER_X_FROM, PIER_X_TO, PIER_Z, Plan,
                   SIDEWALK, is_avenue, road_half, street_name)
from .terrain import SEA_LEVEL, Terrain

ROAD_X_MIN = IX_MIN * CELL
ROAD_X_MAX = (IX_MAX + 1) * CELL
ROAD_Z_MIN = IZ_MIN * CELL
ROAD_Z_MAX = (IZ_MAX + 1) * CELL


# ---------------------------------------------------------------------------
#  Рельеф
# ---------------------------------------------------------------------------

# Доля бедрока на слоях над дном мира: 80% на -63 и дальше по убыванию.
BEDROCK_GRADIENT = ((-63, 0.8), (-62, 0.6), (-61, 0.4), (-60, 0.2))


def _noise(x: int, y: int, z: int) -> float:
    """Детерминированный шум 0..1 для блока: один и тот же на любом прогоне."""
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFFFF) / 65536.0

def draw_terrain(canvas: RegionCanvas, terrain: Terrain) -> None:
    """Залить весь регион рельефом: камень, подпочва, поверхность, вода."""
    for x in range(canvas.x0, canvas.x1 + 1):
        for z in range(canvas.z0, canvas.z1 + 1):
            height = terrain.height(x, z)
            top, subsoil = terrain.surface(x, z, height)
            # Бедрок как в ванили: сплошной только на -64, выше — редеющие
            # вкрапления до -60 среди глубинного сланца, а не ровная плита.
            canvas.set(x, -64, z, B.BEDROCK)
            canvas.column(x, z, -63, min(-1, height - 4), B.DEEPSLATE)
            for y, chance in BEDROCK_GRADIENT:
                if _noise(x, y, z) < chance:
                    canvas.set(x, y, z, B.BEDROCK)
            if height - 4 >= 0:
                canvas.column(x, z, 0, height - 4, B.STONE)
            canvas.column(x, z, max(0, height - 3), height - 1, subsoil)
            canvas.set(x, height, z, top)
            if height < SEA_LEVEL:
                canvas.column(x, z, height + 1, SEA_LEVEL, B.WATER)


# ---------------------------------------------------------------------------
#  Улицы
# ---------------------------------------------------------------------------

def draw_roads(canvas: RegionCanvas) -> None:
    if not canvas.covers(ROAD_X_MIN - SIDEWALK - 8, ROAD_Z_MIN - SIDEWALK - 8,
                         ROAD_X_MAX + SIDEWALK + 8, ROAD_Z_MAX + SIDEWALK + 8):
        return
    for index in range(IX_MIN, IX_MAX + 2):
        _street(canvas, index, vertical=True)
    for index in range(IZ_MIN, IZ_MAX + 2):
        _street(canvas, index, vertical=False)
    _intersections(canvas)


def _street(canvas: RegionCanvas, index: int, vertical: bool) -> None:
    half = road_half(index)
    center = index * CELL
    avenue = is_avenue(index)

    if vertical:
        if not canvas.covers(center - half - SIDEWALK, ROAD_Z_MIN,
                             center + half + SIDEWALK, ROAD_Z_MAX):
            return
        # Полотно и воздух над ним.
        canvas.fill(center - half, CITY_Y, ROAD_Z_MIN, center + half, CITY_Y,
                    ROAD_Z_MAX, B.ASPHALT)
        canvas.fill(center - half, CITY_Y + 1, ROAD_Z_MIN, center + half, CITY_Y + 6,
                    ROAD_Z_MAX, B.AIR)
        # Тротуары и бордюр.
        for side in (-1, 1):
            edge = center + side * (half + 1)
            canvas.fill(edge, CITY_Y, ROAD_Z_MIN, edge + side * (SIDEWALK - 1), CITY_Y,
                        ROAD_Z_MAX, B.SIDEWALK)
            canvas.fill(edge, CITY_Y, ROAD_Z_MIN, edge, CITY_Y, ROAD_Z_MAX, B.CURB)
            canvas.fill(edge, CITY_Y + 1, ROAD_Z_MIN, edge + side * (SIDEWALK - 1),
                        CITY_Y + 4, ROAD_Z_MAX, B.AIR)
        # Разметка.
        for z in range(ROAD_Z_MIN, ROAD_Z_MAX + 1):
            if avenue:
                canvas.set(center - 1, CITY_Y, z, B.ROAD_LINE)
                canvas.set(center + 1, CITY_Y, z, B.ROAD_LINE)
            elif z % 4 < 2:
                canvas.set(center, CITY_Y, z, B.ROAD_LINE)
            canvas.set(center - half + 1, CITY_Y, z, B.ROAD_LINE_WHITE)
            canvas.set(center + half - 1, CITY_Y, z, B.ROAD_LINE_WHITE)
    else:
        if not canvas.covers(ROAD_X_MIN, center - half - SIDEWALK,
                             ROAD_X_MAX, center + half + SIDEWALK):
            return
        canvas.fill(ROAD_X_MIN, CITY_Y, center - half, ROAD_X_MAX, CITY_Y,
                    center + half, B.ASPHALT)
        canvas.fill(ROAD_X_MIN, CITY_Y + 1, center - half, ROAD_X_MAX, CITY_Y + 6,
                    center + half, B.AIR)
        for side in (-1, 1):
            edge = center + side * (half + 1)
            canvas.fill(ROAD_X_MIN, CITY_Y, edge, ROAD_X_MAX, CITY_Y,
                        edge + side * (SIDEWALK - 1), B.SIDEWALK)
            canvas.fill(ROAD_X_MIN, CITY_Y, edge, ROAD_X_MAX, CITY_Y, edge, B.CURB)
            canvas.fill(ROAD_X_MIN, CITY_Y + 1, edge, ROAD_X_MAX, CITY_Y + 4,
                        edge + side * (SIDEWALK - 1), B.AIR)
        for x in range(ROAD_X_MIN, ROAD_X_MAX + 1):
            if avenue:
                canvas.set(x, CITY_Y, center - 1, B.ROAD_LINE)
                canvas.set(x, CITY_Y, center + 1, B.ROAD_LINE)
            elif x % 4 < 2:
                canvas.set(x, CITY_Y, center, B.ROAD_LINE)
            canvas.set(x, CITY_Y, center - half + 1, B.ROAD_LINE_WHITE)
            canvas.set(x, CITY_Y, center + half - 1, B.ROAD_LINE_WHITE)


def _intersections(canvas: RegionCanvas) -> None:
    """Перекрёстки: чистый асфальт плюс зебры на подходах."""
    for ix in range(IX_MIN, IX_MAX + 2):
        hx = road_half(ix)
        cx = ix * CELL
        if not canvas.covers(cx - hx - SIDEWALK, ROAD_Z_MIN, cx + hx + SIDEWALK, ROAD_Z_MAX):
            continue
        for iz in range(IZ_MIN, IZ_MAX + 2):
            hz = road_half(iz)
            cz = iz * CELL
            if not canvas.covers(cx - hx - 4, cz - hz - 4, cx + hx + 4, cz + hz + 4):
                continue
            canvas.fill(cx - hx, CITY_Y, cz - hz, cx + hx, CITY_Y, cz + hz, B.ASPHALT)
            # Зебры: полосы поперёк каждой из четырёх подходящих улиц.
            for offset in (-1, 1):
                zebra_z = cz + offset * (hz + 2)
                for x in range(cx - hx, cx + hx + 1):
                    if (x - cx) % 2 == 0:
                        canvas.set(x, CITY_Y, zebra_z, B.CROSSWALK)
                        canvas.set(x, CITY_Y, zebra_z + offset, B.CROSSWALK)
                zebra_x = cx + offset * (hx + 2)
                for z in range(cz - hz, cz + hz + 1):
                    if (z - cz) % 2 == 0:
                        canvas.set(zebra_x, CITY_Y, z, B.CROSSWALK)
                        canvas.set(zebra_x + offset, CITY_Y, z, B.CROSSWALK)
            # Угловые фонари на тротуарах.
            for sx in (-1, 1):
                for sz in (-1, 1):
                    lx = cx + sx * (hx + 2)
                    lz = cz + sz * (hz + 2)
                    canvas.fill(lx, CITY_Y + 1, lz, lx, CITY_Y + 4, lz, B.CONCRETE_BLACK)
                    canvas.set(lx, CITY_Y + 5, lz, B.LAMP)
            _street_signs(canvas, ix, iz, cx, cz, hx, hz)


def _street_signs(canvas: RegionCanvas, ix: int, iz: int,
                  cx: int, cz: int, hx: int, hz: int) -> None:
    """Указатели с названиями улиц на северо-западном фонаре перекрёстка.

    Табличка называет ту улицу, которую пересекаешь: едешь вдоль X —
    читаешь имя поперечной линии, и наоборот. Как на настоящем перекрёстке.
    """
    lx = cx - (hx + 2)
    lz = cz - (hz + 2)
    y = CITY_Y + 4
    glow = is_avenue(ix) or is_avenue(iz)
    canvas.sign(lx - 1, y, lz, B.SIGN_WALL.format(f="west"),
                _sign_lines(street_name(ix, vertical=True)),
                color="white", glowing=glow)
    canvas.sign(lx, y, lz - 1, B.SIGN_WALL.format(f="north"),
                _sign_lines(street_name(iz, vertical=False)),
                color="white", glowing=glow)


def _sign_lines(name: str) -> list[str]:
    """Название на табличке: не больше 15 символов в строке."""
    words = name.split()
    lines: list[str] = [""]
    for word in words:
        if not lines[-1]:
            lines[-1] = word
        elif len(lines[-1]) + 1 + len(word) <= 15:
            lines[-1] += " " + word
        else:
            lines.append(word)
    return lines[:4]


# ---------------------------------------------------------------------------
#  Эстакада
# ---------------------------------------------------------------------------

def draw_freeway(canvas: RegionCanvas) -> None:
    """Эстакада над улицей: подъём, ровный участок на опорах, спуск."""
    x0, x1 = FREEWAY_X - FREEWAY_HALF, FREEWAY_X + FREEWAY_HALF
    if not canvas.covers(x0 - 2, ROAD_Z_MIN, x1 + 2, ROAD_Z_MAX):
        return
    ramp = 40
    rise_end = ROAD_Z_MIN + ramp
    fall_start = ROAD_Z_MAX - ramp

    for z in range(ROAD_Z_MIN, ROAD_Z_MAX + 1):
        if z < rise_end:
            progress = (z - ROAD_Z_MIN) / ramp
            y = int(CITY_Y + 1 + (FREEWAY_Y - CITY_Y - 1) * progress)
        elif z > fall_start:
            progress = (ROAD_Z_MAX - z) / ramp
            y = int(CITY_Y + 1 + (FREEWAY_Y - CITY_Y - 1) * progress)
        else:
            y = FREEWAY_Y

        canvas.fill(x0, y, z, x1, y, z, B.ASPHALT_WORN)
        canvas.fill(x0, y + 1, z, x1, y + 5, z, B.AIR)
        canvas.set(FREEWAY_X, y, z, B.ROAD_LINE if z % 4 < 2 else B.ASPHALT_WORN)
        # Отбойники.
        canvas.set(x0, y + 1, z, B.wall("stone_brick"))
        canvas.set(x1, y + 1, z, B.wall("stone_brick"))
        # Опоры и подпорка полотна.
        canvas.fill(x0, y - 1, z, x1, y - 1, z, B.CONCRETE_GRAY)
        if z % 8 == 0 and y > CITY_Y + 3:
            for px in (x0 + 2, x1 - 2):
                canvas.fill(px, CITY_Y + 1, z, px, y - 2, z, B.CONCRETE_GRAY)
        if z % 16 == 0 and y == FREEWAY_Y:
            canvas.fill(x0 + 1, y + 1, z, x0 + 1, y + 3, z, B.CONCRETE_BLACK)
            canvas.set(x0 + 1, y + 4, z, B.LAMP)
        # Под эстакадой светло: лампы в пролёте, чтобы там не было тёмной дыры.
        if z % 6 == 0 and y > CITY_Y + 4:
            for lx in (x0 + 3, x1 - 3):
                canvas.set(lx, y - 2, z, B.LIGHT)


# ---------------------------------------------------------------------------
#  Пирс
# ---------------------------------------------------------------------------

def draw_pier(canvas: RegionCanvas, terrain: Terrain) -> None:
    half = 5
    x_lo, x_hi = min(PIER_X_FROM, PIER_X_TO), max(PIER_X_FROM, PIER_X_TO)
    if not canvas.covers(x_lo - 2, PIER_Z - half - 2, x_hi + 2, PIER_Z + half + 2):
        return
    deck = SEA_LEVEL + 2
    for x in range(x_lo, x_hi + 1):
        canvas.fill(x, deck, PIER_Z - half, x, deck, PIER_Z + half, B.SPRUCE_PLANKS)
        # Над палубой — открытое небо: там, где пирс проходит сквозь пляж,
        # вырезаем песок до поверхности, иначе он висел над проходом и
        # осыпался от первого касания.
        for z in range(PIER_Z - half, PIER_Z + half + 1):
            top = max(deck + 3, terrain.height(x, z))
            canvas.column(x, z, deck + 1, top, B.AIR)
        canvas.set(x, deck + 1, PIER_Z - half, B.fence("spruce"))
        canvas.set(x, deck + 1, PIER_Z + half, B.fence("spruce"))
        if x % 6 == 0:
            for pz in (PIER_Z - half + 1, PIER_Z + half - 1):
                floor = terrain.height(x, pz)
                canvas.column(x, pz, floor, deck - 1, B.SPRUCE_LOG)
        if x % 12 == 0:
            canvas.set(x, deck + 2, PIER_Z - half, B.LANTERN)
            canvas.set(x, deck + 2, PIER_Z + half, B.LANTERN)
    # Кафе на дальнем конце пирса.
    cafe_x0 = x_lo + 2
    cafe_x1 = cafe_x0 + 10
    canvas.fill(cafe_x0, deck, PIER_Z - half, cafe_x1, deck, PIER_Z + half, B.SPRUCE_PLANKS)
    canvas.outline(cafe_x0, deck + 1, PIER_Z - half, cafe_x1, deck + 4, PIER_Z + half,
                   B.BIRCH_PLANKS)
    canvas.fill(cafe_x0, deck + 5, PIER_Z - half, cafe_x1, deck + 5, PIER_Z + half,
                B.CONCRETE_RED)
    for z in range(PIER_Z - half + 1, PIER_Z + half):
        canvas.set(cafe_x1, deck + 2, z, B.GLASS)
        canvas.set(cafe_x1, deck + 3, z, B.GLASS)
    canvas.fill(cafe_x1, deck + 1, PIER_Z, cafe_x1, deck + 2, PIER_Z, B.AIR)
    canvas.set(cafe_x0 + 2, deck + 1, PIER_Z, B.BARREL)
    canvas.set(cafe_x0 + 3, deck + 1, PIER_Z, B.slab("spruce", top=False))
    canvas.set(cafe_x0 + 5, deck + 4, PIER_Z, B.LIGHT)
    canvas.sign(cafe_x1 + 1, deck + 4, PIER_Z, B.SIGN_WALL.format(f="east"),
                ["КАФЕ", "НА ПИРСЕ"], color="blue", glowing=True)


# ---------------------------------------------------------------------------
#  Тоннели метро
# ---------------------------------------------------------------------------

def draw_metro_tunnels(canvas: RegionCanvas, plan: Plan) -> None:
    stations = sorted(plan.metro_stations)
    for index in range(len(stations) - 1):
        x0, z0, _ = stations[index]
        x1, z1, _ = stations[index + 1]
        _tunnel_segment(canvas, x0, z0, x1, z0)   # сначала по X
        _tunnel_segment(canvas, x1, z0, x1, z1)   # затем по Z


def _tunnel_segment(canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int) -> None:
    if x0 == x1 and z0 == z1:
        return
    horizontal = z0 == z1
    lo, hi = (min(x0, x1), max(x0, x1)) if horizontal else (min(z0, z1), max(z0, z1))
    if horizontal:
        if not canvas.covers(lo, z0 - 3, hi, z0 + 3):
            return
        canvas.fill(lo, METRO_Y, z0 - 2, hi, METRO_Y + 5, z0 + 2, B.AIR)
        canvas.fill(lo, METRO_Y - 1, z0 - 3, hi, METRO_Y - 1, z0 + 3, B.SIDEWALK_EDGE)
        canvas.fill(lo, METRO_Y + 6, z0 - 3, hi, METRO_Y + 6, z0 + 3, B.STONE)
        canvas.outline(lo, METRO_Y, z0 - 3, hi, METRO_Y + 5, z0 + 3, B.STONE)
        canvas.fill(lo, METRO_Y, z0, hi, METRO_Y, z0, B.GRAVEL)
        for x in range(lo, hi + 1):
            canvas.set(x, METRO_Y + 1, z0,
                       B.POWERED_RAIL_EW if x % 8 == 0 else B.RAIL_EW)
            if x % 8 == 0:
                canvas.set(x, METRO_Y, z0, B.REDSTONE_BLOCK)
            if x % 12 == 0:
                canvas.set(x, METRO_Y + 5, z0 - 2, B.LAMP)
    else:
        if not canvas.covers(x0 - 3, lo, x0 + 3, hi):
            return
        canvas.fill(x0 - 2, METRO_Y, lo, x0 + 2, METRO_Y + 5, hi, B.AIR)
        canvas.fill(x0 - 3, METRO_Y - 1, lo, x0 + 3, METRO_Y - 1, hi, B.SIDEWALK_EDGE)
        canvas.fill(x0 - 3, METRO_Y + 6, lo, x0 + 3, METRO_Y + 6, hi, B.STONE)
        canvas.outline(x0 - 3, METRO_Y, lo, x0 + 3, METRO_Y + 5, hi, B.STONE)
        canvas.fill(x0, METRO_Y, lo, x0, METRO_Y, hi, B.GRAVEL)
        for z in range(lo, hi + 1):
            canvas.set(x0, METRO_Y + 1, z,
                       B.POWERED_RAIL_NS if z % 8 == 0 else B.RAIL_NS)
            if z % 8 == 0:
                canvas.set(x0, METRO_Y, z, B.REDSTONE_BLOCK)
            if z % 12 == 0:
                canvas.set(x0 - 2, METRO_Y + 5, z, B.LAMP)


# ---------------------------------------------------------------------------
#  Автовокзал у точки появления
# ---------------------------------------------------------------------------

def draw_bus_stop(canvas: RegionCanvas) -> None:
    """Навес остановки и вводные таблички там, где игрок появляется."""
    from .plan import SPAWN

    sx, _, sz = SPAWN
    if not canvas.covers(sx - 4, sz - 8, sx + 4, sz + 8):
        return
    # Расширяем тротуар под остановку.
    canvas.fill(sx - 2, CITY_Y, sz - 6, sx + 2, CITY_Y, sz + 6, B.SIDEWALK)
    canvas.fill(sx - 2, CITY_Y + 1, sz - 6, sx + 2, CITY_Y + 5, sz + 6, B.AIR)
    # Навес на столбах.
    for pz in (sz - 5, sz + 5):
        for px in (sx - 2, sx + 2):
            canvas.fill(px, CITY_Y + 1, pz, px, CITY_Y + 3, pz, B.IRON_BARS)
    canvas.fill(sx - 2, CITY_Y + 4, sz - 5, sx + 2, CITY_Y + 4, sz + 5, B.CONCRETE_BLUE)
    canvas.fill(sx - 2, CITY_Y + 3, sz - 5, sx - 2, CITY_Y + 3, sz + 5, B.GLASS)
    canvas.set(sx, CITY_Y + 3, sz, B.LIGHT)
    # Лавка.
    for z in range(sz - 3, sz + 4):
        canvas.set(sx - 1, CITY_Y + 1, z, B.stairs("spruce", facing="east"))
    # Вводные таблички на столбиках.
    for dz in (-4, -2, 0, 2):
        canvas.set(sx + 1, CITY_Y + 1, sz + dz, B.fence("spruce"))
    canvas.sign(sx + 1, CITY_Y + 2, sz - 4, B.SIGN_STANDING.format(r=0),
                ["LOS SANTOS", "автовокзал", "добро", "пожаловать"],
                color="white", glowing=True)
    canvas.sign(sx + 1, CITY_Y + 2, sz - 2, B.SIGN_STANDING.format(r=0),
                ["С чего начать:", "1. SIM в телефон", "2. /work", "3. банк, магазины"],
                color="blue", glowing=False)
    canvas.sign(sx + 1, CITY_Y + 2, sz, B.SIGN_STANDING.format(r=0),
                ["Ты в центре", "Пляж — запад", "Холмы — север",
                 "Пригород — юг"], color="green", glowing=False)
    canvas.sign(sx + 1, CITY_Y + 2, sz + 2, B.SIGN_STANDING.format(r=0),
                ["Свободные", "участки:", "ищи табличку", "ПРОДАЁТСЯ"],
                color="red", glowing=False)
