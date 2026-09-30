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
    /** Показывать выписку вместо перевода: переключается нажатием на карту. */
    private boolean statement;
    private int offset;

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
        String toggle = Component.translatable(statement ? "citylife.bank.back_to_pay"
                : "citylife.bank.statement").getString();
        screen.text(g, toggle, area[0] + area[2] - 34 - screen.font().width(toggle), area[1] + 7,
                0xCCFFFFFF);
        if (statement) {
            renderStatement(g, area, h, t);
            return;
        }

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

    private void renderStatement(GuiGraphics g, int[] area, int top, DeviceScreen.Theme t) {
        ListTag lines = screen.list("statement");
        if (lines.isEmpty()) {
            screen.empty(g, new int[]{area[0], area[1] + top, area[2], area[3] - top},
                    "citylife.bank.statement_empty");
            return;
        }
        int y = area[1] + top + 6;
        for (int i = offset; i < lines.size(); i++) {
            if (y + 20 > area[1] + area[3]) {
                break;
            }
            CompoundTag line = lines.getCompound(i);
            long amount = line.getLong("amount");
            String sum = (amount > 0 ? "+" : "−") + Money.format(Math.abs(amount));
            int sw = screen.font().width(sum);
            screen.text(g, screen.trim(line.getString("text"), area[2] - sw - 8), area[0] + 2, y,
                    t.text());
            screen.text(g, sum, area[0] + area[2] - sw - 2, y, amount > 0 ? t.green() : t.red());
            screen.text(g, BrowserApp.ago(line.getLong("ago")), area[0] + 2, y + 10, t.dim());
            y += 22;
        }
    }

    @Override
    public boolean scroll(double delta) {
        if (!statement) {
            return false;
        }
        offset = Math.max(0, Math.min(screen.list("statement").size() - 1,
                offset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (screen.inside(mx, my, new int[]{area[0], area[1], area[2], 52})) {
            statement = !statement;
            offset = 0;
            return true;
        }
        if (statement) {
            return false;
        }
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
