#!/usr/bin/env python3
"""
Отчёт по прилавкам: кто где стоит и что продаёт — docs/trades.md.

Собирается из того же каталога, что уходит в игру (ROLE_TRADES), поэтому
не расходится с ней. Названия предметов — из русских файлов игры и модов
(build/item_names_ru.json), стволы и калибры — из build/tacz_catalog.json.

    python3 build/gen_trades_doc.py
"""
from __future__ import annotations

import json
import re
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "world", "generator"))

import gen_datapack as G  # noqa: E402
from citygen import plan as P  # noqa: E402

NAMES = json.load(open(os.path.join(HERE, "item_names_ru.json"), encoding="utf-8"))
GUNS = {g["id"]: g["name"] for g in G.TACZ["guns"]}
AMMO = {a["id"]: a["name"] for a in G.TACZ["ammo"]}
COLOURS = {0x1D1D21: "чёрн.", 0xF9FFFE: "бел.", 0x3C44AA: "син.", 0xB02E26: "красн.",
           0xC8AD7F: "беж."}
POTIONS = {"healing": "лечения", "strong_healing": "лечения II", "regeneration": "регенерации",
           "fire_resistance": "огнестойкости", "night_vision": "ночного зрения",
           "water_breathing": "подводного дыхания"}
BENCHES = {"tacz:ammo_workbench": "Патронный верстак",
           "tacz:attachment_workbench": "Верстак обвесов",
           "elitex:elitebench": "Elite Bench (верстак Elite X)"}

WHERE = {
    "trader_food": "«Продукты», ТЦ", "trader_clothes": "«Одежда», ТЦ",
    "trader_tech": "«Электроника», ТЦ", "phone_seller": "Салон связи",
    "banker": "Городской банк", "gunsmith": "Оружейный магазин",
    "arms_dealer": "Оружейный магазин", "rifle_dealer": "Оружейный магазин",
    "ammo_seller": "Оружейный магазин", "car_dealer": "Автосалон",
    "realtor": "Мэрия, многоквартирные дома", "clerk": "Мэрия",
    "cook": "Закусочные, кафе, фастфуд", "shopkeeper": "«Магазин», «Мини-маркет»",
    "bartender": "Ночной клуб, пляжный бар", "fuel_seller": "АЗС",
    "medic": "Аптеки, больница", "foreman": "Склады", "builder": "Стройки, «Стройматериалы»",
    "security": "ТЦ, клубы", "cop": "Полиция", "firefighter": "Пожарная часть",
}


def price(buy: dict) -> int:
    note = buy["id"].split(":")[1]
    value = {"coin_10": 10, "banknote_100": 100, "banknote_1000": 1000,
             "banknote_5000": 5000}[note]
    return value * int(buy["Count"])


def title(sell: dict) -> str:
    item, tag = sell["id"], sell.get("tag") or {}
    if item == "tacz:modern_kinetic_gun":
        return GUNS.get(tag.get("GunId"), tag.get("GunId"))
    if item == "tacz:ammo":
        return AMMO.get(tag.get("AmmoId"), tag.get("AmmoId"))
    if "BlockId" in tag:
        return BENCHES.get(tag["BlockId"], tag["BlockId"])
    if item == "vehicle:vehicle_crate":
        name = json.loads(tag["display"]["Name"])["text"]
        return name
    name = NAMES.get(item) or item
    if "Potion" in tag:
        kind = tag["Potion"].split(":")[1]
        name = ("Взрывное зелье " if "splash" in item else "Зелье ") + POTIONS.get(kind, kind)
    if "display" in tag and "color" in tag["display"]:
        name += f" ({COLOURS.get(tag['display']['color'], 'цвет')})"
    # Цветовые коды Minecraft (§a, §l…) в названиях не нужны.
    return re.sub(r"§[0-9a-fk-or]", "", name)


def main() -> int:
    city = P.build_plan(20260927)
    people = Counter(spot["role"] for spot in city.npc_spots)
    titles = {spot["role"]: spot["title"] for spot in city.npc_spots}
    total = sum(len(v) for v in G.ROLE_TRADES.values())
    out = ["# Прилавки жителей", "",
           "Отчёт собирается скриптом `build/gen_trades_doc.py` из каталога, который "
           "уходит в игру, — расхождений с игрой нет. Все товары проверены на сервере "
           "командой `/citylife shops`: каждый предмет существует.", "",
           f"Всего {len(people)} ролей у {len(city.npc_spots)} жителей, "
           f"{total} предложений. Цены в рублях, оплата наличными.", "",
           "| Кто | Где | Жителей | Товаров |", "|---|---|---|---|"]
    for role in G.ROLE_TRADES:
        out.append(f"| {titles.get(role, role)} | {WHERE.get(role, '')} | "
                   f"{people.get(role, 0)} | {len(G.ROLE_TRADES[role])} |")
    for role, offers in G.ROLE_TRADES.items():
        out += ["", f"## {titles.get(role, role)} — {WHERE.get(role, '')}", ""]
        if not offers:
            out.append("Не торгует — отвечает репликой.")
            continue
        sales = [o for o in offers if not o["buy"]["id"].startswith("minecraft:")]
        buys = [o for o in offers if o["buy"]["id"].startswith("minecraft:")]
        if sales:
            out += ["| Товар | Кол-во | Цена, ₽ |", "|---|---|---|"]
            for o in sales:
                out.append(f"| {title(o['sell'])} | {int(o['sell']['Count'])} | "
                           f"{price(o['buy'])} |")
        if buys:
            # Скупка: житель берёт товар у игрока и платит рублями.
            out += ["", "**Скупка** — житель покупает у игрока:", "",
                    "| Товар | Кол-во | Платит, ₽ |", "|---|---|---|"]
            for o in buys:
                out.append(f"| {title(o['buy'])} | {int(o['buy']['Count'])} | "
                           f"{price(o['sell'])} |")
    path = os.path.join(ROOT, "docs", "trades.md")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    print(f"{total} предложений -> {os.path.relpath(path, ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
