package dev.lscity.citylife.data;

import dev.lscity.citylife.CityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Городские данные сервера: банковские счета, сообщения, метки на карте
 * и привязки телефонов к замкам.
 *
 * Хранится в data/citylife_city.dat мира, поэтому переживает перезапуск
 * сервера и не зависит от инвентаря игрока (телефон можно потерять).
 */
public class CityData extends SavedData {
    private static final String NAME = "citylife_city";

    private final Map<UUID, Long> balances = new LinkedHashMap<>();
    private final Map<UUID, List<Message>> inbox = new LinkedHashMap<>();
    private final Map<UUID, List<Waypoint>> waypoints = new LinkedHashMap<>();
    private final Map<UUID, List<BlockPos>> pairedLocks = new LinkedHashMap<>();

    public static CityData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("City Life: нет мира overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(CityData::load, CityData::new, NAME);
    }

    // --- банк ---------------------------------------------------------------

    public long balance(UUID player) {
        Long value = balances.get(player);
        if (value == null) {
            value = CityConfig.CONFIG.startingBalance.get();
            balances.put(player, value);
            setDirty();
        }
        return value;
    }

    public void setBalance(UUID player, long amount) {
        balances.put(player, Math.max(0L, amount));
        setDirty();
    }

    public void deposit(UUID player, long amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(player, balance(player) + amount);
    }

    /** Списать со счёта. Возвращает false, если денег не хватило. */
    public boolean withdraw(UUID player, long amount) {
        if (amount <= 0) {
            return false;
        }
        long current = balance(player);
        if (current < amount) {
            return false;
        }
        setBalance(player, current - amount);
        return true;
    }

    public boolean transfer(UUID from, UUID to, long amount) {
        if (from.equals(to) || amount <= 0 || !withdraw(from, amount)) {
            return false;
        }
        deposit(to, amount);
        return true;
    }

    // --- сообщения ----------------------------------------------------------

    public List<Message> messages(UUID player) {
        return inbox.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public void addMessage(UUID to, Message message) {
        List<Message> list = messages(to);
        list.add(0, message);
        int limit = CityConfig.CONFIG.maxMessages.get();
        while (list.size() > limit) {
            list.remove(list.size() - 1);
        }
        setDirty();
    }

    // --- метки на карте -----------------------------------------------------

    public List<Waypoint> waypoints(UUID player) {
        return waypoints.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public boolean addWaypoint(UUID player, Waypoint point) {
        List<Waypoint> list = waypoints(player);
        if (list.size() >= CityConfig.CONFIG.maxWaypoints.get()) {
            return false;
        }
        list.add(point);
        setDirty();
        return true;
    }

    public boolean removeWaypoint(UUID player, int index) {
        List<Waypoint> list = waypoints(player);
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        setDirty();
        return true;
    }

    // --- привязка замков к телефону ----------------------------------------

    public List<BlockPos> locks(UUID player) {
        return pairedLocks.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public boolean pairLock(UUID player, BlockPos pos) {
        List<BlockPos> list = locks(player);
        BlockPos immutable = pos.immutable();
        if (list.contains(immutable)) {
            return false;
        }
        list.add(immutable);
        setDirty();
        return true;
    }

    public void unpairLock(UUID player, BlockPos pos) {
        if (locks(player).remove(pos.immutable())) {
            setDirty();
        }
    }

    // --- сохранение ---------------------------------------------------------

    public static CityData load(CompoundTag tag) {
        CityData data = new CityData();
        ListTag accounts = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accounts.size(); i++) {
            CompoundTag entry = accounts.getCompound(i);
            data.balances.put(entry.getUUID("id"), entry.getLong("balance"));
        }
        ListTag boxes = tag.getList("inbox", Tag.TAG_COMPOUND);
        for (int i = 0; i < boxes.size(); i++) {
            CompoundTag entry = boxes.getCompound(i);
            UUID owner = entry.getUUID("id");
            List<Message> list = new ArrayList<>();
            ListTag items = entry.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                list.add(Message.load(items.getCompound(j)));
            }
            data.inbox.put(owner, list);
        }
        ListTag marks = tag.getList("waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < marks.size(); i++) {
            CompoundTag entry = marks.getCompound(i);
            UUID owner = entry.getUUID("id");
            List<Waypoint> list = new ArrayList<>();
            ListTag items = entry.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                list.add(Waypoint.load(items.getCompound(j)));
            }
            data.waypoints.put(owner, list);
        }
        ListTag pairs = tag.getList("locks", Tag.TAG_COMPOUND);
        for (int i = 0; i < pairs.size(); i++) {
            CompoundTag entry = pairs.getCompound(i);
            UUID owner = entry.getUUID("id");
            List<BlockPos> list = new ArrayList<>();
            for (long packed : entry.getLongArray("positions")) {
                list.add(BlockPos.of(packed));
            }
            data.pairedLocks.put(owner, list);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag accounts = new ListTag();
        balances.forEach((id, balance) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putLong("balance", balance);
            accounts.add(entry);
        });
        tag.put("accounts", accounts);

        ListTag boxes = new ListTag();
        inbox.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ListTag items = new ListTag();
            list.forEach(message -> items.add(message.save()));
            entry.put("items", items);
            boxes.add(entry);
        });
        tag.put("inbox", boxes);

        ListTag marks = new ListTag();
        waypoints.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ListTag items = new ListTag();
            list.forEach(point -> items.add(point.save()));
            entry.put("items", items);
            marks.add(entry);
        });
        tag.put("waypoints", marks);

        ListTag pairs = new ListTag();
        pairedLocks.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            long[] packed = new long[list.size()];
            for (int i = 0; i < list.size(); i++) {
                packed[i] = list.get(i).asLong();
            }
            entry.putLongArray("positions", packed);
            pairs.add(entry);
        });
        tag.put("locks", pairs);
        return tag;
    }
}
