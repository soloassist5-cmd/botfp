package dev.lscity.citylife.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Жизнь города: чья недвижимость, кому владелец дал ключи, кто в розыске
 * и кто сидит. Отдельно от банка (CityData), чтобы не раздувать один файл.
 *
 * Хранится в data/citylife_life.dat мира.
 */
public class LifeData extends SavedData {
    private static final String NAME = "citylife_life";

    /** Владелец объекта недвижимости. */
    public record Owner(UUID id, String name, long since) {
    }

    private final Map<String, Owner> owners = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> trusted = new LinkedHashMap<>();
    /** Долг по коммуналке: копится, когда на счёте не хватило денег. */
    private final Map<String, Long> debt = new LinkedHashMap<>();
    /** Цена аренды за игровые сутки (0 — не сдаётся) и кто снимает. */
    private final Map<String, Long> rent = new LinkedHashMap<>();
    private final Map<String, Owner> tenants = new LinkedHashMap<>();
    /** Последние игровые сутки, за которые уже взяли плату. */
    private long billedDay = -1;
    private final Map<UUID, String> trustedNames = new LinkedHashMap<>();
    /** Звёзды розыска и момент, когда снимется следующая. */
    private final Map<UUID, Integer> wanted = new LinkedHashMap<>();
    private final Map<UUID, Long> wantedDecay = new LinkedHashMap<>();
    /** До какого игрового тика игрок сидит в камере. */
    private final Map<UUID, Long> jail = new LinkedHashMap<>();
    /** Статистика заработка для приложения «Работа». */
    private final Map<UUID, Long> earned = new LinkedHashMap<>();
    private final Map<UUID, Integer> jobsDone = new LinkedHashMap<>();

    public static LifeData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("City Life: нет мира overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(LifeData::load, LifeData::new, NAME);
    }

    // --- недвижимость ---------------------------------------------------------

    public Owner owner(String unit) {
        return owners.get(unit);
    }

    public Map<String, Owner> owners() {
        return owners;
    }

    public List<String> ownedBy(UUID player) {
        List<String> out = new ArrayList<>();
        owners.forEach((unit, owner) -> {
            if (owner.id().equals(player)) {
                out.add(unit);
            }
        });
        return out;
    }

    public void setOwner(String unit, UUID id, String name, long since) {
        owners.put(unit, new Owner(id, name, since));
        trusted.remove(unit);
        debt.remove(unit);
        rent.remove(unit);
        tenants.remove(unit);
        setDirty();
    }

    public void clearOwner(String unit) {
        owners.remove(unit);
        trusted.remove(unit);
        debt.remove(unit);
        rent.remove(unit);
        tenants.remove(unit);
        setDirty();
    }

    // --- коммуналка и аренда --------------------------------------------------------

    public long debt(String unit) {
        return debt.getOrDefault(unit, 0L);
    }

    public void setDebt(String unit, long amount) {
        if (amount <= 0) {
            debt.remove(unit);
        } else {
            debt.put(unit, amount);
        }
        setDirty();
    }

    public long rent(String unit) {
        return rent.getOrDefault(unit, 0L);
    }

    public void setRent(String unit, long perDay) {
        if (perDay <= 0) {
            rent.remove(unit);
        } else {
            rent.put(unit, perDay);
        }
        setDirty();
    }

    public Owner tenant(String unit) {
        return tenants.get(unit);
    }

    public void setTenant(String unit, UUID id, String name, long since) {
        if (id == null) {
            tenants.remove(unit);
        } else {
            tenants.put(unit, new Owner(id, name, since));
        }
        setDirty();
    }

    /** Что снимает игрок. */
    public List<String> rentedBy(UUID player) {
        List<String> out = new ArrayList<>();
        tenants.forEach((unit, t) -> {
            if (t.id().equals(player)) {
                out.add(unit);
            }
        });
        return out;
    }

    public long billedDay() {
        return billedDay;
    }

    public void setBilledDay(long day) {
        billedDay = day;
        setDirty();
    }

    public Set<UUID> trusted(String unit) {
        return trusted.getOrDefault(unit, Set.of());
    }

    public String trustedName(UUID id) {
        return trustedNames.getOrDefault(id, id.toString().substring(0, 8));
    }

    public boolean trust(String unit, UUID id, String name) {
        trustedNames.put(id, name);
        boolean added = trusted.computeIfAbsent(unit, key -> new LinkedHashSet<>()).add(id);
        setDirty();
        return added;
    }

    public boolean untrust(String unit, UUID id) {
        Set<UUID> set = trusted.get(unit);
        boolean removed = set != null && set.remove(id);
        setDirty();
        return removed;
    }

    /** Свой ли дом для игрока: владелец или ему дали ключи. */
    public boolean mayUse(String unit, UUID player) {
        Owner owner = owners.get(unit);
        if (owner == null || owner.id().equals(player) || trusted(unit).contains(player)) {
            return true;
        }
        Owner tenant = tenants.get(unit);
        return tenant != null && tenant.id().equals(player);
    }

    // --- розыск и тюрьма ------------------------------------------------------------

    public int wanted(UUID player) {
        return wanted.getOrDefault(player, 0);
    }

    public Map<UUID, Integer> allWanted() {
        return wanted;
    }

    public void setWanted(UUID player, int stars, long decayAt) {
        if (stars <= 0) {
            wanted.remove(player);
            wantedDecay.remove(player);
        } else {
            wanted.put(player, Math.min(5, stars));
            wantedDecay.put(player, decayAt);
        }
        setDirty();
    }

    public long wantedDecay(UUID player) {
        return wantedDecay.getOrDefault(player, 0L);
    }

    public long jailUntil(UUID player) {
        return jail.getOrDefault(player, 0L);
    }

    public void setJail(UUID player, long until) {
        if (until <= 0) {
            jail.remove(player);
        } else {
            jail.put(player, until);
        }
        setDirty();
    }

    // --- работа ---------------------------------------------------------------------

    public void recordJob(UUID player, long pay) {
        earned.merge(player, pay, Long::sum);
        jobsDone.merge(player, 1, Integer::sum);
        setDirty();
    }

    public long earned(UUID player) {
        return earned.getOrDefault(player, 0L);
    }

    public int jobsDone(UUID player) {
        return jobsDone.getOrDefault(player, 0);
    }

    // --- сохранение -----------------------------------------------------------------

    public static LifeData load(CompoundTag tag) {
        LifeData data = new LifeData();
        for (Tag item : tag.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            String unit = entry.getString("unit");
            data.owners.put(unit, new Owner(entry.getUUID("id"), entry.getString("name"),
                    entry.getLong("since")));
            if (entry.getLong("debt") > 0) {
                data.debt.put(unit, entry.getLong("debt"));
            }
            if (entry.getLong("rent") > 0) {
                data.rent.put(unit, entry.getLong("rent"));
            }
            if (entry.hasUUID("tenant")) {
                data.tenants.put(unit, new Owner(entry.getUUID("tenant"),
                        entry.getString("tenantName"), entry.getLong("tenantSince")));
            }
            Set<UUID> keys = new LinkedHashSet<>();
            for (Tag key : entry.getList("trusted", Tag.TAG_INT_ARRAY)) {
                keys.add(NbtUtils.loadUUID(key));
            }
            if (!keys.isEmpty()) {
                data.trusted.put(unit, keys);
            }
        }
        data.billedDay = tag.contains("billedDay") ? tag.getLong("billedDay") : -1;
        for (Tag item : tag.getList("names", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            data.trustedNames.put(entry.getUUID("id"), entry.getString("name"));
        }
        for (Tag item : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            UUID id = entry.getUUID("id");
            if (entry.getInt("wanted") > 0) {
                data.wanted.put(id, entry.getInt("wanted"));
                data.wantedDecay.put(id, entry.getLong("decay"));
            }
            if (entry.getLong("jail") > 0) {
                data.jail.put(id, entry.getLong("jail"));
            }
            if (entry.getLong("earned") > 0) {
                data.earned.put(id, entry.getLong("earned"));
                data.jobsDone.put(id, entry.getInt("jobs"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        owners.forEach((unit, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("unit", unit);
            entry.putUUID("id", owner.id());
            entry.putString("name", owner.name());
            entry.putLong("since", owner.since());
            entry.putLong("debt", debt(unit));
            entry.putLong("rent", rent(unit));
            Owner tenant = tenants.get(unit);
            if (tenant != null) {
                entry.putUUID("tenant", tenant.id());
                entry.putString("tenantName", tenant.name());
                entry.putLong("tenantSince", tenant.since());
            }
            ListTag keys = new ListTag();
            trusted(unit).forEach(id -> keys.add(NbtUtils.createUUID(id)));
            entry.put("trusted", keys);
            list.add(entry);
        });
        tag.put("owners", list);
        tag.putLong("billedDay", billedDay);
        ListTag names = new ListTag();
        trustedNames.forEach((id, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("name", name);
            names.add(entry);
        });
        tag.put("names", names);
        Set<UUID> people = new LinkedHashSet<>();
        people.addAll(wanted.keySet());
        people.addAll(jail.keySet());
        people.addAll(earned.keySet());
        ListTag players = new ListTag();
        for (UUID id : people) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putInt("wanted", wanted(id));
            entry.putLong("decay", wantedDecay(id));
            entry.putLong("jail", jailUntil(id));
            entry.putLong("earned", earned(id));
            entry.putInt("jobs", jobsDone(id));
            players.add(entry);
        }
        tag.put("players", players);
        return tag;
    }
}
