package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Метроном: темп 40–220 ударов в минуту, размер 4/4, сильная доля выше тоном. */
class MetronomeApp extends PcApp {

    private int bpm = 100;
    private boolean running;
    private long start;
    private long lastBeat = -1;

    MetronomeApp(DeviceScreen screen) {
        super(screen, "metronome");
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + area[3] - 20, area[2], 20, 5, 1, i, 5);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long beat = running ? (System.currentTimeMillis() - start) * bpm / 60_000 : -1;
        if (running && beat != lastBeat) {
            lastBeat = beat;
            mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(),
                    beat % 4 == 0 ? 1.8F : 1.2F));
        }
        big(g, bpm + " уд/мин", area[0] + area[2] / 2, area[1] + 4, 2.2F, Kit.TEXT);
        for (int i = 0; i < 4; i++) {
            int[] r = Kit.cell(area[0] + 20, area[1] + 34, area[2] - 40, 26, 4, 1, i, 10);
            boolean lit = running && beat % 4 == i;
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6, lit ? (i == 0 ? 0xFFFF6B6B : Kit.ACCENT) : 0xFF1B1F2B);
        }
        // Маятник.
        double phase = running ? Math.sin((System.currentTimeMillis() - start) * Math.PI * bpm / 60_000.0) : 0;
        int px = area[0] + area[2] / 2 + (int) (phase * area[2] / 4);
        g.fill(area[0] + area[2] / 2, area[1] + area[3] - 30, px, area[1] + area[3] - 29, Kit.DIM);
        PhoneUi.disc(g, px, area[1] + area[3] - 30, 4, Kit.ACCENT);
        String[] labels = {"−10", "−1", running ? "Стоп" : "Старт", "+1", "+10"};
        for (int i = 0; i < 5; i++) {
            Kit.tile(g, screen, button(area, i), labels[i], i == 2 ? (running ? 0xFFB03434 : 0xFF1F8F57) : Kit.TILE,
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        int[] deltas = {-10, -1, 0, 1, 10};
        for (int i = 0; i < 5; i++) {
            if (Kit.inside(mx, my, button(area, i))) {
                if (i == 2) {
                    running = !running;
                    start = System.currentTimeMillis();
                    lastBeat = -1;
                } else {
                    bpm = Math.max(40, Math.min(220, bpm + deltas[i]));
                    start = System.currentTimeMillis();
                }
                return true;
            }
        }
        return false;
    }
}
