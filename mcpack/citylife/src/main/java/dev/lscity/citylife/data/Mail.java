package dev.lscity.citylife.data;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Письмо: приходит на ник, а не на номер, поэтому работает на ноутбуке
 * и компьютере без SIM. Читается с любого гаджета владельца.
 */
public record Mail(UUID from, String fromName, String text, long time, boolean read) {

    public Mail markRead() {
        return new Mail(from, fromName, text, time, true);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("from", from);
        tag.putString("fromName", fromName);
        tag.putString("text", text);
        tag.putLong("time", time);
        tag.putBoolean("read", read);
        return tag;
    }

    public static Mail load(CompoundTag tag) {
        return new Mail(tag.getUUID("from"), tag.getString("fromName"), tag.getString("text"),
                tag.getLong("time"), tag.getBoolean("read"));
    }
}
