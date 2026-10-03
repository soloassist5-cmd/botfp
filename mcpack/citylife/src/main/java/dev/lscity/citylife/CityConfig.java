package dev.lscity.citylife;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Настройки мода (config/citylife-common.toml). */
public final class CityConfig {
    public static final ForgeConfigSpec SPEC;
    public static final CityConfig CONFIG;

    public final ForgeConfigSpec.LongValue startingBalance;
    public final ForgeConfigSpec.IntValue cardPrice;
    public final ForgeConfigSpec.IntValue phoneLockRange;
    public final ForgeConfigSpec.IntValue maxWaypoints;
    public final ForgeConfigSpec.IntValue maxMessages;
    public final ForgeConfigSpec.DoubleValue lockpickChance;
    public final ForgeConfigSpec.IntValue lockAutoCloseTicks;
    public final ForgeConfigSpec.BooleanValue emergencyToEveryone;
    public final ForgeConfigSpec.BooleanValue lockpickAlarm;
    public final ForgeConfigSpec.IntValue deliverySeconds;
    public final ForgeConfigSpec.DoubleValue arrivalRadius;
    public final ForgeConfigSpec.BooleanValue noMobs;
    public final ForgeConfigSpec.BooleanValue explosionsKeepBlocks;
    public final ForgeConfigSpec.IntValue pedestrians;
    public final ForgeConfigSpec.IntValue pedestriansMax;
    public final ForgeConfigSpec.BooleanValue npcFacePlayers;
    public final ForgeConfigSpec.IntValue maxHomes;
    public final ForgeConfigSpec.IntValue homeSellPercent;
    public final ForgeConfigSpec.BooleanValue homeLocks;
    public final ForgeConfigSpec.IntValue homeUpkeepPerMille;
    public final ForgeConfigSpec.IntValue homeDebtDays;
    public final ForgeConfigSpec.IntValue maxBusinesses;
    public final ForgeConfigSpec.IntValue businessIncomePerMille;
    public final ForgeConfigSpec.IntValue responderDelay;
    public final ForgeConfigSpec.IntValue responderStay;
    public final ForgeConfigSpec.IntValue medicFee;
    public final ForgeConfigSpec.IntValue finePerStar;
    public final ForgeConfigSpec.IntValue jailSecondsPerStar;
    public final ForgeConfigSpec.BooleanValue hospitalRespawn;
    public final ForgeConfigSpec.IntValue hospitalBill;
    public final ForgeConfigSpec.IntValue jobPayPercent;
    public final ForgeConfigSpec.IntValue backupMinutes;
    public final ForgeConfigSpec.IntValue backupKeep;
    public final ForgeConfigSpec.BooleanValue backupSingleplayer;
    public final ForgeConfigSpec.IntValue shopRobSeconds;
    public final ForgeConfigSpec.IntValue shopRobLoot;
    public final ForgeConfigSpec.IntValue atmRobSeconds;
    public final ForgeConfigSpec.IntValue atmRobLoot;
    public final ForgeConfigSpec.IntValue robCooldownMinutes;
    public final ForgeConfigSpec.IntValue citizenCashMax;
    public final ForgeConfigSpec.IntValue patrolPercent;
    public final ForgeConfigSpec.BooleanValue policeShoot;
    public final ForgeConfigSpec.IntValue policeRange;
    public final ForgeConfigSpec.DoubleValue policeDamage;
    public final ForgeConfigSpec.IntValue lanViewDistance;
    public final ForgeConfigSpec.IntValue lanSimulationDistance;
    public final ForgeConfigSpec.BooleanValue tuneDistantHorizons;
    public final ForgeConfigSpec.IntValue dhBandwidthPerPlayer;

    private CityConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("City Life — телефоны, банк и умные замки").push("general");
        startingBalance = builder
                .comment("Стартовый баланс банковского счёта нового игрока, в рублях.")
                .defineInRange("startingBalance", 5000L, 0L, 1_000_000_000L);
        cardPrice = builder
                .comment("Сколько банк берёт за выпуск карты в банкомате, в рублях.")
                .defineInRange("cardPrice", 300, 0, 100_000);
        phoneLockRange = builder
                .comment("На каком расстоянии телефон управляет привязанным замком.")
                .defineInRange("phoneLockRange", 64, 8, 512);
        maxWaypoints = builder
                .comment("Сколько меток можно хранить в приложении «Карта».")
                .defineInRange("maxWaypoints", 12, 1, 64);
        maxMessages = builder
                .comment("Сколько последних сообщений хранится на одном номере.")
                .defineInRange("maxMessages", 50, 5, 500);
        emergencyToEveryone = builder
                .comment("Экстренный вызов 112 виден всем игрокам.",
                         "Выключи, если на сервере есть отдельная роль полиции (тогда видят только операторы).")
                .define("emergencyToEveryone", true);
        deliverySeconds = builder
                .comment("Сколько секунд заказ маркетплейса едет до пункта выдачи.")
                .defineInRange("deliverySeconds", 120, 0, 86_400);
        arrivalRadius = builder
                .comment("В каком радиусе от метки навигатор считает, что игрок пришёл,",
                         "и сам снимает маршрут.")
                .defineInRange("arrivalRadius", 1.6D, 0.5D, 16.0D);
        builder.pop();

        builder.comment("Правила города").push("city");
        noMobs = builder
                .comment("Никаких мобов в мире: ни враждебных, ни животных, ни стражей.",
                         "Остаются только жители Easy NPC, машины, предметы и прочие не-мобы.")
                .define("noMobs", true);
        explosionsKeepBlocks = builder
                .comment("Взрывы (гранаты, ракеты, мины, динамит) ранят, но не ломают блоки.")
                .define("explosionsKeepBlocks", true);
        pedestrians = builder
                .comment("Сколько прохожих гуляет вокруг каждого игрока (0 — без прохожих).")
                .defineInRange("pedestrians", 6, 0, 40);
        pedestriansMax = builder
                .comment("Предел прохожих на весь сервер.")
                .defineInRange("pedestriansMax", 48, 0, 400);
        npcFacePlayers = builder
                .comment("Продавцы поворачиваются к подошедшему игроку.")
                .define("npcFacePlayers", true);
        builder.pop();

        builder.comment("Недвижимость").push("estate");
        maxHomes = builder
                .comment("Сколько домов и квартир может купить один игрок.")
                .defineInRange("maxHomes", 3, 1, 100);
        homeSellPercent = builder
                .comment("Сколько процентов цены агентство возвращает при продаже.")
                .defineInRange("homeSellPercent", 70, 0, 100);
        homeLocks = builder
                .comment("Чужой не войдёт в купленный дом, пока владелец в сети,",
                         "и никогда не сломает и не построит ничего на его участке.")
                .define("homeLocks", true);
        homeUpkeepPerMille = builder
                .comment("Коммуналка за игровые сутки в промилле от цены дома (3 = 0,3%; 0 — без неё).")
                .defineInRange("homeUpkeepPerMille", 3, 0, 100);
        homeDebtDays = builder
                .comment("Через сколько суток неоплаченной коммуналки дом отходит городу.")
                .defineInRange("homeDebtDays", 7, 1, 365);
        maxBusinesses = builder
                .comment("Сколько бизнесов (магазинов, складов, офисов) может купить один игрок.")
                .defineInRange("maxBusinesses", 3, 0, 100);
        businessIncomePerMille = builder
                .comment("Доход бизнеса за игровые сутки в промилле от цены (15 = 1,5%).")
                .defineInRange("businessIncomePerMille", 15, 0, 1000);
        builder.pop();

        builder.comment("Экстренные службы 112").push("emergency");
        responderDelay = builder
                .comment("Через сколько секунд после вызова приезжает наряд.")
                .defineInRange("responderDelay", 30, 0, 600);
        responderStay = builder
                .comment("Сколько секунд наряд остаётся на месте, если его не отпустили.")
                .defineInRange("responderStay", 180, 30, 3600);
        medicFee = builder
                .comment("Плата за лечение у фельдшера скорой (0 — бесплатно).")
                .defineInRange("medicFee", 300, 0, 100_000);
        finePerStar = builder
                .comment("Штраф за каждую звезду розыска при задержании.")
                .defineInRange("finePerStar", 1000, 0, 1_000_000);
        jailSecondsPerStar = builder
                .comment("Сколько секунд в камере за каждую звезду розыска.")
                .defineInRange("jailSecondsPerStar", 60, 0, 3600);
        hospitalRespawn = builder
                .comment("Без своей кровати игрок возрождается у городской больницы.")
                .define("hospitalRespawn", true);
        hospitalBill = builder
                .comment("Сколько стоит лечение после смерти (со счёта; нет денег — бесплатно).")
                .defineInRange("hospitalBill", 200, 0, 1_000_000);
        builder.pop();

        builder.comment("Ограбления: касса магазина и банкомат").push("crime");
        shopRobSeconds = builder
                .comment("Сколько секунд стоять у кассы, пока продавец не отдаст выручку.")
                .defineInRange("shopRobSeconds", 30, 5, 600);
        shopRobLoot = builder
                .comment("Сколько может быть в кассе: выпадает от половины до этой суммы.")
                .defineInRange("shopRobLoot", 4000, 0, 1_000_000);
        atmRobSeconds = builder
                .comment("Сколько секунд вскрывать банкомат отмычкой.")
                .defineInRange("atmRobSeconds", 45, 5, 600);
        atmRobLoot = builder
                .comment("Сколько может быть в банкомате: от половины до этой суммы.")
                .defineInRange("atmRobLoot", 9000, 0, 1_000_000);
        robCooldownMinutes = builder
                .comment("Через сколько минут ограбленная касса или банкомат снова с деньгами.")
                .defineInRange("robCooldownMinutes", 20, 0, 1440);
        citizenCashMax = builder
                .comment("Сколько наличных может выпасть из убитого прохожего (от 10% до этой суммы).")
                .defineInRange("citizenCashMax", 300, 0, 100_000);
        patrolPercent = builder
                .comment("Какая доля прохожих — патрульные полицейские с пистолетом, в процентах.")
                .defineInRange("patrolPercent", 12, 0, 100);
        policeShoot = builder
                .comment("Полиция окликает игрока с оружием в руках и стреляет, если он его не убрал.")
                .define("policeShoot", true);
        policeRange = builder
                .comment("С какого расстояния полиция замечает оружие, в блоках.")
                .defineInRange("policeRange", 20, 4, 64);
        policeDamage = builder
                .comment("Урон одного попадания полицейского (20 — полное здоровье).")
                .defineInRange("policeDamage", 4.0D, 0.0D, 40.0D);
        builder.pop();

        builder.comment("Подработки").push("jobs");
        jobPayPercent = builder
                .comment("Множитель оплаты заданий в процентах (100 — как задумано).")
                .defineInRange("jobPayPercent", 100, 0, 1000);
        builder.pop();

        builder.comment("Резервные копии мира (папка backups рядом с сервером)").push("backup");
        backupMinutes = builder
                .comment("Как часто делать копию мира, в минутах. 0 — не делать.")
                .defineInRange("backupMinutes", 60, 0, 10_080);
        backupKeep = builder
                .comment("Сколько последних копий хранить; старые удаляются.")
                .defineInRange("backupKeep", 24, 1, 1000);
        backupSingleplayer = builder
                .comment("Делать копии и в одиночной игре (по умолчанию только на сервере).")
                .define("backupSingleplayer", false);
        builder.pop();

        builder.comment("Умные замки").push("locks");
        lockpickChance = builder
                .comment("Шанс вскрыть замок отмычкой за одну попытку.")
                .defineInRange("lockpickChance", 0.25D, 0.0D, 1.0D);
        lockAutoCloseTicks = builder
                .comment("Через сколько тиков замок закрывается сам (20 тиков = 1 секунда).")
                .defineInRange("lockAutoCloseTicks", 120, 20, 12000);
        lockpickAlarm = builder
                .comment("Поднимать тревогу и уведомлять владельца при неудачном вскрытии.")
                .define("lockpickAlarm", true);
        builder.pop();

        builder.comment("Сетевая игра (мир, открытый по LAN или через Radmin/Hamachi, и сервер)").push("network");
        lanViewDistance = builder
                .comment("Когда к открытому миру подключился друг, дальность прорисовки хозяина (а значит,",
                        "и сервера) снижается до стольких чанков: каждый лишний чанк — это данные, которые",
                        "идут другу по сети. Дальше город дорисует Distant Horizons. 0 — не трогать.")
                .defineInRange("lanViewDistance", 8, 0, 32);
        lanSimulationDistance = builder
                .comment("То же для дальности симуляции (где работают жители, машины, механизмы).")
                .defineInRange("lanSimulationDistance", 6, 0, 32);
        tuneDistantHorizons = builder
                .comment("Distant Horizons отдаёт игрокам дальнюю прорисовку по той же сети, что и игра.",
                        "По умолчанию он шлёт до 500 КБ/с каждому и не подстраивается под канал — через",
                        "VPN это забивает соединение, и игрок не может сдвинуться. Если включено, мод",
                        "ставит ограничение ниже и включает подстройку скорости (только если значения",
                        "в config/DistantHorizons.toml не меняли вручную).")
                .define("tuneDistantHorizons", true);
        dhBandwidthPerPlayer = builder
                .comment("Сколько КБ/с Distant Horizons может отдавать одному игроку.")
                .defineInRange("dhBandwidthPerPlayer", 96, 16, 100_000);
        builder.pop();
    }

    static {
        Pair<CityConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(CityConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
