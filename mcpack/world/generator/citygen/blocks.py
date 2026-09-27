"""
Палитра блоков города.

Вся геометрия города строится ТОЛЬКО на ванильных блоках: если игрок
отключит любой декоративный мод, мир останется целым. Блоки модов
добавляет отдельный проход (props.py), который можно выключить.
"""
from __future__ import annotations

AIR = "minecraft:air"
WATER = "minecraft:water"
STONE = "minecraft:stone"
DEEPSLATE = "minecraft:deepslate"
BEDROCK = "minecraft:bedrock"
DIRT = "minecraft:dirt"
GRASS = "minecraft:grass_block[snowy=false]"
SAND = "minecraft:sand"
SANDSTONE = "minecraft:sandstone"
GRAVEL = "minecraft:gravel"
CLAY = "minecraft:clay"
COARSE_DIRT = "minecraft:coarse_dirt"
PODZOL = "minecraft:podzol[snowy=false]"

# --- дороги и тротуары ------------------------------------------------------
ASPHALT = "minecraft:black_concrete"
ASPHALT_WORN = "minecraft:gray_concrete"
ROAD_LINE = "minecraft:yellow_concrete"
ROAD_LINE_WHITE = "minecraft:white_concrete"
CROSSWALK = "minecraft:white_concrete"
SIDEWALK = "minecraft:smooth_stone"
SIDEWALK_EDGE = "minecraft:stone_bricks"
CURB = "minecraft:polished_andesite"

# --- конструктив ------------------------------------------------------------
CONCRETE_GRAY = "minecraft:gray_concrete"
CONCRETE_LIGHT = "minecraft:light_gray_concrete"
CONCRETE_WHITE = "minecraft:white_concrete"
CONCRETE_BLACK = "minecraft:black_concrete"
CONCRETE_BLUE = "minecraft:blue_concrete"
CONCRETE_CYAN = "minecraft:cyan_concrete"
CONCRETE_RED = "minecraft:red_concrete"
CONCRETE_ORANGE = "minecraft:orange_concrete"
CONCRETE_YELLOW = "minecraft:yellow_concrete"
CONCRETE_GREEN = "minecraft:green_concrete"
CONCRETE_LIME = "minecraft:lime_concrete"
CONCRETE_BROWN = "minecraft:brown_concrete"
CONCRETE_PINK = "minecraft:pink_concrete"
CONCRETE_PURPLE = "minecraft:purple_concrete"
CONCRETE_MAGENTA = "minecraft:magenta_concrete"
TERRACOTTA_WHITE = "minecraft:white_terracotta"
TERRACOTTA_ORANGE = "minecraft:orange_terracotta"
TERRACOTTA_LIGHT = "minecraft:light_gray_terracotta"
BRICKS = "minecraft:bricks"
QUARTZ = "minecraft:quartz_block"
QUARTZ_SMOOTH = "minecraft:smooth_quartz"
IRON_BLOCK = "minecraft:iron_block"
GLASS = "minecraft:glass"
GLASS_TINTED = "minecraft:tinted_glass"
GLASS_PANE = "minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]"
GLASS_BLUE = "minecraft:light_blue_stained_glass"
GLASS_CYAN = "minecraft:cyan_stained_glass"
GLASS_GRAY = "minecraft:gray_stained_glass"
GLASS_BLACK = "minecraft:black_stained_glass"
GLASS_GREEN = "minecraft:green_stained_glass"
IRON_BARS = "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]"

# --- дерево -----------------------------------------------------------------
OAK_PLANKS = "minecraft:oak_planks"
SPRUCE_PLANKS = "minecraft:spruce_planks"
BIRCH_PLANKS = "minecraft:birch_planks"
DARK_OAK_PLANKS = "minecraft:dark_oak_planks"
OAK_LOG = "minecraft:oak_log[axis=y]"
SPRUCE_LOG = "minecraft:spruce_log[axis=y]"
STRIPPED_OAK = "minecraft:stripped_oak_log[axis=y]"
OAK_LEAVES = "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]"
BIRCH_LEAVES = "minecraft:birch_leaves[distance=7,persistent=true,waterlogged=false]"
JUNGLE_LEAVES = "minecraft:jungle_leaves[distance=7,persistent=true,waterlogged=false]"
JUNGLE_LOG = "minecraft:jungle_log[axis=y]"

# --- прочее -----------------------------------------------------------------
LAMP = "minecraft:sea_lantern"
GLOWSTONE = "minecraft:glowstone"
LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]"
LANTERN_HANGING = "minecraft:lantern[hanging=true,waterlogged=false]"
REDSTONE_LAMP_ON = "minecraft:redstone_lamp[lit=true]"
BOOKSHELF = "minecraft:bookshelf"
CRAFTING = "minecraft:crafting_table"
FURNACE = "minecraft:furnace[facing=north,lit=false]"
ANVIL = "minecraft:anvil[facing=north]"
CAULDRON = "minecraft:cauldron"
BARREL = "minecraft:barrel[facing=up,open=false]"
CHEST_N = "minecraft:chest[facing=north,type=single,waterlogged=false]"
BELL = "minecraft:bell[attachment=floor,facing=north,powered=false]"
FLOWER_POT = "minecraft:flower_pot"
COMPOSTER = "minecraft:composter[level=0]"
CAMPFIRE_OFF = "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]"
BREWING = "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]"
JUKEBOX = "minecraft:jukebox[has_record=false]"
NOTE_BLOCK = "minecraft:note_block[instrument=harp,note=0,powered=false]"
TARGET = "minecraft:target[power=0]"
LECTERN = "minecraft:lectern[facing=north,has_book=false,powered=false]"
BED_RED = "minecraft:red_bed[facing=north,occupied=false,part=foot]"
BED_RED_HEAD = "minecraft:red_bed[facing=north,occupied=false,part=head]"
RAIL_NS = "minecraft:rail[shape=north_south,waterlogged=false]"
RAIL_EW = "minecraft:rail[shape=east_west,waterlogged=false]"
RAIL = RAIL_NS
POWERED_RAIL_NS = "minecraft:powered_rail[powered=true,shape=north_south,waterlogged=false]"
POWERED_RAIL_EW = "minecraft:powered_rail[powered=true,shape=east_west,waterlogged=false]"
POWERED_RAIL = POWERED_RAIL_NS
REDSTONE_BLOCK = "minecraft:redstone_block"
LADDER_N = "minecraft:ladder[facing=north,waterlogged=false]"
SCAFFOLD = "minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]"
CHAIN = "minecraft:chain[axis=y,waterlogged=false]"

DOOR_TOP = "minecraft:oak_door[facing={f},half=upper,hinge=left,open=false,powered=false]"
DOOR_BOTTOM = "minecraft:oak_door[facing={f},half=lower,hinge=left,open=false,powered=false]"
IRON_DOOR_TOP = "minecraft:iron_door[facing={f},half=upper,hinge=left,open=false,powered=false]"
IRON_DOOR_BOTTOM = "minecraft:iron_door[facing={f},half=lower,hinge=left,open=false,powered=false]"

WALL_TORCH = "minecraft:wall_torch[facing={f}]"
SIGN_WALL = "minecraft:oak_wall_sign[facing={f},waterlogged=false]"
SIGN_STANDING = "minecraft:oak_sign[rotation={r},waterlogged=false]"


def slab(material: str, top: bool = False) -> str:
    return f"minecraft:{material}_slab[type={'top' if top else 'bottom'},waterlogged=false]"


def stairs(material: str, facing: str = "north", half: str = "bottom",
           shape: str = "straight") -> str:
    return (f"minecraft:{material}_stairs[facing={facing},half={half},"
            f"shape={shape},waterlogged=false]")


def wall(material: str) -> str:
    return (f"minecraft:{material}_wall[east=none,north=none,south=none,"
            f"up=true,waterlogged=false,west=none]")


def fence(material: str = "oak") -> str:
    return (f"minecraft:{material}_fence[east=false,north=false,"
            f"south=false,waterlogged=false,west=false]")


def carpet(color: str) -> str:
    return f"minecraft:{color}_carpet"


def concrete(color: str) -> str:
    return f"minecraft:{color}_concrete"


def glazed(color: str, facing: str = "north") -> str:
    return f"minecraft:{color}_glazed_terracotta[facing={facing}]"


def trapdoor(material: str = "oak", facing: str = "north",
             half: str = "bottom", open_: bool = False) -> str:
    return (f"minecraft:{material}_trapdoor[facing={facing},half={half},"
            f"open={'true' if open_ else 'false'},powered=false,waterlogged=false]")


# Материалы фасадов для случайной, но правдоподобной застройки.
FACADE_SETS = [
    # (стена, акцент, стекло, плита-карниз)
    (CONCRETE_LIGHT, CONCRETE_GRAY, GLASS_GRAY, "smooth_stone"),
    (TERRACOTTA_WHITE, TERRACOTTA_ORANGE, GLASS, "sandstone"),
    (CONCRETE_WHITE, CONCRETE_CYAN, GLASS_BLUE, "quartz"),
    (BRICKS, CONCRETE_RED, GLASS, "brick"),
    (CONCRETE_ORANGE, TERRACOTTA_ORANGE, GLASS, "smooth_sandstone"),
    (QUARTZ, CONCRETE_LIGHT, GLASS_CYAN, "quartz"),
    (TERRACOTTA_LIGHT, CONCRETE_BROWN, GLASS, "stone_brick"),
    (CONCRETE_YELLOW, CONCRETE_ORANGE, GLASS, "smooth_sandstone"),
]

HOUSE_SETS = [
    (TERRACOTTA_WHITE, OAK_PLANKS, "brick", CONCRETE_RED),
    (CONCRETE_WHITE, SPRUCE_PLANKS, "spruce", TERRACOTTA_ORANGE),
    (TERRACOTTA_ORANGE, BIRCH_PLANKS, "sandstone", CONCRETE_ORANGE),
    (CONCRETE_LIGHT, OAK_PLANKS, "oak", CONCRETE_GRAY),
    (QUARTZ, BIRCH_PLANKS, "quartz", CONCRETE_LIGHT),
]
