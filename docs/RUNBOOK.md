# Запуск и управление

Практическая инструкция: как поднять бота, как им управлять каждый день и
что делать, когда что-то сломалось.

Управление идёт через четыре канала:

| Канал | Для чего |
|---|---|
| **Панель в Telegram** | повседневное управление с телефона: склад, зависшие заказы, повтор выдачи, объявления, проверка, бэкап |
| Командная строка | всё то же плюс пополнение склада и создание объявлений |
| Команды в чате FunPay | `!стат`, `!ожидают` — если Telegram под рукой нет |
| Уведомления | бот сам пишет в Telegram и в личку на FunPay |

---

## 1. Первый запуск

### Установка

```bash
sudo useradd -r -m -d /opt/botfp botfp
sudo -u botfp git clone <репозиторий> /opt/botfp
cd /opt/botfp
sudo -u botfp python3 -m venv .venv
sudo -u botfp .venv/bin/pip install -r requirements.txt
```

Нужен Python 3.11 или новее.

### Ключ доступа

`golden_key` — cookie вашей сессии FunPay, это полный доступ к аккаунту.
Держите его в отдельном файле, а не в `config.toml`:

```bash
sudo mkdir -p /etc/botfp
sudo cp deploy/botfp.env.example /etc/botfp/botfp.env
sudo nano /etc/botfp/botfp.env          # вставьте ключ
sudo chown root:botfp /etc/botfp/botfp.env
sudo chmod 640 /etc/botfp/botfp.env
```

Взять ключ: войдите на FunPay в браузере → F12 → Application → Cookies →
`golden_key`. Оттуда же скопируйте свой User-Agent в конфиг: если он не
совпадает с браузерным, FunPay чаще сбрасывает сессию.

### Конфиг

```bash
sudo -u botfp .venv/bin/python -m botfp init
sudo -u botfp nano config.toml
```

Заполните `admins` (ваш ник на FunPay) и лоты. Про типы лотов и поля —
в [README](../README.md).

### Проверка перед боем

```bash
.venv/bin/python -m botfp doctor            # локальные проверки
.venv/bin/python -m botfp doctor --online   # плюс связь с FunPay и объявления
```

Разбирает конфиг, склад, права на файлы, зависшие заказы и пересечения правил
`match`. Красный крестик — запускаться рано.

### Товар на склад

```bash
# Уникальные лоты (аккаунты, ключи): одна строка файла — одна единица.
.venv/bin/python -m botfp stock add --lot starter --file items.txt

# Безлимитные лоты (гайды, ссылки) склад не используют:
# товар лежит в config.toml в поле payload.
```

### Объявления

```bash
.venv/bin/python -m botfp lots sync           # сухой прогон — покажет план
.venv/bin/python -m botfp lots sync --apply   # применить
```

Помните: в каждой подкатегории FunPay нужен **один лот, заведённый руками** —
бот копирует с него форму. Без образца `doctor --online` скажет об этом прямо.

### Панель в Telegram

1. Создайте бота у [@BotFather](https://t.me/BotFather), получите токен.
2. Узнайте свой числовой ID у [@userinfobot](https://t.me/userinfobot).
3. Впишите ID в `telegram.admin_ids`, поставьте `enabled = true`.
4. Токен — в файл окружения, рядом с `golden_key`:

```bash
sudo nano /etc/botfp/botfp.env
# TELEGRAM_BOT_TOKEN=123456:AA...
```

5. **Напишите своему боту `/start`** — без этого Telegram не даст ему
   написать вам первым, и уведомления не придут.

Панель поднимается вместе с ботом (`botfp run`). Отдельно, для проверки:

```bash
python3 -m botfp panel
```

Доступ только у тех, чьи ID в `admin_ids`. Сообщения от всех остальных бот
игнорирует молча — отвечать незнакомцу «у вас нет доступа» значит
подтверждать, что бот жив.

### Запуск как служба

```bash
sudo cp deploy/botfp.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now botfp
systemctl status botfp
```

### Бэкапы

```bash
sudo -u botfp crontab -e
# 0 * * * * /opt/botfp/deploy/backup.sh >> /var/log/botfp-backup.log 2>&1
```

Копию можно снимать на работающем боте. В базе история выдач: потеряете её —
перестанете знать, кому что уже отправили.

---

## 2. Ежедневное управление

### С сервера

```bash
python3 -m botfp stats            # остатки и сводка по выдачам
python3 -m botfp stock count      # только остатки
python3 -m botfp pending          # заказы, требующие внимания
python3 -m botfp lots list        # какой лот к какому объявлению привязан
python3 -m botfp backup --dir backups
```

### С телефона: панель в Telegram

Напишите боту `/start` — придёт меню:

```
Панель управления botfp
⏳ Требуют внимания: 1

[📦 Склад]      [⏳ Зависшие]
[📊 Статистика] [🏷 Объявления]
[🩺 Проверка]   [💾 Бэкап]
```

Что можно прямо оттуда:

- посмотреть остатки (🔴 пусто / 🟡 заканчивается / 🟢 норма);
- повторить зависшую выдачу — по одной или все разом;
- вернуть товар заказа на склад (спросит подтверждение — операция
  возвращает единицу в продажу);
- посмотреть, какие объявления на витрине, и обновить их состояние;
- прогнать проверку `doctor`;
- снять бэкап базы.

Команды текстом работают тоже: `/stock`, `/pending`, `/stats`, `/doctor`,
`/backup`.

### Запасной канал: чат FunPay

Если Telegram недоступен, те же сводки есть в чате FunPay (вы должны быть
в `bot.admins`):

- `!стат` — склад и статистика выдач
- `!ожидают` — зависшие заказы

### Что бот пишет сам

- товар заканчивается (порог `low_stock_threshold`);
- товар кончился и заказ не выдан;
- выдача сорвалась технически;
- покупатель позвал живого человека командой `!человек`;
- синхронизация объявлений прошла с ошибками.

Уведомления приходят и в Telegram, и в личку на FunPay. Отключить
дублирование в Telegram: `telegram.notify = false`.

### Логи

```bash
journalctl -u botfp -f              # живой поток
journalctl -u botfp -n 200          # последние 200 строк
journalctl -u botfp --since today | grep -i ошибк
```

---

## 3. Когда что-то пошло не так

### Заказ оплачен, товар не пришёл

```bash
python3 -m botfp pending        # найдите заказ и причину
python3 -m botfp retry 1234     # повторить выдачу
python3 -m botfp retry --all    # повторить все зависшие
```

Повтор отправляет **тот же самый** товар, что был закреплён за заказом, —
второй единицы со склада он не возьмёт.

### Покупатель оформил возврат

```bash
python3 -m botfp release 1234   # вернуть товар на склад
```

Делайте это только если уверены, что покупатель товаром не воспользовался:
команда возвращает единицу в продажу.

### Товар кончился

```bash
python3 -m botfp stock add --lot starter --file new.txt
python3 -m botfp lots refresh    # вернуть лот на витрину
```

Если бот запущен, `refresh` он сделает сам после ближайшего заказа.

### Бот не поднимается

```bash
systemctl status botfp
journalctl -u botfp -n 50
python3 -m botfp doctor --online
```

Частые причины:
- **сессия FunPay истекла** — возьмите свежий `golden_key`, обновите
  `/etc/botfp/botfp.env`, `sudo systemctl restart botfp`;
- **User-Agent не совпадает** с браузером, из которого взят ключ;
- **опечатка в конфиге** — `doctor` покажет, где.

### Панель в Telegram молчит

- вы не написали боту `/start` — Telegram запрещает ботам писать первыми;
- ваш ID не в `admin_ids` (проверьте у @userinfobot, нужен именно числовой);
- неверный токен — в логе будет `Telegram недоступен`;
- `enabled = false` в секции `[telegram]`.

Бот игнорирует посторонних молча, поэтому «нет ответа» и «нет доступа»
выглядят одинаково. Сверьтесь с `journalctl -u botfp | grep -i telegram`.

### Заказы уходят не в тот лот

Пересеклись правила `match`. `doctor` это ловит и называет виновника.
Правила сравниваются по вхождению подстроки, длинные проверяются первыми.

### Восстановление из копии

```bash
sudo systemctl stop botfp
sudo -u botfp cp /opt/botfp/backups/botfp-ГГГГММДД-ЧЧММСС.db /opt/botfp/botfp.db
sudo systemctl start botfp
python3 -m botfp doctor
```

После восстановления сверьте `botfp pending`: заказы, прошедшие после снятия
копии, в ней не отражены.

---

## 4. Обновление бота

```bash
cd /opt/botfp
sudo -u botfp .venv/bin/python -m botfp backup --dir backups   # сначала копия
sudo -u botfp git pull
sudo -u botfp .venv/bin/pip install -r requirements.txt
sudo -u botfp .venv/bin/python -m botfp doctor
sudo systemctl restart botfp
```

Схема базы мигрируется сама при запуске, данные не теряются.
