#!/usr/bin/env python3
"""
Дроны города: модель, текстуры трёх расцветок и иконки.

  * «Сокол» — дрон-камера: светлый корпус, стабилизированный подвес;
  * «Стриж» — гоночный FPV: чёрный карбон с неоновыми кромками;
  * «Пеликан» — курьер: жёлтый, с захватом для посылки.

Модель одна (client/drone/DroneModel.java), размер у каждого свой — его
задаёт рендерер. Как у Mark 42, кубы описаны тут: развёртка UV и картинка
считаются из одного списка.

    python3 citylife/tools/gen_drones.py
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Canvas  # noqa: E402
from gen_gadgets import ASSETS, ITEMS, write_json  # noqa: E402
import gen_mark42 as M  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java", "dev", "lscity", "citylife", "client", "drone", "DroneModel.java")
TEX = os.path.join(ASSETS, "textures", "entity", "drone")

# Части: (имя, x, y, z, ш, в, г, раздув, стиль). Ось y вниз, начало — центр
# дрона на уровне рамы; модель рисуется «вверх ногами», как все сущности.
BODY = [
    ("hull", -3, -2, -4, 6, 2, 8, 0.0, "hull"),
    ("canopy", -2, -3, -3, 4, 1, 5, 0.0, "canopy"),
    ("battery", -2, 0, -1, 4, 1, 4, 0.0, "battery"),
    ("gimbal", -1, 0, -5, 2, 2, 2, 0.0, "gimbal"),
    ("led_front", -2.5, -1.6, -4.3, 5, 1, 1, -0.3, "led_front"),
    ("led_back", -2.5, -1.6, 3.3, 5, 1, 1, -0.3, "led_back"),
    ("skid_r", -3.5, 0, -3, 1, 2, 6, -0.2, "skid"),
    ("skid_l", 2.5, 0, -3, 1, 2, 6, -0.2, "skid"),
]
# Лучи рамы — повёрнутые части, у каждой мотор и винт на конце.
ARM = [
    ("arm", -0.5, -1.5, 0, 1, 1, 7, 0.0, "arm"),
    ("motor", -1, -2.5, 6, 2, 2, 2, 0.0, "motor"),
]
ROTOR = [
    ("blade_a", -3.5, 0, -0.5, 7, 0, 1, 0.0, "blade"),
    ("blade_b", -0.5, 0, -3.5, 1, 0, 7, 0.0, "blade"),
]

SCHEMES = {
    # (корпус, кромка, акцент, свет спереди)
    "camera": ((226, 230, 236), (150, 156, 168), (60, 64, 74), (255, 255, 255)),
    "racer": ((40, 42, 48), (22, 22, 26), (64, 255, 196), (64, 255, 196)),
    "courier": ((246, 196, 40), (170, 120, 18), (40, 40, 46), (255, 240, 200)),
}


def all_cubes():
    out = {"body": BODY, "arm": ARM, "rotor": ROTOR}
    return out


def pack():
    uv = {}
    x = y = shelf = 0
    size = 64
    for part, cubes in all_cubes().items():
        for cube in cubes:
            name, cx, cy, cz, w, h, d = cube[:7]
            W, H, D = math.ceil(w), max(1, math.ceil(h)), math.ceil(d)
            bw, bh = 2 * (D + W), D + H
            if x + bw > size:
                x, y, shelf = 0, y + shelf + 1, 0
            uv[(part, name)] = (x, y)
            x += bw + 1
            shelf = max(shelf, bh)
    return uv


def mat(c, dark=0.0):
    hi = tuple(min(255, int(v * 1.12 + 10)) for v in c)
    lo = tuple(int(v * 0.72) for v in c)
    sm = tuple(int(v * 0.5) for v in c)
    base = tuple(int(v * (1 - dark)) for v in c)
    return hi, base, lo, sm


def paint(scheme: str, uv: dict) -> None:
    body, edge, accent, light = SCHEMES[scheme]
    B, E, A = mat(body), mat(edge), mat(accent)
    c, g = Canvas(64, 64), Canvas(64, 64)

    def styles(style, f):
        if style == "hull":
            M.paint_all(f, B)
            f["top"].vline(f["top"].w // 2, B)
            for side in ("right", "left"):
                f[side].hline(0, E)
            f["bottom"].fill(E, shade=False)
        elif style == "canopy":
            M.paint_all(f, B)
            f["top"].fill(A, x0=1, y0=1, x1=f["top"].w - 2, y1=f["top"].h - 2)
        elif style == "battery":
            M.paint_all(f, E)
            f["bottom"].fill(A, shade=False)
            f["back"].glow(1, 0, (80, 255, 120, 255))
            f["back"].glow(2, 0, (80, 255, 120, 255))
        elif style == "gimbal":
            M.paint_all(f, A)
            fr = f["front"]
            fr.px(0, 0, M.rgba(A[3]))
            fr.glow(0, 1, (120, 200, 255, 255))
            fr.glow(1, 0, (40, 90, 160, 255))
            fr.glow(1, 1, (190, 230, 255, 255))
        elif style == "led_front":
            M.paint_all(f, E)
            for x in (0, f["front"].w - 1):
                f["front"].glow(x, 0, M.rgba(light))
        elif style == "led_back":
            M.paint_all(f, E)
            for x in (0, f["back"].w - 1):
                f["back"].glow(x, 0, (255, 40, 40, 255))
        elif style == "skid":
            M.paint_all(f, A)
        elif style == "arm":
            M.paint_all(f, E if scheme != "racer" else A)
            if scheme == "racer":
                for name in ("right", "left"):
                    for y in range(f[name].h):
                        f[name].glow(f[name].w - 1, y, M.rgba(accent))
        elif style == "motor":
            M.paint_all(f, A if scheme != "racer" else E)
            f["top"].px(0, 0, M.rgba((200, 200, 210)))
        elif style == "blade":
            for face in f.values():
                for x in range(face.w):
                    for y in range(face.h):
                        tip = x in (0, face.w - 1) or y in (0, face.h - 1)
                        face.px(x, y, (30, 32, 38, 255) if not tip else M.rgba(accent if scheme == "racer"
                                                                               else (210, 60, 40)))

    for part, cubes in all_cubes().items():
        for cube in cubes:
            name, x, y, z, w, h, d, inf, style = cube
            W, H, D = math.ceil(w), max(1, math.ceil(h)), math.ceil(d)
            u, v = uv[(part, name)]
            styles(style, M.faces(c, g, u, v, W, H, D))
    os.makedirs(TEX, exist_ok=True)
    c.write(os.path.join(TEX, f"{scheme}.png"))
    g.write(os.path.join(TEX, f"{scheme}_glow.png"))


def java(uv: dict) -> None:
    fl = M.fl
    lines = [
        "package dev.lscity.citylife.client.drone;",
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
        " * Модель дрона: корпус, подвес камеры, четыре луча с моторами и винтами.",
        " * Собрана citylife/tools/gen_drones.py вместе с текстурами.",
        " */",
        "@OnlyIn(Dist.CLIENT)",
        "public final class DroneModel {",
        "",
        "    public static final ModelLayerLocation LAYER =",
        "            new ModelLayerLocation(new ResourceLocation(\"citylife\", \"drone\"), \"main\");",
        "",
        "    private DroneModel() {",
        "    }",
        "",
        "    public static LayerDefinition create() {",
        "        MeshDefinition mesh = new MeshDefinition();",
        "        PartDefinition root = mesh.getRoot();",
        "        PartDefinition body = root.addOrReplaceChild(\"body\", CubeListBuilder.create(), PartPose.ZERO);",
    ]

    def cube_line(var, part, cube):
        name, x, y, z, w, h, d, inf = cube[:8]
        u, v = uv[(part, name)]
        return (f"        {var}.addOrReplaceChild(\"{name}\", CubeListBuilder.create().texOffs({u}, {v})"
                f".addBox({fl(x)}, {fl(y)}, {fl(z)}, {fl(w)}, {fl(h)}, {fl(d)}, new CubeDeformation({fl(inf)})),"
                f" PartPose.ZERO);")

    for cube in BODY:
        lines.append(cube_line("body", "body", cube))
    for i, deg in enumerate((45, 135, 225, 315)):
        rad = math.radians(deg)
        lines.append(f"        PartDefinition arm{i} = body.addOrReplaceChild(\"arm{i}\", CubeListBuilder.create(), "
                     f"PartPose.rotation(0F, {fl(rad)}, 0F));")
        for cube in ARM:
            lines.append(cube_line(f"arm{i}", "arm", cube))
        lines.append(f"        PartDefinition rotor{i} = arm{i}.addOrReplaceChild(\"rotor\", CubeListBuilder.create(), "
                     f"PartPose.offset(0F, -2.6F, 7F));")
        for cube in ROTOR:
            lines.append(cube_line(f"rotor{i}", "rotor", cube))
    lines += ["        return LayerDefinition.create(mesh, 64, 64);", "    }", "}"]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


ICON_DRONE = [
    "................",
    "................",
    ".kkk........kkk.",
    "..m..........m..",
    "..mAA......AAm..",
    "....AABBBBAA....",
    ".....BBBBBB.....",
    ".....BBccBB.....",
    ".....BBBBBB.....",
    "....AABBBBAA....",
    "..mAA..gg..AAm..",
    "..m....gL....m..",
    ".kkk........kkk.",
    "................",
    "................",
    "................",
]
ICON_REMOTE = [
    "................",
    "...........S....",
    "...........S....",
    "...........S....",
    "..DDDDDDDDDDDD..",
    ".DDdddddddddddD.",
    ".DdCCCCCCCCCCdD.",
    ".DdCWWCCCCCCCdD.",
    ".DdCCCCCCCCCCdD.",
    ".DddddddddddddD.",
    ".DdOdddddddOddD.",
    ".DOOOdddddOOOdD.",
    ".DdOdddddddOddD.",
    "..DDDD....DDDD..",
    "................",
    "................",
]


def icons() -> None:
    for scheme, (body, edge, accent, light) in SCHEMES.items():
        pal = {"B": M.rgba(body), "A": M.rgba(edge), "m": M.rgba(accent), "k": (30, 30, 34, 255),
               "c": M.rgba(accent), "g": (40, 44, 52, 255), "L": (150, 210, 255, 255)}
        cv = Canvas(16, 16)
        M.put(cv, ICON_DRONE, pal)
        if scheme == "courier":
            cv.rect(6, 11, 9, 13, (150, 110, 60, 255))
            cv.frame(6, 11, 9, 13, (100, 70, 36, 255))
        cv.write(os.path.join(ITEMS, f"drone_{scheme}.png"))
        write_json(os.path.join(ASSETS, "models", "item", f"drone_{scheme}.json"),
                   {"parent": "item/generated", "textures": {"layer0": f"citylife:item/drone_{scheme}"}})
    pal = {"D": (44, 46, 54, 255), "d": (70, 74, 84, 255), "S": (160, 166, 176, 255), "C": (40, 90, 140, 255),
           "W": (160, 220, 255, 255), "O": (190, 194, 204, 255)}
    cv = Canvas(16, 16)
    M.put(cv, ICON_REMOTE, pal)
    cv.write(os.path.join(ITEMS, "drone_remote.png"))
    write_json(os.path.join(ASSETS, "models", "item", "drone_remote.json"),
               {"parent": "item/generated", "textures": {"layer0": "citylife:item/drone_remote"}})


def main() -> int:
    uv = pack()
    for scheme in SCHEMES:
        paint(scheme, uv)
    java(uv)
    icons()
    print("Дроны: модель, три расцветки и иконки собраны")
    return 0


if __name__ == "__main__":
    sys.exit(main())
