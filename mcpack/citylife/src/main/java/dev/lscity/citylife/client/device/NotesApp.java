package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Заметки: одна страница текста на устройство.
 *
 * Хранится в самом гаджете, поэтому переживает перезаход и уходит вместе
 * с телефоном, если его отдать.
 */
class NotesApp extends DeviceApp {

    NotesApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("notes");
    }

    @Override
    public void init(int[] area) {
        var box = screen.input("notes", area[0], area[1] + 4, area[2], "citylife.notes.hint", 400);
        if (box.getValue().isEmpty()) {
            box.setValue(screen.data().getString("notes"));
        }
    }

    private int[] saveRect(int[] area) {
        String label = Component.translatable("citylife.notes.save").getString();
        int w = screen.buttonWidth(label);
        return new int[]{area[0] + area[2] - w, area[1] + 26, w, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        screen.card(g, new int[]{area[0] - 3, area[1], area[2] + 6, 16}, false);
        int[] save = saveRect(area);
        screen.button(g, save[0], save[1], save[2],
                Component.translatable("citylife.notes.save").getString(), 0xFFE0A93A,
                mouseX, mouseY);
        // Сохранённый текст — ниже, с переносом по словам, чтобы его было видно целиком.
        String saved = screen.data().getString("notes");
        int y = area[1] + 50;
        for (var line : screen.font().split(Component.literal(saved), area[2])) {
            if (y > area[1] + area[3] - 10) {
                break;
            }
            g.drawString(screen.font(), line, area[0], y, t.text(), false);
            y += 10;
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (screen.inside(mx, my, saveRect(area))) {
            CompoundTag args = new CompoundTag();
            args.putString("text", screen.value("notes"));
            screen.send("notes_save", args);
            return true;
        }
        return false;
    }
}
