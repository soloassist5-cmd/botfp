package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Фонарик: минута ночного зрения. Есть только у защищённого «Полюса» и
 * флагмана — это одна из причин их купить.
 */
class FlashlightApp extends DeviceApp {

    FlashlightApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("flashlight");
    }

    private int radius(int[] area) {
        return Math.min(area[2], area[3]) / 4;
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int cx = area[0] + area[2] / 2;
        int cy = area[1] + area[3] / 2 - 8;
        int r = radius(area);
        boolean hover = (mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy) <= r * r;
        for (int i = 4; i >= 1; i--) {
            PhoneUi.disc(g, cx, cy, r + i * 4, PhoneUi.alpha(0xFFFFE066, 0.06F * i));
        }
        PhoneUi.disc(g, cx, cy, r, hover ? 0xFFFFE680 : 0xFFE8C94A);
        PhoneUi.disc(g, cx, cy, r - 6, 0xFFFFF4C0);
        screen.fitted(g, Component.translatable("citylife.flashlight.hint").getString(),
                cx, cy + r + 14, area[2], screen.theme().dim());
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        int cx = area[0] + area[2] / 2;
        int cy = area[1] + area[3] / 2 - 8;
        int r = radius(area);
        if ((mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= r * r) {
            screen.send("flashlight");
            return true;
        }
        return false;
    }
}
