package dev.lscity.citylife.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: действие в гаджете или банкомате. Сервер всё проверяет сам. */
public record ActionPacket(String action, CompoundTag args) {

    public static void encode(ActionPacket packet, FriendlyByteBuf buf) {
        Net.writeString(buf, packet.action());
        buf.writeNbt(packet.args());
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        String action = Net.readString(buf);
        CompoundTag tag = buf.readNbt();
        return new ActionPacket(action, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ActionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // Банкомат и телефон ходят по одному каналу, но логика у них разная:
            // действия банкомата проверяют расстояние до блока, телефонные — нет.
            if (packet.action().startsWith("atm_")) {
                if (AtmServer.handle(player, packet.action(), packet.args())) {
                    AtmServer.sync(player,
                            net.minecraft.core.BlockPos.of(packet.args().getLong("pos")));
                }
            } else if (packet.action().startsWith("checkout_")) {
                dev.lscity.citylife.trade.Checkout.handle(player, packet.action(), packet.args());
            } else if (packet.action().startsWith("garage_")) {
                dev.lscity.citylife.vehicle.Garage.handle(player, packet.action(), packet.args());
            } else if (packet.action().startsWith("elevator_")) {
                dev.lscity.citylife.building.Elevator.handle(player, packet.action(), packet.args());
            } else if (packet.action().startsWith("stark_")) {
                dev.lscity.citylife.stark.StarkSecurity.handle(player, packet.action(), packet.args());
            } else if (packet.action().startsWith("printer_")) {
                dev.lscity.citylife.stark.Printer.handle(player, packet.action(), packet.args());
            } else if (packet.action().startsWith("realty_")) {
                dev.lscity.citylife.estate.EstateServer.handle(player, packet.action(),
                        packet.args());
            } else {
                DeviceServer.handle(player, packet.action(), packet.args());
            }
        });
        ctx.setPacketHandled(true);
    }
}
