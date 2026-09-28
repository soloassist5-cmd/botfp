package dev.lscity.citylife.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Обои рабочего стола.
 *
 * Четыре картинки в стиле сайта сборки: закатное солнце как в шапке, луна
 * с кратерами, Марс и Юпитер. Рисуются кодом, поэтому тянутся под любой
 * размер экрана телефона и не весят ни байта в ресурсах.
 */
@OnlyIn(Dist.CLIENT)
public final class Wallpapers {

    public static final List<String> ALL = List.of("sunset", "moon", "mars", "jupiter");

    private Wallpapers() {
    }

    public static String normalize(String id) {
        return ALL.contains(id) ? id : "sunset";
    }

    /** Полноэкранные обои со скруглением под корпус телефона. */
    public static void draw(GuiGraphics g, String id, int x, int y, int w, int h, int radius) {
        switch (normalize(id)) {
            case "moon" -> moon(g, x, y, w, h, radius);
            case "mars" -> mars(g, x, y, w, h, radius);
            case "jupiter" -> jupiter(g, x, y, w, h, radius);
            default -> sunset(g, x, y, w, h, radius);
        }
    }

    /** Закат с сайта: золото сверху, малиновый к горизонту, город внизу. */
    private static void sunset(GuiGraphics g, int x, int y, int w, int h, int radius) {
        PhoneUi.roundedGradient(g, x, y, w, h, radius, 0xFF241035, 0xFF6A1F3A);
        int sunX = x + w / 2;
        int sunY = y + h / 2 - h / 12;
        int sunR = Math.max(16, w / 3);
        halo(g, sunX, sunY, sunR + 10, 0x33FF7A45);
        // Диск солнца: сверху золотой, снизу уходит в розовый — как на странице.
        for (int dy = -sunR; dy <= sunR; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, sunR * sunR - dy * dy)));
            int colour = PhoneUi.lerp(0xFFFFC44D, 0xFFFF4D7D, (dy + sunR) / (2F * sunR));
            g.fill(sunX - dx, sunY + dy, sunX + dx, sunY + dy + 1, colour);
        }
        skyline(g, x, y, w, h, 0x99140A1E);
    }

    /** Ночь и луна: холодный градиент, кратеры, звёзды. */
    private static void moon(GuiGraphics g, int x, int y, int w, int h, int radius) {
        PhoneUi.roundedGradient(g, x, y, w, h, radius, 0xFF070C1C, 0xFF16284A);
        stars(g, x, y, w, h, 40);
        int cx = x + w / 2;
        int cy = y + h / 2 - h / 10;
        int r = Math.max(14, w / 3);
        halo(g, cx, cy, r + 12, 0x2288AEDC);
        PhoneUi.disc(g, cx, cy, r, 0xFFD9DEE8);
        PhoneUi.disc(g, cx + r / 3, cy - r / 4, r * 2 / 3, 0xFFEFF2F7);
        // Кратеры: тёмное пятно с подсвеченным краем.
        int[][] craters = {{-r / 3, -r / 4, r / 5}, {r / 4, r / 3, r / 7},
                           {-r / 5, r / 2 - 2, r / 9}, {r / 2 - 2, -r / 3, r / 8}};
        for (int[] c : craters) {
            PhoneUi.disc(g, cx + c[0], cy + c[1], c[2], 0xFFB9C0CE);
            PhoneUi.disc(g, cx + c[0], cy + c[1] - 1, Math.max(1, c[2] - 1), 0xFFC9D0DC);
        }
        skyline(g, x, y, w, h, 0x99050914);
    }

    /** Марс: ржавые оттенки, полярная шапка и тёмные моря. */
    private static void mars(GuiGraphics g, int x, int y, int w, int h, int radius) {
        PhoneUi.roundedGradient(g, x, y, w, h, radius, 0xFF200B0B, 0xFF5A2418);
        stars(g, x, y, w, h, 22);
        int cx = x + w / 2;
        int cy = y + h / 2 - h / 12;
        int r = Math.max(15, w / 3);
        halo(g, cx, cy, r + 9, 0x33D86A3A);
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            int colour = PhoneUi.lerp(0xFFE07A46, 0xFF8E3A22, (dy + r) / (2F * r));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, colour);
        }
        PhoneUi.disc(g, cx - r / 3, cy + r / 5, r / 3, 0x66702A18);
        PhoneUi.disc(g, cx + r / 4, cy - r / 6, r / 4, 0x55702A18);
        // Полярная шапка.
        for (int dy = -r; dy <= -r + r / 4; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, 0xCCF0E6E0);
        }
        skyline(g, x, y, w, h, 0x99180806);
    }

    /** Юпитер: полосы, Большое красное пятно, тёплый фон. */
    private static void jupiter(GuiGraphics g, int x, int y, int w, int h, int radius) {
        PhoneUi.roundedGradient(g, x, y, w, h, radius, 0xFF1A1206, 0xFF4E3416);
        stars(g, x, y, w, h, 18);
        int cx = x + w / 2;
        int cy = y + h / 2 - h / 12;
        int r = Math.max(16, w / 3);
        halo(g, cx, cy, r + 10, 0x33E0B070);
        int[] bands = {0xFFE8D6B0, 0xFFC79A66, 0xFFEBDCC0, 0xFFA97846,
                       0xFFE3CDA4, 0xFFB98A54, 0xFFEFE2C8};
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            int band = bands[Math.min(bands.length - 1,
                    (int) ((dy + r) / (2F * r) * bands.length))];
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, band);
        }
        // Большое красное пятно.
        int spotX = cx + r / 3;
        int spotY = cy + r / 4;
        for (int dy = -r / 7; dy <= r / 7; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, (r / 4F) * (r / 4F) - dy * dy)) * 1.4);
            g.fill(spotX - dx, spotY + dy, spotX + dx, spotY + dy + 1, 0xFFC4593A);
        }
        skyline(g, x, y, w, h, 0x99140D05);
    }

    // --- общие детали -------------------------------------------------------

    private static void halo(GuiGraphics g, int cx, int cy, int r, int colour) {
        for (int i = 3; i >= 1; i--) {
            PhoneUi.disc(g, cx, cy, r + i * 3, PhoneUi.alpha(colour, 0.35F * i / 3F));
        }
    }

    private static void stars(GuiGraphics g, int x, int y, int w, int h, int count) {
        int seed = 1337;
        for (int i = 0; i < count; i++) {
            seed = seed * 1103515245 + 12345;
            int sx = x + Math.abs(seed >> 8) % Math.max(1, w);
            seed = seed * 1103515245 + 12345;
            int sy = y + Math.abs(seed >> 8) % Math.max(1, h * 2 / 3);
            int bright = (seed >> 16 & 1) == 0 ? 0x88FFFFFF : 0x55FFFFFF;
            g.fill(sx, sy, sx + 1, sy + 1, bright);
        }
    }

    /** Силуэт города по низу — общий мотив для всех обоев. */
    private static void skyline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        int base = y + h - 18;
        int seed = 24;
        for (int px = x; px < x + w; px += 9) {
            seed = seed * 1103515245 + 12345;
            int height = 12 + Math.abs(seed >> 16) % 28;
            int width = Math.min(8, x + w - px);
            PhoneUi.roundedRect(g, px, base - height, width, height + 18, 1, colour);
            for (int wy = base - height + 3; wy < base; wy += 5) {
                seed = seed * 1103515245 + 12345;
                if ((seed >> 20 & 3) == 0) {
                    g.fill(px + 2, wy, px + 4, wy + 2, 0x44FFD98A);
                }
            }
        }
    }

    /** Мини-превью для экрана выбора. */
    public static void thumbnail(GuiGraphics g, String id, int x, int y, int w, int h,
                                 boolean active) {
        draw(g, id, x, y, w, h, 6);
        PhoneUi.roundedOutline(g, x, y, w, h, 6, active ? 0xFF45D0F0 : 0x33FFFFFF);
        if (active) {
            PhoneUi.disc(g, x + w - 7, y + 7, 4, 0xFF45D0F0);
        }
    }
}
