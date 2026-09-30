package dev.lscity.citylife.cmd;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.nbt.CompoundTag;
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

    /** Что увидел «виртуальный игрок» при клике; пишется только во время проверки. */
    public static final java.util.List<String> CLICK_LOG = new java.util.ArrayList<>();
    public static boolean clickTest;

    public static void note(String text) {
        if (clickTest) {
            CLICK_LOG.add(text);
        }
    }

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

        // Диагностика ассортимента: видно, какие товары собрались, а какие
        // отпали из-за отсутствующего мода.
        root.then(Commands.literal("shops").requires(source -> source.hasPermission(2))
                .executes(context -> {
                    int roles = 0;
                    int offers = 0;
                    for (var entry : dev.lscity.citylife.trade.ShopCatalog.BY_ROLE.entrySet()) {
                        int built = entry.getValue().build().size();
                        int listed = entry.getValue().offers().size();
                        roles++;
                        offers += built;
                        // Пропавший товар — это чужой мод переименовал предмет,
                        // поэтому показываем сам идентификатор, а не только счёт.
                        String lost = entry.getValue().missing();
                        String tail = lost.isEmpty() ? "" : " — нет: " + lost;
                        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                .literal(entry.getKey() + ": " + built + "/" + listed + tail),
                                false);
                    }
                    int finalRoles = roles;
                    int finalOffers = offers;
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                            .literal("ролей " + finalRoles + ", предложений " + finalOffers
                                    + " (без товара — служебные роли)"),
                            false);
                    return roles;
                }));

        // Диагностика недвижимости: сколько объектов в каталоге и сколько куплено.
        root.then(Commands.literal("estate").requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    var all = dev.lscity.citylife.estate.Estate.all();
                    var life = dev.lscity.citylife.data.LifeData.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Недвижимость: "
                            + all.size() + " объектов, куплено " + life.owners().size()), false);
                    return all.size();
                })
                .then(Commands.literal("at")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates
                                .BlockPosArgument.blockPos()).executes(ctx -> {
                                    var pos = net.minecraft.commands.arguments.coordinates
                                            .BlockPosArgument.getBlockPos(ctx, "pos");
                                    var unit = dev.lscity.citylife.estate.Estate.plotAt(pos);
                                    ctx.getSource().sendSuccess(() -> Component.literal(unit == null
                                            ? "здесь нет жилья" : unit.id() + " — " + unit.label()
                                            + ", " + unit.price() + " ₽, дверь "
                                            + unit.door().toShortString()), false);
                                    return unit == null ? 0 : 1;
                                })))
                .then(Commands.literal("give")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            String id = StringArgumentType.getString(ctx, "id");
                                            if (dev.lscity.citylife.estate.Estate.get(id) == null) {
                                                return 0;
                                            }
                                            dev.lscity.citylife.data.LifeData.get(target.server)
                                                    .setOwner(id, target.getUUID(),
                                                            target.getGameProfile().getName(),
                                                            target.level().getGameTime());
                                            return 1;
                                        }))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                            dev.lscity.citylife.data.LifeData.get(ctx.getSource().getServer())
                                    .clearOwner(StringArgumentType.getString(ctx, "id"));
                            return 1;
                        }))));

        // Диагностика жителя: какую роль видит мод и что он ответит на клик.
        root.then(Commands.literal("npc").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.entities()).executes(ctx -> {
                    int n = 0;
                    for (var entity : EntityArgument.getEntities(ctx, "targets")) {
                        String role = dev.lscity.citylife.trade.ShopHandler.roleOf(entity);
                        var shop = role == null ? null
                                : dev.lscity.citylife.trade.ShopCatalog.BY_ROLE.get(role);
                        String line = entity.getName().getString() + " теги=" + entity.getTags()
                                + " роль=" + role + " прилавок="
                                + (shop == null ? "нет" : shop.title() + " товаров "
                                + shop.build().size());
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                        n++;
                    }
                    return n;
                })
                // Клик «виртуальным игроком» по тому же пути событий, что у живого.
                .then(Commands.literal("click").executes(ctx -> {
                    int n = 0;
                    for (var entity : EntityArgument.getEntities(ctx, "targets")) {
                        var level = (net.minecraft.server.level.ServerLevel) entity.level();
                        var fake = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
                        fake.moveTo(entity.getX() + 1, entity.getY(), entity.getZ());
                        CLICK_LOG.clear();
                        clickTest = true;
                        var result = fake.interactOn(entity, net.minecraft.world.InteractionHand.MAIN_HAND);
                        clickTest = false;
                        String line = entity.getName().getString() + " -> " + result + " "
                                + String.join(" | ", CLICK_LOG);
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                        n++;
                    }
                    return n;
                }))));

        // Самотесты мода: жители, жильё, 112, работа, ноутбук, прохожие.
        root.then(Commands.literal("selftest").requires(source -> source.hasPermission(2))
                .executes(ctx -> dev.lscity.citylife.test.SelfTests.start(ctx.getSource())));

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
        event.getDispatcher().register(house());
        dev.lscity.citylife.city.Emergency.register(event.getDispatcher());
        dev.lscity.citylife.jobs.Jobs.register(event.getDispatcher());
    }

    /**
     * /house — своё жильё без похода в агентство: где я стою, мои дома,
     * ключи друзьям. Действует на дом, в котором (или на участке которого)
     * стоит игрок, а если он на улице — на первый купленный.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> house() {
        return Commands.literal("house")
                .executes(ctx -> houseInfo(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("info")
                        .executes(ctx -> houseInfo(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("list").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    var homes = dev.lscity.citylife.estate.EstateServer.homes(player);
                    if (homes.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("citylife.house.none"));
                    }
                    for (var unit : homes) {
                        player.sendSystemMessage(Component.literal("• " + unit.label()
                                + " (" + unit.door().toShortString() + ")"));
                    }
                    return homes.size();
                }))
                .then(Commands.literal("route").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    var unit = myHome(player);
                    if (unit == null) {
                        player.sendSystemMessage(Component.translatable("citylife.house.none"));
                        return 0;
                    }
                    CompoundTag args = new CompoundTag();
                    args.putString("id", unit.id());
                    dev.lscity.citylife.estate.EstateServer.handle(player, "realty_route", args);
                    return 1;
                }))
                .then(Commands.literal("trust")
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var unit = myHome(player);
                            if (unit == null) {
                                player.sendSystemMessage(
                                        Component.translatable("citylife.house.none"));
                                return 0;
                            }
                            dev.lscity.citylife.estate.EstateServer.trust(player, unit,
                                    StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })))
                .then(Commands.literal("untrust")
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var unit = myHome(player);
                            String name = StringArgumentType.getString(ctx, "name");
                            var life = dev.lscity.citylife.data.LifeData.get(player.server);
                            if (unit == null) {
                                return 0;
                            }
                            for (java.util.UUID id : java.util.List.copyOf(life.trusted(unit.id()))) {
                                if (life.trustedName(id).equalsIgnoreCase(name)) {
                                    life.untrust(unit.id(), id);
                                    player.sendSystemMessage(Component.translatable(
                                            "citylife.realty.untrusted", name, unit.address()));
                                    return 1;
                                }
                            }
                            player.sendSystemMessage(Component.translatable("citylife.mail.unknown",
                                    name));
                            return 0;
                        })));
    }

    private static dev.lscity.citylife.estate.Estate.Unit myHome(ServerPlayer player) {
        var here = dev.lscity.citylife.estate.Estate.plotAt(player.blockPosition());
        var owner = here == null ? null
                : dev.lscity.citylife.data.LifeData.get(player.server).owner(here.id());
        if (owner != null && owner.id().equals(player.getUUID())) {
            return here;
        }
        var homes = dev.lscity.citylife.estate.EstateServer.homes(player);
        return homes.isEmpty() ? null : homes.get(0);
    }

    private static int houseInfo(ServerPlayer player) {
        var unit = dev.lscity.citylife.estate.Estate.plotAt(player.blockPosition());
        if (unit == null) {
            player.sendSystemMessage(Component.translatable("citylife.house.street"));
            return 0;
        }
        var owner = dev.lscity.citylife.data.LifeData.get(player.server).owner(unit.id());
        player.sendSystemMessage(Component.literal(unit.label() + " · " + unit.rooms() + " · "
                + dev.lscity.citylife.economy.Money.format(unit.price())));
        player.sendSystemMessage(owner == null
                ? Component.translatable("citylife.realty.free")
                : Component.translatable("citylife.realty.owner", owner.name()));
        return 1;
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
