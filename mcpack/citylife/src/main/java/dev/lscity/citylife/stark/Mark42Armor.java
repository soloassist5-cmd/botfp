package dev.lscity.citylife.stark;

import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

/** Часть брони Mark 42: своя модель и текстура, не ломается. */
public class Mark42Armor extends ArmorItem {

    private static final String OUTER = "citylife:textures/models/armor/mark42_outer.png";
    private static final String INNER = "citylife:textures/models/armor/mark42_inner.png";

    public Mark42Armor(Type type) {
        super(Mark42.MATERIAL, type, new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return slot == EquipmentSlot.LEGS ? INNER : OUTER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("citylife.mark42.tooltip").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("citylife.mark42.tooltip2").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                          HumanoidModel<?> original) {
                return dev.lscity.citylife.client.stark.Mark42Client.armorModel(entity, slot);
            }
        });
    }
}
