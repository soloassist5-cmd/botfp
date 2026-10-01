package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.economy.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Финансы: приход и расход по выписке банка, график баланса по операциям
 * и крупнейшие траты.
 */
class BankViewApp extends PcApp {

    BankViewApp(DeviceScreen screen) {
        super(screen, "bankview");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        ListTag lines = screen.list("statement");
        long balance = screen.data().getLong("balance");
        long income = 0;
        long spent = 0;
        for (int i = 0; i < lines.size(); i++) {
            long amount = lines.getCompound(i).getLong("amount");
            if (amount > 0) {
                income += amount;
            } else {
                spent -= amount;
            }
        }
        screen.text(g, "Баланс: " + Money.format(balance), area[0], area[1], Kit.TEXT);
        screen.text(g, "Приход: " + Money.format(income), area[0], area[1] + 12, Kit.GREEN);
        screen.text(g, "Расход: " + Money.format(spent), area[0] + area[2] / 2, area[1] + 12, Kit.RED);
        // График: баланс до каждой операции (выписка — от новых к старым).
        int gx = area[0];
        int gy = area[1] + 28;
        int gw = area[2];
        int gh = area[3] / 2 - 20;
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF101218);
        int n = Math.min(lines.size(), 40);
        if (n > 1) {
            long[] values = new long[n];
            long running = balance;
            for (int i = 0; i < n; i++) {
                values[n - 1 - i] = running;
                running -= lines.getCompound(i).getLong("amount");
            }
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (long v : values) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            long span = Math.max(1, max - min);
            for (int i = 0; i < n; i++) {
                int x = gx + 2 + i * (gw - 4) / Math.max(1, n - 1);
                int y = gy + gh - 2 - (int) ((values[i] - min) * (gh - 4) / span);
                g.fill(x - 1, y - 1, x + 1, y + 1, Kit.ACCENT);
            }
        } else {
            screen.fitted(g, "Операций пока мало для графика", gx + gw / 2, gy + gh / 2 - 4, gw, Kit.DIM);
        }
        int y = gy + gh + 8;
        screen.text(g, "Последние операции:", area[0], y, Kit.DIM);
        y += 12;
        for (int i = 0; i < lines.size() && y < area[1] + area[3] - 10; i++) {
            CompoundTag line = lines.getCompound(i);
            long amount = line.getLong("amount");
            String sum = (amount > 0 ? "+" : "") + Money.format(amount);
            screen.text(g, screen.trim(line.getString("text"), area[2] - 80), area[0], y, Kit.TEXT);
            screen.text(g, sum, area[0] + area[2] - screen.font().width(sum), y, amount > 0 ? Kit.GREEN : Kit.RED);
            y += 11;
        }
    }
}
