package dev.lscity.citylife.city;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Сетевая игра: чтобы друг через VPN мог ходить, а не стоять на месте.
 *
 * Главный виновник — Distant Horizons. В мире, открытом по сети, он у хозяина
 * работает и как сервер: шлёт другу дальнюю прорисовку города по тому же
 * соединению, что и игра, — по умолчанию до 500 КБ/с и без подстройки под
 * канал. Через Radmin VPN (особенно через его ретранслятор) это забивает всю
 * полосу: пакеты движения стоят в очереди, и игрок «примёрз». Поэтому при
 * старте сервера мы ставим разумный предел и включаем адаптивную скорость —
 * только если в DistantHorizons.toml эти значения не меняли вручную.
 *
 * Команда /net — диагностика для игроков: такт сервера, пинг каждого и что
 * с этим делать.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class NetTuning {

    private static final String DH = "com.seibel.distanthorizons.core.config.Config$Server";

    private NetTuning() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (CityConfig.CONFIG.tuneDistantHorizons.get()) {
            tuneDistantHorizons();
        }
    }

    /** Предел и подстройка скорости для раздачи LOD Distant Horizons. */
    static void tuneDistantHorizons() {
        Class<?> server;
        try {
            server = Class.forName(DH);
        } catch (ClassNotFoundException | LinkageError e) {
            return;   // Distant Horizons нет — и настраивать нечего
        }
        int perPlayer = CityConfig.CONFIG.dhBandwidthPerPlayer.get();
        List<String> changed = new ArrayList<>();
        set(server, "playerBandwidthLimit", perPlayer, changed);
        set(server, "globalBandwidthLimit", perPlayer * 4, changed);
        set(server, "enableAdaptiveTransferSpeed", true, changed);
        // Реже запросы: на холодном старте клиент иначе просит 50 участков в секунду.
        set(server, "syncOnLoadRateLimit", 10, changed);
        set(server, "generationRequestRateLimit", 5, changed);
        if (!changed.isEmpty()) {
            CityLife.LOG.info("City Life: Distant Horizons для сетевой игры — {}", String.join(", ", changed));
        }
    }

    /** Поменять значение, если оно всё ещё заводское (игрок его не трогал). */
    private static void set(Class<?> server, String field, Object value, List<String> changed) {
        try {
            Object entry = server.getField(field).get(null);
            var get = entry.getClass().getMethod("get");
            var def = entry.getClass().getMethod("getDefaultValue");
            Object now = get.invoke(entry);
            if (now == null || !now.equals(def.invoke(entry)) || now.equals(value)) {
                return;
            }
            entry.getClass().getMethod("set", Object.class).invoke(entry, value);
            changed.add(field + " " + now + " → " + value);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            CityLife.LOG.debug("City Life: настройка Distant Horizons {} недоступна: {}", field, e.toString());
        }
    }

    /** Текущий предел Distant Horizons на игрока, КБ/с; -1 — мода нет. */
    private static int dhLimit() {
        try {
            Object entry = Class.forName(DH).getField("playerBandwidthLimit").get(null);
            return (Integer) entry.getClass().getMethod("get").invoke(entry);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return -1;
        }
    }

    // --- /net -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("net").executes(ctx -> report(ctx.getSource())));
    }

    private static int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        float mspt = server.getAverageTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 1.0F));
        source.sendSuccess(() -> Component.translatable("citylife.net.server", String.format("%.1f", tps),
                String.format("%.0f", mspt)).withStyle(mspt > 50 ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        List<String> pings = new ArrayList<>();
        ServerPlayer worst = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            pings.add(p.getGameProfile().getName() + " " + p.latency + " мс");
            if (worst == null || p.latency > worst.latency) {
                worst = p;
            }
        }
        source.sendSuccess(() -> Component.translatable("citylife.net.ping", String.join(", ", pings))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("citylife.net.distance",
                server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance())
                .withStyle(ChatFormatting.GRAY), false);
        int dh = dhLimit();
        if (dh >= 0) {
            source.sendSuccess(() -> Component.translatable("citylife.net.dh", dh == 0
                    ? Component.translatable("citylife.net.dh_unlimited").getString() : dh + " КБ/с")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        // Что с этим делать: сервер не успевает — это железо хозяина, высокий пинг — сеть.
        if (mspt > 50) {
            source.sendSuccess(() -> Component.translatable("citylife.net.slow").withStyle(ChatFormatting.YELLOW),
                    false);
        }
        if (worst != null && worst.latency > 200) {
            String name = worst.getGameProfile().getName();
            source.sendSuccess(() -> Component.translatable("citylife.net.lag", name)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        if (mspt <= 50 && (worst == null || worst.latency <= 200)) {
            source.sendSuccess(() -> Component.translatable("citylife.net.ok").withStyle(ChatFormatting.GREEN),
                    false);
        }
        return 1;
    }
}
