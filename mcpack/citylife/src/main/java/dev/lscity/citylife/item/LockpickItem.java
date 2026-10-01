package dev.lscity.citylife.item;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.block.SmartLockBlock;
import dev.lscity.citylife.block.SmartLockBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/** Отмычка: шанс вскрыть умный замок, при провале — тревога. */
public class LockpickItem extends Item {

    public LockpickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        if (state.getBlock() instanceof dev.lscity.citylife.block.AtmBlock) {
            // Отмычкой по банкомату — взлом. Отмычка тупится, только если взлом начался.
            if (dev.lscity.citylife.city.Robbery.robAtm(player, context.getClickedPos())) {
                context.getItemInHand().hurtAndBreak(1, player, owner ->
                        owner.broadcastBreakEvent(context.getHand()));
            }
            return InteractionResult.CONSUME;
        }
        if (!(state.getBlock() instanceof SmartLockBlock lock)
                || !(level.getBlockEntity(context.getClickedPos())
                        instanceof SmartLockBlockEntity entity)) {
            return InteractionResult.PASS;
        }

        context.getItemInHand().hurtAndBreak(1, player, owner ->
                owner.broadcastBreakEvent(context.getHand()));

        if (level.random.nextDouble() < CityConfig.CONFIG.lockpickChance.get()) {
            lock.toggle(level, context.getClickedPos(), state, entity, "отмычка", false);
            entity.note("ВЗЛОМ: " + player.getGameProfile().getName());
            player.sendSystemMessage(Component.translatable("citylife.lockpick.success"));
            dev.lscity.citylife.city.Wanted.crime(player, 1, "citylife.wanted.burglary");
            return InteractionResult.CONSUME;
        }

        player.sendSystemMessage(Component.translatable("citylife.lockpick.fail"));
        entity.note("Попытка взлома: " + player.getGameProfile().getName());
        if (CityConfig.CONFIG.lockpickAlarm.get()) {
            dev.lscity.citylife.city.Wanted.crime(player, 1, "citylife.wanted.burglary");
            level.playSound(null, context.getClickedPos(), SoundEvents.BELL_BLOCK,
                    SoundSource.BLOCKS, 1.4F, 1.8F);
            if (entity.getOwner() != null) {
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.getOwner());
                if (owner != null) {
                    owner.sendSystemMessage(Component.translatable("citylife.lockpick.alarm",
                            entity.getLabel(),
                            context.getClickedPos().getX(), context.getClickedPos().getZ()));
                }
            }
        }
        return InteractionResult.CONSUME;
    }
}
