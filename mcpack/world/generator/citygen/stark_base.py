"""
Подвал башни STARK: Зал брони, как в «Железном человеке 3», мастерская
Тони и пост охраны «Джарвиса».

Подвал занимает весь участок под площадью и башней и опускается на два
уровня:

* −1, мастерская (пол на 60): станки Satsu, два 3D-принтера Stark
  Industries, голостол, Дубина у верстака, склад, бар и пост охраны за
  стеклом — пульт «Джарвис» и стена голо-мониторов;
* −2 и −3, Зал брони (пол на 44, потолок на 60): по стенам в два яруса
  стоят стеклянные капсулы с подсветкой, в каждой — костюм на стойке и
  табличка с маркой. Верхний ярус — галерея на 52 вдоль стен; в центре
  вокруг ядра — площадка, от неё к галерее идут четыре мостика. Под
  южным мостиком на круглой сцене стоит Халкбастер, на стенах ядра —
  голо-мониторы со списком брони.

Попасть вниз можно по лестнице ядра и на лифте (кнопки −1, −2, −3).
Пульт охраны знает, что охраняет: зоны подвала, камеры SecurityCraft
и места стоек с костюмами — стойки ставит и пополняет мод (citylife:
stark), поэтому в генераторе сущностей нет.

Координаты — локальные u/v участка Старка (фасадом на север, u = x - x0,
v = z - z0); высоты абсолютные.
"""
from __future__ import annotations

import random

from . import blocks as B
from . import furniture as F
from . import nbt
from . import suits as S
from .frame import LOCAL_VEC, OPPOSITE, Frame

FLOOR_B2 = 44          # пол Зала брони
FLOOR_MEZZ = 52        # галерея и центральная площадка
FLOOR_B1 = 60          # пол мастерской
CEIL_B1 = 66           # потолок мастерской (под полом вестибюля на 68)
BELOW = [44, 48, 52, 56, 60, 64]      # уровни лестницы ниже вестибюля
STOPS = [44, 52, 60]                  # там выход из лестницы и лифта

U0, U1 = 3, 40         # внутренние стены подвала
V0, V1 = 4, 40

WALL = B.POLISHED_DEEPSLATE
FLOOR = "minecraft:polished_blackstone"
TRIM = B.QUARTZ_SMOOTH
DECK = "minecraft:smooth_stone"
RAIL = B.GLASS_PANE
FRAME = "minecraft:black_concrete"      # тонкие рамки капсул
SIGN = "dark_oak"
GLOW = B.LAMP

# Ядро башни (см. towers.Tower.core): лестница u 17..21, шахта лифта u 22..25.
CORE = (17, 21, 21, 26)   # u0, v0, u1, v1 — ствол лестницы с площадками, заливка перед core()


def _in_core(u: int, v: int) -> bool:
    """Клетки ядра: ствол лестницы с площадками и шахта лифта."""
    return 17 <= u <= 21 and 21 <= v <= 26 or 22 <= u <= 25 and 23 <= v <= 26


def _side_cell(side: str, a: int, d: int) -> tuple[int, int]:
    """Клетка у стены side: a — вдоль стены, d — глубина от стены в зал."""
    if side == "N":
        return a, V0 + d
    if side == "S":
        return a, V1 - d
    if side == "W":
        return U0 + d, a
    return U1 - d, a


SIDE_OUT = {"N": "back", "S": "front", "W": "right", "E": "left"}
# Капсулы по 2 клетки и перегородка: вдоль северной и южной стен — 12, по бокам — 7.
SIDE_PODS = {"N": list(range(4, 38, 3)), "S": list(range(4, 38, 3)),
             "W": list(range(12, 31, 3)), "E": list(range(12, 31, 3))}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}


class Basement:
    def __init__(self, frame: Frame, rng: random.Random):
        self.f = frame
        self.rng = rng
        self.suits: list[dict] = []
        self.cams: list[dict] = []

    # --- координаты мира ---------------------------------------------------------

    def world_point(self, u: float, v: float) -> tuple[float, float]:
        """Точка внутри участка (дробные u, v) — в координаты мира."""
        iu, iv = int(u // 1), int(v // 1)
        fu, fv = u - iu, v - iv
        x, z = self.f.world(iu, iv)
        du, dv = self.f.du, self.f.dv
        # Клетка (iu, iv) в мире — блок (x, z); смещение внутри неё поворачиваем.
        ox = du[0] * (fu - 0.5) + dv[0] * (fv - 0.5)
        oz = du[1] * (fu - 0.5) + dv[1] * (fv - 0.5)
        return x + 0.5 + ox, z + 0.5 + oz

    def stand(self, u: float, y: float, v: float, out: str, item: str, slot: str = "chest") -> None:
        x, z = self.world_point(u, v)
        self.suits.append({"x": nbt.Double(round(x, 3)), "y": nbt.Double(y), "z": nbt.Double(round(z, 3)),
                           "yaw": nbt.Float(YAW[self.f.dir(out)]), "item": item, "slot": slot})

    def camera(self, u: int, y: int, v: int, name: str, facing: str = "down") -> None:
        state = (f"securitycraft:security_camera[being_viewed=false,facing={facing},"
                 f"powered=false,waterlogged=false]")
        self.f.block_entity(u, y, v, state, "securitycraft:security_camera",
                            {"owner": "Stark Industries", "ownerUUID": "00000000-0000-0000-0000-000000005ae4"})
        x, z = self.f.world(u, v)
        self.cams.append({"x": x, "y": y, "z": z, "name": name})

    def screen(self, u: int, y: int, v: int, out: str, mode: str, w: int, h: int) -> None:
        """Голо-монитор: (u, y, v) — левый нижний угол, если смотреть на экран; out — куда смотрит."""
        self.f.block_entity(u, y, v, f"citylife:holo_screen[facing={self.f.dir(out)}]", "citylife:holo_screen",
                            {"Mode": mode, "Width": w, "Height": h})

    def laser(self, u: int, y: int, v: int, beam: str) -> None:
        self.f.block_entity(u, y, v, f"citylife:laser_sensor[facing={self.f.dir(beam)}]",
                            "citylife:laser_sensor", {})

    # --- коробка подвала ----------------------------------------------------------

    def dig(self) -> None:
        """Сплошной короб под участком, затем пустоты залов. Вызывать до core()."""
        f = self.f
        f.fill(U0 - 2, FLOOR_B2 - 1, V0 - 2, U1 + 2, CEIL_B1 + 1, V1 + 2, WALL)
        f.fill(U0, FLOOR_B2 + 1, V0, U1, FLOOR_B1 - 1, V1, B.AIR)
        f.fill(U0, FLOOR_B1 + 1, V0, U1, CEIL_B1 - 1, V1, B.AIR)
        f.fill(U0, FLOOR_B2, V0, U1, FLOOR_B2, V1, FLOOR)
        f.fill(U0, FLOOR_B1, V0, U1, FLOOR_B1, V1, "minecraft:light_gray_concrete")
        # Ствол лестницы и площадки этажей до вестибюля: сплошняк, лестница
        # вырежет в нём марши, а выходы и закрытые площадки — ниже.
        cu0, cv0, cu1, cv1 = CORE
        f.fill(cu0, FLOOR_B2 + 1, cv0, cu1, CEIL_B1 + 1, cv1, WALL)

    def landings(self) -> None:
        """Площадки лестницы: на 44/52/60 — выход в зал, на 48/56/64 — глухой тамбур."""
        f = self.f
        for fy in BELOW:
            if fy in STOPS:
                f.fill(18, fy + 1, 21, 20, fy + 3, 22, B.AIR)
                f.light(19, fy + 3, 21)
            else:
                f.fill(18, fy, 22, 20, fy, 22, WALL)
                f.fill(18, fy + 1, 22, 20, fy + 3, 22, B.AIR)

    # --- Зал брони -----------------------------------------------------------------

    def hall(self) -> None:
        f = self.f
        pods = []
        for yb in (FLOOR_B2, FLOOR_MEZZ):
            for side in ("N", "W", "E", "S"):
                for a0 in SIDE_PODS[side]:
                    pods.append((side, a0, yb))
        # Галерея вдоль стен: настил на 52 глубиной 7, ограждение по краю.
        for side in ("N", "S", "W", "E"):
            lo, hi = (U0, U1) if side in "NS" else (V0, V1)
            for a in range(lo, hi + 1):
                for d in range(0, 7):
                    u, v = _side_cell(side, a, d)
                    f.set(u, FLOOR_MEZZ, v, DECK)
        self._rails()
        # Стены за капсулами сплошные; капсулы вырезаются в них ниже.
        for yb, top in ((FLOOR_B2, FLOOR_MEZZ - 1), (FLOOR_MEZZ, FLOOR_B1 - 1)):
            for side in ("N", "S", "W", "E"):
                lo, hi = (U0, U1) if side in "NS" else (V0 + 7, V1 - 7)
                for a in range(lo, hi + 1):
                    for d in range(0, 3):
                        u, v = _side_cell(side, a, d)
                        f.fill(u, yb + 1, v, u, top, v, WALL)
        for side, a0, yb in pods:
            self._pod_shell(side, a0, yb)
        self._platform()
        self._floor_lights()
        # Костюмы по капсулам; Халкбастер — на сцене, свободные капсулы ждут новых марок.
        queue = [s for s in S.SUITS if s[0] != S.HULKBUSTER]
        for side, a0, yb in pods:
            if queue:
                item, l1, l2 = queue.pop(0)
                self._pod_suit(side, a0, yb, item, l1, l2)
            else:
                self._pod_sign(side, a0, yb, ["MARK ??", "В РАЗРАБОТКЕ"])
        self._hulkbuster()
        # Мониторы на стенах ядра: список брони, «Джарвис», реактор.
        self.screen(16, FLOOR_B2 + 2, 23, "left", "armor", 4, 3)
        self.screen(26, FLOOR_B2 + 2, 26, "right", "jarvis", 4, 3)
        self.screen(17, FLOOR_B2 + 2, 27, "back", "reactor", 5, 3)
        self.screen(16, FLOOR_MEZZ + 2, 23, "left", "security", 4, 2)
        self.screen(26, FLOOR_MEZZ + 2, 26, "right", "radar", 4, 2)
        # Лазерные рубежи: выходы с лестницы и из лифта на −2 и −3.
        for fy in (FLOOR_B2, FLOOR_MEZZ):
            for dy in (1, 2):
                self.laser(18, fy + dy, 21, "right")
            f.fill(26, fy + 1, 21, 26, fy + 3, 21, TRIM)
            for dy in (1, 2):
                self.laser(22, fy + dy, 21, "right")
        # Камеры под потолком по углам зала.
        for u, v, name in ((10, 12, "ЗАЛ БРОНИ · СЗ"), (33, 12, "ЗАЛ БРОНИ · СВ"),
                           (10, 32, "ЗАЛ БРОНИ · ЮЗ"), (33, 32, "ЗАЛ БРОНИ · ЮВ")):
            self.camera(u, FLOOR_B1 - 1, v, name)
        # Свет: под галереей и под потолком над центром.
        for side in ("N", "S", "W", "E"):
            lo, hi = (U0 + 7, U1 - 7) if side in "NS" else (V0 + 7, V1 - 7)
            for a in range(lo, hi + 1, 3):
                u, v = _side_cell(side, a, 4)
                f.light(u, FLOOR_MEZZ - 1, v)
                f.light(u, FLOOR_B1 - 2, v)
        f.light_grid(U0 + 7, V0 + 7, U1 - 7, V1 - 7, FLOOR_B1 - 1, 4)

    def _rails(self) -> None:
        f = self.f
        bridges = {("N", a) for a in (20, 21, 22)} | {("S", a) for a in (20, 21, 22)} | \
                  {("W", a) for a in (23, 24, 25)} | {("E", a) for a in (23, 24, 25)}
        for side in ("N", "S", "W", "E"):
            lo, hi = (U0 + 7, U1 - 7) if side in "NS" else (V0 + 7, V1 - 7)
            for a in range(lo, hi + 1):
                if (side, a) in bridges:
                    continue
                u, v = _side_cell(side, a, 6)
                f.set(u, FLOOR_MEZZ + 1, v, RAIL)
                # Кромка галереи светится снизу.
                f.set(u, FLOOR_MEZZ, v, TRIM)

    def _pod_shell(self, side: str, a0: int, yb: int) -> None:
        """Капсула: подсвеченная ниша 2×2, стекло спереди, перегородки, световая крышка."""
        f = self.f
        for a in (a0 - 1, a0 + 2):
            for d in range(0, 3):
                u, v = _side_cell(side, a, d)
                f.fill(u, yb + 1, v, u, yb + 5, v, FRAME if d == 2 else WALL)
        for a in (a0, a0 + 1):
            for d in (0, 1):
                u, v = _side_cell(side, a, d)
                # Пол ниши светится: костюм подсвечен снизу, как в фильме.
                f.set(u, yb, v, GLOW)
                f.fill(u, yb + 1, v, u, yb + 4, v, B.AIR)
                f.set(u, yb + 5, v, GLOW)
            u, v = _side_cell(side, a, 2)
            f.set(u, yb, v, FRAME)
            f.fill(u, yb + 1, v, u, yb + 4, v, B.GLASS)
            f.set(u, yb + 5, v, FRAME)
            # Задняя стенка капсулы светлая — силуэт костюма читается издали.
            u, v = _side_cell(side, a, -1)
            f.fill(u, yb + 1, v, u, yb + 4, v, "minecraft:white_concrete")
        # Над капсулой нижнего яруса — карниз до настила галереи.
        if yb == FLOOR_B2:
            for a in range(a0 - 1, a0 + 3):
                for d in range(0, 3):
                    u, v = _side_cell(side, a, d)
                    f.fill(u, yb + 6, v, u, FLOOR_MEZZ - 1, v, WALL)
        else:
            for a in range(a0 - 1, a0 + 3):
                for d in range(0, 3):
                    u, v = _side_cell(side, a, d)
                    f.fill(u, yb + 6, v, u, FLOOR_B1 - 1, v, WALL)
        u, v = _side_cell(side, a0, 0)
        f.light(u, yb + 4, v)

    def _pod_sign(self, side: str, a0: int, yb: int, lines: list[str]) -> None:
        u, v = _side_cell(side, a0, 2)
        self.f.wall_sign(u, yb + 1, v, SIDE_OUT[side], lines, color="light_blue", glowing=True, wood=SIGN)

    def _pod_suit(self, side: str, a0: int, yb: int, item: str, l1: str, l2: str) -> None:
        f = self.f
        out = SIDE_OUT[side]
        # Подставка — полублок по центру ниши, стойка стоит на нём.
        cu, cv = _side_cell(side, a0, 0)
        ou, ov = LOCAL_VEC[out]
        # Центр ниши: граница двух клеток вдоль стены, ближе к задней стенке.
        along = {"N": (1, 0), "S": (1, 0), "W": (0, 1), "E": (0, 1)}[side]
        pu = cu + 0.5 + along[0] * 0.5 + ou * 0.4
        pv = cv + 0.5 + along[1] * 0.5 + ov * 0.4
        slot = "head" if item == "mark_01_head" else "chest"
        self.stand(pu, yb + 1, pv, out, S.item(item), slot)
        self._pod_sign(side, a0, yb, [l1, l2])

    def _platform(self) -> None:
        """Площадка вокруг ядра на 52 и четыре мостика к галерее."""
        f = self.f
        core = ({(u, v) for u in range(17, 22) for v in range(21, 27)}
                | {(u, v) for u in range(22, 26) for v in range(23, 27)})
        pu0, pv0, pu1, pv1 = 14, 18, 28, 29
        for u in range(pu0, pu1 + 1):
            for v in range(pv0, pv1 + 1):
                if (u, v) not in core:
                    f.set(u, FLOOR_MEZZ, v, DECK)
        exits = set()
        bridges = [(20, 22, V0 + 7, pv0 - 1, "v"), (20, 22, pv1 + 1, V1 - 7, "v"),
                   (U0 + 7, pu0 - 1, 23, 25, "u"), (pu1 + 1, U1 - 7, 23, 25, "u")]
        for a, b, c, d, axis in bridges:
            if axis == "v":
                for v in range(c, d + 1):
                    for u in range(a, b + 1):
                        f.set(u, FLOOR_MEZZ, v, DECK)
                    f.set(a - 1, FLOOR_MEZZ + 1, v, RAIL)
                    f.set(b + 1, FLOOR_MEZZ + 1, v, RAIL)
                    f.set(a - 1, FLOOR_MEZZ, v, TRIM)
                    f.set(b + 1, FLOOR_MEZZ, v, TRIM)
                exits |= {(u, pv0) for u in range(a, b + 1)} | {(u, pv1) for u in range(a, b + 1)}
            else:
                for u in range(a, b + 1):
                    for v in range(c, d + 1):
                        f.set(u, FLOOR_MEZZ, v, DECK)
                    f.set(u, FLOOR_MEZZ + 1, c - 1, RAIL)
                    f.set(u, FLOOR_MEZZ + 1, d + 1, RAIL)
                    f.set(u, FLOOR_MEZZ, c - 1, TRIM)
                    f.set(u, FLOOR_MEZZ, d + 1, TRIM)
                exits |= {(pu0, v) for v in range(c, d + 1)} | {(pu1, v) for v in range(c, d + 1)}
        for u in range(pu0, pu1 + 1):
            for v in range(pv0, pv1 + 1):
                edge = u in (pu0, pu1) or v in (pv0, pv1)
                if edge and (u, v) not in exits and (u, v) not in core:
                    f.set(u, FLOOR_MEZZ + 1, v, RAIL)
                    f.set(u, FLOOR_MEZZ, v, TRIM)
        # Колонны под углами площадки: тёмные, со световыми вставками.
        for u, v in ((pu0, pv0), (pu1, pv0), (pu0, pv1), (pu1, pv1)):
            f.fill(u, FLOOR_B2 + 1, v, u, FLOOR_MEZZ - 1, v, FRAME)
            for y in (FLOOR_B2 + 2, FLOOR_B2 + 5):
                f.set(u, y, v, GLOW)
        # Свет под площадкой и мостиками.
        for u in range(pu0 + 1, pu1, 3):
            for v in range(pv0 + 1, pv1, 3):
                if (u, v) not in core:
                    f.light(u, FLOOR_MEZZ - 1, v)

    def _floor_lights(self) -> None:
        """Световая дорожка вдоль капсул и разметка зала, как в фильме."""
        f = self.f
        for side in ("N", "S", "W", "E"):
            lo, hi = (U0 + 3, U1 - 3) if side in "NS" else (V0 + 7, V1 - 7)
            for a in range(lo, hi + 1):
                u, v = _side_cell(side, a, 3)
                f.set(u, FLOOR_B2, v, GLOW)
        # Светящаяся рамка по полу вокруг центра зала и линии к ядру.
        for u in range(U0 + 8, U1 - 7):
            for v in (V0 + 8, V1 - 8):
                f.set(u, FLOOR_B2, v, GLOW)
        for v in range(V0 + 8, V1 - 7):
            for u in (U0 + 8, U1 - 8):
                f.set(u, FLOOR_B2, v, GLOW)
        for v in range(V0 + 8, 21):
            f.set(21, FLOOR_B2, v, GLOW)

    def _hulkbuster(self) -> None:
        """Круглая сцена под южным мостиком: Халкбастер лицом к ядру."""
        f = self.f
        cu, cv = 21, 31
        for u in range(cu - 3, cu + 4):
            for v in range(cv - 3, cv + 4):
                d = ((u - cu) ** 2 + (v - cv) ** 2) ** 0.5
                if d <= 3.2:
                    # Невысокий подиум-полублок, по краю — светящееся кольцо в полу.
                    f.set(u, FLOOR_B2 + 1, v, B.slab("smooth_quartz"))
                    if d > 2.3:
                        f.set(u, FLOOR_B2, v, GLOW)
                elif d <= 4.2:
                    f.set(u, FLOOR_B2, v, FRAME)
        self.stand(cu + 0.5, FLOOR_B2 + 1.5, cv + 0.5, "front", S.item(S.HULKBUSTER))
        for u, v in ((cu - 3, cv), (cu + 3, cv), (cu, cv + 3)):
            f.set(u, FLOOR_B2 + 2, v, "minecraft:end_rod[facing=up]")
        f.set(cu, FLOOR_B2 + 1, cv - 4, FRAME)
        f.wall_sign(cu, FLOOR_B2 + 1, cv - 4, "front", ["MARK 44", "HULKBUSTER", "VERONICA"],
                    color="light_blue", glowing=True, wood=SIGN)

    # --- мастерская −1 ------------------------------------------------------------

    def workshop(self) -> None:
        f = self.f
        y = FLOOR_B1 + 1
        # Мастерская светлая, как у Тони: белые стены, светлый потолок,
        # белая сетка в сером полу.
        for yy in range(y, CEIL_B1):
            f.fill(U0 - 1, yy, V0 - 1, U1 + 1, yy, V0 - 1, "minecraft:white_concrete")
            f.fill(U0 - 1, yy, V1 + 1, U1 + 1, yy, V1 + 1, "minecraft:white_concrete")
            f.fill(U0 - 1, yy, V0 - 1, U0 - 1, yy, V1 + 1, "minecraft:white_concrete")
            f.fill(U1 + 1, yy, V0 - 1, U1 + 1, yy, V1 + 1, "minecraft:white_concrete")
        # Ядро не трогаем: в его полу и потолке — проёмы лестницы.
        for u in range(U0, U1 + 1):
            for v in range(V0, V1 + 1):
                if _in_core(u, v):
                    continue
                f.set(u, CEIL_B1, v, "minecraft:white_concrete")
                if u % 4 == U0 % 4 or v % 4 == V0 % 4:
                    f.set(u, FLOOR_B1, v, "minecraft:white_concrete")
        # Ствол ядра в мастерской — тоже под белый камень.
        f.fill(17, y, 21, 21, CEIL_B1 - 1, 21, TRIM)
        f.fill(18, y, 21, 20, y + 2, 21, B.AIR)
        # Потолок: световые полосы.
        for u in range(U0 + 2, U1 - 1, 6):
            for v in range(V0 + 1, V1):
                if not _in_core(u, v):
                    f.set(u, CEIL_B1, v, GLOW)
        # Станки Satsu вдоль северной стены.
        machines = ["gantry_station", "stark_station", "armored_construct", "recipe_table",
                    "reactor_ark_recharge_on", "gantry_station", "stark_station", "reactor_ark_recharge_on"]
        for i, block in enumerate(machines):
            f.set(5 + i * 2, y, V0, f"{S.SATSU}:{block}")
        f.wall_sign(4, y, V0 - 1, "back", ["МАСТЕРСКАЯ", "STARK", "INDUSTRIES"], color="light_blue",
                    glowing=True, wood=SIGN)
        # Три 3D-принтера в стеклянном боксе и монитор «Джарвиса» над ними.
        f.fill(4, FLOOR_B1, 8, 13, FLOOR_B1, 12, "minecraft:white_concrete")
        for u in (5, 8, 11):
            f.block_entity(u, y, 10, f"citylife:printer_3d[facing={f.dir('back')}]", "citylife:printer_3d", {})
        f.fill(4, y, 12, 13, y + 2, 12, B.GLASS)
        f.fill(7, y, 12, 9, y + 1, 12, B.AIR)
        f.wall_sign(9, y + 2, 12, "back", ["3D-ПРИНТЕРЫ", "печатают", "что угодно"],
                    color="light_blue", glowing=True, wood=SIGN)
        self.screen(5, y + 2, 8, "back", "jarvis", 7, 2)
        f.fill(4, y + 2, 7, 13, y + 4, 7, "minecraft:white_concrete")
        # Верстаки у западной стены.
        for v in range(15, 19):
            F.put(f, U0, y, v, "mcwfurnitures:dark_oak_counter", "right")
        for v, block in ((15, "minecraft:anvil[facing=north]"), (16, "minecraft:smithing_table"),
                         (17, "minecraft:grindstone[face=floor,facing=north]"), (18, "minecraft:lantern[hanging=false,waterlogged=false]")):
            f.set(U0, y + 1, v, block)
        # Голостол на юге: стеклянная столешница и два экрана над ней.
        for u in range(18, 25):
            for v in range(31, 34):
                f.set(u, y, v, "minecraft:polished_blackstone")
                f.set(u, y + 1, v, "minecraft:light_blue_stained_glass")
        for u in (18, 24):
            for v in (31, 33):
                f.set(u, y + 2, v, "minecraft:end_rod[facing=up]")
        # Экраны спина к спине: на север — реактор, на юг — броня.
        self.screen(23, y + 2, 31, "front", "reactor", 5, 2)
        self.screen(19, y + 2, 33, "back", "armor", 5, 2)
        # Дубина — робот-манипулятор у верстака.
        du, dv = 9, 33
        f.set(du, y, dv, "minecraft:iron_block")
        f.set(du, y + 1, dv, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
        f.set(du, y + 2, dv, "minecraft:iron_block")
        f.set(du + 1, y + 2, dv, B.CHAIN.replace("axis=y", "axis=x"))
        f.set(du + 2, y + 2, dv, "minecraft:end_rod[facing=east]")
        f.wall_sign(du, y, dv, "front", ["DUM-E"], color="white", wood=SIGN)
        for v in range(30, 37):
            F.put(f, U0, y, v, "mcwfurnitures:dark_oak_counter", "right")
            f.set(U0, y + 1, v, self.rng.choice(("minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,"
                                                  "has_bottle_2=false]", "minecraft:lantern[hanging=false,"
                                                  "waterlogged=false]", "minecraft:flower_pot",
                                                  "minecraft:end_rod[facing=up]")))
        # Склад на востоке: стальные блоки, бочки и зарядные станции реактора.
        for v in range(28, 38):
            for u in (37, 38):
                f.set(u, y, v, self.rng.choice((f"{S.SATSU}:steel_block", "minecraft:barrel[facing=up,open=false]",
                                                f"{S.SATSU}:steel_gold_alloy_block")))
            f.set(U1, y, v, f"{S.SATSU}:reactor_ark_recharge_on" if v % 3 == 0 else f"{S.SATSU}:steel_block")
        # Бар и диван Тони на западе.
        F.sofa(f, 5, y, 22, 4, "back", "right", "black")
        F.put(f, 8, y, 23, "another_furniture:dark_oak_table", "front")
        for v in range(21, 27):
            F.put(f, 11, y, v, "mcwfurnitures:dark_oak_counter", "left")
        for v in (22, 24):
            F.stool(f, 12, y, v)
        f.set(11, y + 1, 21, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
        self.security_room()
        # Камеры мастерской.
        self.camera(10, CEIL_B1 - 1, 14, "МАСТЕРСКАЯ")
        self.camera(21, CEIL_B1 - 1, 35, "ГОЛОСТОЛ")
        self.camera(34, CEIL_B1 - 1, 28, "СКЛАД")
        f.light_grid(U0, V0, U1, V1, CEIL_B1 - 1, 4)

    def security_room(self) -> None:
        """Пост охраны за стеклом в северо-восточном углу: пульт и стена мониторов."""
        f = self.f
        y = FLOOR_B1 + 1
        u0, v1 = 28, 14
        f.fill(u0, y, V0, u0, CEIL_B1 - 1, v1, B.GLASS)
        f.fill(u0, y, v1, U1, CEIL_B1 - 1, v1, B.GLASS)
        f.door(u0, y, 9, "left", "warped")
        f.fill(u0 + 1, FLOOR_B1, V0, U1, FLOOR_B1, v1 - 1, "minecraft:black_concrete")
        # Стена мониторов на северной стене.
        self.screen(29, y + 1, V0, "back", "security", 3, 2)
        self.screen(33, y + 1, V0, "back", "radar", 4, 3)
        self.screen(38, y + 1, V0, "back", "jarvis", 3, 2)
        self.screen(U1, y + 1, 6, "left", "stocks", 4, 2)
        f.block_entity(34, y, 8, f"citylife:security_console[facing={f.dir('back')}]", "citylife:security_console",
                       {})   # данные пульта дописывает finish()
        self.console = (34, y, 8)
        F.chair(f, 34, y, 9, "front", "mcwfurnitures:dark_oak_modern_chair")
        f.wall_sign(u0, y + 2, 11, "left", ["ОХРАНА", "J.A.R.V.I.S."], color="light_blue", glowing=True,
                    wood=SIGN)
        self.camera(33, CEIL_B1 - 1, 12, "ПОСТ ОХРАНЫ")

    # --- вестибюль ------------------------------------------------------------------

    def lobby(self, front_v: int) -> None:
        """Над стойкой ресепшена — два парящих голо-экрана; камера у входа."""
        y = 69
        # Экран, смотрящий на север, растёт от точки крепления на запад.
        self.screen(15, y + 1, 20, "front", "stocks", 4, 2)
        self.screen(30, y + 1, 20, "front", "jarvis", 4, 2)
        self.camera(21, 71, front_v + 2, "ВЕСТИБЮЛЬ")

    # --- пульт ---------------------------------------------------------------------------

    def finish(self) -> None:
        """Записать в пульт охраны зоны подвала, камеры и стойки с костюмами."""
        f = self.f
        ax, az = f.world(U0 - 1, V0 - 1)
        bx, bz = f.world(U1 + 1, V1 + 1)
        zones = [{"x0": min(ax, bx), "y0": FLOOR_B2, "z0": min(az, bz),
                  "x1": max(ax, bx), "y1": CEIL_B1 + 1, "z1": max(az, bz)}]
        u, y, v = self.console
        f.block_entity(u, y, v, f"citylife:security_console[facing={f.dir('back')}]", "citylife:security_console", {
            "Zones": nbt.List(nbt.TAG_COMPOUND, zones),
            "Cams": nbt.List(nbt.TAG_COMPOUND, self.cams),
            "Suits": nbt.List(nbt.TAG_COMPOUND, self.suits),
        })
