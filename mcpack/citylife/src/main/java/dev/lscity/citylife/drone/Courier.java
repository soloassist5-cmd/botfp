package dev.lscity.citylife.drone;

import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.Order;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.market.Market;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Доставка заказа маркетплейса курьерским дроном.
 *
 * Готовый заказ можно не забирать на пункте выдачи: за небольшую плату
 * «Пеликан» службы доставки прилетает на высоте выше крыш, спускается над
 * игроком и сбрасывает посылку у ног. Нужно открытое небо над головой —
 * в помещение дрон не залетит.
 */
public final class Courier {

    public static final long FEE = 250;
    /** Высота перелёта: выше любой башни города. */
    static final double CRUISE = 236;

    private Courier() {
    }

    public static void deliver(ServerPlayer player, CityData data, int orderId) {
        long now = player.level().getGameTime();
        Order order = data.orders(player.getUUID()).stream().filter(o -> o.id() == orderId).findFirst()
                .orElse(null);
        if (order == null || !order.ready(now)) {
            return;
        }
        if (!player.level().canSeeSky(player.blockPosition().above())) {
            player.displayClientMessage(Component.translatable("citylife.courier.no_sky")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        Market.Offer offer = Market.BY_ID.get(order.offer());
        Item item = offer == null ? null : ForgeRegistries.ITEMS.getValue(new ResourceLocation(offer.item()));
        if (item == null) {
            return;
        }
        if (!data.withdraw(player.getUUID(), FEE, Texts.ru("citylife.statement.courier"), now)) {
            player.displayClientMessage(Component.translatable("citylife.market.no_money",
                    dev.lscity.citylife.economy.Money.format(FEE)).withStyle(ChatFormatting.RED), false);
            return;
        }
        data.markTaken(player.getUUID(), order.id());
        // Дрон заходит со стороны города: в 96 блоках от игрока, на высоте перелёта.
        double ang = player.getRandom().nextDouble() * Math.PI * 2;
        Vec3 start = new Vec3(player.getX() + Math.cos(ang) * 96, Math.max(CRUISE, player.getY() + 40),
                player.getZ() + Math.sin(ang) * 96);
        DroneEntity drone = Drones.spawn(player.serverLevel(), null, DroneType.COURIER, start, 0, 1.0F);
        if (drone == null) {
            player.getInventory().placeItemBackInInventory(new ItemStack(item, offer.count()));
            return;
        }
        drone.startDelivery(player, new ItemStack(item, offer.count()));
        player.displayClientMessage(Component.translatable("citylife.courier.sent", order.title(),
                dev.lscity.citylife.economy.Money.format(FEE)).withStyle(ChatFormatting.AQUA), false);
    }
}
