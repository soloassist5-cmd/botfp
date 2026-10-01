package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Случайное число в диапазоне «от» и «до»; история последних бросков. */
class RandomApp extends PcApp {

    private final Random random = new Random();
    private String result = "—";
    private final java.util.ArrayDeque<String> history = new java.util.ArrayDeque<>();

    RandomApp(DeviceScreen screen) {
        super(screen, "random");
    }

    @Override
    public void init(int[] area) {
        var from = screen.input("rnd:from", area[0] + 30, area[1] + 4, area[2] / 2 - 40, "citylife.pc.enter_number", 12);
        var to = screen.input("rnd:to", area[0] + area[2] / 2 + 26, area[1] + 4, area[2] / 2 - 32,
                "citylife.pc.enter_number", 12);
        if (from.getValue().isEmpty()) {
            from.setValue("1");
        }
        if (to.getValue().isEmpty()) {
            to.setValue("100");
        }
    }

    private int[] roll(int[] area) {
        return new int[]{area[0], area[1] + 26, area[2], 20};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        screen.text(g, "от", area[0], area[1] + 4, Kit.DIM);
        screen.text(g, "до", area[0] + area[2] / 2 + 6, area[1] + 4, Kit.DIM);
        g.fill(area[0] + 28, area[1] + 14, area[0] + area[2] / 2 - 8, area[1] + 15, 0x55FFFFFF);
        g.fill(area[0] + area[2] / 2 + 24, area[1] + 14, area[0] + area[2] - 4, area[1] + 15, 0x55FFFFFF);
        Kit.tile(g, screen, roll(area), "Бросить", 0xFF1F8F57, mouseX, mouseY);
        big(g, result, area[0] + area[2] / 2, area[1] + 56, 3F, Kit.TEXT);
        int y = area[1] + 92;
        screen.text(g, "Раньше: " + String.join(", ", history), area[0], y, Kit.DIM);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (!Kit.inside(mx, my, roll(area))) {
            return false;
        }
        long a = (long) Kit.parse(screen.value("rnd:from"));
        long b = (long) Kit.parse(screen.value("rnd:to"));
        if (a > b) {
            long t = a;
            a = b;
            b = t;
        }
        if (!result.equals("—")) {
            history.addFirst(result);
            while (history.size() > 8) {
                history.removeLast();
            }
        }
        result = String.valueOf(a + (long) (random.nextDouble() * (b - a + 1)));
        return true;
    }
}
