package dev.lscity.citylife.client.game;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

import dev.lscity.citylife.client.ui.PhoneUi;

/** Тетрис в телефоне: стакан 10x18, семь фигур, ускорение по уровням. */
@OnlyIn(Dist.CLIENT)
public class TetrisGame {
    private static final int W = 10;
    private static final int H = 18;

    /** Семь фигур в четырёх поворотах: битовые маски 4x4. */
    private static final int[][] SHAPES = {
            {0x0F00, 0x2222, 0x00F0, 0x4444},  // I
            {0x8E00, 0x6440, 0x0E20, 0x44C0},  // J
            {0x2E00, 0x4460, 0x0E80, 0xC440},  // L
            {0x6600, 0x6600, 0x6600, 0x6600},  // O
            {0x6C00, 0x4620, 0x06C0, 0x8C40},  // S
            {0x4E00, 0x4640, 0x0E40, 0x4C40},  // T
            {0xC600, 0x2640, 0x0C60, 0x4C80},  // Z
    };
    private static final int[] COLOURS = {
            0xFF45D0F0, 0xFF3F7BFF, 0xFFFFA24D, 0xFFFFD24D,
            0xFF7BE07B, 0xFFB07BFF, 0xFFFF6B6B,
    };

    private final Random random = new Random();
    private final int[][] field = new int[H][W];

    private int piece;
    private int next;
    private int rotation;
    private int px;
    private int py;
    private int score;
    private int lines;
    private boolean over;
    private long lastFall;

    public TetrisGame() {
        next = random.nextInt(SHAPES.length);
        spawn();
    }

    public boolean isOver() {
        return over;
    }

    public int score() {
        return score;
    }

    public int lines() {
        return lines;
    }

    public void restart() {
        for (int[] row : field) {
            java.util.Arrays.fill(row, 0);
        }
        score = 0;
        lines = 0;
        over = false;
        next = random.nextInt(SHAPES.length);
        spawn();
    }

    private void spawn() {
        piece = next;
        next = random.nextInt(SHAPES.length);
        rotation = 0;
        px = 3;
        py = -1;
        if (collides(px, py, rotation)) {
            over = true;
        }
    }

    private boolean cell(int shape, int rot, int x, int y) {
        return (SHAPES[shape][rot] & (0x8000 >> (y * 4 + x))) != 0;
    }

    private boolean collides(int nx, int ny, int rot) {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (!cell(piece, rot, x, y)) {
                    continue;
                }
                int fx = nx + x;
                int fy = ny + y;
                if (fx < 0 || fx >= W || fy >= H) {
                    return true;
                }
                if (fy >= 0 && field[fy][fx] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void lock() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (cell(piece, rotation, x, y) && py + y >= 0) {
                    field[py + y][px + x] = piece + 1;
                }
            }
        }
        int cleared = 0;
        for (int y = H - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < W; x++) {
                if (field[y][x] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                for (int row = y; row > 0; row--) {
                    field[row] = field[row - 1].clone();
                }
                field[0] = new int[W];
                y++;
            }
        }
        if (cleared > 0) {
            lines += cleared;
            // Четыре линии за раз ценятся заметно дороже четырёх по одной.
            score += switch (cleared) {
                case 1 -> 100;
                case 2 -> 300;
                case 3 -> 500;
                default -> 800;
            };
        }
        spawn();
    }

    /** Ход времени: чем больше линий собрано, тем быстрее падение. */
    public void tick() {
        if (over) {
            return;
        }
        long now = System.currentTimeMillis();
        long delay = Math.max(120, 700 - (long) (lines / 5) * 60);
        if (now - lastFall < delay) {
            return;
        }
        lastFall = now;
        if (collides(px, py + 1, rotation)) {
            lock();
        } else {
            py++;
        }
    }

    public boolean key(int code) {
        if (over) {
            if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_SPACE) {
                restart();
                return true;
            }
            return false;
        }
        switch (code) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                if (!collides(px - 1, py, rotation)) {
                    px--;
                }
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                if (!collides(px + 1, py, rotation)) {
                    px++;
                }
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                if (!collides(px, py + 1, rotation)) {
                    py++;
                    score++;
                }
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                int turned = (rotation + 1) % 4;
                // Пристенный поворот: пробуем сдвинуться, если мешает стена.
                for (int shift : new int[]{0, -1, 1, -2, 2}) {
                    if (!collides(px + shift, py, turned)) {
                        px += shift;
                        rotation = turned;
                        break;
                    }
                }
            }
            case GLFW.GLFW_KEY_SPACE -> {
                while (!collides(px, py + 1, rotation)) {
                    py++;
                    score += 2;
                }
                lock();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
        int cell = Math.min((w - 8) / W, (h - 26) / H);
        int fieldW = cell * W;
        int fieldH = cell * H;
        int fx = x + (w - fieldW) / 2;
        int fy = y + 20;

        PhoneUi.roundedRect(g, fx - 3, fy - 3, fieldW + 6, fieldH + 6, 4, 0xFF10131C);
        for (int row = 0; row < H; row++) {
            for (int col = 0; col < W; col++) {
                int value = field[row][col];
                int colour = value == 0 ? 0xFF171B27 : COLOURS[value - 1];
                g.fill(fx + col * cell, fy + row * cell,
                        fx + col * cell + cell - 1, fy + row * cell + cell - 1, colour);
            }
        }
        if (!over) {
            for (int cy = 0; cy < 4; cy++) {
                for (int cx = 0; cx < 4; cx++) {
                    if (cell(piece, rotation, cx, cy) && py + cy >= 0) {
                        g.fill(fx + (px + cx) * cell, fy + (py + cy) * cell,
                                fx + (px + cx) * cell + cell - 1,
                                fy + (py + cy) * cell + cell - 1, COLOURS[piece]);
                    }
                }
            }
        }

        g.drawString(font, "Очки " + score, x + 4, y + 6, 0xFFE8E8F0, false);
        String linesText = "Линии " + lines;
        g.drawString(font, linesText, x + w - 4 - font.width(linesText), y + 6, 0xFF8E94A8, false);

        if (over) {
            PhoneUi.roundedRect(g, fx, fy + fieldH / 2 - 16, fieldW, 32, 4, 0xE0111520);
            String text = "Игра окончена";
            g.drawString(font, text, fx + (fieldW - font.width(text)) / 2,
                    fy + fieldH / 2 - 10, 0xFFFF6B6B, false);
            String hint = "Enter — заново";
            g.drawString(font, hint, fx + (fieldW - font.width(hint)) / 2,
                    fy + fieldH / 2 + 2, 0xFF8E94A8, false);
        }
    }
}
