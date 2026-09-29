package dev.lscity.citylife.data;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Объявление на городской доске в «Браузере»: видно всем, у кого есть сеть. */
public record Ad(int id, UUID author, String authorName, String text, long time) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putUUID("author", author);
        tag.putString("authorName", authorName);
        tag.putString("text", text);
        tag.putLong("time", time);
        return tag;
    }

    public static Ad load(CompoundTag tag) {
        return new Ad(tag.getInt("id"), tag.getUUID("author"), tag.getString("authorName"),
                tag.getString("text"), tag.getLong("time"));
    }
}
