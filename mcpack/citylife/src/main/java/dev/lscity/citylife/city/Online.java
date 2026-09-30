package dev.lscity.citylife.city;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Игроки в сети — для звонков, такси и дежурств.
 *
 * Самотесты подкладывают сюда своих ненастоящих игроков (FakePlayer): в
 * список сервера их не добавить, а проверять звонок между двумя людьми
 * как-то надо.
 */
public final class Online {

    public static final List<ServerPlayer> TEST = new ArrayList<>();

    private Online() {
    }

    public static List<ServerPlayer> players(MinecraftServer server) {
        if (TEST.isEmpty()) {
            return server.getPlayerList().getPlayers();
        }
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        all.addAll(TEST);
        return all;
    }

    public static ServerPlayer get(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player;
        }
        for (ServerPlayer fake : TEST) {
            if (fake.getUUID().equals(id)) {
                return fake;
            }
        }
        return null;
    }
}
