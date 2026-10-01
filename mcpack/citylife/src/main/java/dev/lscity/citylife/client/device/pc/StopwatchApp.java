package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Секундомер с кругами. */
class StopwatchApp extends PcApp {

    private long started;
    private long accumulated;
    private boolean running;
    private final List<Long> laps = new ArrayList<>();

    StopwatchApp(DeviceScreen screen) {
        super(screen, "stopwatch");
    }

    private long elapsed() {
        return accumulated + (running ? System.currentTimeMillis() - started : 0);
    }

    static String format(long ms) {
        return String.format("%02d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms / 10 % 100);
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + 54, area[2], 20, 3, 1, i, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        big(g, format(elapsed()), area[0] + area[2] / 2, area[1] + 8, 3F, Kit.TEXT);
        Kit.tile(g, screen, button(area, 0), running ? "Стоп" : "Старт", running ? 0xFFB03434 : 0xFF1F8F57,
                mouseX, mouseY);
        Kit.tile(g, screen, button(area, 1), "Круг", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 2), "Сброс", Kit.TILE, mouseX, mouseY);
        int y = area[1] + 82;
        for (int i = laps.size() - 1; i >= 0 && y < area[1] + area[3] - 10; i--) {
            long lap = laps.get(i) - (i > 0 ? laps.get(i - 1) : 0);
            screen.text(g, "Круг " + (i + 1) + ":  " + format(lap) + "   (" + format(laps.get(i)) + ")",
                    area[0] + 6, y, Kit.TEXT);
            y += 11;
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, button(area, 0))) {
            if (running) {
                accumulated = elapsed();
                running = false;
            } else {
                started = System.currentTimeMillis();
                running = true;
            }
            return true;
        }
        if (Kit.inside(mx, my, button(area, 1)) && running) {
            laps.add(elapsed());
            return true;
        }
        if (Kit.inside(mx, my, button(area, 2))) {
            running = false;
            accumulated = 0;
            laps.clear();
            return true;
        }
        return false;
    }
}
