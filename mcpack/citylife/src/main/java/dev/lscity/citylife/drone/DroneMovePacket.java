package dev.lscity.citylife.drone;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент пилота → сервер: где дрон сейчас (каждый тик); crash — удар о стену (урон). */
public record DroneMovePacket(int id, double x, double y, double z, float yaw, float pitch, float crash) {

    public static void encode(DroneMovePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.id);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.yaw);
        buf.writeFloat(p.pitch);
        buf.writeFloat(p.crash);
    }

    public static DroneMovePacket decode(FriendlyByteBuf buf) {
        return new DroneMovePacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(DroneMovePacket p, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                Drones.move(ctx.getSender(), p);
            }
        });
        ctx.setPacketHandled(true);
    }
}
