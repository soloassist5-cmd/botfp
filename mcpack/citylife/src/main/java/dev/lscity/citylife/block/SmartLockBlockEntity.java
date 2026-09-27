package dev.lscity.citylife.block;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Состояние умного замка: владелец, доступ, код и журнал. */
public class SmartLockBlockEntity extends BlockEntity {
    private static final int LOG_LIMIT = 10;

    private UUID owner;
    private String ownerName = "";
    private String label = "";
    private String pin = "";
    private int autoCloseTicks = -1;   // -1 = взять из конфига
    private final Set<UUID> allowed = new LinkedHashSet<>();
    private final List<String> log = new ArrayList<>();

    public SmartLockBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.SMART_LOCK_BE.get(), pos, state);
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID id, String name) {
        this.owner = id;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    public String getLabel() {
        return label.isEmpty() ? "Замок" : label;
    }

    public void setLabel(String value) {
        this.label = value == null ? "" : value.substring(0, Math.min(24, value.length()));
        setChanged();
    }

    public boolean hasPin() {
        return !pin.isEmpty();
    }

    public boolean checkPin(String code) {
        return !pin.isEmpty() && pin.equals(code);
    }

    public void setPin(String code) {
        this.pin = code == null ? "" : code.replaceAll("[^0-9A-Za-z]", "")
                .substring(0, Math.min(8, code.length()));
        setChanged();
    }

    public int autoCloseTicks() {
        return autoCloseTicks > 0 ? autoCloseTicks : CityConfig.CONFIG.lockAutoCloseTicks.get();
    }

    public void setAutoCloseTicks(int ticks) {
        this.autoCloseTicks = ticks;
        setChanged();
    }

    public boolean isOwner(UUID id) {
        return owner != null && owner.equals(id);
    }

    public boolean isAllowed(UUID id) {
        return isOwner(id) || allowed.contains(id);
    }

    public void allow(UUID id) {
        if (allowed.add(id)) {
            setChanged();
        }
    }

    public void deny(UUID id) {
        if (allowed.remove(id)) {
            setChanged();
        }
    }

    public Set<UUID> allowedPlayers() {
        return allowed;
    }

    public List<String> getLog() {
        return log;
    }

    /** Записать событие в журнал доступа. */
    public void note(String text) {
        log.add(0, text);
        while (log.size() > LOG_LIMIT) {
            log.remove(log.size() - 1);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("ownerName", ownerName);
        tag.putString("label", label);
        tag.putString("pin", pin);
        tag.putInt("autoClose", autoCloseTicks);
        ListTag allowedTag = new ListTag();
        allowed.forEach(id -> allowedTag.add(NbtUtils.createUUID(id)));
        tag.put("allowed", allowedTag);
        ListTag logTag = new ListTag();
        log.forEach(line -> logTag.add(net.minecraft.nbt.StringTag.valueOf(line)));
        tag.put("log", logTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        label = tag.getString("label");
        pin = tag.getString("pin");
        autoCloseTicks = tag.contains("autoClose") ? tag.getInt("autoClose") : -1;
        allowed.clear();
        ListTag allowedTag = tag.getList("allowed", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < allowedTag.size(); i++) {
            allowed.add(NbtUtils.loadUUID(allowedTag.get(i)));
        }
        log.clear();
        ListTag logTag = tag.getList("log", Tag.TAG_STRING);
        for (int i = 0; i < logTag.size(); i++) {
            log.add(logTag.getString(i));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
