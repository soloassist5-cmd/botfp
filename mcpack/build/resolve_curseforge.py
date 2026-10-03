#!/usr/bin/env python3
"""
Сопоставление модов из mods.lock.json с файлами на CurseForge.

Зачем: в пак CurseForge-формата jar-ы не кладутся — там manifest.json с парами
projectID/fileID, и CurseForge App скачивает моды сам, с авторских страниц.
Это единственный способ отдать готовый пак, не нарушая лицензии: 26 из 58 модов
запрещают перевыкладывание файлов.

Данные берём через api.cfwidget.com — публичное зеркало метаданных CurseForge
(сам CF API требует ключ, а его CDN из этого окружения отдаёт 403). Совпадение
считаем только по точному имени файла: та же версия мода, что уже проверена
по sha512 в mods.lock.json.
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "build", ".cfcache")
UA = "ls-city-life-packbuilder/1.0 (+https://ls-city-life.vercel.app)"
API = "https://api.cfwidget.com"


def get(url: str, tries: int = 8) -> dict | None:
    for attempt in range(1, tries + 1):
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=90) as resp:
                if resp.status == 202:          # проект встал в очередь на обход
                    time.sleep(8 * attempt)
                    continue
                return json.load(resp)
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                return None
            if exc.code in (202, 429, 500, 502, 503, 504):
                time.sleep(3 * attempt)
                continue
            return None
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            time.sleep(3 * attempt)
    return None


def project(slug: str, fresh: bool = False) -> dict | None:
    """Проект CurseForge по слагу, с кэшем на диске (fresh — мимо кэша)."""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, f"{slug}.json")
    if os.path.isfile(path) and not fresh:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        return data or None
    data = get(f"{API}/minecraft/mc-mods/{urllib.parse.quote(slug)}")
    # Промах не кэшируем: 202/429 от зеркала — это «подожди», а не «нет проекта»,
    # и закэшированный null отравил бы все следующие запуски.
    if data:
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh)
    time.sleep(1.5)
    return data


def search(title: str) -> list[dict]:
    query = urllib.parse.urlencode({"game": "minecraft", "category": "mc-mods", "name": title})
    data = get(f"{API}/search?{query}")
    time.sleep(1.0)
    return data if isinstance(data, list) else []


# Номера проектов CurseForge для модов, которые зеркало не находит по слагу
# (его поиск по адресу отвечает 404 даже на существующие проекты, а по номеру
# отдаёт). Номер — со страницы мода на curseforge.com.
CF_IDS = {
    "architectury-api": 419699, "balm": 531761, "moonlight": 499980,
    "ferrite-core": 429235, "simple-voice-chat": 416089, "entityculling": 448233,
    "chipped": 456956, "framedblocks": 441647, "macaws-lights-and-lamps": 502372,
    "macaws-fences-and-walls": 453925, "supermartijn642s-core-lib": 454372,
    "supermartijn642s-config-lib": 438332, "cameracraft": 819401,
}


def by_id(pid: int) -> dict | None:
    """Проект по номеру; кладётся в тот же кэш под своим слагом."""
    data = get(f"{API}/{pid}")
    if data:
        slug = (data.get("urls", {}).get("curseforge", "") or "").rstrip("/").split("/")[-1]
        if slug:
            os.makedirs(CACHE, exist_ok=True)
            with open(os.path.join(CACHE, f"{slug}.json"), "w", encoding="utf-8") as fh:
                json.dump(data, fh)
    time.sleep(1.5)
    return data


def slug_variants(mod: dict) -> list[str]:
    out = [mod["slug"]]
    title = mod["title"].lower()
    title = re.sub(r"\(.*?\)|\[.*?\]", " ", title)
    title = re.sub(r"[^a-z0-9]+", "-", title).strip("-")
    for variant in (title, title.replace("-mod", ""), mod["slug"].replace("-", "")):
        if variant and variant not in out:
            out.append(variant)
    return out


def match_file(data: dict, filename: str) -> dict | None:
    for entry in data.get("files") or []:
        if (entry.get("name") or "").lower() == filename.lower():
            return entry
    return None


def main() -> int:
    with open(os.path.join(ROOT, "mods.lock.json"), encoding="utf-8") as fh:
        lock = json.load(fh)

    found, missing = [], []
    for i, mod in enumerate(lock["mods"], 1):
        hit = None
        for slug in slug_variants(mod):
            data = project(slug)
            if not data:
                continue
            entry = match_file(data, mod["filename"])
            if not entry:
                # В кэше мог остаться снимок проекта до выхода нужного файла.
                data = project(slug, fresh=True) or data
                entry = match_file(data, mod["filename"])
            if entry:
                hit = (data, entry)
                break
        if not hit and mod["slug"] in CF_IDS:
            data = by_id(CF_IDS[mod["slug"]])
            entry = match_file(data, mod["filename"]) if data else None
            if entry:
                hit = (data, entry)
        if not hit:
            for candidate in search(mod["title"])[:4]:
                slug = (candidate.get("urls", {}).get("curseforge", "") or "").rstrip("/").split("/")[-1]
                if not slug:
                    continue
                data = project(slug, fresh=True)
                if not data:
                    continue
                entry = match_file(data, mod["filename"])
                if entry:
                    hit = (data, entry)
                    break
        if hit:
            data, entry = hit
            found.append({
                "slug": mod["slug"], "title": mod["title"], "filename": mod["filename"],
                "side": mod["side"], "optional": mod.get("optional", False),
                "projectID": data["id"], "fileID": entry["id"],
                "cf_url": data.get("urls", {}).get("curseforge", ""),
            })
            mark = "+"
        else:
            missing.append(mod)
            mark = "X"
        print(f"  [{i:2d}/{len(lock['mods'])}] {mark} {mod['title']}", flush=True)

    out = {
        "mc": lock.get("mc") or lock.get("minecraft"),
        "found": len(found), "missing": [m["title"] for m in missing],
        "mods": found,
    }
    with open(os.path.join(ROOT, "curseforge.lock.json"), "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
    print(f"\nнашлось на CurseForge: {len(found)} из {len(lock['mods'])}")
    if missing:
        print("не нашлось:")
        for m in missing:
            print(f"  - {m['title']} ({m['filename']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
