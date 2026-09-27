package dev.lscity.citylife.data;

import net.minecraft.nbt.CompoundTag;

/** Сообщение в приложении «Сообщения». */
public record Message(String fromName, String text, long time) {

    public static Message load(CompoundTag tag) {
        return new Message(tag.getString("from"), tag.getString("text"), tag.getLong("time"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("from", fromName);
        tag.putString("text", text);
        tag.putLong("time", time);
        return tag;
    }
}
