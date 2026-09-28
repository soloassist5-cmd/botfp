#!/usr/bin/env python3
"""
Таблицы в docs/overview.md — из плана города и списка прилавков.

Состав жителей и их товар меняются вместе с генератором, а таблица в обзоре
жила отдельной жизнью и разошлась с игрой. Теперь она собирается между
метками <!-- npc:begin --> и <!-- npc:end -->.

    python3 build/gen_docs.py
"""
from __future__ import annotations

import os
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "build"))
sys.path.insert(0, os.path.join(ROOT, "world", "generator"))

import gen_datapack as G  # noqa: E402
from citygen import plan as P  # noqa: E402

DOC = os.path.join(ROOT, "docs", "overview.md")
BEGIN = "<!-- npc:begin -->"
END = "<!-- npc:end -->"

# Как назвать товар в таблице: идентификатор предмета — не для чтения.
GOODS_RU = {
    "bread": "хлеб", "cooked_beef": "говядина", "golden_carrot": "морковь",
    "sweet_berries": "ягоды", "leather_chestplate": "куртка",
    "leather_boots": "ботинки", "white_dye": "краска", "smartphone": "смартфон",
    "sim_card": "SIM-карта", "redstone": "редстоун", "camera": "фотоаппарат",
    "atm": "банкомат", "gun_smith_table": "верстак оружейника",
    "iron_ingot": "железо", "gunpowder": "порох", "ammo_box": "патроны",
    "iron_block": "железные блоки", "coal": "уголь", "glass": "стекло",
    "smart_lock": "умный замок", "keypad": "кодовый замок",
    "security_camera": "камера", "paper": "бумага", "map": "карта",
    "cooked_chicken": "курица", "pumpkin_pie": "пирог", "cake": "торт",
    "torch": "факелы", "oak_planks": "доски", "stone": "камень",
    "scaffolding": "леса", "golden_apple": "золотые яблоки", "potion": "зелья",
    "car_key": "ключи от машин", "fuel_canister": "канистра",
    "banknote_5000": "5000 ₽", "jukebox": "патефон",
    "iron_pickaxe": "кирка", "bucket": "ведро", "honey_bottle": "лимонад",
    "music_disc_cat": "пластинка", "smooth_stone": "плиты", "compass": "компас",
    "shears": "ножницы", "lantern": "фонарь", "glow_berries": "ягоды",
}


def goods(role: str) -> str:
    trades = G.ROLE_TRADES.get(role, [])
    if not trades:
        return "не торгует, отвечает репликой"
    names = []
    for trade in trades:
        item = trade["sell"]["id"].split(":")[-1]
        name = GOODS_RU.get(item, item)
        if name not in names:
            names.append(name)
    return ", ".join(names[:5])


def plural(number: int, one: str, few: str, many: str) -> str:
    """Форма слова после числа: 1 прилавок, 2 прилавка, 19 прилавков."""
    tail, hundred = number % 10, number % 100
    if 11 <= hundred <= 14 or tail == 0 or tail >= 5:
        return many
    return one if tail == 1 else few


def build() -> str:
    city = P.build_plan(20260927)
    counts = Counter(spot["role"] for spot in city.npc_spots)
    titles = {spot["role"]: spot["title"] for spot in city.npc_spots}
    lines = [BEGIN, "", "| Кто | Сколько | Чем торгует |", "|---|---|---|"]
    for role, count in counts.most_common():
        lines.append(f"| {titles[role]} | {count} | {goods(role)} |")
    shops = len([role for role in G.ROLE_TRADES if G.ROLE_TRADES[role]])
    offers = sum(len(items) for items in G.ROLE_TRADES.values())
    lines += ["", f"Всего {len(city.npc_spots)} жителей. В каталоге {shops} "
                  f"{plural(shops, 'прилавок', 'прилавка', 'прилавков')} и {offers} "
                  f"{plural(offers, 'предложение', 'предложения', 'предложений')}.",
              "", END]
    return "\n".join(lines)


def main() -> int:
    with open(DOC, encoding="utf-8") as fh:
        text = fh.read()
    if BEGIN not in text or END not in text:
        print("В docs/overview.md нет меток npc:begin / npc:end")
        return 1
    head, rest = text.split(BEGIN, 1)
    _, tail = rest.split(END, 1)
    with open(DOC, "w", encoding="utf-8") as fh:
        fh.write(head + build() + tail)
    print("Таблица жителей обновлена")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
