package dev.lscity.citylife.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: запросить код доступа к замку. */
public record OpenPinPacket(BlockPos pos, String label) {

    public static void encode(OpenPinPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos());
        Net.writeString(buf, packet.label());
    }

    public static OpenPinPacket decode(FriendlyByteBuf buf) {
        return new OpenPinPacket(buf.readBlockPos(), Net.readString(buf));
    }

    public static void handle(OpenPinPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.lscity.citylife.client.ClientHooks.handlePinPrompt(packet)));
        ctx.setPacketHandled(true);
    }
}
