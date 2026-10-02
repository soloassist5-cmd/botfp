#!/usr/bin/env python3
"""
Текстуры и модели техники башни STARK: стеклянный телефон Старка,
3D-принтер, голо-монитор, лазерный датчик и пульт охраны.

Всё рисуется кодом, как гаджеты в gen_gadgets.py: стекло — настоящая
полупрозрачность (render_type translucent), голубое свечение — светлые
пиксели поверх тёмного металла.

    python3 citylife/tools/gen_stark.py
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Canvas  # noqa: E402
from gen_gadgets import ASSETS, DATA, ITEMS, BLOCKS, write_json, box, ALL  # noqa: E402

CYAN = (87, 216, 255, 255)
CYAN_DIM = (60, 150, 190, 255)
WHITE = (232, 251, 255, 255)
METAL = (34, 38, 46, 255)
METAL_DARK = (20, 23, 29, 255)
METAL_LIGHT = (58, 64, 76, 255)
RED = (255, 64, 64, 255)


def tex(name: str, c: Canvas, folder: str = BLOCKS) -> None:
    c.write(os.path.join(folder, f"{name}.png"))


# ---------------------------------------------------------------------------
#  Текстуры
# ---------------------------------------------------------------------------

def phone_texture() -> None:
    """Стеклянная пластина: почти прозрачная, светлая кромка, «реактор» камеры, блик."""
    c = Canvas(16, 16)
    c.rect(4, 1, 11, 14, (150, 225, 255, 70))
    c.frame(4, 1, 11, 14, (155, 232, 255, 230))
    c.rect(5, 3, 10, 12, (120, 200, 240, 45))
    for i in range(6):
        c.set(9 - i // 2, 3 + i, (255, 255, 255, 120))
    c.set(7, 2, WHITE)
    c.set(8, 2, CYAN)
    c.rect(6, 5, 9, 5, (87, 216, 255, 170))
    c.rect(6, 7, 8, 7, (87, 216, 255, 140))
    c.rect(6, 9, 9, 9, (87, 216, 255, 110))
    c.set(7, 13, (155, 232, 255, 200))
    c.set(8, 13, (155, 232, 255, 200))
    tex("phone_stark", c, ITEMS)


def printer_textures() -> None:
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, METAL)
    c.rect(0, 0, 15, 0, METAL_LIGHT)
    c.rect(2, 3, 13, 3, CYAN)
    c.rect(4, 6, 11, 11, METAL_DARK)
    c.rect(5, 7, 10, 10, (30, 90, 120, 255))
    c.rect(5, 7, 7, 7, CYAN)
    c.rect(5, 9, 9, 9, CYAN_DIM)
    c.set(12, 9, (92, 255, 157, 255))
    tex("printer_base", c)
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (16, 34, 46, 255))
    for i in range(0, 16, 3):
        c.rect(i, 0, i, 15, (60, 150, 190, 200))
        c.rect(0, i, 15, i, (60, 150, 190, 200))
    tex("printer_bed", c)
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, METAL_LIGHT)
    c.rect(0, 0, 0, 15, (90, 98, 112, 255))
    c.rect(15, 0, 15, 15, METAL)
    tex("printer_frame", c)
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (170, 230, 255, 48))
    c.frame(0, 0, 15, 15, (155, 232, 255, 120))
    for i in range(5):
        c.set(10 - i, 2 + i, (255, 255, 255, 90))
        c.set(12 - i, 2 + i, (255, 255, 255, 60))
    tex("printer_glass", c)


def console_textures() -> None:
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, METAL)
    c.rect(0, 0, 15, 1, METAL_LIGHT)
    c.rect(2, 4, 13, 4, CYAN_DIM)
    c.rect(2, 12, 13, 13, METAL_DARK)
    c.set(3, 12, RED)
    c.set(5, 12, (92, 255, 157, 255))
    tex("console_side", c)
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (10, 22, 32, 255))
    c.frame(0, 0, 15, 15, CYAN_DIM)
    # Голо-карта башни и клавиши.
    c.ring(5.5, 5.5, 4.2, 1.0, CYAN)
    c.disc(5.5, 5.5, 1.3, WHITE)
    for x in range(10, 15):
        c.set(x, 2, CYAN)
        c.set(x, 4, CYAN_DIM)
        c.set(x, 6, CYAN_DIM)
    for row in range(2):
        for col in range(6):
            c.set(2 + col * 2, 11 + row * 2, (120, 200, 240, 255))
    c.set(13, 13, RED)
    tex("console_panel", c)


def laser_textures() -> None:
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, METAL_DARK)
    c.frame(0, 0, 15, 15, METAL_LIGHT)
    c.set(2, 2, RED)
    tex("laser_body", c)
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (120, 10, 10, 255))
    c.disc(7.5, 7.5, 5.5, (220, 30, 30, 255))
    c.disc(7.5, 7.5, 2.5, (255, 200, 200, 255))
    tex("laser_lens", c)


def screen_icon() -> None:
    c = Canvas(16, 16)
    c.rect(0, 3, 15, 12, (8, 24, 36, 210))
    c.frame(0, 3, 15, 12, CYAN)
    c.ring(4.5, 7.5, 3.2, 1.0, CYAN)
    c.set(4, 7, WHITE)
    for x in range(9, 15):
        c.set(x, 5, CYAN)
    for x in range(9, 13):
        c.set(x, 7, CYAN_DIM)
    for x in range(9, 14):
        c.set(x, 9, CYAN_DIM)
    tex("holo_screen", c, ITEMS)
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, (8, 24, 36, 200))
    c.frame(0, 0, 15, 15, CYAN)
    tex("holo_screen", c)


# ---------------------------------------------------------------------------
#  Модели
# ---------------------------------------------------------------------------

def printer_model() -> dict:
    els = [
        box([0, 0, 0], [16, 2, 16], {"north": "#base", "south": "#frame", "east": "#frame", "west": "#frame",
                                    "up": "#frame", "down": "#frame"}),
        box([2, 2, 2], [14, 3, 14], {f: "#bed" for f in ALL}),
    ]
    for x0, z0 in ((0, 0), (14.5, 0), (0, 14.5), (14.5, 14.5)):
        els.append(box([x0, 2, z0], [x0 + 1.5, 16, z0 + 1.5], {f: "#frame" for f in ALL}))
    els += [
        box([1.5, 14.5, 0], [14.5, 16, 1.5], {f: "#frame" for f in ALL}),
        box([1.5, 14.5, 14.5], [14.5, 16, 16], {f: "#frame" for f in ALL}),
        box([0, 14.5, 1.5], [1.5, 16, 14.5], {f: "#frame" for f in ALL}),
        box([14.5, 14.5, 1.5], [16, 16, 14.5], {f: "#frame" for f in ALL}),
        # Стёкла со всех четырёх сторон и крышка.
        box([1.5, 2, 0.5], [14.5, 14.5, 1], {"north": "#glass", "south": "#glass"}),
        box([1.5, 2, 15], [14.5, 14.5, 15.5], {"north": "#glass", "south": "#glass"}),
        box([0.5, 2, 1.5], [1, 14.5, 14.5], {"east": "#glass", "west": "#glass"}),
        box([15, 2, 1.5], [15.5, 14.5, 14.5], {"east": "#glass", "west": "#glass"}),
        box([1.5, 15, 1.5], [14.5, 15.5, 14.5], {"up": "#glass", "down": "#glass"}),
    ]
    return {
        "parent": "block/block",
        "render_type": "minecraft:translucent",
        "textures": {"base": "citylife:block/printer_base", "bed": "citylife:block/printer_bed",
                     "frame": "citylife:block/printer_frame", "glass": "citylife:block/printer_glass",
                     "particle": "citylife:block/printer_frame"},
        "elements": els,
    }


def console_model() -> dict:
    return {
        "parent": "block/block",
        "textures": {"side": "citylife:block/console_side", "panel": "citylife:block/console_panel",
                     "particle": "citylife:block/console_side"},
        "elements": [
            box([1, 0, 1], [15, 10, 15], {"north": "#side", "south": "#side", "east": "#side", "west": "#side",
                                         "down": "#side", "up": "#side"}),
            {"from": [0, 9, 0], "to": [16, 11, 16],
             "rotation": {"origin": [8, 10, 8], "axis": "x", "angle": -22.5},
             "faces": {"up": {"texture": "#panel"}, "north": {"texture": "#side"},
                       "south": {"texture": "#side"}, "east": {"texture": "#side"},
                       "west": {"texture": "#side"}, "down": {"texture": "#side"}}},
        ],
    }


def laser_model() -> dict:
    return {
        "parent": "block/block",
        "textures": {"body": "citylife:block/laser_body", "lens": "citylife:block/laser_lens",
                     "particle": "citylife:block/laser_body"},
        "elements": [
            box([5, 5, 13], [11, 11, 16], {f: "#body" for f in ALL}),
            box([6, 6, 12], [10, 10, 13], {"north": "#lens", "east": "#body", "west": "#body", "up": "#body",
                                          "down": "#body"}),
        ],
    }


def main() -> int:
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(BLOCKS, exist_ok=True)
    phone_texture()
    printer_textures()
    console_textures()
    laser_textures()
    screen_icon()

    write_json(os.path.join(ASSETS, "models", "item", "phone_stark.json"), {
        "parent": "minecraft:item/generated",
        "render_type": "minecraft:translucent",
        "textures": {"layer0": "citylife:item/phone_stark"},
    })
    models = {"printer_3d": printer_model(), "security_console": console_model(),
              "laser_sensor": laser_model(),
              "holo_screen": {"textures": {"particle": "citylife:block/holo_screen"}}}
    for name, model in models.items():
        write_json(os.path.join(ASSETS, "models", "block", f"{name}.json"), model)
        item = ({"parent": "minecraft:item/generated", "textures": {"layer0": "citylife:item/holo_screen"}}
                if name == "holo_screen" else {"parent": f"citylife:block/{name}"})
        write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"), item)
        write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": {
            f"facing={facing}": ({"model": f"citylife:block/{name}", "y": rot} if rot
                                 else {"model": f"citylife:block/{name}"})
            for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
        }})
        write_json(os.path.join(DATA, "loot_tables", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"citylife:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })
    print("STARK: телефон, принтер, пульт, датчик, монитор")
    return 0


if __name__ == "__main__":
    sys.exit(main())
