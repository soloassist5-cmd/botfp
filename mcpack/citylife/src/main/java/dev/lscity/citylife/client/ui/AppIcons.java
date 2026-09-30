package dev.lscity.citylife.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Иконки приложений рисуются кодом.
 *
 * Текстур нет намеренно: иконка масштабируется под любой размер плитки. У
 * каждого приложения свой силуэт и свой цвет — раньше «Умный дом» и
 * «Маркет» рисовались одинаково (дужка над прямоугольником) и путались.
 */
@OnlyIn(Dist.CLIENT)
public final class AppIcons {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xCCFFFFFF;
    private static final int INK = 0x55000000;

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
            case "store" -> new int[]{0xFF5AA8FF, 0xFF2F55D6};
            case "marketplace" -> new int[]{0xFFC77DFF, 0xFF7B2FBF};
            case "settings" -> new int[]{0xFF9AA3B5, 0xFF555D70};
            case "calc" -> new int[]{0xFF4A4F5E, 0xFF22252E};
            case "notes" -> new int[]{0xFFFFE27A, 0xFFE0A93A};
            case "compass" -> new int[]{0xFFEFEFEF, 0xFFB8BEC9};
            case "flashlight" -> new int[]{0xFF3A3F50, 0xFF15171F};
            case "sysinfo" -> new int[]{0xFF2FD1A0, 0xFF16806A};
            case "tetris" -> new int[]{0xFF8E7BFF, 0xFF4C3FD0};
            case "snake" -> new int[]{0xFF7BE07B, 0xFF2F9E4F};
            case "cameras" -> new int[]{0xFF5B6B8C, 0xFF263049};
            case "browser" -> new int[]{0xFF45A3F0, 0xFF1A5FA8};
            case "mail" -> new int[]{0xFFFF8A65, 0xFFD04A2E};
            case "music" -> new int[]{0xFFFF5C8A, 0xFFB0245A};
            case "mines" -> new int[]{0xFF9AA3B5, 0xFF4A5268};
            case "game2048" -> new int[]{0xFFF2B179, 0xFFD27A2E};
            case "terminal" -> new int[]{0xFF2A2F38, 0xFF0E1014};
            case "jobs" -> new int[]{0xFFFFC857, 0xFFD9822B};
            case "phone" -> new int[]{0xFF6BE07B, 0xFF1F9E4A};
            case "homes" -> new int[]{0xFF6FD08C, 0xFF2E8A5A};
            default -> new int[]{0xFF6E7488, 0xFF3A3F50};
        };
    }

    private static void glyph(GuiGraphics g, String id, int x, int y, int s) {
        int cx = x + s / 2;
        int cy = y + s / 2;
        switch (id) {
            case "messages" -> {
                // Облачко реплики с хвостиком.
                int w = s / 2, h = s / 3;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2 - 1, w, h, h / 3, WHITE);
                for (int i = 0; i < s / 10; i++) {
                    g.fill(cx - w / 4 + i, cy + h / 2 - 1 + i, cx - w / 4 + s / 10,
                            cy + h / 2 + i, WHITE);
                }
            }
            case "contacts" -> {
                PhoneUi.disc(g, cx, cy - s / 8, s / 8, WHITE);
                PhoneUi.roundedRect(g, cx - s / 5, cy + s / 16, s * 2 / 5, s / 5, s / 10, WHITE);
            }
            case "bank" -> {
                int w = s / 2, h = s / 3;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2, w, h, 2, WHITE);
                g.fill(cx - w / 2, cy - h / 2 + 3, cx + w / 2, cy - h / 2 + 6, INK);
                g.fill(cx - w / 2 + 3, cy + h / 2 - 5, cx - w / 2 + 9, cy + h / 2 - 3, INK);
            }
            case "navigator" -> {
                // Стрелка навигатора: треугольник с вырезом снизу.
                for (int i = 0; i < s / 3; i++) {
                    g.fill(cx - i / 2, cy - s / 5 + i, cx + i / 2 + 1, cy - s / 5 + i + 1, WHITE);
                }
                for (int i = 0; i < s / 8; i++) {
                    g.fill(cx - i / 2, cy + s / 8 - i + s / 12, cx + i / 2 + 1,
                            cy + s / 8 - i + s / 12 + 1, 0xFF1A7FA8);
                }
            }
            case "locks" -> {
                // Домик с замочной скважиной: крыша треугольником, стены квадратом.
                int half = s / 4;
                for (int i = 0; i <= half; i++) {
                    g.fill(cx - i, cy - s / 5 + i / 1, cx + i + 1, cy - s / 5 + i + 1, WHITE);
                }
                g.fill(cx - half + 2, cy + s / 20, cx + half - 1, cy + s / 4, WHITE);
                PhoneUi.disc(g, cx, cy + s / 8, Math.max(1, s / 16), 0xFFD06A1E);
                g.fill(cx - 1, cy + s / 8, cx + 1, cy + s / 5 + 1, 0xFFD06A1E);
            }
            case "jobs" -> {
                // Портфель: корпус, ручка сверху и застёжка.
                int w = s / 2;
                int h = s / 3;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2 + 2, w, h, 2, WHITE);
                g.fill(cx - s / 10, cy - h / 2 - 2, cx + s / 10, cy - h / 2, WHITE);
                g.fill(cx - s / 10, cy - h / 2 - 2, cx - s / 10 + 2, cy - h / 2 + 2, WHITE);
                g.fill(cx + s / 10 - 2, cy - h / 2 - 2, cx + s / 10, cy - h / 2 + 2, WHITE);
                g.fill(cx - w / 2, cy + 1, cx + w / 2, cy + 2, 0xFFD9822B);
                g.fill(cx - 2, cy - 1, cx + 2, cy + 4, 0xFFD9822B);
            }
            case "phone" -> {
                // Трубка: две скруглённые «чашки» и дуга между ними.
                int r = Math.max(2, s / 9);
                PhoneUi.disc(g, cx - s / 6, cy + s / 7, r, WHITE);
                PhoneUi.disc(g, cx + s / 6, cy - s / 7, r, WHITE);
                for (int i = -s / 6; i <= s / 6; i++) {
                    int yy = cy - i * 6 / 7 + (i * i) / Math.max(1, s / 3);
                    g.fill(cx + i - 1, yy - 1, cx + i + 1, yy + 1, WHITE);
                }
            }
            case "homes" -> {
                // Домик с дверью.
                int half = s / 4;
                for (int i = 0; i <= half; i++) {
                    g.fill(cx - i, cy - s / 5 + i, cx + i + 1, cy - s / 5 + i + 1, WHITE);
                }
                g.fill(cx - half + 2, cy + s / 20, cx + half - 1, cy + s / 4, WHITE);
                g.fill(cx - 2, cy + s / 10, cx + 2, cy + s / 4, 0xFF2E8A5A);
            }
            case "sos" -> {
                PhoneUi.ring(g, cx, cy, s / 4, 2, WHITE);
                g.fill(cx - 1, cy - s / 8, cx + 1, cy + s / 16, WHITE);
                g.fill(cx - 1, cy + s / 10, cx + 1, cy + s / 8, WHITE);
            }
            case "store" -> {
                // Магазин приложений: четыре плитки сеткой.
                int b = s / 7;
                int gap = Math.max(1, s / 24);
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 2; col++) {
                        PhoneUi.roundedRect(g, cx - b - gap + col * (b + gap * 2),
                                cy - b - gap + row * (b + gap * 2), b, b, 2,
                                row == col ? WHITE : SOFT);
                    }
                }
            }
            case "marketplace" -> {
                // Посылка: коробка с лентой крест-накрест.
                int w = s / 2, h = s * 2 / 5;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2 + 1, w, h, 2, WHITE);
                g.fill(cx - 1, cy - h / 2 + 1, cx + 1, cy + h / 2 + 1, 0xFF7B2FBF);
                g.fill(cx - w / 2, cy - h / 2 + h / 3, cx + w / 2, cy - h / 2 + h / 3 + 2,
                        0xFF7B2FBF);
                PhoneUi.arcTop(g, cx - 3, cy - h / 2 + 1, 3, 1, WHITE);
                PhoneUi.arcTop(g, cx + 3, cy - h / 2 + 1, 3, 1, WHITE);
            }
            case "settings" -> {
                // Шестерёнка: кольцо и восемь зубцов.
                int r = s / 5;
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    int tx = cx + (int) Math.round(Math.cos(a) * (r + 2));
                    int ty = cy + (int) Math.round(Math.sin(a) * (r + 2));
                    g.fill(tx - 2, ty - 2, tx + 2, ty + 2, WHITE);
                }
                PhoneUi.ring(g, cx, cy, r + 1, Math.max(2, r / 2), WHITE);
            }
            case "calc" -> {
                int w = s * 2 / 5, h = s / 2;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2, w, h, 2, 0xFFE8EAF0);
                g.fill(cx - w / 2 + 2, cy - h / 2 + 2, cx + w / 2 - 2, cy - h / 2 + h / 4,
                        0xFF7BE07B);
                int k = Math.max(2, s / 14);
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 3; col++) {
                        g.fill(cx - w / 2 + 3 + col * (k + 2), cy + row * (k + 2),
                                cx - w / 2 + 3 + col * (k + 2) + k, cy + row * (k + 2) + k,
                                col == 2 ? 0xFFFFA24D : 0xFF4A4F5E);
                    }
                }
            }
            case "notes" -> {
                int w = s * 2 / 5, h = s / 2;
                PhoneUi.roundedRect(g, cx - w / 2, cy - h / 2, w, h, 2, WHITE);
                for (int line = 1; line <= 3; line++) {
                    g.fill(cx - w / 2 + 3, cy - h / 2 + line * h / 4, cx + w / 2 - 3,
                            cy - h / 2 + line * h / 4 + 1, 0x66C08A20);
                }
            }
            case "compass" -> {
                PhoneUi.ring(g, cx, cy, s / 4, 2, 0xFF3A3F50);
                for (int i = 0; i < s / 5; i++) {
                    g.fill(cx - (s / 5 - i) / 3, cy - i, cx + (s / 5 - i) / 3 + 1, cy - i + 1,
                            0xFFE0413A);
                    g.fill(cx - (s / 5 - i) / 3, cy + i, cx + (s / 5 - i) / 3 + 1, cy + i + 1,
                            0xFF3A3F50);
                }
            }
            case "flashlight" -> {
                // Луч фонаря расширяется вверх, внизу корпус.
                for (int i = 0; i < s / 4; i++) {
                    g.fill(cx - i / 2 - 2, cy - s / 5 + i, cx + i / 2 + 3, cy - s / 5 + i + 1,
                            PhoneUi.alpha(0xFFFFE066, 0.2F + i / (float) (s / 4)));
                }
                g.fill(cx - 2, cy + s / 20, cx + 3, cy + s / 4, WHITE);
            }
            case "sysinfo" -> {
                // Микросхема: квадрат с ножками.
                int b = s / 5;
                PhoneUi.roundedRect(g, cx - b, cy - b, b * 2, b * 2, 2, WHITE);
                for (int i = -b + 2; i < b - 1; i += 3) {
                    g.fill(cx + i, cy - b - 2, cx + i + 1, cy - b, SOFT);
                    g.fill(cx + i, cy + b, cx + i + 1, cy + b + 2, SOFT);
                    g.fill(cx - b - 2, cy + i, cx - b, cy + i + 1, SOFT);
                    g.fill(cx + b, cy + i, cx + b + 2, cy + i + 1, SOFT);
                }
                g.fill(cx - b / 2, cy - b / 2, cx + b / 2, cy + b / 2, 0xFF16806A);
            }
            case "tetris" -> {
                int b = Math.max(2, s / 8);
                g.fill(cx - b, cy - b * 2, cx, cy - b, WHITE);
                g.fill(cx - b, cy - b, cx + b, cy, WHITE);
                g.fill(cx, cy, cx + b * 2, cy + b, SOFT);
                g.fill(cx - b * 2, cy, cx, cy + b, SOFT);
            }
            case "snake" -> {
                int b = Math.max(2, s / 9);
                g.fill(cx - b * 3, cy - b, cx, cy, WHITE);
                g.fill(cx - b, cy - b * 2, cx, cy - b, WHITE);
                g.fill(cx, cy, cx + b * 2, cy + b, WHITE);
                PhoneUi.disc(g, cx + b * 2, cy + b / 2, b, 0xFFFFE066);
            }
            case "cameras" -> {
                // Камера на кронштейне: корпус, объектив и штанга.
                int b = s / 5;
                PhoneUi.roundedRect(g, cx - b - 2, cy - b / 2 - 2, b * 2, b + 2, 2, WHITE);
                PhoneUi.disc(g, cx + b - 1, cy - 1, Math.max(2, s / 12), 0xFF263049);
                g.fill(cx - b / 2, cy + b / 2, cx - b / 2 + 2, cy + b + 2, WHITE);
                g.fill(cx - b - 2, cy + b + 1, cx + 1, cy + b + 3, WHITE);
            }
            case "browser" -> {
                // Глобус: круг, экватор и меридиан.
                PhoneUi.ring(g, cx, cy, s / 4, 2, WHITE);
                g.fill(cx - s / 4, cy - 1, cx + s / 4, cy + 1, WHITE);
                g.fill(cx - 1, cy - s / 4, cx + 1, cy + s / 4, WHITE);
            }
            case "mail" -> {
                // Конверт с клапаном.
                int w = s / 3;
                int h = s / 4;
                PhoneUi.roundedRect(g, cx - w, cy - h, w * 2, h * 2, 2, WHITE);
                for (int i = 0; i < w; i++) {
                    g.fill(cx - w + i, cy - h + i / 2, cx - w + i + 1, cy - h + i / 2 + 1, 0xFFD04A2E);
                    g.fill(cx + w - i - 1, cy - h + i / 2, cx + w - i, cy - h + i / 2 + 1, 0xFFD04A2E);
                }
            }
            case "music" -> {
                // Нота: головка и штиль с флажком.
                PhoneUi.disc(g, cx - s / 10, cy + s / 7, Math.max(2, s / 9), WHITE);
                g.fill(cx - s / 10 + s / 9 - 1, cy - s / 4, cx - s / 10 + s / 9 + 1, cy + s / 7, WHITE);
                g.fill(cx - s / 10 + s / 9, cy - s / 4, cx + s / 5, cy - s / 4 + 3, WHITE);
            }
            case "mines" -> {
                // Мина: круг с шипами.
                PhoneUi.disc(g, cx, cy, s / 6, 0xFF15171F);
                g.fill(cx - s / 4, cy - 1, cx + s / 4, cy + 1, 0xFF15171F);
                g.fill(cx - 1, cy - s / 4, cx + 1, cy + s / 4, 0xFF15171F);
                g.fill(cx - s / 12, cy - s / 12, cx - s / 24, cy - s / 24, WHITE);
            }
            case "game2048" -> {
                // Четыре плитки.
                int t = s / 7;
                g.fill(cx - t * 2, cy - t * 2, cx - 1, cy - 1, WHITE);
                g.fill(cx + 1, cy - t * 2, cx + t * 2, cy - 1, 0xFFFFE0B0);
                g.fill(cx - t * 2, cy + 1, cx - 1, cy + t * 2, 0xFFFFE0B0);
                g.fill(cx + 1, cy + 1, cx + t * 2, cy + t * 2, WHITE);
            }
            case "terminal" -> {
                // Приглашение командной строки: «>_».
                int t = s / 8;
                for (int i = 0; i < t; i++) {
                    g.fill(cx - s / 4 + i, cy - t + i, cx - s / 4 + i + 2, cy - t + i + 2, 0xFF7BE07B);
                    g.fill(cx - s / 4 + i, cy + t - i, cx - s / 4 + i + 2, cy + t - i + 2, 0xFF7BE07B);
                }
                g.fill(cx, cy + t, cx + s / 4, cy + t + 2, 0xFF7BE07B);
            }
            default -> PhoneUi.disc(g, cx, cy, s / 5, WHITE);
        }
    }
}
