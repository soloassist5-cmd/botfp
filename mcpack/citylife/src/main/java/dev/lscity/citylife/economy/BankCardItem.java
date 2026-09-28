package dev.lscity.citylife.economy;

import dev.lscity.citylife.data.CityData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Банковская карта: «Мир» или Mastercard.
 *
 * Сама по себе карта денег не хранит — она лишь ключ к счёту в CityData.
 * Потерял карту, выпустил новую в банкомате — деньги на месте.
 */
public class BankCardItem extends Item {

    /** Платёжная система. От неё зависит только оформление и приём за границей. */
    public enum Kind {
        MIR("mir", "2200", 0x2E86DE),
        MASTERCARD("mastercard", "5536", 0xE8703A);

        public final String id;
        public final String bin;
        public final int colour;

        Kind(String id, String bin, int colour) {
            this.id = id;
            this.bin = bin;
            this.colour = colour;
        }
    }

    private final Kind kind;

    public BankCardItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Выпустить карту на игрока: номер и владелец записываются в NBT. */
    public static ItemStack issue(Item card, Player owner) {
        ItemStack stack = new ItemStack(card);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID("owner", owner.getUUID());
        tag.putString("ownerName", owner.getGameProfile().getName());
        Kind kind = card instanceof BankCardItem bank ? bank.kind() : Kind.MIR;
        Random random = new Random(owner.getUUID().getLeastSignificantBits() ^ kind.hashCode());
        tag.putString("number", String.format("%s %04d %04d %04d", kind.bin,
                random.nextInt(10000), random.nextInt(10000), random.nextInt(10000)));
        return stack;
    }

    public static UUID ownerOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    public static String numberOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString("number");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            UUID owner = ownerOf(stack);
            if (owner == null) {
                player.displayClientMessage(
                        Component.translatable("citylife.card.blank").withStyle(ChatFormatting.RED),
                        true);
            } else if (!owner.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("citylife.card.foreign",
                        stack.getTag().getString("ownerName")).withStyle(ChatFormatting.RED), true);
            } else {
                long balance = CityData.get(server.server).balance(owner);
                player.displayClientMessage(Component.translatable("citylife.card.balance",
                        Money.format(balance)).withStyle(ChatFormatting.GREEN), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines,
                                TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("number")) {
            lines.add(Component.literal(tag.getString("number")).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(tag.getString("ownerName"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("citylife.card.unissued")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable(kind == Kind.MIR
                ? "citylife.card.note.mir" : "citylife.card.note.mastercard")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
