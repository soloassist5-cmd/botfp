package dev.lscity.citylife.stark;

import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Лазерный датчик: луч идёт от линзы по FACING до первого твёрдого блока
 * (не дальше {@link #MAX}). Длину пересчитываем раз в секунду — поставили
 * ящик поперёк луча, и луч стал короче.
 */
public class LaserBlockEntity extends BlockEntity {
    public static final int MAX = 24;

    private int length = -1;
    private long measured = -100;

    public LaserBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.LASER_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(LaserBlock.FACING);
    }

    /** Длина луча в блоках (0 — упёрся сразу). */
    public int length() {
        if (level == null) {
            return 0;
        }
        long now = level.getGameTime();
        if (length < 0 || now - measured >= 20) {
            measured = now;
            Direction dir = facing();
            BlockPos.MutableBlockPos p = worldPosition.mutable();
            int n = 0;
            while (n < MAX) {
                p.move(dir);
                BlockState state = level.getBlockState(p);
                // Луч гасит любой непрозрачный блок; стекло он проходит насквозь.
                if (!state.getCollisionShape(level, p).isEmpty() && state.canOcclude()
                        || state.getBlock() instanceof LaserBlock) {
                    break;
                }
                n++;
            }
            length = n;
        }
        return length;
    }

    /** Коробка луча в мире или null, если луча нет. */
    public AABB beam() {
        int n = length();
        if (n <= 0) {
            return null;
        }
        Direction dir = facing();
        var start = worldPosition.getCenter().relative(dir, 0.5);
        var end = start.relative(dir, n);
        return new AABB(start, end).inflate(0.06);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            StarkSecurity.addLaser(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            StarkSecurity.removeLaser(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) {
            StarkSecurity.removeLaser(level, worldPosition);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(net.minecraft.world.phys.Vec3.atLowerCornerOf(
                facing().getNormal()).scale(MAX));
    }
}
