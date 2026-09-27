package dev.lscity.citylife;

import com.mojang.logging.LogUtils;
import dev.lscity.citylife.cmd.CityCommands;
import dev.lscity.citylife.net.Net;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * City Life — телефоны и умные замки для городской сборки.
 *
 * Мод сознательно не зависит ни от одного другого мода: экономика,
 * сообщения и замки работают сами по себе, а связка с Lightman's Currency
 * и магазинами делается через команды из KubeJS.
 */
@Mod(CityLife.MOD_ID)
public class CityLife {
    public static final String MOD_ID = "citylife";
    public static final Logger LOG = LogUtils.getLogger();

    public CityLife() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        Registration.register(modBus);
        modBus.addListener(Net::onCommonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CityConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(CityCommands.class);
        LOG.info("City Life: телефоны и умные замки загружены");
    }
}
