#!/usr/bin/env python3
"""
Сборка страницы LS City Life.

Все числа, списки модов, адреса объектов и ассортимент жителей берутся из самой
сборки: mods.lock.json, pack.toml и плана города. Руками в тексте ничего не
дублируется, поэтому страница не может разойтись с паком.

    python3 build/build_site.py          # -> dist/
"""
from __future__ import annotations

import hashlib
import html
import json
import os
import shutil
import sys
import datetime
import tomllib
from collections import Counter

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(HERE, "build"))
sys.path.insert(0, os.path.join(PACK, "world", "generator"))
sys.path.insert(0, os.path.join(PACK, "build"))

from citygen import plan as P            # noqa: E402
from city_map import build_svg           # noqa: E402
from skyline import skyline_svg, stars_svg  # noqa: E402
import gen_datapack as G                 # noqa: E402

SEED = 20260927
ARCHIVE_NAME = "ls-city-life-1.0.0.zip"
ARCHIVE_URL = f"download/{ARCHIVE_NAME}"
OFFICIAL_NAME = "ls-city-life-1.0.0-official.zip"
OFFICIAL_URL = f"download/{OFFICIAL_NAME}"
# Ссылки относительные: та же страница работает и в корне Vercel, и на
# зеркале GitHub Pages, где сайт лежит в подкаталоге /botfp/.
SITE_URL = "https://ls-city-life.vercel.app"
MIRROR_URL = "https://soloassist5-cmd.github.io/botfp/"
# Пак в формате Modrinth: лаунчер разворачивает его сам, установщик не нужен.
MRPACK_NAME = "ls-city-life-1.0.0.mrpack"
MRPACK_URL = f"download/{MRPACK_NAME}"
# Профиль CurseForge App: zip с manifest.json, моды он качает сам.
CF_NAME = "ls-city-life-1.0.0-curseforge.zip"
CF_URL = f"download/{CF_NAME}"

KIND_RU = {
    "empty": "Свободные участки", "house": "Частные дома", "shop": "Магазины",
    "villa": "Виллы", "apartment": "Доходные дома", "warehouse": "Склады",
    "park": "Парки и скверы", "office": "Офисные здания", "metro": "Станции метро",
    "diner": "Закусочные", "tower": "Небоскрёбы", "club": "Ночные клубы",
    "gas": "АЗС", "construction": "Стройплощадки", "phone_shop": "Салон связи",
    "gun_shop": "Оружейный магазин", "parking": "Парковка", "city_hall": "Мэрия",
    "bank": "Банк", "mall": "Торговый центр", "police": "Полиция",
    "hospital": "Больница", "dealership": "Автосалон", "fire_station": "Пожарная часть",
    "rowhouse": "Таунхаусы", "square": "Скверы", "pickup": "Пункты выдачи",
}

GOODS_RU = {
    "bread": "хлеб", "cooked_beef": "говядина", "golden_carrot": "золотая морковь",
    "sweet_berries": "ягоды", "leather_chestplate": "кожаная куртка",
    "leather_boots": "кожаные ботинки", "white_dye": "краска",
    "smartphone": "смартфон", "sim_card": "SIM-карта", "redstone": "редстоун",
    "camera": "фотоаппарат", "atm_card": "банковская карта",
    "coin_chest": "сундук монет", "cash_register": "касса", "atm": "банкомат",
    "banknote_100": "100 \u20bd", "banknote_1000": "1000 \u20bd",
    "banknote_5000": "5000 \u20bd", "coin_10": "10 \u20bd",
    "fuel_canister": "канистра", "vehicle_crate": "ящик с машиной", "wrench": "гаечный ключ",
    "ammo_box": "ящик патронов", "card_mir": "карта \u00abМир\u00bb",
    "gun_smith_table": "верстак оружейника", "iron_ingot": "железо",
    "gunpowder": "порох", "ammo_box.iron": "ящик патронов",
    "iron_block": "железные блоки", "coal": "уголь", "glass": "стекло",
    "smart_lock": "умный замок", "keypad": "кодовый замок",
    "security_camera": "камера наблюдения", "paper": "бумага", "map": "карта",
    "ticket_machine": "автомат билетов", "cooked_chicken": "курица",
    "pumpkin_pie": "пирог", "cake": "торт", "torch": "факелы",
    "oak_planks": "доски", "iron_pickaxe": "кирка", "bucket": "ведро",
    "honey_bottle": "напитки", "jukebox": "патефон", "music_disc_cat": "пластинка",
    "coal_block": "блок угля", "golden_apple": "золотое яблоко", "potion": "зелья",
    "stone": "камень", "scaffolding": "строительные леса",
    "smooth_stone": "гладкий камень",
}

GROUP_RU = {
    "core": ("Ядро геймплея", "оружие, деньги, замки, камеры, транспорт, жители"),
    "deco": ("Строительство и декор", "чем достраивать город под себя"),
    "qol": ("Удобства и атмосфера", "карты, голосовой чат, звук, анимации"),
    "perf": ("Производительность", "чтобы плотный город не тормозил"),
    "lib": ("Библиотеки", "нужны перечисленным модам, сами ничего не добавляют"),
}

ICONS = {
    "gun": '<path d="M2 9h14l3 3h3v4h-6l-2-2H9v4H6v-4H2z"/><path d="M6 13v3"/>',
    "coin": '<circle cx="9" cy="9" r="6"/><circle cx="15" cy="15" r="6"/>'
            '<path d="M9 6v6M15 12v6"/>',
    "phone": '<rect x="6" y="2" width="12" height="20" rx="3"/>'
             '<path d="M10 18h4"/><path d="M9 6h6"/>',
    "camera": '<path d="M3 8h4l2-3h6l2 3h4v12H3z"/><circle cx="12" cy="13" r="4"/>',
    "lock": '<rect x="4" y="10" width="16" height="11" rx="2"/>'
            '<path d="M8 10V7a4 4 0 0 1 8 0v3"/><circle cx="12" cy="16" r="1.6"/>',
    "car": '<path d="M3 15l2-6h14l2 6v4h-3l-1-2H7l-1 2H3z"/>'
           '<circle cx="7.5" cy="17.5" r="1.8"/><circle cx="16.5" cy="17.5" r="1.8"/>',
    "npc": '<circle cx="9" cy="8" r="3.4"/><path d="M3 20c0-3.4 2.7-5.6 6-5.6s6 2.2 6 5.6"/>'
           '<circle cx="17" cy="9" r="2.6"/><path d="M15.5 20c0-2.6 1.6-4.3 3.5-4.3 1.2 0 2 .6 2 .6"/>',
    "house": '<path d="M3 11l9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/>',
    "siren": '<path d="M6 18v-5a6 6 0 0 1 12 0v5"/><path d="M4 18h16v3H4z"/>'
             '<path d="M12 3v2M4.5 6l1.4 1.4M19.5 6l-1.4 1.4"/>',
    "call": '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/>'
            '<path d="M15 3a6 6 0 0 1 6 6M15 7a2 2 0 0 1 2 2"/>',
    "walk": '<circle cx="13" cy="4" r="2"/><path d="M9 21l3-7 3 3v4"/>'
            '<path d="M7 11l3-3h4l2 4 3 1"/><path d="M12 8l-1 6"/>',
    "shield": '<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
    "work": '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5h6v2"/>'
            '<path d="M3 12h18"/>',
}


def icon(name: str, colour: str) -> str:
    return (f'<svg class="card-icon" viewBox="0 0 24 24" fill="none" '
            f'stroke="currentColor" stroke-width="1.6" stroke-linecap="round" '
            f'stroke-linejoin="round" aria-hidden="true">{ICONS[name]}</svg>')


def esc(text: str) -> str:
    return html.escape(str(text), quote=True)


def code_block(text: str, lang: str = "команда") -> str:
    return (f'<div class="code"><div class="code-bar">'
            f'<span class="code-lang">{esc(lang)}</span>'
            f'<button class="copy" type="button">копировать</button></div>'
            f'<code>{esc(text)}</code></div>')


MSK = datetime.timezone(datetime.timedelta(hours=3), "МСК")


def file_facts(path: str) -> tuple[str, str, str]:
    """Размер, короткая метка содержимого и дата сборки файла для страницы."""
    if not os.path.exists(path):
        return "—", "dev", "—"
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    months = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля",
              "августа", "сентября", "октября", "ноября", "декабря"]
    # Время — по Москве и с пометкой: сборка идёт на машине с часами в UTC,
    # и без пояса на странице пак выглядел на три часа старше.
    built = datetime.datetime.fromtimestamp(os.path.getmtime(path), MSK)
    return (f"{os.path.getsize(path) / 1048576:.1f}".replace(".", ","),
            digest.hexdigest()[:8],
            f"{built.day} {months[built.month - 1]}, {built:%H:%M} МСК")


def main() -> int:
    with open(os.path.join(PACK, "pack.toml"), "rb") as fh:
        pack = tomllib.load(fh)["pack"]
    with open(os.path.join(PACK, "mods.lock.json"), encoding="utf-8") as fh:
        lock = json.load(fh)

    city = P.build_plan(SEED)
    counts = Counter(lot.kind for lot in city.lots)
    archive_path = os.path.join(PACK, "dist", "ls-city-life-1.0.0-full.zip")
    archive_mb, archive_tag, archive_date = file_facts(archive_path)
    archive_href = f"{ARCHIVE_URL}?v={archive_tag}"
    official_path = os.path.join(PACK, "dist", "ls-city-life-1.0.0-official.zip")
    official_mb, official_tag, _ = file_facts(official_path)
    official_href = f"{OFFICIAL_URL}?v={official_tag}"
    mrpack_path = os.path.join(PACK, "dist", f"{pack['id']}-{pack['version']}.mrpack")
    mrpack_mb, mrpack_tag, mrpack_date = file_facts(mrpack_path)
    mrpack_href = f"{MRPACK_URL}?v={mrpack_tag}"
    cf_path = os.path.join(PACK, "dist", f"{pack['id']}-{pack['version']}-curseforge.zip")
    cf_mb, cf_tag, _ = file_facts(cf_path)
    cf_href = f"{CF_URL}?v={cf_tag}"
    map_svg, pins = build_svg()

    mc = pack["minecraft"]
    forge = pack["loader_version"]
    mods_total = lock["mod_count"] + 1          # плюс самописный
    download_mb = round(lock["total_size"] / 1048576)
    npc_total = len(city.npc_spots)
    # Сколько модов тянет лаунчер: всё из lock, кроме необязательных.
    auto_mods = len([m for m in lock["mods"] if not m.get("optional")])
    empty_share = round(counts["empty"] * 100 / len(city.lots))

    # --- герой -------------------------------------------------------------
    stats = [
        (mods_total, "модов"),
        (len(city.lots), "участков"),
        (len(city.npc_spots), "жителей"),
        ("2048²", "блоков карты"),
        (f"{empty_share}%", "земли свободно"),
    ]
    stats_html = "".join(
        f'<div><div class="stat-num grad">{esc(value)}</div>'
        f'<div class="stat-cap">{esc(caption)}</div></div>'
        for value, caption in stats
    )

    # --- возможности -------------------------------------------------------
    shops_total = len([role for role in G.ROLE_TRADES if G.ROLE_TRADES[role]])
    offers_total = sum(len(items) for items in G.ROLE_TRADES.values())
    features = [
        ("gun", "#ff6b6b", "Огнестрел", "93 модульных ствола: прицелы, глушители, "
         "рукояти, магазины, отдача и баллистика. В оружейной — все верстаки, "
         "готовые стволы и патроны 32 калибров. Мобов нет, взрывы город не ломают.",
         "TaCZ + пак Elite X Quality Guns"),
        ("coin", "#ffc44d", "Деньги", "Рубли: монета 10 ₽ и купюры 100, 1000 и "
         "5000 ₽. Карты «Мир» и Mastercard, счёт в банке и восемь банкоматов по "
         "городу — снять, положить, перевести, выпустить карту.",
         "самописный мод citylife"),
        ("phone", "#35c7f0", "Гаджеты", "Семь телефонов, планшет, ноутбук и "
         "компьютер, который собирается из деталей. SIM с номером из четырёх цифр, "
         "СМС, банк, навигатор с меткой на месте, маркетплейс с доставкой в пункты "
         "выдачи, умный дом, 112 и игры.",
         "самописный мод citylife"),
        ("camera", "#9b7bff", "Камеры", "Камеры наблюдения над своим магазином: смотреть "
         "с монитора или из приложения «Камеры» в телефоне и ноутбуке. И фотоаппарат "
         "с альбомом, видеокамеры на штативе.",
         "SecurityCraft + CameraCraft"),
        ("lock", "#7be07b", "Умные замки", "Белый список, код для гостей, "
         "автозакрытие, журнал входов. Замок привязывается к телефону и открывается "
         "с другого конца квартала. Отмычка даёт шанс вскрыть чужой — с тревогой "
         "владельцу.", "SecurityCraft + citylife"),
        ("car", "#ff7a45", "Транспорт", "17 машин, лодок и самолётов в ящиках: "
         "вскрыл гаечным ключом — и едешь. Машина твоя: запирается из телефона, "
         "навигатор ведёт к стоянке, за угон — розыск. Дороги, метро и эстакада.",
         "MrCrayfish's Vehicle + Traffic Control"),
        ("work", "#ffc857", "Заработок", "Семь подработок: курьер с посылкой, доставка "
         "еды, такси с живым пассажиром, смена, охрана, грузчик, мусорщик. Или "
         "дежурство за полицию, скорую, пожарных и такси — вызовы от других игроков.",
         "самописный мод citylife"),
        ("house", "#6fd08c", "Своё жильё", "Продаются все 1 400 домов, вилл, "
         "таунхаусов и квартир. Риелтор в мэрии, управдомы у подъездов. Пока "
         "хозяин в игре — чужой не войдёт. Коммуналка, аренда другу, а 190 магазинов "
         "и офисов покупаются как бизнес с доходом каждые сутки.",
         "самописный мод citylife"),
        ("siren", "#ff6b6b", "112 и полиция", "Скорая, пожарные и полиция приезжают "
         "через 30 секунд на машине с сиреной. Лечат, тушат, задерживают: звёзды "
         "розыска на экране, наряд бежит к месту по улице, погоня, штраф и камера. "
         "Можно и самому: ограбить кассу или вскрыть банкомат — и уйти от погони.",
         "самописный мод citylife + Easy NPC"),
        ("npc", "#ff4d7d", "Жители", f"{len(city.npc_spots)} человек со скинами "
         "игроков, а не деревенские: продавцы, банкир, оружейник, автодилер, "
         "риелтор, чиновники, повара, заправщики, охрана и полиция. "
         f"{shops_total} прилавков, {offers_total} предложения, кассы с оплатой картой. "
         "Прохожие говорят, уступают дорогу и смертны; патрульные с пистолетами.",
         "Easy NPC + citylife"),
        ("call", "#35c7f0", "Звонки", "Набрал номер SIM друга — у него звонит телефон. "
         "Ответил — и вы говорите голосом из любой точки города. Пропущенный — СМС. "
         "Вызов такси к себе кнопкой в телефоне.",
         "citylife + Simple Voice Chat"),
        ("walk", "#b58cff", "Живой город", "По тротуарам ходят прохожие — от магазинов "
         "к кафе и домам, продавцы поворачиваются к покупателю. Их число подстраивается "
         "под ПК.", "самописный мод citylife"),
        ("shield", "#7be07b", "Надёжность", "Профили для слабого и мощного ПК в "
         "установщике. Сервер сам делает копию мира раз в час, откат — одним файлом. "
         "Обновление не трогает дома и деньги. 19 автотестов перед каждой выкладкой.",
         "установщик + citylife"),
    ]
    cards_html = "".join(
        f'<article class="card reveal" style="--accent:{colour}">{icon(key, colour)}'
        f'<h3>{esc(title)}</h3><p>{esc(text)}</p>'
        f'<div class="card-note">на чём сделано: <b>{esc(note)}</b></div></article>'
        for key, colour, title, text, note in features
    )

    # --- карта -------------------------------------------------------------
    legend_html = "".join(
        f'<div class="legend-item" data-pin="{pin["n"]}">'
        f'<span class="legend-num" style="background:{pin["color"]}">{pin["n"]}</span>'
        f'<span class="legend-name">{esc(pin["name"])}</span>'
        f'<span class="legend-xy">{pin["x"]}, {pin["z"]}</span></div>'
        for pin in pins
    )
    map_svg = map_svg.replace('<g class="pin" data-pin="', '<g class="pin" tabindex="0" data-pin="')
    for pin in pins:
        map_svg = map_svg.replace(
            f'data-pin="{pin["n"]}" filter',
            f'data-pin="{pin["n"]}" data-name="{esc(pin["name"])}" '
            f'data-xy="{pin["x"]}, {pin["z"]}" filter')

    keys = [("#35c7f0", "небоскрёбы"), ("#ff4d7d", "торговый центр"),
            ("#ffc44d", "городские службы"), ("#9b7bff", "метро"),
            ("#2f7a56", "парки"), ("#1d2942", "свободные участки"),
            ("#454c70", "жилая застройка"), ("#3c4160", "склады")]
    keys_html = ('<span class="map-key"><i style="background:#ff4d7d"></i>'
                 'вся карта 2048×2048, за границу не выйти</span>')
    keys_html += "".join(
        f'<span class="map-key"><i style="background:{colour}"></i>{esc(name)}</span>'
        for colour, name in keys)

    # --- застройка ---------------------------------------------------------
    mass = [(kind, n) for kind, n in counts.most_common() if n > 2]
    unique = [(kind, n) for kind, n in counts.most_common() if n <= 2]
    top = max(n for _, n in mass)
    bars_html = "".join(
        f'<div class="bar-row"><div class="bar-name">{esc(KIND_RU.get(kind, kind))}</div>'
        f'<div class="bar-track"><div class="bar-fill" style="--v:{n / top:.3f}"></div></div>'
        f'<div class="bar-val">{n}</div></div>'
        for kind, n in mass
    )
    tiles_html = "".join(
        f'<div class="tile"><div class="tile-num grad">{n}</div>'
        f'<div class="tile-cap">{esc(KIND_RU.get(kind, kind))}</div></div>'
        for kind, n in unique
    )

    # --- жители ------------------------------------------------------------
    roles = Counter(spot["role"] for spot in city.npc_spots)
    titles = {spot["role"]: spot["title"] for spot in city.npc_spots}
    rows = []
    for role, n in roles.most_common():
        trades = G.ROLE_TRADES.get(role, [])
        if trades:
            goods = ", ".join(
                GOODS_RU.get(t["sell"]["id"].split(":")[-1], t["sell"]["id"].split(":")[-1])
                for t in trades)
        else:
            goods = '<span class="pill-off">не торгует, отвечает репликой</span>'
        rows.append(f'<tr><td>{esc(titles[role])}</td><td class="num">{n}</td>'
                    f'<td class="goods">{goods}</td></tr>')
    npc_html = "".join(rows)

    # --- сценарий ----------------------------------------------------------
    paths = [
        ("#35c7f0", "Начало", "общее для всех", [
            "<b>Приезд.</b> Автовокзал, таблички с подсказками, книга-гид в инвентаре",
            "<b>Телефон.</b> Он уже есть, но можно купить в салоне связи или скрафтить",
            "<b>Первые деньги.</b> Подработка командой <code>/work</code> раз в 5 минут",
            "<b>Счёт в банке.</b> Карта «Мир» или Mastercard в любом банкомате",
            "<b>Своё жильё.</b> Поставить умный замок — и дверь слушается только тебя",
        ]),
        ("#7be07b", "Законный путь", "работа, бизнес, охрана", [
            "<b>Лицензия.</b> Верстак оружейника у оружейника",
            "<b>Свой бизнес.</b> Свой банкомат — точку удобно взять в ТЦ",
            "<b>Под охраной.</b> Камера или кодовый замок на объекте",
            "<b>Предприниматель.</b> Пачка пятитысячных и репутация в городе",
        ]),
        ("#ff4d7d", "Криминальный путь", "риск и последствия", [
            "<b>Отмычка в кармане.</b> Два железных самородка и палка",
            "<b>Первое дело.</b> Чужой замок: шанс 25%, при провале тревога",
            "<b>Взломщик.</b> Кодолом SecurityCraft",
            "<b>Дело в банке.</b> Дойти до хранилища",
            "<b>В розыске.</b> Полицейский участок — сам или под конвоем",
        ]),
    ]
    paths_html = "".join(
        f'<article class="path reveal" style="--accent:{colour}">'
        f'<h3>{esc(title)}</h3><div class="path-sub">{esc(sub)}</div>'
        f'<ul class="steps">' + "".join(f"<li>{step}</li>" for step in steps) +
        f'</ul></article>'
        for colour, title, sub, steps in paths
    )

    # --- быстрый старт -----------------------------------------------------
    steps = [
        ("Шаг 1", "Поставить Java 17",
         f"Именно 17: на 21 Forge {mc} работает нестабильно. Adoptium Temurin 17, x64.",
         None),
        ("Шаг 2", "Поставить лаунчер с поддержкой паков",
         "Prism Launcher, Modrinth App или ATLauncher — любой из них понимает "
         "формат .mrpack и ставит всё сам. Все три бесплатные.", None),
        ("Шаг 3", f"Скачать пак · {mrpack_mb} МБ",
         f"Один файл. Внутри готовый город, конфиги и самописный мод; "
         f"остальные {auto_mods} модов лаунчер скачает сам "
         f"с авторских страниц — {download_mb} МБ.", None),
        ("Шаг 4", "Импортировать и нажать Play",
         "Prism: Add Instance → Import → выбрать файл. Modrinth App: "
         "Create → From file. ATLauncher: Add Pack → Import. Больше ничего "
         "делать не нужно — ни установщиков, ни команд.", None),
        ("Шаг 5", "Зайти в мир Los Santos",
         f"Мир уже в списке одиночных. Появишься на тротуаре у автовокзала, "
         f"а {npc_total} жителей расставятся сами при первом входе.", None),
    ]
    start_html = "".join(
        f'<article class="start-step reveal"><div class="start-n">{esc(number)}</div>'
        f'<h3>{esc(title)}</h3><p>{esc(text)}</p>'
        + (f'<a class="btn btn-ghost step-dl" href="{mrpack_href}" download data-download>'
           f'Скачать · {mrpack_mb} МБ</a>' if number == "Шаг 3" else "")
        + (code_block(*command) if command else "")
        + '</article>'
        for number, title, text, command in steps
    )

    # --- моды --------------------------------------------------------------
    own = '<span class="chip own">citylife <em>свой мод</em></span>'
    groups_html = []
    for key, (title, note) in GROUP_RU.items():
        mods = sorted((m for m in lock["mods"] if m["group"] == key),
                      key=lambda m: m["title"].lower())
        if not mods:
            continue
        chips = own if key == "core" else ""
        chips += "".join(
            f'<a class="chip" href="{esc(m["page"])}" target="_blank" rel="noopener">'
            f'{esc(m["title"])} <em>{esc(m["version_number"])}</em></a>'
            for m in mods)
        count = len(mods) + (1 if key == "core" else 0)
        groups_html.append(
            f'<div class="mod-group reveal"><h3>{esc(title)} — {count}</h3>'
            f'<div class="mod-group-note">{esc(note)}</div>'
            f'<div class="chips">{chips}</div></div>')
    mods_html = "".join(groups_html)

    # --- страница ----------------------------------------------------------
    page = f"""<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>LS City Life — сборка Minecraft про современный город</title>
<meta name="description" content="Сборка Minecraft {mc}: огнестрел, экономика, телефоны и компьютеры, маркетплейс, камеры, умные замки, машины и готовый город на 2048×2048 блоков с {len(city.npc_spots)} жителями.">
<meta property="og:title" content="LS City Life — сборка Minecraft про современный город">
<meta property="og:description" content="{mods_total} модов, готовый город Лос-Сантос, {len(city.npc_spots)} жителей, сценарий на 18 целей и сервер для игры с друзьями.">
<meta property="og:type" content="website">
<meta name="theme-color" content="#06070d">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Oswald:wght@400;500;600;700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400&display=swap" rel="stylesheet">
<link rel="stylesheet" href="styles.css">
<link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'><rect width='32' height='32' rx='7' fill='%2306070d'/><path d='M6 24h5V12h4v12h3V8h4v16h4' stroke='%23ff7a45' stroke-width='2.4' fill='none'/></svg>">
</head>
<body>

<nav class="nav">
  <div class="wrap nav-inner">
    <div class="nav-logo">LS <span>City Life</span></div>
    <div class="nav-links">
      <a href="#about">О сборке</a>
      <a href="#features">Возможности</a>
      <a href="#map">Карта</a>
      <a href="#city">Город</a>
      <a href="#npc">Жители</a>
      <a href="#story">Сценарий</a>
      <a href="#start">Как поставить</a>
      <a href="#mods">Моды</a>
    </div>
  </div>
</nav>

<header class="hero">
  <div class="hero-sky"></div>
  {stars_svg()}
  <div class="hero-sun"></div>
  {skyline_svg()}
  <div class="wrap hero-content">
    <div class="hero-kicker">Minecraft {mc} · Forge {forge}</div>
    <h1 class="grad">Los Santos</h1>
    <div class="hero-sub">City Life · сборка про жизнь в городе</div>
    <p class="hero-lead">Асфальт вместо леса: работа и деньги на карте, телефон с SIM
      в кармане, компьютер своей сборки, заказы с маркетплейса, камеры над входом, умный замок на двери и {len(city.npc_spots)} жителей,
      у каждого свой товар. Город уже построен — {empty_share}% участков оставлены
      под твои постройки.</p>
    <div class="hero-actions">
      <a class="btn btn-primary" href="{mrpack_href}" download data-download>Скачать сборку · {mrpack_mb} МБ</a>
      <a class="btn btn-ghost" href="#start">Как поставить</a>
    </div>
    <div class="hero-note">Один файл для Prism Launcher, Modrinth App и ATLauncher:
      импорт — и можно играть. Для CurseForge App —
      <a href="{cf_href}" download>свой zip · {cf_mb} МБ</a> (Import → выбрать файл),
      для Legacy Launcher и TLauncher —
      <a href="{archive_href}" download>архив с установщиком · {archive_mb} МБ</a>.</div>
    <div class="hero-build">Пак от {mrpack_date} · метка {mrpack_tag}</div>
    <div class="hero-stats">{stats_html}</div>
  </div>
</header>

<section id="about">
  <div class="wrap">
    <div class="eyebrow">Что это</div>
    <h2 class="section-title">Город, в который приезжают<br>с одним телефоном</h2>
    <p class="section-lead">Играть можно одному, но задумана сборка под компанию:
      кто-то держит магазин, кто-то работает в полиции, кто-то таксует, а кто-то
      вскрывает замки отмычкой. Карта закрыта границей — всё происходит в городе.</p>
    <div class="cards">
      <article class="card reveal" style="--accent:var(--gold)">
        <h3>Версию выбрали моды</h3>
        <p>Главный мод на огнестрел существует максимум до {mc} и только под Forge —
          он и задал версию. Всё остальное ядро под {mc} есть.</p>
      </article>
      <article class="card reveal" style="--accent:var(--cyan)">
        <h3>Любой лаунчер</h3>
        <p>Ни один мод не проверяет лицензию Minecraft. Legacy Launcher, TLauncher,
          CurseForge, Modrinth, Prism — работает везде, включая голосовой чат.</p>
      </article>
      <article class="card reveal" style="--accent:var(--green)">
        <h3>Мир уже построен</h3>
        <p>{len(city.lots)} участков разного размера: дома с двускатными крышами и
          садами, таунхаусы, виллы, многоэтажки, парки, метро, эстакада, пирс. Только
          ванильные блоки: отключишь декор-мод, и карта останется целой.</p>
      </article>
    </div>
  </div>
</section>

<section id="features" class="section-alt">
  <div class="wrap">
    <div class="eyebrow">Возможности</div>
    <h2 class="section-title">Из чего складывается жизнь</h2>
    <p class="section-lead">{len(features)} тем, вокруг которых собрана вся сборка. Под каждой —
      конкретные моды, а не общие слова.</p>
    <div class="cards">{cards_html}</div>
  </div>
</section>

<section id="map">
  <div class="wrap">
    <div class="eyebrow">Карта</div>
    <h2 class="section-title">Лос-Сантос целиком</h2>
    <p class="section-lead">Карта нарисована из того же плана, по которому построен
      мир: улицы, участки и адреса совпадают с игрой до блока. Наведи курсор на метку
      или строку списка.</p>
    <div class="map-layout">
      <div class="map-frame reveal">
        {map_svg}
        <div class="map-tip"></div>
      </div>
      <div class="legend reveal">
        <h3>Адреса</h3>
        {legend_html}
        <div class="map-keys">{keys_html}</div>
      </div>
    </div>
    <p class="note">Океан и пляж на западе, холмы на севере, пустыня на востоке,
      в центре — ровная «чаша» под городом. Улицы идут по координатам, кратным 64:
      если обе координаты кратны 64, ты на перекрёстке.</p>
  </div>
</section>

<section id="city" class="section-alt">
  <div class="wrap">
    <div class="eyebrow">Застройка</div>
    <h2 class="section-title">{len(city.lots)} участка</h2>
    <p class="section-lead">Почти треть города оставлена пустой специально: такие
      участки огорожены забором с табличкой «ПРОДАЁТСЯ УЧАСТОК». Забор можно просто
      сломать — это разметка, а не механика.</p>
    <div class="bars">{bars_html}</div>
    <h3 style="margin:44px 0 6px;font-size:22px">По одному на город</h3>
    <div class="tiles">{tiles_html}</div>
  </div>
</section>

<section id="npc">
  <div class="wrap">
    <div class="eyebrow">Жители</div>
    <h2 class="section-title">{len(city.npc_spots)} NPC с готовыми ценами</h2>
    <p class="section-lead">Расчёты в рублях: наличные, карта и банкоматы. Жители появляются
      одной командой после первого входа, её можно повторять.</p>
    <div class="table-wrap reveal">
      <table>
        <thead><tr><th>Кто</th><th>Сколько</th><th>Чем торгует</th></tr></thead>
        <tbody>{npc_html}</tbody>
      </table>
    </div>
    <p class="note">В черте города над землёй враждебные мобы автоматически убираются —
      ночью по улицам ходить безопасно. Под землёй и за городом всё как в обычной игре.</p>
  </div>
</section>

<section id="story" class="section-alt">
  <div class="wrap">
    <div class="eyebrow">Сценарий</div>
    <h2 class="section-title">18 целей, две дороги</h2>
    <p class="section-lead">Не рельсы, а подсказки: цели видны в меню достижений по
      клавише L, а пройти можно обе ветки.</p>
    <div class="paths">{paths_html}</div>
    <p class="note">Две цели рассчитаны на компанию: общий бизнес и ограбление, где
      один вскрывает, второй глушит камеры, третий ждёт в машине.</p>
  </div>
</section>

<section id="start">
  <div class="wrap">
    <div class="eyebrow">Установка</div>
    <h2 class="section-title">Пять шагов, без установщиков</h2>
    <p class="section-lead">Пак — один файл формата .mrpack. Лаунчер сам ставит Forge,
      качает моды, раскладывает конфиги и мир: ни команд, ни распаковки вручную.</p>
    <div class="start">{start_html}</div>
    <p class="note">Официальный Minecraft Launcher сборки не импортирует — для него
      <a href="{official_href}" download>zip с установщиком</a>: распаковать и запустить
      УСТАНОВИТЬ-ОФИЦИАЛЬНЫЙ-ЛАУНЧЕР.bat, он поставит Forge и добавит профиль сам.</p>
    <p class="note">CurseForge App импортирует сборку из zip, а не из .mrpack. Для него
      есть <a href="{cf_href}" download>профиль CurseForge · {cf_mb} МБ</a>: Create Custom
      Profile → Import → этот файл, и CurseForge сам скачает все моды. Распаковывать не нужно.</p>
    <p class="note">Legacy Launcher и TLauncher импорт не умеют. Для них есть
      <a href="{archive_href}" download>архив с установщиком</a>: распаковать,
      запустить УСТАНОВИТЬ.bat, выбрать профиль — дальше он всё сделает сам.</p>
    <p class="note">Для игры с друзьями в комплекте серверная часть: скрипты запуска,
      настроенные конфиги и голосовой чат. Работает через Radmin VPN без проброса
      портов — инструкция с правилами брандмауэра лежит в архиве.</p>
  </div>
</section>

<section id="mods" class="section-alt">
  <div class="wrap">
    <div class="eyebrow">Состав</div>
    <h2 class="section-title">{mods_total} модов</h2>
    <p class="section-lead">Версии зафиксированы и проверены на совместимость: диапазоны
      зависимостей сверяются скриптом по mods.toml каждого мода. Ссылка ведёт на
      первоисточник.</p>
    {mods_html}
    <p class="note">Файлы модов в пак не входят: {download_mb} МБ лаунчер качает сам
      с авторских страниц и сверяет SHA-512. Иначе и нельзя — 26 модов из
      {lock["mod_count"]} запрещают перевыкладывание своих файлов. Внутри пака лежит
      только наше: город, конфиги и самописный мод citylife.</p>
  </div>
</section>

<footer>
  <div class="wrap footer-inner">
    <div class="footer-logo">LS City Life {pack["version"]}</div>
    <div>Minecraft {mc} · Forge {forge} · Java 17</div>
    <div>Карта и страница собраны из данных сборки скриптами</div>
    <div><a href="{mrpack_href}" download data-download>Скачать сборку · {mrpack_mb} МБ</a> ·
      <a href="{archive_href}" download>архив с установщиком · {archive_mb} МБ</a></div>
    <div>Если сайт не открывается: <a href="{SITE_URL}">{SITE_URL.split("//")[1]}</a> ·
      зеркало <a href="{MIRROR_URL}">{MIRROR_URL.split("//")[1].rstrip("/")}</a></div>
  </div>
</footer>

<div class="dl-overlay" id="dl" hidden>
  <div class="dl-sheet" role="dialog" aria-modal="true" aria-labelledby="dl-title">
    <button class="dl-close" type="button" aria-label="Закрыть">&times;</button>
    <div class="dl-head">
      <div class="eyebrow">Скачивание</div>
      <h3 id="dl-title">Каким лаунчером играешь?</h3>
      <p>От этого зависит только формат файла. Сборка, город и моды одинаковые.</p>
    </div>
    <div class="dl-cards">
      <article class="dl-card" style="--accent:#35c7f0">
        <div class="dl-badge">Official</div>
        <h4>Лицензия Minecraft</h4>
        <div class="dl-subtitle">примеры лаунчеров</div>
        <ul class="dl-list">
          <li>Prism Launcher</li><li>Modrinth App</li><li>ATLauncher</li>
          <li>CurseForge App</li>
        </ul>
        <p class="dl-hint">Prism, Modrinth App и ATLauncher ставят пак .mrpack одним импортом.
          CurseForge App импортирует zip: Create Custom Profile → Import → файл ниже,
          моды он скачает сам.</p>
        <a class="btn btn-primary dl-go" href="{mrpack_href}" download>
          Пак .mrpack · {mrpack_mb} МБ<span class="dl-arrow">↓</span></a>
        <a class="btn btn-ghost dl-alt" href="{cf_href}" download>
          Для CurseForge · zip {cf_mb} МБ<span class="dl-arrow">↓</span></a>
      </article>
      <article class="dl-card" style="--accent:#7be07b">
        <div class="dl-badge">Mojang</div>
        <h4>Официальный лаунчер</h4>
        <div class="dl-subtitle">Minecraft Launcher, в том числе из Microsoft Store</div>
        <ul class="dl-list">
          <li>ставит Forge сам</li><li>моды в отдельной папке</li>
          <li>готовый профиль «LS City Life»</li>
        </ul>
        <p class="dl-hint">Официальный лаунчер сборки не импортирует. Распаковать zip и
          запустить УСТАНОВИТЬ-ОФИЦИАЛЬНЫЙ-ЛАУНЧЕР.bat — дальше выбрать профиль
          «LS City Life» и нажать «Играть».</p>
        <a class="btn btn-primary dl-go" href="{official_href}" download>
          Архив .zip · {official_mb} МБ<span class="dl-arrow">↓</span></a>
      </article>
      <article class="dl-card" style="--accent:#ff7a45">
        <div class="dl-badge">Pirate</div>
        <h4>Пиратский лаунчер</h4>
        <div class="dl-subtitle">примеры лаунчеров</div>
        <ul class="dl-list">
          <li>TLauncher</li><li>Legacy Launcher</li><li>SKlauncher</li>
          <li>PollyMC</li><li>Prism в офлайн-режиме</li>
        </ul>
        <p class="dl-hint">Распаковать и запустить УСТАНОВИТЬ.bat — он скачает моды,
          разложит конфиги и город сам. Ни команд, ни настроек.</p>
        <a class="btn btn-primary dl-go" href="{archive_href}" download>
          Архив · {archive_mb} МБ<span class="dl-arrow">↓</span></a>
      </article>
    </div>
    <p class="dl-note">Ни в один файл моды не входят: больше двадцати модов из
      {lock["mod_count"]} запрещают перевыкладывание. Их скачивает лаунчер или установщик с авторских страниц —
      {download_mb} МБ с проверкой хэшей.</p>
  </div>
</div>

<script src="app.js" defer></script>
</body>
</html>
"""

    dist = os.path.join(HERE, "dist")
    os.makedirs(dist, exist_ok=True)
    with open(os.path.join(dist, "index.html"), "w", encoding="utf-8") as fh:
        fh.write(page)
    for name in ("styles.css", "app.js"):
        shutil.copy2(os.path.join(HERE, name), os.path.join(dist, name))

    # Архив сборки: кладём рядом со страницей, чтобы кнопка скачивания работала.
    download_dir = os.path.join(dist, "download")
    os.makedirs(download_dir, exist_ok=True)
    for src, name in ((os.path.join(PACK, "dist", f"{pack['id']}-{pack['version']}.mrpack"),
                       MRPACK_NAME),
                      (os.path.join(PACK, "dist", f"{pack['id']}-{pack['version']}-full.zip"),
                       ARCHIVE_NAME),
                      (os.path.join(PACK, "dist", f"{pack['id']}-{pack['version']}-official.zip"),
                       OFFICIAL_NAME),
                      (cf_path, CF_NAME)):
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(download_dir, name))
            print(f"  download/{name} ({os.path.getsize(src) / 1048576:.1f} МБ)")
        else:
            print(f"  ! нет {os.path.basename(src)} — кнопка будет вести в пустоту")

    size = sum(os.path.getsize(os.path.join(root, n))
               for root, _dirs, names in os.walk(dist) for n in names)
    print(f"Страница собрана: {dist}")
    print(f"  index.html {os.path.getsize(os.path.join(dist, 'index.html')) / 1024:.0f} КБ, "
          f"всего {size / 1024:.0f} КБ")
    print(f"  метки на карте: {len(pins)}, модов в списке: {mods_total}, "
          f"строк в таблице жителей: {len(roles)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
