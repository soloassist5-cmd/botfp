package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/** Общие мелочи программ компьютера: плитки, перенос текста, числа. */
@OnlyIn(Dist.CLIENT)
final class Kit {

    static final int TEXT = 0xFFEDEFF7;
    static final int DIM = 0xFF9AA0B4;
    static final int ACCENT = 0xFF45D0F0;
    static final int GREEN = 0xFF7BE07B;
    static final int RED = 0xFFFF6B6B;
    static final int TILE = 0xFF2A2F3F;

    private Kit() {
    }

    static boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    /** Ячейка сетки cols x rows в прямоугольнике (x, y, w, h). */
    static int[] cell(int x, int y, int w, int h, int cols, int rows, int index, int gap) {
        int cw = (w - gap * (cols - 1)) / cols;
        int ch = (h - gap * (rows - 1)) / rows;
        return new int[]{x + (index % cols) * (cw + gap), y + (index / cols) * (ch + gap), cw, ch};
    }

    /** Плитка с подписью по центру; подсвечивается под мышью. */
    static void tile(GuiGraphics g, DeviceScreen screen, int[] r, String label, int colour,
                     int mouseX, int mouseY) {
        int fill = inside(mouseX, mouseY, r) ? PhoneUi.lerp(colour, 0xFFFFFFFF, 0.15F) : colour;
        PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 5, fill);
        screen.fitted(g, label, r[0] + r[2] / 2, r[1] + (r[3] - 8) / 2, r[2] - 4, 0xFFFFFFFF);
    }

    /** Текст с переносом по словам; возвращает y под последней строкой. */
    static int wrap(GuiGraphics g, DeviceScreen screen, String text, int x, int y, int w,
                    int colour, int bottom) {
        for (var line : screen.font().split(Component.literal(text), w)) {
            if (y + 9 > bottom) {
                break;
            }
            g.drawString(screen.font(), line, x, y, colour, false);
            y += 10;
        }
        return y;
    }

    /** Число по-русски: запятая, без лишних нулей, разряды через пробел. */
    static String num(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "—";
        }
        double abs = Math.abs(value);
        String out;
        if (abs != 0 && (abs < 1e-4 || abs >= 1e12)) {
            out = String.format(Locale.ROOT, "%.4e", value);
        } else if (abs >= 1000) {
            out = String.format(Locale.ROOT, "%,.2f", value).replace(',', ' ');
        } else {
            out = String.format(Locale.ROOT, "%.6f", value);
        }
        if (out.contains(".") && !out.contains("e")) {
            out = out.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return out.replace('.', ',');
    }

    /** Разобрать число из поля: запятая или точка, пробелы игнорируются. */
    static double parse(String text) {
        try {
            return Double.parseDouble(text.replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException bad) {
            return Double.NaN;
        }
    }

    static String rub(double value) {
        return num(Math.round(value * 100) / 100.0) + " ₽";
    }
}
