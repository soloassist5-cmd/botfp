package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/** «О системе»: из чего собран компьютер, потребление и итоговая производительность. */
class SysInfoApp extends DeviceApp {

    SysInfoApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("sysinfo");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        CompoundTag info = screen.data().getCompound("sysinfo");
        int score = info.getInt("score");
        PhoneUi.roundedGradient(g, area[0], area[1], area[2], 34, 7, 0xFF2FD1A0, 0xFF16806A);
        screen.text(g, Component.translatable("citylife.pc.score_total").getString(),
                area[0] + 8, area[1] + 6, 0xDDFFFFFF);
        screen.text(g, score + " · " + Component.translatable(tier(score)).getString(),
                area[0] + 8, area[1] + 19, 0xFFFFFFFF);
        screen.text(g, Component.translatable("citylife.pc.power", info.getInt("draw"),
                info.getInt("supply")).getString(), area[0], area[1] + 42, t.dim());
        ListTag parts = info.getList("parts", 8);
        int y = area[1] + 58;
        for (int i = 0; i < parts.size(); i++) {
            screen.text(g, "• " + screen.trim(parts.getString(i), area[2] - 10), area[0], y,
                    t.text());
            y += 11;
        }
    }

    private static String tier(int score) {
        if (score >= 200) {
            return "citylife.pc.tier.top";
        }
        if (score >= 120) {
            return "citylife.pc.tier.gaming";
        }
        if (score >= 60) {
            return "citylife.pc.tier.home";
        }
        return "citylife.pc.tier.office";
    }
}
