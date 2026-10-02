package dev.lscity.citylife.stark;

import dev.lscity.citylife.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Задание принтера: что печатаем, сколько, когда начали и сколько длится.
 * Клиент знает начало и длительность, поэтому сам плавно рисует прогресс —
 * пакеты идут только на старте и на финише.
 */
public class PrinterBlockEntity extends BlockEntity {

    private ItemStack job = ItemStack.EMPTY;
    private long start;
    private int total;

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.PRINTER_BE.get(), pos, state);
    }

    public boolean busy() {
        return !job.isEmpty();
    }

    public ItemStack job() {
        return job;
    }

    /** Доля готовности 0..1 по игровому времени. */
    public float progress(float partial) {
        if (job.isEmpty() || level == null || total <= 0) {
            return 0F;
        }
        return Math.max(0F, Math.min(1F, (level.getGameTime() - start + partial) / total));
    }

    public void start(Item item, int count, ServerPlayer by) {
        if (level == null) {
            return;
        }
        job = new ItemStack(item, count);
        start = level.getGameTime();
        total = Printer.ticks(count);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.8F);
        update();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PrinterBlockEntity be) {
        if (be.job.isEmpty()) {
            return;
        }
        long t = level.getGameTime() - be.start;
        if (t % 8 == 0) {
            level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.25F,
                    1.4F + level.random.nextFloat() * 0.5F);
        }
        if (t < be.total) {
            return;
        }
        // Готово: выдаём стопками поверх принтера.
        int left = be.job.getCount();
        while (left > 0) {
            int n = Math.min(left, be.job.getMaxStackSize());
            ItemStack out = be.job.copyWithCount(n);
            ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, out,
                    0, 0.18, 0);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            left -= n;
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.9F, 1.6F);
        be.job = ItemStack.EMPTY;
        be.update();
    }

    private void update() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!job.isEmpty()) {
            tag.putString("Item", String.valueOf(ForgeRegistries.ITEMS.getKey(job.getItem())));
            tag.putInt("Count", job.getCount());
            tag.putLong("Start", start);
            tag.putInt("Total", total);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        Item item = tag.contains("Item")
                ? ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(tag.getString("Item"))) : null;
        job = item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY
                : new ItemStack(item, Math.max(1, tag.getInt("Count")));
        start = tag.getLong("Start");
        total = tag.getInt("Total");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
