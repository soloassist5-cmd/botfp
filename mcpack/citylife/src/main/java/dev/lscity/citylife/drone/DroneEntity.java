package dev.lscity.citylife.drone;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

/**
 * Дрон в мире.
 *
 * Пока им управляют, летает он у пилота: клиент пилота считает полёт и
 * присылает координаты каждый тик, сервер только проверяет, что дрон не
 * прыгнул дальше, чем может пролететь. Так управление не «плывёт» от пинга.
 * Без пилота дрон работает сам: садится на землю, а в режиме «Домой» летит
 * к хозяину. Батарея кончилась — моторы встали, дрон падает.
 */
public class DroneEntity extends Entity {

    private static final EntityDataAccessor<Byte> TYPE =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> BATTERY =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> PILOT =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MOTORS =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LIGHTS =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HOMING =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> CARGO =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Служба доставки: кому везём (null — это обычный дрон игрока) и этап полёта. */
    private UUID deliverTo;
    private ServerPlayer deliverRef;
    private int deliverPhase;
    private int deliverAge;

    /** Тик последнего пакета от пилота: для проверки скорости. */
    private long lastMove;
    private String ownerName = "";

    // Плавное движение у зрителей (не у пилота): как у лодки.
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYaw;
    private float lerpPitch;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    // --- состояние --------------------------------------------------------------

    public DroneType droneType() {
        int i = entityData.get(TYPE);
        return DroneType.values()[Mth.clamp(i, 0, DroneType.values().length - 1)];
    }

    void setType(DroneType type) {
        entityData.set(TYPE, (byte) type.ordinal());
        entityData.set(HEALTH, type.maxHealth);
    }

    public UUID owner() {
        return entityData.get(OWNER).orElse(null);
    }

    public String ownerName() {
        return ownerName;
    }

    void setOwner(Player player) {
        entityData.set(OWNER, Optional.of(player.getUUID()));
        ownerName = player.getGameProfile().getName();
    }

    public boolean ownedBy(Player player) {
        return player.getUUID().equals(owner());
    }

    public float battery() {
        return entityData.get(BATTERY);
    }

    public void setBattery(float value) {
        entityData.set(BATTERY, Mth.clamp(value, 0, 1));
    }

    public float health() {
        return entityData.get(HEALTH);
    }

    public int pilotId() {
        return entityData.get(PILOT);
    }

    void setPilot(int id) {
        entityData.set(PILOT, id);
        if (id >= 0) {
            entityData.set(MOTORS, true);
            entityData.set(HOMING, false);
        }
    }

    public boolean motors() {
        return entityData.get(MOTORS);
    }

    public boolean lights() {
        return entityData.get(LIGHTS);
    }

    void toggleLights() {
        entityData.set(LIGHTS, !lights());
    }

    public boolean homing() {
        return entityData.get(HOMING);
    }

    public void setHoming(boolean value) {
        entityData.set(HOMING, value);
        if (value) {
            entityData.set(MOTORS, battery() > 0);
        }
    }

    public ItemStack cargo() {
        return entityData.get(CARGO);
    }

    void setCargo(ItemStack stack) {
        entityData.set(CARGO, stack.copy());
    }

    // --- такт -------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0 && !Drones.localPilotOf(this)) {
                double t = 1.0 / lerpSteps;
                setPos(getX() + (lerpX - getX()) * t, getY() + (lerpY - getY()) * t, getZ() + (lerpZ - getZ()) * t);
                setYRot(getYRot() + Mth.wrapDegrees(lerpYaw - getYRot()) * (float) t);
                setXRot(getXRot() + (lerpPitch - getXRot()) * (float) t);
                lerpSteps--;
            }
            if (motors() && random.nextInt(12) == 0) {
                level().addParticle(ParticleTypes.CLOUD, getX(), getY() - 0.1, getZ(), 0, -0.05, 0);
            }
            return;
        }
        serverTick();
    }

    /** Держит чанк дрона загруженным, пока им управляют или он летит домой. */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
            net.minecraft.server.level.TicketType.create("citylife_drone",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 60);

    /** Курьер службы доставки: везёт goods игроку target (см. Courier). */
    public void startDelivery(ServerPlayer target, ItemStack goods) {
        deliverTo = target.getUUID();
        deliverRef = target;
        deliverPhase = 0;
        ownerName = "служба доставки";
        setCargo(goods);
        entityData.set(MOTORS, true);
        entityData.set(LIGHTS, true);
        noPhysics = true;
    }

    public boolean courier() {
        return deliverTo != null;
    }

    /**
     * Полёт курьера: над игроком на высоте перелёта, вниз до головы, сброс
     * посылки — и вверх, где дрон и пропадает. Если игрок вышел или полёт
     * затянулся, посылка просто окажется у него в инвентаре.
     */
    private void deliveryTick() {
        deliverAge++;
        ServerPlayer target = getServer() == null ? null : getServer().getPlayerList().getPlayer(deliverTo);
        if (target == null && deliverRef != null && !deliverRef.isRemoved()) {
            target = deliverRef;   // игрок теста не числится в списке игроков
        }
        if (target == null || target.level() != level() || deliverAge > 3000) {
            if (target != null && !cargo().isEmpty()) {
                target.getInventory().placeItemBackInInventory(cargo());
            }
            discard();
            return;
        }
        if (tickCount % 20 == 0 && level() instanceof ServerLevel sl) {
            var chunk = new net.minecraft.world.level.ChunkPos(blockPosition());
            sl.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
        }
        Vec3 aim;
        double speed;
        if (deliverPhase == 0) {
            aim = new Vec3(target.getX(), Math.max(Courier.CRUISE, target.getY() + 40), target.getZ());
            speed = 0.9;
            if (Math.hypot(aim.x - getX(), aim.z - getZ()) < 1.5) {
                deliverPhase = 1;
            }
        } else if (deliverPhase == 1) {
            aim = target.position().add(0, 2.4, 0);
            speed = 0.8;
            if (position().distanceTo(aim) < 0.7) {
                ItemEntity drop = new ItemEntity(level(), target.getX(), target.getY() + 0.5, target.getZ(), cargo());
                drop.setNoPickUpDelay();
                level().addFreshEntity(drop);
                setCargo(ItemStack.EMPTY);
                level().playSound(null, blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 1.0F,
                        1.0F);
                target.displayClientMessage(Component.translatable("citylife.courier.arrived")
                        .withStyle(ChatFormatting.GREEN), true);
                deliverPhase = 2;
            }
        } else {
            aim = position().add(0, 10, 0);
            speed = 0.7;
            if (getY() > Math.max(Courier.CRUISE, target.getY() + 40)) {
                discard();
                return;
            }
        }
        Vec3 to = aim.subtract(position());
        Vec3 step = to.length() <= speed ? to : to.normalize().scale(speed);
        setDeltaMovement(step);
        setPos(position().add(step));
        if (step.horizontalDistanceSqr() > 1.0E-4) {
            setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F);
        }
    }

    private void serverTick() {
        if (deliverTo != null) {
            deliveryTick();
            return;
        }
        DroneType type = droneType();
        ServerPlayer pilot = Drones.pilot(this);
        if ((pilot != null || homing()) && tickCount % 20 == 0 && level() instanceof ServerLevel sl) {
            var chunk = new net.minecraft.world.level.ChunkPos(blockPosition());
            sl.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
        }
        if (motors()) {
            float drain = type.drainPerTick() * (pilot != null ? 1.0F : 0.6F);
            if (!cargo().isEmpty()) {
                drain *= 1.4F;
            }
            setBattery(battery() - drain);
            if (battery() <= 0) {
                entityData.set(MOTORS, false);
                Drones.stop(this, "citylife.drone.battery_dead");
                pilot = null;
            }
        }
        if (pilot != null) {
            // Связь: дальше предела — управление пропадает, дрон летит домой.
            if (pilot.level() != level() || pilot.distanceTo(this) > Drones.range(this) || !pilot.isAlive()) {
                Drones.stop(this, "citylife.drone.signal_lost");
                setHoming(true);
            } else if (level().getGameTime() - lastMove > 100) {
                Drones.stop(this, "citylife.drone.signal_lost");   // клиент пилота молчит
            }
            return;
        }
        Vec3 motion;
        if (!motors()) {
            // Без моторов — камнем вниз.
            motion = getDeltaMovement().multiply(0.98, 1, 0.98).add(0, -0.06, 0);
        } else if (homing()) {
            motion = homeMotion(type);
        } else if (!onGround()) {
            motion = new Vec3(0, -0.08, 0);   // аккуратно садится
        } else {
            entityData.set(MOTORS, false);
            motion = Vec3.ZERO;
        }
        double fall = motion.y;
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (onGround() && fall < -0.7) {
            hurt(damageSources().fall(), (float) (-fall * 6));
        }
    }

    /** «Домой»: набрать высоту, долететь до хозяина, сесть рядом. */
    private Vec3 homeMotion(DroneType type) {
        ServerPlayer owner = owner() == null || getServer() == null ? null
                : getServer().getPlayerList().getPlayer(owner());
        if (owner == null || owner.level() != level()) {
            setHoming(false);
            return Vec3.ZERO;
        }
        Vec3 to = owner.position().subtract(position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        double speed = type.maxSpeed * 0.8;
        if (flat < 2.0) {
            if (onGround()) {
                setHoming(false);
                entityData.set(MOTORS, false);
                owner.displayClientMessage(Component.translatable("citylife.drone.home_landed")
                        .withStyle(ChatFormatting.AQUA), true);
                return Vec3.ZERO;
            }
            return new Vec3(to.x * 0.2, -0.15, to.z * 0.2);
        }
        // Над препятствием — вверх; идём на высоте хозяина + 6 блоков.
        double cruise = owner.getY() + 6 - getY();
        double vy = horizontalCollision ? type.climb : Mth.clamp(cruise * 0.1, -type.climb, type.climb);
        return new Vec3(to.x / flat * speed, vy, to.z / flat * speed);
    }

    /**
     * Координаты от пилота. Скорость проверяем: дрон не может пролететь
     * больше, чем позволяет его модель (с запасом на форсаж и пропуски пакетов).
     */
    boolean acceptMove(double x, double y, double z, float yaw, float pitch) {
        long now = level().getGameTime();
        long ticks = Math.max(1, Math.min(10, now - lastMove));
        lastMove = now;
        double limit = (droneType().maxSpeed * 1.9 + 0.6) * ticks;
        if (position().distanceToSqr(x, y, z) > limit * limit) {
            return false;
        }
        setPos(x, y, z);
        setYRot(yaw);
        setXRot(pitch);
        entityData.set(MOTORS, true);
        return true;
    }

    void touchMove() {
        lastMove = level().getGameTime();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpPitch = pitch;
        lerpSteps = 3;
    }

    // --- урон, подобрать ------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved() || courier()) {
            return false;
        }
        // Хозяин, ударивший пустой рукой, дрон не ломает — так его подбирают.
        if (source.getEntity() instanceof Player p && ownedBy(p) && p.getMainHandItem().isEmpty()
                && !p.isShiftKeyDown()) {
            return false;
        }
        float hp = health() - amount;
        entityData.set(HEALTH, hp);
        level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.6F, 1.6F);
        if (hp <= 0) {
            wreck();
        }
        return true;
    }

    /** Сбит или разбился: искры, дым, на землю падает сам дрон (с пустой батареей) и груз. */
    void wreck() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 20, 0.3, 0.2, 0.3, 0.2);
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.5F, 1.8F);
        }
        Drones.stop(this, "citylife.drone.wrecked");
        ItemStack item = DroneItem.of(droneType(), 0.0F);
        spawnAtLocation(item);
        if (!cargo().isEmpty()) {
            spawnAtLocation(cargo());
        }
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (courier()) {
            return InteractionResult.PASS;
        }
        if (!ownedBy(player)) {
            player.displayClientMessage(Component.translatable("citylife.drone.not_yours", ownerName)
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (droneType() == DroneType.COURIER && !held.isEmpty() && !(held.getItem() instanceof DroneRemoteItem)
                && cargo().isEmpty()) {
            setCargo(held);
            player.setItemInHand(hand, ItemStack.EMPTY);
            player.displayClientMessage(Component.translatable("citylife.drone.cargo_loaded",
                    cargo().getHoverName()).withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() || held.getItem() instanceof DroneRemoteItem && player.isShiftKeyDown()) {
            pickUp(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Подобрать дрон хозяину в инвентарь (с зарядом и грузом). */
    public void pickUp(Player player) {
        Drones.stop(this, null);
        player.getInventory().placeItemBackInInventory(DroneItem.of(droneType(), battery()));
        if (!cargo().isEmpty()) {
            player.getInventory().placeItemBackInInventory(cargo());
        }
        level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        discard();
    }

    /** Курьер: сбросить груз или взять предмет под собой. */
    void cargoAction(ServerPlayer pilot) {
        if (droneType() != DroneType.COURIER) {
            return;
        }
        if (!cargo().isEmpty()) {
            ItemEntity drop = new ItemEntity(level(), getX(), getY() - 0.4, getZ(), cargo());
            drop.setDeltaMovement(getDeltaMovement().scale(0.5));
            level().addFreshEntity(drop);
            setCargo(ItemStack.EMPTY);
            pilot.displayClientMessage(Component.translatable("citylife.drone.cargo_dropped")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        AABB below = getBoundingBox().inflate(1.2, 0, 1.2).expandTowards(0, -2.5, 0);
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, below)) {
            setCargo(item.getItem());
            item.discard();
            pilot.displayClientMessage(Component.translatable("citylife.drone.cargo_loaded",
                    cargo().getHoverName()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        pilot.displayClientMessage(Component.translatable("citylife.drone.cargo_none")
                .withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public boolean shouldBeSaved() {
        return !courier() && super.shouldBeSaved();   // курьер службы доставки не сохраняется
    }

    /** Сервер останавливается посреди доставки — посылку сразу в руки. */
    public void handOver() {
        if (courier() && getServer() != null) {
            ServerPlayer target = getServer().getPlayerList().getPlayer(deliverTo);
            if (target != null && !cargo().isEmpty()) {
                target.getInventory().placeItemBackInInventory(cargo());
                setCargo(ItemStack.EMPTY);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192 * 192;
    }

    // --- сохранение ---------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        entityData.define(TYPE, (byte) 0);
        entityData.define(OWNER, Optional.empty());
        entityData.define(BATTERY, 1.0F);
        entityData.define(HEALTH, 8.0F);
        entityData.define(PILOT, -1);
        entityData.define(MOTORS, false);
        entityData.define(LIGHTS, false);
        entityData.define(HOMING, false);
        entityData.define(CARGO, ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TYPE, (byte) DroneType.byId(tag.getString("Type")).ordinal());
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
        ownerName = tag.getString("OwnerName");
        setBattery(tag.contains("Battery") ? tag.getFloat("Battery") : 1.0F);
        entityData.set(HEALTH, tag.contains("Health") ? tag.getFloat("Health") : droneType().maxHealth);
        entityData.set(LIGHTS, tag.getBoolean("Lights"));
        entityData.set(CARGO, ItemStack.of(tag.getCompound("Cargo")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Type", droneType().id);
        if (owner() != null) {
            tag.putUUID("Owner", owner());
        }
        tag.putString("OwnerName", ownerName);
        tag.putFloat("Battery", battery());
        tag.putFloat("Health", health());
        tag.putBoolean("Lights", lights());
        if (!cargo().isEmpty()) {
            tag.put("Cargo", cargo().save(new CompoundTag()));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
