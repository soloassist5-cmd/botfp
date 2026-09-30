#!/usr/bin/env python3
"""
Экономика в часах работы: docs/economy.md.

Цены берутся из тех же мест, что уходят в игру: прилавки — ROLE_TRADES
(gen_datapack.py), жильё — каталог estate.json, оплата подработок — те же
константы, что в jobs/Jobs.java (продублированы ниже, меняются вместе).

    python3 build/gen_economy_doc.py
"""
from __future__ import annotations

import json
import os
import statistics
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import gen_datapack as G  # noqa: E402

# Как в jobs/Jobs.java.
COURIER_BASE, COURIER_PER_BLOCK = 60, 0.35
TAXI_BASE, TAXI_PER_BLOCK = 80, 0.3
FOOD_BASE, FOOD_PER_BLOCK = 60, 0.25
SHIFT_PAY, SHIFT_SECONDS = 280, 180
GARBAGE_PAY = 380
UPKEEP_PER_MILLE = 3
WALK, DRIVE = 4.3, 12.0          # блоков в секунду пешком и на машине


def per_hour() -> dict[str, int]:
    courier_d = (150 + 700) / 2
    courier = COURIER_BASE + courier_d * COURIER_PER_BLOCK
    courier_time = courier_d / WALK + 30
    taxi_d, pickup = (300 + 900) / 2, (60 + 300) / 2
    taxi = TAXI_BASE + taxi_d * TAXI_PER_BLOCK
    taxi_time = (taxi_d + pickup) / DRIVE + 40
    shift_time = SHIFT_SECONDS + 60
    food_d = (100 + 500) / 2 + 150
    food = FOOD_BASE + food_d * FOOD_PER_BLOCK
    food_time = food_d / DRIVE + 40
    garbage_time = 5 * 60 / WALK * 4 + 60
    return {
        "Курьер (пешком)": round(courier * 3600 / courier_time, -2),
        "Такси (на машине)": round(taxi * 3600 / taxi_time, -2),
        "Доставка еды (на машине)": round(food * 3600 / food_time, -2),
        "Смена, охранник, грузчик": round(SHIFT_PAY * 3600 / shift_time, -2),
        "Мусорщик (пешком)": round(GARBAGE_PAY * 3600 / garbage_time, -2),
        "/work (раз в 15 минут)": round(4 * 350, -2),
    }


def num(value: float) -> str:
    """Число с пробелом между разрядами: 12 000."""
    return f"{int(value):,}".replace(",", " ")


def price_of(offer: dict) -> int:
    value = {"coin_10": 10, "banknote_100": 100, "banknote_1000": 1000, "banknote_5000": 5000}
    return value[offer["buy"]["id"].split(":")[1]] * int(offer["buy"]["Count"])


def main() -> int:
    rates = per_hour()
    hour = statistics.median([v for k, v in rates.items() if not k.startswith("/work")])
    estate = json.load(open(os.path.join(ROOT, "citylife", "src", "main", "resources", "data",
                                         "citylife", "estate.json"), encoding="utf-8"))["units"]
    kinds = {"flat": "Квартира", "rowhouse": "Таунхаус", "house": "Дом", "villa": "Вилла"}
    out = ["# Экономика в часах работы", "",
           "Таблица собирается скриптом `build/gen_economy_doc.py` из цен, которые уходят "
           "в игру. «Часов» — сколько работать, чтобы купить, при заработке "
           f"~{num(hour)} ₽ в час (медиана подработок).", "",
           "## Заработок", "", "| Подработка | ₽ в час |", "|---|---|"]
    out += [f"| {k} | {num(v)} |" for k, v in rates.items()]
    out += ["", "## Жильё", "",
            "| Тип | Дешевле всего | Медиана | Дороже всего | Часов (медиана) | Коммуналка в сутки (медиана) |",
            "|---|---|---|---|---|---|"]
    for kind, title in kinds.items():
        prices = sorted(u["price"] for u in estate if u["kind"] == kind)
        med = statistics.median(prices)
        out.append(f"| {title} | {num(prices[0])} | {num(med)} | {num(prices[-1])} | "
                   f"{med / hour:.1f} | {num(med * UPKEEP_PER_MILLE / 1000)} |")
    out += ["", "## Товары", "", "| Что | Цена, ₽ | Часов |", "|---|---|---|"]
    import gen_trades_doc as D  # noqa: E402 — русские названия товаров

    # (роль, id предмета или кусок названия ящика с машиной)
    picks = [("phone_seller", "citylife:smartphone"), ("phone_seller", "citylife:phone_fold"),
             ("trader_tech", "citylife:laptop"), ("trader_tech", "citylife:gpu_gx1650"),
             ("arms_dealer", "tacz:modern_kinetic_gun"), ("rifle_dealer", "tacz:modern_kinetic_gun"),
             ("car_dealer", "Мопед"), ("car_dealer", "Смарт-кар"), ("car_dealer", "Внедорожник"),
             ("realtor", "citylife:smart_lock"), ("realtor", "securitycraft:security_camera"),
             ("shopkeeper", "decorative_blocks:chandelier")]
    for role, key in picks:
        for o in G.ROLE_TRADES[role]:
            if o["buy"]["id"].startswith("minecraft:"):
                continue
            title = D.title(o["sell"])
            if o["sell"]["id"] == key or (key in title and ":" not in key):
                price = price_of(o)
                out.append(f"| {title} | {num(price)} | {price / hour:.1f} |")
                break
    out += ["", "Коммуналка — 0,3% цены жилья за игровые сутки (20 минут). Неделя долга — "
            "жильё отходит городу. Аренда — цену за сутки назначает хозяин.", ""]
    path = os.path.join(ROOT, "docs", "economy.md")
    open(path, "w", encoding="utf-8").write("\n".join(out))
    print("\n".join(out[:20]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
