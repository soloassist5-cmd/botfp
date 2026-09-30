package dev.lscity.citylife.phone;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;

import java.util.UUID;

/** Звонок в голосовом чате: скрытая приватная группа ISOLATED с паролем. */
final class VoiceImpl implements Voice.Impl {

    private final VoicechatServerApi server;

    VoiceImpl(VoicechatServerApi server) {
        this.server = server;
    }

    @Override
    public boolean join(UUID a, UUID b, String name) {
        Group group = server.groupBuilder()
                .setName(name)
                .setPassword(UUID.randomUUID().toString())
                .setType(Group.Type.ISOLATED)
                .setPersistent(false)
                .setHidden(true)
                .build();
        boolean both = true;
        for (UUID id : new UUID[]{a, b}) {
            VoicechatConnection connection = server.getConnectionOf(id);
            if (connection == null || !connection.isInstalled()) {
                both = false;
                continue;
            }
            connection.setGroup(group);
        }
        return both;
    }

    @Override
    public void leave(UUID player) {
        VoicechatConnection connection = server.getConnectionOf(player);
        if (connection != null) {
            connection.setGroup(null);
        }
    }
}
