package dev.lscity.citylife.sit;

import com.mojang.brigadier.CommandDispatcher;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Сесть: правый клик пустой рукой по ступеньке или нижней плите, либо /sit
 * на месте. Встать — Shift. Сесть можно только на блок или на землю:
 * на игроков и на других жителей — нельзя.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Sitting {

    /** Дальше трёх блоков к ступеньке не тянемся. */
    private static final double REACH = 3.0D;

    private Sitting() {
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        Double height = seatHeight(player.level().getBlockState(pos));
        if (height == null) {
            return;
        }
        if (sitOn(player, pos, height)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Высота сиденья над низом блока; null — на этом блоке не сидят. */
    public static Double seatHeight(BlockState state) {
        if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
            return 0.5D;
        }
        if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            return 0.5D;
        }
        return null;
    }

    /** Сесть на блок. false — далеко, занято или над блоком нет места. */
    public static boolean sitOn(ServerPlayer player, BlockPos pos, double height) {
        if (player.isPassenger() || player.position().distanceTo(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)) > REACH + 1
                || !player.level().getBlockState(pos.above()).getCollisionShape(player.level(),
                pos.above()).isEmpty()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!level.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
            player.displayClientMessage(Component.translatable("citylife.sit.taken")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return seat(player, pos.getX() + 0.5D, pos.getY() + height - 0.2D, pos.getZ() + 0.5D, pos);
    }

    /** /sit: сесть на землю там, где стоишь. */
    public static boolean sitHere(ServerPlayer player) {
        if (player.isPassenger() || !player.onGround()) {
            player.displayClientMessage(Component.translatable("citylife.sit.cannot")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1D, player.getZ());
        Double height = seatHeight(player.level().getBlockState(below));
        if (height != null) {
            return seat(player, below.getX() + 0.5D, below.getY() + height - 0.2D,
                    below.getZ() + 0.5D, below);
        }
        return seat(player, player.getX(), player.getY() - 0.2D, player.getZ(), null);
    }

    private static boolean seat(ServerPlayer player, double x, double y, double z, BlockPos block) {
        SeatEntity seat = Registration.SEAT.get().create(player.level());
        if (seat == null) {
            return false;
        }
        seat.moveTo(x, y, z, player.getYRot(), 0F);
        seat.setBlock(block);
        player.serverLevel().addFreshEntity(seat);
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        player.displayClientMessage(Component.translatable("citylife.sit.hint")
                .withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sit").executes(ctx ->
                sitHere(ctx.getSource().getPlayerOrException()) ? 1 : 0));
    }
}
