package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/** Умный дом: привязанные замки, клик открывает или закрывает на расстоянии. */
class LocksApp extends DeviceApp {

    LocksApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("locks");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private int[] row(int[] area, int index) {
        return new int[]{area[0], area[1] + index * 27, area[2], 24};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        ListTag locks = screen.list("locks");
        if (locks.isEmpty()) {
            screen.empty(g, area, "citylife.phone.no_locks");
            return;
        }
        for (int i = 0; i < locks.size(); i++) {
            int[] r = row(area, i);
            if (r[1] + r[3] > area[1] + area[3]) {
                break;
            }
            CompoundTag entry = locks.getCompound(i);
            screen.card(g, r, screen.inside(mouseX, mouseY, r));
            screen.text(g, screen.trim(entry.getString("label"), r[2] - 70), r[0] + 8, r[1] + 3,
                    t.text());
            screen.text(g, entry.getInt("dist") + " м", r[0] + 8, r[1] + 13, t.dim());
            boolean open = entry.getBoolean("open");
            String state = Component.translatable(open
                    ? "citylife.lock.open" : "citylife.lock.closed").getString();
            screen.text(g, state, r[0] + r[2] - 8 - screen.font().width(state), r[1] + 8,
                    open ? t.green() : t.dim());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        ListTag locks = screen.list("locks");
        for (int i = 0; i < locks.size(); i++) {
            if (screen.inside(mx, my, row(area, i))) {
                CompoundTag args = new CompoundTag();
                args.putLong("pos", locks.getCompound(i).getLong("pos"));
                screen.send("lock_toggle", args);
                return true;
            }
        }
        return false;
    }
}
