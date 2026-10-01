package dev.lscity.citylife.city;

import dev.lscity.citylife.CityLife;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Починка уже созданных миров.
 *
 * В первых версиях города крыши магазинов и офисов местами были из гравия:
 * крыша в один блок, и гравий осыпался внутрь от первого касания. Новый мир
 * строится с туфом, а в старом мод при загрузке чанка заменяет гравий выше
 * уровня улиц на туф (у земли и на пляже гравий природный — его не трогаем).
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class WorldFixes {

    /** Выше асфальта (68) гравий в городе бывает только на крышах. */
    private static final int ABOVE_Y = 70;

    private static final Deque<ChunkPos> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();

    private WorldFixes() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.maybeHas(state -> state.is(Blocks.GRAVEL))) {
                long key = chunk.getPos().toLong();
                if (QUEUED.add(key)) {
                    QUEUE.add(chunk.getPos());
                }
                return;
            }
        }
    }

    /** Менять блоки прямо в загрузке чанка нельзя — делаем это в тике, по 4 чанка. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || QUEUE.isEmpty()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        for (int n = 0; n < 4 && !QUEUE.isEmpty(); n++) {
            ChunkPos pos = QUEUE.poll();
            QUEUED.remove(pos.toLong());
            if (level.hasChunk(pos.x, pos.z)) {
                fix(level, level.getChunk(pos.x, pos.z));
            }
        }
    }

    /** Заменить гравий выше улиц в чанке. Возвращает, сколько блоков заменено. */
    public static int fix(ServerLevel level, LevelChunk chunk) {
        int fixed = 0;
        int top = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < chunk.getSections().length; i++) {
            LevelChunkSection section = chunk.getSections()[i];
            int base = level.getSectionYFromSectionIndex(i) << 4;
            if (base + 16 <= ABOVE_Y || base >= top
                    || !section.maybeHas(state -> state.is(Blocks.GRAVEL))) {
                continue;
            }
            for (int y = Math.max(base, ABOVE_Y); y < base + 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        at.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                        if (chunk.getBlockState(at).is(Blocks.GRAVEL)) {
                            level.setBlock(at, Blocks.TUFF.defaultBlockState(), 2);
                            fixed++;
                        }
                    }
                }
            }
        }
        if (fixed > 0) {
            CityLife.LOG.info("City Life: крыша в чанке {} — гравий заменён на туф ({} блоков)",
                    chunk.getPos(), fixed);
        }
        return fixed;
    }
}
