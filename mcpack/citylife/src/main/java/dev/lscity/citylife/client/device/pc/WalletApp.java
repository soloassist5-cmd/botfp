package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.economy.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Кошелёк: сколько наличных по купюрам и сколько на счёте. */
class WalletApp extends PcApp {

    WalletApp(DeviceScreen screen) {
        super(screen, "wallet");
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
        long[] counts = new long[Money.DENOMINATIONS.length];
        long total = 0;
        for (ItemStack stack : player.getInventory().items) {
            long value = Money.value(stack);
            for (int i = 0; i < counts.length; i++) {
                if (Money.DENOMINATIONS[i] == value) {
                    counts[i] += stack.getCount();
                }
            }
            total += value * stack.getCount();
        }
        long balance = screen.data().getLong("balance");
        int half = area[2] / 2 - 4;
        PhoneUi.roundedGradient(g, area[0], area[1], half, 44, 8, 0xFFE0B04A, 0xFF9A6F1C);
        PhoneUi.roundedGradient(g, area[0] + half + 8, area[1], half, 44, 8, 0xFF57D48B, 0xFF1F8F57);
        screen.text(g, "Наличные", area[0] + 8, area[1] + 6, 0xFFFFFFFF);
        big(g, Money.format(total), area[0] + half / 2, area[1] + 22, 1.5F, 0xFFFFFFFF);
        screen.text(g, "На счёте", area[0] + half + 16, area[1] + 6, 0xFFFFFFFF);
        big(g, Money.format(balance), area[0] + half + 8 + half / 2, area[1] + 22, 1.5F, 0xFFFFFFFF);
        int y = area[1] + 54;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) {
                continue;
            }
            String line = Money.format(Money.DENOMINATIONS[i]) + " × " + counts[i];
            screen.text(g, line, area[0] + 6, y, Kit.TEXT);
            String sum = Money.format(Money.DENOMINATIONS[i] * counts[i]);
            screen.text(g, sum, area[0] + area[2] - 6 - screen.font().width(sum), y, Kit.DIM);
            y += 12;
        }
        if (total == 0) {
            screen.text(g, "Наличных нет. Снять можно в банкомате.", area[0] + 6, y, Kit.DIM);
        }
        screen.text(g, "Всего денег: " + Money.format(total + balance), area[0] + 6,
                area[1] + area[3] - 10, Kit.GREEN);
    }
}
