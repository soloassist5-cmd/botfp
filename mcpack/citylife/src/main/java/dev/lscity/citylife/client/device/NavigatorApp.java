package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.NavClient;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Навигатор: метки города и свои, ближайшие сверху.
 *
 * Клик по метке — маршрут. Своя метка ставится там, где стоишь, вместе с
 * высотой: на крыше, в метро, на пирсе — туда и поведёт навигатор.
 */
class NavigatorApp extends DeviceApp {

    private int scroll;

    NavigatorApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("navigator");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void init(int[] area) {
        screen.input("mark", area[0], area[1] + area[3] - 16, area[2] - markWidth() - 8,
                "citylife.phone.mark_name", 20);
    }

    private int markWidth() {
        return screen.buttonWidth(Component.translatable("citylife.button.mark").getString());
    }

    private int listTop(int[] area) {
        return area[1] + (screen.data().contains("route") ? 32 : 0);
    }

    private int visible(int[] area) {
        return Math.max(1, (area[1] + area[3] - 24 - listTop(area)) / 24);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        CompoundTag route = screen.data().getCompound("route");
        if (!route.isEmpty()) {
            PhoneUi.roundedGradient(g, area[0], area[1], area[2], 28, 7, 0xFF1A6E8C, 0xFF12465A);
            screen.text(g, screen.trim(route.getString("name"), area[2] - 60), area[0] + 8,
                    area[1] + 5, 0xFFFFFFFF);
            screen.text(g, route.getInt("dist") + " м · " + Component.translatable(
                    "citylife.nav.height", route.getInt("y")).getString(), area[0] + 8,
                    area[1] + 16, 0xCCDDF6FF);
            String stop = Component.translatable("citylife.button.stop").getString();
            screen.button(g, area[0] + area[2] - screen.buttonWidth(stop) - 5, area[1] + 6, 0,
                    stop, 0xFFB03434, mouseX, mouseY);
        }
        ListTag points = screen.list("waypoints");
        int y = listTop(area);
        for (int i = scroll; i < points.size() && i < scroll + visible(area); i++) {
            CompoundTag point = points.getCompound(i);
            int[] r = {area[0], y, area[2], 21};
            screen.card(g, r, screen.inside(mouseX, mouseY, r));
            boolean city = point.getBoolean("city");
            PhoneUi.disc(g, r[0] + 10, r[1] + 10, 5, city ? t.accent() : t.green());
            screen.text(g, screen.trim(point.getString("name"), r[2] - 64), r[0] + 20, r[1] + 3,
                    t.text());
            screen.text(g, point.getInt("dist") + " м", r[0] + 20, r[1] + 12, t.dim());
            if (!city) {
                screen.text(g, "×", r[0] + r[2] - 10, r[1] + 7, t.red());
            }
            y += 24;
        }
        int[] field = {area[0] - 3, area[1] + area[3] - 18, area[2] - markWidth() - 2, 16};
        screen.card(g, field, false);
        screen.button(g, area[0] + area[2] - markWidth(), area[1] + area[3] - 18, 0,
                Component.translatable("citylife.button.mark").getString(), t.button(),
                mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (screen.data().contains("route")) {
            String stop = Component.translatable("citylife.button.stop").getString();
            int w = screen.buttonWidth(stop);
            if (screen.inside(mx, my, new int[]{area[0] + area[2] - w - 5, area[1] + 6, w, 16})) {
                screen.send("nav_stop");
                NavClient.clear();
                return true;
            }
        }
        if (screen.inside(mx, my, new int[]{area[0] + area[2] - markWidth(),
                area[1] + area[3] - 18, markWidth(), 16})) {
            CompoundTag args = new CompoundTag();
            args.putString("name", screen.value("mark"));
            screen.send("wp_add", args);
            screen.clear("mark");
            return true;
        }
        ListTag points = screen.list("waypoints");
        int y = listTop(area);
        for (int i = scroll; i < points.size() && i < scroll + visible(area); i++) {
            CompoundTag point = points.getCompound(i);
            int[] r = {area[0], y, area[2], 21};
            if (screen.inside(mx, my, r)) {
                if (!point.getBoolean("city") && mx >= r[0] + r[2] - 16) {
                    CompoundTag args = new CompoundTag();
                    args.putInt("index", point.getInt("index"));
                    screen.send("wp_del", args);
                } else {
                    CompoundTag args = new CompoundTag();
                    args.put("point", point);
                    screen.send("nav_set", args);
                }
                return true;
            }
            y += 24;
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        int max = Math.max(0, screen.list("waypoints").size() - 3);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }
}
