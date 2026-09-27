package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Ввод кода доступа к замку. */
@OnlyIn(Dist.CLIENT)
public class PinPromptScreen extends Screen {
    private final BlockPos pos;
    private final String label;
    private EditBox codeBox;

    public PinPromptScreen(BlockPos pos, String label) {
        super(Component.translatable("citylife.screen.pin"));
        this.pos = pos;
        this.label = label;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        codeBox = new EditBox(font, centerX - 60, centerY - 8, 120, 18,
                Component.translatable("citylife.screen.pin"));
        codeBox.setMaxLength(8);
        addRenderableWidget(codeBox);
        setInitialFocus(codeBox);

        addRenderableWidget(Button.builder(Component.translatable("citylife.button.unlock"),
                        button -> submit())
                .bounds(centerX - 60, centerY + 16, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                        button -> onClose())
                .bounds(centerX + 2, centerY + 16, 58, 20).build());
    }

    private void submit() {
        CompoundTag args = new CompoundTag();
        args.putLong("pos", pos.asLong());
        args.putString("code", codeBox.getValue());
        Net.sendAction("pin_try", args);
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) {   // Enter
            submit();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, label, width / 2, height / 2 - 34, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("citylife.screen.pin_hint"),
                width / 2, height / 2 - 22, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
