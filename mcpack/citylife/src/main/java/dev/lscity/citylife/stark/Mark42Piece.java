package dev.lscity.citylife.stark;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Летящая деталь Mark 42 — шлем, кираса, поножи или ботинки.
 *
 * Путь у детали предсказуемый: старт, плавная дуга с подъёмом, точка на теле
 * хозяина (или точка в небе, если костюм улетает домой). Поэтому клиент не
 * ждёт координат от сервера, а считает их сам по тем же формулам — полёт
 * получается гладким даже при плохом пинге. Сервер решает только, когда
 * деталь долетела и защёлкнулась.
 */
public class Mark42Piece extends Entity {

    private static final EntityDataAccessor<Byte> SLOT =
            SynchedEntityData.defineId(Mark42Piece.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET =
            SynchedEntityData.defineId(Mark42Piece.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> START =
            SynchedEntityData.defineId(Mark42Piece.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> END =
            SynchedEntityData.defineId(Mark42Piece.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> TIMING =
            SynchedEntityData.defineId(Mark42Piece.class, EntityDataSerializers.INT);

    private UUID owner;
    /** Хозяин на сервере: прямая ссылка (игрок теста, например, не числится в мире). */
    private LivingEntity targetRef;
    private int age;

    public Mark42Piece(EntityType<? extends Mark42Piece> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Задать полёт: к игроку (target) или в точку end. delay и flight — в тиках. */
    void launch(Vec3 start, EquipmentSlot slot, ServerPlayer target, Vec3 end, int delay, int flight) {
        setPos(start);
        entityData.set(SLOT, (byte) slot.getIndex());
        entityData.set(TARGET, target == null ? -1 : target.getId());
        entityData.set(START, start.toVector3f());
        entityData.set(END, (end == null ? start : end).toVector3f());
        entityData.set(TIMING, delay << 8 | flight);
        owner = target == null ? null : target.getUUID();
        targetRef = target;
        setYRot(target == null ? 0 : target.getYRot());
    }

    public EquipmentSlot slot() {
        return EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, entityData.get(SLOT));
    }

    public int delay() {
        return entityData.get(TIMING) >> 8;
    }

    public int flight() {
        return Math.max(1, entityData.get(TIMING) & 0xFF);
    }

    public int age() {
        return age;
    }

    /** Насколько пройден путь, 0…1 (partial — доля тика для плавной картинки). */
    public double progress(float partial) {
        return Mth.clamp((age + partial - delay()) / flight(), 0, 1);
    }

    public LivingEntity target() {
        if (targetRef != null) {
            return targetRef.isRemoved() ? null : targetRef;
        }
        int id = entityData.get(TARGET);
        return id < 0 ? null : level().getEntity(id) instanceof LivingEntity e ? e : null;
    }

    /** Где деталь в момент age + partial. */
    public Vec3 position(float partial) {
        Vec3 start = new Vec3(entityData.get(START));
        LivingEntity target = target();
        Vec3 end = target != null ? Mark42.mount(target, slot()).add(target.getDeltaMovement())
                : new Vec3(entityData.get(END));
        double t = Mark42.ease(progress(partial));
        Vec3 line = start.lerp(end, t);
        // Дуга: деталь взмывает и заходит сверху, а не ползёт по прямой.
        double lift = Math.sin(t * Math.PI) * Math.min(4.0, start.distanceTo(end) * 0.25);
        double hover = age + partial < delay() ? Math.sin((age + partial) * 0.5) * 0.05 : 0;
        return line.add(0, lift + hover, 0);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Vec3 now = position(0);
        Vec3 was = getPosition(0);
        setPos(now);
        Vec3 step = now.subtract(was);
        if (step.horizontalDistanceSqr() > 1.0E-4) {
            setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F);
        }
        if (level().isClientSide) {
            if (age > delay() && progress(0) < 1) {
                // Выхлоп маленьких двигателей детали.
                level().addParticle(ParticleTypes.FLAME, getX(), getY() - 0.2, getZ(), -step.x * 0.2, -0.02,
                        -step.z * 0.2);
                if (random.nextInt(2) == 0) {
                    level().addParticle(ParticleTypes.SMOKE, getX(), getY() - 0.2, getZ(), 0, 0.01, 0);
                }
            }
            return;
        }
        if (progress(0) >= 1) {
            LivingEntity target = target();
            if (target instanceof ServerPlayer player && player.isAlive() && player.level() == level()) {
                Mark42.attach(player, slot());
            } else if (owner != null) {
                Mark42.lost(owner);
            }
            discard();
        } else if (owner != null && !(target() instanceof ServerPlayer)) {
            Mark42.lost(owner);   // хозяин пропал посреди сборки
            discard();
        } else if (age > delay() + flight() + 100) {
            discard();
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // Координаты считаем сами (см. описание класса).
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SLOT, (byte) 0);
        entityData.define(TARGET, -1);
        entityData.define(START, new Vector3f());
        entityData.define(END, new Vector3f());
        entityData.define(TIMING, 20);
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
