#!/usr/bin/env python3
"""
Скины жителей города.

NPC должны выглядеть людьми, а не ванильными жителями. Скины рисуем сами и
кладём в мод: так они работают без интернета и без учёток Mojang — Easy NPC
берёт их по ресурс-локации. Каждая роль одета по делу: полицейский в форме,
механик в комбинезоне, банкир в костюме.

Раскладка 64x64 как у обычного скина игрока: голова, тело, руки, ноги и
второй слой (куртка, волосы) поверх.

    python3 citylife/tools/gen_skins.py
"""
from __future__ import annotations

import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Canvas  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "citylife",
                   "textures", "entity", "npc")

SKIN_TONES = [(245, 213, 186), (234, 192, 160), (205, 160, 124),
              (170, 122, 88), (126, 86, 60), (94, 63, 44)]
HAIR = [(38, 28, 22), (70, 45, 28), (120, 78, 40), (186, 154, 96),
        (28, 24, 26), (140, 60, 40)]

# Роль -> (цвет одежды, цвет штанов, цвет обуви, акцент на груди)
ROLES = {
    "citizen_a": ((62, 108, 168), (48, 52, 66), (38, 34, 32), None),
    "citizen_b": ((150, 78, 92), (54, 58, 74), (38, 34, 32), None),
    "citizen_c": ((86, 140, 96), (60, 56, 50), (42, 36, 30), None),
    "citizen_d": ((196, 160, 84), (44, 48, 60), (36, 32, 30), None),
    "shopkeeper": ((238, 238, 242), (52, 56, 70), (40, 36, 34), (46, 134, 222)),
    "banker": ((36, 40, 56), (32, 36, 50), (26, 24, 24), (214, 88, 60)),
    "police": ((36, 62, 118), (30, 40, 70), (24, 24, 28), (226, 196, 86)),
    "medic": ((240, 244, 248), (214, 224, 234), (60, 60, 66), (214, 72, 72)),
    "mechanic": ((196, 116, 52), (78, 66, 58), (40, 34, 30), (60, 56, 52)),
    "builder": ((232, 176, 48), (72, 78, 92), (46, 40, 34), (60, 56, 52)),
    "bartender": ((44, 46, 58), (40, 42, 54), (32, 30, 30), (160, 120, 60)),
    "cook": ((244, 244, 240), (60, 62, 72), (40, 38, 36), (200, 60, 60)),
    "clerk": ((120, 132, 168), (52, 56, 70), (38, 34, 32), None),
    "gunsmith": ((96, 92, 78), (62, 60, 52), (40, 36, 30), (150, 70, 40)),
    "realtor": ((70, 74, 96), (52, 54, 70), (34, 32, 32), (120, 190, 220)),
    "dealer": ((58, 62, 84), (46, 48, 62), (32, 30, 30), (240, 200, 90)),
}


def shade(colour, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in colour)


def box(c: Canvas, x: int, y: int, w: int, h: int, depth: int, colour, rng):
    """
    Развёртка параллелепипеда: сверху крышка и дно, ниже четыре стороны.

    Грани красим чуть разной яркостью — иначе фигура в игре выглядит плоской.
    """
    top = shade(colour, 1.12)
    bottom = shade(colour, 0.75)
    c.rect(x + depth, y, x + depth + w - 1, y + depth - 1, (*top, 255))
    c.rect(x + depth + w, y, x + depth + w * 2 - 1, y + depth - 1, (*bottom, 255))
    sides = [shade(colour, 0.92), colour, shade(colour, 1.04), shade(colour, 0.86)]
    widths = [depth, w, depth, w]
    ox = x
    for side, width in zip(sides, widths):
        for px in range(width):
            for py in range(h):
                jitter = rng.choice((0.97, 1.0, 1.0, 1.03))
                c.set(ox + px, y + depth + py, (*shade(side, jitter), 255))
        ox += width


def face(c: Canvas, x: int, y: int, tone, hair, rng):
    """Лицо: глаза, брови, рот — чтобы NPC не выглядел манекеном."""
    eye_y = y + 4
    for ex in (x + 2, x + 5):
        c.rect(ex, eye_y, ex + 1, eye_y, (250, 250, 252, 255))
        c.set(ex + 1 if rng.random() < 0.5 else ex, eye_y, (46, 62, 96, 255))
    c.rect(x + 2, eye_y - 2, x + 3, eye_y - 2, (*shade(hair, 0.9), 255))
    c.rect(x + 5, eye_y - 2, x + 6, eye_y - 2, (*shade(hair, 0.9), 255))
    c.rect(x + 3, y + 6, x + 5, y + 6, (*shade(tone, 0.78), 255))


def skin(name: str, palette, seed: int) -> None:
    shirt, pants, boots, accent = palette
    rng = random.Random(seed)
    tone = SKIN_TONES[rng.randrange(len(SKIN_TONES))]
    hair = HAIR[rng.randrange(len(HAIR))]

    c = Canvas(64, 64)
    # Голова 8x8x8 и волосы вторым слоем.
    box(c, 0, 0, 8, 8, 8, tone, rng)
    face(c, 8, 8, tone, hair, rng)
    box(c, 32, 0, 8, 8, 8, hair, rng)
    # Шапка-волосы не должны закрывать лицо: очищаем переднюю грань второго слоя.
    for px in range(8):
        for py in range(2, 8):
            c.set(40 + px, 8 + py, (0, 0, 0, 0))

    # Тело 8x12x4.
    box(c, 16, 16, 8, 12, 4, shirt, rng)
    if accent:
        c.rect(24, 20, 27, 22, (*accent, 255))       # нашивка или галстук
        c.rect(25, 24, 26, 27, (*shade(shirt, 0.8), 255))
    # Руки 4x12x4.
    box(c, 40, 16, 4, 12, 4, shirt, rng)
    box(c, 32, 48, 4, 12, 4, shirt, rng)
    for arm_x in (44, 36):
        c.rect(arm_x, 28 - 4, arm_x + 3, 27, (*tone, 255))
    # Ноги 4x12x4.
    box(c, 0, 16, 4, 12, 4, pants, rng)
    box(c, 16, 48, 4, 12, 4, pants, rng)
    for leg_x in (4, 20):
        c.rect(leg_x, 26, leg_x + 3, 27, (*boots, 255))

    os.makedirs(OUT, exist_ok=True)
    c.write(os.path.join(OUT, f"{name}.png"))


def main() -> int:
    for index, (name, palette) in enumerate(ROLES.items()):
        skin(name, palette, seed=1000 + index * 17)
    print(f"Скинов записано: {len(ROLES)} -> {os.path.relpath(OUT, os.path.dirname(ROOT))}")
    print("  " + ", ".join(ROLES))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
