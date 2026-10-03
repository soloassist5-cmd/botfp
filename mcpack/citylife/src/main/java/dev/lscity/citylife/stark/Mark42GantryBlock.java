package dev.lscity.citylife.stark;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Стенд сборки Mark 42: платформа, на которой стоит костюм.
 *
 * ПКМ пустой рукой — детали снимаются со стенда и летят на игрока. ПКМ в
 * костюме — костюм разбирается и возвращается на стенд. Без допуска охраны
 * это кража (как с витринами Зала брони): костюм уйдёт, но с розыском.
 */
public class Mark42GantryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public Mark42GantryBlock(Properties properties) {
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Mark42GantryBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof Mark42GantryBlockEntity be)) {
            return InteractionResult.PASS;
        }
        Vec3 stand = Vec3.atBottomCenterOf(pos).add(0, 0.25, 0);
        if (Mark42.wearsAny(sp)) {
            if (Mark42.home(sp, stand)) {
                be.returned();
            }
            return InteractionResult.CONSUME;
        }
        if (!player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(Component.translatable("citylife.mark42.gantry.empty_hand")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (be.away()) {
            player.displayClientMessage(Component.translatable("citylife.mark42.gantry.away")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        StarkData data = StarkData.get(sp.server);
        if (!StarkSecurity.cleared(sp) && data.armed()) {
            StarkSecurity.raise(sp, 4, "citylife.wanted.stark_theft");
        }
        if (Mark42.assemble(sp, stand)) {
            be.taken();
            data.log(level.getGameTime(), dev.lscity.citylife.data.Texts.ru("citylife.stark.log.took",
                    sp.getGameProfile().getName(), "Mark 42"), false);
        }
        return InteractionResult.CONSUME;
    }
}
