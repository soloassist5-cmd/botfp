package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * «Мой дом»: купленное жильё и дома, от которых дали ключи.
 * Кнопка «Маршрут» ведёт навигатор к двери, поле ника — выдать ключ.
 * Купить или продать дом можно только у риелтора.
 */
class HomesApp extends DeviceApp {

    private int offset;

    HomesApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("homes");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void init(int[] area) {
        screen.input("home_key", area[0] + 4, area[1] + 4, area[2] - 90, "citylife.realty.key_hint", 16);
    }

    private int[] giveRect(int[] area) {
        return new int[]{area[0] + area[2] - 80, area[1], 80, 16};
    }

    private int[] routeRect(int[] area, int row) {
        return new int[]{area[0] + area[2] - 62, area[1] + 22 + row * 46 + 26, 58, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        ListTag homes = screen.list("homes");
        screen.card(g, new int[]{area[0], area[1], area[2] - 84, 16}, false);
        int[] give = giveRect(area);
        screen.button(g, give[0], give[1], give[2],
                Component.translatable("citylife.realty.key_give").getString(), t.button(),
                mouseX, mouseY);
        if (homes.isEmpty()) {
            screen.empty(g, new int[]{area[0], area[1] + 20, area[2], area[3] - 20},
                    "citylife.homes.empty");
            return;
        }
        for (int row = 0; offset + row < homes.size(); row++) {
            int y = area[1] + 22 + row * 46;
            if (y + 44 > area[1] + area[3]) {
                break;
            }
            CompoundTag home = homes.getCompound(offset + row);
            screen.card(g, new int[]{area[0], y, area[2], 44}, false);
            screen.text(g, home.getString("title") + (home.getBoolean("mine") ? ""
                    : " · " + Component.translatable("citylife.homes.guest",
                    home.getString("owner")).getString()), area[0] + 5, y + 3, t.accent());
            screen.text(g, screen.trim(home.getString("address"), area[2] - 10), area[0] + 5,
                    y + 14, t.text());
            StringBuilder keys = new StringBuilder();
            for (Tag key : home.getList("keys", Tag.TAG_STRING)) {
                keys.append(keys.length() == 0 ? "" : ", ").append(key.getAsString());
            }
            String line = home.getInt("distance") + " м" + (keys.length() == 0 ? ""
                    : " · " + Component.translatable("citylife.realty.keys").getString() + " " + keys);
            screen.text(g, screen.trim(line, area[2] - 72), area[0] + 5, y + 30, t.dim());
            int[] r = routeRect(area, row);
            screen.button(g, r[0], r[1], r[2],
                    Component.translatable("citylife.realty.route").getString(), t.button(),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        ListTag homes = screen.list("homes");
        if (screen.inside(mx, my, giveRect(area))) {
            // Ключ выдаётся от первого своего дома в списке.
            for (int i = 0; i < homes.size(); i++) {
                CompoundTag home = homes.getCompound(i);
                if (home.getBoolean("mine") && !screen.value("home_key").isBlank()) {
                    CompoundTag args = new CompoundTag();
                    args.putString("id", home.getString("id"));
                    args.putString("name", screen.value("home_key"));
                    screen.send("home_trust", args);
                    screen.clear("home_key");
                    break;
                }
            }
            return true;
        }
        for (int row = 0; offset + row < homes.size(); row++) {
            if (screen.inside(mx, my, routeRect(area, row))) {
                CompoundTag args = new CompoundTag();
                args.putString("id", homes.getCompound(offset + row).getString("id"));
                screen.send("home_route", args);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        offset = Math.max(0, Math.min(screen.list("homes").size() - 1,
                offset - (int) Math.signum(delta)));
        return true;
    }
}
