package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Панель лифта: кнопки этажей сеткой, как в настоящей кабине, снизу вверх.
 * Этаж, на котором стоишь, подсвечен и не нажимается.
 */
@OnlyIn(Dist.CLIENT)
public class ElevatorScreen extends Screen {

    private static final int CELL = 26;
    private final CompoundTag data;

    public ElevatorScreen(CompoundTag data) {
        super(Component.translatable("citylife.elevator.title"));
        this.data = data;
    }

    private ListTag floors() {
        return data.getList("floors", Tag.TAG_COMPOUND);
    }

    private int cols() {
        return Math.min(6, Math.max(2, (int) Math.ceil(Math.sqrt(floors().size()))));
    }

    private int rows() {
        return (floors().size() + cols() - 1) / cols();
    }

    private int w() {
        return cols() * CELL + 20;
    }

    private int h() {
        return Math.min(height - 16, rows() * CELL + 44);
    }

    @Override
    protected void init() {
        ListTag floors = floors();
        int x0 = (width - w()) / 2 + 10;
        int y0 = (height - h()) / 2 + 30;
        int rows = rows();
        for (int i = 0; i < floors.size(); i++) {
            CompoundTag floor = floors.getCompound(i);
            // Первый этаж — внизу слева, как на панели в кабине.
            int row = rows - 1 - i / cols();
            int col = i % cols();
            int y = y0 + row * CELL;
            if (y + CELL > (height + h()) / 2) {
                continue;
            }
            int target = floor.getInt("y");
            boolean here = floor.getBoolean("here");
            Button button = Button.builder(Component.literal(String.valueOf(floor.getInt("n"))),
                    b -> {
                        CompoundTag args = new CompoundTag();
                        args.putLong("pos", data.getLong("pos"));
                        args.putInt("y", target);
                        Net.sendAction("elevator_go", args);
                        onClose();
                    }).bounds(x0 + col * CELL, y, CELL - 4, CELL - 4).build();
            button.active = !here;
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int x = (width - w()) / 2;
        int y = (height - h()) / 2;
        PhoneUi.roundedRect(g, x, y, w(), h(), 8, 0xF0181C26);
        g.drawCenteredString(font, title, x + w() / 2, y + 9, 0xFFEDEFF7);
        g.drawCenteredString(font, Component.translatable("citylife.elevator.hint"),
                x + w() / 2, y + 19, 0xFF8A90A4);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
