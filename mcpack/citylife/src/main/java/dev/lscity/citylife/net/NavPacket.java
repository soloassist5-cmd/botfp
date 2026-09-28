package dev.lscity.citylife.net;

import dev.lscity.citylife.data.Waypoint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: активный маршрут навигатора.
 *
 * Отдельным пакетом, а не в снимке телефона: линию на земле видно и с
 * закрытым телефоном, а снимок приходит только пока открыт экран.
 */
public record NavPacket(boolean active, String name, int x, int y, int z, String icon) {

    public static NavPacket of(Waypoint point) {
        return point == null
                ? new NavPacket(false, "", 0, 0, 0, "pin")
                : new NavPacket(true, point.name(), point.x(), point.y(), point.z(), point.icon());
    }

    public static void encode(NavPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active());
        Net.writeString(buf, packet.name());
        buf.writeVarInt(packet.x());
        buf.writeVarInt(packet.y());
        buf.writeVarInt(packet.z());
        Net.writeString(buf, packet.icon());
    }

    public static NavPacket decode(FriendlyByteBuf buf) {
        return new NavPacket(buf.readBoolean(), Net.readString(buf), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), Net.readString(buf));
    }

    public static void handle(NavPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.lscity.citylife.client.ClientHooks.handleNav(packet)));
        ctx.setPacketHandled(true);
    }
}
