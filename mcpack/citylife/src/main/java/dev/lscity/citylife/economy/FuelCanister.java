package dev.lscity.citylife.economy;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Заправка канистрой.
 *
 * Бензин в городе покупается готовым: канистра с заправки, правый клик по
 * машине — бак полный. Ни насосов, ни труб, ни смешивания топлива: это ровно
 * то, чего ждёшь от простой езды по городу.
 *
 * С модом машин (MrCrayfish's Vehicle) не связываемся напрямую: дописываем
 * сущности тег CurrentFuel до её же FuelCapacity. Поэтому пак собирается и без
 * мода машин, а канистра тогда молчит.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class FuelCanister {

    /** Пространство имён мода машин. */
    private static final String VEHICLES = "vehicle";

    private FuelCanister() {
    }

    /** Метка «бак уже заливали»: полный бак дают только новой машине. */
    private static final String FIRST_FILL = "citylife_first_fill";

    /**
     * Новая машина из ящика выходит с пустым баком — купил, открыл, а она
     * не едет. Поэтому при первом появлении в мире заливаем бак полностью.
     * Метка в тегах сущности сохраняется, и при повторной загрузке чанка
     * пустой бак уже не наполнится сам.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || entity.getTags().contains(FIRST_FILL)) {
            return;
        }
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null || !VEHICLES.equals(key.getNamespace())) {
            return;
        }
        entity.addTag(FIRST_FILL);
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        float capacity = tag.getFloat("FuelCapacity");
        if (capacity > 0 && tag.contains("CurrentFuel")) {
            tag.putFloat("CurrentFuel", capacity);
            entity.load(tag);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.getItem() != Registration.FUEL_CANISTER.get()) {
            return;
        }
        Entity target = event.getTarget();
        var key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (key == null || !VEHICLES.equals(key.getNamespace())) {
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (player.level().isClientSide) {
            return;
        }

        CompoundTag tag = new CompoundTag();
        target.saveWithoutId(tag);
        float capacity = tag.getFloat("FuelCapacity");
        if (capacity <= 0 || !tag.contains("CurrentFuel")) {
            // Лодка без мотора, прицеп, тележка — им топливо не нужно.
            player.displayClientMessage(Component.translatable("citylife.fuel.not_needed")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (tag.getFloat("CurrentFuel") >= capacity - 0.5F) {
            player.displayClientMessage(Component.translatable("citylife.fuel.full")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        tag.putFloat("CurrentFuel", capacity);
        target.load(tag);

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.level().playSound(null, target.blockPosition(), SoundEvents.BUCKET_EMPTY,
                SoundSource.PLAYERS, 0.7F, 1.0F);
        player.displayClientMessage(Component.translatable("citylife.fuel.filled")
                .withStyle(ChatFormatting.GREEN), true);
    }
}
