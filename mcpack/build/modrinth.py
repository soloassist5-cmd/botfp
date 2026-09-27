"""Тонкий клиент Modrinth API с повторами и кэшем на диске."""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
UA = "ls-city-life-packbuilder/1.0 (github.com/soloassist5-cmd/botfp)"
CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")


def _cache_path(key: str) -> str:
    safe = urllib.parse.quote(key, safe="")
    return os.path.join(CACHE_DIR, safe + ".json")


def get(path: str, params: dict | None = None, *, use_cache: bool = True, retries: int = 4):
    """GET на Modrinth API. Возвращает разобранный JSON."""
    query = ""
    if params:
        query = "?" + urllib.parse.urlencode(
            {k: (json.dumps(v, separators=(",", ":")) if isinstance(v, (list, dict)) else v)
             for k, v in params.items()}
        )
    key = path + query
    cached = _cache_path(key)
    if use_cache and os.path.exists(cached):
        with open(cached, encoding="utf-8") as fh:
            return json.load(fh)

    url = API + "/" + urllib.parse.quote(path.lstrip("/"), safe="/") + query
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
            with urllib.request.urlopen(req, timeout=40) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            os.makedirs(CACHE_DIR, exist_ok=True)
            with open(cached, "w", encoding="utf-8") as fh:
                json.dump(data, fh)
            return data
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                raise
            last = exc
        except Exception as exc:  # сеть: таймаут, разрыв, DNS
            last = exc
        time.sleep(2 ** attempt)
    raise RuntimeError(f"Modrinth API недоступен: {url} ({last})")


def project(slug_or_id: str) -> dict:
    return get(f"project/{slug_or_id}")


def versions(slug_or_id: str, mc: str, loader: str) -> list[dict]:
    return get(f"project/{slug_or_id}/version",
               {"game_versions": [mc], "loaders": [loader]})


def version(version_id: str) -> dict:
    return get(f"version/{version_id}")
