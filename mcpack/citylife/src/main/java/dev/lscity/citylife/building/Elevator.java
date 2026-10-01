package dev.lscity.citylife.building;

import dev.lscity.citylife.block.ElevatorBlock;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Лифт небоскрёба. Своей кабины у него нет: кнопки вызова висят на каждом
 * этаже одна над другой, и всё, что лежит в одном вертикальном столбце,
 * считается одной шахтой. Нажал — выбрал этаж — вышел у дверей на нём.
 *
 * Так лифт работает в любом здании, которое построит и игрок: достаточно
 * повесить кнопки друг над другом. Сервер проверяет, что игрок стоит у
 * кнопки, а этаж назначения — кнопка той же шахты.
 */
public final class Elevator {

    /** Сколько блоков вверх и вниз искать кнопки той же шахты. */
    public static final int REACH = 256;

    private Elevator() {
    }

    /** Кнопки шахты снизу вверх: та же x и z, тот же блок. */
    public static List<BlockPos> floors(Level level, BlockPos at) {
        List<BlockPos> out = new ArrayList<>();
        int low = Math.max(level.getMinBuildHeight(), at.getY() - REACH);
        int high = Math.min(level.getMaxBuildHeight() - 1, at.getY() + REACH);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(at.getX(), low, at.getZ());
        for (int y = low; y <= high; y++) {
            p.setY(y);
            if (level.getBlockState(p).getBlock() instanceof ElevatorBlock) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    public static void open(ServerPlayer player, BlockPos at) {
        List<BlockPos> floors = floors(player.level(), at);
        if (floors.size() < 2) {
            player.displayClientMessage(Component.translatable("citylife.elevator.alone")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putLong("pos", at.asLong());
        ListTag list = new ListTag();
        for (int i = 0; i < floors.size(); i++) {
            CompoundTag floor = new CompoundTag();
            floor.putInt("y", floors.get(i).getY());
            floor.putInt("n", i + 1);
            floor.putBoolean("here", floors.get(i).equals(at));
            list.add(floor);
        }
        data.put("floors", list);
        Net.sendPanel(player, "elevator", data, true);
    }

    /** elevator_go: {pos — кнопка, у которой стоит игрок, y — этаж назначения}. */
    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        if (!"elevator_go".equals(action)) {
            return;
        }
        BlockPos from = BlockPos.of(args.getLong("pos"));
        if (!(player.level().getBlockState(from).getBlock() instanceof ElevatorBlock)
                || player.distanceToSqr(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5) > 36) {
            return;
        }
        BlockPos to = new BlockPos(from.getX(), args.getInt("y"), from.getZ());
        if (to.equals(from) || !floors(player.level(), from).contains(to)) {
            return;
        }
        ride(player, to);
    }

    /** Выйти из лифта у кнопки to: клетка перед ней, ноги на полу этажа. */
    public static void ride(ServerPlayer player, BlockPos to) {
        BlockState state = player.level().getBlockState(to);
        Direction facing = state.getValue(ElevatorBlock.FACING);
        BlockPos stand = to.relative(facing);
        // Кнопка висит на высоте груди: ищем пол под клеткой перед ней.
        BlockPos feet = stand;
        for (int i = 0; i <= 3; i++) {
            BlockPos c = stand.below(i);
            if (!player.level().getBlockState(c.below())
                    .getCollisionShape(player.level(), c.below()).isEmpty()) {
                feet = c;
                break;
            }
        }
        float yaw = facing.toYRot();
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.BLOCKS, 0.6F, 1.6F);
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0);
        // У ненастоящих игроков (тесты, моды-боты) пакет телепорта ничего не
        // двигает; позицию ставим и напрямую — живому игроку это не мешает.
        player.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0);
        level.playSound(null, feet, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS,
                0.6F, 2.0F);
        int floor = floors(level, to).indexOf(to) + 1;
        player.displayClientMessage(Component.translatable("citylife.elevator.arrived", floor)
                .withStyle(ChatFormatting.AQUA), true);
    }
}
