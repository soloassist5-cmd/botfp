package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.client.ui.Wallpapers;
import dev.lscity.citylife.device.DeviceModel;
import dev.lscity.citylife.device.Devices;
import dev.lscity.citylife.item.SimCardItem;
import dev.lscity.citylife.net.Net;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Экран любого гаджета: телефона, планшета, ноутбука или компьютера.
 *
 * Здесь корпус, строка состояния, шапка приложения, переходы и сеть.
 * Содержимое рисуют приложения (DeviceApp). Клиент ничего не решает сам:
 * действие уходит на сервер вместе с контекстом устройства, в ответ
 * приходит свежий снимок.
 */
@OnlyIn(Dist.CLIENT)
public class DeviceScreen extends Screen {

    public static final int STATUS_H = 13;
    public static final int HEADER_H = 20;
    public static final int BOTTOM_H = 14;

    /** Цвета интерфейса: у кнопочной «Нокты» своя, монохромная, палитра. */
    public record Theme(int text, int dim, int card, int cardHover, int accent, int green,
                        int red, int button) {
    }

    private static final Theme MODERN = new Theme(0xFFEDEFF7, 0xFF9AA0B4, 0xE01B1F2B,
            0xF0262B3B, 0xFF45D0F0, 0xFF7BE07B, 0xFFFF6B6B, 0xFF1F6FD0);
    private static final Theme LCD = new Theme(0xFF0F2A12, 0xFF2E4F24, 0x5530622F,
            0x7730622F, 0xFF0F2A12, 0xFF0F2A12, 0xFF5A1010, 0xFF30622F);

    private CompoundTag data;
    private DeviceModel model;
    private DeviceFrame frame;

    private String appId = "home";
    private DeviceApp app;
    private long appSince = System.currentTimeMillis();
    /** Номер собеседника для открытого чата. */
    public int chatNumber;

    private final Map<String, EditBox> inputs = new LinkedHashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    /** Для какого appId создан текущий экземпляр приложения. */
    private String appFor;

    public DeviceScreen(CompoundTag snapshot) {
        super(Component.translatable("citylife.screen.phone"));
        this.data = snapshot;
        this.model = Devices.get(snapshot.getString("model"));
        this.frame = DeviceFrame.of(model);
    }

    public void update(CompoundTag fresh) {
        this.data = fresh;
        DeviceModel next = Devices.get(fresh.getString("model"));
        if (next != model) {
            model = next;
            frame = DeviceFrame.of(model);
            appId = "home";
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    // --- раскладка ----------------------------------------------------------

    @Override
    protected void init() {
        frame.layout(width, height);
        inputs.forEach((key, box) -> drafts.put(key, box.getValue()));
        inputs.clear();
        // Приложение пересоздаём только при переходе в другое: обновление данных
        // с сервера приходит часто, и раньше оно обнуляло игры и прокрутку.
        boolean networkChanged = app instanceof OfflineApp ? online()
                : app != null && app.needsNetwork() && !online();
        if (app == null || !appId.equals(appFor) || networkChanged) {
            app = create(appId);
            appFor = appId;
        }
        app.init(contentArea());
    }

    private DeviceApp create(String id) {
        DeviceApp created = switch (id) {
            case "messages" -> new MessagesApp(this, false);
            case "chat" -> new MessagesApp(this, true);
            case "contacts" -> new ContactsApp(this);
            case "bank" -> new BankApp(this);
            case "navigator" -> new NavigatorApp(this);
            case "locks" -> new LocksApp(this);
            case "cameras" -> new CamerasApp(this);
            case "browser" -> new BrowserApp(this, false);
            case "browser_board" -> new BrowserApp(this, true);
            case "mail" -> new MailApp(this);
            case "music" -> new MusicApp(this);
            case "mines" -> new MinesApp(this);
            case "game2048" -> new Game2048App(this);
            case "terminal" -> new TerminalApp(this);
            case "sos" -> new SosApp(this);
            case "store" -> new StoreApp(this);
            case "marketplace" -> new MarketApp(this);
            case "settings" -> new SettingsApp(this);
            case "calc" -> new CalcApp(this);
            case "notes" -> new NotesApp(this);
            case "compass" -> new CompassApp(this);
            case "flashlight" -> new FlashlightApp(this);
            case "sysinfo" -> new SysInfoApp(this);
            case "tetris", "snake" -> new GameApp(this, id);
            default -> new HomeApp(this);
        };
        if (created.needsNetwork() && !online()) {
            return new OfflineApp(this, created.title());
        }
        return created;
    }

    /** Рабочая область приложения: без строки состояния, шапки и нижней панели. */
    public int[] contentArea() {
        int[] s = frame.screen();
        int top = s[1] + STATUS_H + (appId.equals("home") ? 0 : HEADER_H);
        int bottom = s[1] + s[3] - BOTTOM_H;
        return new int[]{s[0] + 8, top + 4, s[2] - 16, bottom - top - 8};
    }

    // --- отрисовка ----------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        frame.draw(g);
        int[] s = frame.screen();
        if (frame.monochrome()) {
            PhoneUi.roundedGradient(g, s[0], s[1], s[2], s[3], frame.screenRadius(),
                    0xFFA7C957, 0xFF8BAC3F);
        } else {
            Wallpapers.draw(g, wallpaper(), s[0], s[1], s[2], s[3], frame.screenRadius());
            if (!appId.equals("home")) {
                // Приложение поверх обоев — на затемнённой подложке, как в настоящем телефоне.
                g.fill(s[0], s[1] + STATUS_H, s[0] + s[2], s[1] + s[3] - BOTTOM_H, 0xC00B0D14);
            }
        }
        renderStatusBar(g, s);
        if (!appId.equals("home")) {
            renderHeader(g, s);
        }

        float age = (System.currentTimeMillis() - appSince) / 220F;
        int slide = Math.round((1F - PhoneUi.ease(age)) * 12);
        int[] area = contentArea();
        g.pose().pushPose();
        g.pose().translate(0, slide, 0);
        app.render(g, area, mouseX, mouseY - slide);
        g.pose().popPose();

        renderBottom(g, s, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partial);
    }

    private void renderStatusBar(GuiGraphics g, int[] s) {
        Theme t = theme();
        long time = data.getLong("daytime");
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);
        g.drawString(font, String.format("%d:%02d", hours, minutes), s[0] + 10, s[1] + 3,
                t.text(), false);

        String net;
        if (model.kind().wired()) {
            net = "Wi-Fi";
        } else if (sim() != 0) {
            net = SimCardItem.format(sim());
        } else {
            net = Component.translatable("citylife.sim.status_none").getString();
        }
        int netW = font.width(net);
        int x = s[0] + s[2] - 26 - netW - 16;
        g.drawString(font, net, x + 16, s[1] + 3, online() ? t.text() : t.red(), false);
        for (int i = 0; i < 4; i++) {
            int bar = 2 + i * 2;
            boolean lit = online() && i < 3;
            g.fill(x + i * 3, s[1] + 10 - bar, x + i * 3 + 2, s[1] + 10,
                    lit ? t.text() : PhoneUi.alpha(t.text(), 0.3F));
        }
        int bx = s[0] + s[2] - 20;
        PhoneUi.roundedOutline(g, bx, s[1] + 3, 14, 7, 2, PhoneUi.alpha(t.text(), 0.6F));
        g.fill(bx + 14, s[1] + 5, bx + 15, s[1] + 8, PhoneUi.alpha(t.text(), 0.6F));
        g.fill(bx + 2, s[1] + 5, bx + 11, s[1] + 8, t.green());
    }

    private void renderHeader(GuiGraphics g, int[] s) {
        Theme t = theme();
        int y = s[1] + STATUS_H;
        g.fill(s[0], y, s[0] + s[2], y + HEADER_H, 0x40000000);
        g.drawString(font, "‹", s[0] + 10, y + 6, t.accent(), false);
        g.drawString(font, trim(app.title(), s[2] - 40), s[0] + 22, y + 6, t.text(), false);
        g.fill(s[0], y + HEADER_H - 1, s[0] + s[2], y + HEADER_H, 0x22FFFFFF);
    }

    private void renderBottom(GuiGraphics g, int[] s, int mouseX, int mouseY) {
        Theme t = theme();
        int y = s[1] + s[3] - BOTTOM_H;
        if (frame.taskbar()) {
            g.fill(s[0], y, s[0] + s[2], s[1] + s[3], 0xD00B0D14);
            int[] start = startButton(s);
            PhoneUi.roundedRect(g, start[0], start[1], start[2], start[3], 3,
                    inside(mouseX, mouseY, start) ? 0xFF2F6FD6 : 0xFF1F4FA8);
            g.drawString(font, "⌂", start[0] + 5, start[1] + 2, 0xFFFFFFFF, false);
            String owner = data.getString("owner");
            g.drawString(font, owner, s[0] + s[2] - font.width(owner) - 6, y + 3, t.dim(), false);
        } else {
            int barW = s[2] / 3;
            PhoneUi.roundedRect(g, s[0] + (s[2] - barW) / 2, s[1] + s[3] - 8, barW, 3, 2,
                    PhoneUi.alpha(t.text(), 0.45F));
        }
    }

    private int[] startButton(int[] s) {
        return new int[]{s[0] + 4, s[1] + s[3] - BOTTOM_H + 2, 16, 10};
    }

    // --- ввод ---------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int[] s = frame.screen();
        if (!appId.equals("home") && my >= s[1] + STATUS_H && my <= s[1] + STATUS_H + HEADER_H
                && mx <= s[0] + 34) {
            open(app.back());
            return true;
        }
        if (my >= s[1] + s[3] - BOTTOM_H && mx >= s[0] && mx <= s[0] + s[2]) {
            open("home");
            return true;
        }
        return app.click(mx, my, contentArea());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return app.scroll(delta) || super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int code, int scan, int modifiers) {
        if (app.key(code)) {
            return true;
        }
        if (code == 256 && !appId.equals("home")) {
            open(app.back());
            return true;
        }
        return super.keyPressed(code, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- для приложений -----------------------------------------------------

    public Font font() {
        return font;
    }

    public CompoundTag data() {
        return data;
    }

    public ListTag list(String key) {
        return data.getList(key, Tag.TAG_COMPOUND);
    }

    public ListTag strings(String key) {
        return data.getList(key, Tag.TAG_STRING);
    }

    public DeviceModel model() {
        return model;
    }

    public DeviceFrame frame() {
        return frame;
    }

    public Theme theme() {
        return frame.monochrome() ? LCD : MODERN;
    }

    public int sim() {
        return data.getInt("sim");
    }

    public boolean online() {
        return data.getBoolean("online");
    }

    public String wallpaper() {
        return Wallpapers.normalize(data.getString("wallpaper"));
    }

    public String appTitle(String id) {
        return Component.translatable("citylife.app." + id).getString();
    }

    public void open(String id) {
        inputs.forEach((key, box) -> drafts.put(key, box.getValue()));
        appId = id;
        appSince = System.currentTimeMillis();
        rebuildWidgets();
    }

    /** Начать приложение заново (новая партия в игре). */
    public void restart() {
        appFor = null;
        open(appId);
    }

    public void openChat(int number) {
        chatNumber = number;
        open("chat");
    }

    /** Отправить действие на сервер вместе с контекстом устройства. */
    public void send(String action, CompoundTag args) {
        args.put("ctx", data.getCompound("ctx"));
        Net.sendAction(action, args);
    }

    public void send(String action) {
        send(action, new CompoundTag());
    }

    /** Поле ввода приложения; черновик переживает обновление экрана. */
    public EditBox input(String key, int x, int y, int w, String hint, int max) {
        EditBox box = new EditBox(font, x, y, w, 16, Component.translatable(hint));
        box.setHint(Component.translatable(hint));
        box.setMaxLength(max);
        box.setValue(drafts.getOrDefault(key, ""));
        box.setBordered(false);
        box.setTextColor(theme().text());
        inputs.put(key, box);
        addRenderableWidget(box);
        return box;
    }

    public String value(String key) {
        EditBox box = inputs.get(key);
        return box == null ? "" : box.getValue();
    }

    public void clear(String key) {
        EditBox box = inputs.get(key);
        if (box != null) {
            box.setValue("");
        }
        drafts.remove(key);
    }

    public boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    public void card(GuiGraphics g, int[] r, boolean hover) {
        PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6,
                hover ? theme().cardHover() : theme().card());
    }

    /** Кнопка с подписью по центру; ширина по тексту, если w <= 0. */
    public int[] button(GuiGraphics g, int x, int y, int w, String label, int colour,
                        int mouseX, int mouseY) {
        int width = w > 0 ? w : font.width(label) + 14;
        int[] r = {x, y, width, 16};
        int fill = inside(mouseX, mouseY, r) ? PhoneUi.lerp(colour, 0xFFFFFFFF, 0.15F) : colour;
        PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6, fill);
        g.drawString(font, label, x + (width - font.width(label)) / 2, y + 4, 0xFFFFFFFF, false);
        return r;
    }

    public int buttonWidth(String label) {
        return font.width(label) + 14;
    }

    public void text(GuiGraphics g, String text, int x, int y, int colour) {
        g.drawString(font, text, x, y, colour, false);
    }

    public void empty(GuiGraphics g, int[] area, String key) {
        String text = Component.translatable(key).getString();
        g.drawString(font, text, area[0] + (area[2] - font.width(text)) / 2,
                area[1] + area[3] / 2 - 10, theme().dim(), false);
    }

    public String trim(String text, int maxWidth) {
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

    /**
     * Текст, ужатый в ширину: сначала уменьшаем шрифт до 70%, и только если
     * и так не влезает — обрезаем с многоточием. Подписи под значками больше
     * не наезжают друг на друга.
     */
    public void fitted(GuiGraphics g, String text, int cx, int y, int maxWidth, int colour) {
        int width = font.width(text);
        float scale = width <= maxWidth ? 1F : Math.max(0.7F, maxWidth / (float) width);
        String shown = width * scale <= maxWidth ? text : trim(text, (int) (maxWidth / scale));
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, shown, -font.width(shown) / 2, 0, colour, false);
        g.pose().popPose();
    }
}
