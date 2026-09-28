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
                .comment("Сколько последних сообщений хранится у игрока.")
                .defineInRange("maxMessages", 50, 5, 500);
        emergencyToEveryone = builder
                .comment("Экстренный вызов 112 виден всем игрокам.",
                         "Выключи, если на сервере есть отдельная роль полиции (тогда видят только операторы).")
                .define("emergencyToEveryone", true);
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
