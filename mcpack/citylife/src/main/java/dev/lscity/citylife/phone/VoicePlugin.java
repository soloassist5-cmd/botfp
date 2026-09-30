package dev.lscity.citylife.phone;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import dev.lscity.citylife.CityLife;

/**
 * Связь с Simple Voice Chat. Этот класс загружает сам голосовой мод (по
 * аннотации), поэтому без него он просто не используется, и звонки
 * работают как «набрал — ответили» без голоса.
 */
@ForgeVoicechatPlugin
public class VoicePlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return CityLife.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class,
                event -> {
                    Voice.connect(new VoiceImpl(event.getVoicechat()));
                    CityLife.LOG.info("City Life: звонки по телефону идут через Simple Voice Chat");
                });
    }
}
