package dev.lscity.citylife.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BooleanSupplier;

/**
 * Площадка одного самотеста: каменный пол 16x16 высоко над городом.
 *
 * Повторяет те немногие методы GameTestHelper, что нужны тестам: встроенный
 * GameTest в обычной (не dev) сборке Forge выключен, а проверять хочется
 * именно на настоящем сервере со всеми модами сборки.
 */
public final class TestKit {

    /** Провал теста: сообщение уходит в отчёт. */
    public static final class Failure extends RuntimeException {
        public Failure(String message) {
            super(message);
        }
    }

    private final ServerLevel level;
    private final BlockPos origin;
    BooleanSupplier waitFor;
    /** Последняя причина, по которой отложенная проверка ещё не прошла. */
    String lastFailure = "";
    int timeout = 100;

    TestKit(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(16, 7, 16))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(15, 0, 15))) {
            level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 2 | 16);
        }
        // Жители и тела от прошлых прогонов: мир сервера постоянный, и забытый
        // на площадке NPC загораживал дорогу прохожему из следующего теста.
        for (net.minecraft.world.entity.Entity stray : level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(origin.offset(-2, -2, -2), origin.offset(18, 12, 18)),
                e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            stray.discard();
        }
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos absolutePos(BlockPos rel) {
        return origin.offset(rel);
    }

    public void setBlock(BlockPos rel, BlockState state) {
        level.setBlockAndUpdate(absolutePos(rel), state);
    }

    public void setBlock(BlockPos rel, Block block) {
        setBlock(rel, block.defaultBlockState());
    }

    public BlockEntity getBlockEntity(BlockPos rel) {
        return level.getBlockEntity(absolutePos(rel));
    }

    public void destroyBlock(BlockPos rel) {
        level.destroyBlock(absolutePos(rel), true);
    }

    public void fail(String message) {
        throw new Failure(message);
    }

    public void succeed() {
    }

    /** Отложенная проверка: условие проверяется каждый тик до таймаута. */
    public void succeedWhen(Runnable check) {
        waitFor = () -> {
            try {
                check.run();
                return true;
            } catch (Failure notYet) {
                lastFailure = notYet.getMessage();
                return false;
            }
        };
    }

    /** Снести площадку после теста. */
    void clear() {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(16, 7, 16))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }
}
