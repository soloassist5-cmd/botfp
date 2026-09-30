package dev.lscity.citylife.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: открыть или обновить экран-панель (агентство
 * недвижимости и т.п.). kind выбирает экран, snapshot — его данные.
 */
public record PanelPacket(String kind, CompoundTag snapshot, boolean open) {

    public static void encode(PanelPacket packet, FriendlyByteBuf buf) {
        Net.writeString(buf, packet.kind());
        buf.writeNbt(packet.snapshot());
        buf.writeBoolean(packet.open());
    }

    public static PanelPacket decode(FriendlyByteBuf buf) {
        String kind = Net.readString(buf);
        CompoundTag tag = buf.readNbt();
        return new PanelPacket(kind, tag == null ? new CompoundTag() : tag, buf.readBoolean());
    }

    public static void handle(PanelPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.lscity.citylife.client.ClientHooks.handlePanel(packet)));
        ctx.setPacketHandled(true);
    }
}
