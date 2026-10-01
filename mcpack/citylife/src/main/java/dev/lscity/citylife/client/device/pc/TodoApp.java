package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Список дел: добавить задачу, отметить сделанной, удалить правым кликом.
 * Хранится до выхода из игры.
 */
class TodoApp extends PcApp {

    private record Item(String text, boolean done) {
    }

    private static final List<Item> ITEMS = new ArrayList<>(List.of(new Item("Вставить SIM в телефон", false),
            new Item("Выпустить карту в банкомате", false), new Item("Купить квартиру", false)));

    TodoApp(DeviceScreen screen) {
        super(screen, "todo");
    }

    @Override
    public void init(int[] area) {
        screen.input("todo:new", area[0] + 4, area[1] + 4, area[2] - 64, "citylife.pc.todo_hint", 60);
    }

    private int[] add(int[] area) {
        return new int[]{area[0] + area[2] - 54, area[1], 54, 16};
    }

    private int[] row(int[] area, int i) {
        return new int[]{area[0], area[1] + 22 + i * 16, area[2], 14};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        PhoneUi.roundedRect(g, area[0], area[1], area[2] - 58, 16, 4, 0xFF101218);
        Kit.tile(g, screen, add(area), "Добавить", 0xFF1F8F57, mouseX, mouseY);
        for (int i = 0; i < ITEMS.size(); i++) {
            int[] r = row(area, i);
            if (r[1] + r[3] > area[1] + area[3]) {
                break;
            }
            Item item = ITEMS.get(i);
            PhoneUi.roundedRect(g, r[0], r[1], 12, 12, 3, item.done() ? 0xFF1F8F57 : 0xFF2A2F3F);
            if (item.done()) {
                screen.text(g, "✔", r[0] + 2, r[1] + 2, 0xFFFFFFFF);
            }
            screen.text(g, screen.trim(item.text(), r[2] - 20), r[0] + 18, r[1] + 2,
                    item.done() ? Kit.DIM : Kit.TEXT);
            if (item.done()) {
                g.fill(r[0] + 18, r[1] + 6, r[0] + 18 + screen.font().width(screen.trim(item.text(), r[2] - 20)),
                        r[1] + 7, Kit.DIM);
            }
        }
        long left = ITEMS.stream().filter(i -> !i.done()).count();
        screen.text(g, "Осталось: " + left + " из " + ITEMS.size() + " · правый клик — удалить", area[0],
                area[1] + area[3] - 9, Kit.DIM);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, add(area))) {
            String text = screen.value("todo:new").trim();
            if (!text.isEmpty()) {
                ITEMS.add(new Item(text, false));
                screen.clear("todo:new");
            }
            return true;
        }
        for (int i = 0; i < ITEMS.size(); i++) {
            if (Kit.inside(mx, my, row(area, i))) {
                Item item = ITEMS.get(i);
                ITEMS.set(i, new Item(item.text(), !item.done()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean rightClick(double mx, double my, int[] area) {
        for (int i = 0; i < ITEMS.size(); i++) {
            if (Kit.inside(mx, my, row(area, i))) {
                ITEMS.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean key(int code) {
        if (code == 257 && !screen.value("todo:new").isBlank()) {
            ITEMS.add(new Item(screen.value("todo:new").trim(), false));
            screen.clear("todo:new");
            return true;
        }
        return false;
    }
}
