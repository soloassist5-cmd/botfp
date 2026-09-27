"""
level.dat: настройки мира, граница карты, геймрулы и подключённый датапак.
"""
from __future__ import annotations

import os
import time

from . import nbt
from .region import DATA_VERSION

GAME_RULES = {
    # Город не должен гореть и разрушаться сам по себе.
    "doFireTick": "false",
    "mobGriefing": "false",
    # В городе не нужны фантомы, патрули и странствующие торговцы.
    "doInsomnia": "false",
    "doPatrolSpawning": "false",
    "doTraderSpawning": "false",
    "doWardenSpawning": "false",
    # Удобства для игры с друзьями.
    "keepInventory": "false",
    "playersSleepingPercentage": "50",
    "announceAdvancements": "true",
    "spawnRadius": "2",
    "showDeathMessages": "true",
    "universalAnger": "false",
}


def _dimensions() -> dict:
    """Стандартные три измерения: генератор ванильный, чанки города уже записаны."""
    overworld = {
        "type": "minecraft:overworld",
        "generator": {
            "type": "minecraft:noise",
            "settings": "minecraft:overworld",
            "biome_source": {
                "type": "minecraft:multi_noise",
                "preset": "minecraft:overworld",
            },
        },
    }
    nether = {
        "type": "minecraft:the_nether",
        "generator": {
            "type": "minecraft:noise",
            "settings": "minecraft:nether",
            "biome_source": {
                "type": "minecraft:multi_noise",
                "preset": "minecraft:nether",
            },
        },
    }
    end = {
        "type": "minecraft:the_end",
        "generator": {
            "type": "minecraft:noise",
            "settings": "minecraft:end",
            "biome_source": {"type": "minecraft:the_end"},
        },
    }
    return {
        "minecraft:overworld": overworld,
        "minecraft:the_nether": nether,
        "minecraft:the_end": end,
    }


def write_level_dat(path: str, *, name: str, seed: int, spawn: tuple[int, int, int],
                    border: int, datapacks: list[str]) -> None:
    data = {
        "DataVersion": nbt.Int(DATA_VERSION),
        "version": {
            "Id": nbt.Int(DATA_VERSION),
            "Name": "1.20.1",
            "Series": "main",
            "Snapshot": nbt.Byte(0),
        },
        "LevelName": name,
        "GameType": nbt.Int(0),
        "Difficulty": nbt.Byte(2),
        "DifficultyLocked": nbt.Byte(0),
        "hardcore": nbt.Byte(0),
        # Читы включены: без них не запустить функции датапака со спавном NPC.
        "allowCommands": nbt.Byte(1),
        "initialized": nbt.Byte(1),
        "LastPlayed": nbt.Long(int(time.time() * 1000)),
        "SpawnX": nbt.Int(spawn[0]),
        "SpawnY": nbt.Int(spawn[1]),
        "SpawnZ": nbt.Int(spawn[2]),
        "SpawnAngle": nbt.Float(0.0),
        "Time": nbt.Long(0),
        "DayTime": nbt.Long(1000),
        "clearWeatherTime": nbt.Int(0),
        "rainTime": nbt.Int(12000),
        "raining": nbt.Byte(0),
        "thunderTime": nbt.Int(24000),
        "thundering": nbt.Byte(0),
        "WanderingTraderSpawnChance": nbt.Int(0),
        "WanderingTraderSpawnDelay": nbt.Int(24000),
        "BorderCenterX": nbt.Double(0.0),
        "BorderCenterZ": nbt.Double(0.0),
        "BorderSize": nbt.Double(float(border)),
        "BorderSizeLerpTarget": nbt.Double(float(border)),
        "BorderSizeLerpTime": nbt.Long(0),
        "BorderSafeZone": nbt.Double(5.0),
        "BorderWarningBlocks": nbt.Double(8.0),
        "BorderWarningTime": nbt.Double(15.0),
        "BorderDamagePerBlock": nbt.Double(0.2),
        "GameRules": dict(GAME_RULES),
        "WorldGenSettings": {
            "seed": nbt.Long(seed),
            "generate_features": nbt.Byte(1),
            "bonus_chest": nbt.Byte(0),
            "dimensions": _dimensions(),
        },
        "DataPacks": {
            "Enabled": ["vanilla"] + [f"file/{pack}" for pack in datapacks],
            "Disabled": nbt.List(nbt.TAG_STRING),
        },
        "ServerBrands": ["forge"],
        # Без этого блока сервер пишет ошибку "key missing: DragonFight".
        "DimensionData": {
            "1": {
                "DragonFight": {
                    "NeedsStateScanning": nbt.Byte(1),
                    "DragonKilled": nbt.Byte(0),
                    "PreviouslyKilled": nbt.Byte(0),
                    "Gateways": nbt.IntArray(list(range(20))),
                },
            },
        },
    }
    nbt.write_gzip(path, {"Data": data})


def write_session_lock(directory: str) -> None:
    with open(os.path.join(directory, "session.lock"), "wb") as fh:
        fh.write(b"\xe2\x98\x83")   # снеговик, как в ванили
