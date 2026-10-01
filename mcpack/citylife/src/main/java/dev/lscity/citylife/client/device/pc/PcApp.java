package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceApp;
import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Программа компьютера: заголовок из реестра и доступ к миру клиента. */
@OnlyIn(Dist.CLIENT)
abstract class PcApp extends DeviceApp {

    protected final String id;

    PcApp(DeviceScreen screen, String id) {
        super(screen);
        this.id = id;
    }

    @Override
    public String title() {
        return screen.appTitle(id);
    }

    protected static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Игровое время суток «чч:мм» по снимку устройства. */
    protected String clock() {
        long time = screen.data().getLong("daytime");
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    /** Номер игрового дня, начиная с первого. */
    protected long day() {
        return mc().level == null ? 1 : mc().level.getDayTime() / 24000L + 1;
    }

    /** Нет мира (например, окно открыто не в игре): сказать об этом и ничего не рисовать. */
    protected boolean noWorld(net.minecraft.client.gui.GuiGraphics g, int[] area) {
        if (mc().player != null && mc().level != null) {
            return false;
        }
        screen.fitted(g, "Нет данных: программа работает в игре", area[0] + area[2] / 2,
                area[1] + area[3] / 2 - 4, area[2], Kit.DIM);
        return true;
    }

    /** Крупный текст по центру. */
    protected void big(net.minecraft.client.gui.GuiGraphics g, String text, int cx, int y, float scale,
                       int colour) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(screen.font(), text, -screen.font().width(text) / 2, 0, colour, false);
        g.pose().popPose();
    }
}
