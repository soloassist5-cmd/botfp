"""Сверка адаптера с настоящей библиотекой FunPayAPI.

Тесты пропускаются, если библиотека не установлена: она не лежит в
репозитории, а достаётся скриптом scripts/fetch_funpayapi.sh.

Смысл этих проверок — поймать расхождение между нашим адаптером и
библиотекой. Один такой баг уже был: без вызова ``renew_fields()`` правки
названия, цены и описания не попадали в POST, и на FunPay уезжала пустая
форма вместо заполненного лота.
"""

from __future__ import annotations

import pytest

pytest.importorskip("FunPayAPI", reason="запустите scripts/fetch_funpayapi.sh")

from FunPayAPI.common.enums import EventTypes  # noqa: E402
from FunPayAPI.types import LotFields  # noqa: E402

from botfp.funpay_transport import FunPayTransport  # noqa: E402
from botfp.listings import ListingSpec  # noqa: E402

BLANK_FORM = {
    "node_id": "1234",
    "offer_id": "0",
    "price": "",
    "active": "",
    "amount": "5",
    "fields[summary][ru]": "",
    "fields[summary][en]": "",
    "fields[desc][ru]": "",
    "fields[desc][en]": "",
    "csrf_token": "tok",
}


def spec(**overrides) -> ListingSpec:
    base = dict(
        lot_id="starter",
        node_id=1234,
        title="Стартовый аккаунт",
        description="Выдача сразу после оплаты",
        price=1.0,
        amount=7,
        active=True,
    )
    base.update(overrides)
    return ListingSpec(**base)


@pytest.fixture()
def adapter() -> FunPayTransport:
    return FunPayTransport.__new__(FunPayTransport)


def apply(adapter, lot_spec, *, as_new=True) -> dict:
    fields = LotFields(0, dict(BLANK_FORM))
    adapter._apply_spec(fields, lot_spec, as_new=as_new)
    return fields.fields


def test_title_reaches_the_post_payload(adapter):
    """Главная регрессия: без renew_fields() здесь была пустая строка."""
    assert apply(adapter, spec())["fields[summary][ru]"] == "Стартовый аккаунт"


def test_description_reaches_the_post_payload(adapter):
    assert apply(adapter, spec())["fields[desc][ru]"] == "Выдача сразу после оплаты"


def test_price_reaches_the_post_payload(adapter):
    assert apply(adapter, spec(price=25.5))["price"] == "25.5"


def test_active_lot_is_marked_on(adapter):
    assert apply(adapter, spec(active=True))["active"] == "on"


def test_inactive_lot_is_blanked(adapter):
    """Пустой склад -> лот не должен висеть на витрине."""
    assert apply(adapter, spec(active=False))["active"] == ""


def test_new_lot_gets_zero_offer_id(adapter):
    """offer_id = 0 означает «создать», а не «править существующий»."""
    assert apply(adapter, spec(), as_new=True)["offer_id"] == "0"


def test_description_is_left_alone_when_empty(adapter):
    fields = LotFields(0, {**BLANK_FORM, "fields[desc][ru]": "старое описание"})
    adapter._apply_spec(fields, spec(description=""), as_new=False)

    assert fields.fields["fields[desc][ru]"] == "старое описание"


def test_amount_is_carried_over(adapter):
    # Библиотека кладёт количество числом, а не строкой, в отличие от цены.
    assert int(apply(adapter, spec(amount=42))["amount"]) == 42


def test_missing_renew_fields_is_reported_loudly(adapter):
    """Молча уехавшая пустая форма хуже честной ошибки."""
    from botfp.transport import TransportError

    class Crippled:
        lot_id = 0
        title_ru = title_en = description_ru = description_en = ""
        price = 0.0
        active = False
        amount = 0

    with pytest.raises(TransportError, match="renew_fields"):
        adapter._apply_spec(Crippled(), spec(), as_new=True)


def test_event_types_we_rely_on_exist():
    assert EventTypes.NEW_MESSAGE is not None
    assert EventTypes.NEW_ORDER is not None


@pytest.mark.parametrize(
    "method", ["get", "send_message", "get_chat_by_name", "get_lot_fields", "save_lot", "get_user"]
)
def test_account_still_has_the_methods_we_call(method):
    from FunPayAPI import Account

    assert hasattr(Account, method), f"FunPayAPI.Account.{method} исчез — адаптер сломается"


def test_runner_listen_accepts_ignore_exceptions():
    """Без ignore_exceptions=False обрыв связи не долетит до наших оповещений."""
    import inspect

    from FunPayAPI import Runner

    assert "ignore_exceptions" in inspect.signature(Runner.listen).parameters
