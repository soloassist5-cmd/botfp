package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Фаза луны: картинка, название и сколько дней до полнолуния. */
class MoonApp extends PcApp {

    private static final String[] NAMES = {"Полнолуние", "Убывающая луна", "Последняя четверть",
            "Старая луна", "Новолуние", "Молодая луна", "Первая четверть", "Растущая луна"};

    MoonApp(DeviceScreen screen) {
        super(screen, "moon");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int phase = mc().level == null ? 0 : mc().level.getMoonPhase();
        int cx = area[0] + area[2] / 2;
        int cy = area[1] + area[3] / 2 - 14;
        int r = Math.min(area[2], area[3]) / 4;
        g.fill(area[0], area[1], area[0] + area[2], area[1] + area[3], 0xFF0B0D14);
        PhoneUi.disc(g, cx, cy, r, 0xFFE8E4D0);
        // Тень: смещённый тёмный диск — чем дальше от полнолуния, тем больше закрыто.
        double lit = Math.cos(Math.PI * phase / 4.0);
        int offset = (int) Math.round(r * 2 * (1 - Math.abs(lit)) * (phase < 4 ? 1 : -1));
        if (phase != 0) {
            PhoneUi.disc(g, cx + (phase < 4 ? r * 2 - offset : -r * 2 - offset), cy, r, 0xFF0B0D14);
        }
        screen.fitted(g, NAMES[phase], cx, cy + r + 10, area[2], Kit.TEXT);
        int toFull = Math.floorMod(8 - phase, 8);
        screen.fitted(g, toFull == 0 ? "Сегодня полнолуние" : "До полнолуния: " + toFull + " дн.",
                cx, cy + r + 24, area[2], Kit.DIM);
    }
}
