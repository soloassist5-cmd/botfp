package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Погаси свет 5×5: клик переключает лампу и её соседей; цель — погасить все. */
class LightsOutApp extends PcApp {

    private final boolean[] on = new boolean[25];
    private int moves;

    LightsOutApp(DeviceScreen screen) {
        super(screen, "lightsout");
        reset();
    }

    private void reset() {
        java.util.Arrays.fill(on, false);
        Random random = new Random();
        for (int k = 0; k < 12; k++) {
            toggle(random.nextInt(25));
        }
        moves = 0;
    }

    private void toggle(int i) {
        int r = i / 5;
        int c = i % 5;
        on[i] = !on[i];
        if (r > 0) {
            on[i - 5] = !on[i - 5];
        }
        if (r < 4) {
            on[i + 5] = !on[i + 5];
        }
        if (c > 0) {
            on[i - 1] = !on[i - 1];
        }
        if (c < 4) {
            on[i + 1] = !on[i + 1];
        }
    }

    private int[] lamp(int[] area, int i) {
        int size = Math.min(area[2] - 120, area[3]);
        return Kit.cell(area[0], area[1], size, size, 5, 5, i, 3);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int lit = 0;
        for (int i = 0; i < 25; i++) {
            int[] r = lamp(area, i);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 4, on[i] ? 0xFFFFE27A : 0xFF1B1F2B);
            lit += on[i] ? 1 : 0;
        }
        int tx = area[0] + area[2] - 110;
        screen.text(g, "Горит: " + lit, tx, area[1] + 4, Kit.TEXT);
        screen.text(g, "Ходов: " + moves, tx, area[1] + 16, Kit.DIM);
        if (lit == 0) {
            screen.text(g, "Темно! Победа", tx, area[1] + 30, Kit.GREEN);
        }
        Kit.tile(g, screen, new int[]{tx, area[1] + 46, 100, 18}, "Новая", 0xFF1F6FD0, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, new int[]{area[0] + area[2] - 110, area[1] + 46, 100, 18})) {
            reset();
            return true;
        }
        for (int i = 0; i < 25; i++) {
            if (Kit.inside(mx, my, lamp(area, i))) {
                toggle(i);
                moves++;
                return true;
            }
        }
        return false;
    }
}
