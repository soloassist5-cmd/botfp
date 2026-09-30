package dev.lscity.citylife.estate;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Mail;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Замок на купленном жилье.
 *
 * Пока владелец в сети, чужой не откроет дверь и сундук, а если окажется
 * внутри (через окно, вместе с гостем) — его выставят за дверь. Когда
 * владельца нет, в дом можно зайти: так задумано, дом не сейф. Ломать и
 * строить на чужом участке нельзя никогда. Ключи — у тех, кому владелец
 * их дал (экран агентства или /house trust).
 *
 * Незанятые дома открыты всем — их смотрят перед покупкой.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class EstateGuard {

    /** Когда игроку в последний раз показывали предупреждение (тик игры). */
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    /** Кто и когда заходил в дом без хозяина: чтобы не слать письмо на каждый шаг. */
    private static final Map<String, Long> VISITS = new HashMap<>();

    private EstateGuard() {
    }

    /** Может ли игрок обходить защиту: администратор в творческом режиме. */
    private static boolean bypass(Player player) {
        return player instanceof ServerPlayer sp && sp.hasPermissions(2)
                && sp.gameMode.getGameModeForPlayer() != GameType.SURVIVAL
                && sp.gameMode.getGameModeForPlayer() != GameType.ADVENTURE;
    }

    private static boolean ownerOnline(ServerPlayer player, LifeData.Owner owner) {
        return player.server.getPlayerList().getPlayer(owner.id()) != null;
    }

    private static void warn(ServerPlayer player, Component text) {
        long now = player.level().getGameTime();
        Long last = WARNED.get(player.getUUID());
        if (last == null || now - last > 40) {
            WARNED.put(player.getUUID(), now);
            player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    /** Чужая собственность для этого игрока (или null — можно). */
    private static Estate.Unit foreign(ServerPlayer player, Estate.Unit unit) {
        if (unit == null || bypass(player)) {
            return null;
        }
        return LifeData.get(player.server).mayUse(unit.id(), player.getUUID()) ? null : unit;
    }

    private static boolean guarded(BlockState state, BlockEntity entity) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof BedBlock
                || state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock
                || entity instanceof Container || entity != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !CityConfig.CONFIG.homeLocks.get()) {
            return;
        }
        BlockPos pos = event.getPos();
        Estate.Unit unit = foreign(player, Estate.plotAt(pos));
        if (unit == null) {
            return;
        }
        LifeData.Owner owner = LifeData.get(player.server).owner(unit.id());
        BlockState state = player.level().getBlockState(pos);
        if (!guarded(state, player.level().getBlockEntity(pos))) {
            return;
        }
        if (!ownerOnline(player, owner)) {
            note(player, unit, owner);
            // Зайти можно, а рыться в чужих сундуках — уже кража.
            if (player.level().getBlockEntity(pos) instanceof Container) {
                theft(player, unit);
            }
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        warn(player, Component.translatable("citylife.home.locked", owner.name()));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !CityConfig.CONFIG.homeLocks.get()) {
            return;
        }
        Estate.Unit unit = foreign(player, Estate.plotAt(event.getPos()));
        if (unit != null) {
            event.setCanceled(true);
            warn(player, Component.translatable("citylife.home.no_build",
                    LifeData.get(player.server).owner(unit.id()).name()));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !CityConfig.CONFIG.homeLocks.get()) {
            return;
        }
        Estate.Unit unit = foreign(player, Estate.plotAt(event.getPos()));
        if (unit != null) {
            event.setCanceled(true);
            warn(player, Component.translatable("citylife.home.no_build",
                    LifeData.get(player.server).owner(unit.id()).name()));
        }
    }

    /**
     * Дважды в секунду: не стоит ли кто-то в чужом доме при хозяине.
     * Такого выставляем на крыльцо — через окно или следом за гостем
     * в запертый дом не попасть.
     */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0 || !CityConfig.CONFIG.homeLocks.get()
                || player.isSpectator()) {
            return;
        }
        Estate.Unit unit = foreign(player, Estate.boxAt(player.getX(), player.getY() + 0.1D,
                player.getZ()));
        if (unit == null) {
            return;
        }
        LifeData.Owner owner = LifeData.get(player.server).owner(unit.id());
        if (!ownerOnline(player, owner)) {
            note(player, unit, owner);
            return;
        }
        BlockPos out = unit.outside();
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.teleportTo(out.getX() + 0.5D, out.getY(), out.getZ() + 0.5D);
        warn(player, Component.translatable("citylife.home.kicked", owner.name()));
    }

    /** Хозяину — письмо, что у него был гость, пока его не было (раз в 10 минут). */
    private static void note(ServerPlayer player, Estate.Unit unit, LifeData.Owner owner) {
        String key = unit.id() + "|" + player.getUUID();
        long now = player.level().getGameTime();
        Long last = VISITS.get(key);
        if (last != null && now - last < 12000) {
            return;
        }
        VISITS.put(key, now);
        CityData.get(player.server).deliverMail(owner.id(), new Mail(player.getUUID(),
                "Охрана дома", Component.translatable("citylife.home.visit_mail",
                player.getGameProfile().getName(), unit.address()).getString(), now, false));
    }

    private static void theft(ServerPlayer player, Estate.Unit unit) {
        String key = unit.id() + "|theft|" + player.getUUID();
        long now = player.level().getGameTime();
        Long last = VISITS.get(key);
        if (last == null || now - last > 6000) {
            VISITS.put(key, now);
            dev.lscity.citylife.city.Wanted.crime(player, 1, "citylife.wanted.theft");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WARNED.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            EstateServer.forget(player);
        }
    }
}
