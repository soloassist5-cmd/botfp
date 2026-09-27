package dev.lscity.citylife.client;

import dev.lscity.citylife.client.screen.PhoneScreen;
import dev.lscity.citylife.client.screen.PinPromptScreen;
import dev.lscity.citylife.net.OpenPhonePacket;
import dev.lscity.citylife.net.OpenPinPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Точки входа клиентской части (вызываются только на клиенте). */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void handlePhone(OpenPhonePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PhoneScreen screen) {
            screen.update(packet.snapshot());
            return;
        }
        if (packet.open()) {
            minecraft.setScreen(new PhoneScreen(packet.snapshot()));
        }
    }

    public static void handlePinPrompt(OpenPinPacket packet) {
        Minecraft.getInstance().setScreen(new PinPromptScreen(packet.pos(), packet.label()));
    }
}
