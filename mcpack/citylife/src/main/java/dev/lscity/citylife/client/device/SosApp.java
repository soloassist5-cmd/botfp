package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** 112: полиция, скорая, пожарные. Работает и без SIM-карты, как в жизни. */
class SosApp extends DeviceApp {

    private static final String[] KINDS = {"police", "medic", "fire"};
    private static final int[] COLOURS = {0xFF3F7BFF, 0xFF2FB36A, 0xFFE0413A};

    SosApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("sos");
    }

    private int[] row(int[] area, int index) {
        return new int[]{area[0], area[1] + index * 34, area[2], 30};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        for (int i = 0; i < KINDS.length; i++) {
            int[] r = row(area, i);
            boolean hover = screen.inside(mouseX, mouseY, r);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 8,
                    hover ? COLOURS[i] : PhoneUi.lerp(COLOURS[i], 0xFF000000, 0.35F));
            screen.text(g, Component.translatable("citylife.sos." + KINDS[i]).getString(),
                    r[0] + 12, r[1] + 11, 0xFFFFFFFF);
            screen.text(g, "112", r[0] + r[2] - 12 - screen.font().width("112"), r[1] + 11,
                    0xCCFFFFFF);
        }
        if (screen.sim() == 0 && screen.data().getBoolean("simSlot")) {
            screen.fitted(g, Component.translatable("citylife.sos.no_sim").getString(),
                    area[0] + area[2] / 2, area[1] + 3 * 34 + 6, area[2], screen.theme().dim());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int i = 0; i < KINDS.length; i++) {
            if (screen.inside(mx, my, row(area, i))) {
                CompoundTag args = new CompoundTag();
                args.putString("kind", KINDS[i]);
                screen.send("sos", args);
                return true;
            }
        }
        return false;
    }
}
