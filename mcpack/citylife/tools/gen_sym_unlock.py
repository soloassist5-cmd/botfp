#!/usr/bin/env python3
"""
Способности костюмов Sym's Armored Industries — открыты сразу.

В моде почти все способности костюма (сила, защита, юнибим, ракеты, щит,
репульсоры…) надо «покупать» очками уровня Железного человека: на панели
способностей у них замочки, работает только шлем. В городе костюм выдают
Джарвис и Зал брони — ждать прокачки незачем. Поэтому кладём в мод City Life
копии файлов сил Sym без условия symindustries_iron_man_level: всё остальное
(нужен полный костюм, реактор, энергия) остаётся как у автора.

Файлы лежат во встроенном датапаке packs/sym_unlock: City Life подключает его
поверх всех модов (SymUnlockPack), и Palladium берёт наш файл вместо файла Sym.

    python3 citylife/tools/gen_sym_unlock.py [путь/к/sym-industries.jar]
"""
from __future__ import annotations

import glob
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'packs', 'sym_unlock', 'data', 'sym_industries',
                   'palladium', 'powers')
LOCK = 'palladium:symindustries_iron_man_level'
# Марки 7 и 42 у автора — для подписчиков Patreon: их не трогаем.
SKIP = {'mk7.json', 'mk42.json'}
PREFIX = 'data/sym_industries/palladium/powers/'


def find_jar() -> str:
    if len(sys.argv) > 1:
        return sys.argv[1]
    for pattern in ('build/.cache/jars/sym-industries-*.jar', 'build/.cache/jars/sym_industries-*.jar'):
        found = sorted(glob.glob(os.path.join(ROOT, pattern)))
        if found:
            return found[-1]
    sys.exit('Не найден jar Sym\'s Armored Industries: запусти build/build_pack.py или укажи путь')


def is_lock(cond) -> bool:
    return isinstance(cond, dict) and cond.get('type') == LOCK


def strip(cond):
    """Условие без замка. None — от условия ничего не осталось (оно всегда истинно)."""
    if is_lock(cond):
        return None
    if isinstance(cond, list):
        rest = [c for c in (strip(c) for c in cond) if c is not None]
        return rest
    if isinstance(cond, dict) and cond.get('type') in ('palladium:and', 'palladium:or'):
        inner = cond.get('conditions', [])
        if cond['type'] == 'palladium:or' and any(is_lock(c) for c in inner):
            return None   # «или купи» — купить больше не нужно, ветка всегда открыта
        rest = [c for c in (strip(c) for c in inner) if c is not None]
        if not rest:
            return None
        return dict(cond, conditions=rest)
    return cond


def unlock(power: dict) -> int:
    count = 0
    for ability in power.get('abilities', {}).values():
        conditions = ability.get('conditions')
        if not isinstance(conditions, dict) or 'unlocking' not in conditions:
            continue
        before = json.dumps(conditions['unlocking'])
        if LOCK not in before:
            continue
        new = strip(conditions['unlocking'])
        conditions['unlocking'] = [] if new is None else new
        if not conditions['unlocking']:
            del conditions['unlocking']
        count += 1
    return count


def main() -> None:
    jar = find_jar()
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, '*.json')):
        os.remove(old)
    total = 0
    with zipfile.ZipFile(jar) as z:
        for name in sorted(z.namelist()):
            if not name.startswith(PREFIX) or not name.endswith('.json') or os.path.basename(name) in SKIP:
                continue
            text = z.read(name).decode('utf-8')
            if LOCK not in text:
                continue
            power = json.loads(text)
            n = unlock(power)
            if not n:
                continue
            assert LOCK not in json.dumps(power), name
            with open(os.path.join(OUT, os.path.basename(name)), 'w', encoding='utf-8') as f:
                json.dump(power, f, ensure_ascii=False, indent=1)
                f.write('\n')
            print(f'{os.path.basename(name)}: открыто способностей — {n}')
            total += n
    print(f'Всего: {total}')


if __name__ == '__main__':
    main()
