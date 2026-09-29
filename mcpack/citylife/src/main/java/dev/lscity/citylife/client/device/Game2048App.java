package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/** 2048: стрелки или WASD сдвигают плитки, одинаковые складываются. */
class Game2048App extends DeviceApp {

    private final int[][] grid = new int[4][4];
    private final Random random = new Random();
    private int score;
    private boolean over;

    Game2048App(DeviceScreen screen) {
        super(screen);
        spawn();
        spawn();
    }

    @Override
    public String title() {
        return screen.appTitle("game2048");
    }

    private void spawn() {
        int free = 0;
        for (int[] row : grid) {
            for (int v : row) {
                free += v == 0 ? 1 : 0;
            }
        }
        if (free == 0) {
            return;
        }
        int pick = random.nextInt(free);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (grid[y][x] == 0 && pick-- == 0) {
                    grid[y][x] = random.nextInt(10) == 0 ? 4 : 2;
                    return;
                }
            }
        }
    }

    /** Сдвиг влево одной строки; возвращает, изменилось ли что-то. */
    private boolean slide(int[] line) {
        int[] out = new int[4];
        int n = 0;
        int last = 0;
        for (int v : line) {
            if (v == 0) {
                continue;
            }
            if (last == v) {
                out[n - 1] = v * 2;
                score += v * 2;
                last = 0;
            } else {
                out[n++] = v;
                last = v;
            }
        }
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            changed |= line[i] != out[i];
            line[i] = out[i];
        }
        return changed;
    }

    private boolean move(int dir) {
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            int[] line = new int[4];
            for (int j = 0; j < 4; j++) {
                line[j] = switch (dir) {
                    case 0 -> grid[i][j];
                    case 1 -> grid[i][3 - j];
                    case 2 -> grid[j][i];
                    default -> grid[3 - j][i];
                };
            }
            changed |= slide(line);
            for (int j = 0; j < 4; j++) {
                switch (dir) {
                    case 0 -> grid[i][j] = line[j];
                    case 1 -> grid[i][3 - j] = line[j];
                    case 2 -> grid[j][i] = line[j];
                    default -> grid[3 - j][i] = line[j];
                }
            }
        }
        return changed;
    }

    private boolean stuck() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (grid[y][x] == 0 || (x < 3 && grid[y][x] == grid[y][x + 1])
                        || (y < 3 && grid[y][x] == grid[y + 1][x])) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean key(int code) {
        int dir = switch (code) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> 0;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> 1;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> 2;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> 3;
            default -> -1;
        };
        if (dir < 0 || over) {
            return false;
        }
        if (move(dir)) {
            spawn();
        }
        over = stuck();
        return true;
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (over) {
            screen.restart();
            return true;
        }
        return false;
    }

    private static int colour(int v) {
        return switch (v) {
            case 0 -> 0xFF3A3F50;
            case 2 -> 0xFFEEE4DA;
            case 4 -> 0xFFEDE0C8;
            case 8 -> 0xFFF2B179;
            case 16 -> 0xFFF59563;
            case 32 -> 0xFFF67C5F;
            case 64 -> 0xFFF65E3B;
            case 128, 256 -> 0xFFEDCF72;
            case 512, 1024 -> 0xFFEDC850;
            default -> 0xFFEDC22E;
        };
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        screen.text(g, over ? "Ходов нет · счёт " + score + " · клик — заново"
                : "Счёт: " + score + " · стрелки или WASD", area[0] + 2, area[1] + 3,
                over ? t.red() : t.dim());
        int size = Math.min(area[2] - 8, area[3] - 20);
        int c = size / 4;
        int x0 = area[0] + (area[2] - c * 4) / 2;
        int y0 = area[1] + 16;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int v = grid[y][x];
                int px = x0 + x * c;
                int py = y0 + y * c;
                g.fill(px + 1, py + 1, px + c - 1, py + c - 1, colour(v));
                if (v > 0) {
                    String s = String.valueOf(v);
                    g.drawString(screen.font(), s, px + (c - screen.font().width(s)) / 2,
                            py + (c - 8) / 2, v <= 4 ? 0xFF776E65 : 0xFFFFFFFF, false);
                }
            }
        }
    }
}
