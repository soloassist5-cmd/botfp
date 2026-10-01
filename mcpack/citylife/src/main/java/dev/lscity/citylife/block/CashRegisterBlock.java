package dev.lscity.citylife.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Кассовый аппарат на прилавке. Клик — касса ближайшего продавца: корзина
 * и оплата картой или наличными. FACING смотрит на покупателя.
 */
public class CashRegisterBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public CashRegisterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            Entity clerk = nearestClerk(level, pos);
            if (clerk == null || !dev.lscity.citylife.trade.Checkout.open(server, clerk)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "citylife.checkout.closed"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Продавец, к которому относится касса: ближайший житель с прилавком в 3 блоках. */
    public static Entity nearestClerk(Level level, BlockPos pos) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, new AABB(pos).inflate(3),
                e -> dev.lscity.citylife.trade.ShopHandler.shopOf(e) != null)) {
            double d = e.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
