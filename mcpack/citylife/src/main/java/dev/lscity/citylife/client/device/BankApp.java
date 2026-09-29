package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.economy.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Банк: счёт игрока, наличные и перевод.
 *
 * Счёт привязан к нику, а не к SIM: сменил телефон — деньги на месте.
 * Получателя выбирают среди тех, кто в сети, сумму вводят цифрами.
 */
class BankApp extends DeviceApp {

    private UUID target;
    private String targetName = "";

    BankApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("bank");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void init(int[] area) {
        screen.input("amount", area[0], area[1] + area[3] - 16, area[2] - payWidth() - 8,
                "citylife.phone.amount", 10);
    }

    private int payWidth() {
        return screen.buttonWidth(Component.translatable("citylife.button.pay").getString());
    }

    private int[] chip(int[] area, int index) {
        int w = (area[2] - 4) / 2;
        return new int[]{area[0] + (index % 2) * (w + 4), area[1] + 88 + (index / 2) * 18, w, 15};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        int h = 52;
        PhoneUi.roundedGradient(g, area[0], area[1], area[2], h, 8, 0xFF2E86DE, 0xFF163E78);
        screen.text(g, Component.translatable("citylife.phone.account").getString(),
                area[0] + 10, area[1] + 7, 0xCCFFFFFF);
        screen.text(g, Money.format(screen.data().getLong("balance")), area[0] + 10,
                area[1] + 20, 0xFFFFFFFF);
        screen.text(g, screen.data().getString("owner"), area[0] + 10, area[1] + 36, 0x99FFFFFF);
        PhoneUi.roundedRect(g, area[0] + area[2] - 30, area[1] + 32, 20, 12, 3, 0x55FFFFFF);

        screen.text(g, Component.translatable("citylife.phone.cash").getString() + ": "
                + Money.format(screen.data().getLong("cash")), area[0], area[1] + h + 6, t.dim());
        screen.text(g, Component.translatable("citylife.phone.pay_to").getString(),
                area[0], area[1] + h + 20, t.dim());

        ListTag payees = screen.list("payees");
        if (payees.isEmpty()) {
            screen.text(g, Component.translatable("citylife.bank.nobody").getString(),
                    area[0], area[1] + 90, t.dim());
        }
        for (int i = 0; i < payees.size() && i < 8; i++) {
            CompoundTag entry = payees.getCompound(i);
            int[] r = chip(area, i);
            boolean chosen = entry.getUUID("id").equals(target);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6,
                    chosen ? 0xFF1F8F57 : screen.inside(mouseX, mouseY, r) ? t.cardHover() : t.card());
            screen.fitted(g, entry.getString("name"), r[0] + r[2] / 2, r[1] + 4, r[2] - 6,
                    t.text());
        }

        int[] field = {area[0] - 3, area[1] + area[3] - 18, area[2] - payWidth() - 2, 16};
        screen.card(g, field, false);
        screen.button(g, area[0] + area[2] - payWidth(), area[1] + area[3] - 18, 0,
                Component.translatable("citylife.button.pay").getString(),
                target == null ? 0xFF3A3F50 : 0xFF1F8F57, mouseX, mouseY);
        if (target != null) {
            screen.fitted(g, Component.translatable("citylife.phone.transfer_to", targetName)
                    .getString(), area[0] + area[2] / 2, area[1] + area[3] - 30, area[2], t.dim());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        ListTag payees = screen.list("payees");
        for (int i = 0; i < payees.size() && i < 8; i++) {
            if (screen.inside(mx, my, chip(area, i))) {
                target = payees.getCompound(i).getUUID("id");
                targetName = payees.getCompound(i).getString("name");
                return true;
            }
        }
        int[] pay = {area[0] + area[2] - payWidth(), area[1] + area[3] - 18, payWidth(), 16};
        if (screen.inside(mx, my, pay) && target != null) {
            long amount = parse(screen.value("amount"));
            if (amount > 0) {
                CompoundTag args = new CompoundTag();
                args.putUUID("target", target);
                args.putLong("amount", amount);
                screen.send("pay", args);
                screen.clear("amount");
            }
            return true;
        }
        return false;
    }

    private static long parse(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 10) {
            return 0;
        }
        return Long.parseLong(digits);
    }
}
