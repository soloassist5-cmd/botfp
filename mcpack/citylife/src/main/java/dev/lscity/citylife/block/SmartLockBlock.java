package dev.lscity.citylife.block;

import dev.lscity.citylife.Registration;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Умный замок: тонкая панель на стене рядом с дверью.
 *
 * Управляет соседними дверьми и калитками, выдаёт сигнал редстоуна,
 * ведёт журнал доступа и умеет привязываться к телефону владельца.
 */
public class SmartLockBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    // Панель прижата к той стороне блока, куда смотрит FACING.
    private static final VoxelShape NORTH = Block.box(4, 4, 0, 12, 12, 2);
    private static final VoxelShape SOUTH = Block.box(4, 4, 14, 12, 12, 16);
    private static final VoxelShape WEST = Block.box(0, 4, 4, 2, 12, 12);
    private static final VoxelShape EAST = Block.box(14, 4, 4, 16, 12, 12);

    public SmartLockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                           @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof ServerPlayer player)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof SmartLockBlockEntity lock) {
            lock.setOwner(player.getUUID(), player.getGameProfile().getName());
            lock.setLabel("Замок " + pos.getX() + ":" + pos.getZ());
            lock.note("Установлен: " + player.getGameProfile().getName());
            CityData.get(player.server).pairLock(player.getUUID(), pos);
            player.sendSystemMessage(Component.translatable("citylife.lock.placed"));
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SmartLockBlockEntity lock)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        // Телефон в руке: привязать замок, если доступ есть; иначе спросить код.
        if (held.is(Registration.SMARTPHONE.get())) {
            if (lock.isAllowed(player.getUUID())) {
                boolean added = CityData.get(serverPlayer.server).pairLock(player.getUUID(), pos);
                serverPlayer.sendSystemMessage(Component.translatable(
                        added ? "citylife.lock.paired" : "citylife.lock.already_paired"));
            } else if (lock.hasPin()) {
                Net.openPinPrompt(serverPlayer, pos, lock.getLabel());
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("citylife.lock.no_access"));
                lock.note("Отказ (телефон): " + player.getGameProfile().getName());
            }
            return InteractionResult.CONSUME;
        }

        if (lock.isAllowed(player.getUUID())) {
            toggle(level, pos, state, lock, serverPlayer.getGameProfile().getName(), true);
            return InteractionResult.CONSUME;
        }

        serverPlayer.sendSystemMessage(Component.translatable("citylife.lock.locked",
                lock.getOwnerName()));
        lock.note("Отказ: " + player.getGameProfile().getName());
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.6F);
        return InteractionResult.CONSUME;
    }

    /** Открыть или закрыть замок и связанные двери. */
    public void toggle(Level level, BlockPos pos, BlockState state, SmartLockBlockEntity lock,
                       String who, boolean announce) {
        boolean open = !state.getValue(POWERED);
        level.setBlock(pos, state.setValue(POWERED, open), Block.UPDATE_ALL);
        setNeighbourDoors(level, pos, state.getValue(FACING), open);
        level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, 0.7F, open ? 1.2F : 0.9F);
        if (announce) {
            lock.note((open ? "Открыл: " : "Закрыл: ") + who);
        }
        if (open) {
            level.scheduleTick(pos, this, lock.autoCloseTicks());
        }
    }

    /** Двери и калитки в радиусе двух блоков подчиняются замку. */
    private void setNeighbourDoors(Level level, BlockPos pos, Direction facing, boolean open) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos target = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(target);
                    if (state.getBlock() instanceof DoorBlock door) {
                        if (state.getValue(DoorBlock.OPEN) != open) {
                            door.setOpen(null, level, state, target, open);
                        }
                    } else if (state.getBlock() instanceof TrapDoorBlock) {
                        if (state.getValue(TrapDoorBlock.OPEN) != open) {
                            level.setBlock(target, state.setValue(TrapDoorBlock.OPEN, open),
                                    Block.UPDATE_ALL);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        setNeighbourDoors(level, pos, state.getValue(FACING), false);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 0.9F);
        if (level.getBlockEntity(pos) instanceof SmartLockBlockEntity lock) {
            lock.note("Автозакрытие");
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartLockBlockEntity(pos, state);
    }
}
