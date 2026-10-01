package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Пятнашки 4×4: перемешано случайными ходами, поэтому всегда решаемо. */
class Puzzle15App extends PcApp {

    private final int[] tiles = new int[16];
    private int moves;
    private final Random random = new Random();

    Puzzle15App(DeviceScreen screen) {
        super(screen, "puzzle15");
        shuffle();
    }

    private void shuffle() {
        for (int i = 0; i < 16; i++) {
            tiles[i] = (i + 1) % 16;
        }
        int empty = 15;
        for (int k = 0; k < 400; k++) {
            int[] near = neighbours(empty);
            int pick = near[random.nextInt(near.length)];
            tiles[empty] = tiles[pick];
            tiles[pick] = 0;
            empty = pick;
        }
        moves = 0;
    }

    private static int[] neighbours(int i) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        int r = i / 4;
        int c = i % 4;
        if (r > 0) {
            out.add(i - 4);
        }
        if (r < 3) {
            out.add(i + 4);
        }
        if (c > 0) {
            out.add(i - 1);
        }
        if (c < 3) {
            out.add(i + 1);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean solved() {
        for (int i = 0; i < 15; i++) {
            if (tiles[i] != i + 1) {
                return false;
            }
        }
        return true;
    }

    private int[] tile(int[] area, int i) {
        int size = Math.min(area[2] - 120, area[3]);
        return Kit.cell(area[0], area[1], size, size, 4, 4, i, 3);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        for (int i = 0; i < 16; i++) {
            if (tiles[i] == 0) {
                continue;
            }
            int[] r = tile(area, i);
            boolean right = tiles[i] == i + 1;
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 5, right ? 0xFF2E8A5A : 0xFFD27A2E);
            big(g, String.valueOf(tiles[i]), r[0] + r[2] / 2, r[1] + r[3] / 2 - 7, 1.8F, 0xFFFFFFFF);
        }
        int tx = area[0] + area[2] - 110;
        screen.text(g, "Ходов: " + moves, tx, area[1] + 4, Kit.TEXT);
        if (solved()) {
            screen.text(g, "Собрано!", tx, area[1] + 20, Kit.GREEN);
        }
        Kit.tile(g, screen, new int[]{tx, area[1] + 40, 100, 18}, "Перемешать", 0xFF1F6FD0, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, new int[]{area[0] + area[2] - 110, area[1] + 40, 100, 18})) {
            shuffle();
            return true;
        }
        for (int i = 0; i < 16; i++) {
            if (Kit.inside(mx, my, tile(area, i)) && tiles[i] != 0) {
                for (int n : neighbours(i)) {
                    if (tiles[n] == 0) {
                        tiles[n] = tiles[i];
                        tiles[i] = 0;
                        moves++;
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
