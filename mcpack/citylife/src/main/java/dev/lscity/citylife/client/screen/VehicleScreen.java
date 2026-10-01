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
 * Окно «Мой транспорт» по клавише (K, переназначается в управлении).
 *
 * Слева — свои машины и те, от которых дали ключ: где стоит и заперта ли.
 * Справа — выбранная: запереть или открыть, маршрут к ней, сигнал (машина
 * гудит и светится, чтобы найти её на стоянке), ключи для игроков рядом.
 */
@OnlyIn(Dist.CLIENT)
public class VehicleScreen extends Screen {

    private static final int ROW = 26;

    private CompoundTag data;
    private String selected = "";
    private int scroll;

    public VehicleScreen(CompoundTag data) {
        super(Component.translatable("citylife.garage.title"));
        this.data = data;
        ListTag cars = cars();
        String riding = data.getString("riding");
        for (int i = 0; i < cars.size(); i++) {
            if (cars.getCompound(i).getString("id").equals(riding)) {
                selected = riding;
            }
        }
        if (selected.isEmpty() && !cars.isEmpty()) {
            selected = cars.getCompound(0).getString("id");
        }
    }

    public void update(CompoundTag fresh) {
        data = fresh;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private ListTag cars() {
        return data.getList("cars", Tag.TAG_COMPOUND);
    }

    private CompoundTag car() {
        ListTag cars = cars();
        for (int i = 0; i < cars.size(); i++) {
            if (cars.getCompound(i).getString("id").equals(selected)) {
                return cars.getCompound(i);
            }
        }
        return null;
    }

    private int w() {
        return Math.min(width - 16, 380);
    }

    private int h() {
        return Math.min(height - 16, 230);
    }

    private int left() {
        return (width - w()) / 2;
    }

    private int top() {
        return (height - h()) / 2;
    }

    private int listW() {
        return w() * 48 / 100;
    }

    private int rows() {
        return Math.max(1, (h() - 34) / ROW);
    }

    private void act(String action, CompoundTag extra) {
        CompoundTag args = extra == null ? new CompoundTag() : extra;
        args.putString("id", selected);
        Net.sendAction(action, args);
    }

    @Override
    protected void init() {
        CompoundTag car = car();
        if (car == null) {
            return;
        }
        int x = left() + listW() + 12;
        int y = top() + 64;
        int bw = (w() - listW() - 24) / 2 - 2;
        boolean locked = car.getBoolean("locked");
        addRenderableWidget(Button.builder(Component.translatable(locked
                ? "citylife.garage.unlock" : "citylife.garage.lock"),
                b -> act(locked ? "garage_unlock" : "garage_lock", null))
                .bounds(x, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("citylife.garage.route"),
                b -> act("garage_route", null)).bounds(x + bw + 4, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("citylife.garage.horn"),
                b -> act("garage_horn", null)).bounds(x, y + 22, bw * 2 + 4, 18).build());
        if (!car.getBoolean("mine")) {
            return;
        }
        ListTag people = data.getList("people", Tag.TAG_COMPOUND);
        ListTag keys = car.getList("keys", Tag.TAG_STRING);
        int py = y + 66;
        for (int i = 0; i < people.size() && py + 18 <= top() + h() - 6; i++) {
            CompoundTag person = people.getCompound(i);
            String name = person.getString("name");
            boolean has = false;
            for (int k = 0; k < keys.size(); k++) {
                has |= keys.getString(k).equals(name);
            }
            CompoundTag extra = new CompoundTag();
            extra.putUUID("friend", person.getUUID("id"));
            addRenderableWidget(Button.builder(Component.literal((has ? "✔ " : "+ ") + name),
                    b -> act("garage_trust", extra)).bounds(x, py, bw * 2 + 4, 16).build());
            py += 18;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int x = left();
        int y = top();
        PhoneUi.roundedRect(g, x, y, w(), h(), 8, 0xF0141824);
        g.drawString(font, title, x + 10, y + 9, 0xFFEDEFF7, false);
        String hint = dev.lscity.citylife.client.Keys.VEHICLE.getTranslatedKeyMessage().getString();
        ListTag cars = cars();
        if (cars.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("citylife.garage.none"), x + 10, y + 34,
                    w() - 20, 0xFF9AA0B4);
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        String riding = data.getString("riding");
        scroll = Math.max(0, Math.min(scroll, Math.max(0, cars.size() - rows())));
        for (int i = scroll; i < cars.size() && i - scroll < rows(); i++) {
            CompoundTag car = cars.getCompound(i);
            int ry = y + 28 + (i - scroll) * ROW;
            int[] r = {x + 6, ry, listW(), ROW - 3};
            boolean sel = car.getString("id").equals(selected);
            boolean hover = mouseX >= r[0] && mouseX <= r[0] + r[2] && mouseY >= r[1] && mouseY <= r[1] + r[3];
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 5,
                    sel ? 0xFF2A4F8F : hover ? 0xFF262B3B : 0xFF1B1F2B);
            String name = Component.translatable(car.getString("model")).getString();
            g.drawString(font, font.plainSubstrByWidth(name, r[2] - 26), r[0] + 6, r[1] + 3, 0xFFEDEFF7, false);
            g.drawString(font, "●", r[0] + r[2] - 14, r[1] + 3,
                    car.getBoolean("locked") ? 0xFFFF6B6B : 0xFF7BE07B, false);
            g.drawString(font, where(car, riding), r[0] + 6, r[1] + 13, 0xFF9AA0B4, false);
        }
        CompoundTag car = car();
        if (car != null) {
            int cx = x + listW() + 12;
            String name = Component.translatable(car.getString("model")).getString();
            g.drawString(font, font.plainSubstrByWidth(name, w() - listW() - 24), cx, y + 28, 0xFFEDEFF7, false);
            String state = Component.translatable(car.getBoolean("locked")
                    ? "citylife.garage.locked" : "citylife.garage.open").getString()
                    + " · " + where(car, riding);
            g.drawString(font, state, cx, y + 40, car.getBoolean("locked") ? 0xFFFF9A9A : 0xFF9AE09A, false);
            if (!car.getBoolean("mine")) {
                g.drawString(font, Component.translatable("citylife.garage.not_mine",
                        car.getString("owner")), cx, y + 52, 0xFF9AA0B4, false);
            } else {
                ListTag keys = car.getList("keys", Tag.TAG_STRING);
                StringBuilder names = new StringBuilder();
                for (int k = 0; k < keys.size(); k++) {
                    names.append(k > 0 ? ", " : "").append(keys.getString(k));
                }
                g.drawString(font, font.plainSubstrByWidth(keys.isEmpty()
                        ? Component.translatable("citylife.garage.nokeys").getString()
                        : Component.translatable("citylife.garage.keys", names.toString()).getString(),
                        w() - listW() - 24), cx, y + 52, 0xFF9AA0B4, false);
                boolean people = !data.getList("people", Tag.TAG_COMPOUND).isEmpty();
                g.drawString(font, Component.translatable(people ? "citylife.garage.give"
                        : "citylife.garage.nobody"), cx, y + 118, 0xFF9AA0B4, false);
            }
        }
        g.drawString(font, "[" + hint + "]", x + w() - font.width("[" + hint + "]") - 10, y + 9,
                0xFF5A6078, false);
        super.render(g, mouseX, mouseY, partial);
    }

    private String where(CompoundTag car, String riding) {
        if (car.getString("id").equals(riding)) {
            return Component.translatable("citylife.garage.riding").getString();
        }
        int d = car.getInt("distance");
        if (d < 0) {
            return Component.translatable("citylife.garage.otherworld").getString();
        }
        return d < 8 ? Component.translatable("citylife.garage.here").getString()
                : Component.translatable("citylife.garage.far", d).getString();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        ListTag cars = cars();
        for (int i = scroll; i < cars.size() && i - scroll < rows(); i++) {
            int ry = top() + 28 + (i - scroll) * ROW;
            if (mx >= left() + 6 && mx <= left() + 6 + listW() && my >= ry && my <= ry + ROW - 3) {
                selected = cars.getCompound(i).getString("id");
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int code, int scan, int modifiers) {
        if (dev.lscity.citylife.client.Keys.VEHICLE.matches(code, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(code, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
