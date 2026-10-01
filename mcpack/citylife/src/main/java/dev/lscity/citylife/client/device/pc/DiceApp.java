package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Игральные кости: 1–5 кубиков, бросок с анимацией, сумма. */
class DiceApp extends PcApp {

    private final Random random = new Random();
    private int count = 2;
    private final int[] faces = {1, 1, 1, 1, 1};
    private long rolledAt;

    DiceApp(DeviceScreen screen) {
        super(screen, "dice");
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + area[3] - 20, area[2], 20, 3, 1, i, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        boolean rolling = System.currentTimeMillis() - rolledAt < 500;
        int size = Math.min(48, (area[2] - 10 * count) / count);
        int total = 0;
        int x0 = area[0] + (area[2] - count * (size + 10)) / 2;
        for (int i = 0; i < count; i++) {
            int face = rolling ? 1 + random.nextInt(6) : faces[i];
            total += face;
            int x = x0 + i * (size + 10);
            int y = area[1] + 20;
            PhoneUi.roundedRect(g, x, y, size, size, 8, 0xFFF4F4F4);
            pips(g, x, y, size, face);
        }
        if (!rolling) {
            screen.fitted(g, "Сумма: " + total, area[0] + area[2] / 2, area[1] + 32 + size, area[2], Kit.TEXT);
        }
        Kit.tile(g, screen, button(area, 0), "−", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 1), "Бросить", 0xFF1F8F57, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 2), "+", Kit.TILE, mouseX, mouseY);
    }

    private static void pips(GuiGraphics g, int x, int y, int s, int face) {
        int[][] spots = switch (face) {
            case 1 -> new int[][]{{2, 2}};
            case 2 -> new int[][]{{1, 1}, {3, 3}};
            case 3 -> new int[][]{{1, 1}, {2, 2}, {3, 3}};
            case 4 -> new int[][]{{1, 1}, {3, 1}, {1, 3}, {3, 3}};
            case 5 -> new int[][]{{1, 1}, {3, 1}, {2, 2}, {1, 3}, {3, 3}};
            default -> new int[][]{{1, 1}, {3, 1}, {1, 2}, {3, 2}, {1, 3}, {3, 3}};
        };
        for (int[] p : spots) {
            PhoneUi.disc(g, x + p[0] * s / 4, y + p[1] * s / 4, Math.max(2, s / 10), face == 1 ? 0xFFC02B3F : 0xFF15171F);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, button(area, 0))) {
            count = Math.max(1, count - 1);
        } else if (Kit.inside(mx, my, button(area, 2))) {
            count = Math.min(5, count + 1);
        } else if (Kit.inside(mx, my, button(area, 1))) {
            for (int i = 0; i < faces.length; i++) {
                faces[i] = 1 + random.nextInt(6);
            }
            rolledAt = System.currentTimeMillis();
        } else {
            return false;
        }
        return true;
    }
}
