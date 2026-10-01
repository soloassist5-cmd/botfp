package dev.lscity.citylife.sit;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Невидимое сиденье: на нём сидит игрок (ступенька, плита или /sit).
 *
 * Сиденье живёт, только пока на нём кто-то сидит; встал — пропало. Если
 * под ним сломали ступеньку, тоже пропадает, и игрок встаёт.
 */
public class SeatEntity extends Entity {

    /** Блок, на котором сидят; null — сидят на земле (/sit). */
    private BlockPos block;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setBlock(BlockPos block) {
        this.block = block;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (getPassengers().isEmpty() || (block != null && level().getBlockState(block).isAir())) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Встать рядом, а не провалиться в ступеньку: на блок выше сиденья.
        return new Vec3(getX(), Math.floor(getY()) + 1.0D, getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
