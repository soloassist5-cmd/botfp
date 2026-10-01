package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;

/** Координаты: где ты, куда смотришь, какой биом, высота над морем. */
class GpsApp extends PcApp {

    GpsApp(DeviceScreen screen) {
        super(screen, "gps");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (noWorld(g, area)) {
            return;
        }
        var player = mc().player;
        var level = mc().level;
        if (player == null || level == null) {
            return;
        }
        String[] dirs = {"юг", "юго-запад", "запад", "северо-запад", "север", "северо-восток", "восток",
                "юго-восток"};
        int dir = Math.floorMod(Math.round(player.getYRot() / 45F), 8);
        var biome = level.getBiome(player.blockPosition()).unwrapKey()
                .map(k -> k.location().getPath().replace('_', ' ')).orElse("?");
        int cx = area[0] + area[2] / 2;
        big(g, player.getBlockX() + "  " + player.getBlockY() + "  " + player.getBlockZ(), cx, area[1] + 8,
                2F, Kit.TEXT);
        screen.fitted(g, "X · Y · Z", cx, area[1] + 30, area[2], Kit.DIM);
        String[] lines = {
                "Смотрите на: " + dirs[dir],
                "Высота над морем: " + (player.getBlockY() - 62) + " м",
                "Биом: " + biome,
                "Чанк: " + (player.getBlockX() >> 4) + ", " + (player.getBlockZ() >> 4),
                "Регион файла: r." + (player.getBlockX() >> 9) + "." + (player.getBlockZ() >> 9) + ".mca",
                "До центра города: " + (int) Math.sqrt(player.getX() * player.getX()
                        + player.getZ() * player.getZ()) + " м",
        };
        int y = area[1] + 48;
        for (String line : lines) {
            screen.text(g, line, area[0] + 6, y, Kit.TEXT);
            y += 13;
        }
    }
}
