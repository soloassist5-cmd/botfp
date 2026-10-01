package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Четыре в ряд 7×6 против компьютера: он ищет победный ход, блокирует ваш, а иначе играет в центр. */
class Connect4App extends PcApp {

    private final int[][] grid = new int[6][7];
    private int winner;
    private String status = "Ваш ход — красные";

    Connect4App(DeviceScreen screen) {
        super(screen, "connect4");
    }

    private int drop(int col, int who) {
        for (int r = 5; r >= 0; r--) {
            if (grid[r][col] == 0) {
                grid[r][col] = who;
                return r;
            }
        }
        return -1;
    }

    private boolean wins(int who) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                for (int[] d : dirs) {
                    int k = 0;
                    while (k < 4) {
                        int rr = r + d[0] * k;
                        int cc = c + d[1] * k;
                        if (rr < 0 || rr >= 6 || cc < 0 || cc >= 7 || grid[rr][cc] != who) {
                            break;
                        }
                        k++;
                    }
                    if (k == 4) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int aiColumn() {
        for (int who : new int[]{2, 1}) {
            for (int c = 0; c < 7; c++) {
                int r = drop(c, who);
                if (r < 0) {
                    continue;
                }
                boolean w = wins(who);
                grid[r][c] = 0;
                if (w) {
                    return c;
                }
            }
        }
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        for (int c : order) {
            if (grid[0][c] == 0 && Math.random() < 0.7) {
                return c;
            }
        }
        for (int c : order) {
            if (grid[0][c] == 0) {
                return c;
            }
        }
        return -1;
    }

    private int[] board(int[] area) {
        int size = Math.min((area[2] - 120) / 7, (area[3] - 4) / 6);
        return new int[]{area[0], area[1], size * 7, size * 6, size};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int[] b = board(area);
        PhoneUi.roundedRect(g, b[0], b[1], b[2], b[3], 6, 0xFF2F55D6);
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                int colour = grid[r][c] == 1 ? 0xFFFF4A4A : grid[r][c] == 2 ? 0xFFFFD84A : 0xFF101218;
                PhoneUi.disc(g, b[0] + c * b[4] + b[4] / 2, b[1] + r * b[4] + b[4] / 2, b[4] / 2 - 2, colour);
            }
        }
        int tx = area[0] + area[2] - 110;
        screen.text(g, screen.trim(status, 110), tx, area[1] + 4, Kit.TEXT);
        Kit.tile(g, screen, new int[]{tx, area[1] + 24, 100, 18}, "Заново", 0xFF1F6FD0, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, new int[]{area[0] + area[2] - 110, area[1] + 24, 100, 18})) {
            for (int[] row : grid) {
                java.util.Arrays.fill(row, 0);
            }
            winner = 0;
            status = "Ваш ход — красные";
            return true;
        }
        int[] b = board(area);
        if (winner != 0 || mx < b[0] || mx >= b[0] + b[2] || my < b[1] || my >= b[1] + b[3]) {
            return false;
        }
        int col = (int) ((mx - b[0]) / b[4]);
        if (drop(col, 1) < 0) {
            return true;
        }
        if (wins(1)) {
            winner = 1;
            status = "Вы победили!";
            return true;
        }
        int ai = aiColumn();
        if (ai < 0) {
            winner = 3;
            status = "Ничья";
            return true;
        }
        drop(ai, 2);
        if (wins(2)) {
            winner = 2;
            status = "Компьютер выиграл";
        }
        return true;
    }
}
