package dev.lscity.citylife.trade;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.economy.BankCardItem;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Касса магазина: корзина, оплата картой со счёта или наличными.
 *
 * Клик по продавцу или по кассовому аппарату на прилавке открывает окно
 * кассы. Товары и цены — из того же каталога, что и прилавок (ShopCatalog),
 * только цена считается в рублях, а не в купюрах. Картой платят со своего
 * счёта в банке: нужна своя карта (Мир или Mastercard) в инвентаре, чужую
 * терминал не примет. Наличными — купюрами из инвентаря, со сдачей.
 *
 * Скупка (житель покупает у игрока) остаётся в старом окне обмена: кнопка
 * «Скупка» в кассе открывает его.
 */
public final class Checkout {

    /** Насколько далеко от продавца можно стоять у кассы. */
    private static final double REACH = 6.0D;
    /** Больше этого в одну покупку не набрать: корзина — не склад. */
    private static final int MAX_QTY = 64;

    private Checkout() {
    }

    /** Товар кассы: что дают и сколько стоит в рублях. */
    public record Line(int index, ItemStack goods, long price) {
    }

    /** Что из прилавка продаётся за деньги (без скупки и бартера). */
    public static List<Line> lines(Shop shop) {
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < shop.offers().size(); i++) {
            Shop.ShopOffer offer = shop.offers().get(i);
            ItemStack cost = offer.buy().toStack();
            ItemStack goods = offer.sell().toStack();
            long price = Money.value(cost) * cost.getCount();
            if (price > 0 && !goods.isEmpty() && Money.value(goods) == 0) {
                out.add(new Line(i, goods, price));
            }
        }
        return out;
    }

    /** Есть ли у прилавка скупка: тогда в кассе кнопка старого окна. */
    public static boolean buysFromPlayers(Shop shop) {
        for (Shop.ShopOffer offer : shop.offers()) {
            if (Money.value(offer.sell().toStack()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Своя карта в инвентаре, или пустой стек. */
    public static ItemStack card(ServerPlayer player) {
        ItemStack foreign = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BankCardItem) {
                if (player.getUUID().equals(BankCardItem.ownerOf(stack))) {
                    return stack;
                }
                foreign = stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof BankCardItem
                    && player.getUUID().equals(BankCardItem.ownerOf(stack))) {
                return stack;
            }
        }
        return foreign.isEmpty() ? ItemStack.EMPTY : foreign;
    }

    public static CompoundTag snapshot(ServerPlayer player, Entity clerk, Shop shop) {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", shop.title());
        tag.putUUID("clerk", clerk.getUUID());
        ListTag items = new ListTag();
        for (Line line : lines(shop)) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("i", line.index());
            entry.put("stack", line.goods().save(new CompoundTag()));
            entry.putLong("price", line.price());
            String group = shop.offers().get(line.index()).group();
            if (!group.isEmpty()) {
                entry.putString("g", group);
            }
            items.add(entry);
        }
        tag.put("items", items);
        tag.putLong("balance", CityData.get(player.server).balance(player.getUUID()));
        tag.putLong("cash", Money.cash(player));
        tag.putBoolean("barter", buysFromPlayers(shop));
        ItemStack card = card(player);
        if (!card.isEmpty()) {
            tag.putString("card", ((BankCardItem) card.getItem()).kind().id);
            tag.putString("cardNumber", BankCardItem.numberOf(card));
            tag.putBoolean("cardMine", player.getUUID().equals(BankCardItem.ownerOf(card)));
        }
        return tag;
    }

    /** Открыть кассу у продавца. */
    public static boolean open(ServerPlayer player, Entity clerk) {
        Shop shop = ShopHandler.shopOf(clerk);
        if (shop == null || lines(shop).isEmpty()) {
            return false;
        }
        Net.sendPanel(player, "checkout", snapshot(player, clerk, shop), true);
        return true;
    }

    /** Итог оплаты: удалась ли, сколько списали, что сказать на экране терминала. */
    public record Result(boolean ok, long total, String message) {
    }

    /**
     * Оплатить корзину. cart — [{i: номер предложения, n: сколько}], method —
     * "card" (приложить или провести — не важно) или "cash".
     */
    public static Result pay(ServerPlayer player, Entity clerk, ListTag cart, String method) {
        Shop shop = ShopHandler.shopOf(clerk);
        if (shop == null || !clerk.isAlive() || clerk.distanceTo(player) > REACH) {
            return new Result(false, 0, Texts.ru("citylife.checkout.too_far"));
        }
        List<Line> lines = lines(shop);
        long total = 0;
        List<ItemStack> goods = new ArrayList<>();
        for (int i = 0; i < cart.size(); i++) {
            CompoundTag entry = cart.getCompound(i);
            int qty = Math.max(0, Math.min(MAX_QTY, entry.getInt("n")));
            Line line = lines.stream().filter(l -> l.index() == entry.getInt("i")).findFirst().orElse(null);
            if (line == null || qty == 0) {
                continue;
            }
            total += line.price() * qty;
            for (int k = 0; k < qty; k++) {
                goods.add(line.goods().copy());
            }
        }
        if (goods.isEmpty()) {
            return new Result(false, 0, Texts.ru("citylife.checkout.empty"));
        }
        long now = player.level().getGameTime();
        if ("card".equals(method)) {
            ItemStack card = card(player);
            if (card.isEmpty()) {
                return new Result(false, total, Texts.ru("citylife.checkout.no_card"));
            }
            if (!player.getUUID().equals(BankCardItem.ownerOf(card))) {
                return new Result(false, total, Texts.ru("citylife.checkout.foreign_card"));
            }
            if (!CityData.get(player.server).withdraw(player.getUUID(), total,
                    Texts.ru("citylife.statement.purchase", shop.title()), now)) {
                return new Result(false, total, Texts.ru("citylife.checkout.declined"));
            }
        } else {
            if (!Money.take(player, total)) {
                return new Result(false, total, Texts.ru("citylife.checkout.no_cash"));
            }
        }
        for (ItemStack stack : goods) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        CityLife.LOG.info("City Life: {} купил в «{}» на {} ₽ ({})", player.getGameProfile().getName(),
                shop.title(), total, method);
        return new Result(true, total, Texts.ru("citylife.checkout.approved"));
    }

    /** Действия окна кассы: checkout_pay и checkout_barter. */
    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        if (!args.hasUUID("clerk")) {
            return;
        }
        UUID id = args.getUUID("clerk");
        Entity clerk = player.serverLevel().getEntity(id);
        if (clerk == null) {
            return;
        }
        Shop shop = ShopHandler.shopOf(clerk);
        if (shop == null) {
            return;
        }
        if ("checkout_barter".equals(action)) {
            if (clerk.distanceTo(player) <= REACH) {
                new CityTrader(clerk, shop).open(player);
            }
            return;
        }
        if (!"checkout_pay".equals(action)) {
            return;
        }
        Result result = pay(player, clerk, args.getList("cart", Tag.TAG_COMPOUND),
                args.getString("method"));
        CompoundTag tag = snapshot(player, clerk, shop);
        CompoundTag answer = new CompoundTag();
        answer.putBoolean("ok", result.ok());
        answer.putLong("total", result.total());
        answer.putString("message", result.message());
        tag.put("result", answer);
        Net.sendPanel(player, "checkout", tag, false);
    }
}
