package dev.lscity.citylife.pc;

import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ноутбук на столе хранит сам предмет: все приложения, заметки и обои
 * лежат в его NBT, поэтому поставил, поработал, забрал — ничего не пропало.
 */
public class LaptopBlockEntity extends BlockEntity {

    private ItemStack stack = ItemStack.EMPTY;

    public LaptopBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.LAPTOP_BE.get(), pos, state);
    }

    public ItemStack stack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
        setChanged();
    }

    /** Состояние гаджета: тот же тег, что у предмета в руке. */
    public CompoundTag device() {
        setChanged();
        return stack.getOrCreateTag();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!stack.isEmpty()) {
            tag.put("Laptop", stack.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stack = tag.contains("Laptop") ? ItemStack.of(tag.getCompound("Laptop")) : ItemStack.EMPTY;
    }
}
