package dev.lscity.citylife.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Метка навигатора.
 *
 * Городские метки (city = true) приходят из плана генератора и одинаковы
 * у всех, личные добавляет сам игрок и видит только он.
 */
public record Waypoint(String name, int x, int y, int z, String icon, boolean city) {

    public static Waypoint of(String name, BlockPos pos) {
        return new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), "pin", false);
    }

    public static Waypoint load(CompoundTag tag) {
        return new Waypoint(tag.getString("name"), tag.getInt("x"), tag.getInt("y"),
                tag.getInt("z"), tag.contains("icon") ? tag.getString("icon") : "pin",
                tag.getBoolean("city"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putString("icon", icon);
        tag.putBoolean("city", city);
        return tag;
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }
}
