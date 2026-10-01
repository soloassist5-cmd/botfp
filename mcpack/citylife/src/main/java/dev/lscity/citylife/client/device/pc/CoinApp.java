package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Монетка: орёл или решка, монета вращается при броске; счёт бросков. */
class CoinApp extends PcApp {

    private final Random random = new Random();
    private boolean heads = true;
    private long flippedAt;
    private int headsCount;
    private int tailsCount;

    CoinApp(DeviceScreen screen) {
        super(screen, "coin");
    }

    private int[] button(int[] area) {
        return new int[]{area[0] + area[2] / 4, area[1] + area[3] - 22, area[2] / 2, 20};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long age = System.currentTimeMillis() - flippedAt;
        int cx = area[0] + area[2] / 2;
        int cy = area[1] + area[3] / 2 - 16;
        int r = Math.min(area[2], area[3]) / 4;
        if (age < 700) {
            int w = (int) Math.abs(Math.cos(age / 60.0) * r);
            PhoneUi.roundedRect(g, cx - w, cy - r, Math.max(2, w * 2), r * 2, Math.max(1, w), 0xFFE8C35A);
        } else {
            PhoneUi.disc(g, cx, cy, r, 0xFFE8C35A);
            PhoneUi.disc(g, cx, cy, r - 4, 0xFFD4A940);
            big(g, heads ? "Орёл" : "Решка", cx, cy - 6, 1.6F, 0xFF5A3E00);
        }
        screen.fitted(g, "Орёл: " + headsCount + " · Решка: " + tailsCount, cx, cy + r + 8, area[2], Kit.DIM);
        Kit.tile(g, screen, button(area), "Подбросить", 0xFF1F8F57, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (!Kit.inside(mx, my, button(area))) {
            return false;
        }
        heads = random.nextBoolean();
        if (heads) {
            headsCount++;
        } else {
            tailsCount++;
        }
        flippedAt = System.currentTimeMillis();
        return true;
    }
}
