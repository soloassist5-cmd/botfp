package dev.lscity.citylife.phone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.city.Online;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.jobs.Duty;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Такси по телефону: заказ уходит игрокам на смене «Такси» (кнопка в
 * «Работе» или /duty taxi). Кто принял — навигатор ведёт его к клиенту,
 * маршрут обновляется, пока тот ходит. Доехал — город доплачивает за
 * подачу, а поездку клиент оплачивает водителю сам (перевод в «Банке»).
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Taxi {

    public static final long PICKUP_PAY = 100;

    private static final class Order {
        final int id;
        final UUID client;
        UUID driver;
        final long placed;

        Order(int id, UUID client, long placed) {
            this.id = id;
            this.client = client;
            this.placed = placed;
        }
    }

    private static final List<Order> ORDERS = new ArrayList<>();
    private static int nextOrder = 1;

    private Taxi() {
    }

    /** Заказать такси; вернёт номер заказа (0 — не вышло). */
    public static int order(ServerPlayer client) {
        for (Order o : ORDERS) {
            if (o.client.equals(client.getUUID())) {
                client.displayClientMessage(Component.translatable("citylife.taxi.already")
                        .withStyle(ChatFormatting.YELLOW), false);
                return 0;
            }
        }
        List<ServerPlayer> drivers = Duty.onDuty(client, "taxi");
        if (drivers.isEmpty()) {
            client.displayClientMessage(Component.translatable("citylife.taxi.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        Order order = new Order(nextOrder++, client.getUUID(), client.level().getGameTime());
        ORDERS.add(order);
        for (ServerPlayer driver : drivers) {
            driver.sendSystemMessage(Component.translatable("citylife.taxi.request",
                    client.getGameProfile().getName(),
                    (int) driver.position().distanceTo(client.position()))
                    .withStyle(ChatFormatting.YELLOW).append(" ")
                    .append(Component.literal("[").append(Component.translatable(
                            "citylife.taxi.accept")).append("]").withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND, "/taxi accept " + order.id)))));
        }
        client.displayClientMessage(Component.translatable("citylife.taxi.sent", drivers.size())
                .withStyle(ChatFormatting.AQUA), false);
        return order.id;
    }

    public static int accept(ServerPlayer driver, int id) {
        if (!"taxi".equals(Duty.of(driver))) {
            return 0;
        }
        for (Order o : ORDERS) {
            if (o.id != id) {
                continue;
            }
            if (o.driver != null) {
                break;
            }
            o.driver = driver.getUUID();
            ServerPlayer client = Online.get(driver.server, o.client);
            if (client != null) {
                client.sendSystemMessage(Component.translatable("citylife.taxi.coming",
                        driver.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
                route(driver, client);
            }
            driver.sendSystemMessage(Component.translatable("citylife.taxi.accepted")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        }
        driver.displayClientMessage(Component.translatable("citylife.taxi.taken")
                .withStyle(ChatFormatting.GRAY), true);
        return 0;
    }

    private static void route(ServerPlayer driver, ServerPlayer client) {
        Waypoint point = new Waypoint(Texts.ru("citylife.taxi.route",
                client.getGameProfile().getName()), client.getBlockX(), client.getBlockY(),
                client.getBlockZ(), "pin", false);
        CityData.get(driver.server).setRoute(driver.getUUID(), point);
        Net.sendRoute(driver, point);
    }

    /** Раз в 5 секунд: маршрут за клиентом, подача, просроченные заказы. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || ORDERS.isEmpty()
                || event.getServer().overworld().getGameTime() % 100 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (Iterator<Order> it = ORDERS.iterator(); it.hasNext(); ) {
            Order o = it.next();
            ServerPlayer client = Online.get(server, o.client);
            ServerPlayer driver = o.driver == null ? null
                    : Online.get(server, o.driver);
            if (client == null || now - o.placed > 20L * 600 || (o.driver != null && driver == null)) {
                it.remove();
                if (client != null) {
                    client.sendSystemMessage(Component.translatable("citylife.taxi.expired")
                            .withStyle(ChatFormatting.GRAY));
                }
                continue;
            }
            if (driver == null) {
                continue;
            }
            if (driver.level() == client.level() && driver.distanceTo(client) < 8) {
                it.remove();
                CityData.get(server).deposit(driver.getUUID(), PICKUP_PAY,
                        Texts.ru("citylife.statement.taxi_pickup", client.getGameProfile().getName()),
                        now);
                LifeData.get(server).recordJob(driver.getUUID(), PICKUP_PAY);
                driver.sendSystemMessage(Component.translatable("citylife.taxi.arrived_driver",
                        Money.format(PICKUP_PAY)).withStyle(ChatFormatting.GOLD));
                client.sendSystemMessage(Component.translatable("citylife.taxi.arrived_client",
                        driver.getGameProfile().getName()).withStyle(ChatFormatting.AQUA));
                CityData.get(server).setRoute(driver.getUUID(), null);
                Net.sendRoute(driver, null);
            } else {
                route(driver, client);
            }
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("taxi")
                .executes(ctx -> order(ctx.getSource().getPlayerOrException()) > 0 ? 1 : 0)
                .then(Commands.literal("accept").then(Commands.argument("order",
                        IntegerArgumentType.integer(1)).executes(ctx -> accept(
                        ctx.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(ctx, "order"))))));
    }
}
