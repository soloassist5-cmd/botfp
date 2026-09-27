package dev.lscity.citylife.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Метка в приложении «Карта». */
public record Waypoint(String name, int x, int y, int z) {

    public static Waypoint of(String name, BlockPos pos) {
        return new Waypoint(name, pos.getX(), pos.getY(), pos.getZ());
    }

    public static Waypoint load(CompoundTag tag) {
        return new Waypoint(tag.getString("name"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }
}
