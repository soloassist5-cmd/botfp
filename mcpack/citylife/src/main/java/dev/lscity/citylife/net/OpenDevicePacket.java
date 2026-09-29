package dev.lscity.citylife.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: снимок состояния гаджета (телефон, планшет, ноутбук, компьютер). */
public record OpenDevicePacket(CompoundTag snapshot, boolean open) {

    public static void encode(OpenDevicePacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.snapshot());
        buf.writeBoolean(packet.open());
    }

    public static OpenDevicePacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new OpenDevicePacket(tag == null ? new CompoundTag() : tag, buf.readBoolean());
    }

    public static void handle(OpenDevicePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.lscity.citylife.client.ClientHooks.handleDevice(packet)));
        ctx.setPacketHandled(true);
    }
}
