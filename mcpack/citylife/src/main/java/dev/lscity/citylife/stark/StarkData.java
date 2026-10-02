package dev.lscity.citylife.stark;

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
 * Охрана башни STARK: у кого есть допуск, включена ли охрана и журнал
 * событий для пульта и мониторов.
 *
 * Хранится в data/citylife_stark.dat мира.
 */
public class StarkData extends SavedData {
    private static final String NAME = "citylife_stark";
    private static final int LOG_SIZE = 24;

    /** Строка журнала: игровое время и текст, уже по-русски. */
    public record Entry(long time, String text, boolean alarm) {
    }

    private final Map<UUID, String> access = new LinkedHashMap<>();
    private final List<Entry> log = new ArrayList<>();
    private boolean armed = true;

    public static StarkData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("City Life: нет мира overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(StarkData::load, StarkData::new, NAME);
    }

    public boolean cleared(UUID player) {
        return access.containsKey(player);
    }

    public Map<UUID, String> access() {
        return access;
    }

    public void grant(UUID player, String name) {
        access.put(player, name);
        setDirty();
    }

    public void revoke(UUID player) {
        access.remove(player);
        setDirty();
    }

    public boolean armed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        setDirty();
    }

    public List<Entry> log() {
        return log;
    }

    public void log(long time, String text, boolean alarm) {
        log.add(0, new Entry(time, text, alarm));
        while (log.size() > LOG_SIZE) {
            log.remove(log.size() - 1);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag people = new ListTag();
        access.forEach((id, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("name", name);
            people.add(entry);
        });
        tag.put("access", people);
        ListTag lines = new ListTag();
        for (Entry entry : log) {
            CompoundTag line = new CompoundTag();
            line.putLong("t", entry.time());
            line.putString("s", entry.text());
            line.putBoolean("a", entry.alarm());
            lines.add(line);
        }
        tag.put("log", lines);
        tag.putBoolean("armed", armed);
        return tag;
    }

    public static StarkData load(CompoundTag tag) {
        StarkData data = new StarkData();
        for (Tag t : tag.getList("access", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            data.access.put(entry.getUUID("id"), entry.getString("name"));
        }
        for (Tag t : tag.getList("log", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) t;
            data.log.add(new Entry(line.getLong("t"), line.getString("s"), line.getBoolean("a")));
        }
        data.armed = !tag.contains("armed") || tag.getBoolean("armed");
        return data;
    }
}
