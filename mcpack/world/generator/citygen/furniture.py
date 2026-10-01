"""
Мебель и обстановка из модов интерьера сборки.

Модов четыре, у каждого своя привычка понимать facing: у стула Macaw's
facing — куда смотрит сидящий, у его же кухонной тумбы — откуда на неё
смотрит хозяин, а у шкафа — вообще боковая сторона. Поэтому здесь для
каждого блока записано, куда у него лицевая сторона относительно facing
(посчитано по моделям из jar-файлов модов), а снаружи мебель ставится
по-человечески: «стул лицом к столу», «тумба фасадом в кухню».

Все функции работают в координатах Frame: u — вдоль улицы, v — вглубь
участка, стороны front/back/left/right. Если мод интерьера не установлен,
игра заменит его блоки воздухом — стены и полы остаются ванильными.
"""
from __future__ import annotations

import random

from . import blocks as B
from . import nbt
from .frame import LOCAL_VEC, OPPOSITE, Frame

ORDER = ("north", "east", "south", "west")

# На сколько четвертей оборота по часовой лицевая сторона блока отстоит
# от его facing. 0 — facing и есть лицо, 2 — лицо смотрит против facing,
# 1 — лицо повёрнуто по часовой (так у шкафов и диванов Macaw's).
_REL_SUFFIX = (
    ("mcwfurnitures:", "_wardrobe", 1), ("mcwfurnitures:", "_bookshelf", 1),
    ("mcwfurnitures:", "_couch", 1),
    ("mcwfurnitures:", "_counter", 2), ("mcwfurnitures:", "_sink", 2),
    ("mcwfurnitures:", "_cabinet", 2), ("mcwfurnitures:", "_drawer", 1),
    ("mcwfurnitures:", "_chair", 0),
)


def _rel(block: str) -> int:
    for prefix, suffix, rel in _REL_SUFFIX:
        if block.startswith(prefix) and block.endswith(suffix):
            return rel
    return 0


def facing_for(block: str, front: str) -> str:
    """facing блока, чтобы его лицо смотрело на сторону света front."""
    return ORDER[(ORDER.index(front) - _rel(block)) % 4]


def state(frame: Frame, block: str, front: str | None = None, **props) -> str:
    """Строка состояния: блок, лицом в локальную сторону front, и свойства."""
    parts = []
    if front is not None:
        parts.append(f"facing={facing_for(block, frame.dir(front))}")
    parts += [f"{k}={'true' if v is True else 'false' if v is False else v}"
              for k, v in props.items()]
    return block + (f"[{','.join(parts)}]" if parts else "")


def put(frame: Frame, u: int, y: int, v: int, block: str, front: str | None = None,
        **props) -> None:
    frame.set(u, y, v, state(frame, block, front, **props))


def items_nbt(items: list[tuple]) -> nbt.List:
    """
    Список предметов для block entity: по слоту на предмет. Предмет —
    (id, количество) или (id, количество, tag) для зелий и крашеной одежды.
    """
    out = []
    for slot, item in enumerate(items):
        if not item or not item[0]:
            continue
        entry = {"Slot": nbt.Byte(slot), "id": item[0], "Count": nbt.Byte(item[1])}
        if len(item) > 2 and item[2]:
            entry["tag"] = item[2]
        out.append(entry)
    return nbt.List(nbt.TAG_COMPOUND, out)


def potion(kind: str) -> tuple:
    return ("minecraft:potion", 1, {"Potion": f"minecraft:{kind}"})


def dyed(item: str, color: int) -> tuple:
    return (f"minecraft:leather_{item}", 1, {"display": {"color": nbt.Int(color)}})


def display_shelf(frame: Frame, u: int, y: int, v: int, face: str,
                  items: list[tuple]) -> None:
    """Полка Another Furniture лицом в face; на ней до четырёх предметов."""
    holder(frame, u, y, v, "another_furniture:oak_shelf", "another_furniture:shelf",
           items[:4], face, horizontal="single")


def holder(frame: Frame, u: int, y: int, v: int, block: str, entity: str,
           items: list[tuple[str, int]], front: str | None = None, extra: dict | None = None,
           **props) -> None:
    """Блок, на котором лежат предметы: полка, банка, ящик для цветов, тумба."""
    data = {"Items": items_nbt(items)}
    if extra:
        data.update(extra)
    frame.block_entity(u, y, v, state(frame, block, front, **props), entity, data)


# ---------------------------------------------------------------------------
#  Сиденья и столы
# ---------------------------------------------------------------------------

CHAIRS = ("another_furniture:oak_chair", "another_furniture:spruce_chair",
          "another_furniture:dark_oak_chair", "another_furniture:birch_chair")
OFFICE_CHAIRS = ("mcwfurnitures:oak_modern_chair", "mcwfurnitures:dark_oak_modern_chair",
                 "mcwfurnitures:spruce_modern_chair")
SOFA_COLORS = ("gray", "light_gray", "white", "black", "blue", "brown", "cyan", "green")


def chair(frame: Frame, u: int, y: int, v: int, look: str, kind: str | None = None) -> None:
    """Стул: сидящий смотрит в сторону look."""
    block = kind or CHAIRS[0]
    if block.startswith("another_furniture:"):
        put(frame, u, y, v, block, look, tucked=False, variant=1)
    else:
        put(frame, u, y, v, block, look)


def stool(frame: Frame, u: int, y: int, v: int, tall: bool = True) -> None:
    put(frame, u, y, v, "another_furniture:white_tall_stool" if tall
        else "another_furniture:white_stool", **({} if tall else {"low": False}))


def sofa(frame: Frame, u: int, y: int, v: int, length: int, along: str, look: str,
         color: str = "gray") -> None:
    """
    Диван из length секций от (u, v) в сторону along; сидящие смотрят в look.

    У Another Furniture «левая» секция — та, что слева от того, кто смотрит
    на диван спереди, то есть справа от сидящего.
    """
    du, dv = LOCAL_VEC[along]
    block = f"another_furniture:{color}_sofa"
    # Правая рука сидящего: поворот look по часовой.
    right = {"front": "left", "left": "back", "back": "right", "right": "front"}[look]
    for i in range(length):
        if length == 1:
            kind = "single"
        elif i == 0:
            kind = "left" if along == OPPOSITE[right] else "right"
        elif i == length - 1:
            kind = "right" if along == OPPOSITE[right] else "left"
        else:
            kind = "middle"
        put(frame, u + du * i, y, v + dv * i, block, look, type=kind)


def table(frame: Frame, u0: int, y: int, v0: int, u1: int, v1: int,
          block: str = "mcwfurnitures:oak_table") -> None:
    """Стол на прямоугольник клеток: соседние клетки срастаются в одну столешницу."""
    a, b = min(u0, u1), max(u0, u1)
    c, d = min(v0, v1), max(v0, v1)
    for u in range(a, b + 1):
        for v in range(c, d + 1):
            links = {}
            for local, (lu, lv) in LOCAL_VEC.items():
                links[frame.dir(local)] = a <= u + lu <= b and c <= v + lv <= d
            put(frame, u, y, v, block, **{k: links[k] for k in ("north", "east", "south", "west")})


def dining(frame: Frame, u0: int, y: int, v0: int, u1: int, v1: int, rng: random.Random,
           block: str = "mcwfurnitures:oak_table", chairs: str | None = None) -> None:
    """Обеденный стол со стульями по длинным сторонам (и торцам, если влезают)."""
    table(frame, u0, y, v0, u1, v1, block)
    kind = chairs or rng.choice(CHAIRS)
    if u1 - u0 >= v1 - v0:
        for u in range(u0, u1 + 1):
            chair(frame, u, y, v0 - 1, "back", kind)
            chair(frame, u, y, v1 + 1, "front", kind)
    else:
        for v in range(v0, v1 + 1):
            chair(frame, u0 - 1, y, v, "right", kind)
            chair(frame, u1 + 1, y, v, "left", kind)


def coffee_table(frame: Frame, u: int, y: int, v: int) -> None:
    put(frame, u, y, v, "mcwfurnitures:oak_coffee_table", connection="with_leg")


# ---------------------------------------------------------------------------
#  Кухня
# ---------------------------------------------------------------------------

def kitchen(frame: Frame, u0: int, y: int, v: int, u1: int, face: str,
            rng: random.Random, upper: bool = True, wood: str = "oak") -> None:
    """
    Кухонный гарнитур вдоль стены от u0 до u1 в ряду v, фасадом в face:
    тумбы, мойка, плита с кастрюлей, духовка, вытяжка и навесные шкафы.
    """
    cells = list(range(min(u0, u1), max(u0, u1) + 1))
    if not cells:
        return
    sink = cells[len(cells) // 3] if len(cells) >= 3 else None
    stove = cells[-2] if len(cells) >= 4 else None
    oven = cells[-1] if len(cells) >= 5 else None
    for u in cells:
        if u == sink:
            put(frame, u, y, v, f"mcwfurnitures:{wood}_kitchen_sink", face, water=False)
        elif u == stove:
            frame.block_entity(u, y, v, state(frame, "farmersdelight:stove", face, lit=False),
                               "farmersdelight:stove", {})
            put(frame, u, y + 1, v, "farmersdelight:cooking_pot", face, support="tray")
            put(frame, u, y + 2, v, "handcrafted:kitchen_hood", face)
        elif u == oven:
            put(frame, u, y, v, "handcrafted:oven", face, lit=False)
        else:
            block = rng.choice((f"mcwfurnitures:{wood}_counter",
                                f"mcwfurnitures:{wood}_drawer_counter",
                                f"mcwfurnitures:{wood}_cupboard_counter"))
            put(frame, u, y, v, block, face)
            top = rng.random()
            if top < 0.18:
                put(frame, u, y + 1, v, "farmersdelight:cutting_board", face)
            elif top < 0.3:
                put(frame, u, y + 1, v, "handcrafted:white_crockery_combo", face)
            elif top < 0.38:
                holder(frame, u, y + 1, v, "supplementaries:jar", "supplementaries:jar",
                       [(rng.choice(("minecraft:cookie", "minecraft:sweet_berries",
                                     "minecraft:glow_berries")), 12)])
        if upper and u != stove:
            put(frame, u, y + 2, v, rng.choice((f"mcwfurnitures:{wood}_kitchen_cabinet",
                                                f"mcwfurnitures:{wood}_glass_kitchen_cabinet")),
                face)


def _xz(frame: Frame, u: int, y: int, v: int) -> tuple[int, int, int]:
    x, z = frame.world(u, v)
    return x, y, z


def fridge(frame: Frame, u: int, y: int, v: int, face: str) -> None:
    """Холодильник: высокий белый шкаф (два блока кварца с дверцей-люком)."""
    frame.set(u, y, v, B.QUARTZ_SMOOTH)
    frame.set(u, y + 1, v, B.QUARTZ_SMOOTH)
    du, dv = LOCAL_VEC[face]
    for dy in (0, 1):
        frame.set(u + du, y + dy, v + dv,
                  B.trapdoor("iron", facing=frame.dir(face), half="bottom", open_=True))


# ---------------------------------------------------------------------------
#  Спальня, кабинет, гостиная
# ---------------------------------------------------------------------------

def bed(frame: Frame, u: int, y: int, v: int, head: str, color: str = "white") -> None:
    """Кровать: ноги в (u, v), изголовье в сторону head (у стены)."""
    du, dv = LOCAL_VEC[head]
    facing = frame.dir(head)
    # Кровать рисуется block entity: без записи о нём игра показывает пустое
    # место, хотя на кровать можно лечь. Поэтому пишем его для обеих половин.
    for part, (pu, pv) in (("foot", (u, v)), ("head", (u + du, v + dv))):
        frame.block_entity(pu, y, pv,
                           f"minecraft:{color}_bed[facing={facing},occupied=false,part={part}]",
                           "minecraft:bed", {})


def nightstand(frame: Frame, u: int, y: int, v: int, face: str, lamp: bool = True) -> None:
    put(frame, u, y, v, "handcrafted:oak_nightstand", face, color="none")
    if lamp:
        table_lamp(frame, u, y + 1, v)


def table_lamp(frame: Frame, u: int, y: int, v: int) -> None:
    """Настольная лампа с абажуром: светит по-настоящему."""
    put(frame, u, y, v, "another_furniture:white_lamp", facing="up", base=True, lit=True)


def wardrobe(frame: Frame, u: int, y: int, v: int, face: str, wood: str = "oak",
             modern: bool = False) -> None:
    """Двухблочный шкаф лицом в face."""
    block = f"mcwfurnitures:{wood}_{'modern_' if modern else ''}wardrobe"
    put(frame, u, y, v, block, face, connection="bottom", hinge="left")
    put(frame, u, y + 1, v, block, face, connection="top", hinge="left")


def bookshelf(frame: Frame, u: int, y: int, v: int, face: str, wood: str = "oak") -> None:
    block = f"mcwfurnitures:{wood}_bookshelf"
    put(frame, u, y, v, block, face, connection="bottom")
    put(frame, u, y + 1, v, block, face, connection="top")


def desk(frame: Frame, u: int, y: int, v: int, sit: str, rng: random.Random,
         computer: bool = True, chair_kind: str | None = None) -> None:
    """
    Рабочее место: стол в (u, v), кресло со стороны sit, на столе монитор,
    клавиатура и мышь (стол на две клетки) или папки и лампа.
    """
    du, dv = LOCAL_VEC[sit]
    look = OPPOSITE[sit]
    put(frame, u, y, v, "handcrafted:oak_desk", sit, color=rng.choice(("none", "gray", "black")))
    if computer:
        put(frame, u, y + 1, v, "citylife:monitor", sit)
    else:
        top = rng.random()
        if top < 0.4:
            put(frame, u, y + 1, v, "supplementaries:book_pile")
        elif top < 0.7:
            table_lamp(frame, u, y + 1, v)
    chair(frame, u + du, y, v + dv, look, chair_kind or rng.choice(OFFICE_CHAIRS))


def plant(frame: Frame, u: int, y: int, v: int, rng: random.Random) -> None:
    """Растение в горшке: в кадке или в напольном горшке Handcrafted."""
    pick = rng.random()
    if pick < 0.5:
        frame.set(u, y, v, rng.choice(("minecraft:potted_fern", "minecraft:potted_azalea_bush",
                                       "minecraft:potted_flowering_azalea_bush",
                                       "minecraft:potted_bamboo",
                                       "minecraft:potted_oak_sapling")))
    else:
        frame.set(u, y, v, "minecraft:flowering_azalea" if pick < 0.75 else "minecraft:azalea")


def rug(frame: Frame, u0: int, y: int, v0: int, u1: int, v1: int, color: str) -> None:
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            frame.set(u, y, v, B.carpet(color))


def tv(frame: Frame, u: int, y: int, v: int, face: str) -> None:
    """Телевизор на тумбе: экран — чёрная стеклянная панель над комодом."""
    put(frame, u, y, v, "mcwfurnitures:oak_drawer", face)
    frame.set(u, y + 1, v, "minecraft:black_stained_glass_pane"
              "[east=false,north=false,south=false,waterlogged=false,west=false]")


def wall_shelf(frame: Frame, u: int, y: int, v: int, out: str,
               item: str | None) -> None:
    """Настенная полочка Supplementaries с одним предметом; out — от стены в комнату."""
    holder(frame, u, y, v, "supplementaries:item_shelf", "supplementaries:item_shelf",
           [(item, 1)] if item else [], out)


def ceiling_lamp(frame: Frame, u: int, y: int, v: int) -> None:
    """
    Светильник заподлицо с потолком: светящаяся панель-полублок под
    перекрытием (y — клетка прямо под ним). Подвесные плафоны висели на
    уровне глаз и загораживали зал.
    """
    frame.set(u, y, v, "mcwlights:sea_lantern_slab[type=top]")


def flower_box(frame: Frame, u: int, y: int, v: int, out: str, rng: random.Random) -> None:
    """Ящик с цветами под окном: out — наружу от стены."""
    flowers = [rng.choice(("minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower",
                           "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
                           "minecraft:oxeye_daisy")) for _ in range(3)]
    holder(frame, u, y, v, "supplementaries:flower_box", "supplementaries:flower_box",
           [(f, 1) for f in flowers], out)


def awning(frame: Frame, u0: int, u1: int, y: int, v: int, out: str, color: str) -> None:
    """
    Полосатый навес над витриной: ряд маркиз Supplementaries от u0 до u1,
    крепится к стене в ряду v и выступает в сторону out.
    """
    du, dv = LOCAL_VEC[out]
    for u in range(min(u0, u1), max(u0, u1) + 1):
        frame.set(u + du, y, v + dv,
                  f"supplementaries:awning_{color}[bottom=true,facing={frame.dir(out)},"
                  f"slanted=true]")
