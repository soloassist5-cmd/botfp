package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * «Мой транспорт»: свои машины и те, от которых дали ключ.
 * У своих — «Запереть»/«Открыть», у всех — «Маршрут» до места, где машина
 * стоит. Ключ другу — командой /car trust.
 */
class CarsApp extends DeviceApp {

    private static final int ROW = 46;
    private int offset;
    private long lastRefresh;

    CarsApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("cars");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private int[] routeRect(int[] area, int row) {
        return new int[]{area[0] + area[2] - 62, area[1] + row * ROW + 26, 58, 16};
    }

    private int[] lockRect(int[] area, int row) {
        return new int[]{area[0] + area[2] - 128, area[1] + row * ROW + 26, 62, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (now - lastRefresh > 3000) {
            lastRefresh = now;
            screen.send("refresh");
        }
        DeviceScreen.Theme t = screen.theme();
        ListTag cars = screen.list("cars");
        if (cars.isEmpty()) {
            screen.empty(g, area, "citylife.car.empty");
            return;
        }
        for (int row = 0; offset + row < cars.size(); row++) {
            int y = area[1] + row * ROW;
            if (y + ROW - 2 > area[1] + area[3]) {
                break;
            }
            CompoundTag car = cars.getCompound(offset + row);
            boolean mine = car.getBoolean("mine");
            boolean locked = car.getBoolean("locked");
            screen.card(g, new int[]{area[0], y, area[2], ROW - 2}, false);
            String name = Component.translatable(car.getString("model")).getString();
            screen.text(g, screen.trim(name + (mine ? "" : " · " + Component.translatable(
                    "citylife.homes.guest", car.getString("owner")).getString()), area[2] - 10),
                    area[0] + 5, y + 3, t.accent());
            int distance = car.getInt("distance");
            String where = (distance < 0 ? Component.translatable("citylife.car.elsewhere").getString()
                    : distance + " м") + " · " + Component.translatable(locked
                    ? "citylife.car.state_locked" : "citylife.car.state_open").getString().trim();
            screen.text(g, where, area[0] + 5, y + 14, locked ? t.green() : t.dim());
            StringBuilder keys = new StringBuilder();
            for (Tag key : car.getList("keys", Tag.TAG_STRING)) {
                keys.append(keys.length() == 0 ? "" : ", ").append(key.getAsString());
            }
            if (keys.length() > 0) {
                screen.text(g, screen.trim(Component.translatable("citylife.realty.keys").getString()
                        + " " + keys, area[2] - 140), area[0] + 5, y + 30, t.dim());
            }
            if (mine) {
                int[] l = lockRect(area, row);
                screen.button(g, l[0], l[1], l[2], Component.translatable(locked
                        ? "citylife.car.unlock" : "citylife.car.lock").getString(),
                        locked ? t.button() : t.green(), mouseX, mouseY);
            }
            int[] r = routeRect(area, row);
            screen.button(g, r[0], r[1], r[2],
                    Component.translatable("citylife.realty.route").getString(), t.button(),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        ListTag cars = screen.list("cars");
        for (int row = 0; offset + row < cars.size(); row++) {
            CompoundTag car = cars.getCompound(offset + row);
            CompoundTag args = new CompoundTag();
            args.putString("id", car.getString("id"));
            if (car.getBoolean("mine") && screen.inside(mx, my, lockRect(area, row))) {
                screen.send(car.getBoolean("locked") ? "car_unlock" : "car_lock", args);
                lastRefresh = 0;
                return true;
            }
            if (screen.inside(mx, my, routeRect(area, row))) {
                screen.send("car_route", args);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        offset = Math.max(0, Math.min(screen.list("cars").size() - 1,
                offset - (int) Math.signum(delta)));
        return true;
    }
}
