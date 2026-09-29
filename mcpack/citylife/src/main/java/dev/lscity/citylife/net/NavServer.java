package dev.lscity.citylife.net;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Прибытие к цели навигатора.
 *
 * Метка стоит на своей высоте, и маршрут снимается, только когда игрок
 * вошёл в неё: рядом по горизонтали и на том же уровне. Так навигатор не
 * выключается, если цель на крыше, а ты под ней на улице, или на станции
 * метро под землёй.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class NavServer {

    /** Насколько можно промахнуться по высоте: ступенька, плита, полблока. */
    private static final double HEIGHT_SLACK = 2.2;

    private NavServer() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 5 != 0) {
            return;
        }
        CityData data = CityData.get(player.server);
        Waypoint route = data.route(player.getUUID());
        if (route == null) {
            return;
        }
        double dx = route.x() + 0.5 - player.getX();
        double dz = route.z() + 0.5 - player.getZ();
        double dy = player.getY() - route.y();
        double radius = CityConfig.CONFIG.arrivalRadius.get();
        if (dx * dx + dz * dz > radius * radius || dy < -HEIGHT_SLACK || dy > HEIGHT_SLACK) {
            return;
        }
        data.setRoute(player.getUUID(), null);
        Net.sendRoute(player, null);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.5F, 1.6F);
        player.displayClientMessage(Component.translatable("citylife.nav.arrived", route.name())
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Маршрут переживает перезаход: при входе в мир отдаём его клиенту. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Net.sendRoute(player, CityData.get(player.server).route(player.getUUID()));
        }
    }
}
