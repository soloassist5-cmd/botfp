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
 * телефонов памяти на два, у флагманов — на всё. Установленное стоит
 * сверху (его ищут, чтобы удалить), список листается колесом и пальцем.
 */
class StoreApp extends DeviceApp {

    private int offset;
    private double pressY = -1;
    private double pressX;
    private double dragged;

    StoreApp(DeviceScreen screen) {
        super(screen);
    }

    /** Список магазина: сначала установленное, потом остальное. */
    private java.util.List<CompoundTag> entries() {
        java.util.List<CompoundTag> out = new java.util.ArrayList<>();
        ListTag store = screen.list("store");
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < store.size(); i++) {
                CompoundTag entry = store.getCompound(i);
                if (entry.getBoolean("installed") == (pass == 0)) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    private int[] list(int[] area) {
        return new int[]{area[0], area[1] + 14, area[2], area[3] - 14};
    }

    private void clamp(int[] area) {
        int content = entries().size() * 40;
        offset = Math.max(0, Math.min(offset, Math.max(0, content - list(area)[3])));
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
        return new int[]{area[0], area[1] + 14 + index * 40 - offset, area[2], 36};
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
        java.util.List<CompoundTag> store = entries();
        if (store.isEmpty()) {
            screen.empty(g, area, "citylife.store.all_installed");
        }
        clamp(area);
        int[] box = list(area);
        g.enableScissor(box[0], box[1], box[0] + box[2], box[1] + box[3]);
        for (int i = 0; i < store.size(); i++) {
            int[] r = row(area, i);
            if (r[1] + r[3] < box[1] || r[1] > box[1] + box[3]) {
                continue;
            }
            CompoundTag entry = store.get(i);
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
                    installed ? 0xFFB03434 : 0xFF1F8F57, mouseX, mouseY);
        }
        g.disableScissor();
        screen.scrollbar(g, box, offset, store.size() * 40);
    }

    @Override
    public boolean scroll(double delta) {
        offset -= (int) Math.signum(delta) * 40;
        return true;
    }

    @Override
    public boolean drag(double mx, double my, double dy, int[] area) {
        if (pressY < 0) {
            return false;
        }
        dragged += Math.abs(dy);
        offset -= (int) Math.round(dy);
        clamp(area);
        return true;
    }

    @Override
    public boolean release(double mx, double my, int[] area) {
        if (pressY < 0) {
            return false;
        }
        boolean tap = dragged < 4;
        double x = pressX;
        double y = pressY;
        pressY = -1;
        if (!tap) {
            return true;
        }
        java.util.List<CompoundTag> store = entries();
        int[] box = list(area);
        for (int i = 0; i < store.size() && y >= box[1] && y <= box[1] + box[3]; i++) {
            if (screen.inside(x, y, row(area, i))) {
                CompoundTag entry = store.get(i);
                CompoundTag args = new CompoundTag();
                args.putString("app", entry.getString("app"));
                screen.send(entry.getBoolean("installed") ? "app_remove" : "app_install", args);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (!screen.inside(mx, my, list(area))) {
            return false;
        }
        pressX = mx;
        pressY = my;
        dragged = 0;
        return true;
    }
}
