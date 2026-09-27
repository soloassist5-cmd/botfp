# Установка в Modrinth App, Prism Launcher, MultiMC и ATLauncher

Для этих лаунчеров есть готовый файл `dist/ls-city-life-1.0.0.mrpack` —
в нём ссылки на все моды с хэшами, конфиги и KubeJS-скрипты. Лаунчер сам
скачает Forge и моды.

## Modrinth App

1. **Add instance → From file** → выбери `ls-city-life-1.0.0.mrpack`.
2. Дождись, пока скачаются Forge 1.20.1-47.4.23 и 56 модов.
3. В настройках инстанса выдели 6 ГБ и убедись, что выбрана Java 17.

## Prism Launcher / MultiMC

1. **Add Instance → Import from zip** → укажи `.mrpack` (Prism понимает этот
   формат напрямую; MultiMC — начиная со сборок с поддержкой Modrinth).
2. Настройки инстанса → **Java**: путь к Java 17, память `-Xmx6G`.
3. Настройки инстанса → **Settings → Java arguments**, если нужно поменять флаги.

## ATLauncher

**Add Pack → Import → Modrinth pack** и выбери `.mrpack`.

## Мир и самописный мод

`.mrpack` не содержит мир и самописный мод `citylife` — они лежат в репозитории
и добавляются одной командой:

```bash
./install.sh --target client --path "<папка инстанса>/.minecraft" --no-world
```

или вручную:

- `mods-local/citylife-1.20.1-1.0.0.jar` → в папку `mods` инстанса;
- `world/los-santos.zip` распаковать в `saves`.

В Prism и MultiMC папка игры — это подкаталог `.minecraft` внутри инстанса;
в Modrinth App это корень папки инстанса.

## Шейдеры

Oculus помечен как необязательный и в `.mrpack` не входит: он конфликтует с
Distant Horizons на части шейдерпаков. Если хочешь шейдеры — поставь
[Oculus](https://modrinth.com/mod/oculus) вручную и уменьши дистанцию
Distant Horizons до 64.
