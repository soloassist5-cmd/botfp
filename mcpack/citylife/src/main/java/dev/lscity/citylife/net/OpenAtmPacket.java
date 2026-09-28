package dev.lscity.citylife.net;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: открыть или обновить экран банкомата. */
public record OpenAtmPacket(BlockPos pos, CompoundTag snapshot, boolean open) {

    public static void encode(OpenAtmPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos());
        buf.writeNbt(packet.snapshot());
        buf.writeBoolean(packet.open());
    }

    public static OpenAtmPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag tag = buf.readNbt();
        return new OpenAtmPacket(pos, tag == null ? new CompoundTag() : tag, buf.readBoolean());
    }

    public static void handle(OpenAtmPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.lscity.citylife.client.ClientHooks.handleAtm(packet)));
        ctx.setPacketHandled(true);
    }
}
