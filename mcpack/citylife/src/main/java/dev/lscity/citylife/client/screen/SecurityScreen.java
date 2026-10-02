package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.client.stark.StarkClient;
import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.economy.Money;
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
 * Пульт охраны башни STARK: состояние «Джарвиса», камеры, журнал и допуск.
 * Управлять охраной и смотреть камеры можно только с допуском; купить
 * допуск можно прямо здесь, со своего банковского счёта.
 */
@OnlyIn(Dist.CLIENT)
public class SecurityScreen extends Screen {

    private static final int CYAN = 0xFF57D8FF;
    private static final int RED = 0xFFFF4A4A;
    private static final int GREEN = 0xFF5CFF9D;
    private static final int TEXT = 0xFFE8FBFF;
    private static final int DIM = 0xFF7FA6B8;

    private CompoundTag data;

    public SecurityScreen(CompoundTag data) {
        super(Component.translatable("citylife.stark.console.title"));
        this.data = data;
        StarkClient.update(data);
    }

    public void update(CompoundTag data) {
        this.data = data;
        StarkClient.update(data);
        rebuildWidgets();
    }

    private int w() {
        return Math.min(width - 16, 400);
    }

    private int h() {
        return Math.min(height - 16, 236);
    }

    private void send(String action, CompoundTag args) {
        args.putLong("pos", data.getLong("pos"));
        Net.sendAction(action, args);
    }

    @Override
    protected void init() {
        int x = (width - w()) / 2;
        int y = (height - h()) / 2;
        boolean me = data.getBoolean("me");
        boolean armed = data.getBoolean("armed");
        boolean alarm = data.getBoolean("alarm");

        // Камеры слева.
        ListTag cams = data.getList("cams", Tag.TAG_COMPOUND);
        int cy = y + 44;
        for (int i = 0; i < cams.size() && cy + 18 < y + h() - 30; i++, cy += 20) {
            CompoundTag cam = cams.getCompound(i);
            long pos = cam.getLong("pos");
            Button button = Button.builder(Component.literal("◉ " + cam.getString("name")), b -> {
                CompoundTag args = new CompoundTag();
                args.putLong("cam", pos);
                send("stark_camera", args);
                onClose();
            }).bounds(x + 10, cy, 118, 18).build();
            button.active = me;
            addRenderableWidget(button);
        }

        // Управление по центру.
        int mx = x + 138;
        Button arm = Button.builder(Component.translatable(armed ? "citylife.stark.console.disarm"
                : "citylife.stark.console.arm"), b -> {
            CompoundTag args = new CompoundTag();
            args.putBoolean("on", !armed);
            send("stark_arm", args);
        }).bounds(mx, y + 112, 116, 18).build();
        arm.active = me;
        addRenderableWidget(arm);
        Button reset = Button.builder(Component.translatable("citylife.stark.console.reset"),
                b -> send("stark_reset", new CompoundTag())).bounds(mx, y + 134, 116, 18).build();
        reset.active = me && alarm;
        addRenderableWidget(reset);

        if (!data.getBoolean("bought")) {
            Button buy = Button.builder(Component.translatable("citylife.stark.console.buy",
                    Money.format(data.getLong("price"))), b -> send("stark_buy", new CompoundTag()))
                    .bounds(x + 10, y + h() - 26, w() - 20, 18).build();
            buy.active = data.getLong("balance") >= data.getLong("price");
            addRenderableWidget(buy);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int x = (width - w()) / 2;
        int y = (height - h()) / 2;
        boolean alarm = data.getBoolean("alarm");
        int accent = alarm ? RED : CYAN;
        long now = System.currentTimeMillis();

        PhoneUi.roundedRect(g, x, y, w(), h(), 8, 0xF2061420);
        PhoneUi.roundedOutline(g, x, y, w(), h(), 8, PhoneUi.alpha(accent, 0.8F));
        for (int gx = x + 12; gx < x + w() - 4; gx += 14) {
            g.fill(gx, y + 30, gx + 1, y + h() - 4, 0x0C57D8FF);
        }
        int scan = (int) (now / 20 % Math.max(1, h() - 34));
        g.fill(x + 2, y + 30 + scan, x + w() - 2, y + 31 + scan, PhoneUi.alpha(accent, 0.12F));
        g.drawString(font, title, x + 12, y + 10, accent, false);
        String state = alarm ? Component.translatable("citylife.stark.console.state_alarm",
                data.getString("by")).getString()
                : Component.translatable(data.getBoolean("armed") ? "citylife.stark.console.state_armed"
                : "citylife.stark.console.state_off").getString();
        int sw = font.width(state) + 12;
        PhoneUi.roundedRect(g, x + w() - sw - 10, y + 7, sw, 14, 6, PhoneUi.alpha(accent, 0.25F));
        g.drawString(font, state, x + w() - sw - 4, y + 10, alarm ? RED : data.getBoolean("armed") ? GREEN : DIM,
                false);
        g.fill(x + 8, y + 26, x + w() - 8, y + 27, PhoneUi.alpha(accent, 0.5F));

        g.drawString(font, Component.translatable("citylife.stark.console.cams"), x + 10, y + 32, DIM, false);
        if (data.getList("cams", Tag.TAG_COMPOUND).isEmpty()) {
            g.drawString(font, Component.translatable("citylife.stark.console.no_cams"), x + 10, y + 46, DIM, false);
        }

        int mx = x + 138;
        g.drawString(font, Component.translatable("citylife.stark.console.status"), mx, y + 32, DIM, false);
        int line = y + 46;
        g.drawString(font, Component.translatable("citylife.stark.console.intruders", data.getInt("intruders")),
                mx, line, data.getInt("intruders") > 0 ? RED : TEXT, false);
        g.drawString(font, Component.translatable("citylife.stark.console.cleared", data.getInt("cleared")),
                mx, line + 12, TEXT, false);
        g.drawString(font, Component.translatable(data.getBoolean("me") ? "citylife.stark.console.me_yes"
                : "citylife.stark.console.me_no"), mx, line + 24, data.getBoolean("me") ? GREEN : RED, false);
        g.drawString(font, Component.translatable("citylife.stark.console.balance",
                Money.format(data.getLong("balance"))), mx, line + 36, DIM, false);
        // Маленький «реактор» пульса системы.
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0);
        if (y + 188 < y + h() - 30) {
            PhoneUi.ring(g, mx + 58, y + 174, 11, 2, PhoneUi.alpha(accent, 0.5F + 0.5F * pulse));
            PhoneUi.disc(g, mx + 58, y + 174, 5, PhoneUi.alpha(0xFFE8FBFF, 0.4F + 0.5F * pulse));
        }

        int lx = x + 264;
        int lw = x + w() - 10 - lx;
        g.drawString(font, Component.translatable("citylife.stark.console.log"), lx, y + 32, DIM, false);
        ListTag log = data.getList("log", Tag.TAG_COMPOUND);
        int ly = y + 46;
        for (int i = 0; i < log.size() && ly + 9 < y + h() - 30; i++) {
            CompoundTag e = log.getCompound(i);
            for (var part : font.split(Component.literal(e.getString("t") + " " + e.getString("s")), lw)) {
                if (ly + 9 >= y + h() - 30) {
                    break;
                }
                g.drawString(font, part, lx, ly, e.getBoolean("a") ? RED : TEXT, false);
                ly += 10;
            }
            ly += 2;
        }
        if (data.getBoolean("bought")) {
            g.drawCenteredString(font, Component.translatable("citylife.stark.console.have_access"),
                    x + w() / 2, y + h() - 21, GREEN);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
