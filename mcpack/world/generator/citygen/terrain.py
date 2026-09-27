"""
Ландшафт карты: океан и пляж на западе, холмы на севере,
ровная «чаша» под городом в центре, пустыня на востоке.

Высота — чистая функция от (x, z), поэтому регионы можно генерировать
независимо и в любом порядке: стыки всегда сходятся.
"""
from __future__ import annotations

import math

from . import blocks as B

SEA_LEVEL = 62
CITY_LEVEL = 68          # уровень асфальта в городе
BEDROCK_TOP = -60

# Границы зон по X (запад отрицательный).
OCEAN_EDGE = -700        # западнее — только океан
BEACH_EDGE = -600        # пляж между OCEAN_EDGE и BEACH_EDGE
DESERT_EDGE = 720        # восточнее — пустыня
HILLS_EDGE = -520        # севернее (z меньше) — холмы


def _hash2(x: int, z: int, seed: int) -> float:
    """Детерминированный шум в [0,1) без внешних зависимостей."""
    h = (x * 374761393 + z * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / 0x1000000


def _smooth_noise(x: float, z: float, scale: float, seed: int) -> float:
    """Билинейно сглаженный шум масштаба `scale`."""
    fx, fz = x / scale, z / scale
    x0, z0 = math.floor(fx), math.floor(fz)
    tx, tz = fx - x0, fz - z0
    tx = tx * tx * (3 - 2 * tx)
    tz = tz * tz * (3 - 2 * tz)
    n00 = _hash2(x0, z0, seed)
    n10 = _hash2(x0 + 1, z0, seed)
    n01 = _hash2(x0, z0 + 1, seed)
    n11 = _hash2(x0 + 1, z0 + 1, seed)
    return (n00 * (1 - tx) + n10 * tx) * (1 - tz) + (n01 * (1 - tx) + n11 * tx) * tz


def fbm(x: float, z: float, seed: int, octaves: int = 3, scale: float = 64.0) -> float:
    """Сумма нескольких октав шума, результат в [0,1]."""
    total = 0.0
    amplitude = 1.0
    norm = 0.0
    for octave in range(octaves):
        total += amplitude * _smooth_noise(x, z, scale / (2 ** octave), seed + octave * 7919)
        norm += amplitude
        amplitude *= 0.5
    return total / norm


def _smoothstep(edge0: float, edge1: float, value: float) -> float:
    if edge0 == edge1:
        return 0.0 if value < edge0 else 1.0
    t = (value - edge0) / (edge1 - edge0)
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


class Terrain:
    """Высоты и покрытие поверхности."""

    def __init__(self, seed: int, city_bounds: tuple[int, int, int, int]):
        self.seed = seed
        self.city_x0, self.city_z0, self.city_x1, self.city_z1 = city_bounds

    # --- зоны ---------------------------------------------------------------
    def in_city(self, x: int, z: int, margin: int = 0) -> bool:
        return (self.city_x0 - margin <= x <= self.city_x1 + margin
                and self.city_z0 - margin <= z <= self.city_z1 + margin)

    def height(self, x: int, z: int) -> int:
        """Высота верхнего блока поверхности."""
        # Океан и пляж на западе.
        if x <= OCEAN_EDGE:
            depth = _smoothstep(OCEAN_EDGE, OCEAN_EDGE - 220, x)
            floor = SEA_LEVEL - 4 - int(22 * depth)
            return floor + int(3 * fbm(x, z, self.seed + 11, 2, 48))

        base = float(CITY_LEVEL)

        # Плавный подъём от пляжа к городскому уровню.
        beach_t = _smoothstep(OCEAN_EDGE, BEACH_EDGE + 40, x)
        base = (SEA_LEVEL - 2) + (CITY_LEVEL - SEA_LEVEL + 2) * beach_t

        # Холмы на севере: чем меньше z, тем выше.
        hill_t = _smoothstep(HILLS_EDGE + 120, HILLS_EDGE - 320, z)
        if hill_t > 0:
            ridge = fbm(x, z, self.seed + 31, 4, 140)
            base += hill_t * (14 + 54 * ridge)

        # Пустыня на востоке: дюны.
        desert_t = _smoothstep(DESERT_EDGE - 160, DESERT_EDGE + 200, x)
        if desert_t > 0:
            dunes = fbm(x, z, self.seed + 57, 3, 70)
            base += desert_t * (4 + 22 * dunes)

        # Мелкая неровность вне города.
        city = self._city_falloff(x, z)
        base += (1.0 - city) * (fbm(x, z, self.seed + 71, 3, 40) - 0.5) * 7

        # Внутри города — строго ровная площадка, по краям плавный переход.
        base = base * (1.0 - city) + CITY_LEVEL * city
        return int(round(base))

    def _city_falloff(self, x: int, z: int) -> float:
        """1 внутри города, 0 далеко за его пределами (плавный переход)."""
        fade = 90
        tx = min(_smoothstep(self.city_x0 - fade, self.city_x0, x),
                 _smoothstep(self.city_x1 + fade, self.city_x1, x))
        tz = min(_smoothstep(self.city_z0 - fade, self.city_z0, z),
                 _smoothstep(self.city_z1 + fade, self.city_z1, z))
        return min(tx, tz)

    def surface(self, x: int, z: int, height: int) -> tuple[str, str]:
        """(верхний блок, подпочва) для колонны."""
        if height < SEA_LEVEL - 1:
            return (B.GRAVEL if fbm(x, z, self.seed + 91, 2, 24) > 0.62 else B.SAND, B.SAND)
        if x <= BEACH_EDGE + 30 or height <= SEA_LEVEL + 2:
            return (B.SAND, B.SAND)
        if x >= DESERT_EDGE - 120 + int(60 * fbm(x, z, self.seed + 97, 2, 90)):
            return (B.SAND, B.SANDSTONE)
        if height > 96:
            return (B.COARSE_DIRT if fbm(x, z, self.seed + 101, 2, 30) > 0.55
                    else B.GRASS, B.DIRT)
        return (B.GRASS, B.DIRT)

    def biome(self, x: int, z: int) -> str:
        """Биом чанка — по его центру."""
        if x <= OCEAN_EDGE:
            return "minecraft:ocean"
        if x <= BEACH_EDGE + 30:
            return "minecraft:beach"
        if x >= DESERT_EDGE - 100:
            return "minecraft:desert"
        if z <= HILLS_EDGE - 40:
            return "minecraft:windswept_hills" if self.height(x, z) > 100 else "minecraft:forest"
        return "minecraft:plains"
