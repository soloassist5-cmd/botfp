"""
Планировка города: сетка улиц, кварталы, назначение участков.

План — чистая функция от сида: одинаковый сид даёт одинаковый город,
а каждый регион карты можно рисовать независимо от остальных.
"""
from __future__ import annotations

import random
from dataclasses import dataclass, field

CELL = 64          # шаг сетки улиц
SIDEWALK = 3       # ширина тротуара
CITY_Y = 68        # уровень асфальта

# Диапазон кварталов: x от -512 до 704, z от -448 до 768.
IX_MIN, IX_MAX = -8, 10
IZ_MIN, IZ_MAX = -7, 11

# Границы города для выравнивания рельефа (с запасом на тротуары).
CITY_BOUNDS = (IX_MIN * CELL - 32, IZ_MIN * CELL - 32,
               (IX_MAX + 1) * CELL + 32, (IZ_MAX + 1) * CELL + 32)

WORLD_BORDER = 2048          # сторона квадрата карты
# Точка появления — на тротуаре проспекта, чтобы игрок не оказался внутри дома.
SPAWN = (11, CITY_Y + 1, 20)

# Эстакада идёт по линии улицы ix = 7.
FREEWAY_X = 7 * CELL
FREEWAY_Y = CITY_Y + 9
FREEWAY_HALF = 6

PIER_Z = 32                   # пирс уходит в океан на этой широте
PIER_X_FROM, PIER_X_TO = -516, -676

METRO_Y = 48                  # уровень платформ метро


def road_half(index: int) -> int:
    """Половина ширины улицы: каждая третья — проспект."""
    return 8 if index % 3 == 0 else 5


def is_avenue(index: int) -> bool:
    return index % 3 == 0


# Названия улиц.
#
# По городу без названий ходить неприятно: все перекрёстки на одно лицо.
# Проспекты (каждая третья линия) носят имена, остальные — номера, как
# в настоящем Лос-Анджелесе: улицы с номерами, бульвары с именами.
AVENUE_X = {
    -6: "ОКЕАНСКИЙ ПР.", -3: "ПРОСПЕКТ ПАЛЬМ", 0: "ЦЕНТРАЛЬНЫЙ ПР.",
    3: "ПРОСПЕКТ МЕЧТЫ", 6: "ВОСТОЧНЫЙ ПР.", 9: "ПРОМЗОНА",
}
AVENUE_Z = {
    -6: "СЕВЕРНЫЙ Б-Р", -3: "БУЛЬВАР ЗАКАТА", 0: "БУЛЬВАР ЗВЁЗД",
    3: "ПАЛЬМОВЫЙ Б-Р", 6: "ЮЖНЫЙ Б-Р", 9: "ПОРТОВЫЙ Б-Р",
}


# Несколько обычных улиц тоже с именем. Алмазная — бывший 19-й проезд в
# пригороде: почти одни частные дома, и на ней есть дом 33.
NAMED_X: dict[int, str] = {}
NAMED_Z: dict[int, str] = {IZ_MIN + 18: "АЛМАЗНАЯ УЛИЦА"}


def street_name(index: int, vertical: bool) -> str:
    """Название линии сетки: проспект по имени, остальные — по номеру."""
    named = (NAMED_X if vertical else NAMED_Z).get(index)
    if named:
        return named
    if is_avenue(index):
        names = AVENUE_X if vertical else AVENUE_Z
        known = names.get(index)
        if known:
            return known
    number = index - (IX_MIN if vertical else IZ_MIN) + 1
    return f"{number}-Я УЛИЦА" if vertical else f"{number}-Й ПРОЕЗД"


def cell_rect(ix: int, iz: int) -> tuple[int, int, int, int]:
    """Застраиваемый прямоугольник квартала (без улиц и тротуаров)."""
    x0 = ix * CELL + road_half(ix) + 1 + SIDEWALK
    x1 = (ix + 1) * CELL - road_half(ix + 1) - 1 - SIDEWALK
    z0 = iz * CELL + road_half(iz) + 1 + SIDEWALK
    z1 = (iz + 1) * CELL - road_half(iz + 1) - 1 - SIDEWALK
    return x0, z0, x1, z1


def district(ix: int, iz: int) -> str:
    if -2 <= ix <= 3 and -2 <= iz <= 3:
        return "downtown"
    if ix <= -6:
        return "industrial" if iz >= 6 else "beach"
    if iz <= -5:
        return "hills"
    if iz >= 7:
        return "suburbs"
    if ix >= 7:
        return "eastside"
    return "midtown"


def front_facing(ix: int, iz: int) -> str:
    """Куда смотрит вход: предпочтительно на проспект."""
    if is_avenue(iz):
        return "north"
    if is_avenue(iz + 1):
        return "south"
    if is_avenue(ix):
        return "west"
    if is_avenue(ix + 1):
        return "east"
    return "south"


@dataclass
class Lot:
    ix: int
    iz: int
    x0: int
    z0: int
    x1: int
    z1: int
    kind: str
    district: str
    facing: str
    seed: int
    label: str = ""
    # Для лавок: кто стоит за прилавком. Вывеска над дверью берётся отсюда же.
    shop_role: str = ""
    shop_title: str = ""
    # Адрес «улица, номер» для табличек на домах и справки.
    address: str = ""
    # Стороны света, по которым участок граничит с улицей (а не с соседом).
    street_sides: tuple[str, ...] = ()
    # Уровень земли участка: в городе — асфальт, в Береговом — своя терраса.
    ground: int = CITY_Y

    @property
    def width(self) -> int:
        return self.x1 - self.x0 + 1

    @property
    def depth(self) -> int:
        return self.z1 - self.z0 + 1

    def center(self) -> tuple[int, int]:
        return (self.x0 + self.x1) // 2, (self.z0 + self.z1) // 2


@dataclass
class Plan:
    seed: int
    lots: list[Lot] = field(default_factory=list)
    npc_spots: list[dict] = field(default_factory=list)
    metro_stations: list[tuple[int, int, str]] = field(default_factory=list)
    landmark_points: list[dict] = field(default_factory=list)

    def lots_touching(self, x0: int, z0: int, x1: int, z1: int) -> list[Lot]:
        return [lot for lot in self.lots
                if not (lot.x1 < x0 or lot.x0 > x1 or lot.z1 < z0 or lot.z0 > z1)]


# Ключевые объекты города: квартал -> тип застройки и вывеска.
LANDMARKS: dict[tuple[int, int], tuple[str, str]] = {
    (0, 0): ("tower", "STARK TOWER"),
    (1, 0): ("tower", "MERIDIAN"),
    (0, 1): ("tower", "SUNSET PLAZA"),
    (1, 1): ("mall", "ТОРГОВЫЙ ЦЕНТР"),
    (-1, 0): ("city_hall", "МЭРИЯ"),
    (-1, 1): ("bank", "ГОРОДСКОЙ БАНК"),
    (2, 0): ("police", "ПОЛИЦИЯ"),
    (2, 1): ("hospital", "БОЛЬНИЦА"),
    (-1, -1): ("parking", "ПАРКОВКА"),
    (0, -1): ("office", "ОФИСЫ"),
    (1, -1): ("office", "IT-ЦЕНТР"),
    (2, -1): ("apartment", "ДОХОДНЫЙ ДОМ"),
    (3, 0): ("dealership", "АВТОСАЛОН"),
    (3, 1): ("fire_station", "ПОЖАРНАЯ ЧАСТЬ"),
    (2, 2): ("park", "ГОРОДСКОЙ ПАРК"),
    (0, 2): ("metro", "МЕТРО «ЦЕНТР»"),
    (-2, 0): ("phone_shop", "САЛОН СВЯЗИ"),
    (-2, 1): ("gun_shop", "ОРУЖЕЙНЫЙ МАГАЗИН"),
    (-2, -1): ("diner", "ЗАКУСОЧНАЯ"),
    (3, -1): ("office", "БИЗНЕС-ЦЕНТР"),
    (-2, 2): ("apartment", "ЖИЛОЙ ДОМ"),
    (3, 2): ("shop", "МИНИ-МАРКЕТ"),
    (-1, 2): ("shop", "ПРОДУКТЫ"),
    (1, 2): ("shop", "ЭЛЕКТРОНИКА"),
    (-2, 3): ("club", "НОЧНОЙ КЛУБ"),
    (0, 3): ("shop", "АПТЕКА"),
    (1, 3): ("apartment", "ЖИЛОЙ ДОМ"),
    (2, 3): ("park", "СКВЕР"),
    (3, 3): ("office", "СТРАХОВАЯ"),
    (-1, 3): ("diner", "КОФЕЙНЯ"),
    # За пределами центра.
    (5, 3): ("gas", "АЗС"),
    (-4, 6): ("gas", "АЗС"),
    (6, 2): ("construction", "СТРОЙПЛОЩАДКА"),
    (4, 1): ("diner", "ФАСТФУД"),
    (5, -2): ("metro", "МЕТРО «ВОСТОК»"),
    (-5, 4): ("metro", "МЕТРО «ПЛЯЖ»"),
    (-5, 2): ("club", "ПЛЯЖНЫЙ БАР"),
    (8, 4): ("construction", "НОВЫЙ КВАРТАЛ"),
    (-7, 8): ("warehouse", "СКЛАД №1"),
    (-7, 9): ("warehouse", "СКЛАД №2"),
    (-6, 8): ("warehouse", "ЛОГИСТИКА"),
    (4, 8): ("shop", "СТРОЙМАТЕРИАЛЫ"),
    (-3, 9): ("park", "СПОРТПЛОЩАДКА"),
    (6, -4): ("park", "СМОТРОВАЯ ПЛОЩАДКА"),
    # Большие парки: целый квартал зелени с прудом и аллеями.
    (-4, 1): ("park", "ЦЕНТРАЛЬНЫЙ ПАРК"),
    (5, 6): ("park", "ПАРК «ДУБРАВА»"),
    (-3, -4): ("park", "ПАРК НА ХОЛМАХ"),
}

# Пункты выдачи заказов маркетплейса: в каждом районе, чтобы за посылкой
# не приходилось ехать через весь город. Пункт занимает один участок
# в квартале, остальное застраивается как обычно.
PICKUP_CELLS = [(0, -2), (-3, 0), (4, 0), (-1, 4), (3, 5), (-4, 7), (7, -2),
                (-5, -5), (-6, 3), (8, 7)]

# Чем застраивается район, если это не ключевой объект.
# Вес «empty» задаёт долю свободных участков под постройки игроков.
DISTRICT_MIX: dict[str, list[tuple[str, int]]] = {
    "downtown": [("office", 4), ("shop", 4), ("apartment", 3), ("parking", 1), ("empty", 1),
                 ("square", 1)],
    "midtown": [("shop", 3), ("apartment", 3), ("house", 2), ("rowhouse", 2), ("office", 1),
                ("empty", 3), ("square", 1)],
    "suburbs": [("house", 7), ("rowhouse", 1), ("empty", 3), ("square", 1)],
    "hills": [("villa", 6), ("empty", 2), ("square", 1)],
    "beach": [("house", 3), ("shop", 2), ("apartment", 1), ("rowhouse", 1), ("empty", 3),
              ("square", 1)],
    "industrial": [("warehouse", 5), ("parking", 1), ("empty", 2)],
    "eastside": [("house", 3), ("rowhouse", 1), ("warehouse", 1), ("shop", 1), ("empty", 4),
                 ("square", 1)],
}

# Как делится квартал: сколько рядов и какой ширины участки в ряду.
# Два ряда стоят спиной друг к другу и смотрят каждый на свою улицу.
SUBDIVIDE: dict[str, tuple[int, int, int]] = {
    #            рядов, ширина от, до
    "downtown": (2, 20, 30),
    "midtown": (2, 14, 24),
    "suburbs": (2, 12, 17),
    "hills": (2, 20, 26),
    "beach": (2, 12, 20),
    "industrial": (1, 22, 48),
    "eastside": (2, 12, 22),
}

# Минимальный размер участка (вдоль улицы, вглубь) под тип застройки.
MIN_SIZE = {
    "office": (20, 18), "apartment": (20, 18), "rowhouse": (20, 18),
    "warehouse": (20, 18), "parking": (20, 18), "villa": (18, 18),
    "house": (12, 16), "shop": (12, 14), "pickup": (12, 14),
}

# Где в зданиях стоят NPC: тип застройки -> роль и вывеска.
NPC_ROLES = {
    "mall": [("trader_clothes", "Продавец одежды"), ("trader_food", "Продавец еды"),
             ("trader_tech", "Продавец техники"), ("security", "Охранник ТЦ")],
    "bank": [("banker", "Банкир")],
    "police": [("cop", "Полицейский"), ("cop", "Дежурный")],
    "hospital": [("medic", "Врач")],
    # Оружейная: мастер с верстаками и сырьём, два продавца стволов и патроны.
    "gun_shop": [("gunsmith", "Мастер-оружейник"), ("arms_dealer", "Пистолеты и ПП"),
                 ("rifle_dealer", "Винтовки"), ("ammo_seller", "Патроны")],
    "phone_shop": [("phone_seller", "Продавец связи")],
    "dealership": [("car_dealer", "Автодилер")],
    "city_hall": [("clerk", "Чиновник"), ("realtor", "Риелтор")],
    "diner": [("cook", "Повар")],
    "shop": [("shopkeeper", "Продавец")],  # заменяется на профиль лавки, см. SHOP_KINDS
    "club": [("bartender", "Бармен"), ("security", "Охранник")],
    "gas": [("fuel_seller", "Заправщик")],
    "fire_station": [("firefighter", "Пожарный")],
    "warehouse": [("foreman", "Кладовщик")],
    "construction": [("builder", "Бригадир")],
    "apartment": [("realtor", "Управдом")],
}


# Профили уличных лавок.
#
# Магазинов в городе больше полусотни, и если у всех одинаковый продавец
# с одинаковым прилавком, улица выглядит декорацией. Поэтому каждой лавке
# достаётся свой профиль: вывеска над дверью и ассортимент внутри.
SHOP_KINDS = [
    ("trader_food", "Продавец продуктов", "ПРОДУКТЫ", 4),
    ("shopkeeper", "Продавец", "МАГАЗИН", 3),
    ("trader_clothes", "Продавец одежды", "ОДЕЖДА", 2),
    ("trader_tech", "Продавец техники", "ЭЛЕКТРОНИКА", 2),
    ("cook", "Повар", "КАФЕ", 1),
    ("medic", "Аптекарь", "АПТЕКА", 1),
    # Строймаркеты: стройматериалы, двери, окна, свет и мебель. Раньше всё
    # это продавали в «Магазине у дома», а настоящий был один, на окраине.
    ("builder", "Продавец стройматериалов", "СТРОЙМАРКЕТ", 2),
]


# Именные магазины-ориентиры: у них вывеска задана в LANDMARKS, и продавец
# должен ей соответствовать. Раньше он выбирался случайно, и в одной из
# «Электроник» за прилавком стоял продавец продуктов.
LABEL_PROFILE = {
    "ЭЛЕКТРОНИКА": ("trader_tech", "Продавец техники"),
    "ПРОДУКТЫ": ("trader_food", "Продавец продуктов"),
    "МИНИ-МАРКЕТ": ("shopkeeper", "Продавец"),
    "МАГАЗИН": ("shopkeeper", "Продавец"),
    "АПТЕКА": ("medic", "Аптекарь"),
    "ОДЕЖДА": ("trader_clothes", "Продавец одежды"),
    "КАФЕ": ("cook", "Повар"),
    "СТРОЙМАТЕРИАЛЫ": ("builder", "Продавец стройматериалов"),
    "СТРОЙМАРКЕТ": ("builder", "Продавец стройматериалов"),
}


def shop_profile(rng: random.Random) -> tuple[str, str, str]:
    """Профиль лавки: кто за прилавком и что написано на вывеске."""
    total = sum(weight for _, _, _, weight in SHOP_KINDS)
    roll = rng.randrange(total)
    for role, title, label, weight in SHOP_KINDS:
        roll -= weight
        if roll < 0:
            return role, title, label
    role, title, label, _ = SHOP_KINDS[0]
    return role, title, label


def pick_weighted(rng: random.Random, options: list[tuple[str, int]]) -> str:
    total = sum(weight for _, weight in options)
    roll = rng.randrange(total)
    for kind, weight in options:
        roll -= weight
        if roll < 0:
            return kind
    return options[-1][0]


def _split(length: int, lo: int, hi: int, rng: random.Random) -> list[int]:
    """Разрезать отрезок на куски от lo до hi; остаток уходит в последний кусок."""
    sizes: list[int] = []
    left = length
    while left > 0:
        if left <= hi:
            sizes.append(left)
            break
        size = rng.randint(lo, hi)
        if left - size < lo:
            size = left - lo if left - lo >= lo else left
        sizes.append(size)
        left -= size
    if len(sizes) > 1 and sizes[-1] < lo:
        sizes[-2] += sizes.pop()
    return sizes


def subdivide(x0: int, z0: int, x1: int, z1: int, zone: str, facing: str,
              rng: random.Random) -> list[tuple[int, int, int, int, str]]:
    """Нарезать квартал на участки: ряды вдоль улиц, участки разной ширины."""
    rows, lo, hi = SUBDIVIDE[zone]
    along_x = facing in ("north", "south")
    result = []
    if along_x:
        depth = z1 - z0 + 1
        if rows == 2:
            mid = z0 + depth // 2 - 1
            strips = [(z0, mid, "north"), (mid + 1, z1, "south")]
        else:
            strips = [(z0, z1, facing)]
        for a, b, face in strips:
            x = x0
            for size in _split(x1 - x0 + 1, lo, hi, rng):
                result.append((x, a, x + size - 1, b, face))
                x += size
    else:
        width = x1 - x0 + 1
        if rows == 2:
            mid = x0 + width // 2 - 1
            strips = [(x0, mid, "west"), (mid + 1, x1, "east")]
        else:
            strips = [(x0, x1, facing)]
        for a, b, face in strips:
            z = z0
            for size in _split(z1 - z0 + 1, lo, hi, rng):
                result.append((a, z, b, z + size - 1, face))
                z += size
    return result


def fits(kind: str, lot_x0: int, lot_z0: int, lot_x1: int, lot_z1: int, facing: str) -> bool:
    along = lot_x1 - lot_x0 + 1 if facing in ("north", "south") else lot_z1 - lot_z0 + 1
    deep = lot_z1 - lot_z0 + 1 if facing in ("north", "south") else lot_x1 - lot_x0 + 1
    need = MIN_SIZE.get(kind, (1, 1))
    return along >= need[0] and deep >= need[1]


def build_plan(seed: int) -> Plan:
    plan = Plan(seed=seed)
    for ix in range(IX_MIN, IX_MAX + 1):
        for iz in range(IZ_MIN, IZ_MAX + 1):
            x0, z0, x1, z1 = cell_rect(ix, iz)
            if x1 - x0 < 16 or z1 - z0 < 16:
                continue
            zone = district(ix, iz)
            rng = random.Random((seed * 1000003) ^ (ix * 73856093) ^ (iz * 19349663))
            landmark = LANDMARKS.get((ix, iz))
            cell_side = {"north": z0, "south": z1, "west": x0, "east": x1}
            if landmark:
                kind, label = landmark
                pieces = [(x0, z0, x1, z1, front_facing(ix, iz), kind, label)]
            else:
                # Центр иногда застраивается одним зданием на весь квартал.
                if zone == "downtown" and rng.random() < 0.35:
                    parts = [(x0, z0, x1, z1, front_facing(ix, iz))]
                else:
                    parts = subdivide(x0, z0, x1, z1, zone, front_facing(ix, iz), rng)
                pieces = []
                pickup_here = (ix, iz) in PICKUP_CELLS
                for index, (a, b, c, d, face) in enumerate(parts):
                    if pickup_here and index == len(parts) // 4 and fits("pickup", a, b, c, d,
                                                                      face):
                        pieces.append((a, b, c, d, face, "pickup", "ПУНКТ ВЫДАЧИ"))
                        pickup_here = False
                        continue
                    kind = pick_weighted(rng, DISTRICT_MIX[zone])
                    if kind == "square" and rng.random() < 0.5:
                        kind = "empty"
                    for fallback in (kind, "house", "shop", "square"):
                        if fits(fallback, a, b, c, d, face) and \
                                (fallback != "house" or zone not in ("downtown", "industrial")):
                            kind = fallback
                            break
                    pieces.append((a, b, c, d, face, kind, ""))
                if pickup_here and pieces:
                    a, b, c, d, face, _, _ = pieces[0]
                    pieces[0] = (a, b, c, d, face, "pickup", "ПУНКТ ВЫДАЧИ")

            for a, b, c, d, face, kind, label in pieces:
                # Эстакада проходит над улицей ix=7: не ставим там высокие здания.
                if kind in ("tower", "office") and ix in (6, 7) and abs(FREEWAY_X - a) < 80:
                    kind = "shop"
                sides = tuple(side for side, edge in cell_side.items()
                              if {"north": b, "south": d, "west": a, "east": c}[side] == edge)
                lot = Lot(ix=ix, iz=iz, x0=a, z0=b, x1=c, z1=d, kind=kind,
                          district=zone, facing=face,
                          seed=rng.randrange(1 << 30), label=label, street_sides=sides)
                if kind == "shop":
                    # Полсотни одинаковых лавок — это декорация, а не улица:
                    # каждой даём свой профиль, вывеску и продавца.
                    # Отдельный поток случайности: у соседних клеток rng-состояния
                    # похожи, и на одном общем потоке половина улицы выходила аптеками.
                    lot.shop_role, lot.shop_title, shop_label = shop_profile(
                        random.Random(lot.seed * 2654435761 % (1 << 61)))
                    if not lot.label:
                        lot.label = shop_label
                    elif lot.label in LABEL_PROFILE:
                        lot.shop_role, lot.shop_title = LABEL_PROFILE[lot.label]
                plan.lots.append(lot)
                if kind == "metro":
                    cx, cz = lot.center()
                    plan.metro_stations.append((cx, cz, label or "МЕТРО"))

    _assign_addresses(plan)
    # Прибрежные районы за городской сеткой: адреса у них свои.
    from . import estates
    plan.lots += estates.layout(seed).lots
    _add_npc_spots(plan)
    plan.landmark_points = [
        {"name": "Автовокзал", "x": SPAWN[0], "y": SPAWN[1], "z": SPAWN[2]},
        {"name": "Пирс", "x": PIER_X_TO + 8, "y": CITY_Y - 2, "z": PIER_Z},
    ]
    for area, title in estates.DISTRICTS.items():
        first = next(l for l in plan.lots if l.district == area)
        x, y, z = estates.entrance_point(first)
        plan.landmark_points.append({"name": title, "x": x, "y": y, "z": z})
    return plan


def facing_street(lot: Lot) -> tuple[str, int, bool]:
    """Улица, на которую смотрит участок: (название, индекс линии, вертикальная ли)."""
    if lot.facing == "north":
        return street_name(lot.iz, False), lot.iz, False
    if lot.facing == "south":
        return street_name(lot.iz + 1, False), lot.iz + 1, False
    if lot.facing == "west":
        return street_name(lot.ix, True), lot.ix, True
    return street_name(lot.ix + 1, True), lot.ix + 1, True


def _assign_addresses(plan: Plan) -> None:
    """
    Номера домов по улице: чётные по одной стороне, нечётные по другой,
    по возрастанию от запада к востоку и с севера на юг — как в жизни.
    """
    by_street: dict[tuple[bool, int], list[Lot]] = {}
    for lot in plan.lots:
        if lot.kind in ("park", "square", "metro", "construction"):
            continue
        _, index, vertical = facing_street(lot)
        by_street.setdefault((vertical, index), []).append(lot)
    for (vertical, index), lots in by_street.items():
        lots.sort(key=lambda l: (l.z0 if vertical else l.x0))
        odd, even = 1, 2
        for lot in lots:
            name = street_name(index, vertical).lower()
            if not name[0].isdigit():
                name = name[0].upper() + name[1:]
            # Сторона улицы: север/запад — нечётные, юг/восток — чётные.
            if lot.facing in ("south", "east"):
                lot.address, odd = f"{name}, {odd}", odd + 2
            else:
                lot.address, even = f"{name}, {even}", even + 2


def _add_npc_spots(plan: Plan) -> None:
    """
    Точки спавна NPC внутри зданий — по ним датапак расставит жителей.

    Место считает тот же код, что строит здание (buildings.npc_spots):
    продавец стоит за своим прилавком, а не в стене или на улице.
    """
    from . import buildings  # здания зависят от плана, поэтому импорт здесь

    for lot in plan.lots:
        roles = NPC_ROLES.get(lot.kind)
        if not roles:
            continue
        # Кладовщик стоит только на именных складах. Управдом — в каждом жилом
        # доме: он продаёт квартиры своего подъезда, и без него квартиры
        # безымянных домов покупались бы только в мэрии.
        if lot.kind == "warehouse" and not lot.label:
            continue
        rng = random.Random(lot.seed ^ 0x5F5F)
        if lot.kind == "shop" and lot.shop_role:
            roles = [(lot.shop_role, lot.shop_title)]
        spots = buildings.npc_spots(lot, len(roles))
        for (role, title), spot in zip(roles, spots):
            x, z, yaw = spot[:3]
            plan.npc_spots.append({
                "x": x, "y": spot[3] if len(spot) > 3 else CITY_Y + 1, "z": z,
                "role": role, "title": title,
                "kind": lot.kind,
                "label": lot.label or lot.kind,
                "rotation": yaw,
                "variant": rng.randrange(6),
            })


def stats(plan: Plan) -> dict[str, int]:
    counts: dict[str, int] = {}
    for lot in plan.lots:
        counts[lot.kind] = counts.get(lot.kind, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: -item[1]))
