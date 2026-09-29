package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.client.ui.Wallpapers;
import dev.lscity.citylife.item.SimCardItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Настройки: модель, SIM-карта (номер, вынуть, вставить) и обои. */
class SettingsApp extends DeviceApp {

    SettingsApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("settings");
    }

    private boolean hasSimSlot() {
        return screen.data().getBoolean("simSlot");
    }

    private String simButton() {
        return Component.translatable(screen.sim() == 0
                ? "citylife.sim.insert" : "citylife.sim.eject").getString();
    }

    private int[] simButtonRect(int[] area) {
        int w = screen.buttonWidth(simButton());
        return new int[]{area[0] + area[2] - w - 4, area[1] + 20, w, 16};
    }

    private int wallpaperTop(int[] area) {
        return area[1] + (hasSimSlot() || screen.frame().monochrome() ? 48 : 22);
    }

    private int[] wallpaperRect(int[] area, int index) {
        int columns = area[2] > 260 ? 4 : 2;
        int gap = 8;
        int w = (area[2] - gap * (columns - 1)) / columns;
        int rows = (Wallpapers.ALL.size() + columns - 1) / columns;
        int h = Math.min(w * 3 / 2, (area[1] + area[3] - wallpaperTop(area) - 14 - rows * 14) / rows);
        int col = index % columns;
        int row = index / columns;
        return new int[]{area[0] + col * (w + gap), wallpaperTop(area) + 12 + row * (h + 14), w, h};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        screen.text(g, Component.translatable("citylife.device." + screen.model().id()).getString(),
                area[0], area[1] + 2, t.text());
        if (hasSimSlot()) {
            int[] card = {area[0], area[1] + 16, area[2], 24};
            screen.card(g, card, false);
            String sim = screen.sim() == 0
                    ? Component.translatable("citylife.sim.none").getString()
                    : Component.translatable("citylife.sim.number",
                    SimCardItem.format(screen.sim())).getString();
            screen.text(g, sim, card[0] + 8, card[1] + 8, screen.sim() == 0 ? t.red() : t.text());
            int[] b = simButtonRect(area);
            screen.button(g, b[0], b[1], b[2], simButton(),
                    screen.sim() == 0 ? 0xFF1F8F57 : 0xFF3A3F50, mouseX, mouseY);
        }
        if (screen.frame().monochrome()) {
            screen.text(g, Component.translatable("citylife.device.mono").getString(),
                    area[0], wallpaperTop(area), t.dim());
            return;
        }
        screen.text(g, Component.translatable("citylife.phone.wallpaper").getString(),
                area[0], wallpaperTop(area), t.dim());
        String current = screen.wallpaper();
        for (int i = 0; i < Wallpapers.ALL.size(); i++) {
            int[] r = wallpaperRect(area, i);
            String id = Wallpapers.ALL.get(i);
            Wallpapers.thumbnail(g, id, r[0], r[1], r[2], r[3], id.equals(current));
            if (screen.inside(mouseX, mouseY, r)) {
                PhoneUi.roundedOutline(g, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 7, t.accent());
            }
            screen.fitted(g, Component.translatable("citylife.wallpaper." + id).getString(),
                    r[0] + r[2] / 2, r[1] + r[3] + 3, r[2], id.equals(current) ? t.text() : t.dim());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (hasSimSlot() && screen.inside(mx, my, simButtonRect(area))) {
            screen.send(screen.sim() == 0 ? "sim_insert" : "sim_eject");
            return true;
        }
        if (screen.frame().monochrome()) {
            return false;
        }
        for (int i = 0; i < Wallpapers.ALL.size(); i++) {
            if (screen.inside(mx, my, wallpaperRect(area, i))) {
                CompoundTag args = new CompoundTag();
                args.putString("id", Wallpapers.ALL.get(i));
                screen.send("wallpaper", args);
                return true;
            }
        }
        return false;
    }
}
