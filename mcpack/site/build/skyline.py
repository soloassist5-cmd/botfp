"""Силуэт города для героя страницы — рисуется кодом, а не картинкой."""
from __future__ import annotations

import random

WIDTH = 1600
HEIGHT = 420


def _layer(rng: random.Random, y_base: int, min_h: int, max_h: int, width_range,
           fill: str, windows: bool) -> list[str]:
    parts = [f'<g fill="{fill}">']
    x = -40
    lit = []
    while x < WIDTH + 40:
        w = rng.randrange(*width_range)
        h = rng.randrange(min_h, max_h)
        top = y_base - h
        parts.append(f'<rect x="{x}" y="{top}" width="{w}" height="{h + 10}"/>')
        # Надстройки на крыше: антенна или машинное отделение.
        roll = rng.random()
        if roll < 0.22 and w > 30:
            parts.append(f'<rect x="{x + w // 2 - 3}" y="{top - 26}" width="6" height="26"/>')
        elif roll < 0.4 and w > 46:
            parts.append(f'<rect x="{x + 8}" y="{top - 12}" width="{w - 16}" height="12"/>')
        if windows:
            for wy in range(top + 14, y_base - 8, 18):
                for wx in range(x + 7, x + w - 8, 14):
                    if rng.random() < 0.34:
                        colour = "#ffd98a" if rng.random() < 0.72 else "#9fe8ff"
                        lit.append(f'<rect x="{wx}" y="{wy}" width="5" height="8" '
                                   f'fill="{colour}" fill-opacity="'
                                   f'{0.35 + rng.random() * 0.5:.2f}"/>')
        x += w + rng.randrange(3, 16)
    parts.append('</g>')
    return parts + lit


def skyline_svg(seed: int = 20260927) -> str:
    rng = random.Random(seed)
    out = [f'<svg class="hero-skyline" viewBox="0 0 {WIDTH} {HEIGHT}" '
           f'preserveAspectRatio="xMidYMax slice" xmlns="http://www.w3.org/2000/svg" '
           f'aria-hidden="true">']
    # Дальний план светлее: воздушная перспектива на закате.
    out += _layer(rng, 300, 40, 150, (26, 70), "#2a1c33", False)
    out += _layer(rng, 350, 70, 230, (34, 86), "#170f22", False)
    out += _layer(rng, HEIGHT - 6, 110, 330, (44, 110), "#08070f", True)
    out.append('</svg>')
    return "\n".join(out)


def stars_svg(seed: int = 77, count: int = 90) -> str:
    rng = random.Random(seed)
    dots = []
    for _ in range(count):
        x = rng.random() * 100
        y = rng.random() * 100
        r = 0.6 + rng.random() * 1.1
        o = 0.25 + rng.random() * 0.6
        dots.append(f'<circle cx="{x:.2f}%" cy="{y:.2f}%" r="{r:.2f}" '
                    f'fill="#fff" fill-opacity="{o:.2f}"/>')
    return ('<svg class="hero-stars" xmlns="http://www.w3.org/2000/svg" '
            'aria-hidden="true">' + "".join(dots) + '</svg>')


if __name__ == "__main__":
    svg = skyline_svg()
    print(f"скайлайн: {svg.count('<rect')} блоков, {len(svg) / 1024:.0f} КБ")
    print(f"звёзды: {stars_svg().count('<circle')}")
