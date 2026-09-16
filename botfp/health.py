"""HTTP-заглушка для площадок, которые требуют открытый порт.

Бот — фоновый воркер и порт ему не нужен. Но Render и подобные хостинги на
тарифе Web Service считают сервис упавшим, если он не занял ``$PORT``.
Этот крошечный сервер отвечает на health-check и заодно показывает, жива ли
связь с FunPay.
"""

from __future__ import annotations

import json
import logging
import threading
from collections.abc import Callable
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

log = logging.getLogger(__name__)


def _make_handler(status: Callable[[], dict]):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 - имя задано базовым классом
            body = json.dumps(status(), ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args) -> None:
            # Health-check стучится каждые несколько секунд — в лог это не нужно.
            return

    return Handler


class HealthServer:
    """Отдаёт состояние бота по HTTP в фоновом потоке."""

    def __init__(self, port: int, status: Callable[[], dict]):
        self.port = port
        self._server = ThreadingHTTPServer(("0.0.0.0", port), _make_handler(status))
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._thread = threading.Thread(
            target=self._server.serve_forever, name="health", daemon=True
        )
        self._thread.start()
        log.info("Health-эндпоинт слушает порт %d", self.port)

    def stop(self) -> None:
        self._server.shutdown()
        self._server.server_close()
        if self._thread is not None:
            self._thread.join(timeout=5)
