package dev.lscity.citylife.pc;

import dev.lscity.citylife.item.DeviceItem;
import dev.lscity.citylife.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ноутбук, поставленный на стол.
 *
 * Ставится Shift+ПКМ ноутбуком по верхней грани стола (или любого блока).
 * ПКМ по закрытому — поднять крышку, по открытому — включить. Shift+ПКМ
 * по открытому — закрыть крышку, по закрытому — забрать в инвентарь.
 */
public class LaptopBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape CLOSED = Block.box(1, 0, 3, 15, 2, 13);
    private static final VoxelShape OPEN_NS_NORTH = Shapes.or(Block.box(1, 0, 3, 15, 1, 13),
            Block.box(1, 1, 12, 15, 11, 13));

    public LaptopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        if (!state.getValue(OPEN)) {
            Direction f = state.getValue(FACING);
            return f.getAxis() == Direction.Axis.Z ? CLOSED : Block.box(3, 0, 1, 13, 2, 15);
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> Shapes.or(Block.box(1, 0, 3, 15, 1, 13), Block.box(1, 1, 3, 15, 11, 4));
            case EAST -> Shapes.or(Block.box(3, 0, 1, 13, 1, 15), Block.box(3, 1, 1, 4, 11, 15));
            case WEST -> Shapes.or(Block.box(3, 0, 1, 13, 1, 15), Block.box(12, 1, 1, 13, 11, 15));
            default -> OPEN_NS_NORTH;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaptopBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof LaptopBlockEntity laptop)
                || !(player instanceof ServerPlayer server)) {
            return InteractionResult.CONSUME;
        }
        boolean open = state.getValue(OPEN);
        if (player.isShiftKeyDown()) {
            if (open) {
                level.setBlockAndUpdate(pos, state.setValue(OPEN, false));
                level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS,
                        0.5F, 1.6F);
            } else {
                ItemStack stack = laptop.stack().copy();
                laptop.setStack(ItemStack.EMPTY);
                level.removeBlock(pos, false);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
            return InteractionResult.CONSUME;
        }
        if (!open) {
            level.setBlockAndUpdate(pos, state.setValue(OPEN, true));
            level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS,
                    0.5F, 1.6F);
            return InteractionResult.CONSUME;
        }
        if (laptop.stack().getItem() instanceof DeviceItem) {
            Net.openLaptop(server, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** Сломали блок — ноутбук выпадает целым, со всеми данными. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next,
                         boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof LaptopBlockEntity laptop
                && !laptop.stack().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.2D,
                    pos.getZ() + 0.5D, laptop.stack());
            laptop.setStack(ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
