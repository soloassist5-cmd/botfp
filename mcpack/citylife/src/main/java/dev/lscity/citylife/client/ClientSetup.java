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
    public static void onKeys(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(Keys.VEHICLE);
        event.register(Keys.CONTROLS);
    }

    @SubscribeEvent
    public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        // Сиденье невидимо: рисовать нечего.
        event.registerEntityRenderer(Registration.SEAT.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
        // Башня STARK: голо-мониторы, лучи датчиков и печать на 3D-принтере.
        event.registerBlockEntityRenderer(Registration.HOLO_SCREEN_BE.get(),
                dev.lscity.citylife.client.stark.HoloScreenRenderer::new);
        event.registerBlockEntityRenderer(Registration.LASER_BE.get(),
                dev.lscity.citylife.client.stark.LaserRenderer::new);
        event.registerBlockEntityRenderer(Registration.PRINTER_BE.get(),
                dev.lscity.citylife.client.stark.PrinterRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(Registration.PC_CASE_MENU.get(),
                PcCaseScreen::new));
        event.enqueueWork(ClientSetup::fixUserType);
    }

    /**
     * Часть лаунчеров запускает игру с пустым --userType, и тогда у учётки
     * нет типа вовсе (null). Ванильной игре всё равно, а Sym's Armored
     * Industries каждый такт спрашивает тип учётки и на null падает — игра
     * закрывается через несколько секунд после входа в мир. Пустой тип и есть
     * офлайн-учётка, так что ставим ей LEGACY — как и записал бы лаунчер.
     */
    private static void fixUserType() {
        net.minecraft.client.User user = net.minecraft.client.Minecraft.getInstance().getUser();
        if (user == null || user.getType() != null) {
            return;
        }
        try {
            for (java.lang.reflect.Field field : net.minecraft.client.User.class.getDeclaredFields()) {
                if (field.getType() == net.minecraft.client.User.Type.class) {
                    field.setAccessible(true);
                    field.set(user, net.minecraft.client.User.Type.LEGACY);
                    CityLife.LOG.info("City Life: лаунчер не указал тип учётки — считаем её офлайн (legacy)");
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            CityLife.LOG.warn("City Life: не удалось задать тип учётки: {}", e.toString());
        }
    }
}
