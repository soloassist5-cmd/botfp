package dev.lscity.citylife.stark;

import dev.lscity.citylife.data.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * 3D-принтер Stark Industries печатает что угодно: любой предмет любого
 * мода, от доски до костюма Mark 85 и стеклянного телефона Старка.
 *
 * Нельзя только то, что ломает игру или экономику: деньги и банковские
 * карты (иначе принтер — печатный станок), блоки команд и прочие предметы
 * администратора. Пользоваться принтером может тот, у кого есть допуск
 * охраны STARK.
 */
public final class Printer {

    /** Сколько предметов за одну печать. */
    public static final int MAX_COUNT = 64;

    private static final Set<String> DENY = Set.of(
            "minecraft:barrier", "minecraft:light", "minecraft:structure_void", "minecraft:debug_stick",
            "minecraft:knowledge_book", "minecraft:bedrock", "minecraft:spawner", "minecraft:end_portal_frame",
            "minecraft:reinforced_deepslate", "minecraft:command_block_minecart");

    private Printer() {
    }

    public static boolean allowed(Item item) {
        if (item == null || item == Items.AIR || item instanceof GameMasterBlockItem) {
            return false;
        }
        if (item instanceof dev.lscity.citylife.economy.MoneyItem
                || item instanceof dev.lscity.citylife.economy.BankCardItem) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && !DENY.contains(id.toString()) && !id.getPath().endsWith("spawn_egg");
    }

    /** Время печати: 3 секунды и ещё по четверти секунды на каждый предмет. */
    public static int ticks(int count) {
        return 60 + 5 * Math.max(0, count - 1);
    }

    /** printer_print: {pos, item, count}. */
    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!"printer_print".equals(action)
                || !(player.level().getBlockEntity(pos) instanceof PrinterBlockEntity printer)
                || player.distanceToSqr(pos.getCenter()) > 64) {
            return;
        }
        if (!StarkSecurity.cleared(player)) {
            player.displayClientMessage(Component.translatable("citylife.printer.denied")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(args.getString("item")));
        if (!allowed(item)) {
            player.displayClientMessage(Component.translatable("citylife.printer.forbidden")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (printer.busy()) {
            player.displayClientMessage(Component.translatable("citylife.printer.busy")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int count = Math.max(1, Math.min(MAX_COUNT, args.getInt("count")));
        printer.start(item, count, player);
        StarkData.get(player.server).log(player.level().getGameTime(), Texts.ru("citylife.stark.log.print",
                player.getGameProfile().getName(), new net.minecraft.world.item.ItemStack(item)
                        .getHoverName().getString(), count), false);
    }
}
