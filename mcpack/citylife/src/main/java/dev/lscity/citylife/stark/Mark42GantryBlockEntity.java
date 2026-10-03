package dev.lscity.citylife.stark;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Стенд сборки: помнит, когда костюм сняли. Пока костюм «в деле», над
 * стендом голограмма; через пять минут (или когда костюм вернули) он
 * снова стоит на месте — Зал брони не пустеет.
 */
public class Mark42GantryBlockEntity extends BlockEntity {

    /** Сколько тиков стенд пуст после того, как костюм забрали. */
    public static final long AWAY = 6000;

    private long takenAt = -1;

    public Mark42GantryBlockEntity(BlockPos pos, BlockState state) {
        super(Mark42.GANTRY_BE.get(), pos, state);
    }

    public boolean away() {
        return level != null && takenAt >= 0 && level.getGameTime() - takenAt < AWAY;
    }

    void taken() {
        takenAt = level.getGameTime();
        sync();
    }

    void returned() {
        takenAt = -1;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("taken", takenAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        takenAt = tag.contains("taken") ? tag.getLong("taken") : -1;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("taken", takenAt);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2.5, 0).inflate(0.5);
    }
}
