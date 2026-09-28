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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Заправка канистрой.
 *
 * Бензин в городе покупается готовым: канистра с заправки, правый клик по
 * машине — бак полный. Ни производства биодизеля, ни насосов, ни проводов:
 * это ровно то, чего ждёшь от аркадной езды по городу.
 *
 * С модом машин не связываемся напрямую: просто дописываем его сущности тег
 * fuel. Поэтому пак собирается и без мода машин, а канистра тогда молчит.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class FuelCanister {

    /** Полный бак: с запасом на любой кузов. */
    private static final int FULL = 20000;

    private FuelCanister() {
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
        if (key == null || !"car".equals(key.getNamespace())) {
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (player.level().isClientSide) {
            return;
        }

        CompoundTag tag = new CompoundTag();
        target.saveWithoutId(tag);
        int fuel = tag.getInt("fuel");
        if (fuel >= FULL) {
            player.displayClientMessage(Component.translatable("citylife.fuel.full")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        tag.putInt("fuel", FULL);
        if (!tag.contains("fuel_type")) {
            tag.putString("fuel_type", "car:bio_diesel");
        }
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
