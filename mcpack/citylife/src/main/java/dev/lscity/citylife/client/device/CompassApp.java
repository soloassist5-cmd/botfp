package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.NavClient;
import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.data.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Компас: стороны света поворачиваются вместе с игроком, а если включён
 * маршрут — отдельная стрелка показывает на цель. Работает без сети.
 */
class CompassApp extends DeviceApp {

    CompassApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("compass");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int cx = area[0] + area[2] / 2;
        int cy = area[1] + Math.min(area[3], area[2]) / 2;
        int r = Math.min(area[2], area[3] - 30) / 2 - 6;
        PhoneUi.disc(g, cx, cy, r, 0xFF141821);
        PhoneUi.ring(g, cx, cy, r, 2, 0xFF3A4055);
        // Взгляд игрока всегда вверх: поворачиваем картушку на его угол.
        double yaw = Math.toRadians(player.getYRot());
        String[] sides = {"С", "В", "Ю", "З"};
        for (int i = 0; i < 4; i++) {
            // Север в мире — это -Z; при yaw = 180 игрок смотрит на север.
            double angle = Math.PI - yaw + i * Math.PI / 2;
            int sx = cx + (int) Math.round(Math.sin(angle) * (r - 12));
            int sy = cy - (int) Math.round(Math.cos(angle) * (r - 12));
            screen.text(g, sides[i], sx - screen.font().width(sides[i]) / 2, sy - 4,
                    i == 0 ? 0xFFE0413A : t.text());
        }
        Waypoint target = NavClient.target();
        if (target != null) {
            double dx = target.x() + 0.5 - player.getX();
            double dz = target.z() + 0.5 - player.getZ();
            double bearing = Math.atan2(dx, -dz);
            double angle = bearing - (yaw + Math.PI);
            for (int step = 0; step < r - 18; step++) {
                int px = cx + (int) Math.round(Math.sin(angle) * step);
                int py = cy - (int) Math.round(Math.cos(angle) * step);
                g.fill(px - 1, py - 1, px + 2, py + 2, t.accent());
            }
            PhoneUi.disc(g, cx + (int) Math.round(Math.sin(angle) * (r - 18)),
                    cy - (int) Math.round(Math.cos(angle) * (r - 18)), 4, t.accent());
            screen.fitted(g, target.name() + " · " + (int) Math.sqrt(dx * dx + dz * dz) + " м",
                    cx, cy + r + 6, area[2], t.accent());
        } else {
            screen.fitted(g, Component.translatable("citylife.compass.no_route").getString(),
                    cx, cy + r + 6, area[2], t.dim());
        }
        PhoneUi.disc(g, cx, cy, 3, t.text());
        screen.fitted(g, String.format("X %d  Y %d  Z %d", player.getBlockX(), player.getBlockY(),
                player.getBlockZ()), cx, cy + r + 18, area[2], t.dim());
    }
}
