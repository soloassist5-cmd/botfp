package dev.lscity.citylife.client;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.client.screen.PcCaseScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Клиентская настройка: какой экран открывать для окна корпуса ПК. */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(Registration.PC_CASE_MENU.get(),
                PcCaseScreen::new));
    }
}
