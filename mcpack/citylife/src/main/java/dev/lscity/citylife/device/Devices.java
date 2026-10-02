package dev.lscity.citylife.device;

import dev.lscity.citylife.device.DeviceModel.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Все гаджеты города и правила их приложений.
 *
 * Приложения делятся на три группы. Сетевым нужен интернет: SIM в телефоне
 * или провод/Wi‑Fi у ноутбука и компьютера. Сообщениям и контактам нужен ещё
 * и номер, поэтому их нет на ноутбуке. Остальное работает всегда, а 112, как
 * в жизни, дозванивается и без SIM-карты.
 */
public final class Devices {

    /** Без интернета не открываются. */
    public static final Set<String> NETWORK_APPS =
            Set.of("messages", "contacts", "bank", "navigator", "locks", "store", "marketplace",
                    "cameras", "browser", "mail", "jobs", "homes", "cars", "phone");

    /** Что можно доставить из магазина приложений, если этого нет в прошивке. */
    public static final List<String> STORE_APPS =
            List.of("tetris", "snake", "calc", "notes", "compass", "cameras", "browser", "mail",
                    "music", "mines", "game2048", "terminal", "jobs", "homes", "cars");

    private static final List<String> BASIC = List.of(
            "messages", "phone", "contacts", "bank", "navigator", "jobs", "homes", "cars", "locks", "cameras", "sos",
            "store", "marketplace", "settings");

    public static final DeviceModel LS_PHONE = new DeviceModel("smartphone", Kind.PHONE,
            true, 4, BASIC, 9000, 0xFF0E1018);

    public static final DeviceModel NOKTA = new DeviceModel("phone_nokta", Kind.PHONE,
            true, 0, List.of("messages", "phone", "contacts", "sos", "snake", "calc", "settings"),
            1500, 0xFF1D2A33);

    public static final DeviceModel GRAN_A5 = new DeviceModel("phone_gran_a5", Kind.PHONE,
            true, 2, List.of("messages", "phone", "contacts", "bank", "navigator", "jobs", "homes", "cars", "sos", "store",
            "marketplace", "settings"), 6000, 0xFF26303F);

    public static final DeviceModel GRAN_X = new DeviceModel("phone_gran_x", Kind.PHONE,
            true, 10, List.of("messages", "phone", "contacts", "bank", "navigator", "jobs", "homes", "cars", "locks", "cameras", "sos",
            "store", "marketplace", "browser", "notes", "calc", "compass", "flashlight",
            "settings"),
            30000, 0xFF1A1A1E);

    public static final DeviceModel POLUS = new DeviceModel("phone_polus", Kind.PHONE,
            true, 2, List.of("messages", "phone", "contacts", "navigator", "sos", "compass", "flashlight",
            "store", "settings"), 12000, 0xFF3B4A2C);

    public static final DeviceModel FOLD = new DeviceModel("phone_fold", Kind.PHONE,
            true, 10, List.of("messages", "phone", "contacts", "bank", "navigator", "jobs", "homes", "cars", "locks", "cameras", "sos",
            "store", "marketplace", "browser", "mail", "notes", "calc", "settings"), 40000,
            0xFF2A2440);

    public static final DeviceModel MINIFON = new DeviceModel("phone_mini", Kind.PHONE,
            true, 2, List.of("messages", "phone", "contacts", "navigator", "sos", "tetris", "snake",
            "store", "settings"), 4000, 0xFF3A7BD5);

    public static final DeviceModel TABLET = new DeviceModel("tablet", Kind.TABLET,
            true, 10, List.of("messages", "phone", "contacts", "bank", "navigator", "jobs", "homes", "cars", "locks", "cameras",
            "store", "marketplace", "browser", "mail", "music", "notes", "calc", "settings"),
            18000, 0xFF20242E);

    public static final DeviceModel LAPTOP = new DeviceModel("laptop", Kind.LAPTOP,
            false, 10, List.of("browser", "mail", "bank", "marketplace", "cameras", "locks",
            "navigator", "jobs", "homes", "cars", "music", "notes", "calc", "terminal", "mines", "game2048", "store",
            "settings"), 28000, 0xFF2C3038);

    /**
     * Телефон Старка: стеклянный корпус, сквозь который видно мир. В магазинах
     * его нет — печатается на 3D-принтере в башне STARK. Всё, что есть у
     * флагмана, плюс почта, музыка, терминал и игры прямо в прошивке.
     */
    public static final DeviceModel STARK = new DeviceModel("phone_stark", Kind.PHONE,
            true, 12, List.of("messages", "phone", "contacts", "bank", "navigator", "jobs", "homes", "cars", "locks",
            "cameras", "sos", "store", "marketplace", "browser", "mail", "music", "notes", "calc", "compass",
            "flashlight", "terminal", "tetris", "snake", "game2048", "settings"), 0, 0x3384E1FF);

    /** Компьютер собирается из комплектующих, отдельным предметом не продаётся. */
    public static final DeviceModel COMPUTER = new DeviceModel("computer", Kind.COMPUTER,
            false, 10, computerApps(), 0, 0xFF15171C);

    /** Компьютер: всё, что у ноутбука, плюс сотня программ каталога; магазин и настройки — в конце. */
    private static List<String> computerApps() {
        List<String> apps = new java.util.ArrayList<>(List.of("browser", "mail", "bank", "marketplace",
                "cameras", "locks", "navigator", "jobs", "homes", "cars", "music", "notes", "calc",
                "terminal", "sysinfo", "mines", "game2048", "tetris", "snake"));
        apps.addAll(PcCatalog.ALL);
        apps.add("store");
        apps.add("settings");
        return List.copyOf(apps);
    }

    public static final Map<String, DeviceModel> BY_ID = new LinkedHashMap<>();

    static {
        for (DeviceModel model : List.of(LS_PHONE, NOKTA, GRAN_A5, GRAN_X, POLUS, FOLD, MINIFON, STARK,
                TABLET, LAPTOP, COMPUTER)) {
            BY_ID.put(model.id(), model);
        }
    }

    /** Предметы-гаджеты: всё, кроме компьютера. */
    public static List<DeviceModel> items() {
        return BY_ID.values().stream().filter(m -> m.kind() != Kind.COMPUTER).toList();
    }

    public static DeviceModel get(String id) {
        return BY_ID.getOrDefault(id, LS_PHONE);
    }

    private Devices() {
    }
}
