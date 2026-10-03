package dev.lscity.citylife.drone;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Дрон в коробке. ПКМ по земле — поставить; заряд хранится в предмете и
 * сам потихоньку пополняется, пока дрон лежит в инвентаре (как от
 * пауэрбанка: полный заряд примерно за десять минут).
 */
public class DroneItem extends Item {

    private static final float CHARGE_PER_SECOND = 1.0F / 600.0F;

    private final DroneType type;

    public DroneItem(DroneType type) {
        super(new Properties().stacksTo(1).rarity(type == DroneType.RACER ? Rarity.RARE : Rarity.UNCOMMON));
        this.type = type;
    }

    public DroneType type() {
        return type;
    }

    public static ItemStack of(DroneType type, float battery) {
        ItemStack stack = new ItemStack(Drones.item(type));
        stack.getOrCreateTag().putFloat("Battery", battery);
        return stack;
    }

    public static float battery(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("Battery") ? stack.getTag().getFloat("Battery") : 1.0F;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        DroneEntity drone = Drones.spawn((ServerLevel) level, player, type,
                new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5), player.getYRot(),
                battery(context.getItemInHand()));
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        level.playSound(null, at, SoundEvents.ARMOR_EQUIP_ELYTRA, SoundSource.PLAYERS, 0.8F, 1.4F);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        player.displayClientMessage(Component.translatable("citylife.drone.placed")
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && level.getGameTime() % 20 == 0) {
            float b = battery(stack);
            if (b < 1.0F) {
                stack.getOrCreateTag().putFloat("Battery", Math.min(1.0F, b + CHARGE_PER_SECOND));
            }
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return battery(stack) < 1.0F;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * battery(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float b = battery(stack);
        return b < 0.2F ? 0xFF5050 : b < 0.5F ? 0xFFC040 : 0x60FF90;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("citylife.drone." + type.id + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("citylife.drone.specs", Math.round(type.maxSpeed * 20 * 3.6F),
                type.batterySeconds / 60, type.range).withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("citylife.drone.battery", Math.round(battery(stack) * 100))
                .withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("citylife.drone.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
