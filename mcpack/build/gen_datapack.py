#!/usr/bin/env python3
"""
Генерация датапака citylife: NPC по городу, сценарий и настройки мира.

Датапак строится из того же плана города, что и сам мир, поэтому NPC
всегда стоят там, где для них построены прилавки.

    python3 build/gen_datapack.py            # в mcpack/datapack/citylife
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "world", "generator"))

from citygen import plan as P  # noqa: E402

# Торговцы — ванильные жители: только им можно задать ассортимент заранее,
# через NBT в команде summon. Easy NPC хранит предложения в своих синхронных
# данных и из summon их не читает (проверено на сервере), поэтому
# человекоподобные NPC ставятся только там, где торговля не нужна.
# Все жители — гуманоиды Easy NPC: выглядят как игроки, а не как ванильные
# «картофелины». Ванильных торговцев больше нет, торговля включается через
# TradingDataSet с типом CUSTOM — без него Easy NPC игнорирует готовые Offers.
NPC_ENTITY = "easy_npc:humanoid"

# Поколение жителей. Меняется, когда меняется состав или расстановка NPC:
# мод убирает жителей прошлых поколений, как только их чанк загрузится,
# а датапак один раз расставляет новых — в том числе в старых мирах.
# Дома, деньги и постройки игроков при этом не трогаются.
NPC_GEN = 2
GEN_TAG = f"lsgen_{NPC_GEN}"

# Роль -> скин из citylife/tools/gen_skins.py.
ROLE_SKIN = {
    "trader_food": "cook",
    "trader_clothes": "shopkeeper",
    "trader_tech": "clerk",
    "phone_seller": "clerk",
    "banker": "banker",
    "gunsmith": "gunsmith",
    "arms_dealer": "gunsmith",
    "rifle_dealer": "police",
    "ammo_seller": "mechanic",
    "car_dealer": "dealer",
    "realtor": "realtor",
    "clerk": "clerk",
    "cook": "cook",
    "shopkeeper": "shopkeeper",
    "bartender": "bartender",
    "fuel_seller": "mechanic",
    "medic": "medic",
    "foreman": "builder",
    "builder": "builder",
    "police": "police",
    "guard": "police",
    "cop": "police",
    "security": "police",
    "firefighter": "firefighter",
    "mechanic": "mechanic",
}
CITIZEN_SKINS = ["citizen_a", "citizen_b", "citizen_c", "citizen_d"]

# Профессия жителя подбирается под роль: от неё зависит одежда и звуки.
ROLE_PROFESSION = {
    "trader_food": "minecraft:farmer",
    "trader_clothes": "minecraft:leatherworker",
    "trader_tech": "minecraft:toolsmith",
    "phone_seller": "minecraft:cartographer",
    "banker": "minecraft:librarian",
    "gunsmith": "minecraft:weaponsmith",
    "arms_dealer": "minecraft:weaponsmith",
    "rifle_dealer": "minecraft:weaponsmith",
    "ammo_seller": "minecraft:armorer",
    "car_dealer": "minecraft:toolsmith",
    "realtor": "minecraft:cartographer",
    "clerk": "minecraft:librarian",
    "cook": "minecraft:butcher",
    "shopkeeper": "minecraft:farmer",
    "bartender": "minecraft:butcher",
    "fuel_seller": "minecraft:armorer",
    "medic": "minecraft:cleric",
    "foreman": "minecraft:mason",
    "builder": "minecraft:mason",
}
# Цены раньше считались монетами Lightman's; теперь деньги свои, рублёвые.
# Сопоставление один к одному, чтобы не пересчитывать все прайсы вручную:
# медь — десятка, железо — сотня, золото — тысяча, дальше пять тысяч.
RUBLES = {
    "copper": "citylife:coin_10",
    "iron": "citylife:banknote_100",
    "gold": "citylife:banknote_1000",
    "emerald": "citylife:banknote_5000",
    "diamond": "citylife:banknote_5000",
    "netherite": "citylife:banknote_5000",
}

# Сколько команд в одном файле: длинные функции неудобно отлаживать.
BATCH_SIZE = 25


def coin(kind: str) -> str:
    """Цена в рублях по старому «монетному» тарифу."""
    return RUBLES[kind]


class Byte(int):
    """Целое, которое в SNBT выводится с суффиксом b (нужно для Count)."""


def stack(item: str, count: int = 1, tag: dict | None = None) -> dict:
    # Count в стеке предметов — байт, иначе Minecraft отвергнет команду.
    out = {"id": item, "Count": Byte(count)}
    if tag:
        out["tag"] = tag
    return out


def rubles(price: int) -> dict:
    """Цена в рублях одной стопкой: крупными купюрами, если делится."""
    for note, item in ((5000, "citylife:banknote_5000"), (1000, "citylife:banknote_1000"),
                       (100, "citylife:banknote_100"), (10, "citylife:coin_10")):
        if price % note == 0 and price // note <= 64:
            return stack(item, price // note)
    raise ValueError(f"цену {price} не собрать одной стопкой купюр")


# Автосалон: техника MrCrayfish's Vehicle Mod в ящиках.
#
# В ящике уже лежит машина с мотором и колёсами, поэтому собирать ничего не
# нужно: поставил ящик, ударил сверху гаечным ключом — техника готова. Тип
# мотора обязан совпадать с тем, что ждёт машина (смарт-кар электрический,
# внедорожник — большой мотор), иначе она не поедет: список сверен с файлами
# описаний машин в самом моде. Ступень мотора (железо … незерит) — скорость.
#   (машина, название, мотор, колёса, цвет, цена в рублях)
CARS = [
    ("moped", "Мопед", "iron_small_engine", "standard_wheel", 0xE0413A, 4000),
    ("mini_bike", "Минибайк", "iron_small_engine", "standard_wheel", 0x2F7FE0, 3000),
    ("dirt_bike", "Мотокросс", "gold_small_engine", "off_road_wheel", 0xF08A24, 9000),
    ("quad_bike", "Квадроцикл", "gold_small_engine", "off_road_wheel", 0x3E8E41, 12000),
    ("atv", "Вездеход ATV", "gold_small_engine", "all_terrain_wheel", 0x8C6A3E, 14000),
    ("go_kart", "Картинг", "diamond_small_engine", "racing_wheel", 0xF2C12E, 10000),
    ("golf_cart", "Гольф-кар", "iron_electric_engine", "standard_wheel", 0xF4F4F4, 8000),
    ("smart_car", "Смарт-кар", "gold_electric_engine", "standard_wheel", 0x2E9BD6, 18000),
    ("dune_buggy", "Багги", "gold_small_engine", "off_road_wheel", 0xE8C15A, 15000),
    ("off_roader", "Внедорожник", "gold_large_engine", "off_road_wheel", 0x1E1E24, 35000),
    ("mini_bus", "Микроавтобус", "iron_large_engine", "standard_wheel", 0xD9D9DC, 45000),
    ("tractor", "Трактор", "iron_large_engine", "off_road_wheel", 0x3F9B3A, 20000),
    ("aluminum_boat", "Лодка", "iron_small_engine", None, 0xB8C2CC, 6000),
    ("jet_ski", "Гидроцикл", "gold_small_engine", None, 0xE23B6A, 15000),
    ("speed_boat", "Катер", "gold_large_engine", None, 0xFFFFFF, 30000),
    ("compact_helicopter", "Вертолёт", "diamond_small_engine", None, 0x2B2F3A, 120000),
    ("sports_plane", "Самолёт", "diamond_large_engine", None, 0xC0392B, 150000),
]


def vehicle_crate(vehicle: str, title: str, engine: str, wheel: str | None,
                  colour: int) -> dict:
    """Ящик с готовой машиной: мотор, колёса и цвет уже внутри."""
    entity = {
        "Vehicle": f"vehicle:{vehicle}",
        "Color": colour,
        "EngineStack": stack(f"vehicle:{engine}", 1),
    }
    if wheel:
        entity["WheelStack"] = stack(f"vehicle:{wheel}", 1)
    name = json.dumps({"text": f"{title} — ящик", "italic": False}, ensure_ascii=False)
    lore = json.dumps({"text": "Поставь и ударь сверху гаечным ключом",
                       "color": "gray", "italic": False}, ensure_ascii=False)
    return stack("vehicle:vehicle_crate", 1,
                 {"BlockEntityTag": entity, "display": {"Name": name, "Lore": [lore]}})


def offer(buy: dict, sell: dict, buy_b: dict | None = None) -> dict:
    """Одно предложение торговли в формате ванильного торговца."""
    recipe = {
        "buy": buy,
        "sell": sell,
        "maxUses": 999999,
        "uses": 0,
        "xp": 0,
        "priceMultiplier": 0.0,
        "specialPrice": 0,
        "demand": 0,
        "rewardExp": False,
    }
    if buy_b:
        recipe["buyB"] = buy_b
    return recipe


# Ассортимент по ролям. Валюта — монеты Lightman's Currency.
# Оружейная: стволы и патроны TaCZ и Elite X Quality Guns.
#
# В TaCZ оружие собирается на РАЗНЫХ верстаках: стволы — на оружейном,
# патроны — на патронном, обвесы — на верстаке обвесов, а стволы Elite X —
# только на своём Elite Bench. Раньше продавался один оружейный верстак,
# и патроны с половиной оружия было не скрафтить. Теперь мастер продаёт все
# четыре верстака и сырьё под рецепты, а готовые стволы и патроны лежат
# на прилавках — кто не хочет возиться с крафтом, просто покупает.
TACZ = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                   "tacz_catalog.json"), encoding="utf-8"))

# «Чудо-оружие» из зомби-режима Elite X (посохи, лучемёты) в городе не
# продаётся: его можно только собрать на Elite Bench.
WONDER = ("staff", "ray_gun", "raygun", "wonder_waffle", "mustang_and_sally")

GUN_PRICE = {"pistol": 6000, "smg": 12000, "shotgun": 12000, "rifle": 22000,
             "sniper": 35000, "mg": 50000, "rpg": 80000}

# Цена за патрон по калибру: пистолетные дешевле, снайперские дороже.
AMMO_PISTOL = ("9mm", "45acp", "762x25", "22wmr", "357mag", "50ae", "500mag", "57x28",
               "46x30", "five_seven_ammo")
AMMO_SNIPER = ("338", "50bmg", "45_70", "sp_ammo", "trs_bull_ammo")


def wonder(item_id: str) -> bool:
    return any(key in item_id for key in WONDER)


def gun_stack(gun: dict) -> dict:
    return stack("tacz:modern_kinetic_gun", 1, {
        "GunId": gun["id"], "GunFireMode": gun["fire_mode"],
        "GunCurrentAmmoCount": 0, "HasBulletInBarrel": Byte(0)})


def gun_price(gun: dict) -> int:
    price = GUN_PRICE.get(gun["type"], 20000)
    if "golden" in gun["id"]:
        # Золотой ствол — втрое дороже, округляем до пятитысячной купюры.
        price = round(price * 3 / 5000) * 5000
    return price


def ammo_offer(ammo: dict):
    name = ammo["id"].split(":")[1]
    if name in ("rpg_rocket", "40mm"):
        count, each = 1, 2500 if name == "rpg_rocket" else 800
    else:
        count = min(ammo["stack"], 30)
        each = 10 if name in AMMO_PISTOL else 60 if name in AMMO_SNIPER else \
            25 if name == "12g" else 20
    price = max(100, round(count * each / 100) * 100)
    return offer(rubles(price), stack("tacz:ammo", count, {"AmmoId": ammo["id"]}))


def workbench(item: str, block_id: str | None) -> dict:
    return stack(item, 1, {"BlockId": block_id} if block_id else None)


SIDEARMS = ("pistol", "smg", "shotgun")
GUNSMITH_TRADES = [
    offer(rubles(3000), workbench("tacz:gun_smith_table", None)),
    offer(rubles(3000), workbench("tacz:workbench_a", "tacz:ammo_workbench")),
    offer(rubles(3000), workbench("tacz:workbench_c", "tacz:attachment_workbench")),
    offer(rubles(5000), workbench("tacz:workbench_b", "elitex:elitebench")),
    offer(rubles(800), stack("minecraft:iron_ingot", 16)),
    offer(rubles(1600), stack("minecraft:gold_ingot", 8)),
    offer(rubles(500), stack("minecraft:copper_ingot", 16)),
    offer(rubles(400), stack("minecraft:gunpowder", 8)),
    offer(rubles(400), stack("minecraft:lapis_lazuli", 8)),
    offer(rubles(300), stack("minecraft:redstone", 16)),
    offer(rubles(400), stack("minecraft:leather", 8)),
    offer(rubles(3000), stack("minecraft:diamond", 2)),
    offer(rubles(200), stack("minecraft:oak_log", 16)),
    offer(rubles(1000), stack("minecraft:blaze_rod", 2)),
    offer(rubles(500), stack("minecraft:amethyst_shard", 8)),
]
# На прилавке стволы идут по классу, внутри класса — от дешёвых к дорогим.
GUN_ORDER = ["pistol", "smg", "shotgun", "rifle", "sniper", "mg", "rpg"]


def gun_offers(types) -> list[dict]:
    guns = [g for g in TACZ["guns"] if g["type"] in types and not wonder(g["id"])]
    guns.sort(key=lambda g: (GUN_ORDER.index(g["type"]) if g["type"] in GUN_ORDER else 99,
                             gun_price(g), g["id"]))
    return [offer(rubles(gun_price(g)), gun_stack(g)) for g in guns]


ARMS_TRADES = gun_offers(SIDEARMS)
RIFLE_TRADES = gun_offers([t for t in GUN_ORDER if t not in SIDEARMS])
AMMO_TRADES = [ammo_offer(a) for a in TACZ["ammo"] if not wonder(a["id"])]


def sell(item: str, count: int, price: int, tag: dict | None = None) -> dict:
    """Товар за рубли одной стопкой купюр."""
    return offer(rubles(price), stack(item, count, tag))


def buy(item: str, count: int, price: int) -> dict:
    """Скупка: житель берёт товар и платит рублями — заработок для игрока.

    Цена скупки всегда ниже цены продажи того же товара, иначе деньги
    можно было бы «печатать», перепродавая купленное.
    """
    return offer(stack(item, count), rubles(price))


def potion(kind: str, price: int, item: str = "minecraft:potion") -> dict:
    return sell(item, 1, price, {"Potion": f"minecraft:{kind}"})


# Цвета одежды: чёрный, белый, тёмно-синий, красный, бежевый.
CLOTH_COLOURS = [0x1D1D21, 0xF9FFFE, 0x3C44AA, 0xB02E26, 0xC8AD7F]
CLOTH_PIECES = [("leather_helmet", 150), ("leather_chestplate", 300),
                ("leather_leggings", 250), ("leather_boots", 120)]

# Прилавки.
#
# Правило одно: всё, что продаётся, должно работать сразу после покупки.
# Камера видеонаблюдения — вместе с монитором, ПК — со всеми деталями,
# зелье — с настоящим эффектом, а не «незельеваримое» без тега.
ROLE_TRADES: dict[str, list[dict]] = {
    # Продукты: еда на каждый день, от хлеба до торта.
    "trader_food": [
        sell("minecraft:bread", 4, 40),
        sell("minecraft:baked_potato", 4, 40),
        sell("minecraft:apple", 6, 40),
        sell("minecraft:carrot", 8, 30),
        sell("minecraft:sweet_berries", 8, 30),
        sell("minecraft:melon_slice", 8, 30),
        sell("minecraft:cooked_chicken", 3, 60),
        sell("minecraft:cooked_salmon", 3, 60),
        sell("minecraft:cooked_beef", 3, 90),
        sell("minecraft:cooked_porkchop", 3, 90),
        sell("minecraft:cookie", 8, 50),
        sell("minecraft:pumpkin_pie", 2, 60),
        sell("minecraft:honey_bottle", 2, 60),
        sell("minecraft:milk_bucket", 1, 50),
        sell("minecraft:golden_carrot", 4, 100),
        sell("minecraft:cake", 1, 150),
        # Скупка урожая и улова у фермеров и рыбаков.
        buy("minecraft:wheat", 16, 60),
        buy("minecraft:potato", 16, 50),
        buy("minecraft:carrot", 16, 30),
        buy("minecraft:beetroot", 16, 50),
        buy("minecraft:pumpkin", 4, 60),
        buy("minecraft:melon", 4, 60),
        buy("minecraft:sugar_cane", 16, 50),
        buy("minecraft:cod", 8, 80),
        buy("minecraft:salmon", 8, 100),
    ],
    # Одежда: кожаный комплект в пяти цветах и краски, чтобы перекрасить.
    "trader_clothes": [
        sell(f"minecraft:{piece}", 1, price, {"display": {"color": colour}})
        for colour in CLOTH_COLOURS for piece, price in CLOTH_PIECES
    ] + [
        sell(f"minecraft:{dye}_dye", 8, 80)
        for dye in ("white", "black", "blue", "red", "yellow", "green", "pink", "gray")
    ],
    # Электроника: гаджеты, ПК со всеми базовыми деталями, фото- и видеотехника.
    "trader_tech": [
        sell("citylife:tablet", 1, 18000),
        sell("citylife:laptop", 1, 28000),
        sell("citylife:pc_case", 1, 2500),
        sell("citylife:mb_k1", 1, 4000),
        sell("citylife:cpu_k1_i5", 1, 7000),
        sell("citylife:cooler_air", 1, 900),
        sell("citylife:ram_8", 1, 1500),
        sell("citylife:gpu_gx1650", 1, 6000),
        sell("citylife:psu_450", 1, 1800),
        sell("citylife:ssd_512", 1, 2000),
        sell("citylife:monitor", 1, 5000),
        sell("citylife:keyboard", 1, 1000),
        sell("citylife:mouse", 1, 600),
        sell("citylife:headset", 1, 1500),
        sell("citylife:sim_card", 1, 300),
        sell("cameracraft:camera", 1, 2000),
        sell("cameracraft:video_camera", 1, 4000),
        sell("cameracraft:tripod", 1, 800),
        sell("cameracraft:laptop", 1, 6000),
    ],
    # Салон связи: SIM-карты и все семь телефонов, планшет.
    "phone_seller": [
        sell("citylife:sim_card", 1, 300),
        sell("citylife:phone_nokta", 1, 1500),
        sell("citylife:phone_mini", 1, 4000),
        sell("citylife:phone_gran_a5", 1, 6000),
        sell("citylife:smartphone", 1, 9000),
        sell("citylife:phone_polus", 1, 12000),
        sell("citylife:phone_gran_x", 1, 30000),
        sell("citylife:phone_fold", 1, 40000),
        sell("citylife:tablet", 1, 18000),
    ],
    # Банк: карты (их же выпускает банкомат), свой банкомат, кейс для денег.
    "banker": [
        sell("citylife:card_mir", 1, 300),
        sell("citylife:card_mastercard", 1, 300),
        sell("citylife:atm", 1, 2000),
        sell("securitycraft:briefcase", 1, 1500),
        # Скупка драгметаллов и камней.
        buy("minecraft:gold_ingot", 1, 150),
        buy("minecraft:raw_gold", 1, 120),
        buy("minecraft:diamond", 1, 800),
        buy("minecraft:emerald", 1, 400),
        buy("minecraft:lapis_lazuli", 16, 200),
        buy("minecraft:redstone", 32, 200),
        buy("minecraft:amethyst_shard", 8, 150),
        buy("minecraft:quartz", 16, 150),
    ],
    "gunsmith": GUNSMITH_TRADES,
    "arms_dealer": ARMS_TRADES,
    "rifle_dealer": RIFLE_TRADES,
    "ammo_seller": AMMO_TRADES + [
        # Патронный ящик — один предмет с уровнем в NBT: железный это Level 0.
        sell("tacz:ammo_box", 1, 1000, {"Level": 0}),
    ],
    "car_dealer": [
        # Ключ нужен один раз и навсегда: им же вскрываются все ящики.
        sell("vehicle:wrench", 1, 300),
        sell("citylife:fuel_canister", 1, 200),
    ] + [
        offer(rubles(price), vehicle_crate(vehicle, title, engine, wheel, colour))
        for vehicle, title, engine, wheel, colour, price in CARS
    ],
    # Риелтор: всё для своего жилья — замки, двери, сейф и видеонаблюдение.
    # Камера без монитора бесполезна, поэтому монитор стоит рядом (или
    # приложение «Камеры» в телефоне).
    "realtor": [
        sell("citylife:smart_lock", 1, 1000),
        sell("securitycraft:keypad", 1, 200),
        sell("securitycraft:keypad_door_item", 1, 1500),
        sell("securitycraft:keypad_chest", 1, 3000),
        sell("securitycraft:security_camera", 1, 2500),
        sell("securitycraft:camera_monitor", 1, 1500),
        sell("securitycraft:keycard_reader", 1, 1000),
        sell("securitycraft:keycard_lv1", 1, 200),
        sell("securitycraft:keycard_holder", 1, 300),
        sell("securitycraft:alarm", 1, 800),
        sell("securitycraft:motion_activated_light", 1, 400),
        sell("securitycraft:panic_button", 1, 300),
        sell("securitycraft:portable_radar", 1, 1500),
    ],
    # Мэрия: бумаги, книги, карта, часы, бирки и таблички.
    "clerk": [
        sell("minecraft:paper", 8, 40),
        sell("minecraft:book", 1, 50),
        sell("minecraft:writable_book", 1, 80),
        sell("minecraft:map", 1, 100),
        sell("minecraft:compass", 1, 200),
        sell("minecraft:clock", 1, 200),
        sell("minecraft:name_tag", 1, 300),
        sell("minecraft:oak_sign", 4, 40),
        sell("minecraft:white_banner", 1, 100),
    ],
    # Закусочная, кафе, фастфуд: горячая еда и напитки.
    "cook": [
        sell("minecraft:cooked_chicken", 3, 60),
        sell("minecraft:cooked_beef", 2, 60),
        sell("minecraft:cooked_porkchop", 2, 60),
        sell("minecraft:baked_potato", 4, 40),
        sell("minecraft:mushroom_stew", 1, 60),
        sell("minecraft:rabbit_stew", 1, 80),
        sell("minecraft:beetroot_soup", 1, 50),
        sell("minecraft:cookie", 8, 50),
        sell("minecraft:pumpkin_pie", 2, 60),
        sell("minecraft:cake", 1, 150),
        sell("minecraft:honey_bottle", 2, 60),
        sell("minecraft:milk_bucket", 1, 50),
    ],
    # Магазин у дома и мини-маркет: хозтовары, инструменты, мебель и хлеб.
    "shopkeeper": [
        sell("minecraft:bread", 4, 40),
        sell("minecraft:torch", 16, 30),
        sell("minecraft:lantern", 4, 100),
        sell("minecraft:oak_planks", 32, 50),
        sell("minecraft:glass", 16, 80),
        sell("minecraft:ladder", 16, 60),
        sell("minecraft:oak_door", 2, 60),
        sell("minecraft:chest", 1, 50),
        sell("minecraft:crafting_table", 1, 30),
        sell("minecraft:furnace", 1, 50),
        sell("minecraft:white_bed", 1, 150),
        sell("minecraft:flower_pot", 4, 40),
        sell("minecraft:painting", 2, 60),
        sell("minecraft:item_frame", 4, 60),
        sell("minecraft:bucket", 1, 20),
        sell("minecraft:shears", 1, 50),
        sell("minecraft:fishing_rod", 1, 60),
        sell("minecraft:iron_shovel", 1, 80),
        sell("minecraft:iron_axe", 1, 100),
        sell("minecraft:iron_pickaxe", 1, 100),
        # Мебель и свет для своего дома: стулья, люстры, лампы, шторы, полки.
        sell("decorative_blocks:oak_seat", 1, 150),
        sell("decorative_blocks:dark_oak_seat", 1, 150),
        sell("decorative_blocks:spruce_seat", 1, 150),
        sell("decorative_blocks:chandelier", 1, 600),
        sell("mcwlights:white_lamp", 1, 300),
        sell("mcwlights:black_lamp", 1, 300),
        sell("mcwlights:white_ceiling_light", 1, 250),
        sell("mcwlights:oak_ceiling_fan_light", 1, 900),
        sell("mcwwindows:white_curtain", 2, 200),
        sell("mcwwindows:oak_blinds", 4, 200),
        sell("supplementaries:item_shelf", 4, 200),
        sell("supplementaries:clock_block", 1, 300),
        sell("supplementaries:globe", 1, 800),
        sell("minecraft:red_bed", 1, 150),
        sell("minecraft:light_blue_bed", 1, 150),
        sell("minecraft:white_carpet", 8, 80),
        sell("minecraft:gray_carpet", 8, 80),
        sell("minecraft:bookshelf", 1, 150),
    ],
    # Бар и ночной клуб: напитки, музыка — и кое-что из-под стойки для тех,
    # кто выбрал криминальную ветку (отмычка и кодолом).
    "bartender": [
        sell("minecraft:honey_bottle", 2, 60),
        sell("minecraft:milk_bucket", 1, 50),
        sell("minecraft:glow_berries", 8, 60),
        sell("minecraft:jukebox", 1, 1000),
        sell("minecraft:note_block", 1, 200),
    ] + [
        sell(f"minecraft:music_disc_{disc}", 1, 600)
        for disc in ("cat", "blocks", "chirp", "mall", "mellohi", "stal", "wait", "pigstep")
    ] + [
        sell("citylife:lockpick", 1, 1500),
        sell("securitycraft:codebreaker", 1, 20000),
    ],
    # Заправка: бензин, ключ для ящиков с машинами, перекус в дорогу.
    "fuel_seller": [
        sell("citylife:fuel_canister", 1, 150),
        sell("citylife:fuel_canister", 5, 600),
        sell("vehicle:wrench", 1, 300),
        sell("minecraft:bread", 4, 40),
        sell("minecraft:cookie", 8, 50),
        sell("minecraft:honey_bottle", 2, 60),
    ],
    # Аптека и больница: настоящие зелья с эффектом, золотое яблоко, молоко.
    "medic": [
        potion("healing", 150),
        potion("strong_healing", 300),
        potion("healing", 250, "minecraft:splash_potion"),
        potion("regeneration", 300),
        potion("fire_resistance", 250),
        potion("night_vision", 200),
        potion("water_breathing", 200),
        sell("minecraft:golden_apple", 1, 500),
        sell("minecraft:milk_bucket", 1, 50),
        sell("minecraft:honey_bottle", 2, 60),
    ],
    # Склад: сырьё и стройматериалы крупным оптом.
    "foreman": [
        sell("minecraft:stone", 32, 60),
        sell("minecraft:cobblestone", 64, 60),
        sell("minecraft:sand", 32, 40),
        sell("minecraft:oak_log", 16, 100),
        sell("minecraft:iron_ingot", 3, 100),
        sell("minecraft:copper_ingot", 16, 500),
        sell("minecraft:coal", 16, 100),
        # Скупка сырья: брёвна, уголь, руда, камень.
        buy("minecraft:oak_log", 16, 40),
        buy("minecraft:spruce_log", 16, 40),
        buy("minecraft:birch_log", 16, 40),
        buy("minecraft:coal", 32, 90),
        buy("minecraft:raw_copper", 16, 150),
        buy("minecraft:raw_iron", 8, 120),
        buy("minecraft:cobblestone", 64, 20),
    ],
    # Стройка и магазин стройматериалов: всё для своего дома.
    "builder": [
        sell("minecraft:scaffolding", 16, 50),
        sell("minecraft:smooth_stone", 32, 100),
        sell("minecraft:stone_bricks", 32, 100),
        sell("minecraft:bricks", 16, 100),
        sell("minecraft:white_concrete", 16, 100),
        sell("minecraft:light_gray_concrete", 16, 100),
        sell("minecraft:gray_concrete", 16, 100),
        sell("minecraft:black_concrete", 16, 100),
        sell("minecraft:glass_pane", 16, 60),
        sell("minecraft:iron_bars", 16, 100),
        sell("minecraft:oak_stairs", 16, 80),
        sell("minecraft:spruce_planks", 32, 50),
        sell("minecraft:white_terracotta", 16, 80),
        sell("minecraft:quartz_block", 16, 200),
    ],
    # Роли без торговли: охрана, полиция, пожарные.
    "security": [],
    "cop": [],
    "firefighter": [],
}


HOSTILE_MOBS = [
    "zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray",
    "wither_skeleton", "creeper", "spider", "cave_spider", "witch", "enderman",
    "slime", "phantom", "pillager", "vindicator", "evoker", "silverfish",
    "zombified_piglin", "ravager",
    # Животные и прочие мирные мобы: в городе их тоже не должно быть.
    "cow", "pig", "sheep", "chicken", "horse", "donkey", "mule", "llama", "rabbit",
    "wolf", "cat", "ocelot", "fox", "bee", "bat", "parrot", "goat", "frog", "axolotl",
    "squid", "glow_squid", "cod", "salmon", "tropical_fish", "pufferfish", "dolphin",
    "turtle", "villager", "wandering_trader", "iron_golem", "snow_golem",
]


def mob_clean_function() -> str:
    """
    Запасная уборка мобов по всей карте раз в 15 секунд.

    Основную работу делает мод (city/CityRules.java): он не даёт мобу даже
    появиться. Эта функция страхует на случай, если правило выключено
    в конфиге или сервер запущен без мода.
    Отключить: /schedule clear citylife:city/mob_clean
    """
    lines = [
        "# Мобов в городе нет: запасная уборка на случай, если правило мода выключено.",
        "# Отключить: /schedule clear citylife:city/mob_clean",
    ]
    lines += [f"kill @e[type={mob if ':' in mob else 'minecraft:' + mob}]"
              for mob in HOSTILE_MOBS]
    lines.append("schedule function citylife:city/mob_clean 15s replace")
    return "\n".join(lines)


def npc_command(spot: dict) -> str:
    """Команда summon для одного NPC."""
    trades = ROLE_TRADES.get(spot["role"], [])
    name = json.dumps({"text": spot["title"]}, ensure_ascii=False)
    tags = ["citylife_npc", f"citylife_{spot['role']}", GEN_TAG]
    data: dict = {
        "CustomName": name,
        "CustomNameVisible": True,
        "Tags": tags,
        "PersistenceRequired": True,
        "Invulnerable": True,
        "Rotation": [float(spot["rotation"]), 0.0],
        "NoAI": True,
    }
    # Скин по роли; прохожим раздаём разные, чтобы толпа не была на одно лицо.
    skin = ROLE_SKIN.get(spot["role"])
    if skin is None:
        skin = CITIZEN_SKINS[(spot["x"] * 31 + spot["z"] * 17) % len(CITIZEN_SKINS)]
    data["SkinData"] = {
        "Type": "RESOURCE_LOCATION",
        "Texture": f"citylife:textures/entity/npc/{skin}.png",
        "Name": "",
        "URL": "",
    }
    # Готовые Offers в NBT не кладём: Easy NPC их выбрасывает при спавне
    # (проверено на сервере — тег исчезает при любом расположении). Торговлю
    # ведёт мод citylife по тегу роли, цены и товар берутся из ShopCatalog.
    return f"summon {NPC_ENTITY} {spot['x']}.5 {spot['y']} {spot['z']}.5 {to_snbt(data)}"


def to_snbt(value) -> str:
    """Сериализация в SNBT (формат команд Minecraft)."""
    if isinstance(value, dict):
        inner = ",".join(f"{key}:{to_snbt(item)}" for key, item in value.items())
        return "{" + inner + "}"
    if isinstance(value, list):
        return "[" + ",".join(to_snbt(item) for item in value) + "]"
    if isinstance(value, bool):
        return "1b" if value else "0b"
    if isinstance(value, Byte):
        return f"{int(value)}b"
    if isinstance(value, float):
        return f"{value}f"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, str):
        if value.startswith("{") and value.endswith("}"):
            return f"'{value}'"
        return json.dumps(value, ensure_ascii=False)
    raise TypeError(type(value))


def stack_snbt(item: dict) -> str:
    return to_snbt(item)


# ---------------------------------------------------------------------------
#  Сценарий: дерево достижений
# ---------------------------------------------------------------------------

def advancement(title: str, description: str, icon: str, parent: str | None,
                criteria: dict, frame: str = "task", hidden: bool = False,
                background: str | None = None, rewards: dict | None = None) -> dict:
    display = {
        "icon": {"item": icon},
        "title": {"text": title},
        "description": {"text": description},
        "frame": frame,
        "show_toast": True,
        "announce_to_chat": True,
        "hidden": hidden,
    }
    if background:
        display["background"] = background
    data: dict = {"display": display, "criteria": criteria}
    if parent:
        data["parent"] = parent
    if rewards:
        data["rewards"] = rewards
    return data


def has_items(*items: str) -> dict:
    return {"trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": list(items)}]}}


def placed(block: str) -> dict:
    """
    Условие «игрок поставил такой блок».

    В 1.20.1 у триггера minecraft:placed_block больше нет поля block: блок
    описывается предикатом location. Со старым форматом достижение молча
    не грузится — «Failed to parse location field» в логе сервера.
    """
    return {
        "trigger": "minecraft:placed_block",
        "conditions": {"location": [
            {"condition": "minecraft:location_check",
             "predicate": {"block": {"blocks": [block]}}},
        ]},
    }


def in_area(x0: int, z0: int, x1: int, z1: int) -> dict:
    return {
        "trigger": "minecraft:location",
        "conditions": {
            "player": [{
                "condition": "minecraft:entity_properties",
                "entity": "this",
                "predicate": {"location": {"position": {
                    "x": {"min": min(x0, x1), "max": max(x0, x1)},
                    "z": {"min": min(z0, z1), "max": max(z0, z1)},
                }}},
            }],
        },
    }


MONEY = ["citylife:coin_1", "citylife:coin_10", "citylife:banknote_50",
         "citylife:banknote_100", "citylife:banknote_500",
         "citylife:banknote_1000", "citylife:banknote_5000"]


def story(city: P.Plan) -> dict[str, dict]:
    police = next((lot for lot in city.lots if lot.kind == "police"), None)
    mall = next((lot for lot in city.lots if lot.kind == "mall"), None)
    bank = next((lot for lot in city.lots if lot.kind == "bank"), None)

    tree: dict[str, dict] = {}

    tree["story/arrival"] = advancement(
        "Добро пожаловать в Лос-Сантос", "Ты приехал в город. Осмотрись и начни с телефона.",
        "minecraft:oak_boat", None,
        {"tick": {"trigger": "minecraft:tick"}},
        frame="task", background="minecraft:textures/block/black_concrete.png",
        rewards={"function": "citylife:city/guide"})

    tree["story/phone"] = advancement(
        "На связи", "Заведи смартфон: без него в городе никак.",
        "citylife:smartphone", "citylife:story/arrival",
        {"has_phone": has_items("citylife:smartphone", "citylife:phone_nokta",
                                "citylife:phone_mini", "citylife:phone_gran_a5",
                                "citylife:phone_polus", "citylife:phone_gran_x",
                                "citylife:phone_fold")})

    tree["story/first_money"] = advancement(
        "Первые деньги", "Заработай первую монету.",
        "citylife:banknote_100", "citylife:story/phone",
        {"has_coin": has_items(*MONEY)})

    tree["story/bank_account"] = advancement(
        "Счёт в банке", "Выпусти карту «Мир» или Mastercard в банкомате.",
        "citylife:card_mir", "citylife:story/first_money",
        {"has_card": has_items("citylife:card_mir", "citylife:card_mastercard")})

    tree["story/own_keys"] = advancement(
        "Свои ключи", "Поставь умный замок — теперь у тебя есть своё жильё.",
        "citylife:smart_lock", "citylife:story/bank_account",
        {"placed_lock": placed("citylife:smart_lock")})

    # --- легальная ветка ---------------------------------------------------
    tree["story/legal_licence"] = advancement(
        "Лицензия", "Купи верстак оружейника — законный путь к оружию.",
        "tacz:gun_smith_table", "citylife:story/own_keys",
        {"has_table": has_items("tacz:gun_smith_table")})

    tree["story/legal_shop"] = advancement(
        "Свой бизнес", "Поставь свой банкомат — точка в городе есть.",
        "citylife:atm", "citylife:story/own_keys",
        {"placed_register": placed("citylife:atm")})

    tree["story/legal_security"] = advancement(
        "Под охраной", "Поставь камеру или кодовый замок на своём объекте.",
        "securitycraft:security_camera", "citylife:story/legal_shop",
        {
            "camera": placed("securitycraft:security_camera"),
            "keypad": placed("securitycraft:keypad"),
        })
    tree["story/legal_security"]["requirements"] = [["camera", "keypad"]]

    tree["story/legal_empire"] = advancement(
        "Городской предприниматель", "Скопи пачку пятитысячных — дело пошло.",
        "citylife:banknote_5000", "citylife:story/legal_security",
        {"has_chest": {"trigger": "minecraft:inventory_changed", "conditions": {
            "items": [{"items": ["citylife:banknote_5000"], "count": {"min": 10}}]}}},
        frame="goal")

    # --- криминальная ветка -------------------------------------------------
    tree["story/crime_pick"] = advancement(
        "Отмычка в кармане", "Обзаведись отмычкой. Дальше — на свой риск.",
        "citylife:lockpick", "citylife:story/own_keys",
        {"has_pick": has_items("citylife:lockpick")})

    tree["story/crime_first_job"] = advancement(
        "Первое дело", "Попробуй отмычку на умном замке.",
        "minecraft:tripwire_hook", "citylife:story/crime_pick",
        {"used_pick": {"trigger": "minecraft:item_durability_changed",
                       "conditions": {"item": {"items": ["citylife:lockpick"]}}}})

    tree["story/crime_codebreaker"] = advancement(
        "Взломщик", "Достань кодолом SecurityCraft.",
        "securitycraft:codebreaker", "citylife:story/crime_first_job",
        {"has_breaker": has_items("securitycraft:codebreaker")})

    if bank:
        tree["story/crime_bank"] = advancement(
            "Дело в банке", "Дойди до хранилища городского банка.",
            "minecraft:gold_block", "citylife:story/crime_codebreaker",
            {"at_bank": in_area(bank.x0, bank.z0, bank.x1, bank.z1)},
            frame="goal")

    if police:
        tree["story/crime_wanted"] = advancement(
            "В розыске", "Зайди в полицейский участок. Сам или под конвоем.",
            "minecraft:iron_bars", "citylife:story/crime_pick",
            {"at_police": in_area(police.x0, police.z0, police.x1, police.z1)})

    # --- общие и кооперативные цели -----------------------------------------
    if mall:
        tree["story/mall"] = advancement(
            "Шопинг", "Дойди до торгового центра и поторгуйся с продавцами.",
            "minecraft:emerald", "citylife:story/first_money",
            {"at_mall": in_area(mall.x0, mall.z0, mall.x1, mall.z1)})

    tree["story/photographer"] = advancement(
        "Фотограф", "Возьми фотоаппарат и сними город.",
        "cameracraft:camera", "citylife:story/phone",
        {"has_camera": has_items("cameracraft:camera")})

    tree["story/coop_business"] = advancement(
        "Общее дело", "Кооп: откройте бизнес вдвоём. Выдаётся командой "
        "/function citylife:story/coop_business",
        "minecraft:gold_ingot", "citylife:story/legal_shop",
        {"granted": {"trigger": "minecraft:impossible"}}, frame="challenge")

    tree["story/coop_heist"] = advancement(
        "Работа в команде", "Кооп: ограбление втроём — взлом, камеры, машина. "
        "Выдаётся командой /function citylife:story/coop_heist",
        "minecraft:tnt", "citylife:story/crime_codebreaker",
        {"granted": {"trigger": "minecraft:impossible"}}, frame="challenge")

    return tree


GUIDE_PAGES = [
    [
        {"text": "LOS SANTOS\n\n", "bold": True},
        {"text": "Современный город: работа, рубли, телефоны, компьютеры, "
                 "маркетплейс, умные замки, машины и оружие.\n\n"},
        {"text": "Цели видны в меню достижений (клавиша L)."},
    ],
    [
        {"text": "С чего начать\n\n", "bold": True},
        {"text": "1. Перетащи SIM-карту на телефон в инвентаре.\n"
                 "2. Правый клик телефоном — рабочий стол.\n"
                 "3. Работа — приложение «Работа» или /job.\n"
                 "4. Жильё — у риелтора в мэрии."},
    ],
    [
        {"text": "Заработок\n\n", "bold": True},
        {"text": "«Работа» в телефоне: курьер, доставка еды, такси, смена, "
                 "охрана, грузчик, мусорщик.\n\n"
                 "Дежурство (/duty): полиция, скорая, пожарные, такси — "
                 "вызовы от других игроков.\n\n"
                 "Скупка у жителей, /work — раз в 15 минут."},
    ],
    [
        {"text": "Своё жильё\n\n", "bold": True},
        {"text": "Дома, виллы, таунхаусы и квартиры продаёт риелтор "
                 "в мэрии, квартиры — управдомы у подъездов.\n\n"
                 "Пока хозяин в игре, чужой не войдёт. Когда его нет — "
                 "зайти можно, но рыться в сундуках — кража.\n\n"
                 "Коммуналка списывается каждые сутки; дом можно сдать другу."},
    ],
    [
        {"text": "Бизнес\n\n", "bold": True},
        {"text": "Магазины, склады и офисы тоже продаёт риелтор (фильтр "
                 "«Бизнесы»).\n\n"
                 "Бизнес каждые сутки приносит 1,5% цены на счёт, "
                 "коммуналки нет. Продавцы внутри торгуют как прежде."},
    ],
    [
        {"text": "Своя машина\n\n", "bold": True},
        {"text": "Ящик из автосалона: поставь и ударь сверху гаечным ключом — "
                 "машина твоя.\n\n"
                 "«Мой транспорт» или /car: запереть, маршрут к машине, "
                 "/car trust — ключ другу. Угон — звезда розыска."},
    ],
    [
        {"text": "Больница и звонки\n\n", "bold": True},
        {"text": "Без своей кровати после смерти очнёшься у больницы; "
                 "лечение — 200 ₽. Навигатор ведёт к вещам.\n\n"
                 "«Звонки»: набери номер или нажми трубку в контактах. "
                 "Голос — через Simple Voice Chat."},
    ],
    [
        {"text": "112\n\n", "bold": True},
        {"text": "Полиция, скорая и пожарные приезжают через 30 секунд. "
                 "Кликни по сотруднику — в чате появятся варианты: "
                 "вылечить, потушить, задержать, отбой.\n\n"
                 "Драка, кража и взлом дают звёзды розыска: "
                 "полиция задерживает, штраф и камера."},
    ],
    [
        {"text": "Ограбления\n\n", "bold": True},
        {"text": "Касса: присядь и кликни по продавцу с оружием. ★★, полиция "
                 "едет; 30 с не отходи дальше 7 блоков — выручка твоя.\n\n"
                 "Банкомат: присядь и кликни отмычкой. ★★★, 45 с.\n\n"
                 "Задержат — награбленное изымут. Касса пуста 20 минут."},
    ],
    [
        {"text": "Телефон и SIM\n\n", "bold": True},
        {"text": "Без SIM работает только 112. С SIM — сообщения по номеру, "
                 "банк, навигатор, маркетплейс.\n\n"
                 "Номер у SIM, а деньги — на твоём счёте: сменишь телефон, "
                 "счёт останется."},
    ],
    [
        {"text": "Маркетплейс\n\n", "bold": True},
        {"text": "Видеокарты, детали ПК, телефоны, планшет, ноутбук. "
                 "Оплата со счёта.\n\n"
                 "Через пару минут заказ ждёт в пункте выдачи: нажми на "
                 "фиолетовый терминал. Пункты — в навигаторе."},
    ],
    [
        {"text": "Компьютер\n\n", "bold": True},
        {"text": "Системный блок: плата, процессор, кулер, память, видеокарта, "
                 "блок питания, диск. Сокет процессора = сокет платы, "
                 "горячему нужна водянка.\n\n"
                 "Рядом монитор, клавиатура и мышь — и ПК включится."},
    ],
    [
        {"text": "Навигатор\n\n", "bold": True},
        {"text": "Метки города: банк, банкоматы, пункты выдачи, службы. "
                 "Своя точка — «Метка здесь».\n\n"
                 "По земле лягут стрелки, на месте стоит метка с лучом. "
                 "Войди в неё — маршрут снимется сам."},
    ],
    [
        {"text": "Деньги\n\n", "bold": True},
        {"text": "Наличные — рубли: монета 10 ₽, купюры 100, 1000 и 5000 ₽.\n\n"
                 "Безнал — карта Мир или Mastercard, счёт виден в телефоне.\n\n"
                 "Банкоматы у банка, мэрии, ТЦ, метро и на АЗС."},
    ],
    [
        {"text": "Машины\n\n", "bold": True},
        {"text": "В автосалоне продаются ящики с машинами. Поставь ящик и "
                 "вскрой гаечным ключом — внутри готовая машина с полным "
                 "баком.\n\n"
                 "Бензин — канистра с АЗС. Метро: три станции."},
    ],
    [
        {"text": "Свободные участки\n\n", "bold": True},
        {"text": "Участки с табличкой ПРОДАЁТСЯ свободны под постройки: "
                 "на табличке размер и адрес.\n\n"
                 "Границу карты не перейти: город закрыт в квадрате 2048 блоков."},
    ],
]


def guide_function() -> str:
    pages = [json.dumps(page, ensure_ascii=False) for page in GUIDE_PAGES]
    # JSON уже содержит \n внутри кавычек. В SNBT такая строка попадает в
    # одинарные кавычки, и парсер команд видит \n как недопустимую escape-
    # последовательность — поэтому обратный слэш удваиваем.
    pages_snbt = ",".join(
        "'" + page.replace("\\", "\\\\").replace("'", "\\'") + "'" for page in pages)
    return ("give @s written_book{title:\"Гид по Лос-Сантосу\","
            "author:\"Мэрия города\",generation:0,pages:[" + pages_snbt + "]} 1")


# ---------------------------------------------------------------------------
#  Сборка датапака
# ---------------------------------------------------------------------------

def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text if text.endswith("\n") else text + "\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=os.path.join(ROOT, "datapack", "citylife"))
    parser.add_argument("--seed", type=int, default=20260927)
    args = parser.parse_args()

    city = P.build_plan(args.seed)
    out = args.out
    if os.path.isdir(out):
        shutil.rmtree(out)
    data = os.path.join(out, "data")
    functions = os.path.join(data, "citylife", "functions")

    write(os.path.join(out, "pack.mcmeta"), json.dumps({
        "pack": {
            "pack_format": 15,
            "description": "LS City Life — NPC, сценарий и настройки города",
        },
    }, ensure_ascii=False, indent=2))

    # Настройки мира при загрузке.
    init_lines = [
        "# Выполняется при загрузке мира: граница карты и правила города.",
        f"worldborder center 0 0",
        f"worldborder set {P.WORLD_BORDER}",
        "worldborder warning distance 8",
        "gamerule doFireTick false",
        "gamerule mobGriefing false",
        "gamerule doMobSpawning false",
        "gamerule doWardenSpawning false",
        "gamerule doInsomnia false",
        "gamerule doPatrolSpawning false",
        "gamerule doTraderSpawning false",
        "gamerule announceAdvancements true",
        "gamerule spawnRadius 2",
        f"setworldspawn {P.SPAWN[0]} {P.SPAWN[1]} {P.SPAWN[2]}",
        "scoreboard objectives add citylife_jobs dummy \"Выполненные работы\"",
        "scoreboard objectives add citylife_state dummy \"Состояние города\"",
        "# Запасная уборка мобов (основное правило — в моде citylife).",
        "schedule function citylife:city/mob_clean 15s replace",
    ]
    write(os.path.join(functions, "init.mcfunction"), "\n".join(init_lines))

    # NPC пачками.
    spots = city.npc_spots
    batches = [spots[i:i + BATCH_SIZE] for i in range(0, len(spots), BATCH_SIZE)]
    for index, batch in enumerate(batches, start=1):
        lines = [f"# NPC, пачка {index} из {len(batches)}"]
        for spot in batch:
            lines.append(f"# {spot['label']} — {spot['title']}")
            lines.append(npc_command(spot))
        write(os.path.join(functions, "npc", f"spawn_{index:02d}.mcfunction"),
              "\n".join(lines))

    # Одной командой: summon работает и в незагруженных чанках — игра загружает
    # чанк, чтобы записать сущность. Проверено на сервере: после вызова в мире
    # сохраняются все 125 жителей, хотя рядом со спавном загружено только ~20.
    write(os.path.join(functions, "npc", "spawn_all.mcfunction"), "\n".join(
        ["# Расставить всех городских NPC заново (сначала убирает прежних)."] +
        ["function citylife:npc/clear"] +
        [f"function citylife:npc/spawn_{i:02d}" for i in range(1, len(batches) + 1)] +
        [f'tellraw @a {{"text":"Город заселён: NPC {len(spots)}","color":"green"}}']))

    # Заселение без команд: скрытое достижение срабатывает на первом тике,
    # когда игрок уже в мире, и один раз запускает расстановку. Признак лежит
    # в scoreboard, поэтому при следующих входах ничего не повторяется.
    write(os.path.join(functions, "npc", "populate_once.mcfunction"), "\n".join([
        f"# Заселение города жителями поколения {NPC_GEN}. Вызывается достижением",
        f"# citylife:hidden/populate_{NPC_GEN}: у нового поколения новое достижение, поэтому",
        "# оно срабатывает и в старых мирах, где прежнее уже получено.",
        "# Расставить заново вручную: /function citylife:npc/spawn_all",
        f"execute unless score #populated citylife_state matches {NPC_GEN} run "
        "function citylife:npc/spawn_all",
        f"scoreboard players set #populated citylife_state {NPC_GEN}",
    ]))
    write(os.path.join(data, "citylife", "advancements", "hidden", f"populate_{NPC_GEN}.json"),
          json.dumps({
              "criteria": {"tick": {"trigger": "minecraft:tick"}},
              "rewards": {"function": "citylife:npc/populate_once"},
          }, ensure_ascii=False, indent=2))

    write(os.path.join(functions, "npc", "clear.mcfunction"),
          "# Убрать всех NPC, расставленных датапаком.\n"
          "kill @e[tag=citylife_npc]")

    write(os.path.join(functions, "city", "guide.mcfunction"),
          "# Выдать вводную книгу.\n" + guide_function())

    write(os.path.join(functions, "city", "mob_clean.mcfunction"), mob_clean_function())

    for name in ("coop_business", "coop_heist"):
        write(os.path.join(functions, "story", f"{name}.mcfunction"),
              f"# Выдать кооперативную цель всем игрокам поблизости.\n"
              f"advancement grant @a only citylife:story/{name}")

    # Сценарий.
    for key, body in story(city).items():
        write(os.path.join(data, "citylife", "advancements", key + ".json"),
              json.dumps(body, ensure_ascii=False, indent=2))

    write(os.path.join(data, "minecraft", "tags", "functions", "load.json"),
          json.dumps({"values": ["citylife:init"]}, indent=2))

    total = sum(len(files) for _, _, files in os.walk(out))
    print(f"Датапак собран: {out}")
    print(f"  NPC: {len(spots)} в {len(batches)} пачках")
    print(f"  достижений: {len(story(city))}")
    print(f"  файлов: {total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
