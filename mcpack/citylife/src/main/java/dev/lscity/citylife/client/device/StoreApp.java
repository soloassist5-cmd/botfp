package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.AppIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Магазин приложений: игры и утилиты, которых нет в прошивке.
 *
 * Сколько приложений можно поставить, зависит от модели: у бюджетных
 * телефонов памяти на два, у флагманов — на всё.
 */
class StoreApp extends DeviceApp {

    StoreApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("store");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private int[] row(int[] area, int index) {
        return new int[]{area[0], area[1] + 14 + index * 40, area[2], 36};
    }

    private String buttonLabel(boolean installed) {
        return Component.translatable(installed
                ? "citylife.market.remove" : "citylife.market.install").getString();
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        screen.text(g, Component.translatable("citylife.store.free",
                screen.data().getInt("storeFree")).getString(), area[0], area[1], t.dim());
        ListTag store = screen.list("store");
        if (store.isEmpty()) {
            screen.empty(g, area, "citylife.store.all_installed");
        }
        for (int i = 0; i < store.size(); i++) {
            int[] r = row(area, i);
            if (r[1] + r[3] > area[1] + area[3]) {
                break;
            }
            CompoundTag entry = store.getCompound(i);
            String app = entry.getString("app");
            boolean installed = entry.getBoolean("installed");
            screen.card(g, r, screen.inside(mouseX, mouseY, r));
            AppIcons.draw(g, app, r[0] + 5, r[1] + 5, 26);
            String label = buttonLabel(installed);
            int bw = screen.buttonWidth(label);
            screen.text(g, screen.appTitle(app), r[0] + 38, r[1] + 7, t.text());
            screen.text(g, screen.trim(Component.translatable("citylife.market.desc." + app)
                    .getString(), r[2] - bw - 48), r[0] + 38, r[1] + 19, t.dim());
            screen.button(g, r[0] + r[2] - bw - 5, r[1] + 10, 0, label,
                    installed ? 0xFF3A3F50 : 0xFF1F8F57, mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        ListTag store = screen.list("store");
        for (int i = 0; i < store.size(); i++) {
            if (screen.inside(mx, my, row(area, i))) {
                CompoundTag entry = store.getCompound(i);
                CompoundTag args = new CompoundTag();
                args.putString("app", entry.getString("app"));
                screen.send(entry.getBoolean("installed") ? "app_remove" : "app_install", args);
                return true;
            }
        }
        return false;
    }
}
