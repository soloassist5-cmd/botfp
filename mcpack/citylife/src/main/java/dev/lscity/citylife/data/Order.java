package dev.lscity.citylife.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Заказ маркетплейса.
 *
 * Деньги списываются со счёта сразу при заказе, товар едет на пункт выдачи
 * и становится доступен после readyAt (игровые тики сервера). Забрать можно
 * в любом пункте выдачи — как у настоящих маркетплейсов.
 */
public record Order(int id, String offer, String title, long price, long readyAt,
                    boolean taken) {

    public boolean ready(long now) {
        return !taken && now >= readyAt;
    }

    public Order take() {
        return new Order(id, offer, title, price, readyAt, true);
    }

    public static Order load(CompoundTag tag) {
        return new Order(tag.getInt("id"), tag.getString("offer"), tag.getString("title"),
                tag.getLong("price"), tag.getLong("readyAt"), tag.getBoolean("taken"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("offer", offer);
        tag.putString("title", title);
        tag.putLong("price", price);
        tag.putLong("readyAt", readyAt);
        tag.putBoolean("taken", taken);
        return tag;
    }
}
