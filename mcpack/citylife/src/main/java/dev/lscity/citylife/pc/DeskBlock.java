package dev.lscity.citylife.pc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Настольная периферия: клавиатура, мышь, гарнитура, монитор.
 *
 * Блок смотрит на того, кто его поставил, и имеет свою небольшую форму,
 * чтобы клавиатура лежала на столе плоско, а не торчала кубом.
 */
public class DeskBlock extends HorizontalDirectionalBlock {

    private final VoxelShape northShape;
    private final VoxelShape eastShape;

    /** Форма задаётся для взгляда на север, для восток/запад она поворачивается. */
    public DeskBlock(Properties properties, double x0, double y0, double z0, double x1,
                     double y1, double z1) {
        super(properties);
        this.northShape = Block.box(x0, y0, z0, x1, y1, z1);
        this.eastShape = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.Z ? northShape : eastShape;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
