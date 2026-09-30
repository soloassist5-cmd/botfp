package dev.lscity.citylife.client;

import dev.lscity.citylife.CityLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Строка состояния в правом верхнем углу: звёзды розыска, срок в камере
 * и текущее задание «Работы». Сервер присылает её, только когда что-то
 * изменилось; пустое состояние ничего не рисует.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class HudOverlay {

    private static CompoundTag state = new CompoundTag();

    private HudOverlay() {
    }

    public static void update(CompoundTag fresh) {
        state = fresh;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || state.isEmpty()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int right = mc.getWindow().getGuiScaledWidth() - 6;
        int y = 6;
        int wanted = state.getInt("wanted");
        if (wanted > 0) {
            String stars = "★".repeat(wanted) + "☆".repeat(Math.max(0, 5 - wanted));
            // Мигает, пока звёзды свежие: видно краем глаза.
            boolean blink = (mc.level.getGameTime() / 10) % 2 == 0;
            int colour = blink ? 0xFFFFD23F : 0xFFFF6B3F;
            String label = Component.translatable("citylife.hud.wanted").getString();
            int w = font.width(stars);
            g.fill(right - w - font.width(label) - 10, y - 3, right + 3, y + 10, 0x90000000);
            g.drawString(font, label, right - w - font.width(label) - 4, y, 0xFFE8E8F0, false);
            g.drawString(font, stars, right - w, y, colour, false);
            y += 15;
        }
        int jail = state.getInt("jail");
        if (jail > 0) {
            String line = Component.translatable("citylife.hud.jail",
                    jail / 60 + ":" + String.format("%02d", jail % 60)).getString();
            int w = font.width(line);
            g.fill(right - w - 4, y - 3, right + 3, y + 10, 0x90000000);
            g.drawString(font, line, right - w, y, 0xFFFFB84D, false);
            y += 15;
        }
        String duty = state.getString("duty");
        if (!duty.isEmpty()) {
            int w = font.width(duty);
            g.fill(right - w - 4, y - 3, right + 3, y + 10, 0x90000000);
            g.drawString(font, duty, right - w, y, 0xFF6FB7FF, false);
            y += 15;
        }
        String job = state.getString("job");
        if (!job.isEmpty()) {
            int w = font.width(job);
            g.fill(right - w - 4, y - 3, right + 3, y + 10, 0x90000000);
            g.drawString(font, job, right - w, y, 0xFF7BE07B, false);
        }
    }
}
