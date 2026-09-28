package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.client.NavClient;
import dev.lscity.citylife.client.game.SnakeGame;
import dev.lscity.citylife.client.game.TetrisGame;
import dev.lscity.citylife.client.ui.AppIcons;
import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.client.ui.Wallpapers;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Смартфон: корпус, рабочий стол с иконками и приложения внутри.
 *
 * Клиент ничего не решает сам — любое действие уходит на сервер, а в ответ
 * приходит свежий снимок состояния. Здесь только отрисовка и ввод.
 */
@OnlyIn(Dist.CLIENT)
public class PhoneScreen extends Screen {

    private static final int BASE_W = 188;
    private static final int BASE_H = 340;
    private static final int BEZEL = 6;
    private static final int STATUS_H = 13;
    private static final int HOME_BAR_H = 14;
    private static final int HEADER_H = 20;
    private static final int ICON = 34;

    private static final int BODY = 0xFF0E1018;
    private static final int RIM = 0xFF2B3040;
    private static final int TEXT = 0xFFEDEFF7;
    private static final int DIM = 0xFF8E94A8;
    private static final int CARD = 0xFF1B1F2B;
    private static final int CARD_HOVER = 0xFF242939;
    private static final int ACCENT = 0xFF45D0F0;
    private static final int GREEN = 0xFF7BE07B;
    private static final int RED = 0xFFFF6B6B;

    private CompoundTag snapshot;

    private String app = "home";
    private long appSince = System.currentTimeMillis();
    private String chatName = "";
    private int scroll;

    private EditBox input;
    private String inputDraft = "";
    private TetrisGame tetris;
    private SnakeGame snake;

    private int phoneW;
    private int phoneH;
    private int phoneX;
    private int phoneY;

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

    // --- раскладка ----------------------------------------------------------

    @Override
    protected void init() {
        phoneH = Math.min(BASE_H, height - 16);
        phoneW = Math.max(150, phoneH * BASE_W / BASE_H);
        phoneX = (width - phoneW) / 2;
        phoneY = (height - phoneH) / 2;

        if (input != null) {
            inputDraft = input.getValue();
        }
        input = null;

        int[] area = contentArea();
        switch (app) {
            case "messages", "chat" -> addInput(area, "citylife.phone.message", 64);
            case "navigator" -> addInput(area, "citylife.phone.mark_name", 20);
            case "bank" -> addInput(area, "citylife.phone.amount", 10);
            default -> {
            }
        }
    }

    private void addInput(int[] area, String hint, int limit) {
        int y = area[1] + area[3] - 18;
        input = new EditBox(font, area[0], y, area[2] - 46, 16, Component.translatable(hint));
        input.setHint(Component.translatable(hint));
        input.setMaxLength(limit);
        input.setValue(inputDraft);
        input.setBordered(false);
        addRenderableWidget(input);
    }

    /** Экран внутри корпуса: x, y, ширина, высота. */
    private int[] screenArea() {
        return new int[]{phoneX + BEZEL, phoneY + BEZEL, phoneW - BEZEL * 2, phoneH - BEZEL * 2};
    }

    /** Рабочая область приложения: без строки состояния, шапки и полоски «домой». */
    private int[] contentArea() {
        int[] s = screenArea();
        int top = s[1] + STATUS_H + (app.equals("home") ? 0 : HEADER_H);
        int bottom = s[1] + s[3] - HOME_BAR_H;
        return new int[]{s[0] + 8, top + 4, s[2] - 16, bottom - top - 8};
    }

    // --- данные снимка ------------------------------------------------------

    private ListTag list(String key) {
        return snapshot.getList(key, Tag.TAG_COMPOUND);
    }

    private List<String> apps() {
        List<String> out = new ArrayList<>();
        ListTag installed = snapshot.getList("apps", Tag.TAG_STRING);
        for (int i = 0; i < installed.size(); i++) {
            out.add(installed.getString(i));
        }
        if (out.isEmpty()) {
            out.add("messages");
        }
        return out;
    }

    private String appTitle(String id) {
        return Component.translatable("citylife.app." + id).getString();
    }

    // --- отрисовка ----------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int[] s = screenArea();

        PhoneUi.shadow(g, phoneX, phoneY, phoneW, phoneH, 18);
        PhoneUi.roundedRect(g, phoneX, phoneY, phoneW, phoneH, 18, BODY);
        PhoneUi.roundedOutline(g, phoneX, phoneY, phoneW, phoneH, 18, RIM);
        g.fill(phoneX + phoneW, phoneY + 60, phoneX + phoneW + 2, phoneY + 96, RIM);
        g.fill(phoneX - 2, phoneY + 70, phoneX, phoneY + 96, RIM);
        PhoneUi.roundedRect(g, s[0], s[1], s[2], s[3], 13, 0xFF05070C);

        renderWallpaper(g, s);
        renderStatusBar(g, s);
        if (!app.equals("home")) {
            renderHeader(g, s);
        }

        // Появление приложения: содержимое всплывает снизу.
        float age = (System.currentTimeMillis() - appSince) / 220F;
        int slide = Math.round((1F - PhoneUi.ease(age)) * 12);
        int[] area = contentArea();
        g.pose().pushPose();
        g.pose().translate(0, slide, 0);

        switch (app) {
            case "home" -> renderHome(g, area, mouseX, mouseY - slide);
            case "messages" -> renderMessages(g, area);
            case "chat" -> renderChat(g, area);
            case "contacts" -> renderContacts(g, area, mouseX, mouseY - slide);
            case "bank" -> renderBank(g, area, mouseX, mouseY - slide);
            case "navigator" -> renderNavigator(g, area, mouseX, mouseY - slide);
            case "locks" -> renderLocks(g, area, mouseX, mouseY - slide);
            case "sos" -> renderSos(g, area, mouseX, mouseY - slide);
            case "market" -> renderMarket(g, area, mouseX, mouseY - slide);
            case "settings" -> renderSettings(g, area, mouseX, mouseY - slide);
            case "tetris" -> {
                if (tetris == null) {
                    tetris = new TetrisGame();
                }
                tetris.tick();
                tetris.render(g, font, area[0], area[1], area[2], area[3]);
            }
            case "snake" -> {
                if (snake == null) {
                    snake = new SnakeGame();
                }
                snake.tick();
                snake.render(g, font, area[0], area[1], area[2], area[3]);
            }
            default -> {
            }
        }
        g.pose().popPose();

        int barW = s[2] / 3;
        PhoneUi.roundedRect(g, s[0] + (s[2] - barW) / 2, s[1] + s[3] - 8, barW, 3, 2, 0x66FFFFFF);
        super.render(g, mouseX, mouseY, partial);
    }

    private void renderWallpaper(GuiGraphics g, int[] s) {
        Wallpapers.draw(g, snapshot.getString("wallpaper"), s[0], s[1], s[2], s[3], 13);
    }

    private void renderStatusBar(GuiGraphics g, int[] s) {
        long time = snapshot.getLong("daytime");
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);
        g.drawString(font, String.format("%d:%02d", hours, minutes), s[0] + 10, s[1] + 4,
                TEXT, false);

        int x = s[0] + s[2] - 34;
        for (int i = 0; i < 4; i++) {
            int h = 2 + i * 2;
            g.fill(x + i * 3, s[1] + 10 - h, x + i * 3 + 2, s[1] + 10, i < 3 ? TEXT : 0x55FFFFFF);
        }
        int bx = s[0] + s[2] - 18;
        PhoneUi.roundedOutline(g, bx, s[1] + 3, 14, 7, 2, 0x99FFFFFF);
        g.fill(bx + 14, s[1] + 5, bx + 15, s[1] + 8, 0x99FFFFFF);
        g.fill(bx + 2, s[1] + 5, bx + 11, s[1] + 8, GREEN);
    }

    private void renderHeader(GuiGraphics g, int[] s) {
        int y = s[1] + STATUS_H;
        g.fill(s[0], y, s[0] + s[2], y + HEADER_H, 0x40000000);
        g.drawString(font, "<", s[0] + 10, y + 6, ACCENT, false);
        g.drawString(font, app.equals("chat") ? chatName : appTitle(app),
                s[0] + 24, y + 6, TEXT, false);
        g.fill(s[0], y + HEADER_H - 1, s[0] + s[2], y + HEADER_H, 0x22FFFFFF);
    }

    // --- рабочий стол -------------------------------------------------------

    private int[] iconRect(int index, int[] area) {
        int columns = 3;
        int gapX = (area[2] - columns * ICON) / (columns + 1);
        int col = index % columns;
        int row = index / columns;
        return new int[]{area[0] + gapX + col * (ICON + gapX), area[1] + 6 + row * (ICON + 22),
                ICON, ICON};
    }

    private void renderHome(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        List<String> apps = apps();
        for (int i = 0; i < apps.size(); i++) {
            int[] r = iconRect(i, area);
            boolean hover = inside(mouseX, mouseY, r);
            int lift = hover ? 1 : 0;
            AppIcons.draw(g, apps.get(i), r[0], r[1] - lift, ICON);
            String name = appTitle(apps.get(i));
            g.drawString(font, name, r[0] + (ICON - font.width(name)) / 2, r[1] + ICON + 4 - lift,
                    hover ? TEXT : 0xCCD8DCEA, false);
        }
    }

    // --- приложения ---------------------------------------------------------

    private void renderMessages(GuiGraphics g, int[] area) {
        ListTag messages = list("messages");
        if (messages.isEmpty()) {
            empty(g, area, "citylife.phone.no_messages");
        }
        int y = area[1];
        for (int i = Math.max(0, messages.size() - 6); i < messages.size(); i++) {
            CompoundTag entry = messages.getCompound(i);
            PhoneUi.roundedRect(g, area[0], y, area[2], 22, 6, CARD);
            g.drawString(font, entry.getString("from"), area[0] + 7, y + 3, ACCENT, false);
            g.drawString(font, trim(entry.getString("text"), area[2] - 16),
                    area[0] + 7, y + 12, TEXT, false);
            y += 26;
        }
        sendRow(g, area, "citylife.button.send");
    }

    private void renderChat(GuiGraphics g, int[] area) {
        ListTag messages = list("messages");
        int y = area[1];
        for (int i = 0; i < messages.size(); i++) {
            CompoundTag entry = messages.getCompound(i);
            if (!entry.getString("from").equals(chatName)) {
                continue;
            }
            PhoneUi.roundedRect(g, area[0], y, area[2] - 20, 20, 8, CARD);
            g.drawString(font, trim(entry.getString("text"), area[2] - 36),
                    area[0] + 7, y + 6, TEXT, false);
            y += 24;
        }
        sendRow(g, area, "citylife.button.send");
    }

    private void renderContacts(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        ListTag contacts = list("contacts");
        if (contacts.isEmpty()) {
            empty(g, area, "citylife.phone.no_contacts");
        }
        for (int i = 0; i < contacts.size(); i++) {
            CompoundTag entry = contacts.getCompound(i);
            int[] r = rowRect(area, i);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6,
                    inside(mouseX, mouseY, r) ? CARD_HOVER : CARD);
            PhoneUi.disc(g, r[0] + 14, r[1] + r[3] / 2, 8, 0xFF3A4055);
            g.drawString(font, entry.getString("name"), r[0] + 28, r[1] + 4, TEXT, false);
            g.drawString(font, entry.getInt("dist") + " м", r[0] + 28, r[1] + 13, DIM, false);
        }
    }

    private void renderBank(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int h = 54;
        PhoneUi.roundedGradient(g, area[0], area[1], area[2], h, 8, 0xFF2E86DE, 0xFF163E78);
        g.drawString(font, Component.translatable("citylife.phone.account"),
                area[0] + 10, area[1] + 8, 0xCCFFFFFF, false);
        g.drawString(font, Money.format(snapshot.getLong("balance")),
                area[0] + 10, area[1] + 22, 0xFFFFFFFF, false);
        g.drawString(font, "2200 •••• •••• " + shortId(), area[0] + 10, area[1] + 38,
                0x99FFFFFF, false);
        PhoneUi.roundedRect(g, area[0] + area[2] - 34, area[1] + 34, 22, 14, 3, 0x55FFFFFF);

        g.drawString(font, Component.translatable("citylife.phone.cash").getString() + ": "
                + Money.format(snapshot.getLong("cash")), area[0], area[1] + h + 8, DIM, false);

        int[] r = {area[0], area[1] + h + 22, area[2], 20};
        PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6,
                inside(mouseX, mouseY, r) ? CARD_HOVER : CARD);
        g.drawString(font, Component.translatable("citylife.phone.transfer_to",
                chatName.isEmpty() ? "—" : chatName), r[0] + 8, r[1] + 6, TEXT, false);
        sendRow(g, area, "citylife.button.pay");
    }

    private void renderNavigator(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        CompoundTag route = snapshot.getCompound("route");
        int y = area[1];
        if (!route.isEmpty()) {
            PhoneUi.roundedRect(g, area[0], y, area[2], 26, 6, 0xFF14313D);
            g.drawString(font, route.getString("name"), area[0] + 8, y + 5, ACCENT, false);
            g.drawString(font, route.getInt("dist") + " м", area[0] + 8, y + 15, DIM, false);
            String stop = Component.translatable("citylife.button.stop").getString();
            g.drawString(font, stop, area[0] + area[2] - 8 - font.width(stop), y + 10, RED, false);
            y += 30;
        }
        ListTag points = list("waypoints");
        int shown = 0;
        for (int i = scroll; i < points.size() && shown < 5; i++, shown++) {
            CompoundTag point = points.getCompound(i);
            int[] r = {area[0], y, area[2], 22};
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6,
                    inside(mouseX, mouseY, r) ? CARD_HOVER : CARD);
            PhoneUi.disc(g, r[0] + 11, r[1] + 11, 5, point.getBoolean("city") ? ACCENT : GREEN);
            g.drawString(font, trim(point.getString("name"), area[2] - 70),
                    r[0] + 22, r[1] + 3, TEXT, false);
            g.drawString(font, point.getInt("dist") + " м", r[0] + 22, r[1] + 12, DIM, false);
            y += 24;
        }
        sendRow(g, area, "citylife.button.mark");
    }

    private void renderLocks(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        ListTag locks = list("locks");
        if (locks.isEmpty()) {
            empty(g, area, "citylife.phone.no_locks");
        }
        for (int i = 0; i < locks.size(); i++) {
            CompoundTag entry = locks.getCompound(i);
            int[] r = rowRect(area, i);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6,
                    inside(mouseX, mouseY, r) ? CARD_HOVER : CARD);
            g.drawString(font, entry.getString("label"), r[0] + 8, r[1] + 4, TEXT, false);
            g.drawString(font, entry.getInt("dist") + " м", r[0] + 8, r[1] + 13, DIM, false);
            boolean open = entry.getBoolean("open");
            String state = Component.translatable(open
                    ? "citylife.lock.open" : "citylife.lock.closed").getString();
            g.drawString(font, state, r[0] + r[2] - 8 - font.width(state), r[1] + 8,
                    open ? GREEN : DIM, false);
        }
    }

    private void renderSos(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        String[] kinds = {"police", "medic", "fire"};
        int[] colours = {0xFF3F7BFF, 0xFF7BE07B, 0xFFFF6B6B};
        for (int i = 0; i < kinds.length; i++) {
            int[] r = {area[0], area[1] + i * 34, area[2], 30};
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 8,
                    PhoneUi.alpha(colours[i], inside(mouseX, mouseY, r) ? 0.55F : 0.35F));
            g.drawString(font, Component.translatable("citylife.sos." + kinds[i]).getString(),
                    r[0] + 12, r[1] + 11, TEXT, false);
            g.drawString(font, "112", r[0] + r[2] - 12 - font.width("112"), r[1] + 11,
                    0xCCFFFFFF, false);
        }
    }

    private void renderMarket(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        String[] store = {"tetris", "snake"};
        List<String> installed = apps();
        for (int i = 0; i < store.length; i++) {
            int[] r = {area[0], area[1] + i * 44, area[2], 40};
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 8,
                    inside(mouseX, mouseY, r) ? CARD_HOVER : CARD);
            AppIcons.draw(g, store[i], r[0] + 6, r[1] + 6, 28);
            g.drawString(font, appTitle(store[i]), r[0] + 42, r[1] + 8, TEXT, false);
            g.drawString(font, Component.translatable("citylife.market.desc." + store[i]),
                    r[0] + 42, r[1] + 20, DIM, false);
            boolean have = installed.contains(store[i]);
            String label = Component.translatable(have
                    ? "citylife.market.remove" : "citylife.market.install").getString();
            int bw = font.width(label) + 12;
            PhoneUi.roundedRect(g, r[0] + r[2] - bw - 6, r[1] + 12, bw, 16, 6,
                    have ? 0xFF3A3F50 : 0xFF1F8F57);
            g.drawString(font, label, r[0] + r[2] - bw, r[1] + 16, TEXT, false);
        }
    }


    /** Настройки: пока одна страница — выбор обоев. */
    private void renderSettings(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("citylife.phone.wallpaper"),
                area[0], area[1], DIM, false);
        String current = Wallpapers.normalize(snapshot.getString("wallpaper"));
        for (int i = 0; i < Wallpapers.ALL.size(); i++) {
            int[] r = wallpaperRect(area, i);
            String id = Wallpapers.ALL.get(i);
            Wallpapers.thumbnail(g, id, r[0], r[1], r[2], r[3], id.equals(current));
            if (inside(mouseX, mouseY, r)) {
                PhoneUi.roundedOutline(g, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 7, ACCENT);
            }
            String name = Component.translatable("citylife.wallpaper." + id).getString();
            g.drawString(font, name, r[0] + (r[2] - font.width(name)) / 2, r[1] + r[3] + 3,
                    id.equals(current) ? TEXT : DIM, false);
        }
    }

    private int[] wallpaperRect(int[] area, int index) {
        int gap = 8;
        int w = (area[2] - gap) / 2;
        int h = Math.min(64, (area[3] - 40) / 2);
        int col = index % 2;
        int row = index / 2;
        return new int[]{area[0] + col * (w + gap), area[1] + 12 + row * (h + 18), w, h};
    }

    // --- мелкие помощники ---------------------------------------------------

    private int[] rowRect(int[] area, int index) {
        return new int[]{area[0], area[1] + index * 26, area[2], 22};
    }

    private void empty(GuiGraphics g, int[] area, String key) {
        String text = Component.translatable(key).getString();
        g.drawString(font, text, area[0] + (area[2] - font.width(text)) / 2,
                area[1] + area[3] / 2 - 10, DIM, false);
    }

    private void sendRow(GuiGraphics g, int[] area, String key) {
        int y = area[1] + area[3] - 20;
        PhoneUi.roundedRect(g, area[0] - 4, y - 2, area[2] - 40, 20, 8, CARD);
        String label = Component.translatable(key).getString();
        int bw = font.width(label) + 14;
        PhoneUi.roundedRect(g, area[0] + area[2] - bw, y - 2, bw, 20, 8, 0xFF1F6FD0);
        g.drawString(font, label, area[0] + area[2] - bw + 7, y + 4, TEXT, false);
    }

    private int[] sendButtonRect(int[] area, String key) {
        int bw = font.width(Component.translatable(key).getString()) + 14;
        return new int[]{area[0] + area[2] - bw, area[1] + area[3] - 22, bw, 20};
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (font.width(out.toString() + ch + "…") > maxWidth) {
                break;
            }
            out.append(ch);
        }
        return out + "…";
    }

    private String shortId() {
        return String.format("%04d", Math.abs(snapshot.getString("owner").hashCode() % 10000));
    }

    private boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    // --- ввод ---------------------------------------------------------------

    private void open(String id) {
        app = id;
        appSince = System.currentTimeMillis();
        scroll = 0;
        inputDraft = "";
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int[] s = screenArea();
        int[] area = contentArea();

        if (!app.equals("home") && my >= s[1] + STATUS_H && my <= s[1] + STATUS_H + HEADER_H
                && mx <= s[0] + 24) {
            open(app.equals("chat") ? "contacts" : "home");
            return true;
        }
        if (my >= s[1] + s[3] - HOME_BAR_H && mx >= s[0] && mx <= s[0] + s[2]) {
            open("home");
            return true;
        }

        switch (app) {
            case "home" -> {
                List<String> apps = apps();
                for (int i = 0; i < apps.size(); i++) {
                    if (inside(mx, my, iconRect(i, area))) {
                        open(apps.get(i));
                        return true;
                    }
                }
            }
            case "contacts" -> {
                ListTag contacts = list("contacts");
                for (int i = 0; i < contacts.size(); i++) {
                    if (inside(mx, my, rowRect(area, i))) {
                        chatName = contacts.getCompound(i).getString("name");
                        open("chat");
                        return true;
                    }
                }
            }
            case "messages", "chat" -> {
                if (inside(mx, my, sendButtonRect(area, "citylife.button.send"))) {
                    CompoundTag args = new CompoundTag();
                    args.putString("to", chatName);
                    args.putString("text", input == null ? "" : input.getValue());
                    Net.sendAction("msg", args);
                    if (input != null) {
                        input.setValue("");
                    }
                    return true;
                }
            }
            case "bank" -> {
                if (inside(mx, my, sendButtonRect(area, "citylife.button.pay"))) {
                    CompoundTag args = new CompoundTag();
                    args.putString("to", chatName);
                    args.putLong("amount", parseAmount());
                    Net.sendAction("pay", args);
                    return true;
                }
            }
            case "navigator" -> {
                CompoundTag route = snapshot.getCompound("route");
                int y = area[1];
                if (!route.isEmpty()) {
                    if (my >= y && my <= y + 26 && mx > area[0] + area[2] - 44) {
                        Net.sendAction("nav_stop", new CompoundTag());
                        NavClient.clear();
                        return true;
                    }
                    y += 30;
                }
                if (inside(mx, my, sendButtonRect(area, "citylife.button.mark"))) {
                    CompoundTag args = new CompoundTag();
                    args.putString("name", input == null ? "" : input.getValue());
                    Net.sendAction("wp_add", args);
                    if (input != null) {
                        input.setValue("");
                    }
                    return true;
                }
                ListTag points = list("waypoints");
                int shown = 0;
                for (int i = scroll; i < points.size() && shown < 5; i++, shown++) {
                    if (inside(mx, my, new int[]{area[0], y, area[2], 22})) {
                        CompoundTag args = new CompoundTag();
                        args.put("point", points.getCompound(i));
                        Net.sendAction("nav_set", args);
                        return true;
                    }
                    y += 24;
                }
            }
            case "locks" -> {
                ListTag locks = list("locks");
                for (int i = 0; i < locks.size(); i++) {
                    if (inside(mx, my, rowRect(area, i))) {
                        CompoundTag args = new CompoundTag();
                        args.putLong("pos", locks.getCompound(i).getLong("pos"));
                        Net.sendAction("lock_toggle", args);
                        return true;
                    }
                }
            }
            case "sos" -> {
                String[] kinds = {"police", "medic", "fire"};
                for (int i = 0; i < kinds.length; i++) {
                    if (inside(mx, my, new int[]{area[0], area[1] + i * 34, area[2], 30})) {
                        CompoundTag args = new CompoundTag();
                        args.putString("kind", kinds[i]);
                        Net.sendAction("sos", args);
                        return true;
                    }
                }
            }
            case "settings" -> {
                for (int i = 0; i < Wallpapers.ALL.size(); i++) {
                    if (inside(mx, my, wallpaperRect(area, i))) {
                        CompoundTag args = new CompoundTag();
                        args.putString("id", Wallpapers.ALL.get(i));
                        Net.sendAction("wallpaper", args);
                        return true;
                    }
                }
            }
            case "market" -> {
                String[] store = {"tetris", "snake"};
                List<String> installed = apps();
                for (int i = 0; i < store.length; i++) {
                    if (inside(mx, my, new int[]{area[0], area[1] + i * 44, area[2], 40})) {
                        CompoundTag args = new CompoundTag();
                        args.putString("app", store[i]);
                        Net.sendAction(installed.contains(store[i])
                                ? "app_remove" : "app_install", args);
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    private long parseAmount() {
        String raw = input == null ? "" : input.getValue().replaceAll("[^0-9]", "");
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Math.min(Long.parseLong(raw), 1_000_000_000L);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (app.equals("navigator")) {
            int max = Math.max(0, list("waypoints").size() - 5);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int code, int scan, int modifiers) {
        if (app.equals("tetris") && tetris != null && tetris.key(code)) {
            return true;
        }
        if (app.equals("snake") && snake != null && snake.key(code)) {
            return true;
        }
        if (code == 256 && !app.equals("home")) {
            open("home");
            return true;
        }
        return super.keyPressed(code, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
