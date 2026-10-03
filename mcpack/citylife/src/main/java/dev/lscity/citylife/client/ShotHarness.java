package dev.lscity.citylife.client;

import dev.lscity.citylife.CityLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Снимки экранов для разработки: запускается только с -Dcitylife.shots=1.
 *
 * На титульном экране по очереди открывает окна мода с подставными данными
 * (рабочий стол компьютера, каждую программу, телефон, кассу, «Мой
 * транспорт»), делает скриншот каждого в screenshots/ и закрывает игру.
 * Так интерфейс проверяется без живого сервера и без игрока.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class ShotHarness {

    private static final boolean ON = Boolean.getBoolean("citylife.shots");
    /** Режим кадров карты: грузит мир saves/scenes и снимает точки из scenes.txt. */
    private static final boolean SCENES = Boolean.getBoolean("citylife.scenes");
    private static final List<String[]> VIEWS = new ArrayList<>();
    private static int view = -1;
    private static boolean opening;
    private static int titleTicks;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int index = -1;
    private static int wait;

    private record Shot(String name, Supplier<Screen> screen, Runnable after) {
    }

    private ShotHarness() {
    }

    private static CompoundTag device(String model, String kind, List<String> apps) {
        CompoundTag tag = new CompoundTag();
        CompoundTag ctx = new CompoundTag();
        ctx.putLong("pc", 0L);
        tag.put("ctx", ctx);
        tag.putString("model", model);
        tag.putString("kind", kind);
        tag.putBoolean("online", true);
        tag.putInt("sim", 4821);
        tag.putLong("balance", 48250);
        tag.putLong("cash", 3200);
        tag.putLong("daytime", 6000);
        tag.putString("owner", "Tester");
        tag.putString("wallpaper", "sunset");
        ListTag list = new ListTag();
        apps.forEach(a -> list.add(StringTag.valueOf(a)));
        tag.put("apps", list);
        ListTag removable = new ListTag();
        removable.add(StringTag.valueOf("tetris"));
        tag.put("removable", removable);
        ListTag statement = new ListTag();
        long[] amounts = {4500, -1200, 800, -300, 2500, -640, 400, -2000};
        String[] texts = {"Зарплата: курьер", "Покупка: Закусочная", "Перевод от Анны", "Коммуналка",
                "Доход бизнеса", "Покупка: Аптека", "Чаевые", "Покупка: Магазин техники"};
        for (int i = 0; i < amounts.length; i++) {
            CompoundTag line = new CompoundTag();
            line.putLong("amount", amounts[i]);
            line.putString("text", texts[i]);
            line.putLong("ago", i * 300L);
            statement.add(line);
        }
        tag.put("statement", statement);
        return tag;
    }

    private static void plan() {
        var computer = dev.lscity.citylife.device.Devices.COMPUTER;
        CompoundTag pc = device("computer", "COMPUTER", computer.apps());
        SHOTS.add(new Shot("pc_home", () -> new dev.lscity.citylife.client.device.DeviceScreen(pc), null));
        for (int page = 1; page <= 3; page++) {
            int rows = page * 4;
            SHOTS.add(new Shot("pc_home_" + page, () -> {
                var screen = new dev.lscity.citylife.client.device.DeviceScreen(pc.copy());
                Minecraft.getInstance().setScreen(screen);
                for (int i = 0; i < rows; i++) {
                    screen.mouseScrolled(0, 0, -1);
                }
                return screen;
            }, null));
        }
        for (String app : dev.lscity.citylife.device.PcCatalog.ALL) {
            SHOTS.add(new Shot("pc_" + app, () -> {
                var screen = new dev.lscity.citylife.client.device.DeviceScreen(pc.copy());
                Minecraft.getInstance().setScreen(screen);
                screen.open(app);
                return screen;
            }, null));
        }
        var phoneModel = dev.lscity.citylife.device.Devices.LS_PHONE;
        List<String> phoneApps = new ArrayList<>(phoneModel.apps());
        phoneApps.addAll(List.of("tetris", "snake", "calc", "notes"));
        CompoundTag phone = device("smartphone", "PHONE", phoneApps);
        SHOTS.add(new Shot("phone_home", () -> new dev.lscity.citylife.client.device.DeviceScreen(phone), null));

        CompoundTag checkout = new CompoundTag();
        checkout.putString("title", "Закусочная");
        checkout.putUUID("clerk", java.util.UUID.randomUUID());
        ListTag items = new ListTag();
        String[] ids = {"minecraft:bread", "minecraft:cooked_beef", "minecraft:apple", "minecraft:cake",
                "minecraft:pumpkin_pie", "minecraft:cookie"};
        long[] prices = {60, 150, 40, 400, 120, 30};
        for (int i = 0; i < ids.length; i++) {
            CompoundTag e = new CompoundTag();
            e.putInt("i", i);
            CompoundTag stack = new CompoundTag();
            stack.putString("id", ids[i]);
            stack.putByte("Count", (byte) 1);
            e.put("stack", stack);
            e.putLong("price", prices[i]);
            items.add(e);
        }
        checkout.put("items", items);
        checkout.putLong("balance", 48250);
        checkout.putLong("cash", 3200);
        checkout.putBoolean("barter", true);
        checkout.putString("card", "mir");
        checkout.putString("cardNumber", "2200 1234 5678 9012");
        checkout.putBoolean("cardMine", true);
        SHOTS.add(new Shot("checkout", () -> new dev.lscity.citylife.client.screen.CheckoutScreen(checkout), null));
        CompoundTag paid = checkout.copy();
        CompoundTag result = new CompoundTag();
        result.putBoolean("ok", true);
        result.putLong("total", 270);
        result.putString("message", "Оплата прошла");
        paid.put("result", result);
        SHOTS.add(new Shot("checkout_paid", () -> {
            var screen = new dev.lscity.citylife.client.screen.CheckoutScreen(checkout);
            Minecraft.getInstance().setScreen(screen);
            screen.update(paid);
            return screen;
        }, null));

        CompoundTag garage = new CompoundTag();
        ListTag cars = new ListTag();
        String[][] models = {{"entity.vehicle.sports_car", "true", "12"}, {"entity.vehicle.off_roader", "false", "240"},
                {"entity.vehicle.moped", "false", "-1"}};
        for (String[] m : models) {
            CompoundTag car = new CompoundTag();
            car.putString("id", java.util.UUID.randomUUID().toString());
            car.putString("model", m[0]);
            car.putBoolean("mine", true);
            car.putString("owner", "Tester");
            car.putBoolean("locked", Boolean.parseBoolean(m[1]));
            car.putInt("distance", Integer.parseInt(m[2]));
            ListTag keys = new ListTag();
            keys.add(StringTag.valueOf("Anna"));
            car.put("keys", keys);
            cars.add(car);
        }
        garage.put("cars", cars);
        ListTag people = new ListTag();
        for (String n : new String[]{"Anna", "Max"}) {
            CompoundTag p = new CompoundTag();
            p.putString("name", n);
            p.putUUID("id", java.util.UUID.randomUUID());
            people.add(p);
        }
        garage.put("people", people);
        SHOTS.add(new Shot("vehicles", () -> new dev.lscity.citylife.client.screen.VehicleScreen(garage), null));
    }

    /**
     * Кадры карты: мир saves/scenes, точки съёмки — строки scenes.txt в
     * каталоге запуска: «имя x y z поворот наклон». Игрок в режиме
     * наблюдателя телепортируется в точку, ждёт прогрузки чанков и снимает.
     */
    private static void scenes(Minecraft mc) {
        if (view < 0) {
            if (mc.level == null) {
                titleTicks++;
                if (titleTicks % 100 == 0) {
                    CityLife.LOG.info("City Life: кадры карты ждут, экран {}",
                            mc.screen == null ? "нет" : mc.screen.getClass().getName());
                }
                // Приветствие мода (Supplementaries показывает его при новом конфиге) — закрыть.
                if (mc.screen != null && mc.screen.getClass().getSimpleName().equals("WelcomeMessageScreen")
                        && titleTicks % 100 == 50) {
                    mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
                }
                if (opening && mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm
                        && titleTicks % 100 == 50) {
                    // Вопрос при загрузке мира (резервная копия, изменённый набор модов) —
                    // отвечаем «да»: мир для кадров одноразовый.
                    try {
                        var field = net.minecraft.client.gui.screens.ConfirmScreen.class
                                .getDeclaredField("callback");
                        field.setAccessible(true);
                        ((it.unimi.dsi.fastutil.booleans.BooleanConsumer) field.get(confirm)).accept(true);
                    } catch (ReflectiveOperationException e) {
                        CityLife.LOG.error("City Life: не ответить на вопрос экрана", e);
                    }
                }
                if ((mc.screen instanceof TitleScreen || titleTicks > 600) && !opening) {
                    opening = true;
                    try {
                        for (String line : java.nio.file.Files.readAllLines(
                                mc.gameDirectory.toPath().resolve("scenes.txt"))) {
                            String[] parts = line.trim().split("\\s+");
                            if (line.startsWith("/")) {
                                // Команда перед следующим кадром: расставить подставки и т.п.
                                VIEWS.add(new String[]{line.trim()});
                            } else if (parts.length >= 6 && !line.startsWith("#")) {
                                VIEWS.add(parts);
                            }
                        }
                    } catch (java.io.IOException e) {
                        CityLife.LOG.error("City Life: нет scenes.txt", e);
                    }
                    mc.createWorldOpenFlows().loadLevel(mc.screen, "scenes");
                }
                return;
            }
            if (mc.player == null) {
                return;
            }
            for (String command : new String[]{"gamemode spectator", "time set 6000",
                    "gamerule doDaylightCycle false", "weather clear", "gamerule doWeatherCycle false"}) {
                mc.player.connection.sendCommand(command);
            }
            mc.options.hideGui = true;
            view = 0;
            wait = 200;
            return;
        }
        if (wait-- > 0) {
            return;
        }
        if (view > 0 && VIEWS.get(view - 1).length > 1) {
            // Для замеров: сколько кадров в секунду и что в мире вокруг на этой точке.
            CityLife.LOG.info("City Life: кадр {}: {} FPS, {}", VIEWS.get(view - 1)[0], mc.getFps(),
                    mc.levelRenderer.getChunkStatistics() + " · " + mc.levelRenderer.getEntityStatistics());
            Screenshot.grab(mc.gameDirectory, "scene_" + VIEWS.get(view - 1)[0] + ".png",
                    mc.getMainRenderTarget(), msg -> {
                    });
            if (mc.screen != null) {
                mc.setScreen(null);
            }
        }
        if (view >= VIEWS.size()) {
            CityLife.LOG.info("City Life: кадры карты готовы ({})", VIEWS.size());
            mc.stop();
            return;
        }
        String[] p = VIEWS.get(view++);
        if (p.length == 1) {
            mc.player.connection.sendCommand(p[0].substring(1));
            wait = 4;
            return;
        }
        mc.player.connection.sendCommand("tp @s " + p[1] + " " + p[2] + " " + p[3] + " " + p[4] + " " + p[5]);
        wait = p.length > 6 ? Integer.parseInt(p[6]) : 60;
        // Кадры «ui_…»: поверх мира открывается окно — стеклянный телефон Старка,
        // каталог 3D-принтера, пульт охраны.
        Screen ui = sceneScreen(p[0]);
        if (ui != null) {
            mc.setScreen(ui);
        }
    }

    /** Касса настоящего прилавка из каталога — как её пришлёт сервер. */
    private static CompoundTag shopShot(String role) {
        var shop = dev.lscity.citylife.trade.ShopCatalog.BY_ROLE.get(role);
        CompoundTag tag = new CompoundTag();
        tag.putString("title", shop.title());
        tag.putUUID("clerk", java.util.UUID.randomUUID());
        ListTag items = new ListTag();
        for (var line : dev.lscity.citylife.trade.Checkout.lines(shop)) {
            CompoundTag e = new CompoundTag();
            e.putInt("i", line.index());
            e.put("stack", line.goods().save(new CompoundTag()));
            e.putLong("price", line.price());
            String g = shop.offers().get(line.index()).group();
            if (!g.isEmpty()) {
                e.putString("g", g);
            }
            items.add(e);
        }
        tag.put("items", items);
        tag.putLong("balance", 48250);
        tag.putLong("cash", 3200);
        tag.putBoolean("barter", dev.lscity.citylife.trade.Checkout.buysFromPlayers(shop));
        tag.putString("card", "mir");
        tag.putString("cardNumber", "2200 1234 5678 9012");
        tag.putBoolean("cardMine", true);
        return tag;
    }

    private static Screen sceneScreen(String name) {
        var stark = dev.lscity.citylife.device.Devices.STARK;
        switch (name) {
            case "ui_phone_stark" -> {
                return new dev.lscity.citylife.client.device.DeviceScreen(device("phone_stark", "PHONE", stark.apps()));
            }
            case "ui_phone_stark_bank" -> {
                var screen = new dev.lscity.citylife.client.device.DeviceScreen(
                        device("phone_stark", "PHONE", stark.apps()));
                Minecraft.getInstance().setScreen(screen);
                screen.open("bank");
                return screen;
            }
            case "ui_checkout_hardware" -> {
                return new dev.lscity.citylife.client.screen.CheckoutScreen(shopShot("builder"));
            }
            case "ui_checkout_hardware_doors" -> {
                return new dev.lscity.citylife.client.screen.CheckoutScreen(shopShot("builder"))
                        .preset("Двери", "");
            }
            case "ui_checkout_hardware_find" -> {
                return new dev.lscity.citylife.client.screen.CheckoutScreen(shopShot("builder"))
                        .preset("", "ламп");
            }
            case "ui_checkout_corner" -> {
                return new dev.lscity.citylife.client.screen.CheckoutScreen(shopShot("shopkeeper"));
            }
            case "ui_checkout_food" -> {
                return new dev.lscity.citylife.client.screen.CheckoutScreen(shopShot("trader_food"))
                        .preset("Готовая еда", "");
            }
            case "ui_controls" -> {
                return new dev.lscity.citylife.client.controls.ControlsScreen(null);
            }
            case "ui_controls_v" -> {
                return new dev.lscity.citylife.client.controls.ControlsScreen(null).pin("key.keyboard.v");
            }
            case "ui_controls_find" -> {
                return new dev.lscity.citylife.client.controls.ControlsScreen(null).search("карт");
            }
            case "ui_controls_alt" -> {
                return new dev.lscity.citylife.client.controls.ControlsScreen(null).layer("alt");
            }
            case "ui_jarvis" -> {
                CompoundTag dev = device("phone_stark", "PHONE", stark.apps());
                CompoundTag jarvis = new CompoundTag();
                jarvis.putBoolean("allowed", true);
                jarvis.putBoolean("owner", true);
                jarvis.putInt("mark", 33);
                dev.put("jarvis", jarvis);
                var screen = new dev.lscity.citylife.client.device.DeviceScreen(dev);
                Minecraft.getInstance().setScreen(screen);
                screen.open("jarvis");
                return screen;
            }
            case "ui_printer" -> {
                CompoundTag tag = new CompoundTag();
                tag.putLong("pos", new net.minecraft.core.BlockPos(20, 61, 22).asLong());
                tag.putBoolean("cleared", true);
                return new dev.lscity.citylife.client.screen.PrinterScreen(tag);
            }
            case "ui_security" -> {
                CompoundTag tag = new CompoundTag();
                tag.putBoolean("armed", true);
                tag.putBoolean("me", true);
                tag.putInt("cleared", 2);
                tag.putLong("price", dev.lscity.citylife.stark.StarkSecurity.ACCESS_PRICE);
                tag.putLong("balance", 312500);
                ListTag cams = new ListTag();
                for (String n : new String[]{"ЗАЛ БРОНИ · СЗ", "ЗАЛ БРОНИ · СВ", "МАСТЕРСКАЯ", "ВЕСТИБЮЛЬ"}) {
                    CompoundTag c = new CompoundTag();
                    c.putString("name", n);
                    cams.add(c);
                }
                tag.put("cams", cams);
                ListTag log = new ListTag();
                String[][] lines = {{"14:02", "Выдан допуск: Tony", "0"}, {"13:40", "ТРЕВОГА: Ivan — кража костюма из Зала брони", "1"},
                        {"13:39", "Посторонний в зоне: Ivan", "0"}, {"12:15", "Tony печатает: Телефон Старка ×1", "0"}};
                for (String[] l : lines) {
                    CompoundTag e = new CompoundTag();
                    e.putString("t", l[0]);
                    e.putString("s", l[1]);
                    e.putBoolean("a", l[2].equals("1"));
                    log.add(e);
                }
                tag.put("log", log);
                tag.putBoolean("bought", true);
                return new dev.lscity.citylife.client.screen.SecurityScreen(tag);
            }
            default -> {
                return null;
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (SCENES) {
            scenes(mc);
            return;
        }
        if (index < 0) {
            if (!(mc.screen instanceof TitleScreen)) {
                return;
            }
            plan();
            index = 0;
            wait = 40;
            return;
        }
        if (wait-- > 0) {
            return;
        }
        if (index > 0 && index <= SHOTS.size()) {
            String name = SHOTS.get(index - 1).name();
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {
            });
        }
        if (index >= SHOTS.size()) {
            CityLife.LOG.info("City Life: снимки экранов готовы ({})", SHOTS.size());
            mc.stop();
            return;
        }
        Shot shot = SHOTS.get(index++);
        Screen screen = shot.screen().get();
        if (mc.screen != screen) {
            mc.setScreen(screen);
        }
        wait = 12;
    }
}
