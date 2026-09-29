package dev.lscity.citylife.city;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/**
 * Камеры видеонаблюдения SecurityCraft в гаджетах.
 *
 * Приложение «Камеры» делает то же, что монитор SecurityCraft: хранит список
 * камер и включает просмотр. Сам просмотр — поворот, зум, ночное зрение,
 * выход по Shift — остаётся родным, SecurityCraft ведёт его сам после
 * mountCamera. Проверки повторяют пакет монитора: камера включена, не
 * выключена сигналом, игрок — владелец или в её белом списке.
 *
 * SecurityCraft подключается через рефлексию: без него мод работает, просто
 * камер в городе нет.
 */
public final class Cameras {

    private static final String CAMERA_BLOCK = "net.geforcemods.securitycraft.blocks.SecurityCameraBlock";

    public enum Result { OK, NOT_CAMERA, NOT_OWNER, OFF }

    private Cameras() {
    }

    public static boolean isCamera(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block.getClass().getName().equals(CAMERA_BLOCK);
    }

    /** Может ли игрок пользоваться камерой: владелец или в белом списке. */
    public static boolean allowed(Level level, BlockPos pos, Entity player) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && (call(be, "isOwnedBy", player) || call(be, "isAllowed", player));
    }

    /** Включить просмотр камеры. Чанк с камерой подгружается, если он далеко. */
    public static Result view(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        level.getChunk(pos);
        if (!isCamera(level, pos)) {
            return Result.NOT_CAMERA;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || call(be, "isDisabled") || call(be, "isShutDown")) {
            return Result.OFF;
        }
        if (!allowed(level, pos, player)) {
            return Result.NOT_OWNER;
        }
        try {
            Block block = level.getBlockState(pos).getBlock();
            Method mount = block.getClass().getMethod("mountCamera", Level.class, BlockPos.class,
                    Player.class);
            mount.invoke(block, level, pos, player);
            return Result.OK;
        } catch (ReflectiveOperationException e) {
            return Result.OFF;
        }
    }

    private static boolean call(Object target, String name, Object... args) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    return Boolean.TRUE.equals(method.invoke(target, args));
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Другая версия SecurityCraft: считаем, что условие не выполнено.
        }
        return false;
    }
}
