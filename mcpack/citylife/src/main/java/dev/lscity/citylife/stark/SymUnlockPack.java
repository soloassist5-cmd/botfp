package dev.lscity.citylife.stark;

import dev.lscity.citylife.CityLife;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Встроенный датапак packs/sym_unlock: файлы сил Sym's Armored Industries без
 * «покупки» способностей очками уровня (tools/gen_sym_unlock.py). Файлы мода
 * лежат в общем пакете ресурсов модов, где порядок не гарантирован, поэтому
 * наш пакет — отдельный, обязательный и всегда сверху.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SymUnlockPack {

    private SymUnlockPack() {
    }

    @SubscribeEvent
    public static void onPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded("sym_industries")) {
            return;
        }
        var file = ModList.get().getModFileById(CityLife.MOD_ID).getFile();
        Path root = file.findResource("packs", "sym_unlock");
        if (!Files.isDirectory(root)) {
            CityLife.LOG.warn("City Life: нет встроенного пакета packs/sym_unlock");
            return;
        }
        Pack pack = Pack.readMetaAndCreate(CityLife.MOD_ID + ":sym_unlock",
                Component.literal("City Life: костюмы без прокачки"), true,
                id -> new PathPackResources(id, true, root), PackType.SERVER_DATA, Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
