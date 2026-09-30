package dev.lscity.citylife.phone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.city.Online;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.item.SimCardItem;
import dev.lscity.citylife.net.DeviceServer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Звонки по номеру SIM-карты.
 *
 * Набрал номер — у того, у кого в инвентаре телефон с этой SIM, звонит
 * телефон (звук и кнопки «Ответить» / «Сбросить» в чате, и то же в
 * приложении «Телефон»). Ответил — оба попадают в приватную группу Simple
 * Voice Chat и слышат друг друга на любом расстоянии. Не ответил за 30
 * секунд — пропущенный вызов приходит СМС.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Calls {

    private static final long RING_TICKS = 30 * 20L;

    /** Звонок: кто, кому, с каких номеров, в каком состоянии. */
    public static final class Call {
        final UUID caller;
        final UUID callee;
        final String callerName;
        final String calleeName;
        final int from;
        final int to;
        final long started;
        boolean active;
        long answered;

        Call(ServerPlayer caller, ServerPlayer callee, int from, int to, long now) {
            this.caller = caller.getUUID();
            this.callee = callee.getUUID();
            this.callerName = caller.getGameProfile().getName();
            this.calleeName = callee.getGameProfile().getName();
            this.from = from;
            this.to = to;
            this.started = now;
        }

        boolean involves(UUID id) {
            return caller.equals(id) || callee.equals(id);
        }
    }

    private static final List<Call> CALLS = new ArrayList<>();
    /** Последние звонки игрока для списка в приложении. */
    private static final java.util.Map<UUID, LinkedList<String>> RECENT = new java.util.HashMap<>();

    private Calls() {
    }

    public static Call of(UUID player) {
        for (Call call : CALLS) {
            if (call.involves(player)) {
                return call;
            }
        }
        return null;
    }

    private static ServerPlayer holderOf(MinecraftServer server, int number) {
        for (ServerPlayer p : Online.players(server)) {
            if (DeviceServer.carries(p, number)) {
                return p;
            }
        }
        return null;
    }

    private static void remember(UUID player, String line) {
        LinkedList<String> list = RECENT.computeIfAbsent(player, k -> new LinkedList<>());
        list.addFirst(line);
        while (list.size() > 10) {
            list.removeLast();
        }
    }

    private static MutableComponent button(String key, String command, ChatFormatting colour) {
        return Component.literal("[").append(Component.translatable(key)).append("]")
                .withStyle(Style.EMPTY.withColor(colour)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    // --- действия -----------------------------------------------------------------

    /** Набрать номер со своей SIM (from). */
    public static void dial(ServerPlayer caller, int from, int number) {
        if (from == 0) {
            caller.displayClientMessage(Component.translatable("citylife.call.no_sim")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (of(caller.getUUID()) != null) {
            caller.displayClientMessage(Component.translatable("citylife.call.busy_self")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        CityData data = CityData.get(caller.server);
        if (number == from || !data.numberExists(number)) {
            caller.displayClientMessage(Component.translatable("citylife.msg.no_number",
                    SimCardItem.format(Math.max(0, number))).withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerPlayer callee = holderOf(caller.server, number);
        if (callee == null) {
            caller.sendSystemMessage(Component.translatable("citylife.call.unreachable",
                    SimCardItem.format(number)).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (of(callee.getUUID()) != null) {
            caller.sendSystemMessage(Component.translatable("citylife.call.busy",
                    SimCardItem.format(number)).withStyle(ChatFormatting.GRAY));
            return;
        }
        Call call = new Call(caller, callee, from, number, caller.level().getGameTime());
        CALLS.add(call);
        caller.sendSystemMessage(Component.translatable("citylife.call.dialing",
                SimCardItem.format(number)).withStyle(ChatFormatting.AQUA)
                .append(" ").append(button("citylife.call.hangup", "/phone hangup",
                        ChatFormatting.RED)));
        callee.sendSystemMessage(Component.translatable("citylife.call.incoming",
                SimCardItem.format(from), call.callerName).withStyle(ChatFormatting.AQUA)
                .append(" ").append(button("citylife.call.answer", "/phone answer",
                        ChatFormatting.GREEN))
                .append(" ").append(button("citylife.call.decline", "/phone hangup",
                        ChatFormatting.RED)));
        remember(caller.getUUID(), "→ " + SimCardItem.format(number) + " " + call.calleeName);
        remember(callee.getUUID(), "← " + SimCardItem.format(from) + " " + call.callerName);
    }

    /** Ответить на входящий. */
    public static void answer(ServerPlayer callee) {
        Call call = of(callee.getUUID());
        if (call == null || call.active || !call.callee.equals(callee.getUUID())) {
            return;
        }
        call.active = true;
        call.answered = callee.level().getGameTime();
        ServerPlayer caller = Online.get(callee.server, call.caller);
        boolean voice = Voice.join(call.caller, call.callee,
                SimCardItem.format(call.from) + " ↔ " + SimCardItem.format(call.to));
        Component note = Component.translatable(voice ? "citylife.call.connected"
                : Voice.available() ? "citylife.call.connected_no_client"
                : "citylife.call.connected_no_voice").withStyle(ChatFormatting.GREEN);
        Component hang = button("citylife.call.hangup", "/phone hangup", ChatFormatting.RED);
        callee.sendSystemMessage(note.copy().append(" ").append(hang));
        if (caller != null) {
            caller.sendSystemMessage(note.copy().append(" ").append(hang));
        }
    }

    /** Сбросить: отказ на входящий, отмена исходящего или конец разговора. */
    public static void hangup(ServerPlayer player) {
        Call call = of(player.getUUID());
        if (call == null) {
            return;
        }
        end(player.server, call, player.getUUID());
    }

    private static void end(MinecraftServer server, Call call, UUID by) {
        CALLS.remove(call);
        Voice.leave(call.caller);
        Voice.leave(call.callee);
        long seconds = call.active ? (server.overworld().getGameTime() - call.answered) / 20 : 0;
        for (UUID id : new UUID[]{call.caller, call.callee}) {
            ServerPlayer p = Online.get(server, id);
            if (p == null) {
                continue;
            }
            Component text = call.active
                    ? Component.translatable("citylife.call.ended", seconds / 60 + ":"
                    + String.format("%02d", seconds % 60))
                    : Component.translatable(id.equals(by) ? "citylife.call.cancelled"
                    : "citylife.call.declined");
            p.sendSystemMessage(text.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    // --- гудки, звонок и пропущенные ---------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || CALLS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (Iterator<Call> it = CALLS.iterator(); it.hasNext(); ) {
            Call call = it.next();
            ServerPlayer caller = Online.get(server, call.caller);
            ServerPlayer callee = Online.get(server, call.callee);
            if (caller == null || callee == null) {
                it.remove();
                Voice.leave(call.caller);
                Voice.leave(call.callee);
                continue;
            }
            if (call.active) {
                continue;
            }
            if (now - call.started > RING_TICKS) {
                it.remove();
                CityData.get(server).deliver(new dev.lscity.citylife.data.Message(call.from,
                        call.callerName, call.to, dev.lscity.citylife.data.Texts.ru(
                        "citylife.call.missed_sms"), now));
                callee.sendSystemMessage(Component.translatable("citylife.call.missed",
                        SimCardItem.format(call.from), call.callerName).withStyle(ChatFormatting.GRAY));
                caller.sendSystemMessage(Component.translatable("citylife.call.no_answer")
                        .withStyle(ChatFormatting.GRAY));
                continue;
            }
            if ((now - call.started) % 30 == 0) {
                callee.level().playSound(null, callee.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                        SoundSource.PLAYERS, 1.0F, 1.4F);
                caller.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS,
                        0.5F, 0.8F);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Call call = of(player.getUUID());
            if (call != null) {
                end(player.server, call, player.getUUID());
            }
        }
    }

    // --- для телефона -------------------------------------------------------------

    public static CompoundTag snapshot(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("voice", Voice.available());
        Call call = of(player.getUUID());
        if (call != null) {
            boolean outgoing = call.caller.equals(player.getUUID());
            tag.putString("state", call.active ? "active" : outgoing ? "outgoing" : "incoming");
            tag.putString("peer", outgoing ? call.calleeName : call.callerName);
            tag.putString("number", SimCardItem.format(outgoing ? call.to : call.from));
            long since = call.active ? call.answered : call.started;
            tag.putLong("secs", (player.level().getGameTime() - since) / 20);
        }
        net.minecraft.nbt.ListTag recent = new net.minecraft.nbt.ListTag();
        for (String line : RECENT.getOrDefault(player.getUUID(), new LinkedList<>())) {
            recent.add(net.minecraft.nbt.StringTag.valueOf(line));
        }
        tag.put("recent", recent);
        return tag;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phone")
                .then(Commands.literal("answer").executes(ctx -> {
                    answer(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("hangup").executes(ctx -> {
                    hangup(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("call").then(Commands.argument("number",
                        StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int number = parse(StringArgumentType.getString(ctx, "number"));
                            dial(player, DeviceServer.simInInventory(player), number);
                            return 1;
                        }))));
    }

    /** «48-21», «4821» -> 4821. */
    public static int parse(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
