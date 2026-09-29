package dev.lscity.citylife.pc;

import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Системный блок: восемь слотов под комплектующие и «диск» — состояние
 * рабочего стола (обои, приложения, заметки).
 */
public class PcCaseBlockEntity extends BlockEntity implements MenuProvider {

    private final SimpleContainer parts = new SimpleContainer(PcBuild.SLOTS.length) {
        @Override
        public void setChanged() {
            super.setChanged();
            PcCaseBlockEntity.this.setChanged();
        }
    };
    private CompoundTag device = new CompoundTag();

    public PcCaseBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.PC_CASE_BE.get(), pos, state);
    }

    public Container parts() {
        return parts;
    }

    public List<ItemStack> slots() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < parts.getContainerSize(); i++) {
            out.add(parts.getItem(i));
        }
        return out;
    }

    public PcBuild.Result build() {
        return PcBuild.check(slots());
    }

    /** Рабочий стол компьютера: живёт в корпусе, как настоящий диск. */
    public CompoundTag device() {
        return device;
    }

    public void deviceChanged() {
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.citylife.pc_case");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PcCaseMenu(id, inventory, parts, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Parts", parts.createTag());
        tag.put("Device", device);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        parts.fromTag(tag.getList("Parts", 10));
        device = tag.getCompound("Device");
    }
}
