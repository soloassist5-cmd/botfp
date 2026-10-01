package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Арканоид: ракетка мышью, мяч отскакивает, кирпичи разбиваются; три жизни. */
class BreakoutApp extends PcApp {

    private static final int COLS = 10;
    private static final int ROWS = 5;
    private final boolean[] bricks = new boolean[COLS * ROWS];
    private double bx;
    private double by;
    private double vx;
    private double vy;
    private double paddle = 0.5;
    private boolean launched;
    private int lives = 3;
    private int score;
    private long last;

    BreakoutApp(DeviceScreen screen) {
        super(screen, "breakout");
        java.util.Arrays.fill(bricks, true);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        double dt = last == 0 ? 0 : Math.min(0.05, (now - last) / 1000.0);
        last = now;
        int w = area[2];
        int h = area[3];
        if (mouseX >= area[0] && mouseX <= area[0] + w) {
            paddle = (mouseX - area[0]) / (double) w;
        }
        double pw = 0.18;
        if (!launched) {
            bx = paddle;
            by = 0.9;
        } else if (lives > 0) {
            bx += vx * dt;
            by += vy * dt;
            if (bx < 0.01 || bx > 0.99) {
                vx = -vx;
                bx = Math.max(0.01, Math.min(0.99, bx));
            }
            if (by < 0.02) {
                vy = Math.abs(vy);
            }
            if (by > 0.92 && by < 0.96 && Math.abs(bx - paddle) < pw / 2 && vy > 0) {
                vy = -Math.abs(vy);
                vx = (bx - paddle) * 4;
            }
            if (by > 1.0) {
                lives--;
                launched = false;
            }
            int col = (int) (bx * COLS);
            int row = (int) ((by - 0.08) / 0.05);
            if (row >= 0 && row < ROWS && col >= 0 && col < COLS && bricks[row * COLS + col]) {
                bricks[row * COLS + col] = false;
                vy = -vy;
                score += 10 * (ROWS - row);
            }
        }
        g.fill(area[0], area[1], area[0] + w, area[1] + h, 0xFF0B0D14);
        int[] colours = {0xFFFF6B6B, 0xFFFF9F43, 0xFFFFE27A, 0xFF7BE07B, 0xFF45D0F0};
        boolean any = false;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!bricks[r * COLS + c]) {
                    continue;
                }
                any = true;
                int x = area[0] + c * w / COLS;
                int y = area[1] + (int) ((0.08 + r * 0.05) * h);
                g.fill(x + 1, y + 1, x + w / COLS - 1, y + (int) (0.05 * h) - 1, colours[r]);
            }
        }
        int px = area[0] + (int) ((paddle - pw / 2) * w);
        PhoneUi.roundedRect(g, px, area[1] + (int) (0.93 * h), (int) (pw * w), 4, 2, 0xFFEDEFF7);
        PhoneUi.disc(g, area[0] + (int) (bx * w), area[1] + (int) (by * h), 3, 0xFFFFFFFF);
        screen.text(g, "Очки: " + score + " · жизни: " + lives, area[0] + 4, area[1] + 2, Kit.DIM);
        if (lives <= 0 || !any) {
            screen.fitted(g, (any ? "Игра окончена" : "Все кирпичи разбиты!") + " · клик — заново",
                    area[0] + w / 2, area[1] + h / 2, w, any ? Kit.RED : Kit.GREEN);
        } else if (!launched) {
            screen.fitted(g, "Клик — запустить мяч", area[0] + w / 2, area[1] + h / 2, w, Kit.DIM);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        boolean any = false;
        for (boolean b : bricks) {
            any |= b;
        }
        if (lives <= 0 || !any) {
            java.util.Arrays.fill(bricks, true);
            lives = 3;
            score = 0;
            launched = false;
            return true;
        }
        if (!launched) {
            launched = true;
            vx = (Math.random() - 0.5) * 0.6;
            vy = -0.7;
            return true;
        }
        return false;
    }
}
