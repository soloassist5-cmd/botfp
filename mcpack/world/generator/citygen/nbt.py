"""
Минимальная реализация NBT (Named Binary Tag) для записи миров Minecraft.

Поддерживаются все теги формата 19133 (Anvil). Хватает и на level.dat,
и на чанки. Зависимостей нет — только стандартная библиотека.

Значения представляются обёртками (Byte, Int, ...) там, где тип нельзя
угадать из Python-типа; словарь = TAG_Compound, список = TAG_List.
"""
from __future__ import annotations

import gzip
import struct
import zlib
from typing import Any

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class _Num:
    __slots__ = ("value",)

    def __init__(self, value: int | float):
        self.value = value

    def __repr__(self):  # для отладки
        return f"{type(self).__name__}({self.value})"


class Byte(_Num):
    tag = TAG_BYTE


class Short(_Num):
    tag = TAG_SHORT


class Int(_Num):
    tag = TAG_INT


class Long(_Num):
    tag = TAG_LONG


class Float(_Num):
    tag = TAG_FLOAT


class Double(_Num):
    tag = TAG_DOUBLE


class ByteArray:
    tag = TAG_BYTE_ARRAY
    __slots__ = ("value",)

    def __init__(self, value: bytes | bytearray):
        self.value = value


class IntArray:
    tag = TAG_INT_ARRAY
    __slots__ = ("value",)

    def __init__(self, value):
        self.value = value


class LongArray:
    tag = TAG_LONG_ARRAY
    __slots__ = ("value",)

    def __init__(self, value):
        self.value = value


class List:
    """TAG_List с явно заданным типом элементов (нужен для пустых списков)."""
    tag = TAG_LIST
    __slots__ = ("item_tag", "value")

    def __init__(self, item_tag: int, value: list | None = None):
        self.item_tag = item_tag
        self.value = value if value is not None else []


def _tag_of(value: Any) -> int:
    if isinstance(value, (_Num, ByteArray, IntArray, LongArray, List)):
        return value.tag
    if isinstance(value, bool):
        return TAG_BYTE
    if isinstance(value, int):
        return TAG_INT
    if isinstance(value, float):
        return TAG_DOUBLE
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, dict):
        return TAG_COMPOUND
    if isinstance(value, (list, tuple)):
        if not value:
            raise ValueError("пустой список: используй nbt.List(item_tag)")
        return TAG_LIST
    raise TypeError(f"не знаю, как записать {type(value)}")


def _write_string(out: bytearray, text: str) -> None:
    data = text.encode("utf-8")
    out += struct.pack(">H", len(data))
    out += data


def _write_payload(out: bytearray, tag: int, value: Any) -> None:
    if tag == TAG_BYTE:
        v = value.value if isinstance(value, _Num) else int(value)
        out += struct.pack(">b", v if -128 <= v <= 127 else v - 256)
    elif tag == TAG_SHORT:
        out += struct.pack(">h", value.value if isinstance(value, _Num) else value)
    elif tag == TAG_INT:
        out += struct.pack(">i", value.value if isinstance(value, _Num) else value)
    elif tag == TAG_LONG:
        v = value.value if isinstance(value, _Num) else value
        # Long в NBT знаковый; принимаем и беззнаковые значения.
        if v > 0x7FFFFFFFFFFFFFFF:
            v -= 1 << 64
        out += struct.pack(">q", v)
    elif tag == TAG_FLOAT:
        out += struct.pack(">f", value.value if isinstance(value, _Num) else value)
    elif tag == TAG_DOUBLE:
        out += struct.pack(">d", value.value if isinstance(value, _Num) else value)
    elif tag == TAG_BYTE_ARRAY:
        data = bytes(value.value)
        out += struct.pack(">i", len(data))
        out += data
    elif tag == TAG_STRING:
        _write_string(out, value)
    elif tag == TAG_LIST:
        if isinstance(value, List):
            items, item_tag = value.value, value.item_tag
        else:
            items = list(value)
            item_tag = _tag_of(items[0])
        out += struct.pack(">b", item_tag)
        out += struct.pack(">i", len(items))
        for item in items:
            _write_payload(out, item_tag, item)
    elif tag == TAG_COMPOUND:
        for key, item in value.items():
            item_tag = _tag_of(item)
            out += struct.pack(">b", item_tag)
            _write_string(out, key)
            _write_payload(out, item_tag, item)
        out += b"\x00"
    elif tag == TAG_INT_ARRAY:
        items = value.value
        out += struct.pack(">i", len(items))
        out += struct.pack(f">{len(items)}i", *items) if items else b""
    elif tag == TAG_LONG_ARRAY:
        items = value.value
        out += struct.pack(">i", len(items))
        if items:
            norm = [v - (1 << 64) if v > 0x7FFFFFFFFFFFFFFF else v for v in items]
            out += struct.pack(f">{len(norm)}q", *norm)
    else:
        raise ValueError(f"неизвестный тег {tag}")


def dumps(root: dict, root_name: str = "") -> bytes:
    """Сериализовать TAG_Compound в байты (без сжатия)."""
    out = bytearray()
    out += struct.pack(">b", TAG_COMPOUND)
    _write_string(out, root_name)
    _write_payload(out, TAG_COMPOUND, root)
    return bytes(out)


def write_gzip(path: str, root: dict, root_name: str = "") -> None:
    """level.dat и прочие файлы, которые Minecraft хранит в gzip."""
    with gzip.open(path, "wb", compresslevel=6) as fh:
        fh.write(dumps(root, root_name))


def zlib_bytes(root: dict, root_name: str = "") -> bytes:
    """Чанк внутри region-файла: zlib (тип сжатия 2)."""
    return zlib.compress(dumps(root, root_name), 6)


# --- чтение (нужно только для самопроверки) ---------------------------------

class _Reader:
    def __init__(self, data: bytes):
        self.d = data
        self.i = 0

    def take(self, n: int) -> bytes:
        chunk = self.d[self.i:self.i + n]
        if len(chunk) != n:
            raise EOFError("NBT: данные кончились")
        self.i += n
        return chunk

    def num(self, fmt: str):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(">" + fmt)))[0]

    def string(self) -> str:
        return self.take(self.num("H")).decode("utf-8")

    def payload(self, tag: int):
        if tag == TAG_BYTE:
            return self.num("b")
        if tag == TAG_SHORT:
            return self.num("h")
        if tag == TAG_INT:
            return self.num("i")
        if tag == TAG_LONG:
            return self.num("q")
        if tag == TAG_FLOAT:
            return self.num("f")
        if tag == TAG_DOUBLE:
            return self.num("d")
        if tag == TAG_BYTE_ARRAY:
            return self.take(self.num("i"))
        if tag == TAG_STRING:
            return self.string()
        if tag == TAG_LIST:
            item_tag = self.num("b")
            count = self.num("i")
            return [self.payload(item_tag) for _ in range(count)]
        if tag == TAG_COMPOUND:
            result = {}
            while True:
                child = self.num("b")
                if child == TAG_END:
                    return result
                name = self.string()
                result[name] = self.payload(child)
        if tag == TAG_INT_ARRAY:
            n = self.num("i")
            return list(struct.unpack(f">{n}i", self.take(4 * n)))
        if tag == TAG_LONG_ARRAY:
            n = self.num("i")
            return list(struct.unpack(f">{n}q", self.take(8 * n)))
        raise ValueError(f"неизвестный тег при чтении: {tag}")


def loads(data: bytes) -> tuple[str, dict]:
    """Разобрать NBT. Возвращает (имя корня, содержимое)."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    r = _Reader(data)
    tag = r.num("b")
    if tag != TAG_COMPOUND:
        raise ValueError("корень NBT должен быть TAG_Compound")
    name = r.string()
    return name, r.payload(TAG_COMPOUND)
