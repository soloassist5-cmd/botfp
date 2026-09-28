package dev.lscity.citylife.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Примитивы отрисовки телефона.
 *
 * Интерфейс рисуется кодом, а не текстурами: так он тянется под любой размер
 * окна, а цвета и скругления правятся в одном месте. Скругление делается
 * построчно — для каждой строки считается, насколько её надо укоротить,
 * чтобы угол выглядел дугой, а не ступенькой.
 */
@OnlyIn(Dist.CLIENT)
public final class PhoneUi {

    private PhoneUi() {
    }

    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius,
                                   int colour) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.min(radius, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            int inset = inset(row, h, r);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, colour);
        }
    }

    /**
     * Скруглённая рамка толщиной в пиксель.
     *
     * Рисуем именно контур, а не прямоугольник поверх прямоугольника: залить
     * внутренность «прозрачным» нельзя — fill смешивает цвета, а не стирает.
     */
    public static void roundedOutline(GuiGraphics g, int x, int y, int w, int h, int radius,
                                      int colour) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        int previous = -1;
        for (int row = 0; row < h; row++) {
            int inset = inset(row, h, r);
            if (row == 0 || row == h - 1) {
                g.fill(x + inset, y + row, x + w - inset, y + row + 1, colour);
            } else {
                // На скруглении строка сдвигается в сторону, и, чтобы не осталось
                // дырок, закрашиваем весь пройденный сдвиг.
                int wide = previous < 0 ? inset : Math.max(inset, previous);
                g.fill(x + inset, y + row, x + wide + 1, y + row + 1, colour);
                g.fill(x + w - wide - 1, y + row, x + w - inset, y + row + 1, colour);
            }
            previous = inset;
        }
    }

    private static int inset(int row, int h, int r) {
        if (row < r) {
            return r - (int) Math.round(Math.sqrt(r * r - (r - row - 0.5) * (r - row - 0.5)));
        }
        if (row >= h - r) {
            double d = row - (h - r) + 0.5;
            return r - (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d)));
        }
        return 0;
    }

    /** Вертикальный градиент со скруглением: обои и карточки. */
    public static void roundedGradient(GuiGraphics g, int x, int y, int w, int h, int radius,
                                       int top, int bottom) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            int inset = inset(row, h, r);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1,
                    lerp(top, bottom, row / (float) h));
        }
    }

    public static int lerp(int from, int to, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int a = channel(from, 24), r = channel(from, 16), gr = channel(from, 8), b = channel(from, 0);
        int a2 = channel(to, 24), r2 = channel(to, 16), g2 = channel(to, 8), b2 = channel(to, 0);
        return (Math.round(a + (a2 - a) * t) << 24)
                | (Math.round(r + (r2 - r) * t) << 16)
                | (Math.round(gr + (g2 - gr) * t) << 8)
                | Math.round(b + (b2 - b) * t);
    }

    public static int alpha(int colour, float factor) {
        int a = Math.round(channel(colour, 24) * Math.max(0F, Math.min(1F, factor)));
        return (a << 24) | (colour & 0x00FFFFFF);
    }

    private static int channel(int colour, int shift) {
        return (colour >> shift) & 0xFF;
    }

    /** Мягкая тень под карточкой: несколько полупрозрачных слоёв. */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius) {
        for (int i = 3; i >= 1; i--) {
            roundedRect(g, x - i, y - i + 2, w + i * 2, h + i * 2, radius + i, 0x18000000);
        }
    }

    /** Кружок — основа иконок и индикаторов. */
    public static void disc(GuiGraphics g, int cx, int cy, int radius, int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, colour);
        }
    }

    /**
     * Кольцо: рисуем именно бублик двумя полосами в строке. Нарисовать круг
     * и «стереть» середину нельзя — fill смешивает цвета, а не стирает.
     */
    public static void ring(GuiGraphics g, int cx, int cy, int radius, int width, int colour) {
        int inner = Math.max(0, radius - width);
        for (int dy = -radius; dy <= radius; dy++) {
            int outer = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            if (Math.abs(dy) >= inner) {
                g.fill(cx - outer, cy + dy, cx + outer, cy + dy + 1, colour);
                continue;
            }
            int hole = (int) Math.round(Math.sqrt(Math.max(0, inner * inner - dy * dy)));
            g.fill(cx - outer, cy + dy, cx - hole, cy + dy + 1, colour);
            g.fill(cx + hole, cy + dy, cx + outer, cy + dy + 1, colour);
        }
    }

    /** Верхняя половина кольца: дужка замка, ручка сумки. */
    public static void arcTop(GuiGraphics g, int cx, int cy, int radius, int width, int colour) {
        int inner = Math.max(0, radius - width);
        for (int dy = -radius; dy <= 0; dy++) {
            int outer = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            if (Math.abs(dy) >= inner) {
                g.fill(cx - outer, cy + dy, cx + outer, cy + dy + 1, colour);
                continue;
            }
            int hole = (int) Math.round(Math.sqrt(Math.max(0, inner * inner - dy * dy)));
            g.fill(cx - outer, cy + dy, cx - hole, cy + dy + 1, colour);
            g.fill(cx + hole, cy + dy, cx + outer, cy + dy + 1, colour);
        }
    }

    /** Плавная кривая 0..1 для анимаций открытия приложений. */
    public static float ease(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return 1F - (1F - t) * (1F - t) * (1F - t);
    }
}
