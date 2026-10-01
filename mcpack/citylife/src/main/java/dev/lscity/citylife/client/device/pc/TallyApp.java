package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;

/** Счётчик: большая кнопка «+1», «−1» и сброс — посчитать что угодно. */
class TallyApp extends PcApp {

    private static int value;

    TallyApp(DeviceScreen screen) {
        super(screen, "tally");
    }

    private int[] plus(int[] area) {
        return new int[]{area[0], area[1] + 48, area[2], area[3] - 76};
    }

    private int[] minus(int[] area) {
        return Kit.cell(area[0], area[1] + area[3] - 22, area[2], 20, 2, 1, 0, 6);
    }

    private int[] reset(int[] area) {
        return Kit.cell(area[0], area[1] + area[3] - 22, area[2], 20, 2, 1, 1, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        big(g, String.valueOf(value), area[0] + area[2] / 2, area[1] + 6, 4F, Kit.TEXT);
        Kit.tile(g, screen, plus(area), "+1", 0xFF1F6FD0, mouseX, mouseY);
        Kit.tile(g, screen, minus(area), "−1", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, reset(area), "Сброс", 0xFFB03434, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, plus(area))) {
            value++;
        } else if (Kit.inside(mx, my, minus(area))) {
            value--;
        } else if (Kit.inside(mx, my, reset(area))) {
            value = 0;
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean key(int code) {
        if (code == 32) {
            value++;
            return true;
        }
        return false;
    }
}
