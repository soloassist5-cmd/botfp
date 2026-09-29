package dev.lscity.citylife.market;

import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.Order;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Пункт выдачи маркетплейса — постамат.
 *
 * Правый клик отдаёт все заказы игрока, которые уже приехали, и говорит, что
 * ещё в пути и когда будет. Заказы привязаны к игроку, а не к пункту: забрать
 * можно в любом постамате города.
 */
public class PickupPointBlock extends dev.lscity.citylife.pc.DeskBlock {

    public PickupPointBlock(Properties properties) {
        super(properties, 0, 0, 0, 16, 16, 16);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer server)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        CityData data = CityData.get(server.server);
        long now = level.getGameTime();
        List<Order> orders = data.orders(server.getUUID());
        int given = 0;
        for (Order order : List.copyOf(orders)) {
            if (!order.ready(now)) {
                continue;
            }
            Market.Offer offer = Market.BY_ID.get(order.offer());
            Item item = offer == null ? null
                    : ForgeRegistries.ITEMS.getValue(new ResourceLocation(offer.item()));
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item, offer.count());
            if (!server.getInventory().add(stack)) {
                server.drop(stack, false);
            }
            data.markTaken(server.getUUID(), order.id());
            server.displayClientMessage(Component.translatable("citylife.pickup.given",
                    order.title()).withStyle(ChatFormatting.GREEN), false);
            given++;
        }
        long waiting = orders.stream().filter(o -> !o.taken() && !o.ready(now)).count();
        if (given > 0) {
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.2F);
        } else if (waiting == 0) {
            server.displayClientMessage(Component.translatable("citylife.pickup.nothing")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        for (Order order : orders) {
            if (!order.taken() && !order.ready(now)) {
                long seconds = Math.max(1, (order.readyAt() - now) / 20);
                server.displayClientMessage(Component.translatable("citylife.pickup.waiting",
                        order.title(), seconds / 60, seconds % 60)
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }
        return InteractionResult.CONSUME;
    }
}
