package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

/**
 * Экран смартфона: слева список приложений, справа содержимое.
 *
 * Клиент ничего не решает сам — каждое действие уходит на сервер,
 * а в ответ приходит свежий снимок состояния.
 */
@OnlyIn(Dist.CLIENT)
public class PhoneScreen extends Screen {
    private static final int WIDTH = 288;
    private static final int HEIGHT = 194;
    private static final int PANEL = 0xF01A1A22;
    private static final int PANEL_LIGHT = 0xFF2A2A36;
    private static final int ACCENT = 0xFF35C7F0;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int TEXT_DIM = 0xFF9A9AAA;

    private enum App {
        MESSAGES("citylife.app.messages"),
        CONTACTS("citylife.app.contacts"),
        BANK("citylife.app.bank"),
        MAP("citylife.app.map"),
        HOME("citylife.app.home"),
        SOS("citylife.app.sos");

        final String key;

        App(String key) {
            this.key = key;
        }
    }

    private CompoundTag snapshot;
    private App app = App.MESSAGES;

    private int contactIndex = -1;
    private int lockIndex = -1;

    private String messageDraft = "";
    private String amountDraft = "";
    private String waypointDraft = "";
    private String pinDraft = "";

    private EditBox messageBox;
    private EditBox amountBox;
    private EditBox waypointBox;
    private EditBox pinBox;

    public PhoneScreen(CompoundTag snapshot) {
        super(Component.translatable("citylife.screen.phone"));
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

    private ListTag list(String key) {
        return snapshot.getList(key, Tag.TAG_COMPOUND);
    }

    private CompoundTag selectedContact() {
        ListTag contacts = list("contacts");
        if (contactIndex < 0 || contactIndex >= contacts.size()) {
            return null;
        }
        return contacts.getCompound(contactIndex);
    }

    private CompoundTag selectedLock() {
        ListTag locks = list("locks");
        if (lockIndex < 0 || lockIndex >= locks.size()) {
            return null;
        }
        return locks.getCompound(lockIndex);
    }

    @Override
    protected void init() {
        saveDrafts();
        messageBox = null;
        amountBox = null;
        waypointBox = null;
        pinBox = null;

        int x = left();
        int y = top();

        // Колонка приложений.
        int buttonY = y + 24;
        for (App candidate : App.values()) {
            boolean active = candidate == app;
            Button button = Button.builder(Component.translatable(candidate.key), press -> {
                app = candidate;
                rebuildWidgets();
            }).bounds(x + 6, buttonY, 78, 20).build();
            button.active = !active;
            addRenderableWidget(button);
            buttonY += 22;
        }

        int cx = x + 92;
        int cw = WIDTH - 98;
        switch (app) {
            case MESSAGES -> initMessages(cx, y, cw);
            case CONTACTS -> initContacts(cx, y, cw);
            case BANK -> initBank(cx, y, cw);
            case MAP -> initMap(cx, y, cw);
            case HOME -> initHome(cx, y, cw);
            case SOS -> initSos(cx, y, cw);
        }
    }

    private void saveDrafts() {
        if (messageBox != null) {
            messageDraft = messageBox.getValue();
        }
        if (amountBox != null) {
            amountDraft = amountBox.getValue();
        }
        if (waypointBox != null) {
            waypointDraft = waypointBox.getValue();
        }
        if (pinBox != null) {
            pinDraft = pinBox.getValue();
        }
    }

    // --- приложения ---------------------------------------------------------

    private void initMessages(int cx, int y, int cw) {
        messageBox = new EditBox(font, cx, y + HEIGHT - 48, cw - 62, 18,
                Component.translatable("citylife.app.messages"));
        messageBox.setMaxLength(180);
        messageBox.setValue(messageDraft);
        addRenderableWidget(messageBox);

        addRenderableWidget(Button.builder(Component.translatable("citylife.button.send"), press -> {
            CompoundTag contact = selectedContact();
            if (contact == null || messageBox.getValue().isBlank()) {
                return;
            }
            CompoundTag args = new CompoundTag();
            args.putUUID("target", contact.getUUID("id"));
            args.putString("text", messageBox.getValue());
            messageDraft = "";
            Net.sendAction("msg", args);
        }).bounds(cx + cw - 58, y + HEIGHT - 48, 58, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("citylife.button.refresh"),
                        press -> Net.sendAction("refresh", new CompoundTag()))
                .bounds(cx + cw - 58, y + HEIGHT - 26, 58, 18).build());
    }

    private void initContacts(int cx, int y, int cw) {
        ListTag contacts = list("contacts");
        int rowY = y + 38;
        for (int i = 0; i < Math.min(6, contacts.size()); i++) {
            CompoundTag contact = contacts.getCompound(i);
            int index = i;
            String name = contact.getString("name");
            addRenderableWidget(Button.builder(Component.literal(
                            (index == contactIndex ? "> " : "") + name), press -> {
                        contactIndex = index;
                        rebuildWidgets();
                    }).bounds(cx, rowY, cw - 52, 18).build());
            addRenderableWidget(Button.builder(Component.literal(contact.getInt("dist") + "м"),
                            press -> {
                            }).bounds(cx + cw - 48, rowY, 48, 18).build());
            rowY += 20;
        }
        addRenderableWidget(Button.builder(Component.translatable("citylife.button.refresh"),
                        press -> Net.sendAction("refresh", new CompoundTag()))
                .bounds(cx, y + HEIGHT - 26, 70, 18).build());
    }

    private void initBank(int cx, int y, int cw) {
        amountBox = new EditBox(font, cx, y + 72, 80, 18,
                Component.translatable("citylife.app.bank"));
        amountBox.setMaxLength(10);
        amountBox.setFilter(value -> value.isEmpty() || value.matches("\\d{1,10}"));
        amountBox.setValue(amountDraft);
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("citylife.button.transfer"),
                press -> {
                    CompoundTag contact = selectedContact();
                    if (contact == null || amountBox.getValue().isBlank()) {
                        return;
                    }
                    CompoundTag args = new CompoundTag();
                    args.putUUID("target", contact.getUUID("id"));
                    args.putLong("amount", Long.parseLong(amountBox.getValue()));
                    amountDraft = "";
                    Net.sendAction("pay", args);
                }).bounds(cx + 84, y + 72, 90, 18).build());
    }

    private void initMap(int cx, int y, int cw) {
        ListTag points = list("waypoints");
        int rowY = y + 48;
        for (int i = 0; i < Math.min(5, points.size()); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal("✕"), press -> {
                CompoundTag args = new CompoundTag();
                args.putInt("index", index);
                Net.sendAction("wp_del", args);
            }).bounds(cx + cw - 20, rowY, 20, 18).build());
            rowY += 20;
        }

        waypointBox = new EditBox(font, cx, y + HEIGHT - 48, cw - 82, 18,
                Component.translatable("citylife.app.map"));
        waypointBox.setMaxLength(20);
        waypointBox.setValue(waypointDraft);
        addRenderableWidget(waypointBox);

        addRenderableWidget(Button.builder(Component.translatable("citylife.button.mark"),
                press -> {
                    CompoundTag args = new CompoundTag();
                    args.putString("name", waypointBox.getValue());
                    waypointDraft = "";
                    Net.sendAction("wp_add", args);
                }).bounds(cx + cw - 78, y + HEIGHT - 48, 78, 18).build());
    }

    private void initHome(int cx, int y, int cw) {
        ListTag locks = list("locks");
        int rowY = y + 38;
        for (int i = 0; i < Math.min(4, locks.size()); i++) {
            CompoundTag lock = locks.getCompound(i);
            int index = i;
            boolean open = lock.getBoolean("open");
            addRenderableWidget(Button.builder(Component.literal(
                            (index == lockIndex ? "> " : "") + lock.getString("label")), press -> {
                        lockIndex = index;
                        rebuildWidgets();
                    }).bounds(cx, rowY, cw - 76, 18).build());
            addRenderableWidget(Button.builder(Component.translatable(
                            open ? "citylife.button.close" : "citylife.button.open"), press -> {
                        CompoundTag args = new CompoundTag();
                        args.putLong("pos", lock.getLong("pos"));
                        Net.sendAction("lock_toggle", args);
                    }).bounds(cx + cw - 72, rowY, 72, 18).build());
            rowY += 20;
        }

        CompoundTag selected = selectedLock();
        if (selected != null && selected.getBoolean("owner")) {
            pinBox = new EditBox(font, cx, y + HEIGHT - 48, 70, 18,
                    Component.translatable("citylife.app.home"));
            pinBox.setMaxLength(8);
            pinBox.setValue(pinDraft);
            addRenderableWidget(pinBox);

            addRenderableWidget(Button.builder(Component.translatable("citylife.button.set_pin"),
                    press -> {
                        CompoundTag args = new CompoundTag();
                        args.putLong("pos", selected.getLong("pos"));
                        args.putString("pin", pinBox.getValue());
                        pinDraft = "";
                        Net.sendAction("lock_pin", args);
                    }).bounds(cx + 74, y + HEIGHT - 48, 58, 18).build());

            addRenderableWidget(Button.builder(Component.translatable("citylife.button.grant"),
                    press -> {
                        CompoundTag contact = selectedContact();
                        if (contact == null) {
                            return;
                        }
                        CompoundTag args = new CompoundTag();
                        args.putLong("pos", selected.getLong("pos"));
                        args.putUUID("target", contact.getUUID("id"));
                        Net.sendAction("lock_grant", args);
                    }).bounds(cx + 136, y + HEIGHT - 48, 54, 18).build());
        }

        if (selected != null) {
            addRenderableWidget(Button.builder(Component.translatable("citylife.button.unpair"),
                    press -> {
                        CompoundTag args = new CompoundTag();
                        args.putLong("pos", selected.getLong("pos"));
                        lockIndex = -1;
                        Net.sendAction("lock_unpair", args);
                    }).bounds(cx + cw - 60, y + HEIGHT - 26, 60, 18).build());
        }
    }

    private void initSos(int cx, int y, int cw) {
        String[] kinds = {"police", "medic", "fire"};
        String[] keys = {"citylife.sos.police", "citylife.sos.medic", "citylife.sos.fire"};
        int rowY = y + 44;
        for (int i = 0; i < kinds.length; i++) {
            String kind = kinds[i];
            addRenderableWidget(Button.builder(Component.translatable(keys[i]), press -> {
                CompoundTag args = new CompoundTag();
                args.putString("kind", kind);
                Net.sendAction("sos", args);
                onClose();
            }).bounds(cx, rowY, cw - 20, 20).build());
            rowY += 24;
        }
    }

    // --- отрисовка ----------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        int x = left();
        int y = top();
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        graphics.fill(x, y, x + WIDTH, y + 18, PANEL_LIGHT);
        graphics.fill(x + 88, y + 20, x + WIDTH - 4, y + HEIGHT - 4, 0x30000000);

        graphics.drawString(font, Component.translatable("citylife.screen.phone"),
                x + 8, y + 5, ACCENT, false);
        graphics.drawString(font, Component.translatable("citylife.screen.balance",
                        snapshot.getLong("balance")),
                x + WIDTH - 8 - font.width(Component.translatable("citylife.screen.balance",
                        snapshot.getLong("balance"))), y + 5, 0xFF7BE07B, false);

        int cx = x + 94;
        int cy = y + 24;
        switch (app) {
            case MESSAGES -> renderMessages(graphics, cx, cy);
            case CONTACTS -> renderContacts(graphics, cx, cy);
            case BANK -> renderBank(graphics, cx, cy);
            case MAP -> renderMap(graphics, cx, cy);
            case HOME -> renderHome(graphics, cx, cy);
            case SOS -> renderSos(graphics, cx, cy);
        }
        super.render(graphics, mouseX, mouseY, partial);
    }

    private void renderMessages(GuiGraphics graphics, int cx, int cy) {
        CompoundTag contact = selectedContact();
        graphics.drawString(font, Component.translatable("citylife.screen.to",
                        contact == null ? "—" : contact.getString("name")),
                cx, cy, contact == null ? TEXT_DIM : TEXT, false);
        ListTag messages = list("messages");
        if (messages.isEmpty()) {
            graphics.drawString(font, Component.translatable("citylife.screen.no_messages"),
                    cx, cy + 16, TEXT_DIM, false);
            return;
        }
        int rowY = cy + 16;
        for (int i = 0; i < Math.min(6, messages.size()); i++) {
            CompoundTag message = messages.getCompound(i);
            graphics.drawString(font, message.getString("from") + ":", cx, rowY, ACCENT, false);
            String text = message.getString("text");
            graphics.drawString(font, font.plainSubstrByWidth(text, 172), cx + 4, rowY + 9,
                    TEXT, false);
            rowY += 20;
        }
    }

    private void renderContacts(GuiGraphics graphics, int cx, int cy) {
        ListTag contacts = list("contacts");
        graphics.drawString(font, Component.translatable("citylife.screen.contacts_hint"),
                cx, cy, TEXT_DIM, false);
        if (contacts.isEmpty()) {
            graphics.drawString(font, Component.translatable("citylife.screen.alone"),
                    cx, cy + 16, TEXT_DIM, false);
        }
    }

    private void renderBank(GuiGraphics graphics, int cx, int cy) {
        graphics.drawString(font, Component.translatable("citylife.screen.balance_big",
                snapshot.getLong("balance")), cx, cy + 6, 0xFF7BE07B, false);
        CompoundTag contact = selectedContact();
        graphics.drawString(font, Component.translatable("citylife.screen.to",
                        contact == null ? "—" : contact.getString("name")),
                cx, cy + 22, contact == null ? TEXT_DIM : TEXT, false);
        graphics.drawString(font, Component.translatable("citylife.screen.amount"),
                cx, cy + 38, TEXT_DIM, false);
        if (contact == null) {
            graphics.drawString(font, Component.translatable("citylife.screen.pick_contact"),
                    cx, cy + 92, TEXT_DIM, false);
        }
    }

    private void renderMap(GuiGraphics graphics, int cx, int cy) {
        graphics.drawString(font, Component.translatable("citylife.screen.coords",
                        snapshot.getInt("x"), snapshot.getInt("y"), snapshot.getInt("z")),
                cx, cy, ACCENT, false);
        ListTag points = list("waypoints");
        int rowY = cy + 24;
        for (int i = 0; i < Math.min(5, points.size()); i++) {
            CompoundTag point = points.getCompound(i);
            int dx = point.getInt("x") - snapshot.getInt("x");
            int dz = point.getInt("z") - snapshot.getInt("z");
            int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
            graphics.drawString(font, point.getString("name"), cx, rowY, TEXT, false);
            graphics.drawString(font, distance + "м", cx + 110, rowY, TEXT_DIM, false);
            rowY += 20;
        }
        if (points.isEmpty()) {
            graphics.drawString(font, Component.translatable("citylife.screen.no_marks"),
                    cx, cy + 24, TEXT_DIM, false);
        }
    }

    private void renderHome(GuiGraphics graphics, int cx, int cy) {
        ListTag locks = list("locks");
        if (locks.isEmpty()) {
            graphics.drawString(font, Component.translatable("citylife.screen.no_locks"),
                    cx, cy, TEXT_DIM, false);
            graphics.drawString(font, Component.translatable("citylife.screen.no_locks_hint"),
                    cx, cy + 12, TEXT_DIM, false);
            return;
        }
        CompoundTag selected = selectedLock();
        if (selected == null) {
            graphics.drawString(font, Component.translatable("citylife.screen.pick_lock"),
                    cx, cy, TEXT_DIM, false);
            return;
        }
        graphics.drawString(font, Component.translatable("citylife.screen.lock_state",
                        selected.getInt("dist"),
                        Component.translatable(selected.getBoolean("open")
                                ? "citylife.screen.open" : "citylife.screen.closed")),
                cx, cy, TEXT, false);
        ListTag log = selected.getList("log", Tag.TAG_STRING);
        int rowY = cy + 12;
        for (int i = 0; i < Math.min(4, log.size()); i++) {
            graphics.drawString(font, font.plainSubstrByWidth(log.getString(i), 180),
                    cx, rowY, TEXT_DIM, false);
            rowY += 10;
        }
    }

    private void renderSos(GuiGraphics graphics, int cx, int cy) {
        graphics.drawString(font, Component.translatable("citylife.screen.sos_hint"),
                cx, cy, 0xFFE07B7B, false);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (app == App.MESSAGES && messageBox != null && messageBox.isFocused()
                && (key == 257 || key == 335)) {
            CompoundTag contact = selectedContact();
            if (contact != null && !messageBox.getValue().isBlank()) {
                CompoundTag args = new CompoundTag();
                args.putUUID("target", contact.getUUID("id"));
                args.putString("text", messageBox.getValue());
                messageDraft = "";
                Net.sendAction("msg", args);
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Нужно для CompoundTag.getUUID в кнопках: проверяем формат заранее. */
    @SuppressWarnings("unused")
    private static UUID uuidOrNull(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }
}
