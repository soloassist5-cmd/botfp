package dev.lscity.citylife.city;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.CityLandmarks;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * После смерти — в больницу.
 *
 * У кого нет своей кровати, тот возрождается у входа в городскую больницу, а
 * не в случайной точке мира; лечение списывается со счёта (нет денег —
 * лечат бесплатно). Вещи лежат в трупе на месте гибели, поэтому навигатор
 * сразу строит туда маршрут. Сидящих в камере это не касается: срок идёт.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Hospital {

    /** Где игрок погиб в последний раз (до возрождения). */
    private static final Map<UUID, BlockPos> DIED_AT = new HashMap<>();

    private Hospital() {
    }

    public static Waypoint entrance() {
        for (Waypoint point : CityLandmarks.ALL) {
            if ("hospital".equals(point.icon())) {
                return point;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().dimension() == Level.OVERWORLD) {
            DIED_AT.put(player.getUUID(), player.blockPosition());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos died = DIED_AT.remove(player.getUUID());
        admit(player, died);
    }

    /** Возрождение: больница, счёт и маршрут к месту гибели. Вернёт точку или null. */
    public static BlockPos admit(ServerPlayer player, BlockPos died) {
        long now = player.level().getGameTime();
        if (!CityConfig.CONFIG.hospitalRespawn.get() || player.getRespawnPosition() != null
                || LifeData.get(player.server).jailUntil(player.getUUID()) > now) {
            return null;
        }
        Waypoint door = entrance();
        if (door == null) {
            return null;
        }
        ServerLevel level = player.server.overworld();
        BlockPos spot = free(level, new BlockPos(door.x(), door.y(), door.z()));
        player.teleportTo(level, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                player.getYRot(), 0F);

        long bill = CityConfig.CONFIG.hospitalBill.get();
        CityData bank = CityData.get(player.server);
        boolean paid = bill > 0 && bank.withdraw(player.getUUID(), bill,
                Texts.ru("citylife.statement.hospital"), now);
        player.sendSystemMessage(Component.translatable(paid ? "citylife.hospital.paid"
                        : "citylife.hospital.free", Money.format(bill))
                .withStyle(ChatFormatting.AQUA));
        if (died != null && died.distSqr(spot) > 16 * 16) {
            Waypoint body = new Waypoint(Texts.ru("citylife.hospital.body"), died.getX(),
                    died.getY(), died.getZ(), "pin", false);
            bank.setRoute(player.getUUID(), body);
            Net.sendRoute(player, body);
            player.sendSystemMessage(Component.translatable("citylife.hospital.route",
                    (int) Math.sqrt(died.distSqr(spot))).withStyle(ChatFormatting.GRAY));
        }
        return spot;
    }

    /** Свободная клетка в два блока высотой у входа (двери, крыльцо — не мешают). */
    private static BlockPos free(ServerLevel level, BlockPos start) {
        for (int dy = 0; dy < 6; dy++) {
            BlockPos at = start.above(dy);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                    && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
                return at;
            }
        }
        return start;
    }
}
