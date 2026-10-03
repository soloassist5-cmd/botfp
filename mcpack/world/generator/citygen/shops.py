"""
Торговые залы: что стоит в магазине, кроме продавца и кассы.

Прежде в лавке был только прилавок из кварца и пара бочек за ним — зал
выглядел пустым. Теперь у каждого профиля свой зал:

* продукты — стеллажи с хлебом, овощами и фруктами, ящики с урожаем,
  холодильники с напитками, фруктовый лоток под навесом у входа;
* мини-маркет — то же плюс бытовая мелочь;
* одежда — полки со сложенной одеждой, вешалки с крашеной кожей,
  примерочные за шторками;
* электроника — витрины с мониторами, клавиатурами и наушниками;
* кафе и закусочные — столики со стульями, витрина с выпечкой, кухня;
* аптека — белые полки с зельями и склянками;
* стройматериалы — поддоны с кирпичом, досками и камнем, инструмент.

Зал делится на три части: витрина у входа (до прилавка), прилавок с
продавцом и торговый зал за ним с рядами стеллажей. Покупатель проходит
в ряды по краям прилавка.
"""
from __future__ import annotations

import random

from . import blocks as B
from . import furniture as F
from .interiors import Floor

# --- ассортимент полок -------------------------------------------------------

FOOD = ["minecraft:bread", "minecraft:apple", "minecraft:baked_potato", "minecraft:cookie",
        "minecraft:carrot", "minecraft:melon_slice", "minecraft:pumpkin_pie",
        "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:honey_bottle",
        "minecraft:egg", "minecraft:sugar", "minecraft:beetroot", "minecraft:potato",
        "farmersdelight:tomato", "farmersdelight:cabbage", "farmersdelight:onion",
        "farmersdelight:rice", "farmersdelight:wheat_dough", "minecraft:cooked_cod",
        "minecraft:cake", "minecraft:milk_bucket", "minecraft:golden_carrot",
        "farmersdelight:pie_crust", "farmersdelight:raw_pasta", "minecraft:dried_kelp"]
HOUSEHOLD = ["minecraft:paper", "minecraft:book", "minecraft:candle", "minecraft:string",
             "minecraft:bucket", "minecraft:glass_bottle", "minecraft:flower_pot",
             "minecraft:white_candle", "minecraft:bowl", "minecraft:shears",
             "minecraft:flint_and_steel", "minecraft:writable_book", "minecraft:name_tag",
             "minecraft:map", "minecraft:clock", "minecraft:compass"]
PHARMACY = [F.potion("healing"), F.potion("regeneration"), F.potion("swiftness"),
            F.potion("night_vision"), F.potion("fire_resistance"), F.potion("water_breathing"),
            ("minecraft:honey_bottle", 1), ("minecraft:glass_bottle", 1),
            ("minecraft:golden_apple", 1), ("minecraft:glistering_melon_slice", 1),
            ("minecraft:sugar", 1), ("minecraft:spider_eye", 1)]
TECH = ["minecraft:clock", "minecraft:compass", "minecraft:spyglass", "minecraft:recovery_compass",
        "minecraft:redstone", "minecraft:repeater", "minecraft:comparator",
        "minecraft:daylight_detector", "minecraft:observer", "minecraft:lightning_rod",
        "minecraft:music_disc_cat", "minecraft:music_disc_blocks", "minecraft:music_disc_chirp"]
TOOLS = ["minecraft:iron_pickaxe", "minecraft:iron_shovel", "minecraft:iron_axe",
         "minecraft:iron_hoe", "minecraft:shears", "minecraft:bucket", "minecraft:lantern",
         "minecraft:chain", "minecraft:ladder", "minecraft:torch", "minecraft:brush",
         "minecraft:white_dye", "minecraft:gray_dye", "minecraft:black_dye", "minecraft:blue_dye",
         "minecraft:flower_pot", "minecraft:oak_door", "minecraft:iron_door", "minecraft:glass_pane",
         "minecraft:oak_trapdoor", "mcwlights:white_lamp", "mcwlights:chain_lantern",
         "minecraft:item_frame", "minecraft:painting", "minecraft:flint_and_steel"]
COLORS = [0xB02E26, 0x3C44AA, 0xF9FFFE, 0x1D1D21, 0x5E7C16, 0xF38BAA, 0xFED83D, 0x8932B8,
          0x169C9C, 0x835432, 0x9D9D97]
CLOTHES = ["chestplate", "leggings", "boots", "helmet"]

CRATES = ["farmersdelight:carrot_crate", "farmersdelight:potato_crate",
          "farmersdelight:beetroot_crate", "farmersdelight:cabbage_crate",
          "farmersdelight:tomato_crate", "farmersdelight:onion_crate", "farmersdelight:rice_bag",
          "minecraft:melon", "minecraft:pumpkin"]

GUNSMITH = ["minecraft:iron_ingot", "minecraft:gunpowder", "minecraft:iron_nugget",
            "minecraft:tripwire_hook", "minecraft:flint", "minecraft:copper_ingot",
            "minecraft:string", "minecraft:leather"]

# Профили, у которых торговый зал с полками (см. commercial.furnish_ground).
SHOP_PROFILES = {"trader_food", "shopkeeper", "trader_clothes", "trader_tech", "cook", "medic",
                 "builder", "phone_seller", "gunsmith"}

# Тип здания -> профиль зала, если у лавки нет своей роли.
KIND_PROFILE = {"phone_shop": "phone_seller", "gun_shop": "gunsmith"}

# Цвет полосатого навеса по профилю лавки.
AWNING = {"trader_food": "green", "shopkeeper": "red", "trader_clothes": "pink",
          "trader_tech": "blue", "cook": "orange", "medic": "white", "builder": "yellow",
          "phone_seller": "light_blue", "gunsmith": "black", "diner": "red", "club": "purple"}


def _stock(profile: str, rng: random.Random) -> list[tuple]:
    """Четыре предмета на одну полку."""
    if profile == "medic":
        return [rng.choice(PHARMACY) for _ in range(4)]
    if profile in ("trader_tech", "phone_seller"):
        return [(rng.choice(TECH), 1) for _ in range(4)]
    if profile == "trader_clothes":
        return [F.dyed(rng.choice(CLOTHES), rng.choice(COLORS)) for _ in range(4)]
    if profile == "builder":
        return [(rng.choice(TOOLS), 1) for _ in range(4)]
    if profile == "gunsmith":
        return [(rng.choice(GUNSMITH), rng.randint(1, 4)) for _ in range(4)]
    pool = FOOD if profile in ("trader_food", "cook") else FOOD + HOUSEHOLD
    return [(rng.choice(pool), rng.randint(1, 3)) for _ in range(4)]


def shelf_tower(fl: Floor, u: int, v: int, face: str, profile: str, height: int = 3) -> None:
    """Стеллаж в три яруса: полки одна над другой, на каждой свой товар."""
    for dy in range(height):
        F.display_shelf(fl.frame, u, fl.y + dy, v, face, _stock(profile, fl.rng))


def aisles(fl: Floor, u0: int, v0: int, u1: int, v1: int, profile: str) -> None:
    """
    Ряды двусторонних стеллажей поперёк зала: пара полок спинами друг к
    другу, проход в два блока. Торцы рядов у прохода — ящики с товаром.
    """
    rng = fl.rng
    if v1 - v0 < 2:
        return
    for u in range(u0, u1, 4):
        if u + 1 > u1:
            break
        for v in range(v0, v1 + 1):
            for du, face in ((0, "left"), (1, "right")):
                if not fl.free(u + du, v, u + du, v):
                    continue
                if v == v0 and profile in ("trader_food", "shopkeeper"):
                    fl.frame.set(u + du, fl.y, v, rng.choice(CRATES))
                elif profile == "builder" and (u - u0) % 8 < 4:
                    # Ряды строймаркета: поддоны с кирпичом и досками
                    # чередуются со стеллажами инструмента и фурнитуры.
                    _pallet(fl, u + du, v)
                else:
                    shelf_tower(fl, u + du, v, face, profile)
                fl.take(u + du, v, u + du, v)


def _pallet(fl: Floor, u: int, v: int) -> None:
    """Поддон со стройматериалом: плита-поддон и штабель сверху."""
    f, y, rng = fl.frame, fl.y, fl.rng
    f.set(u, y, v, B.slab("spruce"))
    stack = rng.choice(("minecraft:bricks", "minecraft:oak_planks", "minecraft:stone_bricks",
                        "minecraft:spruce_planks", "minecraft:smooth_stone",
                        "minecraft:terracotta", "minecraft:white_concrete_powder"))
    if "powder" in stack:
        stack = "minecraft:white_wool"
    f.set(u, y + 1, v, stack)
    if rng.random() < 0.5:
        f.set(u, y + 2, v, stack)


def back_wall(fl: Floor, u0: int, u1: int, v: int, profile: str) -> None:
    """У задней стены: холодильники с напитками, ящики или полки в рост."""
    f, y, rng = fl.frame, fl.y, fl.rng
    for u in range(u0, u1 + 1):
        if not fl.free(u, v, u, v):
            continue
        if profile in ("trader_food", "shopkeeper", "cook") and (u - u0) % 4 < 2:
            _cooler(fl, u, v)
        elif profile in ("trader_food", "shopkeeper"):
            f.set(u, y, v, rng.choice(CRATES))
            f.set(u, y + 1, v, rng.choice(CRATES))
        elif profile == "builder" and (u - u0) % 3 == 0:
            _pallet(fl, u, v)
        else:
            shelf_tower(fl, u, v, "front", profile)
        fl.take(u, v, u, v)


def _cooler(fl: Floor, u: int, v: int) -> None:
    """Холодильник с напитками: стеклянная дверца, внутри бутылки на полках."""
    f, y = fl.frame, fl.y
    F.display_shelf(f, u, y, v, "front", [("minecraft:milk_bucket", 1), F.potion("water"),
                                         ("minecraft:honey_bottle", 1), F.potion("water")])
    F.display_shelf(f, u, y + 1, v, "front", [F.potion("water"), ("minecraft:honey_bottle", 1),
                                             F.potion("water"), ("minecraft:honey_bottle", 1)])
    f.set(u, y + 2, v, "minecraft:white_concrete")


def front_display(fl: Floor, u0: int, v0: int, u1: int, v1: int, profile: str) -> None:
    """
    Витрина у окон и островки до прилавка: то, что видно с улицы.

    Ряд у самого окна — сплошная витрина; дальше — островки шириной в две
    клетки с проходами по две, чтобы между ними можно было ходить.
    """
    f, y, rng = fl.frame, fl.y, fl.rng
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            window = v == v0
            island = (u - u0) % 4 in (1, 2) and v >= v0 + 2 and (v - v0 - 2) % 4 != 3
            if not (window or island) or not fl.free(u, v, u, v):
                continue
            _display_cell(fl, u, v, profile, window)
            fl.take(u, v, u, v)


def _display_cell(fl: Floor, u: int, v: int, profile: str, window: bool) -> None:
    f, y, rng = fl.frame, fl.y, fl.rng
    if profile in ("trader_food", "shopkeeper"):
        if window or (u + v) % 3:
            f.set(u, y, v, rng.choice(CRATES))
        else:
            F.holder(f, u, y, v, "farmersdelight:wooden_basket", "farmersdelight:basket",
                     [(rng.choice(("minecraft:apple", "minecraft:carrot",
                                   "farmersdelight:tomato", "minecraft:potato")), 16)],
                     facing="up")
    elif profile == "trader_clothes":
        F.table(f, u, y, v, u, v, "mcwfurnitures:oak_table")
        f.set(u, y + 1, v, B.carpet(rng.choice(("red", "blue", "white", "black",
                                               "pink", "light_blue", "yellow"))))
    elif profile in ("trader_tech", "phone_seller"):
        F.put(f, u, y, v, "handcrafted:oak_desk", "front", color="white")
        F.put(f, u, y + 1, v, rng.choice(("citylife:monitor", "citylife:keyboard",
                                          "citylife:headset", "citylife:mouse")), "front")
    elif profile == "medic":
        if window:
            F.plant(f, u, y, v, rng)
        else:
            shelf_tower(fl, u, v, "front" if (u + v) % 2 else "back", profile, 2)
    elif profile == "builder":
        _pallet(fl, u, v)
    elif profile == "gunsmith":
        f.set(u, y, v, rng.choice(("minecraft:smithing_table", "minecraft:fletching_table",
                                   "minecraft:crafting_table")))
    elif profile == "cook":
        F.holder(f, u, y, v, "supplementaries:pedestal", "supplementaries:pedestal",
                 [(rng.choice(("minecraft:cake", "minecraft:pumpkin_pie",
                               "farmersdelight:apple_pie_slice",
                               "farmersdelight:chocolate_pie_slice")), 1)])


def fitting_rooms(fl: Floor, u0: int, v: int, count: int) -> None:
    """Примерочные: кабинки из досок со шторкой вместо двери."""
    f, y = fl.frame, fl.y
    for i in range(count):
        u = u0 + i * 2
        if not fl.free(u, v - 1, u + 1, v):
            return
        f.fill(u + 1, y, v - 1, u + 1, y + 2, v, B.BIRCH_PLANKS)
        F.put(f, u, y + 1, v - 1, "another_furniture:white_curtain", "front",
              vertical="bottom", horizontal="single", open=False)
        F.put(f, u, y + 2, v - 1, "another_furniture:white_curtain", "front",
              vertical="top", horizontal="single", open=False)
        f.set(u, y + 1, v, "minecraft:glass_pane[east=false,north=false,south=false,"
                           "waterlogged=false,west=false]")
        fl.take(u, v - 1, u + 1, v)


def cafe_tables(fl: Floor, u0: int, v0: int, u1: int, v1: int) -> None:
    """Столики на двоих и четверых с настоящими стульями."""
    f, y, rng = fl.frame, fl.y, fl.rng
    for v in range(v0 + 1, v1, 3):
        for u in range(u0 + 1, u1, 4):
            if not fl.free(u - 1, v - 1, u + 1, v + 1):
                continue
            F.put(f, u, y, v, "another_furniture:oak_table", "front")
            F.chair(f, u - 1, y, v, "right", "another_furniture:spruce_chair")
            F.chair(f, u + 1, y, v, "left", "another_furniture:spruce_chair")
            if rng.random() < 0.5:
                f.set(u, y + 1, v, rng.choice(("handcrafted:white_cup[facing=north]",
                                               "handcrafted:white_plate[facing=north]",
                                               "minecraft:candle[candles=1,lit=false,"
                                               "waterlogged=false]")))
            fl.take(u - 1, v - 1, u + 1, v + 1)


def kitchen_line(fl: Floor, u0: int, u1: int, v: int, face: str) -> None:
    """Кухня заведения: плиты с кастрюлями, духовки, разделочные доски."""
    F.kitchen(fl.frame, u0, fl.y, v, u1, face, fl.rng, upper=True, wood="spruce")
    fl.take(u0, v, u1, v)


def street_stall(frame, lay, rng: random.Random, profile: str) -> None:
    """Лоток у входа под навесом: ящики с овощами и корзины с фруктами."""
    from .plan import CITY_Y
    if lay.v0 < 1 or profile not in ("trader_food", "shopkeeper"):
        return
    v = lay.v0 - 1
    y = CITY_Y + 1
    for u in list(range(lay.u0 + 1, lay.door_u - 1)) + list(range(lay.door_u + 3, lay.u1)):
        if rng.random() < 0.7:
            if u % 2 == 0:
                frame.set(u, y, v, rng.choice(CRATES[:6]))
            else:
                F.holder(frame, u, y, v, "farmersdelight:wooden_basket",
                         "farmersdelight:basket",
                         [(rng.choice(("minecraft:apple", "minecraft:carrot",
                                       "farmersdelight:tomato", "minecraft:melon_slice")), 16)],
                         facing="up")
