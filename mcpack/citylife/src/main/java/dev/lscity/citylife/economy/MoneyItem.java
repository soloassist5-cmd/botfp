package dev.lscity.citylife.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Банкнота или монета: предмет со своим достоинством в рублях. */
public class MoneyItem extends Item {
    private final long value;

    public MoneyItem(long value, Properties properties) {
        super(properties);
        this.value = value;
    }

    public long value() {
        return value;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines,
                                TooltipFlag flag) {
        long total = value * stack.getCount();
        lines.add(Component.translatable("citylife.money.denomination", Money.format(value))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (stack.getCount() > 1) {
            lines.add(Component.translatable("citylife.money.stack", Money.format(total))
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
