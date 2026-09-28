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


def street_name(index: int, vertical: bool) -> str:
    """Название линии сетки: проспект по имени, остальные — по номеру."""
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
    (0, 0): ("tower", "LS TOWER"),
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
}

# Чем застраивается район, если это не ключевой объект.
# Вес «empty» задаёт долю свободных участков под постройки игроков.
DISTRICT_MIX: dict[str, list[tuple[str, int]]] = {
    "downtown": [("office", 5), ("shop", 3), ("apartment", 3), ("parking", 1), ("empty", 2)],
    "midtown": [("shop", 3), ("apartment", 3), ("house", 3), ("office", 2), ("empty", 5)],
    "suburbs": [("house", 6), ("empty", 5), ("shop", 1), ("park", 1)],
    "hills": [("villa", 5), ("empty", 4), ("park", 1)],
    "beach": [("house", 3), ("shop", 2), ("apartment", 2), ("empty", 5), ("park", 1)],
    "industrial": [("warehouse", 5), ("empty", 3), ("parking", 1)],
    "eastside": [("house", 2), ("warehouse", 2), ("empty", 6), ("shop", 1)],
}

# Где в зданиях стоят NPC: тип застройки -> роль и вывеска.
NPC_ROLES = {
    "mall": [("trader_clothes", "Продавец одежды"), ("trader_food", "Продавец еды"),
             ("trader_tech", "Продавец техники"), ("security", "Охранник ТЦ")],
    "bank": [("banker", "Банкир")],
    "police": [("cop", "Полицейский"), ("cop", "Дежурный")],
    "hospital": [("medic", "Врач")],
    "gun_shop": [("gunsmith", "Оружейник")],
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
]


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
            if landmark:
                kind, label = landmark
            else:
                kind = pick_weighted(rng, DISTRICT_MIX[zone])
                label = ""
            # Эстакада проходит над улицей ix=7: не ставим там высокие здания.
            if kind in ("tower", "office") and ix in (6, 7) and abs(FREEWAY_X - x0) < 80:
                kind = "shop"
            lot = Lot(ix=ix, iz=iz, x0=x0, z0=z0, x1=x1, z1=z1, kind=kind,
                      district=zone, facing=front_facing(ix, iz),
                      seed=rng.randrange(1 << 30), label=label)
            if kind == "shop":
                # Полсотни одинаковых лавок — это декорация, а не улица:
                # каждой даём свой профиль, вывеску и продавца.
                # Отдельный поток случайности: у соседних клеток rng-состояния
                # похожи, и на одном общем потоке половина улицы выходила аптеками.
                lot.shop_role, lot.shop_title, shop_label = shop_profile(
                    random.Random(lot.seed * 2654435761 % (1 << 61)))
                if not lot.label:
                    lot.label = shop_label
            plan.lots.append(lot)
            if kind == "metro":
                cx, cz = lot.center()
                plan.metro_stations.append((cx, cz, label or "МЕТРО"))

    _add_npc_spots(plan)
    plan.landmark_points = [
        {"name": "Автовокзал", "x": SPAWN[0], "y": SPAWN[1], "z": SPAWN[2]},
        {"name": "Пирс", "x": PIER_X_TO + 8, "y": CITY_Y - 2, "z": PIER_Z},
    ]
    return plan


def _add_npc_spots(plan: Plan) -> None:
    """Точки спавна NPC внутри зданий — по ним датапак расставит жителей."""
    for lot in plan.lots:
        roles = NPC_ROLES.get(lot.kind)
        if not roles:
            continue
        cx, cz = lot.center()
        rng = random.Random(lot.seed ^ 0x5F5F)
        if lot.kind == "shop" and lot.shop_role:
            roles = [(lot.shop_role, lot.shop_title)]
        for index, (role, title) in enumerate(roles):
            # Раскладываем NPC по фронтальной части здания, у прилавков.
            offset = (index - (len(roles) - 1) / 2) * 4
            if lot.facing in ("north", "south"):
                x = int(cx + offset)
                z = lot.z0 + 4 if lot.facing == "north" else lot.z1 - 4
            else:
                z = int(cz + offset)
                x = lot.x0 + 4 if lot.facing == "west" else lot.x1 - 4
            plan.npc_spots.append({
                "x": x, "y": CITY_Y + 1, "z": z,
                "role": role, "title": title,
                "kind": lot.kind,
                "label": lot.label or lot.kind,
                "rotation": {"north": 180, "south": 0, "west": 90, "east": 270}[lot.facing],
                "variant": rng.randrange(6),
            })


def stats(plan: Plan) -> dict[str, int]:
    counts: dict[str, int] = {}
    for lot in plan.lots:
        counts[lot.kind] = counts.get(lot.kind, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: -item[1]))
