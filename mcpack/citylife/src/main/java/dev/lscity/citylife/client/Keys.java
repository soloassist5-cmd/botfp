package dev.lscity.citylife.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lscity.citylife.CityLife;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши мода. Видны в «Настройки → Управление» в разделе City Life,
 * их можно переназначить, как любые другие.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class Keys {

    /** «Мой транспорт»: запереть, открыть, маршрут, сигнал, ключи. По умолчанию K. */
    public static final KeyMapping VEHICLE = new KeyMapping("key.citylife.vehicle",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.citylife");

    private Keys() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        while (VEHICLE.consumeClick()) {
            dev.lscity.citylife.net.Net.sendAction("garage_open", new CompoundTag());
        }
    }
}
