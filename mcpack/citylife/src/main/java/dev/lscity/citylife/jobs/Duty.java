package dev.lscity.citylife.jobs;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.city.Online;
import dev.lscity.citylife.city.Wanted;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Дежурство игроков в службах 112: полиция, скорая, пожарные.
 *
 * Дежурный получает вызовы других игроков с кнопкой «Принять». Принял —
 * наряд из жителей не выезжает, навигатор ведёт к звонившему, а за приезд
 * платит город. Кроме вызовов:
 *   полицейский задерживает разыскиваемого кликом по нему (награда за звёзды),
 *   медик лечит раненого кликом (пациент платит, если есть чем),
 *   пожарный тушит огонь вокруг себя, пока на дежурстве.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Duty {

    public static final List<String> SERVICES = List.of("police", "medic", "fire", "taxi");
    public static final long ARRIVAL_PAY = 400;
    public static final long ARREST_PAY_PER_STAR = 300;
    public static final long HEAL_FEE = 150;

    private static final Map<UUID, String> ON_DUTY = new HashMap<>();

    private Duty() {
    }

    public static String of(ServerPlayer player) {
        return ON_DUTY.getOrDefault(player.getUUID(), "");
    }

    public static void set(ServerPlayer player, String service) {
        if (service == null || service.isEmpty() || "off".equals(service)) {
            ON_DUTY.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("citylife.duty.off")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (!SERVICES.contains(service)) {
            return;
        }
        ON_DUTY.put(player.getUUID(), service);
        player.displayClientMessage(Component.translatable("citylife.duty.on",
                Texts.ru("taxi".equals(service) ? "citylife.duty.taxi_name"
                        : "citylife.sos." + service)).withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable("citylife.duty.hint." + service)
                .withStyle(ChatFormatting.GRAY), false);
    }

    /** Дежурные этой службы, кроме самого звонящего. */
    public static List<ServerPlayer> onDuty(ServerPlayer caller, String service) {
        return Online.players(caller.server).stream()
                .filter(p -> p != caller && service.equals(ON_DUTY.get(p.getUUID())))
                .toList();
    }

    /** Кнопка «Принять вызов» для дежурного. */
    public static MutableComponent acceptButton(int call) {
        String command = "/sos accept " + call;
        return Component.literal("[")
                .append(Component.translatable("citylife.duty.accept"))
                .append("]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** Дежурный доехал до звонившего: город платит за вызов. */
    public static void paid(ServerPlayer responder, ServerPlayer caller, String service) {
        CityData.get(responder.server).deposit(responder.getUUID(), ARRIVAL_PAY,
                Texts.ru("citylife.statement.duty", Texts.ru("citylife.sos." + service)),
                responder.level().getGameTime());
        LifeData.get(responder.server).recordJob(responder.getUUID(), ARRIVAL_PAY);
        responder.displayClientMessage(Component.translatable("citylife.duty.arrived",
                Money.format(ARRIVAL_PAY)).withStyle(ChatFormatting.GOLD), false);
        if (caller != null) {
            caller.displayClientMessage(Component.translatable("citylife.duty.caller_news",
                    responder.getGameProfile().getName()).withStyle(ChatFormatting.AQUA), false);
        }
    }

    // --- клик по игроку ------------------------------------------------------------

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer officer)
                || !(event.getTarget() instanceof ServerPlayer target)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        String service = ON_DUTY.get(officer.getUUID());
        if (service == null) {
            return;
        }
        LifeData life = LifeData.get(officer.server);
        CityData bank = CityData.get(officer.server);
        long now = officer.level().getGameTime();
        if ("police".equals(service) && life.wanted(target.getUUID()) > 0) {
            int stars = life.wanted(target.getUUID());
            Wanted.arrest(target, false);
            long reward = stars * ARREST_PAY_PER_STAR;
            bank.deposit(officer.getUUID(), reward, Texts.ru("citylife.statement.arrest",
                    target.getGameProfile().getName()), now);
            life.recordJob(officer.getUUID(), reward);
            officer.displayClientMessage(Component.translatable("citylife.duty.arrested",
                    target.getGameProfile().getName(), Money.format(reward))
                    .withStyle(ChatFormatting.GOLD), false);
            done(event);
        } else if ("medic".equals(service) && target.getHealth() < target.getMaxHealth()) {
            target.setHealth(target.getMaxHealth());
            target.clearFire();
            List.copyOf(target.getActiveEffects()).stream()
                    .filter(e -> e.getEffect().getCategory() == MobEffectCategory.HARMFUL)
                    .forEach(e -> target.removeEffect(e.getEffect()));
            long fee = bank.transfer(target.getUUID(), officer.getUUID(), HEAL_FEE) ? HEAL_FEE : 0;
            if (fee > 0) {
                bank.record(target.getUUID(), -fee, Texts.ru("citylife.statement.medic"), now);
                bank.record(officer.getUUID(), fee, Texts.ru("citylife.statement.healed",
                        target.getGameProfile().getName()), now);
            }
            officer.displayClientMessage(Component.translatable("citylife.duty.healed",
                    target.getGameProfile().getName(), Money.format(fee))
                    .withStyle(ChatFormatting.GREEN), false);
            target.displayClientMessage(Component.translatable("citylife.duty.healed_you",
                    officer.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), false);
            done(event);
        }
    }

    private static void done(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Пожарный на дежурстве тушит огонь в паре блоков вокруг себя. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0 || !"fire".equals(ON_DUTY.get(player.getUUID()))) {
            return;
        }
        player.clearFire();
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-3, -1, -3),
                player.blockPosition().offset(3, 3, 3))) {
            if (player.level().getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ON_DUTY.remove(event.getEntity().getUUID());
    }
}
