package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Кто в сети: игроки сервера, пинг и расстояние до тех, кто рядом. */
class OnlineApp extends PcApp {

    private int scroll;

    OnlineApp(DeviceScreen screen) {
        super(screen, "online");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (noWorld(g, area)) {
            return;
        }
        var connection = mc().getConnection();
        var level = mc().level;
        if (connection == null || level == null) {
            return;
        }
        List<net.minecraft.client.multiplayer.PlayerInfo> list = new ArrayList<>(connection.getOnlinePlayers());
        list.sort(Comparator.comparing(p -> p.getProfile().getName().toLowerCase()));
        screen.text(g, "В сети: " + list.size(), area[0], area[1], Kit.DIM);
        int rows = Math.max(1, (area[3] - 14) / 18);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        for (int i = scroll; i < list.size() && i - scroll < rows; i++) {
            var info = list.get(i);
            int y = area[1] + 14 + (i - scroll) * 18;
            PhoneUi.roundedRect(g, area[0], y, area[2], 16, 4, 0xFF1B1F2B);
            String name = info.getProfile().getName();
            screen.text(g, name, area[0] + 6, y + 4, Kit.TEXT);
            var other = level.getPlayerByUUID(info.getProfile().getId());
            String dist = other == null || mc().player == null ? "далеко"
                    : (int) other.distanceTo(mc().player) + " м";
            String ping = info.getLatency() + " мс";
            screen.text(g, dist, area[0] + area[2] / 2, y + 4, Kit.DIM);
            int colour = info.getLatency() < 80 ? Kit.GREEN : info.getLatency() < 200 ? 0xFFFFC857 : Kit.RED;
            screen.text(g, ping, area[0] + area[2] - 6 - screen.font().width(ping), y + 4, colour);
        }
    }

    @Override
    public boolean scroll(double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }
}
