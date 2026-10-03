"""
Карта Лос-Сантоса в SVG — рисуется из того же плана, по которому построен мир.

Координаты SVG совпадают с игровыми: viewBox начинается в -1024 и занимает
2048 единиц, поэтому любой объект можно ставить прямо по его координатам.
"""
from __future__ import annotations

import os
import sys
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "world", "generator"))

from citygen import plan as P                                    # noqa: E402
from citygen.terrain import BEACH_EDGE, OCEAN_EDGE, Terrain      # noqa: E402

CELL = 32          # шаг классификации рельефа
SEED = 20260927

# Окно карты: город с запасом на океан слева и пустыню справа.
VIEW_X0, VIEW_Z0 = -780, -570
VIEW_W, VIEW_H = 1590, 1420

TERRAIN_FILL = {
    "ocean": "url(#ocean)",
    "beach": "#4b432f",
    "plain": "#1b2235",
    "hills": "#20302a",
    "desert": "#332a1b",
}

# Цвет участка по типу застройки.
LOT_FILL = {
    "tower": "#35c7f0",
    "mall": "#ff4d7d",
    "city_hall": "#ffc44d",
    "bank": "#ffc44d",
    "police": "#5b8cff",
    "hospital": "#ff6b6b",
    "fire_station": "#ff8a3d",
    "gun_shop": "#ffc44d",
    "phone_shop": "#ffc44d",
    "dealership": "#ffc44d",
    "club": "#c86bff",
    "gas": "#ff8a3d",
    "metro": "#9b7bff",
    "park": "#2e7d52",
    "construction": "#8a7a3d",
    "warehouse": "#3c4160",
    "parking": "#454a6b",
    "apartment": "#565d92",
    "office": "#515788",
    "shop": "#5a6190",
    "diner": "#6b5a8f",
    "house": "#454c70",
    "villa": "#525b8c",
    "mansion": "#8c7a52",
    "rowhouse": "#4d5580",
    "square": "#2a6b47",
    "pickup": "#c77dff",
    "empty": "#30563f",
}

# Ключевые объекты: подпись на карте и порядок в легенде.
PINS = [
    ("Автовокзал", None, (P.SPAWN[0], P.SPAWN[2]), "spawn"),
    ("STARK TOWER", "STARK TOWER", None, "tower"),
    ("MERIDIAN", "MERIDIAN", None, "tower"),
    ("SUNSET PLAZA", "SUNSET PLAZA", None, "tower"),
    ("Торговый центр", "ТОРГОВЫЙ ЦЕНТР", None, "mall"),
    ("Мэрия · агентство недвижимости", "МЭРИЯ", None, "civic"),
    ("Городской банк", "ГОРОДСКОЙ БАНК", None, "civic"),
    ("Полиция", "ПОЛИЦИЯ", None, "civic"),
    ("Больница", "БОЛЬНИЦА", None, "civic"),
    ("Пожарная часть", "ПОЖАРНАЯ ЧАСТЬ", None, "civic"),
    ("Оружейный магазин", "ОРУЖЕЙНЫЙ МАГАЗИН", None, "shop"),
    ("Салон связи", "САЛОН СВЯЗИ", None, "shop"),
    ("Автосалон", "АВТОСАЛОН", None, "shop"),
    ("Ночной клуб", "НОЧНОЙ КЛУБ", None, "night"),
    ("Пляжный бар", "ПЛЯЖНЫЙ БАР", None, "night"),
    ("Метро «Центр»", "МЕТРО «ЦЕНТР»", None, "metro"),
    ("Метро «Пляж»", "МЕТРО «ПЛЯЖ»", None, "metro"),
    ("Метро «Восток»", "МЕТРО «ВОСТОК»", None, "metro"),
    ("Пирс с кафе", None, (P.PIER_X_TO + 14, P.PIER_Z), "pier"),
    ("Центральный парк", "ЦЕНТРАЛЬНЫЙ ПАРК", None, "park"),
    ("Парк «Дубрава»", "ПАРК «ДУБРАВА»", None, "park"),
    ("Парк на холмах", "ПАРК НА ХОЛМАХ", None, "park"),
    ("Береговой", None, (-512, -720), "estate"),
    ("Приморский", None, (-420, 904), "estate"),
]

PIN_COLOR = {
    "spawn": "#7be07b",
    "tower": "#35c7f0",
    "mall": "#ff4d7d",
    "civic": "#ffc44d",
    "shop": "#ffd88a",
    "night": "#c86bff",
    "metro": "#9b7bff",
    "pier": "#35c7f0",
    "park": "#5fd38d",
    "pickup": "#c77dff",
    "estate": "#f0c35a",
}


def classify(terrain: Terrain, x: int, z: int) -> str:
    if x <= OCEAN_EDGE:
        return "ocean"
    if x <= BEACH_EDGE + 30:
        return "beach"
    height = terrain.height(x, z)
    if x >= 620 and height > 74:
        return "desert"
    if height > 88:
        return "hills"
    return "plain"


def terrain_runs(terrain: Terrain, half: int) -> list[tuple[str, int, int, int]]:
    """Полосы одного типа рельефа: (тип, x, z, ширина) — вместо тысяч клеток."""
    runs = []
    for z in range(-half, half, CELL):
        current = None
        start = -half
        for x in range(-half, half, CELL):
            kind = classify(terrain, x + CELL // 2, z + CELL // 2)
            if kind != current:
                if current is not None:
                    runs.append((current, start, z, x - start))
                current, start = kind, x
        runs.append((current, start, z, half - start))
    return runs


def build_svg() -> tuple[str, list[dict]]:
    city = P.build_plan(SEED)
    terrain = Terrain(SEED, P.CITY_BOUNDS)
    half = P.WORLD_BORDER // 2
    by_label = {lot.label: lot for lot in city.lots if lot.label}

    out: list[str] = []
    add = out.append

    add(f'<svg class="map-svg" viewBox="{VIEW_X0} {VIEW_Z0} {VIEW_W} {VIEW_H}" '
        f'xmlns="http://www.w3.org/2000/svg" '
        f'role="img" aria-label="Карта города Лос-Сантос">')
    add('''<defs>
  <linearGradient id="ocean" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#04101f"/><stop offset="1" stop-color="#0b2540"/>
  </linearGradient>
  <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
    <stop offset="0" stop-color="#ff7a45" stop-opacity="0.35"/>
    <stop offset="1" stop-color="#ff7a45" stop-opacity="0"/>
  </radialGradient>
  <filter id="softTerrain" x="-5%" y="-5%" width="110%" height="110%">
    <feGaussianBlur stdDeviation="14"/>
  </filter>
  <filter id="pinShadow" x="-50%" y="-50%" width="200%" height="200%">
    <feDropShadow dx="0" dy="6" stdDeviation="8" flood-color="#000" flood-opacity="0.55"/>
  </filter>
</defs>''')

    # Рельеф.
    add('<g class="map-terrain">')
    for kind, x, z, width in terrain_runs(terrain, half):
        add(f'<rect x="{x}" y="{z}" width="{width}" height="{CELL}" '
            f'fill="{TERRAIN_FILL[kind]}"/>')
    add('</g>')

    # Свечение над центром — просто чтобы центр читался как центр.
    add('<circle cx="110" cy="70" r="680" fill="url(#glow)"/>')

    # Улицы.
    add('<g class="map-streets">')
    for index in range(P.IX_MIN, P.IX_MAX + 2):
        x = index * P.CELL
        width = 17 if P.is_avenue(index) else 11
        add(f'<rect x="{x - width // 2}" y="{P.IZ_MIN * P.CELL}" width="{width}" '
            f'height="{(P.IZ_MAX + 2 - P.IZ_MIN) * P.CELL}" fill="#0b0d16"/>')
    for index in range(P.IZ_MIN, P.IZ_MAX + 2):
        z = index * P.CELL
        height = 17 if P.is_avenue(index) else 11
        add(f'<rect x="{P.IX_MIN * P.CELL}" y="{z - height // 2}" '
            f'width="{(P.IX_MAX + 2 - P.IX_MIN) * P.CELL}" height="{height}" '
            f'fill="#0b0d16"/>')
    add('</g>')

    # Дороги прибрежных районов.
    from citygen import estates as E
    for road in E.layout(SEED).roads:
        w = E.ROAD_HALF * 2 + 1
        if road.vertical:
            add(f'<rect x="{road.fixed - E.ROAD_HALF}" y="{road.a}" width="{w}" '
                f'height="{road.b - road.a + 1}" fill="#0b0d16"/>')
        else:
            add(f'<rect x="{road.a}" y="{road.fixed - E.ROAD_HALF}" width="{road.b - road.a + 1}" '
                f'height="{w}" fill="#0b0d16"/>')

    # Участки.
    add('<g class="map-lots">')
    for lot in city.lots:
        fill = LOT_FILL.get(lot.kind, "#3c4260")
        extra = ' class="lot-empty"' if lot.kind == "empty" else ""
        add(f'<rect x="{lot.x0}" y="{lot.z0}" width="{lot.width}" '
            f'height="{lot.depth}" fill="{fill}" stroke="#0b0d16" stroke-width="1.2"{extra}/>')
    add('</g>')

    # Эстакада, пирс, линии метро.
    add('<g class="map-infra">')
    add(f'<rect x="{P.FREEWAY_X - P.FREEWAY_HALF}" y="{P.IZ_MIN * P.CELL}" '
        f'width="{P.FREEWAY_HALF * 2}" '
        f'height="{(P.IZ_MAX + 2 - P.IZ_MIN) * P.CELL}" fill="#5a4a2a" '
        f'stroke="#ffc44d" stroke-width="1.5" stroke-opacity="0.5"/>')
    add(f'<rect x="{min(P.PIER_X_FROM, P.PIER_X_TO)}" y="{P.PIER_Z - 5}" '
        f'width="{abs(P.PIER_X_FROM - P.PIER_X_TO)}" height="11" fill="#5b4a33"/>')
    stations = sorted(city.metro_stations)
    for index in range(len(stations) - 1):
        x0, z0, _ = stations[index]
        x1, z1, _ = stations[index + 1]
        add(f'<polyline points="{x0},{z0} {x1},{z0} {x1},{z1}" fill="none" '
            f'stroke="#9b7bff" stroke-width="4" stroke-opacity="0.75" '
            f'stroke-dasharray="14 10"/>')
    add('</g>')

    # Метки.
    pins: list[dict] = []
    add('<g class="map-pins">')
    for number, (name, label, coords, kind) in enumerate(PINS, start=1):
        if coords is None:
            lot = by_label.get(label)
            if lot is None:
                continue
            x, z = lot.center()
        else:
            x, z = coords
        color = PIN_COLOR[kind]
        pins.append({"n": number, "name": name, "x": x, "z": z, "color": color})
        add(f'<g class="pin" data-pin="{number}" filter="url(#pinShadow)">'
            f'<circle cx="{x}" cy="{z}" r="28" fill="{color}" fill-opacity="0.18"/>'
            f'<circle cx="{x}" cy="{z}" r="13.5" fill="{color}" stroke="#05060c" stroke-width="2"/>'
            f'<text x="{x}" y="{z + 7}" text-anchor="middle" font-size="19" '
            f'font-weight="700" fill="#06070d">{number}</text></g>')
    # Пункты выдачи маркетплейса: без номеров, одной строкой в легенде.
    pickups = [lot for lot in city.lots if lot.kind == "pickup"]
    for lot in pickups:
        x, z = lot.center()
        add(f'<g class="pin pin-pickup" filter="url(#pinShadow)">'
            f'<circle cx="{x}" cy="{z}" r="9" fill="{PIN_COLOR["pickup"]}" '
            f'stroke="#05060c" stroke-width="2"/></g>')
    if pickups:
        x, z = pickups[0].center()
        pins.append({"n": "◆", "name": f"Пункты выдачи ({len(pickups)})", "x": x, "z": z,
                     "color": PIN_COLOR["pickup"]})
    add('</g>')

    # Подписи сторон света и масштаб.
    add(f'<text x="{VIEW_X0 + 30}" y="-20" class="map-label">ОКЕАН</text>')
    add(f'<text x="60" y="{VIEW_Z0 + 70}" class="map-label">ХОЛМЫ</text>')
    add(f'<text x="{VIEW_X0 + VIEW_W - 250}" y="-20" class="map-label">ПУСТЫНЯ</text>')
    add(f'<g class="map-scale"><rect x="{VIEW_X0 + 40}" y="{VIEW_Z0 + VIEW_H - 60}" '
        f'width="512" height="7" fill="#e8e9f3" fill-opacity="0.75"/>'
        f'<text x="{VIEW_X0 + 40}" y="{VIEW_Z0 + VIEW_H - 78}" class="map-label">'
        f'512 БЛОКОВ</text></g>')
    add('</svg>')
    return "\n".join(out), pins


if __name__ == "__main__":
    svg, pins = build_svg()
    print(f"элементов rect: {svg.count('<rect')}, метки: {len(pins)}, "
          f"размер: {len(svg) / 1024:.0f} КБ")
    for pin in pins:
        print(f"  {pin['n']:2}. {pin['name']:20} {pin['x']}, {pin['z']}")
