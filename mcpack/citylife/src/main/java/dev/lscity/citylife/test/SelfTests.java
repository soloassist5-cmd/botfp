package dev.lscity.citylife.test;

import dev.lscity.citylife.CityLife;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * /citylife selftest — прогон самотестов мода прямо на сервере.
 *
 * Тесты идут по одному, каждый на своей площадке в небе над точкой
 * появления. Отложенные проверки (прохожий дошёл, наряд уехал) ждут
 * своего числа тиков. Итог — строка «SELFTEST DONE», её ждёт скрипт
 * citylife/tools/run_gametests.py.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class SelfTests {

    private static final Deque<Method> QUEUE = new ArrayDeque<>();
    private static CommandSourceStack source;
    private static ServerLevel level;
    private static Method current;
    private static TestKit kit;
    private static int waited;
    private static int passed;
    private static int total;
    private static int slot;
    private static final List<String> FAILED = new ArrayList<>();

    private SelfTests() {
    }

    public static int start(CommandSourceStack from) {
        if (!QUEUE.isEmpty() || current != null) {
            from.sendFailure(Component.literal("самотесты уже идут"));
            return 0;
        }
        source = from;
        level = from.getServer().overworld();
        List<Method> methods = new ArrayList<>();
        for (Method m : CityTests.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(CityTests.SelfTest.class)) {
                methods.add(m);
            }
        }
        methods.sort(Comparator.comparing(Method::getName));
        QUEUE.addAll(methods);
        passed = 0;
        total = methods.size();
        slot = 0;
        FAILED.clear();
        report("SELFTEST START " + total, ChatFormatting.AQUA);
        return total;
    }

    private static void report(String line, ChatFormatting colour) {
        CityLife.LOG.info(line);
        if (source != null) {
            source.sendSuccess(() -> Component.literal(line).withStyle(colour), false);
        }
    }

    private static BlockPos arena(int index) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockPos(spawn.getX() + index * 24, 300, spawn.getZ());
    }

    private static void finish(boolean ok, String message) {
        String name = current.getName();
        if (ok) {
            passed++;
            report("SELFTEST ok " + name, ChatFormatting.GREEN);
        } else {
            FAILED.add(name);
            report("SELFTEST FAIL " + name + ": " + message, ChatFormatting.RED);
        }
        kit.clear();
        current = null;
        kit = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || level == null) {
            return;
        }
        if (current != null) {
            waited++;
            boolean done;
            try {
                done = kit.waitFor.getAsBoolean();
            } catch (RuntimeException error) {
                finish(false, error.getMessage());
                return;
            }
            if (done) {
                finish(true, "");
            } else if (waited >= kit.timeout) {
                finish(false, "за " + waited + " тиков: " + kit.lastFailure);
            }
            return;
        }
        Method next = QUEUE.poll();
        if (next == null) {
            report("SELFTEST DONE " + passed + "/" + total
                    + (FAILED.isEmpty() ? "" : " FAILED " + FAILED), FAILED.isEmpty()
                    ? ChatFormatting.GREEN : ChatFormatting.RED);
            level = null;
            source = null;
            return;
        }
        current = next;
        BlockPos origin = arena(slot++ % 8);
        ChunkPos chunk = new ChunkPos(origin);
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                level.setChunkForced(chunk.x + dx, chunk.z + dz, true);
            }
        }
        kit = new TestKit(level, origin);
        kit.timeout = next.getAnnotation(CityTests.SelfTest.class).timeout();
        waited = 0;
        try {
            next.invoke(null, kit);
            if (kit.waitFor == null) {
                finish(true, "");
            }
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (!(cause instanceof TestKit.Failure)) {
                CityLife.LOG.error("City Life: самотест {} упал", next.getName(), cause);
            }
            finish(false, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (IllegalAccessException error) {
            finish(false, error.toString());
        }
    }
}
