"""Загрузка и проверка конфигурации."""

from __future__ import annotations

import os
import tomllib
from dataclasses import dataclass
from pathlib import Path


class ConfigError(Exception):
    """Конфигурация отсутствует или заполнена неверно."""


@dataclass(frozen=True)
class LotConfig:
    """Лот FunPay, привязанный к складу."""

    lot_id: str
    title: str
    instructions: str = ""
    # Подстроки, по которым описание заказа с FunPay сопоставляется с этим лотом.
    # FunPay не присылает id лота в событии о заказе — только текст описания.
    match: tuple[str, ...] = ()

    @property
    def display(self) -> str:
        return f"{self.title} (#{self.lot_id})"

    def matches(self, description: str) -> bool:
        haystack = description.casefold()
        return any(needle.casefold() in haystack for needle in self.match)


@dataclass(frozen=True)
class Config:
    golden_key: str
    user_agent: str
    db_path: Path
    admins: tuple[str, ...]
    lots: dict[str, LotConfig]
    poll_interval: float = 4.0
    low_stock_threshold: int = 3
    ask_for_review: bool = True
    request_delay: float = 1.0

    def lot(self, lot_id: str) -> LotConfig | None:
        return self.lots.get(str(lot_id))

    def lot_for_description(self, description: str) -> LotConfig | None:
        """Определяет лот по тексту заказа с FunPay.

        Более длинные подстроки проверяются первыми, чтобы «Roblox Premium» не
        перехватывался общим правилом «Roblox».
        """
        candidates = sorted(
            self.lots.values(),
            key=lambda lot: max((len(m) for m in lot.match), default=0),
            reverse=True,
        )
        return next((lot for lot in candidates if lot.matches(description)), None)

    def is_admin(self, username: str) -> bool:
        return username.casefold() in {a.casefold() for a in self.admins}


DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/125.0 Safari/537.36"
)


def load(path: str | Path = "config.toml", *, require_golden_key: bool = True) -> Config:
    """Читает TOML-конфиг.

    Ключ ``golden_key`` можно не хранить в файле: переменная окружения
    ``FUNPAY_GOLDEN_KEY`` имеет приоритет над значением из конфига.
    """
    path = Path(path)
    if not path.exists():
        raise ConfigError(
            f"Файл конфигурации {path} не найден. "
            f"Скопируйте config.example.toml в {path} и заполните его."
        )

    with path.open("rb") as fh:
        raw = tomllib.load(fh)

    funpay = raw.get("funpay", {})
    golden_key = os.environ.get("FUNPAY_GOLDEN_KEY") or funpay.get("golden_key", "")
    if not golden_key and require_golden_key:
        raise ConfigError(
            "Не задан golden_key. Укажите его в [funpay] или в переменной "
            "окружения FUNPAY_GOLDEN_KEY."
        )

    bot = raw.get("bot", {})
    db_path = Path(bot.get("db_path", "botfp.db")).expanduser()

    admins = tuple(str(a).strip() for a in bot.get("admins", []) if str(a).strip())
    if not admins:
        raise ConfigError(
            "Не указан ни один админ в bot.admins — некому будет получать "
            "уведомления об окончании товара."
        )

    lots: dict[str, LotConfig] = {}
    for entry in raw.get("lots", []):
        lot_id = str(entry.get("id", "")).strip()
        title = str(entry.get("title", "")).strip()
        if not lot_id or not title:
            raise ConfigError("У каждого лота в [[lots]] должны быть id и title.")
        if lot_id in lots:
            raise ConfigError(f"Лот {lot_id} указан в конфиге дважды.")
        match = tuple(
            str(m).strip() for m in entry.get("match", [title]) if str(m).strip()
        )
        if not match:
            raise ConfigError(
                f"У лота {lot_id} пустой match — бот не сможет опознать заказ."
            )
        lots[lot_id] = LotConfig(
            lot_id=lot_id,
            title=title,
            instructions=str(entry.get("instructions", "")).strip(),
            match=match,
        )
    if not lots:
        raise ConfigError("Не описан ни один лот в [[lots]].")

    poll_interval = float(bot.get("poll_interval", 4.0))
    if poll_interval < 2.0:
        raise ConfigError(
            "poll_interval меньше 2 секунд — FunPay начнёт отдавать 429. "
            "Поставьте 3-5."
        )

    return Config(
        golden_key=golden_key,
        user_agent=funpay.get("user_agent", DEFAULT_USER_AGENT),
        db_path=db_path,
        admins=admins,
        lots=lots,
        poll_interval=poll_interval,
        low_stock_threshold=int(bot.get("low_stock_threshold", 3)),
        ask_for_review=bool(bot.get("ask_for_review", True)),
        request_delay=float(bot.get("request_delay", 1.0)),
    )
