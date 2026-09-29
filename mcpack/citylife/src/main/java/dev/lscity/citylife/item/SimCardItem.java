package dev.lscity.citylife.item;

import dev.lscity.citylife.data.CityData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * SIM-карта с четырёхзначным номером.
 *
 * Номер выдаётся, когда карта впервые попадает в инвентарь, и дальше живёт
 * в самой карте: вынул SIM из телефона и вставил в другой — номер тот же,
 * сообщения приходят туда же. Реестр номеров на сервере не даёт выдать один
 * номер дважды.
 */
public class SimCardItem extends Item {

    public static final String NUMBER = "Number";

    public SimCardItem(Properties properties) {
        super(properties);
    }

    public static int number(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(NUMBER) : 0;
    }

    public static ItemStack withNumber(Item item, int number) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt(NUMBER, number);
        return stack;
    }

    /** Номер в привычном виде: 4821 -> «48-21». */
    public static String format(int number) {
        String digits = String.format("%04d", number);
        return digits.substring(0, 2) + "-" + digits.substring(2);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot,
                              boolean selected) {
        if (level instanceof ServerLevel server && number(stack) == 0) {
            stack.getOrCreateTag().putInt(NUMBER,
                    CityData.get(server.getServer()).issueNumber(holder.getUUID()));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines,
                                TooltipFlag flag) {
        int number = number(stack);
        lines.add(number == 0
                ? Component.translatable("citylife.sim.blank").withStyle(ChatFormatting.GRAY)
                : Component.translatable("citylife.sim.number", format(number))
                        .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("citylife.sim.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
