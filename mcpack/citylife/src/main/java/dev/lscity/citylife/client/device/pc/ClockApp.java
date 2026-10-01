package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Часы: игровое время циферблатом и цифрами, плюс реальное время. */
class ClockApp extends PcApp {

    ClockApp(DeviceScreen screen) {
        super(screen, "clock");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long time = screen.data().getLong("daytime");
        double hours = ((time / 1000.0) + 6) % 24;
        int cx = area[0] + area[2] / 3;
        int cy = area[1] + area[3] / 2;
        int r = Math.min(area[2] / 3, area[3] / 2) - 8;
        PhoneUi.disc(g, cx, cy, r, 0xFF1B1F2B);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            int x = cx + (int) (Math.sin(a) * (r - 6));
            int y = cy - (int) (Math.cos(a) * (r - 6));
            g.fill(x - 1, y - 1, x + 1, y + 1, Kit.DIM);
        }
        hand(g, cx, cy, (hours % 12) / 12.0, r * 0.55, 0xFFEDEFF7);
        hand(g, cx, cy, (hours % 1.0), r * 0.85, Kit.ACCENT);
        PhoneUi.disc(g, cx, cy, 2, 0xFFFFFFFF);
        int tx = area[0] + area[2] * 2 / 3 + 10;
        big(g, clock(), tx, cy - 24, 3F, Kit.TEXT);
        screen.fitted(g, "игровое время · день " + day(), tx, cy + 6, area[2] / 3, Kit.DIM);
        java.time.LocalTime real = java.time.LocalTime.now();
        screen.fitted(g, "реальное: " + String.format("%02d:%02d:%02d", real.getHour(), real.getMinute(),
                real.getSecond()), tx, cy + 20, area[2] / 3, Kit.DIM);
    }

    private static void hand(GuiGraphics g, int cx, int cy, double turn, double len, int colour) {
        double a = Math.PI * 2 * turn;
        for (int i = 0; i < (int) len; i++) {
            int x = cx + (int) Math.round(Math.sin(a) * i);
            int y = cy - (int) Math.round(Math.cos(a) * i);
            g.fill(x, y, x + 2, y + 2, colour);
        }
    }
}
