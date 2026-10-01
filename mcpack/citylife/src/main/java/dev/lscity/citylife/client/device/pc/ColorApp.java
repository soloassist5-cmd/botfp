package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Палитра: ползунки R, G, B, образец цвета, коды HEX и RGB; клик по коду — копия. */
class ColorApp extends PcApp {

    private final int[] rgb = {69, 208, 240};
    private int dragging = -1;

    ColorApp(DeviceScreen screen) {
        super(screen, "color");
    }

    private int[] slider(int[] area, int i) {
        return new int[]{area[0] + 14, area[1] + 4 + i * 22, area[2] / 2 - 20, 14};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        String[] names = {"R", "G", "B"};
        int[] colours = {0xFFFF5050, 0xFF50FF50, 0xFF5080FF};
        for (int i = 0; i < 3; i++) {
            int[] s = slider(area, i);
            screen.text(g, names[i], area[0], s[1] + 3, colours[i]);
            PhoneUi.roundedRect(g, s[0], s[1] + 5, s[2], 4, 2, 0xFF101218);
            int x = s[0] + rgb[i] * s[2] / 255;
            PhoneUi.disc(g, x, s[1] + 7, 5, colours[i]);
            screen.text(g, String.valueOf(rgb[i]), s[0] + s[2] + 6, s[1] + 3, Kit.TEXT);
        }
        int colour = 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
        int sx = area[0] + area[2] / 2 + 14;
        PhoneUi.roundedRect(g, sx, area[1], area[2] / 2 - 14, 66, 8, colour);
        String hex = String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
        screen.text(g, "HEX: " + hex, area[0], area[1] + 76, Kit.TEXT);
        screen.text(g, "RGB: " + rgb[0] + ", " + rgb[1] + ", " + rgb[2], area[0], area[1] + 88, Kit.TEXT);
        float[] hsb = java.awt.Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], null);
        screen.text(g, String.format("HSB: %d°, %d%%, %d%%", Math.round(hsb[0] * 360), Math.round(hsb[1] * 100),
                Math.round(hsb[2] * 100)), area[0], area[1] + 100, Kit.TEXT);
        screen.text(g, "Клик по коду HEX — скопировать", area[0], area[1] + 116, Kit.DIM);
    }

    private void set(double mx, int[] area, int i) {
        int[] s = slider(area, i);
        rgb[i] = (int) Math.max(0, Math.min(255, (mx - s[0]) * 255 / s[2]));
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int i = 0; i < 3; i++) {
            if (Kit.inside(mx, my, slider(area, i))) {
                dragging = i;
                set(mx, area, i);
                return true;
            }
        }
        if (Kit.inside(mx, my, new int[]{area[0], area[1] + 74, area[2] / 2, 12})) {
            mc().keyboardHandler.setClipboard(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
            return true;
        }
        return false;
    }

    @Override
    public boolean drag(double mx, double my, double dy, int[] area) {
        if (dragging >= 0) {
            set(mx, area, dragging);
            return true;
        }
        return false;
    }

    @Override
    public boolean release(double mx, double my, int[] area) {
        dragging = -1;
        return false;
    }
}
