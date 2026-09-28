package dev.lscity.citylife.net;

import dev.lscity.citylife.CityLife;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Сетевой канал мода: телефон синхронизируется снимками состояния. */
public final class Net {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new net.minecraft.resources.ResourceLocation(CityLife.MOD_ID, "main"))
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .networkProtocolVersion(() -> VERSION)
            .simpleChannel();

    private Net() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        int id = 0;
        CHANNEL.messageBuilder(OpenPhonePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPhonePacket::encode)
                .decoder(OpenPhonePacket::decode)
                .consumerMainThread(OpenPhonePacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenPinPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPinPacket::encode)
                .decoder(OpenPinPacket::decode)
                .consumerMainThread(OpenPinPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenAtmPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenAtmPacket::encode)
                .decoder(OpenAtmPacket::decode)
                .consumerMainThread(OpenAtmPacket::handle)
                .add();
        CHANNEL.messageBuilder(NavPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NavPacket::encode)
                .decoder(NavPacket::decode)
                .consumerMainThread(NavPacket::handle)
                .add();
        CHANNEL.messageBuilder(ActionPacket.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ActionPacket::encode)
                .decoder(ActionPacket::decode)
                .consumerMainThread(ActionPacket::handle)
                .add();
    }

    /** Открыть телефон у игрока: собрать снимок и отправить клиенту. */
    public static void openPhone(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenPhonePacket(PhoneServer.snapshot(player), true));
    }

    /** Обновить уже открытый телефон. */
    public static void syncPhone(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenPhonePacket(PhoneServer.snapshot(player), false));
    }

    /** Отправить игроку его маршрут: при выборе цели, отмене и входе в мир. */
    public static void sendRoute(ServerPlayer player, dev.lscity.citylife.data.Waypoint point) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), NavPacket.of(point));
    }

    public static void openPinPrompt(ServerPlayer player, BlockPos pos, String label) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenPinPacket(pos, label));
    }

    public static void sendAction(String action, CompoundTag args) {
        CHANNEL.sendToServer(new ActionPacket(action, args));
    }

    static void writeString(FriendlyByteBuf buf, String value) {
        buf.writeUtf(value, 256);
    }

    static String readString(FriendlyByteBuf buf) {
        return buf.readUtf(256);
    }
}
