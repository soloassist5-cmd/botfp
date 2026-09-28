package dev.lscity.citylife.economy;

import dev.lscity.citylife.Registration;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Наличные рубли: номиналы, подсчёт, выдача и приём со сдачей.
 *
 * Наличные — это предметы в инвентаре, безналичные — счёт в CityData.
 * Банкомат и терминалы переводят одно в другое, поэтому вся арифметика
 * с номиналами живёт здесь, а не размазана по экранам.
 */
public final class Money {

    /** Номиналы от крупного к мелкому: в этом порядке удобно набирать сумму. */
    public static final long[] DENOMINATIONS = {5000, 1000, 500, 100, 50, 10, 1};

    private Money() {
    }

    public static Item item(long denomination) {
        return switch ((int) denomination) {
            case 5000 -> Registration.BANKNOTE_5000.get();
            case 1000 -> Registration.BANKNOTE_1000.get();
            case 500 -> Registration.BANKNOTE_500.get();
            case 100 -> Registration.BANKNOTE_100.get();
            case 50 -> Registration.BANKNOTE_50.get();
            case 10 -> Registration.COIN_10.get();
            case 1 -> Registration.COIN_1.get();
            default -> throw new IllegalArgumentException("нет номинала " + denomination);
        };
    }

    /** Достоинство предмета в рублях; 0 — это не деньги. */
    public static long value(ItemStack stack) {
        return stack.getItem() instanceof MoneyItem money ? money.value() : 0L;
    }

    /** Сколько наличных у игрока на руках. */
    public static long cash(Player player) {
        long total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            total += value(stack) * stack.getCount();
        }
        return total;
    }

    /**
     * Снять с игрока сумму наличными.
     *
     * Сначала собираем всё, что мельче или равно остатку, потом при нехватке
     * разменивааем одну крупную купюру и выдаём сдачу — так игрок не теряет
     * деньги, расплачиваясь пятитысячной за булку.
     */
    public static boolean take(Player player, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (cash(player) < amount) {
            return false;
        }
        Inventory inventory = player.getInventory();
        long left = amount;

        for (long denomination : DENOMINATIONS) {
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (value(stack) != denomination) {
                    continue;
                }
                int need = (int) Math.min(stack.getCount(), left / denomination);
                if (need > 0) {
                    stack.shrink(need);
                    left -= (long) need * denomination;
                }
            }
        }
        if (left == 0) {
            return true;
        }

        // Остаток мельче любой имеющейся бумажки: берём ближайшую крупную и сдаём сдачу.
        for (long denomination : DENOMINATIONS) {
            if (denomination < left) {
                continue;
            }
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (value(stack) == denomination) {
                    stack.shrink(1);
                    give(player, denomination - left);
                    return true;
                }
            }
        }
        // Сюда не попасть: наличных хватало, значит размен нашёлся бы.
        give(player, amount - left);
        return false;
    }

    /** Выдать сумму минимальным числом купюр. */
    public static void give(Player player, long amount) {
        for (ItemStack stack : stacks(amount)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** Разложить сумму по номиналам, соблюдая размер стопки. */
    public static List<ItemStack> stacks(long amount) {
        List<ItemStack> out = new ArrayList<>();
        long left = Math.max(0, amount);
        for (long denomination : DENOMINATIONS) {
            long count = left / denomination;
            left -= count * denomination;
            while (count > 0) {
                int portion = (int) Math.min(64, count);
                out.add(new ItemStack(item(denomination), portion));
                count -= portion;
            }
        }
        return out;
    }

    /** «1 250 ₽» — с неразрывными пробелами между разрядами. */
    public static String format(long rubles) {
        String digits = Long.toString(Math.abs(rubles));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return (rubles < 0 ? "-" : "") + out + " ₽";
    }
}
