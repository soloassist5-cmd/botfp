# Установка в CurseForge App

CurseForge не умеет ставить моды по ссылкам с Modrinth, поэтому порядок такой:
сначала импортируется профиль с конфигами и миром, потом установщик доливает
в него сами моды.

## 1. Импортируй профиль

1. Скачай `dist/ls-city-life-1.0.0-curseforge.zip` из этого репозитория.
2. В CurseForge App: **Minecraft → Create Custom Profile → Import**, выбери
   этот zip.
3. Профиль создастся с Forge 1.20.1-47.4.23, конфигами, KubeJS-скриптами и
   пустой папкой `mods`.

## 2. Узнай путь к профилю

В списке профилей нажми на три точки → **Open Folder**. Путь обычно такой:

```
C:\Users\<имя>\curseforge\minecraft\Instances\LS City Life
```

## 3. Долей моды установщиком

```powershell
cd путь\к\репозиторию\mcpack\install
powershell -ExecutionPolicy Bypass -File .\install.ps1 -Target client -Path "C:\Users\<имя>\curseforge\minecraft\Instances\LS City Life"
```

Установщик положит 47 модов, самописный `citylife`, конфиги и мир
`saves\los-santos`.

## 4. Память и Java

- В профиле: **Settings → Java & Memory** → выдели 6 ГБ.
- Там же проверь, что выбрана **Java 17**. CurseForge обычно скачивает нужную
  версию сам, но если в списке только 21 — поставь Temurin 17 и выбери её.

## 5. Запуск

Play → мир **Los Santos** уже в списке. После первого входа один раз выполни:

```
/function citylife:npc/spawn_all
```

## Почему не «обычный» модпак CurseForge

В файле `manifest.json` моды описываются числовыми ID CurseForge, а сборка
собрана по Modrinth: там есть все нужные моды, ссылки стабильные и к каждой
привязан SHA-512, который установщик проверяет. Поэтому `files` в манифесте
намеренно пуст, а моды ставит наш установщик — зато версии в паке точно те же,
что проверялись.
