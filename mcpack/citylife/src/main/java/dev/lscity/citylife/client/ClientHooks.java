package dev.lscity.citylife.client;

import dev.lscity.citylife.client.screen.AtmScreen;
import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.screen.PinPromptScreen;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.net.NavPacket;
import dev.lscity.citylife.net.OpenAtmPacket;
import dev.lscity.citylife.net.OpenDevicePacket;
import dev.lscity.citylife.net.OpenPinPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Точки входа клиентской части (вызываются только на клиенте). */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void handleDevice(OpenDevicePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DeviceScreen screen && !packet.open()) {
            screen.update(packet.snapshot());
            return;
        }
        if (packet.open()) {
            minecraft.setScreen(new DeviceScreen(packet.snapshot()));
        }
    }

    public static void handleAtm(OpenAtmPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AtmScreen screen) {
            screen.update(packet.snapshot());
            return;
        }
        if (packet.open()) {
            minecraft.setScreen(new AtmScreen(packet.pos(), packet.snapshot()));
        }
    }

    public static void handlePanel(dev.lscity.citylife.net.PanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if ("hud".equals(packet.kind())) {
            HudOverlay.update(packet.snapshot());
            return;
        }
        if ("checkout".equals(packet.kind())) {
            if (minecraft.screen instanceof dev.lscity.citylife.client.screen.CheckoutScreen screen) {
                screen.update(packet.snapshot());
            } else if (packet.open()) {
                minecraft.setScreen(
                        new dev.lscity.citylife.client.screen.CheckoutScreen(packet.snapshot()));
            }
            return;
        }
        if ("garage".equals(packet.kind())) {
            if (minecraft.screen instanceof dev.lscity.citylife.client.screen.VehicleScreen screen) {
                screen.update(packet.snapshot());
            } else if (packet.open()) {
                minecraft.setScreen(
                        new dev.lscity.citylife.client.screen.VehicleScreen(packet.snapshot()));
            }
            return;
        }
        if ("elevator".equals(packet.kind())) {
            if (packet.open()) {
                minecraft.setScreen(
                        new dev.lscity.citylife.client.screen.ElevatorScreen(packet.snapshot()));
            }
            return;
        }
        if ("realty".equals(packet.kind())) {
            if (minecraft.screen instanceof dev.lscity.citylife.client.screen.RealtyScreen screen) {
                screen.update(packet.snapshot());
            } else if (packet.open()) {
                minecraft.setScreen(
                        new dev.lscity.citylife.client.screen.RealtyScreen(packet.snapshot()));
            }
        }
    }

    public static void handleNav(NavPacket packet) {
        if (packet.active()) {
            NavClient.set(new Waypoint(packet.name(), packet.x(), packet.y(), packet.z(),
                    packet.icon(), true));
        } else {
            NavClient.clear();
        }
    }

    public static void handlePinPrompt(OpenPinPacket packet) {
        Minecraft.getInstance().setScreen(new PinPromptScreen(packet.pos(), packet.label()));
    }
}
