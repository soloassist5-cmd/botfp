package dev.lscity.citylife.pc;

import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно корпуса: восемь слотов под комплектующие и инвентарь игрока.
 *
 * Каждый слот принимает только свою деталь, поэтому процессор нельзя
 * положить в слот видеокарты, а по shift-клику деталь сама встаёт на место.
 */
public class PcCaseMenu extends AbstractContainerMenu {

    public static final int[][] SLOT_XY = {{12, 20}, {52, 20}, {92, 20}, {132, 20},
            {12, 56}, {52, 56}, {92, 56}, {132, 56}};
    private final Container parts;
    private final BlockPos pos;

    public PcCaseMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(PcBuild.SLOTS.length), buf.readBlockPos());
    }

    public PcCaseMenu(int id, Inventory inventory, Container parts, BlockPos pos) {
        super(Registration.PC_CASE_MENU.get(), id);
        this.parts = parts;
        this.pos = pos;
        for (int slot = 0; slot < PcBuild.SLOTS.length; slot++) {
            PcPart.Type type = PcBuild.SLOTS[slot];
            addSlot(new Slot(parts, slot, SLOT_XY[slot][0], SLOT_XY[slot][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    PcPart part = PcBuild.part(stack);
                    return part != null && part.type() == type;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 124 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 182));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    public List<ItemStack> partStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < parts.getContainerSize(); i++) {
            out.add(parts.getItem(i));
        }
        return out;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int caseSlots = PcBuild.SLOTS.length;
        if (index < caseSlots) {
            if (!moveItemStackTo(stack, caseSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < caseSlots && !moved; i++) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.set(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
    }
}
