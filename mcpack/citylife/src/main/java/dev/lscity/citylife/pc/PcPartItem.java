package dev.lscity.citylife.pc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Комплектующая: в подсказке всё, что нужно для сборки. */
public class PcPartItem extends Item {

    private final PcPart part;

    public PcPartItem(PcPart part, Properties properties) {
        super(properties);
        this.part = part;
    }

    public PcPart part() {
        return part;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable("citylife.pc.type." + part.type().name().toLowerCase())
                .withStyle(ChatFormatting.GRAY));
        if (!part.socket().isEmpty()) {
            lines.add(Component.translatable("citylife.pc.socket_is", part.socket())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (part.type() == PcPart.Type.PSU) {
            lines.add(Component.translatable("citylife.pc.supply", part.watts())
                    .withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable("citylife.pc.draw", part.watts())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (part.score() > 0) {
            lines.add(Component.translatable("citylife.pc.score", part.score())
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
