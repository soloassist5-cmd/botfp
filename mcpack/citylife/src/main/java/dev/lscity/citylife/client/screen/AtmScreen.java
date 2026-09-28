package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.economy.Money;
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

/** Экран банкомата: счёт, наличные, снятие, внесение, перевод, выпуск карты. */
@OnlyIn(Dist.CLIENT)
public class AtmScreen extends Screen {
    private static final int WIDTH = 256;
    private static final int HEIGHT = 196;
    private static final int BODY = 0xF01B1E27;
    private static final int PANEL = 0xFF232733;
    private static final int SCREEN_BG = 0xFF12303D;
    private static final int ACCENT = 0xFF35C7F0;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int DIM = 0xFF8E94A8;

    private final BlockPos pos;
    private CompoundTag snapshot;
    private EditBox amountBox;
    private EditBox targetBox;
    private String amountDraft = "";
    private String targetDraft = "";

    public AtmScreen(BlockPos pos, CompoundTag snapshot) {
        super(Component.translatable("citylife.atm.title"));
        this.pos = pos;
        this.snapshot = snapshot;
    }

    public void update(CompoundTag fresh) {
        this.snapshot = fresh;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private long amount() {
        String raw = amountBox == null ? amountDraft : amountBox.getValue();
        raw = raw.replaceAll("[^0-9]", "");
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Math.min(Long.parseLong(raw), 1_000_000_000L);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private void send(String action) {
        CompoundTag args = new CompoundTag();
        args.putLong("pos", pos.asLong());
        args.putLong("amount", amount());
        if (targetBox != null) {
            args.putString("to", targetBox.getValue());
        }
        Net.sendAction(action, args);
    }

    private void sendCard(String kind) {
        CompoundTag args = new CompoundTag();
        args.putLong("pos", pos.asLong());
        args.putString("kind", kind);
        Net.sendAction("atm_card", args);
    }

    @Override
    protected void init() {
        if (amountBox != null) {
            amountDraft = amountBox.getValue();
        }
        if (targetBox != null) {
            targetDraft = targetBox.getValue();
        }
        int x = left() + 16;
        int y = top() + 84;
        int inner = WIDTH - 32;

        amountBox = new EditBox(font, x, y, inner - 96, 18,
                Component.translatable("citylife.atm.amount"));
        amountBox.setValue(amountDraft);
        amountBox.setHint(Component.translatable("citylife.atm.amount"));
        amountBox.setMaxLength(10);
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("citylife.atm.withdraw"),
                b -> send("atm_withdraw")).bounds(x + inner - 92, y, 44, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("citylife.atm.deposit"),
                b -> send("atm_deposit")).bounds(x + inner - 44, y, 44, 18).build());

        // Быстрые суммы: чаще всего снимают круглые деньги.
        int qx = x;
        for (long quick : new long[]{500, 1000, 5000, 10000}) {
            addRenderableWidget(Button.builder(Component.literal(Money.format(quick)), b -> {
                amountBox.setValue(Long.toString(quick));
            }).bounds(qx, y + 22, 40, 16).build());
            qx += 44;
        }
        addRenderableWidget(Button.builder(Component.translatable("citylife.atm.deposit_all"),
                b -> {
                    amountBox.setValue("");
                    send("atm_deposit");
                }).bounds(x + inner - 68, y + 22, 68, 16).build());

        targetBox = new EditBox(font, x, y + 44, inner - 72, 18,
                Component.translatable("citylife.atm.to"));
        targetBox.setValue(targetDraft);
        targetBox.setHint(Component.translatable("citylife.atm.to"));
        targetBox.setMaxLength(16);
        addRenderableWidget(targetBox);
        addRenderableWidget(Button.builder(Component.translatable("citylife.atm.transfer"),
                b -> send("atm_transfer")).bounds(x + inner - 68, y + 44, 68, 18).build());

        if (!snapshot.getBoolean("hasCard")) {
            addRenderableWidget(Button.builder(Component.translatable("citylife.atm.issue_mir"),
                    b -> sendCard("mir")).bounds(x, y + 68, (inner - 8) / 2, 18).build());
            addRenderableWidget(Button.builder(
                    Component.translatable("citylife.atm.issue_mastercard"),
                    b -> sendCard("mastercard"))
                    .bounds(x + (inner + 8) / 2, y + 68, (inner - 8) / 2, 18).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        int x = left();
        int y = top();

        graphics.fill(x - 2, y - 2, x + WIDTH + 2, y + HEIGHT + 2, 0x60000000);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BODY);
        graphics.fill(x, y, x + WIDTH, y + 1, 0x30FFFFFF);

        // Табло банкомата.
        graphics.fill(x + 16, y + 14, x + WIDTH - 16, y + 74, SCREEN_BG);
        graphics.fill(x + 16, y + 14, x + WIDTH - 16, y + 15, ACCENT);
        graphics.drawString(font, Component.translatable("citylife.atm.title"),
                x + 24, y + 22, ACCENT, false);

        long balance = snapshot.getLong("balance");
        long cash = snapshot.getLong("cash");
        graphics.drawString(font, Component.translatable("citylife.atm.balance"),
                x + 24, y + 38, DIM, false);
        graphics.drawString(font, Component.literal(Money.format(balance)),
                x + 24, y + 50, TEXT, false);
        String cashLabel = Component.translatable("citylife.atm.cash").getString();
        graphics.drawString(font, cashLabel, x + WIDTH - 24 - font.width(cashLabel),
                y + 38, DIM, false);
        String cashValue = Money.format(cash);
        graphics.drawString(font, cashValue, x + WIDTH - 24 - font.width(cashValue),
                y + 50, TEXT, false);

        if (snapshot.getBoolean("hasCard")) {
            String number = snapshot.getString("cardNumber");
            graphics.drawString(font, number, x + 24, y + 62, DIM, false);
        } else {
            String fee = Component.translatable("citylife.atm.card_price",
                    Money.format(snapshot.getInt("cardPrice"))).getString();
            graphics.drawString(font, fee, x + 24, y + 62, DIM, false);
        }

        graphics.fill(x + 8, y + HEIGHT - 8, x + WIDTH - 8, y + HEIGHT - 7, PANEL);
        super.render(graphics, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
