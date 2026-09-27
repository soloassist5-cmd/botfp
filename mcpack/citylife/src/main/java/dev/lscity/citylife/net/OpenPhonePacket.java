package dev.lscity.citylife.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: снимок состояния телефона. */
public record OpenPhonePacket(CompoundTag snapshot, boolean open) {

    public static void encode(OpenPhonePacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.snapshot());
        buf.writeBoolean(packet.open());
    }

    public static OpenPhonePacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new OpenPhonePacket(tag == null ? new CompoundTag() : tag, buf.readBoolean());
    }

    public static void handle(OpenPhonePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.lscity.citylife.client.ClientHooks.handlePhone(packet)));
        ctx.setPacketHandled(true);
    }
}
