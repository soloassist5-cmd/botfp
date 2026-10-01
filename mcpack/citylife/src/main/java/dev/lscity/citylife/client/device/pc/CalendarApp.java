package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Календарь: игровые дни складываются в недели и месяцы по 28 дней, как
 * у города; сегодняшний день подсвечен. Стрелки листают месяцы.
 */
class CalendarApp extends PcApp {

    private int shift;

    CalendarApp(DeviceScreen screen) {
        super(screen, "calendar");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long today = day() - 1;
        long month = today / 28 + shift;
        int year = (int) (month / 12) + 1;
        String title = LiveApps.MONTHS[(int) Math.floorMod(month, 12)] + ", год " + Math.max(1, year);
        screen.fitted(g, title, area[0] + area[2] / 2, area[1] + 2, area[2] - 60, Kit.TEXT);
        Kit.tile(g, screen, prev(area), "‹", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, next(area), "›", Kit.TILE, mouseX, mouseY);
        String[] heads = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        int top = area[1] + 20;
        int h = area[3] - 20;
        for (int c = 0; c < 7; c++) {
            int[] r = Kit.cell(area[0], top, area[2], 14, 7, 1, c, 3);
            screen.fitted(g, heads[c], r[0] + r[2] / 2, r[1] + 3, r[2], c >= 5 ? Kit.RED : Kit.DIM);
        }
        for (int d = 0; d < 28; d++) {
            int[] r = Kit.cell(area[0], top + 16, area[2], h - 16, 7, 4, d, 3);
            long absolute = month * 28 + d;
            boolean now = absolute == today;
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 4, now ? 0xFF1F6FD0 : 0xFF1B1F2B);
            screen.text(g, String.valueOf(d + 1), r[0] + 4, r[1] + 3, now ? 0xFFFFFFFF : Kit.TEXT);
        }
    }

    private int[] prev(int[] area) {
        return new int[]{area[0], area[1], 24, 14};
    }

    private int[] next(int[] area) {
        return new int[]{area[0] + area[2] - 24, area[1], 24, 14};
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, prev(area))) {
            shift--;
            return true;
        }
        if (Kit.inside(mx, my, next(area))) {
            shift++;
            return true;
        }
        return false;
    }
}
