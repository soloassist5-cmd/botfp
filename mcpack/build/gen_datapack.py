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
TRADER_ENTITY = "minecraft:villager"
FLAVOUR_ENTITY = "easy_npc:humanoid"

# Профессия жителя подбирается под роль: от неё зависит одежда и звуки.
ROLE_PROFESSION = {
    "trader_food": "minecraft:farmer",
    "trader_clothes": "minecraft:leatherworker",
    "trader_tech": "minecraft:toolsmith",
    "phone_seller": "minecraft:cartographer",
    "banker": "minecraft:librarian",
    "gunsmith": "minecraft:weaponsmith",
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
COIN = "lightmanscurrency:coin_{}"

# Сколько команд в одном файле: длинные функции неудобно отлаживать.
BATCH_SIZE = 25


def coin(kind: str) -> str:
    return COIN.format(kind)


class Byte(int):
    """Целое, которое в SNBT выводится с суффиксом b (нужно для Count)."""


def stack(item: str, count: int = 1) -> dict:
    # Count в стеке предметов — байт, иначе Minecraft отвергнет команду.
    return {"id": item, "Count": Byte(count)}


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
ROLE_TRADES: dict[str, list[dict]] = {
    "trader_food": [
        offer(stack(coin("copper"), 3), stack("minecraft:bread", 4)),
        offer(stack(coin("copper"), 6), stack("minecraft:cooked_beef", 3)),
        offer(stack(coin("iron"), 1), stack("minecraft:golden_carrot", 4)),
        offer(stack(coin("copper"), 2), stack("minecraft:sweet_berries", 6)),
    ],
    "trader_clothes": [
        offer(stack(coin("iron"), 2), stack("minecraft:leather_chestplate", 1)),
        offer(stack(coin("iron"), 1), stack("minecraft:leather_boots", 1)),
        offer(stack(coin("copper"), 8), stack("minecraft:white_dye", 8)),
    ],
    "trader_tech": [
        offer(stack(coin("gold"), 1), stack("citylife:smartphone", 1)),
        offer(stack(coin("iron"), 2), stack("citylife:sim_card", 2)),
        offer(stack(coin("iron"), 4), stack("minecraft:redstone", 16)),
        offer(stack(coin("gold"), 2), stack("cameracraft:camera", 1)),
    ],
    "phone_seller": [
        offer(stack(coin("gold"), 1), stack("citylife:smartphone", 1)),
        offer(stack(coin("copper"), 9), stack("citylife:sim_card", 1)),
        offer(stack(coin("iron"), 3), stack("cameracraft:camera", 1)),
    ],
    "banker": [
        offer(stack(coin("iron"), 2), stack("lightmanscurrency:atm_card", 1)),
        offer(stack(coin("gold"), 4), stack("lightmanscurrency:coin_chest", 1)),
        offer(stack(coin("gold"), 2), stack("lightmanscurrency:cash_register", 1)),
    ],
    "gunsmith": [
        # Сами стволы собираются на верстаке TaCZ, поэтому продаём верстак и сырьё.
        offer(stack(coin("gold"), 3), stack("tacz:gun_smith_table", 1)),
        offer(stack(coin("iron"), 3), stack("minecraft:iron_ingot", 8)),
        offer(stack(coin("iron"), 2), stack("minecraft:gunpowder", 8)),
        offer(stack(coin("gold"), 1), stack("tacz:ammo_box.iron", 1)),
    ],
    "car_dealer": [
        offer(stack(coin("gold"), 2), stack("minecraft:iron_block", 4)),
        offer(stack(coin("iron"), 4), stack("minecraft:coal", 32)),
        offer(stack(coin("copper"), 6), stack("minecraft:glass", 16)),
    ],
    "realtor": [
        offer(stack(coin("gold"), 1), stack("citylife:smart_lock", 1)),
        offer(stack(coin("iron"), 2), stack("securitycraft:keypad", 1)),
        offer(stack(coin("emerald"), 1), stack("securitycraft:security_camera", 2)),
    ],
    "clerk": [
        offer(stack(coin("copper"), 4), stack("minecraft:paper", 8)),
        offer(stack(coin("iron"), 1), stack("minecraft:map", 1)),
        offer(stack(coin("iron"), 2), stack("lightmanscurrency:ticket_machine", 1)),
    ],
    "cook": [
        offer(stack(coin("copper"), 4), stack("minecraft:cooked_chicken", 3)),
        offer(stack(coin("copper"), 5), stack("minecraft:pumpkin_pie", 2)),
        offer(stack(coin("copper"), 3), stack("minecraft:cake", 1)),
    ],
    "shopkeeper": [
        offer(stack(coin("copper"), 3), stack("minecraft:torch", 16)),
        offer(stack(coin("copper"), 5), stack("minecraft:oak_planks", 32)),
        offer(stack(coin("iron"), 1), stack("minecraft:iron_pickaxe", 1)),
        offer(stack(coin("copper"), 2), stack("minecraft:bucket", 1)),
    ],
    "bartender": [
        offer(stack(coin("copper"), 4), stack("minecraft:honey_bottle", 2)),
        offer(stack(coin("iron"), 1), stack("minecraft:jukebox", 1)),
        offer(stack(coin("copper"), 6), stack("minecraft:music_disc_cat", 1)),
    ],
    "fuel_seller": [
        offer(stack(coin("copper"), 6), stack("minecraft:coal", 16)),
        offer(stack(coin("iron"), 1), stack("minecraft:coal_block", 2)),
        offer(stack(coin("copper"), 2), stack("minecraft:bread", 2)),
    ],
    "medic": [
        offer(stack(coin("iron"), 1), stack("minecraft:golden_apple", 1)),
        offer(stack(coin("copper"), 8), stack("minecraft:potion", 1)),
    ],
    "foreman": [
        offer(stack(coin("copper"), 6), stack("minecraft:stone", 32)),
        offer(stack(coin("iron"), 1), stack("minecraft:iron_ingot", 3)),
    ],
    "builder": [
        offer(stack(coin("copper"), 5), stack("minecraft:scaffolding", 16)),
        offer(stack(coin("iron"), 1), stack("minecraft:smooth_stone", 32)),
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
]


def mob_clean_function() -> str:
    """
    Убирает враждебных мобов в черте города выше уровня земли.

    Под землёй и за городом мобы остаются: там всё как в обычной игре.
    Отключить: /schedule clear citylife:city/mob_clean
    """
    x0, z0, x1, z1 = P.CITY_BOUNDS
    selector = f"x={x0},y=60,z={z0},dx={x1 - x0},dy=220,dz={z1 - z0}"
    lines = [
        "# Город не зарастает мобами: чистим только надземную часть внутри границ.",
        "# Отключить: /schedule clear citylife:city/mob_clean",
    ]
    lines += [f"kill @e[type=minecraft:{mob},{selector}]" for mob in HOSTILE_MOBS]
    lines.append("schedule function citylife:city/mob_clean 15s replace")
    return "\n".join(lines)


def npc_command(spot: dict) -> str:
    """Команда summon для одного NPC."""
    trades = ROLE_TRADES.get(spot["role"], [])
    name = json.dumps({"text": spot["title"]}, ensure_ascii=False)
    tags = ["citylife_npc", f"citylife_{spot['role']}"]
    data: dict = {
        "CustomName": name,
        "CustomNameVisible": True,
        "Tags": tags,
        "PersistenceRequired": True,
        "Invulnerable": True,
        "Rotation": [float(spot["rotation"]), 0.0],
        "NoAI": True,
    }
    if trades:
        entity = TRADER_ENTITY
        data["VillagerData"] = {
            "type": "minecraft:plains",
            "profession": ROLE_PROFESSION.get(spot["role"], "minecraft:nitwit"),
            "level": 5,
        }
        data["Xp"] = 250
        data["Offers"] = {"Recipes": trades}
    else:
        entity = FLAVOUR_ENTITY
    return f"summon {entity} {spot['x']}.5 {spot['y']} {spot['z']}.5 {to_snbt(data)}"


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
    return {"trigger": "minecraft:placed_block", "conditions": {"block": block}}


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


COINS = [coin(k) for k in ("copper", "iron", "gold", "emerald", "diamond", "netherite")]


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
        {"has_phone": has_items("citylife:smartphone")})

    tree["story/first_money"] = advancement(
        "Первые деньги", "Заработай первую монету.",
        "lightmanscurrency:coin_copper", "citylife:story/phone",
        {"has_coin": has_items(*COINS)})

    tree["story/bank_account"] = advancement(
        "Счёт в банке", "Получи банковскую карту в городском банке.",
        "lightmanscurrency:atm_card", "citylife:story/first_money",
        {"has_card": has_items("lightmanscurrency:atm_card")})

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
        "Свой бизнес", "Поставь кассу и открой собственную торговлю.",
        "lightmanscurrency:cash_register", "citylife:story/own_keys",
        {"placed_register": placed("lightmanscurrency:cash_register")})

    tree["story/legal_security"] = advancement(
        "Под охраной", "Поставь камеру или кодовый замок на своём объекте.",
        "securitycraft:security_camera", "citylife:story/legal_shop",
        {
            "camera": placed("securitycraft:security_camera"),
            "keypad": placed("securitycraft:keypad"),
        })
    tree["story/legal_security"]["requirements"] = [["camera", "keypad"]]

    tree["story/legal_empire"] = advancement(
        "Городской предприниматель", "Собери сундук монет — дело пошло.",
        "lightmanscurrency:coin_chest", "citylife:story/legal_security",
        {"has_chest": has_items("lightmanscurrency:coin_chest")},
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
        {"text": "Современный город: работа, деньги, телефоны, камеры, "
                 "умные замки и оружие.\n\n"},
        {"text": "Цели видны в меню достижений (клавиша L)."},
    ],
    [
        {"text": "С чего начать\n\n", "bold": True},
        {"text": "1. Салон связи — купи смартфон.\n"
                 "2. Банк — возьми карту и открой счёт.\n"
                 "3. Найди работу у NPC в ТЦ.\n"
                 "4. Сними или купи жильё и поставь умный замок.\n"},
    ],
    [
        {"text": "Телефон\n\n", "bold": True},
        {"text": "Правый клик — 6 приложений: сообщения, контакты, банк, "
                 "карта, умный дом и вызов 112.\n\n"
                 "Замок привязывается к телефону: кликни телефоном по замку."},
    ],
    [
        {"text": "Деньги\n\n", "bold": True},
        {"text": "Монеты Lightman's Currency: медь, железо, золото, изумруд.\n\n"
                 "Банкоматы и кассы стоят в банке и ТЦ. Свой магазин — "
                 "поставь кассу и настрой ассортимент."},
    ],
    [
        {"text": "Транспорт\n\n", "bold": True},
        {"text": "Машины Immersive Vehicles собираются на верстаке мода, "
                 "заправка — на АЗС.\n\n"
                 "Метро: три станции, рельсы между ними уже проложены.\n\n"
                 "Эстакада идёт с севера на юг по восточной части города."},
    ],
    [
        {"text": "Свободные участки\n\n", "bold": True},
        {"text": "Участки с табличкой ПРОДАЁТСЯ свободны под ваши постройки: "
                 "их примерно треть в жилых кварталах.\n\n"
                 "Границу карты не перейти: город закрыт в квадрате 2048 блоков."},
    ],
]


def guide_function() -> str:
    pages = [json.dumps(page, ensure_ascii=False) for page in GUIDE_PAGES]
    pages_snbt = ",".join("'" + page.replace("'", "\\'") + "'" for page in pages)
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
        "gamerule doInsomnia false",
        "gamerule doPatrolSpawning false",
        "gamerule doTraderSpawning false",
        "gamerule announceAdvancements true",
        "gamerule spawnRadius 2",
        f"setworldspawn {P.SPAWN[0]} {P.SPAWN[1]} {P.SPAWN[2]}",
        "scoreboard objectives add citylife_jobs dummy \"Выполненные работы\"",
        "# Запускаем периодическую уборку мобов в черте города.",
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

    spawn_all = ["# Расставить всех городских NPC (можно вызывать повторно",
                 "# после citylife:npc/clear).",
                 "function citylife:npc/clear"]
    spawn_all += [f"function citylife:npc/spawn_{i:02d}" for i in range(1, len(batches) + 1)]
    spawn_all.append(f'tellraw @a {{"text":"Город заселён: NPC {len(spots)}",'
                     f'"color":"green"}}')
    write(os.path.join(functions, "npc", "spawn_all.mcfunction"), "\n".join(spawn_all))

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
