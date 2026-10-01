package dev.lscity.citylife.vehicle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.city.Online;
import dev.lscity.citylife.city.Wanted;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.Mail;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Свои машины (MrCrayfish's Vehicle).
 *
 * Машина из ящика достаётся тому, кто её распаковал (ближайший игрок в
 * момент появления). Хозяин запирает и отпирает её из приложения «Мой
 * транспорт» или командой /car, даёт ключ другу и строит маршрут к ней.
 *
 * Запертая машина не пускает чужих ни за руль, ни пассажиром, а удар по ней
 * — попытка угона: звезда розыска и сообщение хозяину. Незапертую можно
 * занять пассажиром, но чужой, севший первым (за руль), — угонщик.
 * Машины нарядов 112 ничьи и под это не попадают.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Garage {

    private static final String VEHICLE_CLASS = "com.mrcrayfish.vehicle.entity.VehicleEntity";
    private static final String SERVICE_TAG = "citylife_crew";
    private static final double CLAIM_RANGE = 12.0D;

    private Garage() {
    }

    /** Машина мода Vehicle (любая: наземная, лодка, самолёт, прицеп). */
    public static boolean isVehicle(Entity entity) {
        for (Class<?> c = entity.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
            if (VEHICLE_CLASS.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Component model(GarageData.Car car) {
        return Component.translatable(car.model);
    }

    // --- хозяин появляется вместе с машиной ------------------------------------------

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !isVehicle(entity)
                || entity.getTags().contains(SERVICE_TAG)) {
            return;
        }
        MinecraftServer server = entity.getServer();
        if (server == null || GarageData.get(server).car(entity.getUUID()) != null) {
            return;
        }
        Player nearest = event.getLevel().getNearestPlayer(entity, CLAIM_RANGE);
        if (nearest instanceof ServerPlayer player && !player.isSpectator()) {
            claim(player, entity);
        }
    }

    /** Записать машину на игрока. */
    public static GarageData.Car claim(ServerPlayer player, Entity vehicle) {
        GarageData data = GarageData.get(player.server);
        GarageData.Car car = data.add(vehicle.getUUID(), player.getUUID(),
                player.getGameProfile().getName(), vehicle.getType().getDescriptionId());
        remember(car, vehicle);
        player.displayClientMessage(Component.translatable("citylife.car.claimed", model(car))
                .withStyle(ChatFormatting.GREEN), false);
        return car;
    }

    private static void remember(GarageData.Car car, Entity vehicle) {
        car.dimension = vehicle.level().dimension().location().toString();
        car.x = vehicle.getX();
        car.y = vehicle.getY();
        car.z = vehicle.getZ();
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || !isVehicle(entity) || entity.getServer() == null) {
            return;
        }
        GarageData data = GarageData.get(entity.getServer());
        GarageData.Car car = data.car(entity.getUUID());
        if (car == null) {
            return;
        }
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            // Разбили или разобрали — машины больше нет.
            data.remove(car.id);
        } else {
            remember(car, entity);
            data.setDirty();
        }
    }

    // --- замок и угон ---------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide()
                || !(event.getEntityMounting() instanceof ServerPlayer player)
                || !isVehicle(event.getEntityBeingMounted())) {
            return;
        }
        Entity vehicle = event.getEntityBeingMounted();
        GarageData.Car car = GarageData.get(player.server).car(vehicle.getUUID());
        if (car == null) {
            return;
        }
        // Запертая машина не пускает никого — и хозяина тоже, пока он её не откроет.
        if (car.locked) {
            event.setCanceled(true);
            player.displayClientMessage(car.allows(player.getUUID())
                    ? Component.translatable("citylife.car.locked_own", model(car))
                    .withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("citylife.car.locked_by", car.ownerName)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (car.allows(player.getUUID())) {
            return;
        }
        if (vehicle.getPassengers().isEmpty()) {
            // Первый севший — за рулём: без ключа это угон.
            Wanted.crime(player, 1, "citylife.wanted.car_theft");
            alarm(player, car, vehicle);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isVehicle(event.getTarget())) {
            return;
        }
        GarageData.Car car = GarageData.get(player.server).car(event.getTarget().getUUID());
        if (car == null || car.allows(player.getUUID())) {
            return;
        }
        // Чужую машину нельзя ни разбить, ни разобрать.
        event.setCanceled(true);
        if (car.locked) {
            Wanted.crime(player, 1, "citylife.wanted.car_breakin");
            alarm(player, car, event.getTarget());
        } else {
            player.displayClientMessage(Component.translatable("citylife.car.not_yours",
                    car.ownerName).withStyle(ChatFormatting.RED), true);
        }
    }

    /** Чужой не заправляет, не красит и не лезет в багажник запертой машины. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        guardInteract(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        guardInteract(event, event.getTarget());
    }

    private static void guardInteract(PlayerInteractEvent event, Entity target) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isVehicle(target)) {
            return;
        }
        GarageData.Car car = GarageData.get(player.server).car(target.getUUID());
        if (car != null && car.locked && !car.allows(player.getUUID())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("citylife.car.locked_by",
                    car.ownerName).withStyle(ChatFormatting.RED), true);
        }
    }

    private static void alarm(ServerPlayer thief, GarageData.Car car, Entity vehicle) {
        vehicle.level().playSound(null, vehicle.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.NEUTRAL, 2.0F, 1.6F);
        ServerPlayer owner = Online.get(thief.server, car.owner);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("citylife.car.alarm", model(car),
                    thief.getGameProfile().getName()).withStyle(ChatFormatting.RED));
        } else {
            CityData.get(thief.server).deliverMail(car.owner, new Mail(new UUID(0L, 0L),
                    Texts.ru("citylife.car.alarm_from"), Texts.ru("citylife.car.alarm_mail",
                    thief.getGameProfile().getName(), (int) vehicle.getX() + " " + (int) vehicle.getZ()),
                    thief.level().getGameTime(), false));
        }
    }

    // --- где машина -----------------------------------------------------------------

    /** Раз в 5 секунд обновляем место машин, которые сейчас в загруженных чанках. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCount() % 100 != 0) {
            return;
        }
        GarageData data = GarageData.get(server);
        for (GarageData.Car car : data.all()) {
            Entity vehicle = find(server, car);
            if (vehicle != null && vehicle.distanceToSqr(car.x, car.y, car.z) > 1.0D) {
                remember(car, vehicle);
                data.setDirty();
            }
        }
    }

    public static Entity find(MinecraftServer server, GarageData.Car car) {
        ServerLevel level = level(server, car);
        return level == null ? null : level.getEntity(car.id);
    }

    private static ServerLevel level(MinecraftServer server, GarageData.Car car) {
        if (car.dimension == null || car.dimension.isEmpty()) {
            return server.overworld();
        }
        ResourceLocation dim = ResourceLocation.tryParse(car.dimension);
        return dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
    }

    // --- действия хозяина -----------------------------------------------------------

    private static GarageData.Car pick(ServerPlayer player, int index) {
        List<GarageData.Car> mine = GarageData.get(player.server).of(player.getUUID()).stream()
                .filter(c -> c.owner.equals(player.getUUID())).toList();
        if (mine.isEmpty()) {
            player.displayClientMessage(Component.translatable("citylife.car.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return null;
        }
        if (index > 0) {
            return index <= mine.size() ? mine.get(index - 1) : null;
        }
        // Без номера — ближайшая.
        GarageData.Car best = mine.get(0);
        for (GarageData.Car car : mine) {
            if (player.distanceToSqr(car.x, car.y, car.z) < player.distanceToSqr(best.x, best.y, best.z)) {
                best = car;
            }
        }
        return best;
    }

    public static GarageData.Car byId(ServerPlayer player, String id) {
        try {
            GarageData.Car car = GarageData.get(player.server).car(UUID.fromString(id));
            return car != null && car.allows(player.getUUID()) ? car : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static int setLocked(ServerPlayer player, GarageData.Car car, boolean locked) {
        if (car == null || !car.owner.equals(player.getUUID())) {
            return 0;
        }
        car.locked = locked;
        GarageData.get(player.server).setDirty();
        Entity vehicle = find(player.server, car);
        if (vehicle != null) {
            vehicle.level().playSound(null, vehicle.blockPosition(), SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.NEUTRAL, 0.8F, locked ? 1.8F : 1.4F);
        }
        player.displayClientMessage(Component.translatable(locked ? "citylife.car.locked"
                : "citylife.car.unlocked", model(car)).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    public static int route(ServerPlayer player, GarageData.Car car) {
        if (car == null) {
            return 0;
        }
        Waypoint point = new Waypoint(Texts.ru("citylife.car.route"), (int) Math.floor(car.x),
                (int) Math.floor(car.y), (int) Math.floor(car.z), "pin", false);
        CityData.get(player.server).setRoute(player.getUUID(), point);
        Net.sendRoute(player, point);
        player.displayClientMessage(Component.translatable("citylife.car.route_set", model(car),
                (int) Math.sqrt(player.distanceToSqr(car.x, car.y, car.z))), true);
        return 1;
    }

    public static int trust(ServerPlayer player, GarageData.Car car, ServerPlayer friend, boolean give) {
        if (car == null || !car.owner.equals(player.getUUID()) || friend == player) {
            return 0;
        }
        if (give) {
            car.trusted.add(friend.getUUID());
            car.trustedNames.put(friend.getUUID(), friend.getGameProfile().getName());
            friend.sendSystemMessage(Component.translatable("citylife.car.key_got",
                    player.getGameProfile().getName(), model(car)).withStyle(ChatFormatting.GREEN));
        } else {
            car.trusted.remove(friend.getUUID());
            car.trustedNames.remove(friend.getUUID());
        }
        GarageData.get(player.server).setDirty();
        player.displayClientMessage(Component.translatable(give ? "citylife.car.key_given"
                : "citylife.car.key_taken", friend.getGameProfile().getName()), false);
        return 1;
    }

    /** Для приложения «Мой транспорт». */
    public static ListTag snapshot(ServerPlayer player) {
        ListTag list = new ListTag();
        for (GarageData.Car car : GarageData.get(player.server).of(player.getUUID())) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", car.id.toString());
            entry.putString("model", car.model);
            entry.putBoolean("mine", car.owner.equals(player.getUUID()));
            entry.putString("owner", car.ownerName);
            entry.putBoolean("locked", car.locked);
            boolean here = player.level().dimension().location().toString().equals(car.dimension);
            entry.putInt("distance", here ? (int) Math.sqrt(player.distanceToSqr(car.x, car.y, car.z)) : -1);
            ListTag keys = new ListTag();
            car.trustedNames.values().forEach(n -> keys.add(net.minecraft.nbt.StringTag.valueOf(n)));
            entry.put("keys", keys);
            list.add(entry);
        }
        return list;
    }

    // --- окно «Мой транспорт» (клавиша K) ---------------------------------------------

    /** Снимок для окна: машины игрока, на какой он сидит, кто рядом (дать ключ). */
    public static CompoundTag panel(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.put("cars", snapshot(player));
        if (player.getVehicle() != null) {
            tag.putString("riding", player.getVehicle().getUUID().toString());
        }
        ListTag people = new ListTag();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player && other.level() == player.level() && other.distanceTo(player) < 64) {
                CompoundTag p = new CompoundTag();
                p.putString("name", other.getGameProfile().getName());
                p.putUUID("id", other.getUUID());
                people.add(p);
            }
        }
        tag.put("people", people);
        return tag;
    }

    /** Кнопки окна: garage_open, garage_lock, garage_unlock, garage_route, garage_horn, garage_trust. */
    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        GarageData.Car car = args.contains("id") ? byId(player, args.getString("id")) : null;
        switch (action) {
            case "garage_lock", "garage_unlock" -> {
                if (car != null) {
                    setLocked(player, car, "garage_lock".equals(action));
                }
            }
            case "garage_route" -> {
                if (car != null) {
                    route(player, car);
                }
            }
            case "garage_horn" -> {
                Entity vehicle = car == null ? null : find(player.server, car);
                if (vehicle != null) {
                    vehicle.level().playSound(null, vehicle.blockPosition(),
                            SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.NEUTRAL, 3.0F, 1.2F);
                    ((ServerLevel) vehicle.level()).sendParticles(
                            net.minecraft.core.particles.ParticleTypes.END_ROD, vehicle.getX(),
                            vehicle.getY() + 2.2D, vehicle.getZ(), 12, 0.3D, 0.6D, 0.3D, 0.02D);
                    player.displayClientMessage(Component.translatable("citylife.car.horn",
                            model(car), (int) player.distanceTo(vehicle)).withStyle(ChatFormatting.AQUA),
                            true);
                } else if (car != null) {
                    player.displayClientMessage(Component.translatable("citylife.car.too_far")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
            case "garage_trust" -> {
                ServerPlayer friend = args.hasUUID("friend")
                        ? player.server.getPlayerList().getPlayer(args.getUUID("friend")) : null;
                if (car != null && friend != null) {
                    trust(player, car, friend, !car.trusted.contains(friend.getUUID()));
                }
            }
            default -> {
            }
        }
        dev.lscity.citylife.net.Net.sendPanel(player, "garage", panel(player), "garage_open".equals(action));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("car")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("lock").executes(ctx -> lockCmd(ctx.getSource(), 0, true))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> lockCmd(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n"), true))))
                .then(Commands.literal("unlock").executes(ctx -> lockCmd(ctx.getSource(), 0, false))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> lockCmd(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n"), false))))
                .then(Commands.literal("route").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    return route(p, pick(p, 0));
                }).then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    return route(p, pick(p, IntegerArgumentType.getInteger(ctx, "n")));
                })))
                .then(Commands.literal("trust").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return trust(p, pick(p, 0), EntityArgument.getPlayer(ctx, "player"), true);
                        })))
                .then(Commands.literal("untrust").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return trust(p, pick(p, 0), EntityArgument.getPlayer(ctx, "player"), false);
                        }))));
    }

    private static int lockCmd(CommandSourceStack source, int n, boolean lock)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        return setLocked(p, pick(p, n), lock);
    }

    private static int list(ServerPlayer player) {
        List<GarageData.Car> cars = GarageData.get(player.server).of(player.getUUID());
        if (cars.isEmpty()) {
            player.displayClientMessage(Component.translatable("citylife.car.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        int n = 0;
        for (GarageData.Car car : cars) {
            boolean mine = car.owner.equals(player.getUUID());
            player.sendSystemMessage(Component.literal((mine ? ++n + ". " : "· ")).append(model(car))
                    .append(Component.translatable(car.locked ? "citylife.car.state_locked"
                            : "citylife.car.state_open"))
                    .append(" · " + (int) Math.sqrt(player.distanceToSqr(car.x, car.y, car.z)) + " м")
                    .append(mine ? Component.empty() : Component.translatable("citylife.car.guest",
                            car.ownerName)));
        }
        return cars.size();
    }
}
