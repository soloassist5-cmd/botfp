package dev.lscity.citylife.item;

import dev.lscity.citylife.Registration;
import dev.lscity.citylife.device.DeviceModel;
import dev.lscity.citylife.device.DeviceState;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import dev.lscity.citylife.city.Cameras;
import dev.lscity.citylife.data.CityData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Гаджет в руке: телефон, планшет или ноутбук.
 *
 * Правый клик открывает устройство. SIM вставляется как в сумку: взять карту
 * курсором и нажать ею правой кнопкой по телефону в инвентаре (или наоборот —
 * телефоном по карте). Если в слоте уже была карта, она возвращается в руку.
 */
public class DeviceItem extends Item {

    private final DeviceModel model;

    public DeviceItem(DeviceModel model, Properties properties) {
        super(properties);
        this.model = model;
    }

    public DeviceModel model() {
        return model;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            Net.openDevice(server, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Клик гаджетом по камере SecurityCraft привязывает её к приложению
     * «Камеры». Срабатывает раньше, чем сама камера обработает клик, поэтому
     * привязка не мешает ни монитору, ни универсальному инструменту.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!Cameras.isCamera(level, pos)) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (!Cameras.allowed(level, pos, player)) {
                player.displayClientMessage(Component.translatable("citylife.camera.not_owner")
                        .withStyle(ChatFormatting.RED), true);
            } else if (CityData.get(player.server).pairCamera(player.getUUID(), pos)) {
                player.displayClientMessage(Component.translatable("citylife.camera.paired")
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                player.displayClientMessage(Component.translatable("citylife.camera.already")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // --- SIM-карта в инвентаре ------------------------------------------------

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack device, ItemStack other, Slot slot,
                                            ClickAction action, Player player,
                                            SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof SimCardItem)) {
            return false;
        }
        ItemStack back = insert(device, other, player);
        if (back == null) {
            return false;
        }
        if (!back.isEmpty()) {
            if (other.isEmpty()) {
                carried.set(back);
            } else if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        return true;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack device, Slot slot, ClickAction action,
                                          Player player) {
        ItemStack other = slot.getItem();
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof SimCardItem)) {
            return false;
        }
        ItemStack back = insert(device, other, player);
        if (back == null) {
            return false;
        }
        if (!back.isEmpty()) {
            if (other.isEmpty()) {
                slot.set(back);
            } else if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        return true;
    }

    /**
     * Вставить одну карту из стопки в гаджет.
     *
     * @return прежняя карта (пустой стек, если слот был свободен) или null,
     *         если вставить нельзя
     */
    @Nullable
    public ItemStack insert(ItemStack device, ItemStack sim, Player player) {
        int number = SimCardItem.number(sim);
        if (!model.simSlot()) {
            player.displayClientMessage(Component.translatable("citylife.sim.no_slot")
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        if (number == 0) {
            // Номер выдаёт сервер при первом тике в инвентаре — чуть позже.
            return null;
        }
        int old = DeviceState.sim(device.getTag());
        DeviceState.setSim(device.getOrCreateTag(), number);
        sim.shrink(1);
        player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.8F, 1.4F);
        player.displayClientMessage(Component.translatable("citylife.sim.inserted",
                SimCardItem.format(number)).withStyle(ChatFormatting.GREEN), true);
        return old == 0 ? ItemStack.EMPTY
                : SimCardItem.withNumber(Registration.SIM_CARD.get(), old);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable("citylife.device." + model.id() + ".desc")
                .withStyle(ChatFormatting.GRAY));
        if (model.simSlot()) {
            int sim = DeviceState.sim(stack.getTag());
            lines.add(sim == 0
                    ? Component.translatable("citylife.sim.none").withStyle(ChatFormatting.RED)
                    : Component.translatable("citylife.sim.number", SimCardItem.format(sim))
                            .withStyle(ChatFormatting.AQUA));
            if (sim == 0) {
                lines.add(Component.translatable("citylife.sim.how")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            lines.add(Component.translatable("citylife.device.wifi")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
