#!/bin/sh
# Достаёт библиотеку FunPayAPI для связи с FunPay.
#
# Почему не через pip: FunPayAPI не опубликована в PyPI и не является
# отдельным пакетом — это папка внутри проекта FunPayCardinal, без setup.py.
# Поэтому `pip install FunPayAPI@git+...` не работает в принципе, что и
# роняло сборку.
#
# Код закреплён на конкретном коммите намеренно: библиотека получает ваш
# golden_key, и она не должна меняться под вами между деплоями. Чтобы
# обновиться, поменяйте FUNPAYAPI_REF, посмотрев, что изменилось.
set -eu

REPO="${FUNPAYAPI_REPO:-https://github.com/sidor0912/FunPayCardinal.git}"
REF="${FUNPAYAPI_REF:-65f5f431e3a1d9843a40414ec81e55b44cb08736}"
DEST="${FUNPAYAPI_DEST:-.}"

if [ -d "$DEST/FunPayAPI" ]; then
    echo "FunPayAPI уже на месте — пропускаю."
    exit 0
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "Достаю FunPayAPI из $REPO (коммит ${REF%"${REF#???????}"}...)"
git init --quiet "$TMP"
git -C "$TMP" remote add origin "$REPO"
git -C "$TMP" fetch --quiet --depth 1 origin "$REF"
git -C "$TMP" checkout --quiet FETCH_HEAD

if [ ! -d "$TMP/FunPayAPI" ]; then
    echo "В репозитории нет папки FunPayAPI — проверьте FUNPAYAPI_REPO." >&2
    exit 1
fi

cp -r "$TMP/FunPayAPI" "$DEST/FunPayAPI"
echo "FunPayAPI установлена в $DEST/FunPayAPI"
