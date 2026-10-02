package dev.lscity.citylife.stark;

import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Голо-монитор Stark: одна точка крепления, а экран — Width×Height блоков
 * вправо и вверх от неё (если смотреть на экран). Что показывает — Mode:
 * «Джарвис», охрана, Зал брони, радар, реактор, котировки.
 */
public class HoloScreenBlockEntity extends BlockEntity {

    public static final List<String> MODES = List.of("jarvis", "security", "armor", "radar", "reactor",
            "stocks");

    private String mode = "jarvis";
    private int width = 3;
    private int height = 2;

    public HoloScreenBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.HOLO_SCREEN_BE.get(), pos, state);
    }

    public String mode() {
        return mode;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Direction facing() {
        return getBlockState().getValue(HoloScreenBlock.FACING);
    }

    public void nextMode() {
        int i = MODES.indexOf(mode);
        mode = MODES.get((i + 1) % MODES.size());
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Mode", mode);
        tag.putInt("Width", width);
        tag.putInt("Height", height);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mode = MODES.contains(tag.getString("Mode")) ? tag.getString("Mode") : "jarvis";
        width = tag.contains("Width") ? Math.max(1, Math.min(8, tag.getInt("Width"))) : 3;
        height = tag.contains("Height") ? Math.max(1, Math.min(6, tag.getInt("Height"))) : 2;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction right = facing().getCounterClockWise();
        BlockPos far = worldPosition.relative(right, width - 1).above(height - 1);
        return new AABB(worldPosition).minmax(new AABB(far)).inflate(0.5);
    }
}
