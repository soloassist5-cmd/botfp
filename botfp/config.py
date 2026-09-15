"""Загрузка и проверка конфигурации."""

from __future__ import annotations

import os
import tomllib
from dataclasses import dataclass
from pathlib import Path


class ConfigError(Exception):
    """Конфигурация отсутствует или заполнена неверно."""


# Типы лотов.
#   unique    — каждая единица уникальна и списывается при продаже (аккаунты, ключи).
#   unlimited — один и тот же товар уходит всем покупателям (гайд, ссылка, файл).
KIND_UNIQUE = "unique"
KIND_UNLIMITED = "unlimited"
KINDS = (KIND_UNIQUE, KIND_UNLIMITED)


@dataclass(frozen=True)
class ListingConfig:
    """Поля объявления на FunPay для автосоздания."""

    node_id: int
    price: float
    short_description: str
    description: str = ""
    # Снимать объявление с публикации, когда товар кончился, и возвращать
    # обратно после пополнения. Для безлимитных лотов не имеет смысла.
    auto_deactivate: bool = True
    amount: int | None = None


@dataclass(frozen=True)
class LotConfig:
    """Лот FunPay, привязанный к складу."""

    lot_id: str
    title: str
    kind: str = KIND_UNIQUE
    instructions: str = ""
    # Подстроки, по которым описание заказа с FunPay сопоставляется с этим лотом.
    # FunPay не присылает id лота в событии о заказе — только текст описания.
    match: tuple[str, ...] = ()
    # Для безлимитных лотов: то, что уходит покупателю. Для уникальных — None.
    payload: str | None = None
    listing: ListingConfig | None = None

    @property
    def display(self) -> str:
        return f"{self.title} (#{self.lot_id})"

    @property
    def is_unlimited(self) -> bool:
        return self.kind == KIND_UNLIMITED

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
    # Синхронизировать объявления при старте бота. По умолчанию выключено:
    # создание лотов меняет витрину магазина, это стоит запускать осознанно.
    sync_listings_on_start: bool = False
    # Пауза между запросами на изменение лотов, секунды.
    listing_delay: float = 3.0

    def lot(self, lot_id: str) -> LotConfig | None:
        return self.lots.get(str(lot_id))

    def is_admin(self, username: str) -> bool:
        return username.casefold() in {a.casefold() for a in self.admins}

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

    @property
    def listable_lots(self) -> tuple[LotConfig, ...]:
        """Лоты, для которых описано объявление."""
        return tuple(lot for lot in self.lots.values() if lot.listing is not None)


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
        lot = _parse_lot(entry, base_dir=path.parent)
        if lot.lot_id in lots:
            raise ConfigError(f"Лот {lot.lot_id} указан в конфиге дважды.")
        lots[lot.lot_id] = lot
    if not lots:
        raise ConfigError("Не описан ни один лот в [[lots]].")

    poll_interval = float(bot.get("poll_interval", 4.0))
    if poll_interval < 2.0:
        raise ConfigError(
            "poll_interval меньше 2 секунд — FunPay начнёт отдавать 429. "
            "Поставьте 3-5."
        )

    listing_delay = float(bot.get("listing_delay", 3.0))
    if listing_delay < 1.0:
        raise ConfigError(
            "listing_delay меньше секунды. Создание лотов пачкой без пауз "
            "выглядит как флуд — поставьте 3 и больше."
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
        sync_listings_on_start=bool(bot.get("sync_listings_on_start", False)),
        listing_delay=listing_delay,
    )


def _parse_lot(entry: dict, *, base_dir: Path) -> LotConfig:
    lot_id = str(entry.get("id", "")).strip()
    title = str(entry.get("title", "")).strip()
    if not lot_id or not title:
        raise ConfigError("У каждого лота в [[lots]] должны быть id и title.")

    kind = str(entry.get("kind", KIND_UNIQUE)).strip().lower()
    if kind not in KINDS:
        raise ConfigError(
            f"Лот {lot_id}: неизвестный kind «{kind}». Допустимые: {', '.join(KINDS)}."
        )

    match = tuple(str(m).strip() for m in entry.get("match", [title]) if str(m).strip())
    if not match:
        raise ConfigError(f"У лота {lot_id} пустой match — бот не сможет опознать заказ.")

    payload = _read_payload(entry, lot_id=lot_id, base_dir=base_dir)
    if kind == KIND_UNLIMITED and not payload:
        raise ConfigError(
            f"Лот {lot_id} безлимитный, но у него нет payload или payload_file — "
            f"нечего выдавать."
        )
    if kind == KIND_UNIQUE and payload:
        raise ConfigError(
            f"Лот {lot_id} уникальный: товар для него грузится через "
            f"`botfp stock add`, а не через payload в конфиге."
        )

    return LotConfig(
        lot_id=lot_id,
        title=title,
        kind=kind,
        instructions=str(entry.get("instructions", "")).strip(),
        match=match,
        payload=payload,
        listing=_parse_listing(entry, lot_id=lot_id, title=title, kind=kind),
    )


def _read_payload(entry: dict, *, lot_id: str, base_dir: Path) -> str | None:
    inline = entry.get("payload")
    file_ref = entry.get("payload_file")

    if inline and file_ref:
        raise ConfigError(f"Лот {lot_id}: укажите либо payload, либо payload_file.")
    if inline:
        return str(inline).strip() or None
    if not file_ref:
        return None

    candidate = Path(str(file_ref)).expanduser()
    if not candidate.is_absolute():
        candidate = base_dir / candidate
    if not candidate.exists():
        raise ConfigError(f"Лот {lot_id}: файл payload_file не найден — {candidate}")
    return candidate.read_text(encoding="utf-8").strip() or None


def _parse_listing(
    entry: dict, *, lot_id: str, title: str, kind: str
) -> ListingConfig | None:
    listing = entry.get("listing")
    if listing is None:
        return None
    if not isinstance(listing, dict):
        raise ConfigError(f"Лот {lot_id}: секция listing должна быть таблицей.")

    node_id = listing.get("node_id")
    price = listing.get("price")
    if node_id is None or price is None:
        raise ConfigError(
            f"Лот {lot_id}: в listing обязательны node_id (id подкатегории FunPay) "
            f"и price."
        )
    try:
        node_id = int(node_id)
        price = float(price)
    except (TypeError, ValueError) as exc:
        raise ConfigError(f"Лот {lot_id}: node_id должен быть числом, price — числом.") from exc

    if price <= 0:
        raise ConfigError(f"Лот {lot_id}: price должен быть больше нуля.")

    amount = listing.get("amount")
    if amount is not None:
        amount = int(amount)
        if amount <= 0:
            raise ConfigError(f"Лот {lot_id}: amount должен быть больше нуля.")

    return ListingConfig(
        node_id=node_id,
        price=price,
        short_description=str(listing.get("short_description", title)).strip(),
        description=str(listing.get("description", "")).strip(),
        # Безлимитный товар не кончается — снимать его с публикации нечего.
        auto_deactivate=bool(listing.get("auto_deactivate", True))
        and kind != KIND_UNLIMITED,
        amount=amount,
    )
