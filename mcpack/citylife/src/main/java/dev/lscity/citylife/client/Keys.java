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

    /** Справочник управления: клавиатура с подписями действий. По умолчанию F10. */
    public static final KeyMapping CONTROLS = new KeyMapping("key.citylife.controls",
            KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10,
            "key.categories.citylife");

    private Keys() {
    }

    /** Раскладка сборки применяется, когда игра уже загрузила настройки. */
    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            dev.lscity.citylife.client.controls.Controls.onStart(Minecraft.getInstance());
        }
    }

    /** /controls — открыть справочник управления. */
    @SubscribeEvent
    public static void onClientCommands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("controls").executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            // Команда выполняется до закрытия чата: экран откроем на следующем кадре.
            mc.tell(() -> mc.setScreen(new dev.lscity.citylife.client.controls.ControlsScreen(null)));
            return 1;
        }));
    }

    /** Кнопка «Справочник клавиш» в меню паузы и в настройках управления. */
    @SubscribeEvent
    public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        boolean pause = screen instanceof net.minecraft.client.gui.screens.PauseScreen;
        boolean binds = screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen
                || screen instanceof net.minecraft.client.gui.screens.controls.ControlsScreen;
        if (!pause && !binds) {
            return;
        }
        var label = net.minecraft.network.chat.Component.translatable("citylife.controls.open");
        int w = Minecraft.getInstance().font.width(label) + 16;
        event.addListener(net.minecraft.client.gui.components.Button.builder(label, b ->
                Minecraft.getInstance().setScreen(new dev.lscity.citylife.client.controls.ControlsScreen(screen)))
                .bounds(screen.width - w - 6, 6, w, 20).build());
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && CONTROLS.consumeClick()) {
            minecraft.setScreen(new dev.lscity.citylife.client.controls.ControlsScreen(null));
            return;
        }
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        while (VEHICLE.consumeClick()) {
            dev.lscity.citylife.net.Net.sendAction("garage_open", new CompoundTag());
        }
    }
}
