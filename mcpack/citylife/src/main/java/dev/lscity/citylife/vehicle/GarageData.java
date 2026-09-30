package dev.lscity.citylife.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 * Чьи машины: владелец, замок, кому дан ключ и где машину видели последний
 * раз (чтобы навигатор нашёл её и в невыгруженном квартале).
 *
 * Хранится в data/citylife_garage.dat мира.
 */
public class GarageData extends SavedData {
    private static final String NAME = "citylife_garage";

    /** Машина игрока. model — ключ перевода типа сущности (переводит клиент). */
    public static final class Car {
        public final UUID id;
        public UUID owner;
        public String ownerName;
        public final String model;
        public boolean locked;
        public final Set<UUID> trusted = new LinkedHashSet<>();
        public final Map<UUID, String> trustedNames = new LinkedHashMap<>();
        public String dimension;
        public double x;
        public double y;
        public double z;

        Car(UUID id, UUID owner, String ownerName, String model) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.model = model;
        }

        public boolean allows(UUID player) {
            return owner.equals(player) || trusted.contains(player);
        }
    }

    private final Map<UUID, Car> cars = new LinkedHashMap<>();

    public static GarageData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("City Life: нет мира overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(GarageData::load, GarageData::new, NAME);
    }

    public Car car(UUID vehicle) {
        return cars.get(vehicle);
    }

    public Car add(UUID vehicle, UUID owner, String ownerName, String model) {
        Car car = new Car(vehicle, owner, ownerName, model);
        cars.put(vehicle, car);
        setDirty();
        return car;
    }

    public void remove(UUID vehicle) {
        if (cars.remove(vehicle) != null) {
            setDirty();
        }
    }

    public List<Car> of(UUID player) {
        List<Car> out = new ArrayList<>();
        for (Car car : cars.values()) {
            if (car.allows(player)) {
                out.add(car);
            }
        }
        // Свои — первыми.
        out.sort((a, b) -> Boolean.compare(!a.owner.equals(player), !b.owner.equals(player)));
        return out;
    }

    public java.util.Collection<Car> all() {
        return cars.values();
    }

    // --- сохранение -----------------------------------------------------------------

    public static GarageData load(CompoundTag tag) {
        GarageData data = new GarageData();
        for (Tag item : tag.getList("cars", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            Car car = new Car(entry.getUUID("id"), entry.getUUID("owner"),
                    entry.getString("ownerName"), entry.getString("model"));
            car.locked = entry.getBoolean("locked");
            car.dimension = entry.getString("dim");
            car.x = entry.getDouble("x");
            car.y = entry.getDouble("y");
            car.z = entry.getDouble("z");
            for (Tag key : entry.getList("trusted", Tag.TAG_COMPOUND)) {
                CompoundTag k = (CompoundTag) key;
                car.trusted.add(k.getUUID("id"));
                car.trustedNames.put(k.getUUID("id"), k.getString("name"));
            }
            data.cars.put(car.id, car);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Car car : cars.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", car.id);
            entry.putUUID("owner", car.owner);
            entry.putString("ownerName", car.ownerName);
            entry.putString("model", car.model);
            entry.putBoolean("locked", car.locked);
            entry.putString("dim", car.dimension == null ? "" : car.dimension);
            entry.putDouble("x", car.x);
            entry.putDouble("y", car.y);
            entry.putDouble("z", car.z);
            ListTag keys = new ListTag();
            for (UUID key : car.trusted) {
                CompoundTag k = new CompoundTag();
                k.putUUID("id", key);
                k.putString("name", car.trustedNames.getOrDefault(key, ""));
                keys.add(k);
            }
            entry.put("trusted", keys);
            list.add(entry);
        }
        tag.put("cars", list);
        return tag;
    }
}
