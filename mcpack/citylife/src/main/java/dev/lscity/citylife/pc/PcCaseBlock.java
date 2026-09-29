package dev.lscity.citylife.pc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** Корпус системного блока: правый клик открывает сборку. */
public class PcCaseBlock extends DeskBlock implements EntityBlock {

    public PcCaseBlock(Properties properties) {
        super(properties, 4, 0, 1, 12, 15, 15);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PcCaseBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof PcCaseBlockEntity entity) {
            NetworkHooks.openScreen(server, entity, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
                         boolean moved) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof PcCaseBlockEntity entity) {
            // Детали не пропадают вместе с корпусом — высыпаются рядом.
            Containers.dropContents(level, pos, entity.parts());
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
