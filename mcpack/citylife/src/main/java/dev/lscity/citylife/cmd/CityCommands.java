package dev.lscity.citylife.cmd;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.lscity.citylife.data.CityData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;

/**
 * Команды /citylife — они же точка интеграции с KubeJS и магазинами:
 * скрипт может начислить зарплату или списать деньги за услугу.
 */
public final class CityCommands {

    private CityCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("citylife");

        root.then(Commands.literal("balance")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    long balance = CityData.get(ctx.getSource().getServer()).balance(player.getUUID());
                    ctx.getSource().sendSuccess(() ->
                            Component.translatable("citylife.cmd.balance", balance), false);
                    return 1;
                })
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            long balance = CityData.get(ctx.getSource().getServer())
                                    .balance(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "citylife.cmd.balance_other",
                                    target.getGameProfile().getName(), balance), false);
                            return 1;
                        })));

        root.then(Commands.literal("money").requires(source -> source.hasPermission(2))
                .then(moneyOp("give"))
                .then(moneyOp("take"))
                .then(moneyOp("set")));

        root.then(Commands.literal("pay")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> {
                                    ServerPlayer from = ctx.getSource().getPlayerOrException();
                                    ServerPlayer to = EntityArgument.getPlayer(ctx, "target");
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    CityData data = CityData.get(ctx.getSource().getServer());
                                    if (!data.transfer(from.getUUID(), to.getUUID(), amount)) {
                                        ctx.getSource().sendFailure(
                                                Component.translatable("citylife.bank.no_money"));
                                        return 0;
                                    }
                                    from.sendSystemMessage(Component.translatable("citylife.bank.sent",
                                            amount, to.getGameProfile().getName()));
                                    to.sendSystemMessage(Component.translatable("citylife.bank.received",
                                            amount, from.getGameProfile().getName()));
                                    return 1;
                                }))));

        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moneyOp(String verb) {
        return Commands.literal(verb)
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(ctx -> {
                                    Collection<ServerPlayer> targets =
                                            EntityArgument.getPlayers(ctx, "targets");
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    CityData data = CityData.get(ctx.getSource().getServer());
                                    for (ServerPlayer target : targets) {
                                        switch (verb) {
                                            case "give" -> data.deposit(target.getUUID(), amount);
                                            case "take" -> data.withdraw(target.getUUID(), amount);
                                            default -> data.setBalance(target.getUUID(), amount);
                                        }
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                            "citylife.cmd.money_done", verb, amount,
                                            targets.size()), true);
                                    return targets.size();
                                })));
    }
}
