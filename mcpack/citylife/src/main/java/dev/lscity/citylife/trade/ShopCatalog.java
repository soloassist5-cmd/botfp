package dev.lscity.citylife.trade;

import dev.lscity.citylife.trade.Shop.ShopItem;
import dev.lscity.citylife.trade.Shop.ShopOffer;

import java.util.List;
import java.util.Map;

/**
 * Ассортимент прилавков.
 *
 * Файл собран скриптом citylife/tools/gen_shops.py из того же списка,
 * по которому расставляются NPC, — править руками не нужно.
 */
public final class ShopCatalog {

    public static final Map<String, Shop> BY_ROLE = Map.ofEntries(
            Map.entry("banker", new Shop("Городской банк", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 2, null), new ShopItem("citylife:atm", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 3, null), new ShopItem("minecraft:paper", 8, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 1, null), new ShopItem("minecraft:iron_block", 2, null))))),
            Map.entry("bartender", new Shop("Бар", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 4, null), new ShopItem("minecraft:honey_bottle", 2, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:jukebox", 1, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 6, null), new ShopItem("minecraft:music_disc_cat", 1, null))))),
            Map.entry("builder", new Shop("Стройка", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 5, null), new ShopItem("minecraft:scaffolding", 16, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:smooth_stone", 32, null))))),
            Map.entry("car_dealer", new Shop("Автосалон", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_100", 3, null), new ShopItem("vehicle:wrench", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 2, null), new ShopItem("citylife:fuel_canister", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 4, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:moped\",Color:14696762,EngineStack:{id:\"vehicle:iron_small_engine\",Count:1b},WheelStack:{id:\"vehicle:standard_wheel\",Count:1b}},display:{Name:'{\"text\": \"Мопед — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 3, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:mini_bike\",Color:3112928,EngineStack:{id:\"vehicle:iron_small_engine\",Count:1b},WheelStack:{id:\"vehicle:standard_wheel\",Count:1b}},display:{Name:'{\"text\": \"Минибайк — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 9, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:dirt_bike\",Color:15764004,EngineStack:{id:\"vehicle:gold_small_engine\",Count:1b},WheelStack:{id:\"vehicle:off_road_wheel\",Count:1b}},display:{Name:'{\"text\": \"Мотокросс — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 12, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:quad_bike\",Color:4099649,EngineStack:{id:\"vehicle:gold_small_engine\",Count:1b},WheelStack:{id:\"vehicle:off_road_wheel\",Count:1b}},display:{Name:'{\"text\": \"Квадроцикл — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 14, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:atv\",Color:9202238,EngineStack:{id:\"vehicle:gold_small_engine\",Count:1b},WheelStack:{id:\"vehicle:all_terrain_wheel\",Count:1b}},display:{Name:'{\"text\": \"Вездеход ATV — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 2, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:go_kart\",Color:15909166,EngineStack:{id:\"vehicle:diamond_small_engine\",Count:1b},WheelStack:{id:\"vehicle:racing_wheel\",Count:1b}},display:{Name:'{\"text\": \"Картинг — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 8, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:golf_cart\",Color:16053492,EngineStack:{id:\"vehicle:iron_electric_engine\",Count:1b},WheelStack:{id:\"vehicle:standard_wheel\",Count:1b}},display:{Name:'{\"text\": \"Гольф-кар — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 18, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:smart_car\",Color:3054550,EngineStack:{id:\"vehicle:gold_electric_engine\",Count:1b},WheelStack:{id:\"vehicle:standard_wheel\",Count:1b}},display:{Name:'{\"text\": \"Смарт-кар — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 3, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:dune_buggy\",Color:15253850,EngineStack:{id:\"vehicle:gold_small_engine\",Count:1b},WheelStack:{id:\"vehicle:off_road_wheel\",Count:1b}},display:{Name:'{\"text\": \"Багги — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 7, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:off_roader\",Color:1973796,EngineStack:{id:\"vehicle:gold_large_engine\",Count:1b},WheelStack:{id:\"vehicle:off_road_wheel\",Count:1b}},display:{Name:'{\"text\": \"Внедорожник — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 9, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:mini_bus\",Color:14277084,EngineStack:{id:\"vehicle:iron_large_engine\",Count:1b},WheelStack:{id:\"vehicle:standard_wheel\",Count:1b}},display:{Name:'{\"text\": \"Микроавтобус — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 4, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:tractor\",Color:4168506,EngineStack:{id:\"vehicle:iron_large_engine\",Count:1b},WheelStack:{id:\"vehicle:off_road_wheel\",Count:1b}},display:{Name:'{\"text\": \"Трактор — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 6, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:aluminum_boat\",Color:12108492,EngineStack:{id:\"vehicle:iron_small_engine\",Count:1b}},display:{Name:'{\"text\": \"Лодка — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 3, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:jet_ski\",Color:14826346,EngineStack:{id:\"vehicle:gold_small_engine\",Count:1b}},display:{Name:'{\"text\": \"Гидроцикл — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 6, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:speed_boat\",Color:16777215,EngineStack:{id:\"vehicle:gold_large_engine\",Count:1b}},display:{Name:'{\"text\": \"Катер — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 24, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:compact_helicopter\",Color:2830138,EngineStack:{id:\"vehicle:diamond_small_engine\",Count:1b}},display:{Name:'{\"text\": \"Вертолёт — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}")),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 30, null), new ShopItem("vehicle:vehicle_crate", 1, "{BlockEntityTag:{Vehicle:\"vehicle:sports_plane\",Color:12597547,EngineStack:{id:\"vehicle:diamond_large_engine\",Count:1b}},display:{Name:'{\"text\": \"Самолёт — ящик\", \"italic\": false}',Lore:['{\"text\": \"Поставь и ударь сверху гаечным ключом\", \"color\": \"gray\", \"italic\": false}']}}"))))),
            Map.entry("clerk", new Shop("Мэрия", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 4, null), new ShopItem("minecraft:paper", 8, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:map", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 2, null), new ShopItem("minecraft:compass", 1, null))))),
            Map.entry("cook", new Shop("Закусочная", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 4, null), new ShopItem("minecraft:cooked_chicken", 3, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 5, null), new ShopItem("minecraft:pumpkin_pie", 2, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 3, null), new ShopItem("minecraft:cake", 1, null))))),
            Map.entry("cop", new Shop("Магазин", List.of(
))),
            Map.entry("firefighter", new Shop("Магазин", List.of(
))),
            Map.entry("foreman", new Shop("Прораб", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 6, null), new ShopItem("minecraft:stone", 32, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:iron_ingot", 3, null))))),
            Map.entry("fuel_seller", new Shop("Заправка", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 15, null), new ShopItem("citylife:fuel_canister", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 6, null), new ShopItem("citylife:fuel_canister", 5, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 3, null), new ShopItem("vehicle:wrench", 1, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 3, null), new ShopItem("minecraft:cooked_beef", 4, null))))),
            Map.entry("gunsmith", new Shop("Оружейный магазин", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 3, null), new ShopItem("tacz:gun_smith_table", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 3, null), new ShopItem("minecraft:iron_ingot", 8, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 2, null), new ShopItem("minecraft:gunpowder", 8, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 1, null), new ShopItem("tacz:ammo_box", 1, "{Level:0}"))))),
            Map.entry("medic", new Shop("Аптека", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:golden_apple", 1, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 8, null), new ShopItem("minecraft:potion", 1, null))))),
            Map.entry("phone_seller", new Shop("Салон связи", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_100", 3, null), new ShopItem("citylife:sim_card", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 15, null), new ShopItem("citylife:phone_nokta", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 4, null), new ShopItem("citylife:phone_mini", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 6, null), new ShopItem("citylife:phone_gran_a5", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 9, null), new ShopItem("citylife:smartphone", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 12, null), new ShopItem("citylife:phone_polus", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 6, null), new ShopItem("citylife:phone_gran_x", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 8, null), new ShopItem("citylife:phone_fold", 1, null))))),
            Map.entry("realtor", new Shop("Агентство недвижимости", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 1, null), new ShopItem("citylife:smart_lock", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 2, null), new ShopItem("securitycraft:keypad", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 1, null), new ShopItem("securitycraft:security_camera", 2, null))))),
            Map.entry("security", new Shop("Магазин", List.of(
))),
            Map.entry("shopkeeper", new Shop("Магазин", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 3, null), new ShopItem("minecraft:torch", 16, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 5, null), new ShopItem("minecraft:oak_planks", 32, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:iron_pickaxe", 1, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 2, null), new ShopItem("minecraft:bucket", 1, null))))),
            Map.entry("trader_clothes", new Shop("Одежда", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_100", 2, null), new ShopItem("minecraft:leather_chestplate", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:leather_boots", 1, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 8, null), new ShopItem("minecraft:white_dye", 8, null))))),
            Map.entry("trader_food", new Shop("Продукты", List.of(
                    new ShopOffer(new ShopItem("citylife:coin_10", 3, null), new ShopItem("minecraft:bread", 4, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 6, null), new ShopItem("minecraft:cooked_beef", 3, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 1, null), new ShopItem("minecraft:golden_carrot", 4, null)),
                    new ShopOffer(new ShopItem("citylife:coin_10", 2, null), new ShopItem("minecraft:sweet_berries", 6, null))))),
            Map.entry("trader_tech", new Shop("Электроника", List.of(
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 18, null), new ShopItem("citylife:tablet", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 28, null), new ShopItem("citylife:laptop", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_5000", 1, null), new ShopItem("citylife:monitor", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 1, null), new ShopItem("citylife:keyboard", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 6, null), new ShopItem("citylife:mouse", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_100", 25, null), new ShopItem("citylife:pc_case", 1, null)),
                    new ShopOffer(new ShopItem("citylife:banknote_1000", 2, null), new ShopItem("cameracraft:camera", 1, null)))))
    );

    /** Что говорят те, у кого нет прилавка. */
    public static final Map<String, List<String>> LINES = Map.ofEntries(
            Map.entry("cop", List.of("Порядок в городе — моя работа. Проезжай, не задерживайся.", "Оружие носи в кобуре. Увижу в руках — разговор будет другой.", "Потерял машину? Смотри на стоянке у мэрии, туда всё свозят.")),
            Map.entry("firefighter", List.of("Огонь в жилом квартале — сразу к нам, не тушите сами.", "Каска на голове, вода в баке. Живём.", "Без учений скучно, с учениями тяжело.")),
            Map.entry("security", List.of("Вход свободный, но за витрины отвечаешь ты.", "Сумки на входе не проверяем. Пока.", "Драку начнёшь — вынесу на улицу сам.")),
            Map.entry("state", List.of("Приём граждан по будням. Сегодня, считай, будни.", "Все бумаги — в мэрию, там и очередь короче."))
    );

    /** Реплика на случай роли без своего текста. */
    public static final List<String> DEFAULT_LINES = List.of("Добрый день. Хорошего дня в городе.");

    private ShopCatalog() {
    }
}
