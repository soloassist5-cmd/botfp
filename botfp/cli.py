"""Командная строка: запуск бота и управление складом."""

from __future__ import annotations

import argparse
import logging
import sqlite3
import sys
from pathlib import Path

from . import __version__, config as config_module, db, stock
from .bot import Bot
from .config import Config, ConfigError
from .transport import ConsoleTransport

EXAMPLE_CONFIG = Path(__file__).resolve().parent.parent / "config.example.toml"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="botfp",
        description="Бот автовыдачи цифровых товаров на FunPay.",
    )
    parser.add_argument("--version", action="version", version=f"botfp {__version__}")
    parser.add_argument(
        "-c", "--config", default="config.toml", help="путь к конфигу (по умолчанию config.toml)"
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="подробные логи")

    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("init", help="создать config.toml из примера")

    run = sub.add_parser("run", help="запустить бота")
    run.add_argument(
        "--console",
        action="store_true",
        help="прогон в терминале без подключения к FunPay",
    )

    stock_cmd = sub.add_parser("stock", help="управление складом")
    stock_sub = stock_cmd.add_subparsers(dest="stock_command", required=True)

    add = stock_sub.add_parser("add", help="добавить товар на склад")
    add.add_argument("--lot", required=True, help="id лота из конфига")
    add.add_argument(
        "--file",
        required=True,
        help="файл со строками товара (одна строка — одна единица); «-» читает stdin",
    )

    stock_sub.add_parser("count", help="показать остатки")

    sub.add_parser("pending", help="заказы, требующие внимания")
    sub.add_parser("stats", help="сводка по складу и выдачам")

    retry = sub.add_parser("retry", help="повторить зависшую выдачу")
    group = retry.add_mutually_exclusive_group(required=True)
    group.add_argument("order_id", nargs="?", help="номер заказа")
    group.add_argument("--all", action="store_true", help="повторить все зависшие")

    release = sub.add_parser(
        "release", help="вернуть товар заказа на склад (отмена или возврат)"
    )
    release.add_argument("order_id", help="номер заказа")

    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    if args.command == "init":
        return cmd_init(args)

    # Ключ нужен только тем командам, которые реально ходят на FunPay.
    # Работа со складом и отчёты — чисто локальные, и просить ключ для них незачем.
    needs_funpay = args.command == "retry" or (args.command == "run" and not args.console)

    try:
        cfg = config_module.load(args.config, require_golden_key=needs_funpay)
    except ConfigError as exc:
        print(f"Ошибка конфигурации: {exc}", file=sys.stderr)
        return 2

    conn = db.connect(cfg.db_path)
    try:
        handlers = {
            "run": cmd_run,
            "stock": cmd_stock,
            "pending": cmd_pending,
            "stats": cmd_stats,
            "retry": cmd_retry,
            "release": cmd_release,
        }
        return handlers[args.command](args, cfg, conn)
    finally:
        conn.close()


# ----------------------------------------------------------------------


def cmd_init(args: argparse.Namespace) -> int:
    target = Path(args.config)
    if target.exists():
        print(f"{target} уже существует — не трогаю.", file=sys.stderr)
        return 1
    if not EXAMPLE_CONFIG.exists():  # pragma: no cover
        print(f"Не найден шаблон {EXAMPLE_CONFIG}", file=sys.stderr)
        return 1

    target.write_text(EXAMPLE_CONFIG.read_text(encoding="utf-8"), encoding="utf-8")
    target.chmod(0o600)
    print(
        f"Создан {target}. Заполните golden_key, admins и лоты, затем:\n"
        f"  botfp stock add --lot <id> --file items.txt\n"
        f"  botfp run"
    )
    return 0


def cmd_run(args: argparse.Namespace, cfg: Config, conn: sqlite3.Connection) -> int:
    if args.console:
        print(
            "Консольный режим — FunPay не задействован.\n" + ConsoleTransport.HELP,
            file=sys.stderr,
        )
        transport = ConsoleTransport()
        bot = Bot(conn, cfg, transport, self_username="bot")
        try:
            while True:
                bot.run_once()
        except SystemExit:
            return 0

    from .funpay_transport import FunPayTransport
    from .transport import TransportError

    transport = FunPayTransport(cfg)
    try:
        username = transport.connect()
        transport.start()
    except TransportError as exc:
        print(f"Ошибка подключения: {exc}", file=sys.stderr)
        return 3

    bot = Bot(conn, cfg, transport, self_username=username)
    try:
        bot.run_forever()
    finally:
        transport.stop()
    return 0


def cmd_stock(args: argparse.Namespace, cfg: Config, conn: sqlite3.Connection) -> int:
    if args.stock_command == "count":
        return _print_stock(cfg, conn)

    if cfg.lot(args.lot) is None:
        known = ", ".join(cfg.lots) or "нет ни одного"
        print(f"Лот {args.lot} не описан в конфиге. Известные: {known}", file=sys.stderr)
        return 2

    if args.file == "-":
        lines = sys.stdin.read().splitlines()
    else:
        path = Path(args.file)
        if not path.exists():
            print(f"Файл {path} не найден.", file=sys.stderr)
            return 2
        lines = path.read_text(encoding="utf-8").splitlines()

    result = stock.add_items(conn, args.lot, lines)
    print(
        f"Добавлено: {result.added}. "
        f"Пропущено дубликатов: {result.duplicates}, пустых строк: {result.blank}."
    )
    print(f"Теперь в наличии по лоту {args.lot}: {stock.available_count(conn, args.lot)} шт.")
    return 0


def _print_stock(cfg: Config, conn: sqlite3.Connection) -> int:
    counts = stock.available_counts(conn)
    for lot in cfg.lots.values():
        print(f"{lot.display}: {counts.get(lot.lot_id, 0)} шт.")
    return 0


def cmd_pending(args: argparse.Namespace, cfg: Config, conn: sqlite3.Connection) -> int:
    pending = stock.pending_deliveries(conn)
    if not pending:
        print("Зависших заказов нет.")
        return 0

    print(f"Требуют внимания: {len(pending)}")
    for d in pending:
        error = f" — {d.error}" if d.error else ""
        print(f"  #{d.order_id}  {d.buyer:<20} лот {d.lot_id:<16} {d.status}{error}")
    print("\nПовторить выдачу: botfp retry <номер>  |  botfp retry --all")
    return 0


def cmd_stats(args: argparse.Namespace, cfg: Config, conn: sqlite3.Connection) -> int:
    _print_stock(cfg, conn)
    stats = stock.delivery_stats(conn)
    print(
        f"\nВыдачи: {stats['delivered']} успешных, "
        f"{stats['sending']} в процессе, {stats['failed']} с ошибкой, "
        f"{stats['out_of_stock']} без товара."
    )
    return 0


def cmd_retry(args: argparse.Namespace, cfg: Config, conn: sqlite3.Connection) -> int:
    from .delivery import DeliveryService
    from .funpay_transport import FunPayTransport
    from .transport import TransportError

    transport = FunPayTransport(cfg)
    try:
        transport.connect()
    except TransportError as exc:
        print(f"Ошибка подключения: {exc}", file=sys.stderr)
        return 3

    service = DeliveryService(conn, cfg, transport)
    if args.all:
        ok, total = service.retry_all_pending()
        print(f"Повторено успешно: {ok} из {total}.")
        return 0 if ok == total else 1

    return 0 if service.retry(args.order_id) else 1


def cmd_release(args: argparse.Namespace, cfg: Config, conn: sqlite3.Connection) -> int:
    if stock.release_order(conn, args.order_id):
        print(f"Товар заказа #{args.order_id} возвращён на склад.")
        return 0
    print(f"Заказ #{args.order_id} не найден или товар за ним не закреплён.", file=sys.stderr)
    return 1
