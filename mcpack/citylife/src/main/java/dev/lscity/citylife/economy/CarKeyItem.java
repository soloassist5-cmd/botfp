package dev.lscity.citylife.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ключ от машины: купил у автодилера — поставил готовую машину.
 *
 * Собирать автомобиль по частям и чинить его в сборке не нужно: ключ сразу
 * создаёт собранную машину с полным баком. Модель кузова записана в NBT,
 * поэтому один предмет обслуживает сколько угодно моделей в продаже.
 */
public class CarKeyItem extends Item {

    /** Сколько топлива заливаем при выдаче: полный бак средней машины. */
    private static final int FULL_TANK = 20000;

    public CarKeyItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Item key, String body, String title) {
        ItemStack stack = new ItemStack(key);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("body", body);
        tag.putString("model", title);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getTag();
        if (level.isClientSide || !(level instanceof ServerLevel server) || tag == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos pos = context.getClickedPos().above();
        Entity car = spawn(server, pos, tag.getString("body"));
        if (car == null) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("citylife.car.no_mod")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /**
     * Создать готовую машину.
     *
     * Машина мода Ultimate Car — обычная сущность, а её комплектация лежит
     * в теге parts: кузов, двигатель, бак и четыре колеса. Собираем этот
     * список сами, поэтому игроку ничего собирать не нужно.
     */
    private static Entity spawn(ServerLevel level, BlockPos pos, String body) {
        if (body == null || body.isEmpty()) {
            body = "car:black_suv_body";
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "car:car");

        ListTag parts = new ListTag();
        parts.add(part(body));
        parts.add(part("car:engine_6_cylinder"));
        parts.add(part("car:medium_tank"));
        for (int wheel = 0; wheel < 4; wheel++) {
            parts.add(part("car:wheel"));
        }
        tag.put("parts", parts);
        tag.putInt("fuel", FULL_TANK);
        tag.putString("fuel_type", "car:bio_diesel");
        tag.putFloat("damage", 0.0F);

        ListTag position = new ListTag();
        position.add(net.minecraft.nbt.DoubleTag.valueOf(pos.getX() + 0.5));
        position.add(net.minecraft.nbt.DoubleTag.valueOf(pos.getY()));
        position.add(net.minecraft.nbt.DoubleTag.valueOf(pos.getZ() + 0.5));
        tag.put("Pos", position);

        Entity entity = EntityType.loadEntityRecursive(tag, level, created -> created);
        if (entity == null) {
            return null;
        }
        level.addFreshEntity(entity);
        return entity;
    }

    private static CompoundTag part(String id) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        stack.putByte("Count", (byte) 1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines,
                                TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("model")) {
            lines.add(Component.literal(tag.getString("model")).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("citylife.car.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Строковый тег — вспомогалка для читаемости списков в других классах. */
    static StringTag text(String value) {
        return StringTag.valueOf(value);
    }
}
