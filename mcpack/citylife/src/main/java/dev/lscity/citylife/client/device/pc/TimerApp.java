package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Таймер обратного отсчёта: +1 мин, +10 с, старт; по окончании — звонок. */
class TimerApp extends PcApp {

    private long setMs = 60_000;
    private long endsAt;
    private boolean running;
    private boolean rang;

    TimerApp(DeviceScreen screen) {
        super(screen, "timer");
    }

    private long left() {
        return running ? Math.max(0, endsAt - System.currentTimeMillis()) : setMs;
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + 56, area[2], 20, 4, 1, i, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long left = left();
        if (running && left == 0 && !rang) {
            rang = true;
            for (int i = 0; i < 3; i++) {
                mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(),
                        1.0F + i * 0.2F));
            }
        }
        boolean done = running && left == 0;
        big(g, String.format("%02d:%02d", left / 60000, left / 1000 % 60), area[0] + area[2] / 2, area[1] + 6,
                3.5F, done ? Kit.RED : Kit.TEXT);
        if (running && setMs > 0) {
            int w = (int) (area[2] * left / Math.max(1, setMs));
            PhoneUi.roundedRect(g, area[0], area[1] + 44, area[2], 6, 3, 0xFF101218);
            PhoneUi.roundedRect(g, area[0], area[1] + 44, w, 6, 3, Kit.ACCENT);
        }
        Kit.tile(g, screen, button(area, 0), "+1 мин", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 1), "+10 с", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 2), running ? "Стоп" : "Старт", running ? 0xFFB03434 : 0xFF1F8F57,
                mouseX, mouseY);
        Kit.tile(g, screen, button(area, 3), "Сброс", Kit.TILE, mouseX, mouseY);
        if (done) {
            screen.fitted(g, "Время вышло!", area[0] + area[2] / 2, area[1] + 86, area[2], Kit.RED);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, button(area, 0)) && !running) {
            setMs = Math.min(99 * 60_000L, setMs + 60_000);
        } else if (Kit.inside(mx, my, button(area, 1)) && !running) {
            setMs = Math.min(99 * 60_000L, setMs + 10_000);
        } else if (Kit.inside(mx, my, button(area, 2))) {
            if (running) {
                setMs = left();
                running = false;
            } else if (setMs > 0) {
                endsAt = System.currentTimeMillis() + setMs;
                running = true;
                rang = false;
            }
        } else if (Kit.inside(mx, my, button(area, 3))) {
            running = false;
            setMs = 0;
        } else {
            return false;
        }
        return true;
    }
}
