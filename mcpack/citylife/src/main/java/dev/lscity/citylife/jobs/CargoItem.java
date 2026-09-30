package dev.lscity.citylife.jobs;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Груз для подработок: посылка, пакет с едой, мешок с мусором.
 *
 * Адрес доставки лежит в NBT («address»), номер задания — в «job». Груз —
 * обычный предмет: его можно выронить, передать или украсть, и тогда
 * доставить его сможет только тот, у кого он в руках.
 */
public class CargoItem extends Item {

    public CargoItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines,
                                TooltipFlag flag) {
        String address = stack.hasTag() ? stack.getTag().getString("address") : "";
        if (!address.isEmpty()) {
            lines.add(Component.translatable("citylife.cargo.to", address)
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("citylife.cargo.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
