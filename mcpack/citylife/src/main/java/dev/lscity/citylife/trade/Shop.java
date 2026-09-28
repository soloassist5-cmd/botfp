package dev.lscity.citylife.trade;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Прилавок жителя: название и список предложений.
 *
 * Своя торговля понадобилась потому, что Easy NPC выбрасывает готовые Offers
 * при спавне — жители остаются людьми на вид, а меняет товар на деньги наш мод.
 */
public record Shop(String title, List<ShopOffer> offers) {

    /** Собрать ванильный список предложений: игра умеет показывать его сама. */
    public MerchantOffers build() {
        MerchantOffers out = new MerchantOffers();
        for (ShopOffer offer : offers) {
            ItemStack price = offer.buy().toStack();
            ItemStack goods = offer.sell().toStack();
            if (price.isEmpty() || goods.isEmpty()) {
                continue;
            }
            // maxUses большой, опыт не даём: это магазин, а не ванильный крестьянин.
            out.add(new MerchantOffer(price, goods, 999999, 0, 0.0F));
        }
        return out;
    }

    /** Идентификаторы товаров, которых нет в сборке: для /citylife shops. */
    public String missing() {
        StringBuilder out = new StringBuilder();
        for (ShopOffer offer : offers) {
            for (ShopItem item : List.of(offer.buy(), offer.sell())) {
                if (!item.toStack().isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(item.id());
            }
        }
        return out.toString();
    }

    /** Предмет предложения: идентификатор, количество и необязательный NBT. */
    public record ShopItem(String id, int count, String snbt) {

        public ItemStack toStack() {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item == null) {
                // Товар из мода, которого нет в сборке, просто пропускаем.
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, count);
            if (snbt != null && !snbt.isEmpty()) {
                try {
                    stack.setTag(TagParser.parseTag(snbt));
                } catch (CommandSyntaxException error) {
                    return stack;
                }
            }
            return stack;
        }
    }

    /** Одно предложение: что отдаём и что получаем. */
    public record ShopOffer(ShopItem buy, ShopItem sell) {
    }
}
