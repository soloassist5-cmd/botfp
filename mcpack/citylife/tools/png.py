"""
Мини-генератор PNG без внешних зависимостей.

Pillow в окружении сборки нет, а текстуры нужны маленькие (16x16) и плоские,
поэтому проще писать PNG вручную: заголовок, IHDR, IDAT со строками
фильтра 0 и IEND. Цвет — RGBA по 8 бит.
"""
from __future__ import annotations

import struct
import zlib


class Canvas:
    def __init__(self, width: int, height: int):
        self.w = width
        self.h = height
        self.px = [[(0, 0, 0, 0)] * width for _ in range(height)]

    def set(self, x: int, y: int, colour: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            if colour[3] == 255:
                self.px[y][x] = colour
                return
            # Простое смешивание с тем, что уже лежит: нужно для теней и бликов.
            r0, g0, b0, a0 = self.px[y][x]
            a = colour[3] / 255
            self.px[y][x] = (
                round(colour[0] * a + r0 * (1 - a)),
                round(colour[1] * a + g0 * (1 - a)),
                round(colour[2] * a + b0 * (1 - a)),
                max(a0, colour[3]),
            )

    def rect(self, x0: int, y0: int, x1: int, y1: int, colour) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, colour)

    def frame(self, x0: int, y0: int, x1: int, y1: int, colour) -> None:
        for x in range(x0, x1 + 1):
            self.set(x, y0, colour)
            self.set(x, y1, colour)
        for y in range(y0, y1 + 1):
            self.set(x0, y, colour)
            self.set(x1, y, colour)

    def disc(self, cx: float, cy: float, r: float, colour) -> None:
        for y in range(self.h):
            for x in range(self.w):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.set(x, y, colour)

    def ring(self, cx: float, cy: float, r: float, width: float, colour) -> None:
        for y in range(self.h):
            for x in range(self.w):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                if r - width <= d <= r:
                    self.set(x, y, colour)

    def write(self, path: str) -> None:
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for r, g, b, a in row:
                raw += bytes((r, g, b, a))

        def chunk(kind: bytes, payload: bytes) -> bytes:
            return (struct.pack(">I", len(payload)) + kind + payload
                    + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        with open(path, "wb") as fh:
            fh.write(png)
