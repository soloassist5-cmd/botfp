package dev.lscity.citylife.net;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.economy.BankCardItem;
import dev.lscity.citylife.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Серверная логика банкомата.
 *
 * Правило одно: наличные живут в инвентаре, счёт — в CityData, и переход
 * между ними возможен только здесь. Поэтому «напечатать» деньги нельзя
 * ни снятием, ни внесением — сумма всегда перекладывается, а не создаётся.
 */
public final class AtmServer {

    private static final int RANGE = 6;

    private AtmServer() {
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenAtmPacket(pos, snapshot(player), true));
    }

    public static void sync(ServerPlayer player, BlockPos pos) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenAtmPacket(pos, snapshot(player), false));
    }

    public static CompoundTag snapshot(ServerPlayer player) {
        CityData data = CityData.get(player.server);
        CompoundTag tag = new CompoundTag();
        tag.putLong("balance", data.balance(player.getUUID()));
        tag.putLong("cash", Money.cash(player));
        tag.putString("owner", player.getGameProfile().getName());
        tag.putInt("cardPrice", CityConfig.CONFIG.cardPrice.get());

        ItemStack card = findCard(player);
        tag.putBoolean("hasCard", !card.isEmpty());
        if (!card.isEmpty()) {
            tag.putString("cardNumber", BankCardItem.numberOf(card));
            tag.putString("cardKind", card.getItem() == Registration.CARD_MASTERCARD.get()
                    ? "mastercard" : "mir");
        }
        return tag;
    }

    /** Карта игрока в инвентаре — та, что выпущена на него самого. */
    private static ItemStack findCard(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BankCardItem) {
                UUID owner = BankCardItem.ownerOf(stack);
                if (owner != null && owner.equals(player.getUUID())) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Разбор действий из экрана банкомата. Возвращает true, если экран надо обновить. */
    public static boolean handle(ServerPlayer player, String action, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!player.blockPosition().closerThan(pos, RANGE)) {
            player.displayClientMessage(Component.translatable("citylife.atm.far")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        CityData data = CityData.get(player.server);
        UUID id = player.getUUID();
        long amount = Math.max(0, args.getLong("amount"));

        switch (action) {
            case "atm_refresh" -> {
            }
            case "atm_withdraw" -> {
                if (amount <= 0) {
                    return false;
                }
                if (data.balance(id) < amount) {
                    return fail(player, "citylife.atm.no_funds");
                }
                data.withdraw(id, amount);
                Money.give(player, amount);
                sound(player, pos);
                player.displayClientMessage(Component.translatable("citylife.atm.withdrawn",
                        Money.format(amount)).withStyle(ChatFormatting.GREEN), true);
            }
            case "atm_deposit" -> {
                long take = amount > 0 ? amount : Money.cash(player);
                if (take <= 0) {
                    return false;
                }
                if (!Money.take(player, take)) {
                    return fail(player, "citylife.atm.no_cash");
                }
                data.deposit(id, take);
                sound(player, pos);
                player.displayClientMessage(Component.translatable("citylife.atm.deposited",
                        Money.format(take)).withStyle(ChatFormatting.GREEN), true);
            }
            case "atm_card" -> {
                if (!findCard(player).isEmpty()) {
                    return fail(player, "citylife.atm.card_exists");
                }
                long price = CityConfig.CONFIG.cardPrice.get();
                if (data.balance(id) < price) {
                    return fail(player, "citylife.atm.no_funds");
                }
                boolean mastercard = "mastercard".equals(args.getString("kind"));
                data.withdraw(id, price);
                ItemStack card = BankCardItem.issue(mastercard
                        ? Registration.CARD_MASTERCARD.get() : Registration.CARD_MIR.get(), player);
                if (!player.getInventory().add(card)) {
                    player.drop(card, false);
                }
                sound(player, pos);
                player.displayClientMessage(Component.translatable("citylife.atm.card_issued")
                        .withStyle(ChatFormatting.GREEN), true);
            }
            case "atm_transfer" -> {
                String target = args.getString("to").trim();
                ServerPlayer other = player.server.getPlayerList().getPlayerByName(target);
                if (other == null) {
                    return fail(player, "citylife.atm.no_player");
                }
                if (amount <= 0 || data.balance(id) < amount) {
                    return fail(player, "citylife.atm.no_funds");
                }
                data.withdraw(id, amount);
                data.deposit(other.getUUID(), amount);
                sound(player, pos);
                player.displayClientMessage(Component.translatable("citylife.atm.sent",
                        Money.format(amount), other.getGameProfile().getName())
                        .withStyle(ChatFormatting.GREEN), true);
                other.displayClientMessage(Component.translatable("citylife.atm.received",
                        Money.format(amount), player.getGameProfile().getName())
                        .withStyle(ChatFormatting.GREEN), false);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        return true;
    }

    private static void sound(ServerPlayer player, BlockPos pos) {
        player.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.BLOCKS, 0.6F, 1.6F);
    }
}
