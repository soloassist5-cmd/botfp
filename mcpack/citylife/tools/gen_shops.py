#!/usr/bin/env python3
"""
Прилавки NPC для мода citylife.

Easy NPC делает жителей похожими на игроков, но готовые предложения из NBT
выбрасывает: при спавне тег Offers исчезает (проверено на сервере). Поэтому
торговлю ведёт наш мод, а ассортимент берётся из того же места, что и раньше —
ROLE_TRADES в build/gen_datapack.py. Здесь он просто переносится в Java.

    python3 citylife/tools/gen_shops.py
"""
from __future__ import annotations

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.dirname(ROOT)
sys.path.insert(0, os.path.join(PACK, "build"))

import gen_datapack as G  # noqa: E402

sys.path.insert(0, os.path.join(PACK, "world", "generator"))
from citygen import plan as P  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "java", "dev", "lscity", "citylife", "trade",
                   "ShopCatalog.java")

TITLES = {
    "trader_food": "Продукты", "trader_clothes": "Одежда", "trader_tech": "Электроника",
    "phone_seller": "Салон связи", "banker": "Городской банк", "gunsmith": "Мастерская оружейника",
    "arms_dealer": "Пистолеты, ПП, дробовики", "rifle_dealer": "Винтовки и пулемёты",
    "ammo_seller": "Патроны",
    "car_dealer": "Автосалон", "realtor": "Агентство недвижимости", "clerk": "Мэрия",
    "cook": "Закусочная", "shopkeeper": "Магазин", "bartender": "Бар",
    "fuel_seller": "Заправка", "medic": "Аптека", "foreman": "Прораб", "builder": "Стройка",
}


# Реплики тех, кто ничего не продаёт.
#
# Полицейский, охранник и пожарный стоят на постах ради вида города, но
# молчащий человек выглядит сломанным. Пара строк на роль — и город
# отвечает игроку, даже когда торговать нечем.
LINES = {
    "cop": ["Порядок в городе — моя работа. Проезжай, не задерживайся.",
            "Оружие носи в кобуре. Увижу в руках — разговор будет другой.",
            "Потерял машину? Смотри на стоянке у мэрии, туда всё свозят."],
    "security": ["Вход свободный, но за витрины отвечаешь ты.",
                 "Сумки на входе не проверяем. Пока.",
                 "Драку начнёшь — вынесу на улицу сам."],
    "firefighter": ["Огонь в жилом квартале — сразу к нам, не тушите сами.",
                    "Каска на голове, вода в баке. Живём.",
                    "Без учений скучно, с учениями тяжело."],
    "state": ["Приём граждан по будням. Сегодня, считай, будни.",
              "Все бумаги — в мэрию, там и очередь короче."],
}

DEFAULT_LINES = ["Добрый день. Хорошего дня в городе."]


def java_lines(values: list) -> str:
    """Список строк для Java: кавычки внутри реплик не используем."""
    return ", ".join('"' + value.replace('"', '\\"') + '"' for value in values)


def item_line(stack: dict) -> str:
    """Один предмет предложения в виде вызова конструктора Java."""
    item = stack["id"]
    count = int(stack["Count"])
    tag = stack.get("tag")
    if tag is None:
        return f'new ShopItem("{item}", {count}, null)'
    import json
    snbt = G.to_snbt(tag).replace("\\", "\\\\").replace('"', '\\"')
    return f'new ShopItem("{item}", {count}, "{snbt}")'


def names() -> dict[str, str]:
    """Имя жителя на табличке -> роль, по плану города."""
    out: dict[str, str] = {}
    for spot in P.build_plan(20260927).npc_spots:
        out.setdefault(spot["title"], spot["role"])
    for role, title, _sign, _weight in P.SHOP_KINDS:
        out.setdefault(title, role)
    for role, title in P.LABEL_PROFILE.values():
        out.setdefault(title, role)
    return out


def main() -> int:
    rows = []
    for role, offers in sorted(G.ROLE_TRADES.items()):
        entries = []
        for offer in offers:
            entries.append(f"                    new ShopOffer({item_line(offer['buy'])}, "
                           f"{item_line(offer['sell'])})")
        title = TITLES.get(role, "Магазин")
        rows.append(f'            Map.entry("{role}", new Shop("{title}", List.of(\n'
                    + ",\n".join(entries) + ")))")

    lines = [
        "package dev.lscity.citylife.trade;",
        "",
        "import dev.lscity.citylife.trade.Shop.ShopItem;",
        "import dev.lscity.citylife.trade.Shop.ShopOffer;",
        "",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        "/**",
        " * Ассортимент прилавков.",
        " *",
        " * Файл собран скриптом citylife/tools/gen_shops.py из того же списка,",
        " * по которому расставляются NPC, — править руками не нужно.",
        " */",
        "public final class ShopCatalog {",
        "",
        "    public static final Map<String, Shop> BY_ROLE = Map.ofEntries(",
        ",\n".join(rows),
        "    );",
        "",
        "    /** Что говорят те, у кого нет прилавка. */",
        "    public static final Map<String, List<String>> LINES = Map.ofEntries(",
        ",\n".join(f'            Map.entry("{role}", List.of({java_lines(values)}))'
                   for role, values in sorted(LINES.items())),
        "    );",
        "",
        "    /**",
        "     * Роль по имени жителя: запасной путь, если тег роли потерялся",
        "     * (жителя переставили вручную, мир из старой версии и т.п.).",
        "     */",
        "    public static final Map<String, String> BY_NAME = Map.ofEntries(",
        ",\n".join(f'            Map.entry("{name}", "{role}")'
                   for name, role in sorted(names().items())),
        "    );",
        "",
        "    /** Реплика на случай роли без своего текста. */",
        f"    public static final List<String> DEFAULT_LINES = List.of({java_lines(DEFAULT_LINES)});",
        "",
        "    private ShopCatalog() {",
        "    }",
        "}",
        "",
    ]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    total = sum(len(v) for v in G.ROLE_TRADES.values())
    print(f"Прилавков: {len(G.ROLE_TRADES)}, предложений: {total} -> "
          f"{os.path.relpath(OUT, PACK)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
