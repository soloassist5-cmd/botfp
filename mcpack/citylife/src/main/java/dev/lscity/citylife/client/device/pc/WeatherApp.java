package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Погода над городом: ясно, дождь или гроза, температура по биому и времени суток. */
class WeatherApp extends PcApp {

    WeatherApp(DeviceScreen screen) {
        super(screen, "weather");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (noWorld(g, area)) {
            return;
        }
        var level = mc().level;
        var player = mc().player;
        if (level == null || player == null) {
            return;
        }
        boolean thunder = level.isThundering();
        boolean rain = level.isRaining();
        long time = screen.data().getLong("daytime") % 24000;
        boolean night = time > 13000 && time < 23000;
        float base = level.getBiome(player.blockPosition()).value().getBaseTemperature();
        int celsius = Math.round(base * 25 + (night ? -6 : 4) - (rain ? 5 : 0));
        String state = thunder ? "Гроза" : rain ? "Дождь" : night ? "Ясная ночь" : "Солнечно";
        int top = thunder ? 0xFF3A3F50 : rain ? 0xFF5B6B8C : night ? 0xFF1C1F3A : 0xFF5CC8FF;
        int bottom = thunder ? 0xFF15171F : rain ? 0xFF263049 : night ? 0xFF0B0D14 : 0xFF2A7FD0;
        PhoneUi.roundedGradient(g, area[0], area[1], area[2], area[3] / 2 + 10, 8, top, bottom);
        int cx = area[0] + area[2] / 2;
        big(g, celsius + "°", cx, area[1] + 14, 4F, 0xFFFFFFFF);
        screen.fitted(g, state + " · " + clock(), cx, area[1] + 52, area[2] - 10, 0xFFEDEFF7);
        String[] lines = {
                "Ветер: " + (thunder ? "сильный, 14 м/с" : rain ? "умеренный, 7 м/с" : "слабый, 3 м/с"),
                "Влажность: " + (rain ? "92%" : night ? "68%" : "45%"),
                "Совет: " + (thunder ? "переждите грозу дома" : rain ? "возьмите зонт, на дорогах скользко"
                        : night ? "на улицах темно — включите фары" : "отличный день для пляжа"),
        };
        int y = area[1] + area[3] / 2 + 20;
        for (String line : lines) {
            screen.text(g, screen.trim(line, area[2]), area[0] + 4, y, Kit.TEXT);
            y += 12;
        }
    }
}
