# Установка в CurseForge App

CurseForge умеет скачивать моды только из своего каталога, а сборка собрана по
ссылкам с Modrinth. Поэтому порядок такой: сначала импортируется профиль с
нужной версией Forge и конфигами, потом установщик доливает в него сами моды.

Всё нужное лежит в скачанном архиве `ls-city-life-1.0.0.zip`.

## 1. Распакуй архив

Правый клик по скачанному файлу → «Извлечь всё». Получится папка
`ls-city-life-1.0.0` — не удаляй её, пока не закончишь установку.

## 2. Импортируй профиль в CurseForge

1. Открой CurseForge App → **Minecraft** → **Create Custom Profile** → **Import**.
2. Выбери файл внутри распакованной папки:
   ```
   ls-city-life-1.0.0\dist\ls-city-life-1.0.0-curseforge.zip
   ```
3. Появится профиль **LS City Life** с Forge 1.20.1-47.4.23, конфигами и
   KubeJS-скриптами. Модов в нём пока нет — это нормально.

Если импорт по какой-то причине не проходит, сделай профиль руками:
**Create Custom Profile** → Minecraft **1.20.1** → Modloader **Forge**, версия
**47.4.23**. Дальше всё то же самое.

## 3. Узнай путь к профилю

В списке профилей нажми на три точки рядом с профилем → **Open Folder**.
Скопируй путь из адресной строки проводника. Обычно он такой:

```
C:\Users\<имя>\curseforge\minecraft\Instances\LS City Life
```

## 4. Долей моды установщиком

Самый простой способ — команда, которая сама найдёт и установщик в Загрузках, и
папку профиля. Открой PowerShell (Win+R → `powershell` → Enter) и вставь её
**одной строкой** целиком:

```powershell
$i=(Get-ChildItem "$env:USERPROFILE\Downloads" -Recurse -Filter install.ps1 -ErrorAction SilentlyContinue | Select-Object -First 1).FullName; $p=(Get-ChildItem "$env:USERPROFILE\curseforge\minecraft\Instances" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "City" } | Select-Object -First 1).FullName; if (-not $i) { "НЕ НАЙДЕН УСТАНОВЩИК - распакуй архив" } elseif (-not $p) { "НЕ НАЙДЕН ПРОФИЛЬ - импортируй его в CurseForge" } else { "Ставлю в: $p"; powershell -ExecutionPolicy Bypass -File $i -Target client -Path $p }
```

Важно вставлять именно одной строкой: если разбить её на несколько строк,
консоль выполнит их по очереди и `elseif` отвалится с ошибкой.

Перед установкой команда напечатает, куда ставит, — можно проверить путь.

Если предпочитаешь вручную: открой папку `ls-city-life-1.0.0\install`, набери в
адресной строке проводника `powershell`, Enter, и выполни со своим путём:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1 -Target client -Path "C:\Users\<имя>\curseforge\minecraft\Instances\LS City Life"
```

Установщик:

- скачает 342 МБ модов с Modrinth и сверит SHA-512 каждого файла;
- положит самописный мод `citylife` (телефоны и умные замки);
- разложит конфиги и скрипты;
- распакует город в `saves\los-santos`.

Если что-то не докачалось — просто запусти команду ещё раз, докачает только
недостающее. Уже установленные файлы он не трогает.

## 5. Java и память

В профиле: три точки → **Profile Options** (или **Settings → Java & Memory**).

- **Java 17.** Не 21: на 21 Forge 1.20.1 работает нестабильно. Если в списке
  только 21, поставь [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17)
  и выбери её.
- **Память:** 6 ГБ.

## 6. Запуск

**Play** → в списке одиночных миров уже будет **Los Santos**. После первого
входа один раз выполни в чате:

```
/function citylife:npc/spawn_all
```

Команда расставит по городу 125 жителей. Её можно повторять в любой момент —
старых уберёт и поставит заново.

## Что дальше

- Телефон открывается правым кликом по нему в руке.
- `/work` — подработка, раз в 5 минут.
- `/phonehelp` — рецепты телефона, замков и отмычки.
- Свободные участки помечены табличкой «ПРОДАЁТСЯ УЧАСТОК».

Описание сборки целиком — в файле `docs/overview.md` в той же папке.

## Если хочется совсем без команд

В архиве есть `dist/ls-city-life-1.0.0.mrpack` — он ставится одним импортом в
**Modrinth App** или **Prism Launcher**: они умеют качать моды по ссылкам сами,
и шаг с установщиком не нужен. CurseForge так не умеет.
