package dev.lscity.citylife.client.game;

import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/** Змейка: поле 14x14, еда, ускорение с ростом. */
@OnlyIn(Dist.CLIENT)
public class SnakeGame {
    private static final int SIZE = 14;

    private final Random random = new Random();
    private final Deque<int[]> body = new ArrayDeque<>();
    private final boolean[][] busy = new boolean[SIZE][SIZE];

    private int dirX = 1;
    private int dirZ;
    private int pendingX = 1;
    private int pendingZ;
    private int foodX;
    private int foodZ;
    private int score;
    private boolean over;
    private long lastStep;

    public SnakeGame() {
        restart();
    }

    public boolean isOver() {
        return over;
    }

    public int score() {
        return score;
    }

    public void restart() {
        body.clear();
        for (boolean[] row : busy) {
            java.util.Arrays.fill(row, false);
        }
        int mid = SIZE / 2;
        for (int i = 0; i < 3; i++) {
            int[] cell = {mid - i, mid};
            body.addLast(cell);
            busy[cell[1]][cell[0]] = true;
        }
        dirX = 1;
        dirZ = 0;
        pendingX = 1;
        pendingZ = 0;
        score = 0;
        over = false;
        placeFood();
    }

    private void placeFood() {
        // Еду кладём только в свободную клетку, иначе она окажется под змейкой.
        for (int attempt = 0; attempt < 400; attempt++) {
            int x = random.nextInt(SIZE);
            int z = random.nextInt(SIZE);
            if (!busy[z][x]) {
                foodX = x;
                foodZ = z;
                return;
            }
        }
        over = true;
    }

    public void tick() {
        if (over) {
            return;
        }
        long now = System.currentTimeMillis();
        long delay = Math.max(90, 220 - (long) score * 4);
        if (now - lastStep < delay) {
            return;
        }
        lastStep = now;
        dirX = pendingX;
        dirZ = pendingZ;

        int[] head = body.peekLast();
        int nx = head[0] + dirX;
        int nz = head[1] + dirZ;
        if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE || busy[nz][nx]) {
            over = true;
            return;
        }
        int[] cell = {nx, nz};
        body.addLast(cell);
        busy[nz][nx] = true;

        if (nx == foodX && nz == foodZ) {
            score++;
            placeFood();
        } else {
            int[] tail = body.pollFirst();
            if (tail != null) {
                busy[tail[1]][tail[0]] = false;
            }
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
        // Разворот на 180 градусов запрещаем: иначе змейка съедает сама себя.
        switch (code) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                if (dirX == 0) {
                    pendingX = -1;
                    pendingZ = 0;
                }
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                if (dirX == 0) {
                    pendingX = 1;
                    pendingZ = 0;
                }
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                if (dirZ == 0) {
                    pendingX = 0;
                    pendingZ = -1;
                }
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                if (dirZ == 0) {
                    pendingX = 0;
                    pendingZ = 1;
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
        int cell = Math.min((w - 8) / SIZE, (h - 26) / SIZE);
        int side = cell * SIZE;
        int fx = x + (w - side) / 2;
        int fy = y + 20;

        PhoneUi.roundedRect(g, fx - 3, fy - 3, side + 6, side + 6, 4, 0xFF10131C);
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if ((row + col) % 2 == 0) {
                    g.fill(fx + col * cell, fy + row * cell,
                            fx + col * cell + cell, fy + row * cell + cell, 0xFF171B27);
                }
            }
        }
        g.fill(fx + foodX * cell + 1, fy + foodZ * cell + 1,
                fx + foodX * cell + cell - 1, fy + foodZ * cell + cell - 1, 0xFFFF6B6B);

        int index = 0;
        int total = body.size();
        for (int[] part : body) {
            float t = total <= 1 ? 1F : index / (float) (total - 1);
            int colour = PhoneUi.lerp(0xFF2F9E4F, 0xFF7BE07B, t);
            g.fill(fx + part[0] * cell, fy + part[1] * cell,
                    fx + part[0] * cell + cell - 1, fy + part[1] * cell + cell - 1, colour);
            index++;
        }

        g.drawString(font, "Очки " + score, x + 4, y + 6, 0xFFE8E8F0, false);
        if (over) {
            PhoneUi.roundedRect(g, fx, fy + side / 2 - 16, side, 32, 4, 0xE0111520);
            String text = "Съела сама себя";
            g.drawString(font, text, fx + (side - font.width(text)) / 2,
                    fy + side / 2 - 10, 0xFFFF6B6B, false);
            String hint = "Enter — заново";
            g.drawString(font, hint, fx + (side - font.width(hint)) / 2,
                    fy + side / 2 + 2, 0xFF8E94A8, false);
        }
    }
}
