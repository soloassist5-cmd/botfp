package dev.lscity.citylife.drone;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → пилот: начать (on) или закончить управление дроном id. С
 * координатами — поправка: сервер не принял прыжок, дрон на самом деле здесь.
 */
public record DroneControlPacket(int id, boolean on, boolean fix, double x, double y, double z) {

    public DroneControlPacket(int id, boolean on) {
        this(id, on, false, 0, 0, 0);
    }

    public DroneControlPacket(int id, boolean on, double x, double y, double z) {
        this(id, on, true, x, y, z);
    }

    public static void encode(DroneControlPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.id);
        buf.writeBoolean(p.on);
        buf.writeBoolean(p.fix);
        if (p.fix) {
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
        }
    }

    public static DroneControlPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean on = buf.readBoolean();
        boolean fix = buf.readBoolean();
        return fix ? new DroneControlPacket(id, on, true, buf.readDouble(), buf.readDouble(), buf.readDouble())
                : new DroneControlPacket(id, on);
    }

    public static void handle(DroneControlPacket p, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> dev.lscity.citylife.client.drone.DronePilot.control(p)));
        ctx.setPacketHandled(true);
    }
}
