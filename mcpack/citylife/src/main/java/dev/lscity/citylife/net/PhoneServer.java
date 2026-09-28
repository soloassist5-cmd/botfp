package dev.lscity.citylife.net;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.block.SmartLockBlock;
import dev.lscity.citylife.block.SmartLockBlockEntity;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.Message;
import dev.lscity.citylife.data.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * Серверная часть телефона: сбор снимка состояния и обработка действий.
 * Клиент только рисует и просит — решения принимает сервер.
 */
public final class PhoneServer {

    private PhoneServer() {
    }

    // --- снимок -------------------------------------------------------------

    public static CompoundTag snapshot(ServerPlayer player) {
        CityData data = CityData.get(player.server);
        UUID id = player.getUUID();
        CompoundTag tag = new CompoundTag();
        tag.putLong("balance", data.balance(id));
        tag.putInt("x", player.getBlockX());
        tag.putInt("y", player.getBlockY());
        tag.putInt("z", player.getBlockZ());
        tag.putInt("maxWaypoints", CityConfig.CONFIG.maxWaypoints.get());
        tag.putLong("daytime", player.level().getDayTime() % 24000L);
        tag.putLong("cash", dev.lscity.citylife.economy.Money.cash(player));
        tag.putString("owner", player.getGameProfile().getName());
        tag.putString("wallpaper", data.wallpaper(id));
        tag.putInt("lockRange", CityConfig.CONFIG.phoneLockRange.get());

        ListTag messages = new ListTag();
        for (Message message : data.messages(id)) {
            CompoundTag entry = new CompoundTag();
            entry.putString("from", message.fromName());
            entry.putString("text", message.text());
            entry.putLong("time", message.time());
            messages.add(entry);
        }
        tag.put("messages", messages);

        ListTag contacts = new ListTag();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(id)) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("name", other.getGameProfile().getName());
            entry.putUUID("id", other.getUUID());
            entry.putInt("dist", (int) Math.sqrt(other.distanceToSqr(player)));
            entry.putBoolean("sameLevel", other.level() == player.level());
            contacts.add(entry);
        }
        tag.put("contacts", contacts);

        ListTag apps = new ListTag();
        for (String app : data.apps(id)) {
            apps.add(net.minecraft.nbt.StringTag.valueOf(app));
        }
        tag.put("apps", apps);

        // Меток в городе полсотни, а на экране помещается пять: показываем
        // их по возрастанию расстояния, чтобы ближайший банкомат или метро
        // был первой строкой, а не двадцатой.
        List<Waypoint> points = new java.util.ArrayList<>(
                dev.lscity.citylife.data.CityLandmarks.ALL);
        points.addAll(data.waypoints(id));
        List<CompoundTag> rows = new java.util.ArrayList<>();
        for (Waypoint point : points) {
            rows.add(withDistance(point, player));
        }
        rows.sort(java.util.Comparator.comparingInt(row -> row.getInt("dist")));
        ListTag waypoints = new ListTag();
        rows.forEach(waypoints::add);
        tag.put("waypoints", waypoints);

        Waypoint route = data.route(id);
        if (route != null) {
            tag.put("route", withDistance(route, player));
        }

        ListTag locks = new ListTag();
        for (BlockPos pos : List.copyOf(data.locks(id))) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("dist", (int) Math.sqrt(pos.distToCenterSqr(player.position())));
            BlockState state = player.level().getBlockState(pos);
            boolean loaded = player.level().isLoaded(pos);
            if (loaded && !(state.getBlock() instanceof SmartLockBlock)) {
                // Замка больше нет — чистим привязку.
                data.unpairLock(id, pos);
                continue;
            }
            entry.putBoolean("open", loaded && state.hasProperty(SmartLockBlock.POWERED)
                    && state.getValue(SmartLockBlock.POWERED));
            entry.putBoolean("loaded", loaded);
            String label = "Замок";
            boolean owner = false;
            if (loaded && player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock) {
                label = lock.getLabel();
                owner = lock.isOwner(id);
                if (owner) {
                    ListTag log = new ListTag();
                    lock.getLog().forEach(line ->
                            log.add(net.minecraft.nbt.StringTag.valueOf(line)));
                    entry.put("log", log);
                }
            }
            entry.putString("label", label);
            entry.putBoolean("owner", owner);
            locks.add(entry);
        }
        tag.put("locks", locks);
        return tag;
    }

    // --- действия -----------------------------------------------------------

    /** Метка вместе с расстоянием до игрока — список сортируется по близости. */
    private static CompoundTag withDistance(Waypoint point, ServerPlayer player) {
        CompoundTag entry = point.save();
        double dx = point.x() - player.getX();
        double dz = point.z() - player.getZ();
        entry.putInt("dist", (int) Math.sqrt(dx * dx + dz * dz));
        return entry;
    }

    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        boolean holdsPhone = player.getMainHandItem().is(Registration.SMARTPHONE.get())
                || player.getOffhandItem().is(Registration.SMARTPHONE.get());
        if (!holdsPhone) {
            player.sendSystemMessage(Component.translatable("citylife.phone.needed")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        CityData data = CityData.get(player.server);
        UUID id = player.getUUID();

        switch (action) {
            case "refresh" -> {
                // Ничего не меняем, ниже уйдёт свежий снимок.
            }
            case "msg" -> sendMessage(player, data, args);
            case "pay" -> pay(player, data, args);
            case "wp_add" -> {
                String name = args.getString("name").trim();
                if (name.isEmpty()) {
                    name = "Метка";
                }
                if (data.addWaypoint(id, Waypoint.of(name.substring(0, Math.min(20, name.length())),
                        player.blockPosition()))) {
                    player.sendSystemMessage(Component.translatable("citylife.map.added", name));
                } else {
                    player.sendSystemMessage(Component.translatable("citylife.map.full")
                            .withStyle(ChatFormatting.RED));
                }
            }
            case "wp_del" -> {
                // Городские метки не удаляем: индекс приходит уже из личного списка.
                data.removeWaypoint(id, args.getInt("index"));
                Waypoint active = data.route(id);
                if (active != null && !active.city()) {
                    data.setRoute(id, null);
                    Net.sendRoute(player, null);
                }
            }
            case "app_install" -> {
                if (data.installApp(id, args.getString("app"))) {
                    player.sendSystemMessage(Component.translatable("citylife.market.installed",
                            Component.translatable("citylife.app." + args.getString("app"))));
                }
            }
            case "app_remove" -> data.removeApp(id, args.getString("app"));
            case "wallpaper" -> data.setWallpaper(id, args.getString("id"));
            case "nav_set" -> {
                Waypoint point = Waypoint.load(args.getCompound("point"));
                data.setRoute(id, point);
                Net.sendRoute(player, point);
                player.displayClientMessage(Component.translatable("citylife.nav.started",
                        point.name()), true);
            }
            case "nav_stop" -> {
                data.setRoute(id, null);
                Net.sendRoute(player, null);
            }
            case "lock_toggle" -> lockToggle(player, data, args);
            case "lock_unpair" -> data.unpairLock(id, BlockPos.of(args.getLong("pos")));
            case "lock_grant" -> lockGrant(player, args);
            case "lock_pin" -> lockSetPin(player, args);
            case "pin_try" -> pinTry(player, data, args);
            case "sos" -> emergency(player, args.getString("kind"));
            default -> {
                // Неизвестное действие игнорируем — клиент мог быть от другой версии.
            }
        }
        Net.syncPhone(player);
    }

    private static void sendMessage(ServerPlayer player, CityData data, CompoundTag args) {
        if (!args.hasUUID("target")) {
            return;
        }
        String text = args.getString("text").trim();
        if (text.isEmpty()) {
            return;
        }
        text = text.substring(0, Math.min(180, text.length()));
        ServerPlayer target = player.server.getPlayerList().getPlayer(args.getUUID("target"));
        if (target == null) {
            player.sendSystemMessage(Component.translatable("citylife.msg.offline")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String from = player.getGameProfile().getName();
        data.addMessage(target.getUUID(),
                new Message(from, text, player.level().getGameTime()));
        target.sendSystemMessage(Component.translatable("citylife.msg.incoming", from, text)
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable("citylife.msg.sent",
                target.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
    }

    private static void pay(ServerPlayer player, CityData data, CompoundTag args) {
        if (!args.hasUUID("target")) {
            return;
        }
        long amount = args.getLong("amount");
        if (amount <= 0) {
            return;
        }
        ServerPlayer target = player.server.getPlayerList().getPlayer(args.getUUID("target"));
        if (target == null) {
            player.sendSystemMessage(Component.translatable("citylife.bank.offline")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!data.transfer(player.getUUID(), target.getUUID(), amount)) {
            player.sendSystemMessage(Component.translatable("citylife.bank.no_money")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.translatable("citylife.bank.sent", amount,
                target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.translatable("citylife.bank.received", amount,
                player.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
    }

    private static void lockToggle(ServerPlayer player, CityData data, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        int range = CityConfig.CONFIG.phoneLockRange.get();
        if (pos.distToCenterSqr(player.position()) > (double) range * range) {
            player.sendSystemMessage(Component.translatable("citylife.lock.too_far", range)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SmartLockBlock lock)
                || !(level.getBlockEntity(pos) instanceof SmartLockBlockEntity entity)) {
            data.unpairLock(player.getUUID(), pos);
            return;
        }
        if (!entity.isAllowed(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("citylife.lock.no_access")
                    .withStyle(ChatFormatting.RED));
            entity.note("Отказ (телефон): " + player.getGameProfile().getName());
            return;
        }
        lock.toggle(level, pos, state, entity,
                player.getGameProfile().getName() + " (телефон)", true);
    }

    private static void lockGrant(ServerPlayer player, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!args.hasUUID("target")
                || !(player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock)) {
            return;
        }
        if (!lock.isOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("citylife.lock.not_owner")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        UUID target = args.getUUID("target");
        ServerPlayer targetPlayer = player.server.getPlayerList().getPlayer(target);
        String name = targetPlayer == null ? target.toString().substring(0, 8)
                : targetPlayer.getGameProfile().getName();
        if (lock.isAllowed(target)) {
            lock.deny(target);
            lock.note("Доступ снят: " + name);
            player.sendSystemMessage(Component.translatable("citylife.lock.revoked", name));
        } else {
            lock.allow(target);
            lock.note("Доступ выдан: " + name);
            player.sendSystemMessage(Component.translatable("citylife.lock.granted", name));
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(Component.translatable("citylife.lock.you_granted",
                        lock.getLabel(), player.getGameProfile().getName()));
            }
        }
    }

    private static void lockSetPin(ServerPlayer player, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!(player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock)) {
            return;
        }
        if (!lock.isOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("citylife.lock.not_owner")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String pin = args.getString("pin");
        lock.setPin(pin);
        lock.note(pin.isEmpty() ? "Код снят" : "Код изменён");
        player.sendSystemMessage(Component.translatable(
                pin.isEmpty() ? "citylife.lock.pin_cleared" : "citylife.lock.pin_set"));
    }

    private static void pinTry(ServerPlayer player, CityData data, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!(player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock)) {
            return;
        }
        if (pos.distToCenterSqr(player.position()) > 64.0D) {
            return;
        }
        String code = args.getString("code");
        if (lock.checkPin(code)) {
            lock.allow(player.getUUID());
            data.pairLock(player.getUUID(), pos);
            lock.note("Код верный: " + player.getGameProfile().getName());
            player.sendSystemMessage(Component.translatable("citylife.lock.pin_ok")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lock.note("Неверный код: " + player.getGameProfile().getName());
            player.sendSystemMessage(Component.translatable("citylife.lock.pin_bad")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void emergency(ServerPlayer player, String kind) {
        String service = switch (kind) {
            case "medic" -> "Скорая";
            case "fire" -> "Пожарная";
            default -> "Полиция";
        };
        Component message = Component.translatable("citylife.sos.broadcast", service,
                player.getGameProfile().getName(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ())
                .withStyle(ChatFormatting.RED);
        if (CityConfig.CONFIG.emergencyToEveryone.get()) {
            player.server.getPlayerList().broadcastSystemMessage(message, false);
        } else {
            player.server.getPlayerList().getPlayers().stream()
                    .filter(candidate -> player.server.getPlayerList().isOp(candidate.getGameProfile()))
                    .forEach(candidate -> candidate.sendSystemMessage(message));
            player.sendSystemMessage(message);
        }
    }
}
