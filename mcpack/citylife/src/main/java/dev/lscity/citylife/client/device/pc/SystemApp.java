package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Монитор системы: FPS, память Java, дальность прорисовки, сущности вокруг. */
class SystemApp extends PcApp {

    private final int[] history = new int[60];
    private int head;
    private long lastSample;

    SystemApp(DeviceScreen screen) {
        super(screen, "system");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int fps = mc().getFps();
        long now = System.currentTimeMillis();
        if (now - lastSample > 500) {
            lastSample = now;
            history[head] = fps;
            head = (head + 1) % history.length;
        }
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long max = rt.maxMemory() >> 20;
        screen.text(g, "FPS: " + fps, area[0], area[1], fps >= 50 ? Kit.GREEN : fps >= 25 ? 0xFFFFC857 : Kit.RED);
        screen.text(g, "Память: " + used + " / " + max + " МБ", area[0] + area[2] / 2, area[1], Kit.TEXT);
        int bar = area[2];
        PhoneUi.roundedRect(g, area[0], area[1] + 12, bar, 8, 3, 0xFF101218);
        PhoneUi.roundedRect(g, area[0], area[1] + 12, (int) (bar * used / Math.max(1, max)), 8, 3,
                used * 100 / Math.max(1, max) > 85 ? Kit.RED : Kit.ACCENT);
        int gy = area[1] + 28;
        int gh = Math.max(20, area[3] - 92);
        g.fill(area[0], gy, area[0] + area[2], gy + gh, 0xFF101218);
        int maxFps = 1;
        for (int v : history) {
            maxFps = Math.max(maxFps, v);
        }
        for (int i = 0; i < history.length; i++) {
            int v = history[(head + i) % history.length];
            int x = area[0] + i * area[2] / history.length;
            int h = v * (gh - 4) / maxFps;
            g.fill(x, gy + gh - 2 - h, x + Math.max(1, area[2] / history.length - 1), gy + gh - 2, 0xAA45D0F0);
        }
        var options = mc().options;
        int y = gy + gh + 8;
        String[] lines = {
                "Дальность прорисовки: " + options.renderDistance().get() + " чанков",
                "Сущностей рядом: " + (mc().level == null ? 0 : mc().level.getEntityCount()),
                "Процессоров: " + rt.availableProcessors() + ", Java " + System.getProperty("java.version"),
                "Экран: " + mc().getWindow().getWidth() + "×" + mc().getWindow().getHeight(),
        };
        for (String line : lines) {
            screen.text(g, line, area[0], y, Kit.TEXT);
            y += 12;
        }
    }
}
