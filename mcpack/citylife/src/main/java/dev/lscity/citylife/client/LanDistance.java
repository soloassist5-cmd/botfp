package dev.lscity.citylife.client;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Мир, открытый для друга (LAN, Radmin VPN, Hamachi): сервер работает внутри
 * игры хозяина и берёт дальность прорисовки из его настроек. При 12 чанках
 * другу при каждом шаге уходит по сети целая полоса плотного города —
 * через VPN это не успевает, и друг стоит на месте. Пока подключён кто-то
 * ещё, снижаем дальность хозяина (а с ней и сервера) до lanViewDistance;
 * город вдали дорисует Distant Horizons. Все ушли — возвращаем как было.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class LanDistance {

    /** Дальность хозяина до снижения; -1 — сейчас не снижали. */
    private static int savedView = -1;
    private static int savedSim = -1;

    private LanDistance() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            restore(mc, false);
            return;
        }
        if (mc.level == null || mc.level.getGameTime() % 20 != 0) {
            return;
        }
        boolean guests = server.isPublished() && server.getPlayerCount() > 1;
        if (guests && savedView < 0) {
            lower(mc);
        } else if (!guests && savedView >= 0) {
            restore(mc, true);
        }
    }

    private static void lower(Minecraft mc) {
        int view = CityConfig.CONFIG.lanViewDistance.get();
        int sim = CityConfig.CONFIG.lanSimulationDistance.get();
        var options = mc.options;
        int curView = options.renderDistance().get();
        int curSim = options.simulationDistance().get();
        boolean lowerView = view > 0 && curView > view;
        boolean lowerSim = sim > 0 && curSim > sim;
        if (!lowerView && !lowerSim) {
            savedView = curView;
            savedSim = curSim;
            return;
        }
        savedView = curView;
        savedSim = curSim;
        if (lowerView) {
            options.renderDistance().set(view);
        }
        if (lowerSim) {
            options.simulationDistance().set(sim);
        }
        CityLife.LOG.info("City Life: к миру подключились по сети — дальность {} → {}, симуляция {} → {}",
                curView, options.renderDistance().get(), curSim, options.simulationDistance().get());
        mc.player.displayClientMessage(Component.translatable("citylife.net.lowered",
                options.renderDistance().get(), curView).withStyle(ChatFormatting.AQUA), false);
    }

    private static void restore(Minecraft mc, boolean tell) {
        if (savedView < 0) {
            return;
        }
        var options = mc.options;
        // Вернуть, только если игрок сам не менял дальность за это время.
        int view = CityConfig.CONFIG.lanViewDistance.get();
        int sim = CityConfig.CONFIG.lanSimulationDistance.get();
        if (options.renderDistance().get() == Math.min(view, savedView) && savedView > view) {
            options.renderDistance().set(savedView);
        }
        if (options.simulationDistance().get() == Math.min(sim, savedSim) && savedSim > sim) {
            options.simulationDistance().set(savedSim);
        }
        options.save();
        if (tell && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("citylife.net.restored",
                    options.renderDistance().get()).withStyle(ChatFormatting.GRAY), false);
        }
        savedView = -1;
        savedSim = -1;
    }
}
