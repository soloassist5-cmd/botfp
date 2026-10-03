package dev.lscity.citylife.drone;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Дроны города: «Сокол» (камера), «Стриж» (гоночный FPV) и «Пеликан» (курьер).
 *
 * Пилот берёт пульт и видит мир камерой дрона; сам он при этом стоит на
 * месте, и его видно со стороны. Этот класс держит регистрацию, сеансы
 * управления (кто каким дроном рулит) и действия пилота.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Drones {

    public static final RegistryObject<EntityType<DroneEntity>> DRONE = Registration.ENTITIES.register("drone",
            () -> EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.35F).clientTrackingRange(12).updateInterval(1).build("drone"));

    private static final Map<DroneType, RegistryObject<Item>> ITEMS = new EnumMap<>(DroneType.class);

    static {
        for (DroneType type : DroneType.values()) {
            ITEMS.put(type, Registration.ITEMS.register("drone_" + type.id, () -> new DroneItem(type)));
        }
    }

    public static final RegistryObject<Item> REMOTE = Registration.ITEMS.register("drone_remote",
            DroneRemoteItem::new);

    /** Пилот → дрон, которым он сейчас управляет. */
    private static final Map<UUID, DroneEntity> SESSIONS = new HashMap<>();

    private Drones() {
    }

    public static void init() {
    }

    public static Item item(DroneType type) {
        return ITEMS.get(type).get();
    }

    // --- сеансы управления -----------------------------------------------------------

    public static ServerPlayer pilot(DroneEntity drone) {
        if (drone.pilotId() < 0 || !(drone.level().getEntity(drone.pilotId()) instanceof ServerPlayer p)) {
            return null;
        }
        return SESSIONS.get(p.getUUID()) == drone ? p : null;
    }

    public static DroneEntity session(ServerPlayer player) {
        DroneEntity d = SESSIONS.get(player.getUUID());
        return d == null || d.isRemoved() ? null : d;
    }

    /** Взять управление дроном. */
    public static void start(ServerPlayer player, DroneEntity drone) {
        DroneEntity old = session(player);
        if (old != null) {
            stop(old, null);
        }
        if (drone.pilotId() >= 0) {
            stop(drone, null);
        }
        CityLife.LOG.debug("City Life: {} управляет дроном {}", player.getGameProfile().getName(), drone.getId());
        SESSIONS.put(player.getUUID(), drone);
        drone.setPilot(player.getId());
        drone.touchMove();
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DroneControlPacket(drone.getId(), true));
        player.displayClientMessage(Component.translatable("citylife.drone.connected",
                Component.translatable("item.citylife.drone_" + drone.droneType().id)).withStyle(ChatFormatting.AQUA),
                true);
    }

    /** Отключить пилота; reason — что сказать ему в строке над хотбаром (null — молча). */
    public static void stop(DroneEntity drone, String reason) {
        ServerPlayer pilot = null;
        for (var e : SESSIONS.entrySet()) {
            if (e.getValue() == drone) {
                pilot = drone.getServer() == null ? null : drone.getServer().getPlayerList().getPlayer(e.getKey());
                SESSIONS.remove(e.getKey());
                break;
            }
        }
        drone.setPilot(-1);
        if (pilot != null) {
            final ServerPlayer p = pilot;
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new DroneControlPacket(drone.getId(), false));
            if (reason != null) {
                p.displayClientMessage(Component.translatable(reason).withStyle(ChatFormatting.YELLOW), true);
            }
        }
    }

    /**
     * Дальность связи: у модели своя, но не дальше, чем сервер присылает мир
     * пилоту, — за этой границей у пилота нет ни домов, ни земли.
     */
    public static int range(DroneEntity drone) {
        int view = drone.getServer() == null ? 12 : drone.getServer().getPlayerList().getViewDistance();
        return Math.min(drone.droneType().range, Math.max(48, (view - 1) * 16));
    }

    /** Ближайший свой дрон в пределах его связи. */
    public static DroneEntity nearestOwned(ServerPlayer player) {
        DroneEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (var e : player.serverLevel().getAllEntities()) {
            if (e instanceof DroneEntity d && d.ownedBy(player)) {
                double dist = d.distanceTo(player);
                if (dist <= range(d) && dist < bestDist) {
                    best = d;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    /** Все свои дроны — домой. Сколько отозвано. */
    public static int callHome(ServerPlayer player) {
        int n = 0;
        for (var e : player.serverLevel().getAllEntities()) {
            if (e instanceof DroneEntity d && d.ownedBy(player) && d.battery() > 0) {
                stop(d, null);
                d.setHoming(true);
                n++;
            }
        }
        return n;
    }

    /** Поставить дрон в мир: хозяин, модель, заряд. */
    public static DroneEntity spawn(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player owner,
                                    DroneType type, net.minecraft.world.phys.Vec3 at, float yaw, float battery) {
        DroneEntity drone = DRONE.get().create(level);
        if (drone == null) {
            return null;
        }
        drone.setType(type);
        if (owner != null) {
            drone.setOwner(owner);
        }
        drone.setBattery(battery);
        drone.moveTo(at.x, at.y, at.z, yaw, 0);
        level.addFreshEntity(drone);
        return drone;
    }

    /** Пакет координат от пилота. */
    public static void move(ServerPlayer player, DroneMovePacket p) {
        DroneEntity drone = session(player);
        if (drone == null || drone.getId() != p.id()) {
            return;
        }
        if (!drone.acceptMove(p.x(), p.y(), p.z(), p.yaw(), p.pitch())) {
            // Прыжок дальше возможного: вернуть пилоту настоящую точку.
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new DroneControlPacket(drone.getId(), true, drone.getX(), drone.getY(), drone.getZ()));
        }
        if (p.crash() > 0) {
            drone.hurt(drone.damageSources().flyIntoWall(), p.crash());
        }
    }

    /** Для приложения «Дроны»: свои дроны в этом мире — модель, заряд, где и что делает. */
    public static net.minecraft.nbt.ListTag snapshot(ServerPlayer player) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (var e : player.serverLevel().getAllEntities()) {
            if (e instanceof DroneEntity d && d.ownedBy(player)) {
                CompoundTag t = new CompoundTag();
                t.putInt("id", d.getId());
                t.putString("type", d.droneType().id);
                t.putFloat("battery", d.battery());
                t.putInt("dist", Math.round(d.distanceTo(player)));
                t.putInt("range", range(d));
                t.putString("status", d.pilotId() >= 0 ? "piloted" : d.homing() ? "homing"
                        : d.battery() <= 0 ? "dead" : d.motors() ? "flying" : "landed");
                list.add(t);
            }
        }
        return list;
    }

    private static DroneEntity owned(ServerPlayer player, CompoundTag args) {
        if (player.serverLevel().getEntity(args.getInt("id")) instanceof DroneEntity d && d.ownedBy(player)) {
            return d;
        }
        return null;
    }

    /** Действия: пилота (drone_exit, drone_lights, drone_cargo, drone_return) и приложения. */
    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        DroneEntity drone = session(player);
        if ("drone_home".equals(action)) {
            callHome(player);
            dev.lscity.citylife.net.DeviceServer.handle(player, "refresh", args);
            return;
        }
        if ("drone_fly".equals(action) || "drone_recall".equals(action)) {
            DroneEntity d = owned(player, args);
            if (d != null && "drone_recall".equals(action) && d.battery() > 0) {
                stop(d, null);
                d.setHoming(true);
            } else if (d != null && "drone_fly".equals(action)) {
                if (d.battery() <= 0) {
                    player.displayClientMessage(Component.translatable("citylife.drone.battery_dead")
                            .withStyle(ChatFormatting.RED), true);
                } else if (d.distanceTo(player) > range(d)) {
                    player.displayClientMessage(Component.translatable("citylife.drone.out_of_range")
                            .withStyle(ChatFormatting.YELLOW), true);
                } else {
                    start(player, d);
                    return;
                }
            }
            dev.lscity.citylife.net.DeviceServer.handle(player, "refresh", args);
            return;
        }
        if (drone == null) {
            return;
        }
        switch (action) {
            case "drone_exit" -> stop(drone, null);
            case "drone_lights" -> drone.toggleLights();
            case "drone_cargo" -> drone.cargoAction(player);
            case "drone_return" -> {
                stop(drone, null);
                drone.setHoming(true);
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DroneEntity d = session(player);
            if (d != null) {
                stop(d, null);
                d.setHoming(false);
            }
            SESSIONS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (var level : event.getServer().getAllLevels()) {
            for (var e : level.getAllEntities()) {
                if (e instanceof DroneEntity d && d.courier()) {
                    d.handOver();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && session(player) != null) {
            stop(session(player), "citylife.drone.signal_lost");
        }
    }

    // --- клиентская сторона, нужная сущности -------------------------------------------

    /** Ведёт ли этот дрон локальный игрок (на клиенте). Подменяется клиентом. */
    static java.util.function.Predicate<DroneEntity> localPilot = d -> false;

    public static void setLocalPilot(java.util.function.Predicate<DroneEntity> test) {
        localPilot = test;
    }

    static boolean localPilotOf(DroneEntity drone) {
        return localPilot.test(drone);
    }
}
