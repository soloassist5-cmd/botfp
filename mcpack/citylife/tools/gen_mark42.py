#!/usr/bin/env python3
"""
Mark 42 «Prodigal Son» — наш собственный костюм Железного человека.

Один источник правды для всего, что у костюма должно совпадать:

  * модель брони — список кубов по частям тела. Из него собирается
    Java-класс client/stark/Mark42Model.java (LayerDefinition для внешнего
    слоя — шлем, кираса, руки, ботинки — и внутреннего — поножи);
  * текстуры брони — развёртки тех же кубов, раскрашенные по материалам
    (красный лак, золото, тёмный металл) с фасками, швами, заклёпками;
  * маска свечения — глаза, дуговой реактор, репульсоры ладоней, сопла
    ботинок, вентиляция спины: рисуются поверх брони без затенения;
  * иконки предметов, способностей, текстуры стенда сборки и микроракеты;
  * файлы Palladium: сила citylife:mark42, выдача силы по нагруднику,
    лучи, следы, выхлоп двигателей.

Кубы тут, а не в Java: развёртку UV считает упаковщик ниже, и если
поменять размер детали, текстура и модель останутся согласованы.

    python3 citylife/tools/gen_mark42.py
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Canvas  # noqa: E402
from gen_gadgets import ASSETS, DATA, ITEMS, BLOCKS, write_json  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java", "dev", "lscity", "citylife", "client", "stark",
                    "Mark42Model.java")
ARMOR = os.path.join(ASSETS, "textures", "models", "armor")
ICONS = os.path.join(ASSETS, "textures", "icon")
GUI = os.path.join(ASSETS, "textures", "gui")
PAL = os.path.join(ASSETS, "palladium")
PAL_DATA = os.path.join(DATA, "palladium")

# ---------------------------------------------------------------------------
#  Палитра
# ---------------------------------------------------------------------------

# Материал — (блик, основа, тень, шов). Красный — глубокий лак, золото —
# тёплое, как у Mark 42 в фильме: золота у этой марки больше, чем у других.
RED = ((222, 62, 54), (170, 24, 30), (112, 12, 18), (64, 6, 10))
GOLD = ((252, 218, 128), (218, 164, 64), (152, 104, 32), (98, 64, 18))
DARK = ((88, 92, 104), (48, 50, 58), (28, 29, 34), (12, 12, 16))
STEEL = ((196, 202, 212), (146, 152, 164), (100, 104, 116), (60, 62, 72))
GLOW_CORE = (240, 253, 255, 255)
GLOW_MID = (160, 230, 255, 255)
GLOW_EDGE = (84, 176, 242, 255)
GLOW_DIM = (60, 130, 200, 255)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------------------
#  Модель: части тела и кубы
# ---------------------------------------------------------------------------
#
# Координаты — как у HumanoidModel: пиксели, ось y вниз, начало — точка
# вращения части. Голова и тело вращаются вокруг шеи (0, 0, 0), руки —
# вокруг плеча (±5, 2, 0), ноги — вокруг бедра (±1.9, 12, 0).
#
# Куб: (имя, x, y, z, ш, в, г, раздув, стиль). Стиль — функция раскраски
# граней, ниже. Левая сторона тела — зеркало правой (общая развёртка).

PARTS_OUTER = {
    "head": [
        ("shell", -4, -8, -4, 8, 8, 8, 1.0, "helmet"),
        ("crest", -1, -9.6, -4.6, 2, 1, 8, 0.1, "crest"),
        ("ear_r", -5.7, -5, -1.5, 1, 3, 3, 0.05, "ear"),
        ("ear_l", 4.7, -5, -1.5, 1, 3, 3, 0.05, "ear"),
    ],
    # Забрало — отдельная часть: его можно поднять («Забрало» в способностях).
    "head/face": [
        ("plate", -3.5, -6.8, -5.4, 7, 6, 1, 0.15, "face"),
        ("chin", -2.5, -1.0, -5.2, 5, 1, 1, 0.1, "chin"),
    ],
    "body": [
        ("shell", -4, 0, -2, 8, 12, 4, 1.01, "torso"),
        ("chest", -4.5, 0.3, -3.6, 9, 5, 1, 0.0, "chest"),
        ("reactor", -1.5, 1.4, -4.1, 3, 3, 1, -0.3, "reactor"),
        ("abs", -3, 6, -3.35, 6, 5, 1, -0.2, "abs"),
        ("back", -4, 0.5, 2.6, 8, 8, 1, 0.1, "back"),
    ],
    "right_arm": [
        ("shell", -3, -2, -2, 4, 12, 4, 1.0, "arm"),
        ("pauldron", -4.5, -3.3, -3.5, 6, 3, 7, -0.1, "pauldron"),
        ("cap", -4.0, -4.0, -3.0, 5, 1, 6, 0.0, "cap"),
        ("gauntlet", -4.5, 5.6, -3.5, 7, 4, 7, -0.1, "gauntlet"),
        ("palm", -2.5, 10.7, -1.5, 3, 1, 3, -0.2, "palm"),
    ],
    "right_leg": [
        ("boot", -2, 6, -2, 4, 6, 4, 1.0, "boot"),
        ("shin", -2.6, 3.8, -3.3, 5, 4, 1, 0.0, "shin"),
        ("sole", -2.5, 12.6, -2.5, 5, 1, 5, -0.2, "sole"),
    ],
}

PARTS_INNER = {
    "body": [
        ("belt", -4, 9.5, -2, 8, 3, 4, 0.55, "belt"),
    ],
    "right_leg": [
        ("leg", -2, 0, -2, 4, 12, 4, 0.5, "thigh"),
        ("knee", -2.5, 5.2, -2.95, 5, 2, 1, 0.0, "knee"),
    ],
}

POSES = {
    "head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
    "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
}


def mirrored(parts: dict) -> dict:
    """Добавить левые руку и ногу зеркалом правых: x' = −(x + ш)."""
    out = dict(parts)
    for right, left in (("right_arm", "left_arm"), ("right_leg", "left_leg")):
        if right in parts:
            out[left] = [(name, -(x + w), y, z, w, h, d, inf, style, True)
                         for (name, x, y, z, w, h, d, inf, style) in parts[right]]
    return out


def dims(w, h, d):
    return math.ceil(w), math.ceil(h), math.ceil(d)


def pack(parts: dict, size: int) -> dict:
    """Развёртки кубов полками сверху вниз. Зеркальные кубы берут UV правых."""
    uv = {}
    x = y = shelf = 0
    pad = 1
    for part in parts:
        for cube in parts[part]:
            name, cx, cy, cz, w, h, d = cube[:7]
            if len(cube) > 9:
                continue
            W, H, D = dims(w, h, d)
            bw, bh = 2 * (D + W), D + H
            if x + bw > size:
                x, y, shelf = 0, y + shelf + pad, 0
            if y + bh > size:
                raise SystemExit(f"не влезло в развёртку {size}: {part}/{name}")
            uv[(part, name)] = (x, y)
            x += bw + pad
            shelf = max(shelf, bh)
    return uv


def uv_of(uv: dict, part: str, cube) -> tuple[int, int]:
    if len(cube) > 9:   # зеркало: та же развёртка, что у правой части
        right = part.replace("left", "right")
        name = cube[0].replace("_l", "_r") if cube[0].endswith("_l") else cube[0]
        return uv[(right, name)]
    return uv[(part, cube[0])]


# ---------------------------------------------------------------------------
#  Раскраска
# ---------------------------------------------------------------------------

class Face:
    """Прямоугольник грани на холсте: (x0, y0) — левый верх, w×h пикселей."""

    def __init__(self, c: Canvas, glow: Canvas, x0: int, y0: int, w: int, h: int):
        self.c, self.g, self.x0, self.y0, self.w, self.h = c, glow, x0, y0, w, h

    def px(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c.set(self.x0 + x, self.y0 + y, colour)

    def clear(self, x, y):
        """Прозрачный пиксель: сквозь него видно то, что под бронёй."""
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c.px[self.y0 + y][self.x0 + x] = (0, 0, 0, 0)

    def glow(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c.set(self.x0 + x, self.y0 + y, colour)
            self.g.set(self.x0 + x, self.y0 + y, colour)

    def fill(self, mat, shade=True, seam=True, x0=0, y0=0, x1=None, y1=None):
        """Плита: вертикальный градиент, блик сверху, тень снизу, шов по краю."""
        hi, base, lo, sm = mat
        x1 = self.w - 1 if x1 is None else x1
        y1 = self.h - 1 if y1 is None else y1
        span = max(1, y1 - y0)
        for y in range(y0, y1 + 1):
            t = (y - y0) / span
            row = mix(mix(hi, base, min(1, t * 2.2)), lo, max(0, t - 0.55) * 1.6) if shade else base
            for x in range(x0, x1 + 1):
                # Лёгкая «щётка» по металлу: шум, повторяемый от координат.
                n = ((self.x0 + x) * 73 + (self.y0 + y) * 151) % 7 - 3
                col = tuple(max(0, min(255, v + n * 2)) for v in row)
                self.px(x, y, rgba(col))
        if seam:
            for x in range(x0, x1 + 1):
                self.px(x, y1, rgba(mix(lo, sm, 0.5)))
            for y in range(y0, y1 + 1):
                self.px(x0, y, rgba(mix(base, sm, 0.35)))
                self.px(x1, y, rgba(mix(lo, sm, 0.55)))
            for x in range(x0 + 1, x1):
                self.px(x, y0, rgba(mix(hi, (255, 255, 255), 0.25)))

    def hline(self, y, mat, x0=0, x1=None, colour=None):
        x1 = self.w - 1 if x1 is None else x1
        for x in range(x0, x1 + 1):
            self.px(x, y, colour or rgba(mat[3]))
        # Блик под швом: фаска следующей плиты.
        for x in range(x0, x1 + 1):
            if y + 1 < self.h:
                self.px(x, y + 1, rgba(mix(mat[0], mat[1], 0.3)))

    def vline(self, x, mat, y0=0, y1=None):
        y1 = self.h - 1 if y1 is None else y1
        for y in range(y0, y1 + 1):
            self.px(x, y, rgba(mat[3]))

    def rivet(self, x, y, mat):
        self.px(x, y, rgba(mat[0]))
        self.px(x + 1, y + 1, rgba(mat[2]))


def faces(c, g, u, v, W, H, D, mirror=False):
    """Грани куба в box-развёртке Minecraft (смотрим на персонажа спереди)."""
    return {
        "top": Face(c, g, u + D, v, W, D),
        "bottom": Face(c, g, u + D + W, v, W, D),
        "right": Face(c, g, u, v + D, D, H),          # правый бок персонажа (−x)
        "front": Face(c, g, u + D, v + D, W, H),
        "left": Face(c, g, u + D + W, v + D, D, H),
        "back": Face(c, g, u + 2 * D + W, v + D, W, H),
    }


def paint_all(f: dict, mat, **kw):
    for face in f.values():
        face.fill(mat, **kw)


def st_helmet(f):
    for name in ("top", "back", "right", "left"):
        f[name].fill(RED)
    f["front"].fill(RED)
    f["bottom"].fill(DARK, shade=False)
    # Под забралом — проём: поднимешь забрало, и видно лицо.
    fr = f["front"]
    for x in range(1, fr.w - 1):
        for y in range(1, fr.h - 1):
            fr.clear(x, y)
    for x in range(fr.w):
        fr.px(x, fr.h - 1, rgba(GOLD[1]))
    for side in ("right", "left"):
        s = f[side]
        s.fill(GOLD, x0=0, y0=3, x1=s.w - 3, y1=s.h - 1)
        s.hline(2, RED)
        s.rivet(s.w - 2, s.h - 2, RED)
    # Сверху — гребень по центру и две линии панелей.
    t = f["top"]
    t.vline(1, RED)
    t.vline(t.w - 2, RED)
    b = f["back"]
    b.hline(4, RED)
    b.vline(b.w // 2, RED, 0, 3)


def st_crest(f):
    paint_all(f, RED)
    f["top"].fill(RED, seam=False)
    for x in range(f["top"].w):
        for y in range(f["top"].h):
            if y % 3 == 0:
                f["top"].px(x, y, rgba(RED[0]))


def st_ear(f):
    paint_all(f, GOLD)
    for side in ("right", "left"):
        s = f[side]
        s.px(1, 1, rgba(DARK[1]))
        s.px(1, 0, rgba(DARK[2]))
        s.px(1, 2, rgba(DARK[0]))


def st_face(f):
    paint_all(f, GOLD)
    fr = f["front"]
    fr.fill(GOLD)
    w, h = fr.w, fr.h
    # Глаза: косые прорези, свет изнутри.
    for x, y in ((1, 2), (2, 2), (2, 3), (4, 2), (5, 2), (4, 3)):
        fr.glow(x, y, GLOW_MID)
    fr.glow(1, 2, GLOW_CORE)
    fr.glow(5, 2, GLOW_CORE)
    # Над глазами — тень надбровья, нос — светлая грань, рот — шов.
    for x in range(w):
        fr.px(x, 1, rgba(mix(GOLD[2], GOLD[3], 0.4)))
    fr.px(3, 2, rgba(GOLD[0]))
    fr.px(3, 3, rgba(GOLD[0]))
    for x in (1, 2, 4, 5):
        fr.px(x, 5, rgba(GOLD[3]))
    fr.px(0, 4, rgba(GOLD[2]))
    fr.px(w - 1, 4, rgba(GOLD[2]))
    for x in range(1, w - 1):
        fr.px(x, 0, rgba(GOLD[0]))


def st_chin(f):
    paint_all(f, GOLD)
    f["front"].fill(GOLD)
    f["front"].px(2, 0, rgba(GOLD[3]))


def st_torso(f):
    for name in ("right", "left", "back"):
        f[name].fill(RED)
    f["top"].fill(DARK, shade=False)
    f["bottom"].fill(DARK, shade=False)
    fr = f["front"]
    fr.fill(RED)
    fr.fill(GOLD, x0=1, y0=6, x1=fr.w - 2, y1=fr.h - 1)
    for side in ("right", "left"):
        s = f[side]
        s.fill(GOLD, x0=0, y0=7, x1=s.w - 1, y1=s.h - 1)
        s.hline(4, RED)
    b = f["back"]
    b.vline(b.w // 2 - 1, RED, 0, b.h - 1)
    b.vline(b.w // 2, RED, 0, b.h - 1)
    b.hline(8, RED)


def st_chest(f):
    paint_all(f, RED)
    fr = f["front"]
    fr.fill(RED)
    # Грудные пластины: шов посередине, золотая кромка снизу, заклёпки.
    fr.vline(fr.w // 2, RED, 0, fr.h - 2)
    for x in range(fr.w):
        fr.px(x, fr.h - 1, rgba(GOLD[1]))
    fr.px(0, fr.h - 1, rgba(GOLD[2]))
    fr.rivet(1, 1, RED)
    fr.rivet(fr.w - 3, 1, RED)
    t = f["top"]
    t.fill(GOLD, seam=False)


def st_reactor(f):
    paint_all(f, STEEL)
    fr = f["front"]
    # Дуговой реактор: кольцо свечения, светлый центр, тёмные спицы.
    for x in range(fr.w):
        for y in range(fr.h):
            fr.glow(x, y, GLOW_EDGE)
    fr.glow(1, 1, GLOW_CORE)
    for x, y in ((1, 0), (0, 1), (2, 1), (1, 2)):
        fr.glow(x, y, GLOW_MID)


def st_abs(f):
    paint_all(f, GOLD)
    fr = f["front"]
    fr.fill(GOLD)
    for y in (1, 3):
        fr.hline(y, GOLD)
    fr.vline(fr.w // 2, GOLD, 0, fr.h - 1)


def st_back(f):
    paint_all(f, RED)
    b = f["back"]
    b.fill(RED)
    # Узкие сопла вдоль лопаток: тёмные щели с тусклым свечением внизу.
    for x0 in (1, b.w - 2):
        for y in range(2, 7):
            b.px(x0, y, rgba(DARK[2]))
        b.glow(x0, 6, GLOW_DIM)
    b.vline(b.w // 2, RED, 1, b.h - 2)
    b.hline(b.h - 3, RED)


def st_arm(f):
    for name in ("front", "back", "right", "left"):
        s = f[name]
        s.fill(GOLD)
        s.hline(5, GOLD)
        s.rivet(1, 2, GOLD)
    f["left"].fill(DARK, x0=0, y0=6, x1=f["left"].w - 1)      # внутренняя сторона локтя
    f["top"].fill(RED, shade=False)
    f["bottom"].fill(DARK, shade=False)


def st_pauldron(f):
    for name in ("front", "back", "right", "left"):
        s = f[name]
        s.fill(RED)
        for x in range(s.w):
            s.px(x, s.h - 1, rgba(GOLD[1]))
    t = f["top"]
    t.fill(RED)
    t.vline(t.w // 2, RED, 0, t.h - 1)
    t.rivet(1, 1, RED)
    t.rivet(t.w - 3, t.h - 3, RED)
    f["bottom"].fill(DARK, shade=False)


def st_cap(f):
    paint_all(f, RED)
    t = f["top"]
    t.fill(RED)
    t.vline(t.w // 2, RED, 0, t.h - 1)
    for x in range(t.w):
        t.px(x, 0, rgba(RED[0]))


def st_gauntlet(f):
    for name in ("front", "back", "right", "left"):
        s = f[name]
        s.fill(RED)
        for x in range(s.w):
            s.px(x, 0, rgba(GOLD[0]))
            s.px(x, 1, rgba(GOLD[1]))
        s.hline(s.h - 2, RED)
    f["top"].fill(GOLD, shade=False)
    f["bottom"].fill(DARK, shade=False)


def st_palm(f):
    paint_all(f, DARK, shade=False)
    b = f["bottom"]
    for x in range(b.w):
        for y in range(b.h):
            b.glow(x, y, GLOW_EDGE)
    b.glow(1, 1, GLOW_CORE)


def st_boot(f):
    for name in ("front", "back", "right", "left"):
        s = f[name]
        s.fill(GOLD)
        s.hline(2, GOLD)
    fr = f["front"]
    fr.fill(RED, x0=0, y0=3, x1=fr.w - 1)   # носок — красный
    f["top"].fill(DARK, shade=False)
    f["bottom"].fill(DARK, shade=False)


def st_shin(f):
    paint_all(f, RED)
    fr = f["front"]
    fr.fill(RED)
    fr.vline(fr.w // 2, RED, 1, fr.h - 1)
    for x in range(fr.w):
        fr.px(x, 0, rgba(GOLD[0]))


def st_sole(f):
    paint_all(f, DARK, shade=False)
    b = f["bottom"]
    cx, cy = (b.w - 1) / 2, (b.h - 1) / 2
    for x in range(b.w):
        for y in range(b.h):
            d = math.hypot(x - cx, y - cy)
            if d <= 0.8:
                b.glow(x, y, GLOW_CORE)
            elif d <= 1.7:
                b.glow(x, y, GLOW_MID)
            elif d <= 2.4:
                b.glow(x, y, GLOW_EDGE)


def st_belt(f):
    for name in ("front", "back", "right", "left"):
        s = f[name]
        s.fill(DARK)
        for x in range(s.w):
            s.px(x, 0, rgba(GOLD[1]))
    fr = f["front"]
    for x in range(fr.w // 2 - 1, fr.w // 2 + 1):
        for y in range(1, fr.h):
            fr.px(x, y, rgba(GOLD[1]))
    f["top"].fill(DARK, shade=False)
    f["bottom"].fill(DARK, shade=False)


def st_thigh(f):
    for name in ("front", "back"):
        s = f[name]
        s.fill(RED, y1=6)
        s.fill(GOLD, y0=7)
        s.vline(s.w // 2, RED, 0, 6)
    for name in ("right", "left"):
        s = f[name]
        s.fill(GOLD)
        s.hline(6, GOLD)
        s.vline(1, GOLD, 0, 5)
    f["top"].fill(DARK, shade=False)
    f["bottom"].fill(DARK, shade=False)


def st_knee(f):
    paint_all(f, GOLD)
    fr = f["front"]
    fr.fill(GOLD)
    fr.px(fr.w // 2, 0, rgba(GOLD[0]))
    fr.px(fr.w // 2, 1, rgba(GOLD[3]))


STYLES = {name[3:]: fn for name, fn in globals().items() if name.startswith("st_")}


def paint(parts: dict, uv: dict, size: int, name: str) -> None:
    c, g = Canvas(size, size), Canvas(size, size)
    for part, cubes in parts.items():
        for cube in cubes:
            if len(cube) > 9:
                continue
            _, x, y, z, w, h, d, inf, style = cube
            W, H, D = dims(w, h, d)
            u, v = uv[(part, cube[0])]
            STYLES[style](faces(c, g, u, v, W, H, D))
    c.write(os.path.join(ARMOR, f"mark42_{name}.png"))
    g.write(os.path.join(ARMOR, f"mark42_{name}_glow.png"))


# ---------------------------------------------------------------------------
#  Java-модель
# ---------------------------------------------------------------------------

def fl(v) -> str:
    s = f"{float(v):.2f}".rstrip("0").rstrip(".")
    if s == "-0":
        s = "0"
    return s + "F"


def java_layer(method: str, parts: dict, uv: dict, size: int) -> list[str]:
    out = [f"    public static LayerDefinition {method}() {{",
           "        MeshDefinition mesh = new MeshDefinition();",
           "        PartDefinition root = mesh.getRoot();"]
    created = set()
    for base in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
        px, py, pz = POSES[base]
        var = base.replace("_", "")
        out.append(f"        PartDefinition {var} = root.addOrReplaceChild(\"{base}\", CubeListBuilder.create(), "
                   f"PartPose.offset({fl(px)}, {fl(py)}, {fl(pz)}));")
        created.add(base)
    out.append("        root.addOrReplaceChild(\"hat\", CubeListBuilder.create(), PartPose.ZERO);")
    for part, cubes in parts.items():
        if "/" in part:
            parent, child = part.split("/")
            pvar = parent.replace("_", "")
            var = f"{pvar}_{child}"
            out.append(f"        PartDefinition {var} = {pvar}.addOrReplaceChild(\"{child}\", "
                       f"CubeListBuilder.create(), PartPose.ZERO);")
        else:
            var = part.replace("_", "")
        for cube in cubes:
            name, x, y, z, w, h, d, inf = cube[:8]
            u, v = uv_of(uv, part, cube)
            mirror = ".mirror()" if len(cube) > 9 else ""
            out.append(f"        {var}.addOrReplaceChild(\"{name}\", CubeListBuilder.create().texOffs({u}, {v})"
                       f"{mirror}.addBox({fl(x)}, {fl(y)}, {fl(z)}, {fl(w)}, {fl(h)}, {fl(d)}, "
                       f"new CubeDeformation({fl(inf)})), PartPose.ZERO);")
    out.append(f"        return LayerDefinition.create(mesh, {size}, {size});")
    out.append("    }")
    return out


def write_java(outer, uv_o, inner, uv_i) -> None:
    lines = [
        "package dev.lscity.citylife.client.stark;",
        "",
        "import net.minecraft.client.model.geom.ModelLayerLocation;",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeDeformation;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.LayerDefinition;",
        "import net.minecraft.client.model.geom.builders.MeshDefinition;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "import net.minecraft.resources.ResourceLocation;",
        "import net.minecraftforge.api.distmarker.Dist;",
        "import net.minecraftforge.api.distmarker.OnlyIn;",
        "",
        "/**",
        " * Модель брони Mark 42: внешний слой (шлем с забралом, кираса с реактором,",
        " * руки с наплечниками и репульсорами, ботинки с соплами) и внутренний (поножи).",
        " *",
        " * Файл собран citylife/tools/gen_mark42.py вместе с текстурами — править",
        " * там, иначе развёртка разойдётся с картинкой.",
        " */",
        "@OnlyIn(Dist.CLIENT)",
        "public final class Mark42Model {",
        "",
        "    public static final ModelLayerLocation OUTER =",
        "            new ModelLayerLocation(new ResourceLocation(\"citylife\", \"mark42\"), \"outer\");",
        "    public static final ModelLayerLocation INNER =",
        "            new ModelLayerLocation(new ResourceLocation(\"citylife\", \"mark42\"), \"inner\");",
        "",
        "    private Mark42Model() {",
        "    }",
        "",
    ]
    lines += java_layer("outer", outer, uv_o, 128)
    lines.append("")
    lines += java_layer("inner", inner, uv_i, 64)
    lines.append("}")
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------------------
#  Иконки
# ---------------------------------------------------------------------------

def put(c: Canvas, rows: list[str], pal: dict, ox=0, oy=0) -> None:
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                c.set(ox + x, oy + y, pal[ch])


PAL_ICON = {
    "R": rgba(RED[1]), "r": rgba(RED[2]), "H": rgba(RED[0]), "s": rgba(RED[3]),
    "G": rgba(GOLD[1]), "g": rgba(GOLD[2]), "Y": rgba(GOLD[0]), "y": rgba(GOLD[3]),
    "D": rgba(DARK[1]), "d": rgba(DARK[2]), "K": rgba(DARK[3]), "S": rgba(STEEL[1]),
    "W": GLOW_CORE, "C": GLOW_MID, "c": GLOW_EDGE,
}

HELMET = [
    "................",
    ".....ssssss.....",
    "...sRHHHHHHRs...",
    "..sRHRRRRRRHRs..",
    "..sRRYYYYYYRRs..",
    ".sRRYGGGGGGYRRs.",
    ".sRgGWCGGCWGgRs.",
    ".sRgGGGGGGGGgRs.",
    ".sRRgGGYGGGgRRs.",
    ".sGRgGGYGGGgRGs.",
    ".sGRRgGyyGgRRGs.",
    "..sGRRgGGgRRGs..",
    "...sGRRggRRGs...",
    "....ssDDDDss....",
    "................",
    "................",
]
CHEST = [
    "................",
    "..ss..ssss..ss..",
    ".sRRsYGGGGYsRRs.",
    "sRHRRRRRRRRRRHRs",
    "sRRHRRRccRRRHRRs",
    "sGRRRRcWWcRRRRGs",
    "sGGRRRcWWcRRRGGs",
    ".sGRRRRccRRRRGs.",
    ".sGGRYYYYYYRGGs.",
    "..sGgGGyGGGgGs..",
    "..sGgGGyGGGgGs..",
    "..sGgGGyGGGgGs..",
    "...sGgGyGGgGs...",
    "...sDDDDDDDDs...",
    "................",
    "................",
]
LEGS = [
    "................",
    "...ssssssssss...",
    "...sDDGGGGDDs...",
    "...sRRRssRRRs...",
    "...sRHRssRHRs...",
    "...sRRRs.sRRRs..",
    "...sRRRs.sRRRs..",
    "...sYGGs.sGGYs..",
    "...sGGGs.sGGGs..",
    "...sGgGs.sGgGs..",
    "...sGGGs.sGGGs..",
    "...sGgGs.sGgGs..",
    "...sGGGs.sGGGs..",
    "...ssss..ssss...",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "..ssss....ssss..",
    "..sYGs....sGYs..",
    "..sGGs....sGGs..",
    "..sGgs....sgGs..",
    "..sRRs....sRRs..",
    ".sRHRs....sRHRs.",
    "sRRRRs....sRRRRs",
    "sRRRRs....sRRRRs",
    "sDDDDs....sDDDDs",
    ".cCCc......cCCc.",
    "................",
    "................",
]
MISSILE = [
    "................",
    "................",
    "...........ss...",
    "..........sSSs..",
    ".........sSSSs..",
    "........sSSSs...",
    ".......sSSSs....",
    "......sRRSs.....",
    ".....sRRRs......",
    "....sSSSs.......",
    "...sSSSs........",
    "..sDDDs.........",
    ".sDsDs..........",
    ".CcDs...........",
    "..C.............",
    "................",
]


def item_icons() -> None:
    for name, rows in (("mark42_helmet", HELMET), ("mark42_chestplate", CHEST),
                       ("mark42_leggings", LEGS), ("mark42_boots", BOOTS), ("mark42_missile", MISSILE)):
        c = Canvas(16, 16)
        put(c, rows, PAL_ICON)
        c.write(os.path.join(ITEMS, f"{name}.png"))
        write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"),
                   {"parent": "item/generated", "textures": {"layer0": f"citylife:item/{name}"}})


def ability_icon(name: str, draw) -> None:
    c = Canvas(16, 16)
    draw(c)
    c.write(os.path.join(ICONS, f"mark42_{name}.png"))


def ic_repulsor(c):
    c.disc(8, 8, 7, rgba(DARK[1]))
    c.ring(8, 8, 7, 1.2, rgba(GOLD[1]))
    c.disc(8, 8, 4.5, GLOW_EDGE)
    c.disc(8, 8, 3, GLOW_MID)
    c.disc(8, 8, 1.6, GLOW_CORE)


def ic_unibeam(c):
    c.disc(8, 8, 7.2, rgba(RED[1]))
    c.ring(8, 8, 7.2, 1.2, rgba(RED[3]))
    for i in range(3):
        a = i * 2 * math.pi / 3 - math.pi / 2
        for t in range(2, 7):
            c.set(round(8 + math.cos(a) * t), round(8 + math.sin(a) * t), rgba(STEEL[2]))
    c.ring(8, 8, 5, 1.4, GLOW_EDGE)
    c.disc(8, 8, 2.6, GLOW_CORE)


def ic_missiles(c):
    for ox, oy in ((0, 4), (4, 0), (6, 6)):
        put(c, MISSILE[2:14], PAL_ICON, ox - 2, oy - 1)


def ic_flight(c):
    put(c, BOOTS, PAL_ICON, 0, -3)
    for x, y in ((3, 11), (12, 11), (3, 13), (12, 13), (2, 14), (13, 14)):
        c.set(x, y, (255, 180, 70, 255))
    for x, y in ((3, 12), (12, 12)):
        c.set(x, y, GLOW_CORE)


def ic_shield(c):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.4:
                hexy = (x + (y // 3) % 2 * 2) % 4 == 0 or y % 3 == 0
                c.set(x, y, (120, 220, 255, 200 if hexy else 90))
    c.ring(8, 8, 7.6, 1.0, GLOW_CORE)


def ic_faceplate(c):
    put(c, HELMET, PAL_ICON)
    for x in range(4, 12):
        c.set(x, 5, (255, 255, 255, 140))


def ic_night(c):
    c.disc(8, 8, 7, (16, 32, 22, 255))
    c.disc(8, 8, 5, (60, 200, 90, 255))
    c.disc(8, 8, 2.2, (210, 255, 210, 255))
    c.ring(8, 8, 7, 1.0, rgba(DARK[1]))


def ic_home(c):
    put(c, CHEST, PAL_ICON)
    for i, y in enumerate((14, 12, 10)):
        c.set(14, y, (255, 255, 255, 220))
        c.set(13, y + 1, (255, 255, 255, 160))
        c.set(15, y + 1, (255, 255, 255, 160))


def ic_scan(c):
    c.ring(8, 8, 7.3, 1.0, GLOW_EDGE)
    c.ring(8, 8, 4.5, 0.8, GLOW_DIM)
    for i in range(8):
        a = -math.pi / 4 + i * 0.12
        c.set(round(8 + math.cos(a) * 6), round(8 + math.sin(a) * 6), GLOW_CORE)
    for t in range(0, 7):
        c.set(8 + t, 8 - t // 2, GLOW_MID)
    c.set(5, 10, (255, 90, 90, 255))
    c.set(11, 5, (255, 90, 90, 255))


def ic_strength(c):
    put(c, CHEST[1:], PAL_ICON)


def ability_icons() -> None:
    for name, fn in (("repulsor", ic_repulsor), ("unibeam", ic_unibeam), ("missiles", ic_missiles),
                     ("flight", ic_flight), ("shield", ic_shield), ("faceplate", ic_faceplate),
                     ("night", ic_night), ("home", ic_home), ("scan", ic_scan), ("strength", ic_strength)):
        ability_icon(name, fn)


# ---------------------------------------------------------------------------
#  Стенд сборки и прочие текстуры
# ---------------------------------------------------------------------------

def gantry_textures() -> None:
    top = Canvas(16, 16)
    top.rect(0, 0, 15, 15, rgba(DARK[2]))
    top.ring(8, 8, 7.5, 1.0, rgba(DARK[0]))
    top.ring(8, 8, 6.0, 0.8, GLOW_EDGE)
    top.ring(8, 8, 3.5, 0.6, GLOW_DIM)
    for x in range(16):
        top.set(x, 0, rgba(GOLD[2]))
        top.set(x, 15, rgba(GOLD[2]))
        top.set(0, x, rgba(GOLD[2]))
        top.set(15, x, rgba(GOLD[2]))
    top.write(os.path.join(BLOCKS, "mark42_gantry_top.png"))
    side = Canvas(16, 16)
    side.rect(0, 0, 15, 15, rgba(DARK[1]))
    side.rect(0, 0, 15, 1, rgba(GOLD[1]))
    side.rect(0, 14, 15, 15, rgba(DARK[3]))
    for x in range(1, 15, 3):
        side.set(x, 3, GLOW_EDGE)
    side.write(os.path.join(BLOCKS, "mark42_gantry_side.png"))
    # Энергощит: гексы для «завихрения» поверх брони.
    sh = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            q = (x + (y // 6) % 2 * 4) % 8
            line = q == 0 or y % 6 == 0
            sh.set(x, y, (120, 220, 255, 255) if line else (40, 110, 170, 255))
    sh.write(os.path.join(ARMOR, "mark42_shield.png"))
    # Фон окна способностей Palladium — тёмная плитка с золотой сеткой.
    bg = Canvas(16, 16)
    bg.rect(0, 0, 15, 15, (24, 18, 16, 255))
    for i in range(16):
        bg.set(i, 0, (60, 40, 20, 255))
        bg.set(0, i, (60, 40, 20, 255))
    bg.set(8, 8, (90, 60, 26, 255))
    bg.write(os.path.join(GUI, "mark42_background.png"))


def gantry_model() -> None:
    write_json(os.path.join(ASSETS, "models", "block", "mark42_gantry.json"), {
        "parent": "block/block",
        "textures": {"top": "citylife:block/mark42_gantry_top", "side": "citylife:block/mark42_gantry_side",
                     "particle": "citylife:block/mark42_gantry_side"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16],
             "faces": {"up": {"texture": "#top"}, "down": {"texture": "#side"},
                       **{f: {"texture": "#side", "uv": [0, 12, 16, 16]}
                          for f in ("north", "south", "east", "west")}}},
            {"from": [1, 4, 13], "to": [15, 5, 15],
             "faces": {f: {"texture": "#side"} for f in ("up", "north", "south", "east", "west")}},
            # Задние стойки рамы — как в мастерской Старка.
            {"from": [0.5, 4, 14], "to": [2, 16, 15.5],
             "faces": {f: {"texture": "#side"} for f in ("up", "north", "south", "east", "west")}},
            {"from": [14, 4, 14], "to": [15.5, 16, 15.5],
             "faces": {f: {"texture": "#side"} for f in ("up", "north", "south", "east", "west")}},
        ],
    })
    write_json(os.path.join(ASSETS, "models", "item", "mark42_gantry.json"),
               {"parent": "citylife:block/mark42_gantry"})
    write_json(os.path.join(ASSETS, "blockstates", "mark42_gantry.json"), {"variants": {
        f"facing={f}": {"model": "citylife:block/mark42_gantry", **({"y": r} if r else {})}
        for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write_json(os.path.join(DATA, "loot_tables", "blocks", "mark42_gantry.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "citylife:mark42_gantry"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


# ---------------------------------------------------------------------------
#  Palladium
# ---------------------------------------------------------------------------

UNLOCKED = [{"type": "palladium:ability_unlocked", "ability": "suit"}]


def ab(kind: str, title: str = "", icon: str = "", index: int = -1, hidden: bool = False,
       enabling=None, unlocking=None, usage=None, description: str = "", **extra) -> dict:
    a = {"type": kind}
    if title:
        a["title"] = {"translate": title}
    if icon:
        a["icon"] = icon if ":" in icon and "/" in icon or icon.startswith("citylife:mark42_") \
            else f"citylife:textures/icon/mark42_{icon}.png"
    if description:
        a["description"] = {"translate": description}
    a["list_index"] = index
    a["hidden"] = hidden
    a["hidden_in_bar"] = hidden
    a.update(extra)
    cond = {}
    if enabling is not None:
        cond["enabling"] = enabling
    cond["unlocking"] = UNLOCKED if unlocking is None else unlocking
    a["conditions"] = cond
    if usage:
        a["energy_bar_usage"] = {"energy_bar": "energy", "amount": usage}
    return a


def key(kind: str = "held", cooldown: int = 0, ticks: int = 0, empty_hand: bool = False) -> dict:
    k = {"type": f"palladium:{kind}", "cooldown": cooldown, "key_type": "key_bind",
         "needs_empty_hand": empty_hand}
    if kind == "activation":
        k["ticks"] = ticks
    return k


def energy(minimum: int) -> dict:
    return {"type": "palladium:energy_bar", "energy_bar": "energy", "min": minimum}


def enabled(name: str) -> dict:
    return {"type": "palladium:ability_enabled", "ability": name}


def ticks(name: str, lo: int, hi: int) -> dict:
    return {"type": "palladium:ability_ticks", "ability": name, "min": lo, "max": hi}


def attr(name: str, attribute: str, amount: float, uuid: str, op: int = 0, on=None) -> dict:
    return ab("palladium:attribute_modifier", hidden=True, enabling=on, attribute=attribute, amount=amount,
              operation=op, uuid=uuid)


def missile_data() -> dict:
    # NBT снаряда Palladium (в JSON-виде): урон, взрыв без разрушений,
    # микроракета + дымный след.
    return {"Damage": 6.0, "Gravity": 0.0, "Lifetime": 60, "ExplosionRadius": 1.2, "ExplosionCausesFire": False,
            "ExplosionBlockInteraction": "none", "DieOnEntityHit": True, "DieOnBlockHit": True, "Size": 0.4,
            "Appearances": [{"Type": "item", "Item": {"id": "citylife:mark42_missile", "Count": 1}},
                            {"Type": "trail", "Trail": "citylife:mark42_missile"},
                            {"Type": "particles", "ParticleType": "minecraft:smoke", "Amount": 1, "Spread": 0.02}]}


def power() -> dict:
    A = {}
    pieces = [("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots")]
    A["suit"] = ab("palladium:dummy", hidden=True, unlocking=[
        {"type": "palladium:item_in_slot", "item": {"item": f"citylife:mark42_{p}"}, "slot": s}
        for s, p in pieces])
    # 1. Репульсор: рука вперёд, луч из ладони, пока держишь клавишу.
    A["repulsor"] = ab("palladium:aim", "citylife.mark42.ability.repulsor", "repulsor", 0,
                       enabling=[key("held", empty_hand=True), energy(10)],
                       description="citylife.mark42.ability.repulsor.desc", time=3, arm="right_arm")
    A["repulsor_beam"] = ab("palladium:energy_beam", hidden=True, usage=3,
                            enabling=[ticks("repulsor", 3, 1000000), enabled("repulsor"), energy(5)],
                            energy_beam="citylife:mark42_repulsor", damage=3.0, max_distance=40.0,
                            speed=1.2, set_on_fire_seconds=0, cause_fire=False, smelt_blocks=False)
    A["repulsor_sound"] = ab("palladium:play_sound", hidden=True, enabling=[enabled("repulsor_beam")],
                             sound="minecraft:block.beacon.ambient", volume=0.9, pitch=1.8, looping=True,
                             play_self=True, play_others=True)
    # 2. Юнибим: реактор на груди — широкий луч, тратит много энергии.
    A["unibeam"] = ab("palladium:energy_beam", "citylife.mark42.ability.unibeam", "unibeam", 1,
                      enabling=[key("held"), energy(40)], usage=8,
                      description="citylife.mark42.ability.unibeam.desc",
                      energy_beam="citylife:mark42_unibeam", damage=6.0, max_distance=56.0, speed=0.9,
                      set_on_fire_seconds=0, cause_fire=False, smelt_blocks=False)
    A["unibeam_sound"] = ab("palladium:play_sound", hidden=True, enabling=[enabled("unibeam")],
                            sound="minecraft:block.beacon.power_select", volume=1.0, pitch=0.7,
                            looping=True, play_self=True, play_others=True)
    # 3. Микроракеты: залп из шести, по ракете каждые 4 тика.
    A["missiles"] = ab("palladium:dummy", "citylife.mark42.ability.missiles", "missiles", 2,
                       enabling=[key("activation", cooldown=100, ticks=24), energy(150)], usage=6,
                       description="citylife.mark42.ability.missiles.desc")
    for i in range(6):
        t = 1 + i * 4
        A[f"missile_{i}"] = ab("palladium:projectile", hidden=True, enabling=[ticks("missiles", t, t)],
                               entity_type="palladium:custom_projectile", entity_data=missile_data(),
                               inaccuracy=3.0, velocity=2.0,
                               swinging_arm="none" if i % 2 else "off_arm",
                               damage_from_player=True, ignore_player_movement=False)
    A["missile_sound"] = ab("palladium:play_sound", hidden=True, enabling=[ticks("missiles", 1, 1)],
                            sound="minecraft:entity.firework_rocket.launch", volume=1.0, pitch=0.6,
                            looping=False, play_self=True, play_others=True)
    # 4. Полёт: двойной прыжок — взлёт, бег — форсаж с героической позой.
    A["flight"] = ab("palladium:dummy", "citylife.mark42.ability.flight", "flight", 3,
                     enabling=[key("toggle")], description="citylife.mark42.ability.flight.desc")
    on = [enabled("flight")]
    A["flight_speed"] = attr("flight_speed", "palladium:flight_speed", 0.3,
                             "8a9e1f2c-4b42-4d42-9f42-000000004201", on=on)
    A["flight_sprint"] = attr("flight_sprint", "palladium:flight_sprint_speed", 1.6,
                              "8a9e1f2c-4b42-4d42-9f42-000000004202", on=on)
    A["flight_heroic"] = attr("flight_heroic", "palladium:heroic_flight_type", 1.0,
                              "8a9e1f2c-4b42-4d42-9f42-000000004203", on=on)
    flying = [enabled("flight"), {"type": "palladium:is_hovering_or_flying"}]
    A["thrusters"] = ab("palladium:render_layer", hidden=True, enabling=flying,
                        render_layer="citylife:mark42_thrusters")
    A["thruster_fire"] = ab("palladium:particles", hidden=True, enabling=flying,
                            emitter=["citylife:mark42_thrusters"], particle_type="minecraft:flame",
                            options="")
    A["trail"] = ab("palladium:trail", hidden=True,
                    enabling=[enabled("flight"), {"type": "palladium:is_fast_flying"}],
                    trail="citylife:mark42_flight")
    A["flight_sound"] = ab("palladium:play_sound", hidden=True, enabling=flying,
                           sound="minecraft:item.elytra.flying", volume=0.35, pitch=1.5, looping=True,
                           play_self=True, play_others=True)
    # 5. Энергощит: броня и устойчивость, пока хватает энергии.
    A["shield"] = ab("palladium:dummy", "citylife.mark42.ability.shield", "shield", 4,
                     enabling=[key("toggle"), energy(30)], usage=2,
                     description="citylife.mark42.ability.shield.desc")
    sh = [enabled("shield")]
    A["shield_armor"] = attr("shield_armor", "minecraft:generic.armor", 12.0,
                             "8a9e1f2c-4b42-4d42-9f42-000000004211", on=sh)
    A["shield_toughness"] = attr("shield_toughness", "minecraft:generic.armor_toughness", 8.0,
                                 "8a9e1f2c-4b42-4d42-9f42-000000004212", on=sh)
    A["shield_knockback"] = attr("shield_knockback", "minecraft:generic.knockback_resistance", 1.0,
                                 "8a9e1f2c-4b42-4d42-9f42-000000004213", on=sh)
    # Вторая страница: забрало, ночное зрение, сканер, «Протокол Дом».
    A["faceplate"] = ab("palladium:dummy", "citylife.mark42.ability.faceplate", "faceplate", 5,
                        enabling=[key("toggle")], description="citylife.mark42.ability.faceplate.desc")
    A["night_vision"] = ab("palladium:command", "citylife.mark42.ability.night", "night", 6,
                           enabling=[key("toggle")], description="citylife.mark42.ability.night.desc",
                           first_tick_commands=["effect give @s minecraft:night_vision infinite 0 true"],
                           last_tick_commands=["effect clear @s minecraft:night_vision"], commands=[])
    A["scan"] = ab("palladium:entity_glow", "citylife.mark42.ability.scan", "scan", 7,
                   enabling=[key("toggle"), energy(10)], usage=1, mode="others", color="#7fd8ff",
                   distance=48.0, description="citylife.mark42.ability.scan.desc")
    A["home"] = ab("palladium:command", "citylife.mark42.ability.home", "home", 8,
                   enabling=[key("action", cooldown=40)], description="citylife.mark42.ability.home.desc",
                   first_tick_commands=["mark42 home"], last_tick_commands=[], commands=[])
    # Постоянно: сила удара, скорость, прыжок, без урона от падения.
    A["strength"] = attr("strength", "palladium:punch_damage", 7.0, "8a9e1f2c-4b42-4d42-9f42-000000004221")
    A["speed"] = attr("speed", "minecraft:generic.movement_speed", 0.25,
                      "8a9e1f2c-4b42-4d42-9f42-000000004222", op=1)
    A["jump"] = attr("jump", "palladium:jump_power", 0.3, "8a9e1f2c-4b42-4d42-9f42-000000004223", op=1)
    A["no_fall"] = ab("palladium:damage_immunity", hidden=True, damage_sources=["minecraft:is_fall"])
    return {
        "name": {"translate": "citylife.mark42.power"},
        "background": "citylife:textures/gui/mark42_background.png",
        "icon": "citylife:mark42_helmet",
        "energy_bars": {"energy": {"max": 1000, "auto_increase_per_tick": 4, "auto_increase_interval": 1,
                                   "color": "#ffc23d"}},
        "persistent_data": True,
        "abilities": A,
    }


def palladium() -> None:
    write_json(os.path.join(PAL_DATA, "powers", "mark42.json"), power())
    write_json(os.path.join(PAL_DATA, "item_powers", "mark42.json"),
               {"slot": "chest", "item": ["citylife:mark42_chestplate"], "power": ["citylife:mark42"]})
    write_json(os.path.join(PAL, "energy_beams", "mark42_repulsor.json"), {
        "body_part": "right_arm", "offset": [-1, -11, 0], "glow_color": "#7fd8ff", "core_color": "#ffffff",
        "glow_opacity": 0.6, "core_opacity": 1.0, "bloom": 2, "size": [1, 1]})
    write_json(os.path.join(PAL, "energy_beams", "mark42_unibeam.json"), {
        "body_part": "chest", "offset": [0, -3.25, -2.6], "glow_color": "#9fe8ff", "core_color": "#ffffff",
        "glow_opacity": 0.7, "core_opacity": 1.0, "bloom": 3, "size": [3, 3]})
    write_json(os.path.join(PAL, "trails", "mark42_flight.json"), {
        "type": "palladium:gradient", "color": "#ffd27a", "spacing": 0.6, "lifetime": 10,
        "requires_movement": True, "opacity": 0.35})
    write_json(os.path.join(PAL, "trails", "mark42_missile.json"), {
        "type": "palladium:gradient", "color": "#d9d9d9", "spacing": 0.4, "lifetime": 14,
        "requires_movement": True, "opacity": 0.45})
    write_json(os.path.join(PAL, "render_layers", "mark42_thrusters.json"), {
        "type": "palladium:thrusters", "color": "#ffcf8a", "right_arm": True, "left_arm": True,
        "right_leg": True, "left_leg": True})
    write_json(os.path.join(PAL, "particle_emitters", "mark42_thrusters.json"), [
        {"body_part": part, "amount": 1, "offset": [0, oy, 0], "offset_random": [0.15, 0.1, 0.15],
         "motion": [0, -0.05, 0], "motion_random": [0.03, 0.02, 0.03]}
        for part, oy in (("right_arm", -11), ("left_arm", -11), ("right_leg", -13), ("left_leg", -13))])


# ---------------------------------------------------------------------------

def main() -> int:
    for d in (ARMOR, ICONS, GUI):
        os.makedirs(d, exist_ok=True)
    outer = mirrored(PARTS_OUTER)
    inner = mirrored(PARTS_INNER)
    uv_o = pack(outer, 128)
    uv_i = pack(inner, 64)
    paint(outer, uv_o, 128, "outer")
    paint(inner, uv_i, 64, "inner")
    write_java(outer, uv_o, inner, uv_i)
    item_icons()
    ability_icons()
    gantry_textures()
    gantry_model()
    palladium()
    print("Mark 42: модель, текстуры, иконки и сила Palladium собраны")
    return 0


if __name__ == "__main__":
    sys.exit(main())
