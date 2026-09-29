"""
Локальная система координат участка и строительные детали.

Здание рисуется один раз в координатах «вдоль улицы» (u) и «вглубь от улицы»
(v), а Frame поворачивает их на нужную сторону света. Раньше у каждого
строителя было по четыре ветки на north/south/west/east, и ошибки жили
как раз в них: табличку ставили не с той стороны стены, лестницу — лицом
в стену, калитку — в угол дома. Теперь сторона считается в одном месте.

Направления внутри Frame называются по-человечески:
    front — к улице, back — вглубь участка, left/right — вдоль улицы.
"""
from __future__ import annotations

from . import blocks as B
from .canvas import RegionCanvas

WORLD_VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
VEC_WORLD = {vec: name for name, vec in WORLD_VEC.items()}
LOCAL_VEC = {"front": (0, -1), "back": (0, 1), "left": (-1, 0), "right": (1, 0)}
OPPOSITE = {"front": "back", "back": "front", "left": "right", "right": "left"}


class Frame:
    """Прямоугольник, повёрнутый фасадом к улице."""

    def __init__(self, canvas: RegionCanvas, x0: int, z0: int, x1: int, z1: int,
                 facing: str) -> None:
        self.canvas = canvas
        self.facing = facing
        if facing == "north":
            self.origin, self.du, self.dv = (x0, z0), (1, 0), (0, 1)
            self.W, self.D = x1 - x0 + 1, z1 - z0 + 1
        elif facing == "south":
            self.origin, self.du, self.dv = (x1, z1), (-1, 0), (0, -1)
            self.W, self.D = x1 - x0 + 1, z1 - z0 + 1
        elif facing == "west":
            self.origin, self.du, self.dv = (x0, z1), (0, -1), (1, 0)
            self.W, self.D = z1 - z0 + 1, x1 - x0 + 1
        else:
            self.origin, self.du, self.dv = (x1, z0), (0, 1), (-1, 0)
            self.W, self.D = z1 - z0 + 1, x1 - x0 + 1

    # --- координаты ---------------------------------------------------------

    def world(self, u: int, v: int) -> tuple[int, int]:
        ox, oz = self.origin
        return (ox + self.du[0] * u + self.dv[0] * v,
                oz + self.du[1] * u + self.dv[1] * v)

    def dir(self, local: str) -> str:
        """Сторона света для локального направления."""
        lu, lv = LOCAL_VEC[local]
        wx = self.du[0] * lu + self.dv[0] * lv
        wz = self.du[1] * lu + self.dv[1] * lv
        return VEC_WORLD[(wx, wz)]

    def sub(self, u0: int, v0: int, u1: int, v1: int) -> "Frame":
        """Вложенный прямоугольник с тем же поворотом."""
        ax, az = self.world(u0, v0)
        bx, bz = self.world(u1, v1)
        return Frame(self.canvas, min(ax, bx), min(az, bz), max(ax, bx), max(az, bz),
                     self.facing)

    # --- рисование ------------------------------------------------------------

    def set(self, u: int, y: int, v: int, state: str) -> None:
        x, z = self.world(u, v)
        self.canvas.set(x, y, z, state)

    def fill(self, u0: int, y0: int, v0: int, u1: int, y1: int, v1: int, state: str) -> None:
        ax, az = self.world(u0, v0)
        bx, bz = self.world(u1, v1)
        self.canvas.fill(min(ax, bx), min(y0, y1), min(az, bz),
                         max(ax, bx), max(y0, y1), max(az, bz), state)

    def outline(self, u0: int, y0: int, v0: int, u1: int, y1: int, v1: int,
                state: str) -> None:
        self.fill(u0, y0, v0, u1, y1, v0, state)
        self.fill(u0, y0, v1, u1, y1, v1, state)
        self.fill(u0, y0, v0, u0, y1, v1, state)
        self.fill(u1, y0, v0, u1, y1, v1, state)

    def get_name(self, u: int, y: int, v: int) -> str:
        x, z = self.world(u, v)
        block_id = self.canvas.get(x, y, z)
        return self.canvas.registry.state_nbt(block_id)["Name"]

    def container(self, u: int, y: int, v: int, state: str, entity_id: str) -> None:
        x, z = self.world(u, v)
        self.canvas.container(x, y, z, state, entity_id)

    # --- детали с ориентацией ------------------------------------------------------

    def stairs(self, material: str, rise: str, half: str = "bottom") -> str:
        """Ступень, которая поднимается в сторону rise (там у неё высокая часть)."""
        return B.stairs(material, facing=self.dir(rise), half=half)

    def facing_state(self, template: str, local: str) -> str:
        """Блок с facing, заданным локально: '...[facing={f}]'."""
        return template.format(f=self.dir(local))

    def wall_sign(self, u: int, y: int, v: int, out: str, lines: list[str],
                  color: str = "black", glowing: bool = False) -> None:
        """
        Табличка на стене: (u, y, v) — сам блок стены, табличка встаёт
        в клетку перед ним со стороны out и смотрит туда же.

        Раньше табличку ставили прямо в линию стены: она заменяла блок,
        держалась ни на чём и выглядела вдавленной.
        """
        du, dv = LOCAL_VEC[out]
        x, z = self.world(u + du, v + dv)
        self.canvas.sign(x, y, z, B.SIGN_WALL.format(f=self.dir(out)), lines,
                         color=color, glowing=glowing)

    def door(self, u: int, y: int, v: int, out: str, material: str = "oak",
             hinge: str = "left") -> None:
        """Дверь в проёме стены; out — сторона, куда выходят."""
        facing = self.dir(OPPOSITE[out])
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(u, y + dy, v,
                     f"minecraft:{material}_door[facing={facing},half={half},"
                     f"hinge={hinge},open=false,powered=false]")

    def staircase(self, u: int, v: int, floor_y: int, run: str, material: str,
                  rail: str | None = None, height: int = 4) -> None:
        """
        Лестница на следующий этаж: height ступеней от пола floor_y вдоль run.

        Над ступенями вырезается проём в перекрытии, чтобы не биться головой,
        а сбоку от проёма на верхнем этаже ставится перила (rail — сторона).
        """
        du, dv = LOCAL_VEC[run]
        step = self.stairs(material, run)
        for i in range(height):
            su, sv = u + du * i, v + dv * i
            self.set(su, floor_y + 1 + i, sv, step)
            self.fill(su, floor_y + 2 + i, sv, su, floor_y + 3 + i, sv, B.AIR)
        # Проём в перекрытии над средними ступенями.
        for i in range(1, height - 1):
            self.set(u + du * i, floor_y + height, v + dv * i, B.AIR)
        if rail:
            ru, rv = LOCAL_VEC[rail]
            for i in range(0, height - 1):
                self.set(u + du * i + ru, floor_y + height + 1, v + dv * i + rv,
                         B.fence("oak"))
            # И торец проёма со стороны начала лестницы.
            self.set(u, floor_y + height + 1, v, B.fence("oak"))

    def light(self, u: int, y: int, v: int) -> None:
        """Невидимый источник света (помещение светлое, потолок чистый)."""
        self.set(u, y, v, B.LIGHT)

    def light_grid(self, u0: int, v0: int, u1: int, v1: int, y: int, step: int = 4) -> None:
        """
        Сетка невидимых ламп под потолком: только в пустых клетках, чтобы
        не пробить перегородку или шкаф. Читаем полотно лишь в своих
        клетках региона — на стыке регионов результат одинаковый.
        """
        air = self.canvas.registry.id_of(B.AIR)
        for u in range(u0 + 1, u1, step):
            for v in range(v0 + 1, v1, step):
                x, z = self.world(u, v)
                if self.canvas.get(x, y, z) in (air, 0):
                    self.canvas.set(x, y, z, B.LIGHT)


def vec(local: str) -> tuple[int, int]:
    return LOCAL_VEC[local]
