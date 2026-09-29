#!/usr/bin/env python3
"""
Текстуры для рублёвой экономики: банкноты, монеты, карты, банкомат.

Рисуем кодом, а не руками: так все предметы получаются в одном стиле
и любой номинал добавляется одной строкой в таблице ниже.

    python3 citylife/tools/gen_textures.py
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Canvas  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(ROOT, "src", "main", "resources", "assets", "citylife", "textures", "item")
BLOCKS = os.path.join(ROOT, "src", "main", "resources", "assets", "citylife", "textures", "block")

DARK = (26, 26, 34, 255)
SHADE = (0, 0, 0, 60)
LIGHT = (255, 255, 255, 48)

# Номиналы рублёвых банкнот: цвет взят по мотивам настоящих купюр,
# чтобы в инвентаре они различались с одного взгляда.
NOTES = {
    "banknote_50":   ((104, 156, 196), (58, 96, 130), "50"),
    "banknote_100":  ((188, 156, 110), (128, 98,  62), "100"),
    "banknote_500":  ((128, 152, 176), (74,  92, 116), "500"),
    "banknote_1000": ((150, 176, 132), (92, 116,  78), "1K"),
    "banknote_5000": ((188, 128, 128), (124, 72,  72), "5K"),
}

# Цифры 3x5 для номинала на купюре: рисуем пиксельным шрифтом.
GLYPHS = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "5": ["111", "100", "111", "001", "111"],
    "K": ["101", "110", "100", "110", "101"],
}


def digits(c: Canvas, text: str, x: int, y: int, colour) -> None:
    for ch in text:
        rows = GLYPHS.get(ch)
        if rows:
            for dy, row in enumerate(rows):
                for dx, bit in enumerate(row):
                    if bit == "1":
                        c.set(x + dx, y + dy, colour)
        x += 4


def banknote(name: str, face, edge, label: str) -> None:
    c = Canvas(16, 16)
    c.rect(1, 4, 14, 11, (*face, 255))
    c.frame(1, 4, 14, 11, (*edge, 255))
    c.rect(13, 5, 13, 10, (*edge, 110))         # защитная полоса справа
    c.rect(1, 4, 14, 4, LIGHT)                  # блик по верхней кромке
    c.rect(1, 11, 14, 11, SHADE)                # тень по нижней
    # Номинал по центру купюры: ширина глифа 3 плюс пробел, у последнего
    # пробела нет — иначе трёхзначные номиналы вылезают за край.
    span = 4 * len(label) - 1
    digits(c, label, round(7.5 - span / 2), 6, (255, 255, 255, 235))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def coin(name: str, body, rim, label: str) -> None:
    c = Canvas(16, 16)
    c.disc(8, 8, 6.2, (*rim, 255))
    c.disc(8, 8, 5.2, (*body, 255))
    c.ring(8, 8, 6.2, 1.0, (*rim, 255))
    c.disc(6.2, 6.2, 1.6, (255, 255, 255, 70))   # блик
    digits(c, label, 6 if len(label) < 2 else 5, 6, (*rim, 255))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def card(name: str, top, bottom, accent, second=None) -> None:
    c = Canvas(16, 16)
    for y in range(3, 13):
        t = (y - 3) / 9
        colour = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        c.rect(1, y, 14, y, (*colour, 255))
    c.frame(1, 3, 14, 12, (*tuple(max(0, v - 40) for v in bottom), 255))
    c.rect(2, 6, 5, 8, (218, 186, 96, 255))      # чип
    c.frame(2, 6, 5, 8, (150, 120, 40, 255))
    if second is None:
        c.rect(9, 9, 13, 11, (*accent, 255))     # логотип «Мир» — плашка
        c.rect(9, 9, 13, 9, (255, 255, 255, 60))
    else:
        c.disc(10.2, 10.0, 2.4, (*accent, 255))  # два круга Mastercard
        c.disc(12.4, 10.0, 2.4, (*second, 235))
    c.rect(1, 3, 14, 3, LIGHT)
    c.write(os.path.join(ITEMS, f"{name}.png"))


def canister() -> None:
    """Канистра с бензином: красный корпус, ручка, носик."""
    c = Canvas(16, 16)
    c.rect(3, 3, 12, 14, (186, 62, 48, 255))
    c.frame(3, 3, 12, 14, (120, 36, 28, 255))
    c.rect(4, 4, 12, 4, LIGHT)
    c.rect(5, 6, 10, 11, (150, 44, 34, 255))     # ребро жёсткости
    c.rect(5, 1, 9, 2, (120, 36, 28, 255))       # ручка
    c.rect(6, 2, 8, 2, (0, 0, 0, 0))
    c.rect(11, 1, 12, 3, (70, 70, 76, 255))      # носик
    c.rect(3, 14, 12, 14, SHADE)
    c.write(os.path.join(ITEMS, "fuel_canister.png"))


def atm_front() -> None:
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (58, 62, 74, 255))
    c.frame(0, 0, 15, 15, (38, 42, 52, 255))
    c.rect(2, 2, 13, 7, (24, 46, 58, 255))       # экран
    c.frame(2, 2, 13, 7, (16, 30, 40, 255))
    c.rect(3, 3, 12, 4, (53, 199, 240, 210))     # строка на экране
    c.rect(3, 5, 9, 5, (53, 199, 240, 120))
    for row in range(3):                          # клавиатура
        for col in range(4):
            x = 3 + col * 3
            y = 9 + row * 2
            c.rect(x, y, x + 1, y, (120, 126, 140, 255))
    c.rect(11, 13, 13, 14, (218, 186, 96, 255))  # лоток выдачи
    c.rect(0, 0, 15, 0, LIGHT)
    c.rect(0, 15, 15, 15, SHADE)
    c.write(os.path.join(BLOCKS, "atm_front.png"))


def atm_side() -> None:
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (52, 56, 68, 255))
    c.frame(0, 0, 15, 15, (38, 42, 52, 255))
    c.rect(2, 3, 13, 12, (46, 50, 62, 255))
    c.rect(0, 0, 15, 0, LIGHT)
    c.rect(0, 15, 15, 15, SHADE)
    c.write(os.path.join(BLOCKS, "atm_side.png"))


def atm_top() -> None:
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (66, 70, 84, 255))
    c.frame(0, 0, 15, 15, (38, 42, 52, 255))
    c.rect(3, 3, 12, 12, (74, 78, 92, 255))
    c.write(os.path.join(BLOCKS, "atm_top.png"))


def main() -> int:
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(BLOCKS, exist_ok=True)
    for name, (face, edge, label) in NOTES.items():
        banknote(name, face, edge, label)
    coin("coin_1", (196, 180, 140), (140, 120, 80), "1")
    coin("coin_10", (214, 190, 110), (150, 122, 56), "10")
    card("card_mir", (24, 108, 180), (16, 62, 120), (86, 196, 132))
    card("card_mastercard", (52, 56, 70), (28, 30, 40), (214, 88, 60), (240, 166, 60))
    canister()
    atm_front()
    atm_side()
    atm_top()
    made = len(NOTES) + 2 + 2 + 2 + 3
    print(f"Текстур записано: {made}")
    print(f"  предметы: {ITEMS}")
    print(f"  блоки:    {BLOCKS}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
