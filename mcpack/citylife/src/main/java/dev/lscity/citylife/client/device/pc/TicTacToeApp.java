package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Крестики-нолики против компьютера (он не проигрывает, но иногда ошибается на «лёгком»). */
class TicTacToeApp extends PcApp {

    private final int[] board = new int[9];
    private boolean hard = true;
    private int wins;
    private int losses;
    private int draws;
    private String status = "Ваш ход: вы — ×";

    TicTacToeApp(DeviceScreen screen) {
        super(screen, "tictactoe");
    }

    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}};

    static int winner(int[] b) {
        for (int[] l : LINES) {
            if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) {
                return b[l[0]];
            }
        }
        for (int v : b) {
            if (v == 0) {
                return 0;
            }
        }
        return 3;
    }

    private static int minimax(int[] b, int turn) {
        int w = winner(b);
        if (w == 2) {
            return 1;
        }
        if (w == 1) {
            return -1;
        }
        if (w == 3) {
            return 0;
        }
        int best = turn == 2 ? -2 : 2;
        for (int i = 0; i < 9; i++) {
            if (b[i] == 0) {
                b[i] = turn;
                int score = minimax(b, 3 - turn);
                b[i] = 0;
                best = turn == 2 ? Math.max(best, score) : Math.min(best, score);
            }
        }
        return best;
    }

    private void aiMove() {
        int move = -1;
        if (!hard && Math.random() < 0.35) {
            java.util.List<Integer> free = new java.util.ArrayList<>();
            for (int i = 0; i < 9; i++) {
                if (board[i] == 0) {
                    free.add(i);
                }
            }
            move = free.get((int) (Math.random() * free.size()));
        } else {
            int best = -2;
            for (int i = 0; i < 9; i++) {
                if (board[i] == 0) {
                    board[i] = 2;
                    int score = minimax(board, 1);
                    board[i] = 0;
                    if (score > best) {
                        best = score;
                        move = i;
                    }
                }
            }
        }
        if (move >= 0) {
            board[move] = 2;
        }
    }

    private int[] cell(int[] area, int i) {
        int size = Math.min(area[3] - 24, area[2] / 2);
        int x0 = area[0] + 6;
        return Kit.cell(x0, area[1], size, size, 3, 3, i, 4);
    }

    private int[] modeButton(int[] area) {
        return new int[]{area[0] + area[2] - 110, area[1] + 70, 104, 18};
    }

    private int[] again(int[] area) {
        return new int[]{area[0] + area[2] - 110, area[1] + 94, 104, 18};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        for (int i = 0; i < 9; i++) {
            int[] r = cell(area, i);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6, Kit.inside(mouseX, mouseY, r) && board[i] == 0
                    ? 0xFF262B3B : 0xFF1B1F2B);
            if (board[i] != 0) {
                big(g, board[i] == 1 ? "×" : "○", r[0] + r[2] / 2, r[1] + r[3] / 2 - 10, 2.5F,
                        board[i] == 1 ? Kit.ACCENT : 0xFFFF9F43);
            }
        }
        int tx = area[0] + area[2] - 110;
        screen.text(g, screen.trim(status, 110), tx, area[1] + 4, Kit.TEXT);
        screen.text(g, "Победы: " + wins, tx, area[1] + 22, Kit.GREEN);
        screen.text(g, "Поражения: " + losses, tx, area[1] + 34, Kit.RED);
        screen.text(g, "Ничьи: " + draws, tx, area[1] + 46, Kit.DIM);
        Kit.tile(g, screen, modeButton(area), hard ? "Сложно" : "Легко", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, again(area), "Заново", 0xFF1F6FD0, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, modeButton(area))) {
            hard = !hard;
            return true;
        }
        if (Kit.inside(mx, my, again(area))) {
            java.util.Arrays.fill(board, 0);
            status = "Ваш ход: вы — ×";
            return true;
        }
        if (winner(board) != 0) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (Kit.inside(mx, my, cell(area, i)) && board[i] == 0) {
                board[i] = 1;
                if (winner(board) == 0) {
                    aiMove();
                }
                int w = winner(board);
                if (w == 1) {
                    wins++;
                    status = "Вы победили!";
                } else if (w == 2) {
                    losses++;
                    status = "Компьютер выиграл";
                } else if (w == 3) {
                    draws++;
                    status = "Ничья";
                }
                return true;
            }
        }
        return false;
    }
}
