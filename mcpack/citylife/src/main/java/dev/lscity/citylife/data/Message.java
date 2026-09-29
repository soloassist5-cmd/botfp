package dev.lscity.citylife.data;

import net.minecraft.nbt.CompoundTag;

/**
 * SMS: от кого (номер и ник на момент отправки), кому, текст и время.
 *
 * Номер — главное: сообщение приходит на SIM, а не игроку. Ник хранится
 * только для подписи, чтобы в переписке было видно, кто писал.
 */
public record Message(int fromNumber, String fromName, int toNumber, String text, long time) {

    public static Message load(CompoundTag tag) {
        return new Message(tag.getInt("fromNumber"), tag.getString("from"),
                tag.getInt("toNumber"), tag.getString("text"), tag.getLong("time"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("fromNumber", fromNumber);
        tag.putString("from", fromName);
        tag.putInt("toNumber", toNumber);
        tag.putString("text", text);
        tag.putLong("time", time);
        return tag;
    }
}
