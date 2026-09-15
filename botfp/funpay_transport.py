"""Адаптер к FunPay поверх библиотеки FunPayAPI.

Официального API у FunPay нет, поэтому библиотека работает через сессию
продавца (cookie ``golden_key``). Отсюда два следствия:

* ``golden_key`` — это полный доступ к аккаунту. Держите его в переменной
  окружения, а не в репозитории.
* Слушатель живёт в отдельном потоке: ``Runner.listen()`` блокирующий, а
  остальному боту нужен неблокирующий :meth:`poll`.

Импорты FunPayAPI намеренно ленивые — без установленной библиотеки пакет
остаётся рабочим (консольный режим, тесты, работа со складом).
"""

from __future__ import annotations

import logging
import queue
import threading
from collections.abc import Iterable

from .config import Config
from .listings import ListingSpec, RemoteLot
from .transport import Event, NewMessageEvent, NewOrderEvent, TransportError

log = logging.getLogger(__name__)


class FunPayTransport:
    """Транспорт поверх боевого аккаунта FunPay."""

    def __init__(self, config: Config):
        self.config = config
        self._queue: queue.Queue[Event] = queue.Queue()
        self._account = None
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()
        self._listener_error: BaseException | None = None

    # ------------------------------------------------------------------
    # Подключение
    # ------------------------------------------------------------------

    def connect(self) -> str:
        """Авторизуется на FunPay. Возвращает ник продавца."""
        try:
            from FunPayAPI import Account
        except ImportError as exc:  # pragma: no cover - зависит от окружения
            raise TransportError(
                "Не установлена библиотека FunPayAPI. "
                "Поставьте её: pip install -r requirements.txt"
            ) from exc

        try:
            self._account = Account(
                self.config.golden_key, user_agent=self.config.user_agent
            ).get()
        except Exception as exc:  # pragma: no cover - сеть
            raise TransportError(f"Не удалось войти на FunPay: {exc}") from exc

        log.info("Вошёл на FunPay как %s (id %s)", self._account.username, self._account.id)
        return str(self._account.username)

    def start(self) -> None:
        """Запускает фоновый слушатель событий."""
        if self._account is None:
            raise TransportError("Сначала вызовите connect().")
        if self._thread is not None:
            return

        self._thread = threading.Thread(
            target=self._listen, name="funpay-listener", daemon=True
        )
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    # ------------------------------------------------------------------
    # Transport
    # ------------------------------------------------------------------

    def poll(self) -> Iterable[Event]:
        if self._listener_error is not None:
            error, self._listener_error = self._listener_error, None
            raise TransportError(f"Слушатель FunPay упал: {error}")

        events: list[Event] = []
        while True:
            try:
                events.append(self._queue.get_nowait())
            except queue.Empty:
                return events

    def send_message(self, chat_id: str, text: str) -> None:
        account = self._require_account()
        try:
            account.send_message(int(chat_id), text)
        except Exception as exc:
            raise TransportError(f"Не удалось отправить сообщение в чат {chat_id}: {exc}") from exc

    def send_to_user(self, username: str, text: str) -> None:
        account = self._require_account()
        try:
            chat = account.get_chat_by_name(username, make_request=True)
            if chat is None:
                raise TransportError(f"Пользователь {username} не найден на FunPay.")
            account.send_message(chat.id, text)
        except TransportError:
            raise
        except Exception as exc:
            raise TransportError(f"Не удалось написать пользователю {username}: {exc}") from exc

    # ------------------------------------------------------------------
    # Объявления (LotsAPI)
    # ------------------------------------------------------------------

    def list_my_lots(self) -> list[RemoteLot]:
        """Активные объявления продавца.

        Снятые с публикации лоты в профиле не показываются, поэтому всё, что
        сюда попало, считается активным.
        """
        account = self._require_account()
        try:
            profile = account.get_user(account.id)
            lots = profile.get_lots()
        except Exception as exc:
            raise TransportError(f"Не удалось получить список лотов: {exc}") from exc

        out: list[RemoteLot] = []
        for lot in lots:
            node_id = getattr(getattr(lot, "subcategory", None), "id", None)
            if node_id is None:
                continue
            out.append(
                RemoteLot(
                    funpay_lot_id=str(lot.id),
                    node_id=int(node_id),
                    title=str(getattr(lot, "description", "") or ""),
                    active=True,
                )
            )
        return out

    def create_lot(self, spec: ListingSpec) -> str:
        """Создаёт объявление, копируя форму с существующего лота той же подкатегории.

        У каждой подкатегории FunPay свой набор полей. Гадать их вслепую —
        верный способ создать кривой лот, поэтому бот берёт форму с вашего
        уже существующего лота и меняет в ней только название, описание,
        цену и количество.
        """
        account = self._require_account()
        template_id = self._template_lot_id(spec.node_id)
        if template_id is None:
            raise TransportError(
                f"В подкатегории {spec.node_id} нет ни одного вашего лота. "
                f"Форма лота на FunPay у каждой подкатегории своя, и бот копирует "
                f"её с существующего объявления, а не выдумывает. "
                f"Заведите здесь один лот руками — остальные бот создаст сам."
            )

        try:
            fields = account.get_lot_fields(template_id)
        except Exception as exc:
            raise TransportError(f"Не удалось прочитать форму лота {template_id}: {exc}") from exc

        self._apply_spec(fields, spec, as_new=True)

        try:
            account.save_lot(fields)
        except Exception as exc:
            raise TransportError(f"Не удалось сохранить новое объявление: {exc}") from exc

        created = self._find_by_title(spec)
        if created is None:
            raise TransportError(
                f"Объявление «{spec.title}» сохранено, но не найдено в профиле. "
                f"Проверьте витрину вручную, прежде чем запускать синхронизацию снова."
            )
        return created

    def update_lot(self, funpay_lot_id: str, spec: ListingSpec) -> None:
        account = self._require_account()
        try:
            fields = account.get_lot_fields(int(funpay_lot_id))
        except Exception as exc:
            raise TransportError(f"Не удалось прочитать лот {funpay_lot_id}: {exc}") from exc

        self._apply_spec(fields, spec, as_new=False)

        try:
            account.save_lot(fields)
        except Exception as exc:
            raise TransportError(f"Не удалось обновить лот {funpay_lot_id}: {exc}") from exc

    def set_lot_active(self, funpay_lot_id: str, active: bool) -> None:
        account = self._require_account()
        try:
            fields = account.get_lot_fields(int(funpay_lot_id))
            self._set(fields, "active", active)
            account.save_lot(fields)
        except TransportError:
            raise
        except Exception as exc:
            verb = "вернуть на витрину" if active else "снять с витрины"
            raise TransportError(f"Не удалось {verb} лот {funpay_lot_id}: {exc}") from exc

    # ------------------------------------------------------------------

    def _apply_spec(self, fields: object, spec: ListingSpec, *, as_new: bool) -> None:
        """Переносит наши поля в форму FunPay.

        Названия полей различаются между версиями FunPayAPI, поэтому каждое
        ставится по возможности: чего нет — то пропускается, а не роняет всё.
        """
        if as_new:
            # Обнуляем идентификаторы, иначе сохранится правка шаблона,
            # а не создание нового лота.
            self._set(fields, "lot_id", 0)
            raw = getattr(fields, "fields", None)
            if isinstance(raw, dict):
                raw["offer_id"] = "0"
                raw.pop("deleted", None)

        self._set(fields, "node_id", spec.node_id)
        self._set(fields, "price", spec.price)
        self._set(fields, "active", spec.active)
        for attr in ("title_ru", "summary_ru"):
            self._set(fields, attr, spec.title)
        for attr in ("title_en", "summary_en"):
            self._set(fields, attr, spec.title)
        if spec.description:
            for attr in ("description_ru", "desc_ru"):
                self._set(fields, attr, spec.description)
        if spec.amount is not None:
            self._set(fields, "amount", spec.amount)

    @staticmethod
    def _set(target: object, attr: str, value: object) -> bool:
        if not hasattr(target, attr):
            return False
        try:
            setattr(target, attr, value)
        except Exception as exc:  # pragma: no cover - зависит от версии библиотеки
            log.debug("Поле %s не принято формой лота: %s", attr, exc)
            return False
        return True

    def _template_lot_id(self, node_id: int) -> int | None:
        """Любой существующий лот в этой подкатегории — как образец формы."""
        for lot in self.list_my_lots():
            if lot.node_id == node_id:
                return int(lot.funpay_lot_id)
        return None

    def _find_by_title(self, spec: ListingSpec) -> str | None:
        wanted = spec.title.casefold().strip()
        for lot in self.list_my_lots():
            if lot.node_id == spec.node_id and lot.title.casefold().strip() == wanted:
                return lot.funpay_lot_id
        return None

    def _require_account(self):
        if self._account is None:
            raise TransportError("Нет подключения к FunPay.")
        return self._account

    # ------------------------------------------------------------------
    # Фоновый слушатель
    # ------------------------------------------------------------------

    def _listen(self) -> None:
        try:
            from FunPayAPI import Runner
            from FunPayAPI.updater.events import EventTypes

            runner = Runner(self._account)
            for raw in runner.listen(requests_delay=self.config.request_delay):
                if self._stop.is_set():
                    return
                event = self._convert(raw, EventTypes)
                if event is not None:
                    self._queue.put(event)
        except BaseException as exc:  # noqa: BLE001 - поднимаем в основной поток
            log.exception("Слушатель FunPay остановлен из-за ошибки")
            self._listener_error = exc

    def _convert(self, raw: object, event_types: object) -> Event | None:
        """Переводит событие FunPayAPI во внутреннее представление."""
        raw_type = getattr(raw, "type", None)

        if raw_type is getattr(event_types, "NEW_MESSAGE", None):
            message = raw.message
            return NewMessageEvent(
                chat_id=str(message.chat_id),
                author=str(message.author or ""),
                text=str(message.text or ""),
                message_id=str(message.id),
            )

        if raw_type is getattr(event_types, "NEW_ORDER", None):
            order = raw.order
            description = str(getattr(order, "description", "") or "")
            lot = self.config.lot_for_description(description)
            if lot is None:
                log.warning(
                    "Заказ #%s («%s») не сопоставлен ни с одним лотом — "
                    "проверьте поле match в конфиге.",
                    order.id,
                    description,
                )
                return None
            return NewOrderEvent(
                order_id=str(order.id),
                buyer=str(getattr(order, "buyer_username", "") or ""),
                lot_id=lot.lot_id,
                chat_id=self._chat_id_for(getattr(order, "buyer_username", "")),
                title=description,
            )

        return None

    def _chat_id_for(self, username: str) -> str | None:
        if not username or self._account is None:
            return None
        try:
            chat = self._account.get_chat_by_name(username, make_request=True)
        except Exception as exc:  # pragma: no cover - сеть
            log.warning("Не нашёл чат с %s: %s", username, exc)
            return None
        return str(chat.id) if chat is not None else None
