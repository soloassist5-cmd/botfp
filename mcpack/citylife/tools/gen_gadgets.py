#!/usr/bin/env python3
"""
Текстуры и модели гаджетов: телефоны, планшет, ноутбук, комплектующие ПК,
периферия и пункт выдачи маркетплейса.

Как и деньги, всё рисуется кодом в одном стиле: новый телефон или видеокарта
добавляются строкой в таблице, а не походом в редактор.

    python3 citylife/tools/gen_gadgets.py
"""
from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Canvas  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "citylife")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "citylife")
ITEMS = os.path.join(ASSETS, "textures", "item")
BLOCKS = os.path.join(ASSETS, "textures", "block")

INK = (18, 20, 26, 255)
SHINE = (255, 255, 255, 70)


def rgba(rgb, a=255):
    return (*rgb, a)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def write_json(path: str, data: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


# ---------------------------------------------------------------------------
#  Телефоны, планшет, ноутбук
# ---------------------------------------------------------------------------

# body, экран сверху, экран снизу, (x0, y0, x1, y1) корпуса
PHONES = {
    "phone_nokta":   ((44, 58, 70), (167, 201, 87), (139, 172, 63), (4, 1, 11, 14)),
    "phone_gran_a5": ((52, 62, 80), (74, 144, 226), (40, 70, 140), (4, 1, 11, 14)),
    "phone_gran_x":  ((26, 26, 30), (255, 140, 90), (120, 40, 110), (4, 1, 11, 14)),
    "phone_polus":   ((70, 86, 48), (60, 200, 170), (20, 90, 90), (3, 1, 12, 14)),
    "phone_fold":    ((56, 48, 90), (150, 110, 255), (60, 40, 140), (2, 2, 13, 13)),
    "phone_mini":    ((58, 123, 213), (255, 214, 102), (240, 130, 80), (5, 3, 10, 13)),
}


def phone(name: str, body, top, bottom, box) -> None:
    x0, y0, x1, y1 = box
    c = Canvas(16, 16)
    c.rect(x0, y0, x1, y1, rgba(body))
    c.frame(x0, y0, x1, y1, rgba(mix(body, (0, 0, 0), 0.45)))
    if name == "phone_nokta":
        # Маленький экран сверху и клавиатура снизу.
        for y in range(3, 7):
            c.rect(x0 + 1, y, x1 - 1, y, rgba(mix(top, bottom, (y - 3) / 4)))
        for row in range(3):
            for col in range(3):
                c.set(x0 + 1 + col * 2, 8 + row * 2, (200, 210, 220, 255))
        c.set(x0 + 1, 7, (60, 180, 90, 255))
        c.set(x1 - 1, 7, (200, 60, 60, 255))
    else:
        sx0, sy0, sx1, sy1 = x0 + 1, y0 + 1, x1 - 1, y1 - 1
        for y in range(sy0, sy1 + 1):
            c.rect(sx0, y, sx1, y, rgba(mix(top, bottom, (y - sy0) / max(1, sy1 - sy0))))
        c.rect(sx0, sy0, sx1, sy0, SHINE)
        if name == "phone_fold":
            c.rect(7, sy0, 7, sy1, (0, 0, 0, 70))
        if name == "phone_polus":
            # Резиновые бамперы по углам.
            for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
                c.set(x, y, (30, 34, 24, 255))
        c.set((x0 + x1) // 2, y0, (0, 0, 0, 200))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def tablet() -> None:
    c = Canvas(16, 16)
    c.rect(1, 3, 14, 12, rgba((32, 36, 46)))
    c.frame(1, 3, 14, 12, rgba((14, 16, 22)))
    for y in range(4, 12):
        c.rect(2, y, 13, y, rgba(mix((90, 190, 255), (70, 60, 180), (y - 4) / 7)))
    c.rect(2, 4, 13, 4, SHINE)
    c.set(7, 3, (0, 0, 0, 200))
    c.write(os.path.join(ITEMS, "tablet.png"))


def laptop() -> None:
    c = Canvas(16, 16)
    c.rect(2, 2, 13, 9, rgba((44, 48, 56)))
    for y in range(3, 9):
        c.rect(3, y, 12, y, rgba(mix((80, 170, 250), (40, 60, 140), (y - 3) / 5)))
    c.rect(3, 3, 12, 3, SHINE)
    c.rect(1, 10, 14, 12, rgba((110, 116, 128)))
    c.rect(1, 12, 14, 12, rgba((70, 74, 84)))
    for x in range(3, 13, 2):
        c.set(x, 11, (60, 64, 74, 255))
    c.write(os.path.join(ITEMS, "laptop.png"))


# ---------------------------------------------------------------------------
#  Комплектующие
# ---------------------------------------------------------------------------

def motherboard(name: str, pcb, accent) -> None:
    c = Canvas(16, 16)
    c.rect(1, 1, 14, 14, rgba(pcb))
    c.frame(1, 1, 14, 14, rgba(mix(pcb, (0, 0, 0), 0.4)))
    c.rect(5, 4, 9, 8, rgba((190, 194, 200)))          # сокет
    c.frame(5, 4, 9, 8, rgba((120, 124, 130)))
    for x in (11, 12):                                 # слоты памяти
        c.rect(x, 3, x, 10, rgba(accent))
    c.rect(3, 11, 12, 11, rgba(accent))                # слот видеокарты
    c.rect(2, 2, 3, 5, rgba((60, 60, 66)))             # разъёмы
    c.set(4, 13, (230, 200, 90, 255))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def cpu(name: str, label) -> None:
    c = Canvas(16, 16)
    c.rect(2, 2, 13, 13, rgba((40, 110, 60)))          # подложка
    for i in range(3, 13, 2):
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            c.set(x, y, (210, 180, 90, 255))
    c.rect(4, 4, 11, 11, rgba((196, 200, 208)))        # крышка
    c.frame(4, 4, 11, 11, rgba((140, 144, 150)))
    c.rect(5, 6, 10, 9, rgba(label))
    c.rect(5, 6, 10, 6, SHINE)
    c.write(os.path.join(ITEMS, f"{name}.png"))


def cooler(name: str, water: bool) -> None:
    c = Canvas(16, 16)
    if water:
        c.rect(1, 2, 14, 7, rgba((40, 44, 52)))        # радиатор
        for x in range(2, 14, 2):
            c.rect(x, 3, x, 6, rgba((110, 116, 128)))
        c.rect(4, 8, 4, 12, rgba((24, 26, 30)))        # трубки
        c.rect(11, 8, 11, 12, rgba((24, 26, 30)))
        c.disc(7.5, 12, 2.6, rgba((30, 34, 40)))       # помпа
        c.ring(7.5, 12, 2.6, 0.8, rgba((90, 200, 255)))
    else:
        c.rect(3, 1, 12, 14, rgba((160, 166, 176)))
        for y in range(2, 14, 2):
            c.rect(3, y, 12, y, rgba((110, 116, 126)))
        c.disc(7.5, 7.5, 3.6, rgba((40, 44, 52)))
        c.disc(7.5, 7.5, 1.2, rgba((200, 204, 210)))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def ram(name: str, pcb, heat, rgb: bool) -> None:
    c = Canvas(16, 16)
    c.rect(1, 5, 14, 10, rgba(pcb))
    c.rect(1, 5, 14, 8, rgba(heat))
    c.frame(1, 5, 14, 10, rgba(mix(heat, (0, 0, 0), 0.5)))
    for x in range(2, 14, 2):
        c.set(x, 10, (220, 190, 80, 255))
    if rgb:
        colours = [(255, 60, 80), (255, 180, 40), (80, 220, 120), (60, 160, 255), (180, 90, 255)]
        for i, x in enumerate(range(2, 14, 3)):
            c.rect(x, 4, x + 2, 4, rgba(colours[i % len(colours)]))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def gpu(name: str, shroud, accent, fans: int) -> None:
    c = Canvas(16, 16)
    c.rect(0, 4, 15, 11, rgba(shroud))
    c.frame(0, 4, 15, 11, rgba(mix(shroud, (0, 0, 0), 0.5)))
    c.rect(0, 11, 15, 12, rgba((30, 90, 50)))           # плата
    for x in range(3, 14, 2):
        c.set(x, 12, (220, 190, 80, 255))
    width = 14 / fans
    for i in range(fans):
        cx = 0.5 + width * (i + 0.5)
        c.disc(cx, 7.5, min(2.6, width / 2 - 0.2), rgba((24, 26, 30)))
        c.disc(cx, 7.5, 0.8, rgba(accent))
    c.rect(0, 4, 15, 4, rgba(accent))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def psu(name: str, body, label) -> None:
    c = Canvas(16, 16)
    c.rect(1, 3, 14, 12, rgba(body))
    c.frame(1, 3, 14, 12, rgba(mix(body, (0, 0, 0), 0.5)))
    c.disc(6, 7.5, 3.3, rgba((24, 26, 30)))
    for r in (1.2, 2.3):
        c.ring(6, 7.5, r, 0.4, rgba((90, 94, 104)))
    c.rect(10, 5, 13, 6, rgba(label))
    c.rect(10, 9, 13, 10, rgba((40, 42, 48)))
    c.write(os.path.join(ITEMS, f"{name}.png"))


def storage(name: str, ssd: bool) -> None:
    c = Canvas(16, 16)
    if ssd:
        c.rect(1, 6, 14, 9, rgba((24, 30, 40)))
        c.rect(3, 7, 7, 8, rgba((60, 70, 90)))
        c.rect(9, 7, 11, 8, rgba((60, 70, 90)))
        c.rect(1, 6, 2, 9, rgba((220, 190, 80)))
    else:
        c.rect(2, 2, 13, 13, rgba((150, 156, 164)))
        c.frame(2, 2, 13, 13, rgba((90, 94, 100)))
        c.disc(7.5, 7.5, 4.2, rgba((200, 204, 212)))
        c.disc(7.5, 7.5, 1, rgba((90, 94, 100)))
        c.rect(9, 9, 12, 10, rgba((60, 62, 68)))
    c.write(os.path.join(ITEMS, f"{name}.png"))


# ---------------------------------------------------------------------------
#  Блоки: текстуры, модели, состояния, выпадение
# ---------------------------------------------------------------------------

def block_textures() -> None:
    # Корпус: сетка вентиляции, кнопка питания, боковое стекло.
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((26, 28, 34)))
    for y in range(2, 12, 2):
        c.rect(3, y, 12, y, rgba((44, 48, 58)))
    c.disc(7.5, 13.5, 1.2, rgba((90, 200, 255)))
    c.write(os.path.join(BLOCKS, "pc_case_front.png"))
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((26, 28, 34)))
    c.rect(2, 2, 13, 13, (60, 90, 120, 180))
    for i, colour in enumerate(((255, 80, 120), (120, 90, 255), (60, 200, 255))):
        c.rect(3, 4 + i * 3, 12, 4 + i * 3, rgba(colour, 200))
    c.write(os.path.join(BLOCKS, "pc_case_side.png"))
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((30, 32, 38)))
    for x in range(2, 14, 3):
        for y in range(2, 14, 3):
            c.rect(x, y, x + 1, y + 1, rgba((18, 20, 24)))
    c.write(os.path.join(BLOCKS, "pc_case_top.png"))

    # Монитор: экран с рабочим столом и тёмная рамка.
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((14, 15, 18)))
    for y in range(1, 15):
        c.rect(1, y, 14, y, rgba(mix((60, 150, 240), (40, 40, 120), y / 14)))
    for x in range(3, 13, 3):
        c.rect(x, 3, x + 1, 4, rgba((255, 255, 255), 200))
    c.rect(1, 13, 14, 14, rgba((10, 12, 16), 220))
    c.write(os.path.join(BLOCKS, "monitor_screen.png"))
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((22, 24, 30)))
    c.write(os.path.join(BLOCKS, "monitor_body.png"))

    # Клавиатура: клавиши рядами.
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((30, 32, 38)))
    for row in range(4):
        for key in range(7):
            c.rect(1 + key * 2, 2 + row * 3, 1 + key * 2, 3 + row * 3, rgba((80, 84, 96)))
    c.rect(4, 13, 11, 14, rgba((80, 84, 96)))
    c.write(os.path.join(BLOCKS, "keyboard.png"))

    # Мышь и гарнитура: тёмный пластик с подсветкой.
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((34, 36, 42)))
    c.rect(7, 0, 8, 6, rgba((90, 200, 255)))
    c.write(os.path.join(BLOCKS, "mouse.png"))
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((28, 30, 36)))
    c.disc(7.5, 7.5, 4, rgba((200, 60, 80)))
    c.write(os.path.join(BLOCKS, "headset.png"))

    # Постамат: ячейки, экран, логотип маркетплейса.
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((123, 47, 191)))
    for row in range(4):
        for col in range(3):
            x = 1 + col * 5
            if col == 1 and row in (1, 2):
                continue
            c.rect(x, 1 + row * 4, x + 3, 3 + row * 4, rgba((150, 90, 210)))
            c.set(x + 3, 2 + row * 4, (240, 240, 255, 255))
    c.rect(6, 5, 9, 10, rgba((20, 22, 30)))
    c.rect(7, 6, 8, 8, rgba((120, 220, 255)))
    c.write(os.path.join(BLOCKS, "pickup_point_front.png"))
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, rgba((110, 40, 170)))
    c.rect(0, 0, 15, 1, rgba((150, 90, 210)))
    c.write(os.path.join(BLOCKS, "pickup_point_side.png"))


def box(frm, to, textures: dict, uv=None) -> dict:
    faces = {}
    for face, tex in textures.items():
        entry = {"texture": tex}
        if uv:
            entry["uv"] = uv
        faces[face] = entry
    return {"from": frm, "to": to, "faces": faces}


ALL = ("north", "south", "east", "west", "up", "down")


def block_models() -> None:
    models = {
        "pc_case": {
            "parent": "block/block",
            "textures": {"front": "citylife:block/pc_case_front",
                         "side": "citylife:block/pc_case_side",
                         "top": "citylife:block/pc_case_top",
                         "particle": "citylife:block/pc_case_top"},
            "elements": [box([4, 0, 1], [12, 15, 15], {
                "north": "#front", "south": "#top", "east": "#side", "west": "#side",
                "up": "#top", "down": "#top"})],
        },
        "monitor": {
            "parent": "block/block",
            "textures": {"screen": "citylife:block/monitor_screen",
                         "body": "citylife:block/monitor_body",
                         "particle": "citylife:block/monitor_body"},
            "elements": [
                box([1, 4, 7], [15, 13, 8], {"north": "#screen", "south": "#body",
                                              "east": "#body", "west": "#body",
                                              "up": "#body", "down": "#body"}),
                box([7, 1, 8], [9, 5, 9], {f: "#body" for f in ALL}),
                box([5, 0, 6], [11, 1, 10], {f: "#body" for f in ALL}),
            ],
        },
        "keyboard": {
            "parent": "block/block",
            "textures": {"top": "citylife:block/keyboard", "particle": "citylife:block/keyboard"},
            "elements": [box([1, 0, 5], [15, 1, 11], {f: "#top" for f in ALL})],
        },
        "mouse": {
            "parent": "block/block",
            "textures": {"top": "citylife:block/mouse", "particle": "citylife:block/mouse"},
            "elements": [box([7, 0, 6], [10, 1.5, 11], {f: "#top" for f in ALL})],
        },
        "headset": {
            "parent": "block/block",
            "textures": {"cup": "citylife:block/headset", "particle": "citylife:block/headset"},
            "elements": [
                box([3, 0, 6], [5, 5, 10], {f: "#cup" for f in ALL}),
                box([11, 0, 6], [13, 5, 10], {f: "#cup" for f in ALL}),
                box([4, 5, 7.5], [12, 7, 8.5], {f: "#cup" for f in ALL}),
            ],
        },
        "pickup_point": {
            "parent": "block/orientable",
            "textures": {"front": "citylife:block/pickup_point_front",
                         "side": "citylife:block/pickup_point_side",
                         "top": "citylife:block/pickup_point_side"},
        },
    }
    for name, model in models.items():
        write_json(os.path.join(ASSETS, "models", "block", f"{name}.json"), model)
        write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"),
                   {"parent": f"citylife:block/{name}"})
        write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": {
            f"facing={facing}": ({"model": f"citylife:block/{name}", "y": rot} if rot
                                 else {"model": f"citylife:block/{name}"})
            for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
        }})
        write_json(os.path.join(DATA, "loot_tables", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item",
                                                "name": f"citylife:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })


def item_models(names) -> None:
    for name in names:
        write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"citylife:item/{name}"},
        })


def main() -> int:
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(BLOCKS, exist_ok=True)
    for name, (body, top, bottom, frame) in PHONES.items():
        phone(name, body, top, bottom, frame)
    tablet()
    laptop()

    motherboard("mb_k1", (34, 110, 60), (60, 62, 70))
    motherboard("mb_r5", (28, 28, 32), (200, 50, 60))
    cpu("cpu_k1_i5", (40, 110, 220))
    cpu("cpu_k1_i9", (20, 40, 110))
    cpu("cpu_r5_r7", (230, 120, 40))
    cooler("cooler_air", False)
    cooler("cooler_water", True)
    ram("ram_8", (30, 100, 60), (60, 120, 80), False)
    ram("ram_16", (24, 24, 28), (40, 42, 48), True)
    gpu("gpu_gx1650", (60, 64, 72), (120, 200, 90), 1)
    gpu("gpu_gx3060", (44, 46, 52), (120, 200, 90), 2)
    gpu("gpu_gx4070", (36, 38, 44), (90, 200, 255), 3)
    gpu("gpu_gx4090", (30, 30, 34), (230, 190, 90), 3)
    psu("psu_450", (70, 74, 84), (120, 200, 90))
    psu("psu_750", (44, 46, 54), (230, 190, 90))
    psu("psu_1000", (30, 30, 34), (220, 70, 80))
    storage("ssd_512", True)
    storage("hdd_1tb", False)

    block_textures()
    block_models()
    parts = ["mb_k1", "mb_r5", "cpu_k1_i5", "cpu_k1_i9", "cpu_r5_r7", "cooler_air",
             "cooler_water", "ram_8", "ram_16", "gpu_gx1650", "gpu_gx3060", "gpu_gx4070",
             "gpu_gx4090", "psu_450", "psu_750", "psu_1000", "ssd_512", "hdd_1tb"]
    devices = list(PHONES) + ["tablet", "laptop"]
    item_models(parts + devices)
    print(f"Гаджеты: {len(devices)} устройств, {len(parts)} комплектующих, 6 блоков")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
