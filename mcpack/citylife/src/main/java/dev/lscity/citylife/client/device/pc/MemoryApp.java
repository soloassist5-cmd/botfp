package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Найди пару: 16 карточек, открываются по две; найти все пары за меньшее число ходов. */
class MemoryApp extends PcApp {

    private static final String[] SYMBOLS = {"★", "♥", "♦", "♣", "♠", "☀", "☂", "♫"};
    private static final int[] COLOURS = {0xFFFFC857, 0xFFFF6B6B, 0xFF45D0F0, 0xFF7BE07B, 0xFFC77DFF,
            0xFFFF9F43, 0xFF5AA8FF, 0xFFFF5C8A};
    private final List<Integer> cards = new ArrayList<>();
    private final boolean[] open = new boolean[16];
    private int first = -1;
    private int second = -1;
    private long flipBackAt;
    private int moves;

    MemoryApp(DeviceScreen screen) {
        super(screen, "memory");
        reset();
    }

    private void reset() {
        cards.clear();
        for (int i = 0; i < 8; i++) {
            cards.add(i);
            cards.add(i);
        }
        Collections.shuffle(cards);
        java.util.Arrays.fill(open, false);
        first = second = -1;
        moves = 0;
    }

    private int[] card(int[] area, int i) {
        int size = Math.min(area[2] - 120, area[3]);
        return Kit.cell(area[0], area[1], size, size, 4, 4, i, 4);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (second >= 0 && System.currentTimeMillis() >= flipBackAt) {
            if (!cards.get(first).equals(cards.get(second))) {
                open[first] = false;
                open[second] = false;
            }
            first = second = -1;
        }
        boolean done = true;
        for (int i = 0; i < 16; i++) {
            int[] r = card(area, i);
            boolean shown = open[i];
            done &= open[i];
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6, shown ? 0xFF262B3B : 0xFF1F4F8F);
            if (shown) {
                big(g, SYMBOLS[cards.get(i)], r[0] + r[2] / 2, r[1] + r[3] / 2 - 8, 2F, COLOURS[cards.get(i)]);
            }
        }
        int tx = area[0] + area[2] - 110;
        screen.text(g, "Ходов: " + moves, tx, area[1] + 4, Kit.TEXT);
        if (done) {
            screen.text(g, "Все пары найдены!", tx, area[1] + 20, Kit.GREEN);
        }
        Kit.tile(g, screen, new int[]{tx, area[1] + 40, 100, 18}, "Заново", 0xFF1F6FD0, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, new int[]{area[0] + area[2] - 110, area[1] + 40, 100, 18})) {
            reset();
            return true;
        }
        if (second >= 0) {
            return true;
        }
        for (int i = 0; i < 16; i++) {
            if (Kit.inside(mx, my, card(area, i)) && !open[i]) {
                open[i] = true;
                if (first < 0) {
                    first = i;
                } else {
                    second = i;
                    moves++;
                    flipBackAt = System.currentTimeMillis() + 700;
                }
                return true;
            }
        }
        return false;
    }
}
