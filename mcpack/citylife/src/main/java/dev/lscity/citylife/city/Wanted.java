package dev.lscity.citylife.city;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.CityLandmarks;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 *
 * Команда /wanted: показать свой розыск, откупиться штрафом (вдвое дороже
 * ареста, зато без камеры и только если последнее нарушение было больше
 * двух минут назад), а админу — снять, назначить или выпустить из камеры.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Wanted {

    /** Сколько тиков держится звезда розыска (5 минут). */
    private static final long DECAY = 20L * 60 * 5;
    /** Насколько можно отойти от центра камеры (она 4x3 блока). */
    private static final double CELL = 3.0D;
    /** Кого и когда уже отмечали за драку, чтобы не копить звёзды за каждый удар. */
    private static final Map<UUID, Long> FIGHTS = new HashMap<>();
    /** Когда игрок последний раз нарушил закон: откупиться сразу после преступления нельзя. */
    private static final Map<UUID, Long> LAST_CRIME = new HashMap<>();
    /** Сколько ждать после нарушения, прежде чем можно откупиться (2 минуты). */
    public static final long PAY_COOLDOWN = 20L * 120;

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
        LAST_CRIME.put(player.getUUID(), player.level().getGameTime());
        player.displayClientMessage(Component.translatable("citylife.wanted.gained",
                Component.translatable(reasonKey), stars(now)).withStyle(ChatFormatting.RED), false);
    }

    /** Откуп: штраф вдвое больше, чем при аресте, но без камеры. */
    public static long bail(int stars) {
        return 2L * Math.max(1, stars) * CityConfig.CONFIG.finePerStar.get();
    }

    public enum Pay { OK, NOT_WANTED, TOO_SOON, NO_MONEY, IN_JAIL }

    /** Откупиться от розыска со своего счёта. */
    public static Pay pay(ServerPlayer player) {
        LifeData life = LifeData.get(player.server);
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        if (life.jailUntil(id) > now) {
            return Pay.IN_JAIL;
        }
        int stars = life.wanted(id);
        if (stars <= 0) {
            return Pay.NOT_WANTED;
        }
        Long last = LAST_CRIME.get(id);
        if (last != null && now - last < PAY_COOLDOWN && now >= last) {
            return Pay.TOO_SOON;
        }
        long fine = bail(stars);
        if (!CityData.get(player.server).withdraw(id, fine, dev.lscity.citylife.data.Texts.ru(
                "citylife.statement.bail"), now)) {
            return Pay.NO_MONEY;
        }
        clear(player.server, id);
        return Pay.OK;
    }

    /** Снять розыск и выпустить из камеры (админ или откуп). */
    public static void clear(net.minecraft.server.MinecraftServer server, UUID id) {
        LifeData life = LifeData.get(server);
        life.setWanted(id, 0, 0);
        life.setJail(id, 0);
        Robbery.forget(id);
        LAST_CRIME.remove(id);
        FIGHTS.remove(id);
    }

    @SubscribeEvent
    public static void onCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var root = net.minecraft.commands.Commands.literal("wanted")
                .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()));
        root.then(net.minecraft.commands.Commands.literal("pay").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int stars = LifeData.get(player.server).wanted(player.getUUID());
            Pay result = pay(player);
            String key = "citylife.wanted.pay." + result.name().toLowerCase(java.util.Locale.ROOT);
            Component msg = switch (result) {
                case OK -> Component.translatable(key, Money.format(bail(stars)));
                case TOO_SOON -> Component.translatable(key, PAY_COOLDOWN / 20);
                case NO_MONEY -> Component.translatable(key, Money.format(bail(stars)));
                default -> Component.translatable(key);
            };
            ctx.getSource().sendSuccess(() -> msg.copy().withStyle(result == Pay.OK ? ChatFormatting.GREEN
                    : ChatFormatting.RED), false);
            return result == Pay.OK ? 1 : 0;
        }));
        var target = net.minecraft.commands.arguments.EntityArgument.player();
        root.then(net.minecraft.commands.Commands.literal("clear").requires(s -> s.hasPermission(2))
                .executes(ctx -> clearCmd(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(net.minecraft.commands.Commands.argument("player", target).executes(ctx ->
                        clearCmd(ctx.getSource(), net.minecraft.commands.arguments.EntityArgument
                                .getPlayer(ctx, "player")))));
        root.then(net.minecraft.commands.Commands.literal("set").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("player", target)
                        .then(net.minecraft.commands.Commands.argument("stars",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                            ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                            int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stars");
                            LifeData.get(p.server).setWanted(p.getUUID(), n,
                                    n > 0 ? p.level().getGameTime() + DECAY : 0);
                            ctx.getSource().sendSuccess(() -> Component.translatable("citylife.wanted.cmd.set",
                                    p.getGameProfile().getName(), stars(n)), true);
                            return 1;
                        }))));
        root.then(net.minecraft.commands.Commands.literal("release").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("player", target).executes(ctx -> {
                    ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                    LifeData.get(p.server).setJail(p.getUUID(), p.level().getGameTime());
                    ctx.getSource().sendSuccess(() -> Component.translatable("citylife.wanted.cmd.released",
                            p.getGameProfile().getName()), true);
                    return 1;
                })));
        dispatcher.register(root);
    }

    private static int status(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
        LifeData life = LifeData.get(player.server);
        int stars = life.wanted(player.getUUID());
        long now = player.level().getGameTime();
        Component msg = stars <= 0 ? Component.translatable("citylife.wanted.status.clean")
                : Component.translatable("citylife.wanted.status", stars(stars),
                (life.wantedDecay(player.getUUID()) - now) / 20, Money.format(bail(stars)));
        source.sendSuccess(() -> msg, false);
        return stars;
    }

    private static int clearCmd(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
        boolean jailed = LifeData.get(player.server).jailUntil(player.getUUID()) > player.level().getGameTime();
        clear(player.server, player.getUUID());
        if (jailed) {
            // Из камеры — сразу на выход, а не ждать конца срока.
            LifeData.get(player.server).setJail(player.getUUID(), player.level().getGameTime());
        }
        player.displayClientMessage(Component.translatable("citylife.wanted.cleared")
                .withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.translatable("citylife.wanted.cmd.cleared",
                player.getGameProfile().getName()), true);
        return 1;
    }

    /** Камера: центр клетки из решёток в участке, иначе вход в участок. */
    public static Waypoint cell() {
        BlockPos pos = dev.lscity.citylife.estate.Estate.jailCell();
        return pos == null ? station() : new Waypoint("Камера", pos.getX(), pos.getY(), pos.getZ(),
                "police", true);
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
        bank.withdraw(player.getUUID(), paid, dev.lscity.citylife.data.Texts.ru(
                "citylife.statement.fine"), player.level().getGameTime());
        life.setWanted(player.getUUID(), 0, 0);
        long seized = Robbery.confiscate(player);
        if (seized > 0) {
            player.sendSystemMessage(Component.translatable("citylife.rob.seized",
                    Money.format(seized)).withStyle(ChatFormatting.GOLD));
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        Waypoint cell = cell();
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

    /** Что последним ушло в строку состояния игрока. */
    private static final Map<UUID, String> HUD = new HashMap<>();

    /** Строка состояния: розыск, камера, задание. Шлём, только если изменилась. */
    private static void hud(ServerPlayer player, LifeData life, long now) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        int stars = life.wanted(player.getUUID());
        if (stars > 0) {
            tag.putInt("wanted", stars);
        }
        long jail = life.jailUntil(player.getUUID());
        if (jail > now) {
            tag.putInt("jail", (int) ((jail - now) / 20));
        }
        String duty = dev.lscity.citylife.jobs.Duty.of(player);
        if (!duty.isEmpty()) {
            tag.putString("duty", dev.lscity.citylife.data.Texts.ru("citylife.hud.duty",
                    dev.lscity.citylife.data.Texts.ru("citylife.sos." + duty)));
        }
        String job = Robbery.hudLine(player);
        if (job.isEmpty()) {
            job = dev.lscity.citylife.jobs.Jobs.hudLine(player);
        }
        if (!job.isEmpty()) {
            tag.putString("job", job);
        }
        String key = tag.toString();
        if (!key.equals(HUD.get(player.getUUID()))) {
            HUD.put(player.getUUID(), key);
            dev.lscity.citylife.net.Net.sendPanel(player, "hud", tag, false);
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
        if (!(player instanceof net.minecraftforge.common.util.FakePlayer)) {
            hud(player, life, now);
        }
        int stars = life.wanted(id);
        if (stars > 0 && now >= life.wantedDecay(id)) {
            life.setWanted(id, stars - 1, now + DECAY);
            if (stars == 1) {
                Robbery.forget(id);
            }
            player.displayClientMessage(Component.translatable("citylife.wanted.decay",
                    stars(stars - 1)).withStyle(ChatFormatting.GRAY), true);
        }
        long until = life.jailUntil(id);
        if (until <= 0) {
            return;
        }
        if (now >= until) {
            life.setJail(id, 0);
            BlockPos exit = dev.lscity.citylife.estate.Estate.jailExit();
            if (exit != null) {
                player.teleportTo(player.server.overworld(), exit.getX() + 0.5D, exit.getY(),
                        exit.getZ() + 0.5D, player.getYRot(), player.getXRot());
            }
            player.sendSystemMessage(Component.translatable("citylife.wanted.released")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        Waypoint cell = cell();
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
