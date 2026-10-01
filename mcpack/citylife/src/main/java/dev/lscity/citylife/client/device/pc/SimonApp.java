package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Саймон: запомните последовательность вспышек и повторите её; каждый раунд — на одну длиннее. */
class SimonApp extends PcApp {

    private static final int[] COLOURS = {0xFF2F9E4F, 0xFFC02B3F, 0xFFE0C040, 0xFF2F55D6};
    private static final float[] PITCH = {0.8F, 1.0F, 1.2F, 1.5F};
    private final Random random = new Random();
    private final List<Integer> sequence = new ArrayList<>();
    private int input;
    private long showStart = -1;
    private int lit = -1;
    private long litUntil;
    private String status = "Клик по «Старт»";
    private static int best;
    private int shownStep = -1;

    SimonApp(DeviceScreen screen) {
        super(screen, "simon");
    }

    private int[] pad(int[] area, int i) {
        int size = Math.min(area[2] - 120, area[3]);
        return Kit.cell(area[0], area[1], size, size, 2, 2, i, 6);
    }

    private void flash(int i, long ms) {
        lit = i;
        litUntil = System.currentTimeMillis() + ms;
        mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), PITCH[i]));
    }

    private void nextRound() {
        sequence.add(random.nextInt(4));
        input = 0;
        showStart = System.currentTimeMillis() + 500;
        status = "Смотрите…";
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (showStart >= 0 && now >= showStart) {
            int step = (int) ((now - showStart) / 600);
            if (step < sequence.size()) {
                if (step != shownStep) {
                    shownStep = step;
                    flash(sequence.get(step), 400);
                }
            } else {
                showStart = -1;
                shownStep = -1;
                status = "Повторите: " + sequence.size();
            }
        }
        for (int i = 0; i < 4; i++) {
            int[] r = pad(area, i);
            boolean on = lit == i && now < litUntil;
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 10, on ? PhoneUi.lerp(COLOURS[i], 0xFFFFFFFF, 0.5F)
                    : PhoneUi.lerp(COLOURS[i], 0xFF000000, 0.35F));
        }
        int tx = area[0] + area[2] - 110;
        screen.text(g, screen.trim(status, 110), tx, area[1] + 4, Kit.TEXT);
        screen.text(g, "Рекорд: " + best, tx, area[1] + 18, Kit.DIM);
        Kit.tile(g, screen, new int[]{tx, area[1] + 36, 100, 18}, "Старт", 0xFF1F6FD0, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, new int[]{area[0] + area[2] - 110, area[1] + 36, 100, 18})) {
            sequence.clear();
            nextRound();
            return true;
        }
        if (showStart >= 0 || sequence.isEmpty()) {
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (Kit.inside(mx, my, pad(area, i))) {
                flash(i, 250);
                if (sequence.get(input) == i) {
                    input++;
                    if (input == sequence.size()) {
                        best = Math.max(best, sequence.size());
                        nextRound();
                    }
                } else {
                    status = "Ошибка! Длина: " + (sequence.size() - 1);
                    sequence.clear();
                }
                return true;
            }
        }
        return false;
    }
}
