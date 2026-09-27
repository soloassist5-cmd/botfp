# LS City Life — сборка Minecraft для города в духе Лос-Анджелеса

Сборка про современный город: огнестрельное оружие, работающая экономика,
смартфоны, камеры наблюдения, умные замки, машины, NPC-торговцы и готовая
карта города с ограниченной территорией.

Ставится и на официальный лаунчер, и на пиратские (Legacy Launcher, TLauncher):
ни один мод в паке не требует авторизации Mojang.

| Параметр | Значение |
|---|---|
| Minecraft | 1.20.1 |
| Загрузчик | Forge 47.4.23 |
| Java | **17** (не 21 — Forge 1.20.1 на 21 работает нестабильно) |
| ОЗУ | 6 ГБ рекомендованно, 4 ГБ минимум |
| Место на диске | ~4 ГБ вместе с миром и кэшем Distant Horizons |
| Модов | 58 (из них 1 самописный) + 342 МБ загрузки |
| Карта | «Los Santos», 2048×2048, город ~1300×1300, граница мира закрыта |
| Мультиплеер | клиент + серверная сборка, голосовой чат, Radmin VPN |

**Версия Minecraft выбрана по модам, а не наоборот.** Главный мод на огнестрел
(TaCZ) существует максимум до 1.20.1 и только под Forge — он и задал версию.
Всё остальное ядро (деньги, замки, камеры, транспорт, NPC) под 1.20.1 есть.

---

## Быстрый старт

Выбери свой лаунчер — подробные инструкции в `docs/`:

| Лаунчер | Инструкция |
|---|---|
| Legacy Launcher, TLauncher и прочие «пиратки» | [docs/legacy-launcher.md](docs/legacy-launcher.md) |
| CurseForge App | [docs/curseforge.md](docs/curseforge.md) |
| Modrinth App, Prism Launcher, MultiMC, ATLauncher | [docs/modrinth-prism.md](docs/modrinth-prism.md) |
| Сервер для игры с друзьями (Radmin VPN) | [docs/server-radmin.md](docs/server-radmin.md) |

Коротко для Windows:

```powershell
cd mcpack\install
powershell -ExecutionPolicy Bypass -File install.ps1 -Target client -Path "$env:APPDATA\.minecraft-ls-city"
```

Коротко для Linux и macOS:

```bash
cd mcpack/install
./install.sh --target client --path ~/.minecraft-ls-city
```

Установщик скачает моды с Modrinth, сверит SHA-512, разложит конфиги и
распакует готовый мир. Потом в лаунчере выбираешь Forge 1.20.1-47.4.23 и
указываешь этот каталог как папку игры.

Файлы модов в репозитории не лежат: в нём только ссылки и хэши
(`mods.lock.json`), а скачивание делает установщик. Самописный мод
`citylife` лежит в `mods-local/` — он наш и распространяется вместе с паком.

---

## Что в сборке по темам

### Огнестрельное оружие
**TaCZ** — модульные стволы с кастомизацией: прицелы, глушители, магазины,
разные патроны, отдача и баллистика. Оружие собирается на верстаке оружейника
(Gun Smith Table), а не выпадает готовым. Пак современных моделей — **Elite X
Quality Guns**.

### Деньги
**Lightman's Currency** — полноценная экономика: монеты шести номиналов,
банкоматы, банковские счёта, кредитные карты, кассы, торговые автоматы,
аукцион и налоги. Своя точка в торговом центре открывается за кассу.

Параллельно работает банк самописного мода: баланс на «карте» и переводы
между игроками прямо из телефона. Команды `/citylife balance`, `/citylife pay`,
`/citylife money give` — они же точка интеграции для скриптов и админов.

### Мобильные телефоны
Подходящего мода на 1.20.1 не существует, поэтому он **написан с нуля** —
`citylife`. Смартфон с шестью приложениями:

1. **Сообщения** — личные СМС между игроками, история хранится на сервере
2. **Контакты** — кто в сети и на каком расстоянии
3. **Банк** — баланс и переводы
4. **Карта** — координаты и до 12 именованных меток
5. **Умный дом** — открыть/закрыть привязанные замки на расстоянии, журнал доступа
6. **112** — вызов полиции, скорой или пожарных с координатами

### Камеры
- **SecurityCraft** — камеры наблюдения с монитором, сигнализации, датчики
- **CameraCraft** — фотоаппараты, плёнка, проявка, фотоальбомы, рамки

### Умные замки
- **SecurityCraft** — кодовые замки, считыватели карт-ключей, сканеры сетчатки,
  усиленные двери, лазерные растяжки, сейфы
- **citylife** — замок с привязкой к телефону: владелец, белый список, код
  доступа, автозакрытие, журнал попыток входа и вскрытие отмычкой с тревогой

### NPC и торговля
**Easy NPC** — человекоподобные NPC с диалогами и торговлей; по городу
расставлено 125 жителей с готовым ассортиментом: продавцы в ТЦ, банкир,
оружейник, автодилер, риелтор, чиновники, повара, охрана.
**Guard Villagers** — полиция и охрана, реагирующие на агрессию.

### Транспорт
**Immersive Vehicles** + официальный пак контента — машины с физикой, заправка
и ремонт. **Traffic Control + Roads** — дороги, разметка, светофоры и знаки.

## Полный список модов

### Ядро геймплея (13)

| Мод | Версия | Сторона | Зачем |
|---|---|---|---|
| [CameraCraft](https://modrinth.com/mod/cameracraft) | `2.1` | клиент+сервер | Фотоаппараты, плёнка, проявка, фотоальбомы |
| [Easy NPC: Config UI](https://modrinth.com/mod/easy-npc-config-ui) | `7.12.1` | клиент+сервер | Интерфейс настройки NPC в игре |
| [Easy NPC: Core](https://modrinth.com/mod/easy-npc-core) | `7.12.1` | клиент+сервер | NPC: диалоги, скины, профессии, торговля |
| [Elite X Quality Guns (TACZ)](https://modrinth.com/mod/elite-x-quality-guns) | `5.1` | клиент+сервер | Пак современного оружия для TaCZ |
| [Guard Villagers](https://modrinth.com/mod/guard-villagers) | `1.6.19` | клиент+сервер | Полиция и охрана города |
| [Immersive Vehicles](https://modrinth.com/mod/immersive-vehicles) | `24.0.0-1.20.1` | клиент+сервер | Автомобили с физикой, заправка, ремонт |
| [Immersive Vehicles - Official Content Pack [OCP] - Planes & Cars](https://modrinth.com/mod/immersive-vehicles-official-content-pack) | `29` | клиент+сервер | Официальный пак машин для Immersive Vehicles |
| [KubeJS](https://modrinth.com/mod/kubejs) | `2001.6.5-build.26+forge` | клиент+сервер | Скрипты городской логики: цены, рецепты, правила |
| [Lightman's Currency](https://modrinth.com/mod/lightmans-currency) | `1.20.1-2.3.0.5` | клиент+сервер | Экономика: монеты, банкоматы, счета, карты, торговые автоматы, налоги |
| [SecurityCraft: More Protectables](https://modrinth.com/mod/more-protectables) | `1.2.3.10` | клиент+сервер | Расширение SecurityCraft на блоки других модов |
| [SecurityCraft](https://modrinth.com/mod/security-craft) | `v1.10.2.1` | клиент+сервер | Умные замки, карты-ключи, сканеры, камеры наблюдения, сигнализации |
| [[TaCZ] Timeless and Classics Zero](https://modrinth.com/mod/timeless-and-classics-zero) | `1.1.8-hotfix` | клиент+сервер | Огнестрел: кастомизация, прицелы, магазины, баллистика |
| [Traffic Control + Roads (City) mod by Teerth](https://modrinth.com/mod/traffic-control-+-roads-mod-by-teerth) | `5.5.0` | клиент+сервер | Дороги, разметка, светофоры, знаки, отбойники |

### Строительство и декор (12)

| Мод | Версия | Сторона | Зачем |
|---|---|---|---|
| [Chipped](https://modrinth.com/mod/chipped) | `3.0.7` | клиент+сервер | Варианты ванильных блоков для фасадов |
| [Decorative Blocks](https://modrinth.com/mod/decorative-blocks) | `4.1.3+forge` | клиент+сервер | Навесы, балки, поручни |
| [FramedBlocks](https://modrinth.com/mod/framedblocks) | `9.4.3` | клиент+сервер | Произвольные формы блоков для архитектуры |
| [Macaw's Bridges](https://modrinth.com/mod/macaws-bridges) | `3.1.2` | клиент+сервер | Мосты и эстакады |
| [Macaw's Doors](https://modrinth.com/mod/macaws-doors) | `1.1.5` | клиент+сервер | Двери: стеклянные, гаражные, магазинные |
| [Macaw's Fences and Walls](https://modrinth.com/mod/macaws-fences-and-walls) | `1.2.1` | клиент+сервер | Ограды участков и отбойники |
| [Macaw's Lights and Lamps](https://modrinth.com/mod/macaws-lights-and-lamps) | `1.1.5` | клиент+сервер | Уличные фонари, вывески, подсветка |
| [Macaw's Paths and Pavings](https://modrinth.com/mod/macaws-paths-and-pavings) | `1.1.1` | клиент+сервер | Тротуары и покрытия |
| [Macaw's Roofs](https://modrinth.com/mod/macaws-roofs) | `2.3.2` | клиент+сервер | Крыши |
| [Macaw's Windows](https://modrinth.com/mod/macaws-windows) | `2.4.2` | клиент+сервер | Витрины и окна |
| [Rechiseled](https://modrinth.com/mod/rechiseled) | `1.2.6-forge-mc1.20.1` | клиент+сервер | Дополнительные текстуры бетона и камня |
| [Supplementaries](https://modrinth.com/mod/supplementaries) | `1.20-3.1.43-forge` | клиент+сервер | Городская мелочь: вывески, урны, ящики, флаги |

### Удобства и атмосфера (14)

| Мод | Версия | Сторона | Зачем |
|---|---|---|---|
| [AppleSkin](https://modrinth.com/mod/appleskin) | `2.5.1+mc1.20.1` | клиент+сервер | Показ насыщения еды |
| [Corpse](https://modrinth.com/mod/corpse) | `forge-1.20.1-1.0.23` | клиент+сервер | Труп с вещами вместо разбросанного лута — нужно для погонь и перестрелок |
| [First-person Model](https://modrinth.com/mod/first-person-model) | `2.7.3` | клиент | Видно своё тело от первого лица |
| [Jade 🔍](https://modrinth.com/mod/jade) | `11.13.3+forge` | клиент | Информация о блоке под прицелом |
| [Just Enough Items (JEI)](https://modrinth.com/mod/jei) | `15.62.0.216` | клиент+сервер | Просмотр рецептов |
| [Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks) | `1.20.1-2.25.1-forge` | клиент | Удобное перетаскивание в инвентаре |
| [Not Enough Animations](https://modrinth.com/mod/not-enough-animations) | `1.12.6` | клиент | Анимации тела от третьего лица |
| [Polymorph](https://modrinth.com/mod/polymorph) | `0.49.11+1.20.1` | клиент+сервер | Выбор рецепта при конфликте — обязательно при таком числе модов |
| [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) | `forge-1.20.1-2.6.22` | клиент+сервер | Голосовой чат — ключевое для игры в городе с друзьями |
| [Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered) | `forge-1.20.1-1.4.10` | клиент | Эхо и реверберация в помещениях и переулках |
| [Waystones](https://modrinth.com/mod/waystones) | `14.1.21+forge-1.20.1` | клиент+сервер | Станции метро как точки быстрого перемещения |
| [WorldEdit](https://modrinth.com/mod/worldedit) | `7.2.15` | клиент+сервер | Правка города и своих построек |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | `forge-1.20.1-26.5.0` | клиент | Миникарта с метками |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | `forge-1.20.1-1.46.0` | клиент | Полная карта города |

### Производительность (8)

| Мод | Версия | Сторона | Зачем |
|---|---|---|---|
| [Clumps](https://modrinth.com/mod/clumps) | `12.0.0.4` | клиент+сервер | Слияние сфер опыта |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | `3.3.2-1.20.1` | клиент | LOD-прорисовка: силуэт города издалека без потери FPS |
| [Embeddium](https://modrinth.com/mod/embeddium) | `0.3.31+mc1.20.1` | клиент | Переписанный рендер (аналог Sodium для Forge) |
| [Entity Culling](https://modrinth.com/mod/entityculling) | `1.11.2` | клиент | Не рендерить скрытые сущности — важно для плотного города |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | `6.0.1` | клиент+сервер | Экономия ОЗУ |
| [Memory Leak Fix](https://modrinth.com/mod/memoryleakfix) | `v1.1.5` | клиент+сервер | Починка утечек памяти |
| [ModernFix](https://modrinth.com/mod/modernfix) | `5.27.83+mc1.20.1` | клиент+сервер | Ускорение запуска и снижение потребления памяти |
| [Oculus](https://modrinth.com/mod/oculus) | `1.20.1-1.8.0` | клиент | Шейдеры. Отключить, если слабая видеокарта или конфликт с Distant Horizons |

### Библиотеки (11)

| Мод | Версия | Сторона | Зачем |
|---|---|---|---|
| [Architectury API](https://modrinth.com/mod/architectury-api) | `9.2.14+forge` | клиент+сервер | зависимость `kubejs` |
| [Athena](https://modrinth.com/mod/athena-ctm) | `3.1.2` | клиент+сервер | зависимость `chipped` |
| [Balm](https://modrinth.com/mod/balm) | `7.3.44+forge-1.20.1` | клиент+сервер | зависимость `waystones` |
| [Fusion (Connected Textures)](https://modrinth.com/mod/fusion-connected-textures) | `1.3.15b-forge-mc1.20.1` | клиент+сервер | зависимость `rechiseled` |
| [MezzConfig](https://modrinth.com/mod/mezzconfig) | `0.6.5` | клиент+сервер | зависимость `jei` |
| [Moonlight Lib](https://modrinth.com/mod/moonlight) | `1.20-2.16.35-forge` | клиент+сервер | зависимость `supplementaries` |
| [Resourceful Lib](https://modrinth.com/mod/resourceful-lib) | `2.1.29` | клиент+сервер | зависимость `chipped` |
| [Rhino](https://modrinth.com/mod/rhino) | `2001.2.3-build.10+forge` | клиент+сервер | зависимость `kubejs` |
| [SuperMartijn642's Config Lib](https://modrinth.com/mod/supermartijn642s-config-lib) | `1.1.8-forge-mc1.20` | клиент+сервер | зависимость `rechiseled` |
| [SuperMartijn642's Core Lib](https://modrinth.com/mod/supermartijn642s-core-lib) | `1.1.24b-forge-mc1.20.1` | клиент+сервер | зависимость `rechiseled` |
| [TCT Core](https://modrinth.com/mod/tct-core) | `2.2` | клиент+сервер | зависимость `cameracraft` |

---

## Карта «Los Santos»

Мир сгенерирован скриптом (`world/generator`) и лежит готовым архивом —
собирать ничего не нужно. Подробный путеводитель: [docs/city.md](docs/city.md).

- **Размер:** 2048×2048 блоков, граница мира закрыта (за неё не выйти)
- **Город:** ~1300×1300, сетка улиц с шагом 64 блока, каждая третья — проспект
- **Ландшафт:** океан и пляж на западе, холмы на севере, ровная «чаша» под
  городом, пустыня на востоке
- **361 участок:** 3 небоскрёба, торговый центр, мэрия, банк с хранилищем,
  полиция, больница, пожарная часть, оружейный магазин, автосалон, салон связи,
  2 АЗС, закусочные, ночной клуб, доходные дома, парки, порт со складами,
  3 станции метро с проложенными рельсами, эстакада, пирс с кафе
- **~29% участков свободны** под ваши постройки: они огорожены и помечены
  табличкой «ПРОДАЁТСЯ УЧАСТОК»

Вся геометрия города построена **только на ванильных блоках**: если отключить
любой декоративный мод, мир останется целым. Блоки модов идут отдельным
проходом и не держат карту.

## Сценарий

18 целей в меню достижений: приезд в город, телефон, первые деньги, счёт в
банке, своё жильё — а дальше развилка на законный путь (лицензия, свой магазин,
охрана объекта) и криминальный (отмычка, кодолом, дело в банке). Плюс
кооперативные цели для игры с друзьями.
Подробно: [docs/scenario.md](docs/scenario.md).

## Как всё это собрано

```
mcpack/
├── pack.toml              единый источник истины: версии, моды, стороны
├── mods.lock.json         зафиксированные версии, ссылки и хэши
├── build/
│   ├── resolve_mods.py    разрешение версий через Modrinth API
│   ├── build_pack.py      сборка .mrpack, профиля CurseForge и серверного пака
│   ├── check_deps.py      проверка совместимости модов по mods.toml
│   └── gen_datapack.py    генерация NPC, сценария и правил города
├── install/               установщики для Windows и Linux/macOS
├── citylife/              исходники самописного мода (Gradle)
├── mods-local/            собранный jar самописного мода
├── datapack/citylife/     датапак: NPC, сценарий, уборка мобов
├── overrides/             конфиги и KubeJS-скрипты пака
├── server/                скрипты запуска и конфиги сервера
├── world/
│   ├── generator/         генератор города (пишет region-файлы напрямую)
│   │   ├── generate.py        сборка мира
│   │   └── verify_world.py    проверка готового мира без запуска игры
│   └── los-santos.zip     готовый мир
└── dist/                  собранные дистрибутивы
```

Пересобрать всё с нуля: [docs/development.md](docs/development.md).

## Проверено

- оба установщика реально запускались: скачивание, сверка SHA-512, повторный
  запуск докачивает только недостающее, имена файлов с пробелами и скобками
  обрабатываются;
- `check_deps.py` сверяет диапазоны зависимостей из `mods.toml` всех 58 модов,
  включая вложенные jar-in-jar — конфликтов версий нет;
- сервер поднимается на этой сборке и загружает сгенерированный мир;
- мир проверяется скриптом `world/generator/verify_world.py`, который читает
  region-файлы обратно и сверяет 19 контрольных точек города;
- самописный мод собирается и грузится на выделенном сервере.

Замеры на проверочном сервере: 48 модов, загрузка мира и старт за 5,7 секунды
(город уже сгенерирован, поэтому спавн-чанки не считаются заново), 3 из 3
KubeJS-скриптов загружаются без ошибок и предупреждений, `citylife:npc/spawn_all`
выполняет 139 команд и заселяет город.

Чек-лист ручной проверки в игре: [docs/testing.md](docs/testing.md).
Разбор того, что в логах выглядит как ошибка, но ею не является — там же.
