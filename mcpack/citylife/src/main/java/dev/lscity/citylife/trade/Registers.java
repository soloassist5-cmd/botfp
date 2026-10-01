package dev.lscity.citylife.trade;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.block.CashRegisterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Кассы во всех магазинах: мод сам ставит кассовый аппарат на прилавок
 * рядом с каждым продавцом, как только к магазину подходит игрок. Так касса
 * появляется и в мирах, построенных до этой версии, без перегенерации.
 *
 * Прилавок — твёрдый блок рядом с продавцом, над которым пусто, а за ним
 * проход для покупателя. Касса встаёт сбоку от продавца, чтобы не закрывать
 * его самого; нет места сбоку — прямо перед ним.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Registers {

    private static final Set<UUID> CHECKED = new HashSet<>();

    private Registers() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null
                || event.getServer().overworld().getGameTime() % 40 != 7) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            for (Mob npc : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                    e -> e.isNoAi() && !CHECKED.contains(e.getUUID())
                            && !dev.lscity.citylife.city.Citizens.isWalker(e)
                            && !dev.lscity.citylife.city.Citizens.isCorpse(e))) {
                CHECKED.add(npc.getUUID());
                Shop shop = ShopHandler.shopOf(npc);
                if (shop != null && !Checkout.lines(shop).isEmpty()) {
                    place(level, npc);
                }
            }
        }
    }

    /** Поставить кассу у продавца. Возвращает её позицию, или null. */
    public static BlockPos place(ServerLevel level, Mob npc) {
        BlockPos feet = npc.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2))) {
            if (level.getBlockState(p).getBlock() instanceof CashRegisterBlock) {
                return p.immutable();
            }
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos counter = feet.relative(dir);
            if (!isCounter(level, counter) || !passable(level, counter.relative(dir))) {
                continue;
            }
            for (BlockPos spot : new BlockPos[]{counter.relative(dir.getClockWise()),
                    counter.relative(dir.getCounterClockWise()), counter}) {
                if (isCounter(level, spot)) {
                    BlockPos at = spot.above();
                    level.setBlock(at, Registration.CASH_REGISTER.get().defaultBlockState()
                            .setValue(CashRegisterBlock.FACING, dir), 3);
                    return at;
                }
            }
        }
        return null;
    }

    private static boolean isCounter(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)
                && level.getBlockState(pos.above()).isAir();
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
