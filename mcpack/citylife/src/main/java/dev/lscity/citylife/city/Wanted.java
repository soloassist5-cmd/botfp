package dev.lscity.citylife.city;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.CityLandmarks;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Розыск и камера.
 *
 * Звёзды дают за преступления против игроков: драка (★), убийство (★★),
 * взлом замка и кража из чужого дома (★). Каждые 5 минут снимается одна
 * звезда. Приехавшая по вызову полиция задерживает разыскиваемых рядом:
 * штраф со счёта и время в камере у полицейского участка.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Wanted {

    /** Сколько тиков держится звезда розыска (5 минут). */
    private static final long DECAY = 20L * 60 * 5;
    /** Радиус «камеры» вокруг точки участка: дальше отойти нельзя. */
    private static final double CELL = 4.0D;
    /** Кого и когда уже отмечали за драку, чтобы не копить звёзды за каждый удар. */
    private static final Map<UUID, Long> FIGHTS = new HashMap<>();

    private Wanted() {
    }

    public static String stars(int count) {
        return "★".repeat(Math.max(0, count)) + "☆".repeat(Math.max(0, 5 - count));
    }

    /** Добавить звёзды за преступление и сказать об этом игроку. */
    public static void crime(ServerPlayer player, int stars, String reasonKey) {
        LifeData life = LifeData.get(player.server);
        int now = Math.min(5, life.wanted(player.getUUID()) + stars);
        life.setWanted(player.getUUID(), now, player.level().getGameTime() + DECAY);
        player.displayClientMessage(Component.translatable("citylife.wanted.gained",
                Component.translatable(reasonKey), stars(now)).withStyle(ChatFormatting.RED), false);
    }

    /** Точка участка полиции: вход в здание из плана города. */
    public static Waypoint station() {
        for (Waypoint point : CityLandmarks.ALL) {
            if ("police".equals(point.icon())) {
                return point;
            }
        }
        return CityLandmarks.ALL.isEmpty() ? new Waypoint("Полиция", 0, 70, 0, "police", true)
                : CityLandmarks.ALL.get(0);
    }

    /** Задержание: штраф, камера, розыск снят. */
    public static void arrest(ServerPlayer player, boolean surrendered) {
        LifeData life = LifeData.get(player.server);
        int stars = Math.max(1, life.wanted(player.getUUID()));
        long fine = (long) stars * CityConfig.CONFIG.finePerStar.get();
        long seconds = (long) stars * CityConfig.CONFIG.jailSecondsPerStar.get();
        if (surrendered) {
            fine /= 2;
            seconds /= 2;
        }
        CityData bank = CityData.get(player.server);
        long paid = Math.min(fine, bank.balance(player.getUUID()));
        bank.withdraw(player.getUUID(), paid);
        life.setWanted(player.getUUID(), 0, 0);
        if (player.isPassenger()) {
            player.stopRiding();
        }
        Waypoint cell = station();
        player.teleportTo(player.server.overworld(), cell.x() + 0.5D, cell.y(), cell.z() + 0.5D,
                player.getYRot(), player.getXRot());
        life.setJail(player.getUUID(), player.level().getGameTime() + seconds * 20L);
        player.sendSystemMessage(Component.translatable("citylife.wanted.arrested",
                Money.format(paid), seconds).withStyle(ChatFormatting.GOLD));
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                "citylife.wanted.arrest_news", player.getGameProfile().getName())
                .withStyle(ChatFormatting.BLUE), false);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim
                && event.getSource().getEntity() instanceof ServerPlayer killer
                && killer != victim) {
            crime(killer, 2, "citylife.wanted.murder");
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker == victim) {
            return;
        }
        long now = attacker.level().getGameTime();
        Long last = FIGHTS.get(attacker.getUUID());
        if (last == null || now - last > 1200) {
            FIGHTS.put(attacker.getUUID(), now);
            if (LifeData.get(attacker.server).wanted(attacker.getUUID()) == 0) {
                crime(attacker, 1, "citylife.wanted.assault");
            }
        }
    }

    /** Раз в секунду: звёзды тают, заключённые сидят в камере. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        LifeData life = LifeData.get(player.server);
        long now = player.level().getGameTime();
        UUID id = player.getUUID();
        int stars = life.wanted(id);
        if (stars > 0 && now >= life.wantedDecay(id)) {
            life.setWanted(id, stars - 1, now + DECAY);
            player.displayClientMessage(Component.translatable("citylife.wanted.decay",
                    stars(stars - 1)).withStyle(ChatFormatting.GRAY), true);
        }
        long until = life.jailUntil(id);
        if (until <= 0) {
            return;
        }
        if (now >= until) {
            life.setJail(id, 0);
            player.sendSystemMessage(Component.translatable("citylife.wanted.released")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        Waypoint cell = station();
        if (player.level() != player.server.overworld()
                || player.distanceToSqr(cell.x() + 0.5D, cell.y(), cell.z() + 0.5D) > CELL * CELL) {
            if (player.isPassenger()) {
                player.stopRiding();
            }
            player.teleportTo(player.server.overworld(), cell.x() + 0.5D, cell.y(),
                    cell.z() + 0.5D, player.getYRot(), player.getXRot());
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false));
        player.displayClientMessage(Component.translatable("citylife.wanted.jail",
                (until - now) / 20).withStyle(ChatFormatting.GOLD), true);
    }
}
