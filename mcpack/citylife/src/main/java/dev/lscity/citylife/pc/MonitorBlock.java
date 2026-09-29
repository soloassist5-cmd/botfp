package dev.lscity.citylife.pc;

import dev.lscity.citylife.Registration;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Монитор: через него включается компьютер.
 *
 * Рядом (в пределах RANGE блоков) должны стоять собранный системный блок,
 * клавиатура и мышь. Если чего-то нет, монитор так и говорит — что именно.
 */
public class MonitorBlock extends DeskBlock {

    public static final int RANGE = 4;

    public MonitorBlock(Properties properties) {
        super(properties, 1, 0, 6, 15, 13, 10);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer server)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos casePos = nearby(level, pos, Registration.PC_CASE.get());
        if (casePos == null) {
            refuse(server, "citylife.pc.no_case");
        } else if (nearby(level, pos, Registration.KEYBOARD.get()) == null) {
            refuse(server, "citylife.pc.no_keyboard");
        } else if (nearby(level, pos, Registration.MOUSE.get()) == null) {
            refuse(server, "citylife.pc.no_mouse");
        } else if (level.getBlockEntity(casePos) instanceof PcCaseBlockEntity entity) {
            PcBuild.Result build = entity.build();
            if (!build.works()) {
                server.displayClientMessage(Component.translatable("citylife.pc.not_booting")
                        .withStyle(ChatFormatting.RED), false);
                build.problems().forEach(line -> server.displayClientMessage(
                        Component.literal(" • ").append(line).withStyle(ChatFormatting.GRAY),
                        false));
            } else {
                Net.openComputer(server, casePos);
            }
        }
        return InteractionResult.CONSUME;
    }

    private static void refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    /** Ближайший блок нужного типа в кубе RANGE вокруг монитора. */
    public static BlockPos nearby(Level level, BlockPos center, Block block) {
        for (BlockPos probe : BlockPos.betweenClosed(center.offset(-RANGE, -2, -RANGE),
                center.offset(RANGE, 2, RANGE))) {
            if (level.getBlockState(probe).is(block)) {
                return probe.immutable();
            }
        }
        return null;
    }
}
