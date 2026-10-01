package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Инвентарь: что у тебя с собой, сгруппировано и посчитано; свободные слоты. */
class InventoryApp extends PcApp {

    private int scroll;

    InventoryApp(DeviceScreen screen) {
        super(screen, "inventory");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (noWorld(g, area)) {
            return;
        }
        var player = mc().player;
        if (player == null) {
            return;
        }
        Map<String, ItemStack> grouped = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int free = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                free++;
                continue;
            }
            String key = stack.getHoverName().getString();
            grouped.putIfAbsent(key, stack);
            counts.merge(key, stack.getCount(), Integer::sum);
        }
        screen.text(g, "Видов предметов: " + grouped.size() + " · свободно слотов: " + free, area[0], area[1],
                Kit.DIM);
        var keys = new java.util.ArrayList<>(grouped.keySet());
        int cols = 2;
        int rows = Math.max(1, (area[3] - 14) / 20);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, (keys.size() + 1) / cols - rows)));
        for (int i = scroll * cols; i < keys.size() && i < (scroll + rows) * cols; i++) {
            int col = i % cols;
            int row = i / cols - scroll;
            int w = area[2] / cols - 4;
            int x = area[0] + col * (w + 4);
            int y = area[1] + 14 + row * 20;
            PhoneUi.roundedRect(g, x, y, w, 18, 4, 0xFF1B1F2B);
            ItemStack stack = grouped.get(keys.get(i));
            g.renderItem(stack, x + 1, y + 1);
            String count = "×" + counts.get(keys.get(i));
            screen.text(g, screen.trim(keys.get(i), w - 26 - screen.font().width(count)), x + 20, y + 5, Kit.TEXT);
            screen.text(g, count, x + w - 4 - screen.font().width(count), y + 5, Kit.DIM);
        }
    }

    @Override
    public boolean scroll(double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }
}
