package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Заглушка «Нет сети» вместо сетевого приложения.
 *
 * Говорит, что делать: вставить SIM-карту. Если карта есть в инвентаре,
 * кнопка вставит её сама.
 */
class OfflineApp extends DeviceApp {

    private final String title;

    OfflineApp(DeviceScreen screen, String title) {
        super(screen);
        this.title = title;
    }

    @Override
    public String title() {
        return title;
    }

    private int[] buttonRect(int[] area) {
        String label = Component.translatable("citylife.sim.insert").getString();
        int w = screen.buttonWidth(label);
        return new int[]{area[0] + (area[2] - w) / 2, area[1] + area[3] / 2 + 14, w, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        int cx = area[0] + area[2] / 2;
        int y = area[1] + area[3] / 2 - 30;
        screen.fitted(g, Component.translatable("citylife.device.no_network").getString(),
                cx, y, area[2], t.red());
        screen.fitted(g, Component.translatable("citylife.device.no_network_hint").getString(),
                cx, y + 14, area[2], t.dim());
        if (screen.data().getBoolean("simSlot")) {
            int[] r = buttonRect(area);
            screen.button(g, r[0], r[1], r[2],
                    Component.translatable("citylife.sim.insert").getString(), t.button(),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (screen.data().getBoolean("simSlot") && screen.inside(mx, my, buttonRect(area))) {
            screen.send("sim_insert");
            return true;
        }
        return false;
    }
}
