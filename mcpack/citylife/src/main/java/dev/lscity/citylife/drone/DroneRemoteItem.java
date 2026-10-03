package dev.lscity.citylife.drone;

import net.minecraft.ChatFormatting;
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

/**
 * Пульт дрона. ПКМ — взять управление ближайшим своим дроном (в пределах
 * его связи); Shift+ПКМ — все свои дроны летят домой, к тебе.
 */
public class DroneRemoteItem extends Item {

    public DroneRemoteItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            int n = Drones.callHome(sp);
            sp.displayClientMessage(Component.translatable(n > 0 ? "citylife.drone.home_all" : "citylife.drone.none",
                    n).withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.consume(stack);
        }
        DroneEntity drone = Drones.nearestOwned(sp);
        if (drone == null) {
            sp.displayClientMessage(Component.translatable("citylife.drone.none")
                    .withStyle(ChatFormatting.GRAY), true);
        } else if (drone.battery() <= 0) {
            sp.displayClientMessage(Component.translatable("citylife.drone.battery_dead")
                    .withStyle(ChatFormatting.RED), true);
        } else {
            Drones.start(sp, drone);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("citylife.drone.remote.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("citylife.drone.remote.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
