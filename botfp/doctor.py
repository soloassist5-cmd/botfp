"""Предполётная проверка: всё ли готово к боевому запуску.

Смысл — поймать проблемы до того, как их увидит первый покупатель:
пустой склад у активного лота, лот без образца формы, забытый ключ в
конфиге, зависшие заказы с прошлого запуска.
"""

from __future__ import annotations

import os
import sqlite3
import stat as stat_module
from dataclasses import dataclass
from pathlib import Path

from . import stock
from .config import Config
from .listings import get_link
from .transport import TransportError

OK = "ok"
WARN = "warn"
FAIL = "fail"

MARKS = {OK: "✓", WARN: "⚠", FAIL: "✗"}


@dataclass(frozen=True)
class Check:
    level: str
    title: str
    detail: str = ""

    def render(self) -> str:
        line = f"{MARKS[self.level]} {self.title}"
        return f"{line}\n    {self.detail}" if self.detail else line


def run_checks(
    conn: sqlite3.Connection,
    config: Config,
    config_path: Path,
    *,
    lots_api: object | None = None,
) -> list[Check]:
    """Собирает отчёт о готовности. ``lots_api`` включает проверки по сети."""
    checks: list[Check] = []
    checks += _check_secrets(config_path)
    checks += _check_database(conn, config)
    checks += _check_lots(conn, config)
    checks += _check_pending(conn)
    checks += _check_telegram(config)
    if lots_api is not None:
        checks += _check_funpay(conn, config, lots_api)
    return checks


def _check_secrets(config_path: Path) -> list[Check]:
    checks = []

    if os.environ.get("FUNPAY_GOLDEN_KEY"):
        checks.append(Check(OK, "golden_key берётся из переменной окружения"))
    else:
        checks.append(
            Check(
                WARN,
                "golden_key лежит в файле конфига",
                "Это полный доступ к аккаунту. Надёжнее: export FUNPAY_GOLDEN_KEY='...'",
            )
        )

    if config_path.exists():
        mode = stat_module.S_IMODE(config_path.stat().st_mode)
        if mode & 0o077:
            checks.append(
                Check(
                    WARN,
                    f"config.toml доступен не только вам (права {mode:o})",
                    f"Исправить: chmod 600 {config_path}",
                )
            )
        else:
            checks.append(Check(OK, "права на конфиг в порядке"))

    return checks


def _check_database(conn: sqlite3.Connection, config: Config) -> list[Check]:
    checks = []
    path = Path(str(config.db_path))

    version = conn.execute("PRAGMA user_version").fetchone()[0]
    checks.append(Check(OK, f"база открыта, версия схемы {version}"))

    if path.exists():
        mode = stat_module.S_IMODE(path.stat().st_mode)
        if mode & 0o077:
            checks.append(
                Check(
                    WARN,
                    f"база доступна не только вам (права {mode:o})",
                    f"В ней лежит товар в открытом виде. Исправить: chmod 600 {path}",
                )
            )
        else:
            checks.append(Check(OK, "права на базу в порядке"))

    return checks


def _check_lots(conn: sqlite3.Connection, config: Config) -> list[Check]:
    checks = []
    counts = stock.available_counts(conn)

    for lot in config.lots.values():
        if lot.is_unlimited:
            checks.append(
                Check(OK, f"{lot.display}: безлимитный, товар задан в конфиге")
            )
            continue

        left = counts.get(lot.lot_id, 0)
        if left == 0:
            checks.append(
                Check(
                    WARN,
                    f"{lot.display}: склад пуст",
                    f"Загрузить: botfp stock add --lot {lot.lot_id} --file items.txt",
                )
            )
        elif left <= config.low_stock_threshold:
            checks.append(Check(WARN, f"{lot.display}: осталось {left} шт."))
        else:
            checks.append(Check(OK, f"{lot.display}: {left} шт. на складе"))

    _check_match_collisions(config, checks)
    return checks


def _check_match_collisions(config: Config, checks: list[Check]) -> None:
    """Ищет правила match, из-за которых заказ уедет не в тот лот."""
    for lot in config.lots.values():
        for needle in lot.match:
            resolved = config.lot_for_description(needle)
            if resolved is not None and resolved.lot_id != lot.lot_id:
                checks.append(
                    Check(
                        FAIL,
                        f"{lot.display}: правило «{needle}» перехватывает лот {resolved.display}",
                        "Заказ уедет не в тот лот. Сделайте правила различимыми.",
                    )
                )


def _check_pending(conn: sqlite3.Connection) -> list[Check]:
    pending = stock.pending_deliveries(conn)
    if not pending:
        return [Check(OK, "незавершённых выдач нет")]

    return [
        Check(
            WARN,
            f"незавершённых выдач: {len(pending)}",
            "Посмотреть: botfp pending | Повторить: botfp retry --all",
        )
    ]


def _check_telegram(config: Config) -> list[Check]:
    if config.telegram is None:
        return [
            Check(
                OK,
                "панель Telegram не настроена",
                "Управление через CLI и команды в чате FunPay. "
                "Включить панель: секция [telegram] в конфиге.",
            )
        ]

    checks = [
        Check(OK, f"панель Telegram включена, админов: {len(config.telegram.admin_ids)}")
    ]

    if not os.environ.get("TELEGRAM_BOT_TOKEN"):
        checks.append(
            Check(
                WARN,
                "токен Telegram лежит в файле конфига",
                "Надёжнее: export TELEGRAM_BOT_TOKEN='...'",
            )
        )

    return checks


def _check_funpay(conn: sqlite3.Connection, config: Config, lots_api) -> list[Check]:
    checks: list[Check] = []

    try:
        remote = list(lots_api.list_my_lots())
    except TransportError as exc:
        return [Check(FAIL, "не удалось получить объявления с FunPay", str(exc))]

    checks.append(Check(OK, f"связь с FunPay есть, объявлений на витрине: {len(remote)}"))

    for lot in config.listable_lots:
        link = get_link(conn, lot.lot_id)
        if link is not None and link.funpay_lot_id:
            state = "на витрине" if link.active else "снято с витрины"
            checks.append(Check(OK, f"{lot.display}: объявление #{link.funpay_lot_id}, {state}"))
        else:
            checks.append(
                Check(WARN, f"{lot.display}: объявление ещё не создано", "botfp lots sync --apply")
            )

    return checks


def worst_level(checks: list[Check]) -> str:
    if any(c.level == FAIL for c in checks):
        return FAIL
    if any(c.level == WARN for c in checks):
        return WARN
    return OK
