package dev.lscity.citylife.stark;

import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Пульт охраны хранит, что он охраняет: коробки зон и камеры с подписями.
 * Всё это кладёт в мир генератор карты, а игрок может поправить через
 * /data merge block — например, чтобы охранять свой дом.
 *
 * Zones: [{x0,y0,z0,x1,y1,z1}], Cams: [{x,y,z,name}] — координаты мира.
 */
public class SecurityConsoleBlockEntity extends BlockEntity {

    /** Место стойки Зала брони: где, куда смотрит, какой костюм и в каком слоте. */
    public record Suit(net.minecraft.world.phys.Vec3 pos, float yaw, String item, String slot) {
    }

    private final List<AABB> zones = new ArrayList<>();
    private final List<Suit> suits = new ArrayList<>();
    /** Когда последний раз проверяли стойки (игровое время), -1 — ещё ни разу. */
    public long checked = -1;
    private final Map<BlockPos, String> cams = new LinkedHashMap<>();

    public SecurityConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.SECURITY_CONSOLE_BE.get(), pos, state);
    }

    public List<AABB> zones() {
        return zones;
    }

    public List<Suit> suits() {
        return suits;
    }

    /** Камеры, которые ещё стоят на месте. */
    public List<BlockPos> cameras() {
        List<BlockPos> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        for (BlockPos cam : cams.keySet()) {
            if (dev.lscity.citylife.city.Cameras.isCamera(level, cam)) {
                out.add(cam);
            }
        }
        return out;
    }

    public String cameraName(BlockPos cam, int n) {
        String name = cams.getOrDefault(cam, "");
        return name.isEmpty() ? String.format("CAM %02d", n) : name;
    }

    public void setZones(List<AABB> boxes) {
        zones.clear();
        zones.addAll(boxes);
        setChanged();
        if (level != null) {
            StarkSecurity.addZones(level, worldPosition, zones);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !zones.isEmpty()) {
            StarkSecurity.addZones(level, worldPosition, zones);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            StarkSecurity.removeZones(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) {
            StarkSecurity.removeZones(level, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (AABB box : zones) {
            CompoundTag z = new CompoundTag();
            z.putInt("x0", (int) Math.floor(box.minX));
            z.putInt("y0", (int) Math.floor(box.minY));
            z.putInt("z0", (int) Math.floor(box.minZ));
            z.putInt("x1", (int) Math.ceil(box.maxX) - 1);
            z.putInt("y1", (int) Math.ceil(box.maxY) - 1);
            z.putInt("z1", (int) Math.ceil(box.maxZ) - 1);
            list.add(z);
        }
        tag.put("Zones", list);
        ListTag camList = new ListTag();
        cams.forEach((pos, name) -> {
            CompoundTag c = new CompoundTag();
            c.putInt("x", pos.getX());
            c.putInt("y", pos.getY());
            c.putInt("z", pos.getZ());
            c.putString("name", name);
            camList.add(c);
        });
        tag.put("Cams", camList);
        ListTag suitList = new ListTag();
        for (Suit suit : suits) {
            CompoundTag c = new CompoundTag();
            c.putDouble("x", suit.pos().x);
            c.putDouble("y", suit.pos().y);
            c.putDouble("z", suit.pos().z);
            c.putFloat("yaw", suit.yaw());
            c.putString("item", suit.item());
            c.putString("slot", suit.slot());
            suitList.add(c);
        }
        tag.put("Suits", suitList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        zones.clear();
        for (Tag t : tag.getList("Zones", Tag.TAG_COMPOUND)) {
            CompoundTag z = (CompoundTag) t;
            // Включительно по блокам: коробка от x0 до x1+1.
            zones.add(new AABB(Math.min(z.getInt("x0"), z.getInt("x1")),
                    Math.min(z.getInt("y0"), z.getInt("y1")),
                    Math.min(z.getInt("z0"), z.getInt("z1")),
                    Math.max(z.getInt("x0"), z.getInt("x1")) + 1,
                    Math.max(z.getInt("y0"), z.getInt("y1")) + 1,
                    Math.max(z.getInt("z0"), z.getInt("z1")) + 1));
        }
        cams.clear();
        for (Tag t : tag.getList("Cams", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            cams.put(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")), c.getString("name"));
        }
        suits.clear();
        for (Tag t : tag.getList("Suits", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            suits.add(new Suit(new net.minecraft.world.phys.Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")),
                    c.getFloat("yaw"), c.getString("item"), c.getString("slot").isEmpty() ? "chest" : c.getString("slot")));
        }
        if (level != null && !level.isClientSide) {
            StarkSecurity.addZones(level, worldPosition, zones);
        }
    }
}
