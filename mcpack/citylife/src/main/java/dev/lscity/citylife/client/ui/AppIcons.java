package dev.lscity.citylife.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Иконки приложений рисуются кодом.
 *
 * Текстур нет намеренно: иконка масштабируется под любой размер плитки,
 * а новая добавляется одной веткой switch, без возни с атласом и моделями.
 */
@OnlyIn(Dist.CLIENT)
public final class AppIcons {

    private AppIcons() {
    }

    /** Плитка приложения: скруглённый квадрат с градиентом и белым значком. */
    public static void draw(GuiGraphics g, String id, int x, int y, int size) {
        int[] colours = palette(id);
        PhoneUi.shadow(g, x, y, size, size, size / 4);
        PhoneUi.roundedGradient(g, x, y, size, size, size / 4, colours[0], colours[1]);
        PhoneUi.roundedRect(g, x, y, size, size / 2, size / 4, 0x14FFFFFF);
        glyph(g, id, x, y, size);
    }

    private static int[] palette(String id) {
        return switch (id) {
            case "messages" -> new int[]{0xFF3FA9F5, 0xFF1E6FD0};
            case "contacts" -> new int[]{0xFFB07BFF, 0xFF6F3FD0};
            case "bank" -> new int[]{0xFF57D48B, 0xFF1F8F57};
            case "navigator" -> new int[]{0xFF45D0F0, 0xFF1A7FA8};
            case "locks" -> new int[]{0xFFFFA24D, 0xFFD06A1E};
            case "sos" -> new int[]{0xFFFF6B6B, 0xFFC02B3F};
            case "market" -> new int[]{0xFFFFC44D, 0xFFD08A1E};
            case "tetris" -> new int[]{0xFF8E7BFF, 0xFF4C3FD0};
            case "snake" -> new int[]{0xFF7BE07B, 0xFF2F9E4F};
            default -> new int[]{0xFF6E7488, 0xFF3A3F50};
        };
    }

    private static void glyph(GuiGraphics g, String id, int x, int y, int s) {
        int white = 0xFFFFFFFF;
        int soft = 0xCCFFFFFF;
        int cx = x + s / 2;
        int cy = y + s / 2;
        switch (id) {
            case "messages" -> {
                // Конверт: прямоугольник и сходящиеся к центру линии клапана.
                int w = s / 2, h = s / 3;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2, w, h, 2, white);
                for (int i = 0; i < w / 2; i++) {
                    g.fill(cx - w / 2 + i, cy - h / 2 + i, cx - w / 2 + i + 1,
                            cy - h / 2 + i + 1, 0x40000000);
                    g.fill(cx + w / 2 - i - 1, cy - h / 2 + i, cx + w / 2 - i,
                            cy - h / 2 + i + 1, 0x40000000);
                }
            }
            case "contacts" -> {
                PhoneUi.disc(g, cx, cy - s / 8, s / 8, white);
                PhoneUi.roundedRect(g, cx - s / 5, cy + s / 16, s * 2 / 5, s / 5, s / 10, white);
            }
            case "bank" -> {
                int w = s / 2, h = s / 3;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2, w, h, 2, white);
                g.fill(cx - w / 2, cy - h / 2 + 3, cx + w / 2, cy - h / 2 + 6, 0x66000000);
                g.fill(cx - w / 2 + 3, cy + h / 2 - 5, cx - w / 2 + 9, cy + h / 2 - 3, 0x66000000);
            }
            case "navigator" -> {
                // Стрелка-компас: треугольник вверх и вырез снизу.
                for (int i = 0; i < s / 3; i++) {
                    g.fill(cx - i, cy - s / 5 + i + i / 2, cx + i + 1,
                            cy - s / 5 + i + i / 2 + 1, white);
                }
                g.fill(cx - 2, cy + s / 8, cx + 2, cy + s / 5, 0x55000000);
                PhoneUi.disc(g, cx, cy - s / 5, 1, 0x88FFFFFF);
            }
            case "locks" -> {
                PhoneUi.arcTop(g, cx, cy - s / 16, s / 7, 2, white);
                PhoneUi.roundedRect(g, cx - s / 5, cy - s / 16, s * 2 / 5, s / 3, 2, white);
                g.fill(cx - 1, cy + s / 12, cx + 1, cy + s / 6, 0x55000000);
            }
            case "sos" -> {
                PhoneUi.ring(g, cx, cy, s / 4, 2, white);
                g.fill(cx - 1, cy - s / 8, cx + 1, cy + s / 16, white);
                g.fill(cx - 1, cy + s / 10, cx + 1, cy + s / 8, white);
            }
            case "market" -> {
                // Сумка: ручка дугой сверху, под ней корпус.
                PhoneUi.arcTop(g, cx, cy - s / 10, s / 8, 2, soft);
                PhoneUi.roundedRect(g, cx - s / 5, cy - s / 10, s * 2 / 5, s / 3, 2, white);
            }
            case "tetris" -> {
                int b = Math.max(2, s / 8);
                g.fill(cx - b, cy - b * 2, cx, cy - b, white);
                g.fill(cx - b, cy - b, cx + b, cy, white);
                g.fill(cx, cy, cx + b * 2, cy + b, soft);
                g.fill(cx - b * 2, cy, cx, cy + b, soft);
            }
            case "snake" -> {
                int b = Math.max(2, s / 9);
                g.fill(cx - b * 3, cy - b, cx, cy, white);
                g.fill(cx - b, cy - b * 2, cx, cy - b, white);
                g.fill(cx, cy, cx + b * 2, cy + b, white);
                PhoneUi.disc(g, cx + b * 2, cy + b / 2, b, 0xFFFFE066);
            }
            default -> PhoneUi.disc(g, cx, cy, s / 5, white);
        }
    }
}
