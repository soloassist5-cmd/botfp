package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.Random;

/**
 * Сапёр 10×10, 14 мин. Клик — открыть клетку, Shift+клик — флажок.
 * Первый клик никогда не попадает на мину.
 */
class MinesApp extends DeviceApp {

    private static final int N = 10;
    private static final int MINES = 14;

    private final boolean[][] mine = new boolean[N][N];
    private final boolean[][] open = new boolean[N][N];
    private final boolean[][] flag = new boolean[N][N];
    private boolean started;
    private boolean lost;
    private boolean won;

    MinesApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("mines");
    }

    private int cell(int[] area) {
        return Math.max(8, Math.min((area[2] - 4) / N, (area[3] - 24) / N));
    }

    private int left(int[] area) {
        return area[0] + (area[2] - cell(area) * N) / 2;
    }

    private int top(int[] area) {
        return area[1] + 18;
    }

    private int around(int x, int y) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < N && ny < N && mine[nx][ny]) {
                    count++;
                }
            }
        }
        return count;
    }

    private void seed(int sx, int sy) {
        Random random = new Random();
        int placed = 0;
        while (placed < MINES) {
            int x = random.nextInt(N);
            int y = random.nextInt(N);
            if (mine[x][y] || (Math.abs(x - sx) <= 1 && Math.abs(y - sy) <= 1)) {
                continue;
            }
            mine[x][y] = true;
            placed++;
        }
        started = true;
    }

    private void reveal(int x, int y) {
        if (x < 0 || y < 0 || x >= N || y >= N || open[x][y] || flag[x][y]) {
            return;
        }
        open[x][y] = true;
        if (mine[x][y]) {
            lost = true;
            return;
        }
        if (around(x, y) == 0) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    reveal(x + dx, y + dy);
                }
            }
        }
    }

    private void checkWin() {
        int closed = 0;
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                closed += open[x][y] ? 0 : 1;
            }
        }
        won = !lost && closed == MINES;
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        String status = lost ? "Взрыв! Клик — заново" : won ? "Победа! Клик — заново"
                : "Мин: " + MINES + " · Shift — флажок";
        screen.text(g, status, area[0] + 2, area[1] + 3, lost ? t.red() : won ? t.green() : t.dim());
        int c = cell(area);
        int[] colours = {0, 0xFF3A7BD5, 0xFF2F9E4F, 0xFFC02B3F, 0xFF4C3FD0, 0xFF8A3A00,
                         0xFF16806A, 0xFF222222, 0xFF777777};
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                int px = left(area) + x * c;
                int py = top(area) + y * c;
                boolean shown = open[x][y] || ((lost || won) && mine[x][y]);
                g.fill(px, py, px + c - 1, py + c - 1, shown ? 0xFFD8DCE6 : 0xFF6E7488);
                if (shown && mine[x][y]) {
                    g.fill(px + c / 3, py + c / 3, px + c - c / 3, py + c - c / 3, 0xFF15171F);
                } else if (shown && around(x, y) > 0) {
                    String n = String.valueOf(around(x, y));
                    g.drawString(screen.font(), n, px + (c - screen.font().width(n)) / 2,
                            py + (c - 8) / 2, colours[around(x, y)], false);
                } else if (!shown && flag[x][y]) {
                    g.fill(px + c / 3, py + 2, px + c / 3 + 1, py + c - 3, 0xFF15171F);
                    g.fill(px + c / 3 + 1, py + 2, px + c - 3, py + c / 2, 0xFFFF4D4D);
                }
            }
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (lost || won) {
            screen.restart();
            return true;
        }
        int c = cell(area);
        int x = (int) Math.floor((mx - left(area)) / c);
        int y = (int) Math.floor((my - top(area)) / c);
        if (x < 0 || y < 0 || x >= N || y >= N) {
            return false;
        }
        if (Screen.hasShiftDown()) {
            flag[x][y] = !open[x][y] && !flag[x][y];
            return true;
        }
        if (!started) {
            seed(x, y);
        }
        reveal(x, y);
        checkWin();
        return true;
    }
}
