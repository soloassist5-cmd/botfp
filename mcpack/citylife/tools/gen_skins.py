#!/usr/bin/env python3
"""
Скины жителей города.

NPC должны выглядеть людьми, а не ванильными жителями. Скины рисуем сами и
кладём в мод: так они работают без интернета и без учёток Mojang — Easy NPC
берёт их по ресурс-локации.

Каждый скин собирается из описания человека: тон кожи, причёска, глаза,
борода, одежда (футболка, рубашка, костюм, форма, халат, комбинезон),
обувь и головной убор. Рисуем по развёртке скина 64x64: у каждой части
тела шесть граней, и одежда «оборачивается» вокруг — ремень, манжеты,
светоотражающие полосы идут по кругу, а не только спереди. Грани
чуть разной яркости, у краёв лёгкая тень — в игре фигура объёмная.

Раньше второй слой (волосы) пытались «стереть» прозрачным цветом, но
Canvas.set прозрачное смешивает, а не стирает, и лицо целиком было
закрыто волосами. Теперь второй слой рисуется только там, где нужен.

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

# ---------------------------------------------------------------------------
#  Развёртка скина
# ---------------------------------------------------------------------------

# Часть тела: (x, y) угла развёртки, ширина, высота, глубина.
PARTS = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "arm_r": (40, 16, 4, 12, 4), "sleeve_r": (40, 32, 4, 12, 4),
    "arm_l": (32, 48, 4, 12, 4), "sleeve_l": (48, 48, 4, 12, 4),
    "leg_r": (0, 16, 4, 12, 4), "pants_r": (0, 32, 4, 12, 4),
    "leg_l": (16, 48, 4, 12, 4), "pants_l": (0, 48, 4, 12, 4),
}
# Порядок боковых граней при обходе вокруг части: правая, перед, левая, зад.
SIDES = ("right", "front", "left", "back")
LIGHT = {"top": 1.10, "bottom": 0.70, "right": 0.90, "front": 1.0, "left": 0.94, "back": 0.84}


def faces(part: str) -> dict[str, tuple[int, int, int, int]]:
    x, y, w, h, d = PARTS[part]
    return {
        "top": (x + d, y, w, d),
        "bottom": (x + d + w, y, w, d),
        "right": (x, y + d, d, h),
        "front": (x + d, y + d, w, h),
        "left": (x + d + w, y + d, d, h),
        "back": (x + d + w + d, y + d, w, h),
    }


def clamp(v: float) -> int:
    return max(0, min(255, round(v)))


def shade(colour, factor: float):
    return tuple(clamp(c * factor) for c in colour[:3])


def mix(a, b, t: float):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


class Skin:
    def __init__(self, seed: int):
        self.c = Canvas(64, 64)
        self.rng = random.Random(seed)

    def put(self, x: int, y: int, colour, alpha: int = 255) -> None:
        """Записать пиксель как есть (без смешивания), в том числе прозрачный."""
        if 0 <= x < 64 and 0 <= y < 64:
            self.c.px[y][x] = (*colour[:3], alpha)

    def paint(self, part: str, fn, faces_only: tuple[str, ...] | None = None,
              noise: float = 0.035) -> None:
        """
        Раскрасить часть тела. fn(face, u, v, w, h) -> цвет или None
        (None — пиксель не трогать; для второго слоя это прозрачность).
        Для боковых граней передаётся ещё и «обходная» координата: см. around().
        """
        for name, (fx, fy, fw, fh) in faces(part).items():
            if faces_only and name not in faces_only:
                continue
            for v in range(fh):
                for u in range(fw):
                    colour = fn(name, u, v, fw, fh)
                    if colour is None:
                        continue
                    k = LIGHT[name] * self.rng.uniform(1 - noise, 1 + noise)
                    # Лёгкая тень у нижнего края боковых граней: объём.
                    if name in SIDES and fh > 4 and v >= fh - 1:
                        k *= 0.9
                    self.put(fx + u, fy + v, shade(colour, k))


# ---------------------------------------------------------------------------
#  Описание человека
# ---------------------------------------------------------------------------

SKIN_TONES = [(247, 214, 186), (236, 193, 160), (214, 168, 130),
              (178, 128, 92), (136, 92, 64), (98, 66, 46)]
HAIR = {"black": (30, 26, 26), "dark": (58, 40, 30), "brown": (104, 68, 40),
        "auburn": (132, 58, 34), "blond": (206, 170, 104), "grey": (170, 170, 172)}
EYES = [(58, 92, 150), (78, 58, 40), (62, 110, 70), (40, 40, 46)]


class Person:
    def __init__(self, **kw):
        self.tone = kw.get("tone", 1)
        self.hair = kw.get("hair", "dark")
        self.style = kw.get("style", "short")      # short, long, buzz, bald, bun, curly
        self.beard = kw.get("beard", False)
        self.eyes = kw.get("eyes", 0)
        self.top = kw.get("top", "tee")             # tee, shirt, suit, uniform, coat, hoodie, overall
        self.shirt = kw.get("shirt", (70, 110, 170))
        self.jacket = kw.get("jacket", None)        # цвет пиджака/куртки поверх рубашки
        self.tie = kw.get("tie", None)
        self.sleeves = kw.get("sleeves", "long")    # long, short
        self.pants = kw.get("pants", (48, 52, 66))
        self.shoes = kw.get("shoes", (36, 32, 30))
        self.belt = kw.get("belt", (40, 32, 26))
        self.hat = kw.get("hat", None)              # police, fire, hardhat, chef, cap, beanie
        self.hat_colour = kw.get("hat_colour", (30, 40, 70))
        self.badge = kw.get("badge", None)          # police, medic, fire, name
        self.stripes = kw.get("stripes", None)      # цвет светоотражающих полос
        self.apron = kw.get("apron", None)


def draw(person: Person, seed: int):
    s = Skin(seed)
    tone = SKIN_TONES[person.tone]
    hair = HAIR[person.hair]
    hair_dark = shade(hair, 0.75)
    eye = EYES[person.eyes % len(EYES)]
    lips = mix(tone, (170, 70, 70), 0.35)

    # --- голова -----------------------------------------------------------------
    long_hair = person.style in ("long", "bun", "curly")
    fringe = {"short": 1, "long": 2, "curly": 2, "bun": 1, "buzz": 0, "bald": 0}[person.style]
    has_hair = person.style != "bald"

    def head(face, u, v, w, h):
        if face == "top":
            return hair if has_hair else shade(tone, 1.02)
        if face == "bottom":
            return shade(tone, 0.8)
        if face == "front":
            if has_hair and v < fringe:
                return hair if (u + v) % 3 else hair_dark
            if person.style == "long" and (u == 0 or u == 7) and v < 6:
                return hair
            if v == 3 and u in (1, 2, 5, 6):                      # брови
                return shade(hair if has_hair else (60, 50, 40), 0.85)
            if v == 4 and u in (1, 6):                            # белки
                return (242, 242, 246)
            if v == 4 and u in (2, 5):                            # радужка
                return eye
            if v == 5 and u in (3, 4):                            # нос
                return shade(tone, 0.88)
            if v == 6 and u in (3, 4):                            # рот
                return lips
            if person.beard and (v == 7 or (v == 6 and u in (1, 2, 5, 6)) or
                                 (v == 5 and u in (0, 7))):
                return shade(hair, 0.9)
            if v == 5 and u in (1, 6):                            # румянец
                return mix(tone, (220, 120, 110), 0.12)
            return tone
        # Бока и затылок.
        if not has_hair:
            return tone
        if face == "back":
            limit = 8 if long_hair else (4 if person.style == "buzz" else 6)
            return hair if v < limit else tone
        # Бок: волосы сверху, ухо, у длинных — до плеч.
        limit = 7 if long_hair else (2 if person.style == "buzz" else 3)
        ear_u = 3 if face == "right" else 4
        if v < limit and not (v >= 3 and u == ear_u and not long_hair):
            return hair
        if v in (4, 5) and u == ear_u:
            return shade(tone, 0.9)
        return tone

    s.paint("head", head, noise=0.02)

    # Второй слой головы: пучок/кудри и головные уборы.
    hat = person.hat
    hc = person.hat_colour

    def hat_layer(face, u, v, w, h):
        if hat == "police":
            if face == "top":
                return hc
            if face in SIDES:
                if v < 2:
                    return hc
                if v == 2:                                        # околыш и козырёк
                    return (20, 20, 22) if face == "front" else shade(hc, 0.8)
                if face == "front" and v == 1 and u in (3, 4):
                    return (226, 196, 86)                         # кокарда
            return None
        if hat == "fire":
            if face == "top":
                return hc if (u + v) % 4 else shade(hc, 1.15)
            if face in SIDES:
                if v < 3:
                    return hc
                if face == "front" and v == 1 and u in (3, 4):
                    return (230, 200, 70)                         # эмблема
                if face in ("back", "left", "right") and v == 3:
                    return shade(hc, 0.8)                         # назатыльник
            if face == "front" and v == 0 and 2 <= u <= 5:
                return (230, 200, 70)
            return None
        if hat == "hardhat":
            if face == "top":
                return hc
            if face in SIDES and v < 2:
                return hc if v == 0 else shade(hc, 0.85)
            return None
        if hat == "chef":
            if face == "top":
                return (250, 250, 250)
            if face in SIDES and v < 2:
                return (244, 244, 246)
            return None
        if hat == "cap":
            if face == "top":
                return hc
            if face in SIDES and v < 2:
                return hc if face != "front" or v == 0 else shade(hc, 0.8)
            return None
        if hat == "medic":
            if face == "top":
                return (240, 244, 248)
            if face in SIDES and v < 2:
                if face == "front" and ((v == 0 and u in (3, 4)) or (v == 1 and 2 <= u <= 5)):
                    return (214, 64, 64)
                return (240, 244, 248)
            return None
        if person.style == "bun":
            if face == "back" and 1 <= v <= 3 and 2 <= u <= 5:
                return hair_dark
            return None
        if person.style == "curly":
            if face == "top" or (face in ("left", "right", "back") and v < 5):
                return hair if (u + v) % 2 else hair_dark
            return None
        return None

    s.paint("hat", hat_layer, noise=0.03)

    # --- туловище ------------------------------------------------------------------
    shirt = person.shirt
    jacket = person.jacket
    belt_row = 11

    def body(face, u, v, w, h):
        if face == "top":
            return shade(jacket or shirt, 1.0)
        if face == "bottom":
            return person.pants
        if v == belt_row and person.belt and person.top not in ("coat", "overall"):
            if face == "front" and u in (3, 4):
                return (196, 180, 120)                            # пряжка
            return person.belt
        if person.top == "overall":
            if face == "front" and v < 4 and 1 < u < 6:
                return person.pants                               # нагрудник
            if face == "front" and v < 4 and u in (1, 6):
                return person.pants                               # лямки
            if v >= 4:
                return person.pants
            return shirt
        if person.stripes and v in (7, 8):
            return person.stripes
        if face == "front":
            if person.apron and v >= 3:
                return person.apron if 1 <= u <= 6 else shirt
            if person.top in ("shirt", "suit", "uniform", "coat"):
                # Воротник и пуговицы.
                if v == 0 and u in (2, 5):
                    return mix(shirt, (255, 255, 255), 0.4) if person.top != "suit" else (236, 236, 240)
                if person.top == "suit":
                    if u in (3, 4):
                        if person.tie and 1 <= v <= 7:
                            return person.tie if v < 7 else shade(person.tie, 0.8)
                        return (236, 236, 240)                    # рубашка в вырезе
                    if u in (2, 5) and v < 5:
                        return shade(jacket, 0.8)                 # лацканы
                    return jacket
                if person.top == "coat":
                    if u in (3, 4) and v < 4:
                        return person.tie or (236, 236, 240)
                    if u == 4 and v % 3 == 1:
                        return shade(shirt, 0.8)
                    if v in (7, 8) and u in (1, 6):
                        return shade(shirt, 0.88)                 # карманы
                    return shirt
                if u == 4 and v % 3 == 1:
                    return shade(shirt, 0.75)                     # пуговицы
                if person.top == "uniform" and v in (2, 3) and u in (1, 2, 5, 6):
                    return shade(shirt, 0.88)                     # нагрудные карманы
            if person.top == "hoodie" and v in (0, 1) and 2 <= u <= 5:
                return shade(shirt, 0.8)
            if person.top == "hoodie" and v in (7, 8) and 2 <= u <= 5:
                return shade(shirt, 0.9)                          # карман-кенгуру
            if person.top == "tee" and v == 0 and u in (3, 4):
                return tone                                       # вырез
            # Нашивки.
            if person.badge == "police" and v == 2 and u == 1:
                return (226, 196, 86)
            if person.badge == "name" and v == 2 and u in (5, 6):
                return (240, 240, 244)
        if face == "back" and person.badge == "fire" and v in (3, 4) and 1 <= u <= 6:
            return (230, 200, 70)                                 # надпись на спине
        if face == "back" and person.badge == "medic" and 2 <= v <= 5 and u in (3, 4):
            return (214, 64, 64)
        if face == "back" and person.badge == "medic" and v in (3, 4) and 2 <= u <= 5:
            return (214, 64, 64)
        return jacket if person.top == "suit" else shirt

    s.paint("body", body)
    if person.badge == "medic":
        # Красный крест на груди.
        fx, fy, _, _ = faces("body")["front"]
        for (u, v) in ((6, 1), (5, 2), (6, 2), (7, 2), (6, 3)):
            s.put(fx + u, fy + v, (214, 64, 64))

    # --- руки -----------------------------------------------------------------------
    sleeve = jacket if person.top == "suit" else shirt
    if person.top == "overall":
        sleeve = shirt
    short = person.sleeves == "short"

    def arm(face, u, v, w, h):
        if face == "top":
            return sleeve
        if face == "bottom":
            return tone
        if short:
            if v < 4:
                return sleeve if v < 3 else shade(sleeve, 0.85)
            return tone
        if person.stripes and v in (6, 7):
            return person.stripes
        if v >= 11:
            return tone                                           # кисть
        if v == 10:
            if person.top == "suit":
                return (236, 236, 240)                            # манжета рубашки
            return shade(sleeve, 0.82)
        return sleeve

    s.paint("arm_r", arm)
    s.paint("arm_l", arm)

    # --- ноги -----------------------------------------------------------------------
    pants = person.pants

    def leg(face, u, v, w, h):
        if face == "top":
            return pants
        if face == "bottom":
            return shade(person.shoes, 0.8)
        if v >= 10:
            if v == 10 and face == "front":
                return shade(person.shoes, 1.2)                   # носок ботинка
            return person.shoes
        if person.stripes and v in (7, 8):
            return person.stripes
        if person.apron and face == "front" and v < 5:
            return person.apron
        if person.top == "coat" and v < 5:
            return shirt                                          # полы халата
        if face == "front" and u == 0 and v < 9:
            return shade(pants, 0.9)                              # стрелка
        return pants

    s.paint("leg_r", leg)
    s.paint("leg_l", leg)
    return s.c


# ---------------------------------------------------------------------------
#  Жители
# ---------------------------------------------------------------------------

NAVY = (30, 42, 78)
PEOPLE = {
    "citizen_a": Person(tone=1, hair="brown", style="short", top="hoodie", shirt=(70, 104, 150),
                        pants=(52, 64, 94), shoes=(220, 220, 224), sleeves="long", eyes=0),
    "citizen_b": Person(tone=3, hair="black", style="long", top="tee", shirt=(176, 70, 84),
                        pants=(44, 50, 70), shoes=(40, 36, 34), sleeves="short", eyes=1),
    "citizen_c": Person(tone=0, hair="blond", style="bun", top="shirt", shirt=(96, 150, 110),
                        pants=(150, 128, 96), shoes=(84, 56, 36), sleeves="short", eyes=2),
    "citizen_d": Person(tone=4, hair="black", style="curly", beard=True, top="tee",
                        shirt=(226, 176, 70), pants=(58, 60, 64), shoes=(30, 30, 32),
                        sleeves="short", eyes=1),
    "shopkeeper": Person(tone=2, hair="auburn", style="bun", top="shirt", shirt=(240, 240, 244),
                         apron=(46, 120, 196), pants=(52, 56, 70), eyes=2),
    "banker": Person(tone=0, hair="grey", style="short", top="suit", shirt=(236, 236, 240),
                     jacket=(34, 38, 52), tie=(170, 40, 50), pants=(30, 34, 46),
                     shoes=(20, 18, 18), eyes=0),
    "police": Person(tone=2, hair="dark", style="buzz", top="uniform", shirt=NAVY,
                     pants=(26, 32, 56), shoes=(18, 18, 20), hat="police", hat_colour=NAVY,
                     badge="police", belt=(20, 20, 22), eyes=1),
    "medic": Person(tone=1, hair="brown", style="bun", top="coat", shirt=(242, 246, 250),
                    pants=(120, 190, 200), shoes=(236, 236, 240), hat="medic", badge="medic",
                    eyes=2),
    "firefighter": Person(tone=3, hair="black", style="short", beard=True, top="uniform",
                          shirt=(46, 44, 40), pants=(46, 44, 40), shoes=(22, 20, 20),
                          hat="fire", hat_colour=(196, 34, 30), stripes=(222, 214, 96),
                          badge="fire", belt=(22, 20, 20), eyes=1),
    "mechanic": Person(tone=2, hair="brown", style="short", beard=True, top="overall",
                       shirt=(200, 196, 188), pants=(52, 80, 130), shoes=(40, 34, 30),
                       hat="cap", hat_colour=(190, 60, 40), sleeves="short", eyes=0),
    "builder": Person(tone=3, hair="dark", style="short", top="tee", shirt=(236, 116, 40),
                      stripes=(232, 232, 120), pants=(70, 74, 86), shoes=(92, 64, 40),
                      hat="hardhat", hat_colour=(240, 196, 40), sleeves="long", eyes=1),
    "bartender": Person(tone=1, hair="black", style="short", top="suit", shirt=(236, 236, 240),
                        jacket=(40, 40, 46), tie=(20, 20, 24), pants=(30, 30, 36), eyes=3),
    "cook": Person(tone=2, hair="dark", style="short", top="coat", shirt=(246, 246, 244),
                   tie=(200, 60, 60), pants=(60, 62, 72), hat="chef", eyes=1),
    "clerk": Person(tone=0, hair="auburn", style="long", top="shirt", shirt=(150, 170, 206),
                    pants=(52, 56, 70), badge="name", eyes=2),
    "gunsmith": Person(tone=2, hair="grey", style="buzz", beard=True, top="uniform",
                       shirt=(92, 100, 70), pants=(66, 64, 52), shoes=(40, 34, 28),
                       apron=(100, 70, 44), eyes=3),
    "realtor": Person(tone=1, hair="blond", style="bun", top="suit", shirt=(236, 236, 240),
                      jacket=(70, 80, 120), pants=(52, 58, 84), shoes=(30, 26, 26), eyes=0),
    "dealer": Person(tone=4, hair="black", style="buzz", top="suit", shirt=(236, 236, 240),
                     jacket=(52, 54, 64), tie=(214, 176, 60), pants=(40, 42, 52), eyes=1),
}


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    for index, (name, person) in enumerate(PEOPLE.items()):
        canvas = draw(person, seed=2000 + index * 31)
        canvas.write(os.path.join(OUT, f"{name}.png"))
    print(f"Скинов записано: {len(PEOPLE)} -> {os.path.relpath(OUT, os.path.dirname(ROOT))}")
    print("  " + ", ".join(PEOPLE))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
