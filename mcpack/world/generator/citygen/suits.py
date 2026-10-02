"""
Костюмы Железного человека в Зале брони башни STARK.

Предметы — из Satsu Iron Man Addon: браслет, кейс или нано-модуль каждой
марки. Надетый на стойку брони в слот нагрудника, предмет рисуется как
полный костюм (у Халкбастера — огромный), поэтому зал собирается из
обычных стоек: по стойке на капсулу.

Подпись — то, что выбито на табличке у капсулы: номер и прозвище из фильмов
и комиксов. Порядок — хронология: от «чёрных» костюмов первых марок через
Дом вечеринок Mark 7–41 к нано-броне и Воителю.
"""
from __future__ import annotations

SATSU = "satsu_iron_man_addon"

# (предмет без пространства имён, строка таблички 1, строка таблички 2)
_BLACK = [(f"marks/mark_0{n}/mark_0{n}_black_suit", f"MARK {n}", "") for n in (2, 3, 4)]
_NICK = {
    7: "", 8: "", 9: "", 10: "", 11: "", 12: "", 13: "", 14: "",
    15: "SNEAKY", 16: "NIGHTCLUB", 17: "HEARTBREAKER", 18: "CASSANOVA", 19: "TIGER",
    20: "PYTHON", 21: "MIDAS", 22: "HOTROD", 23: "SHADES", 24: "TANK", 25: "STRIKER",
    26: "GAMMA", 27: "DISCO", 28: "JACK", 29: "FIDDLER", 30: "BLUE STEEL", 31: "PISTON",
    32: "ROMEO", 33: "SILVER CENTURION", 34: "SOUTHPAW", 35: "RED SNAPPER", 36: "PEACEMAKER",
    37: "HAMMERHEAD", 38: "IGOR", 39: "STARBOOST", 40: "SHOTGUN", 41: "BONES",
    42: "PRODIGAL SON", 43: "PRODIGAL SON+", 44: "HULKBUSTER", 45: "AVENGERS",
}

SUITS: list[tuple[str, str, str]] = (
    _BLACK
    + [("marks/mark_05/briefcase", "MARK 5", "SUITCASE"),
       ("marks/mark_06/mark_06_black_suit", "MARK 6", "")]
    + [(f"marks/mark_{n:02d}/bracelet", f"MARK {n}", _NICK[n]) for n in range(7, 46)]
    + [("marks/mark_46/briefcase", "MARK 46", "CIVIL WAR"),
       ("marks/mark_47/briefcase", "MARK 47", "HOMECOMING"),
       ("marks/mark_49/bracelet", "MARK 49", "RESCUE"),
       ("marks/mark_50/main", "MARK 50", "NANOTECH"),
       ("marks/mark_80/main", "MARK 80", ""),
       ("marks/mark_81/main", "MARK 81", ""),
       ("marks/mark_82/main", "MARK 82", "NANO ARC"),
       ("marks/mark_85/main", "MARK 85", "ENDGAME"),
       ("marks/mark_86/main", "MARK 86", ""),
       ("marks/model_prime/main", "MODEL PRIME", ""),
       ("marks/infamous/main", "INFAMOUS", ""),
       ("marks/mark_extremis/main", "EXTREMIS", ""),
       ("marks/mark_1873_nano/main", "MARK 1873", ""),
       ("marks/mark_blooded/main", "BLOODED", ""),
       ("marks/model_50/main", "MODEL 50", ""),
       ("marks/model_37_iron_destroyer/main", "MODEL 37", "IRON DESTROYER"),
       ("marks/mark_taken/main", "TAKEN", ""),
       ("marks/model_nil/briefcase", "MODEL NIL", ""),
       ("marks/modular_armor/main", "MODEL 13", "MODULAR"),
       ("marks/model_72/bracelet", "MODEL 72", ""),
       ("marks/iron_legion_prototype/bracelet", "IRON LEGION", "PROTOTYPE")]
    + [("war_machine/marks/mark_01/mark_01_black_suit", "WAR MACHINE", "MARK 1")]
    + [(f"war_machine/marks/mark_0{n}/main", "WAR MACHINE", f"MARK {n}") for n in range(2, 7)]
    + [("iron_heart/marks/mark_01/main", "IRONHEART", "MARK 1"),
       ("iron_heart/marks/mark_03/main", "IRONHEART", "MARK 3")]
)

# Халкбастер в капсулу не влезает: он стоит в центре зала на своей площадке.
HULKBUSTER = "marks/mark_44/bracelet"


def item(suit: str) -> str:
    return f"{SATSU}:{suit}"
