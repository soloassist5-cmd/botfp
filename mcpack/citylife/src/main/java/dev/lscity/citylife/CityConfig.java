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
    }

    static {
        Pair<CityConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(CityConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
