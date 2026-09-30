package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.estate.Estate;
import dev.lscity.citylife.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Агентство недвижимости: каталог жилья города, поиск по адресу,
 * фильтр по типу, покупка и продажа, маршрут и ключи для друзей.
 *
 * Сам каталог клиент читает из jar мода (тот же файл, что на сервере),
 * по сети приходят только владельцы и баланс.
 */
@OnlyIn(Dist.CLIENT)
public class RealtyScreen extends Screen {
    private static final int BODY = 0xF01B1E27;
    private static final int PANEL = 0xFF232733;
    private static final int ROW_HOVER = 0xFF2E3445;
    private static final int ROW_PICK = 0xFF34507A;
    private static final int ACCENT = 0xFF35C7F0;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int DIM = 0xFF8E94A8;
    private static final int FREE = 0xFF3FCB6E;
    private static final int TAKEN = 0xFFE0413A;
    private static final int MINE = 0xFFF2C12E;
    private static final int ROW = 13;

    private static final String[] FILTERS = {"all", "house", "villa", "rowhouse", "flat", "free", "mine"};
    private static final Map<String, String> DISTRICTS = Map.of(
            "downtown", "Даунтаун", "midtown", "Мидтаун", "suburbs", "Пригород",
            "hills", "Холмы", "beach", "Пляж", "eastside", "Истсайд", "industrial", "Промзона");

    private CompoundTag snapshot;
    private final Map<String, CompoundTag> owners = new HashMap<>();
    private String filter = "all";
    private String selected;
    private int scroll;
    private EditBox search;
    private EditBox keyBox;
    private String searchDraft;
    private String keyDraft = "";
    private List<Estate.Unit> shown = List.of();
    private String shownKey = "";

    public RealtyScreen(CompoundTag snapshot) {
        super(Component.translatable("citylife.realty.title"));
        this.searchDraft = snapshot.getString("focus");
        if (!searchDraft.isEmpty()) {
            filter = "flat";
        }
        update(snapshot);
    }

    public void update(CompoundTag fresh) {
        this.snapshot = fresh;
        owners.clear();
        for (Tag item : fresh.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            owners.put(entry.getString("u"), entry);
        }
        shownKey = "";
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    // --- размеры ------------------------------------------------------------------

    private int w() {
        return Math.min(width - 16, 400);
    }

    private int h() {
        return Math.min(height - 16, 250);
    }

    private int left() {
        return (width - w()) / 2;
    }

    private int top() {
        return (height - h()) / 2;
    }

    private int listW() {
        return w() * 56 / 100;
    }

    private int listTop() {
        return top() + 58;
    }

    private int listRows() {
        return Math.max(1, (top() + h() - 8 - listTop()) / ROW);
    }

    // --- данные -------------------------------------------------------------------

    private static String norm(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private boolean mine(String id) {
        CompoundTag entry = owners.get(id);
        return entry != null && entry.getBoolean("m");
    }

    private List<Estate.Unit> units() {
        String query = norm(search == null ? searchDraft : search.getValue()).trim();
        String key = filter + "|" + query;
        if (key.equals(shownKey)) {
            return shown;
        }
        shownKey = key;
        scroll = 0;
        List<Estate.Unit> out = new ArrayList<>();
        for (Estate.Unit unit : Estate.all()) {
            boolean ok = switch (filter) {
                case "free" -> !owners.containsKey(unit.id());
                case "mine" -> mine(unit.id()) || owners.containsKey(unit.id())
                        && owners.get(unit.id()).getBoolean("k");
                case "all" -> true;
                default -> filter.equals(unit.kind());
            };
            if (ok && (query.isEmpty() || norm(unit.address()).contains(query)
                    || norm(unit.title()).contains(query)
                    || norm(DISTRICTS.getOrDefault(unit.district(), "")).contains(query))) {
                out.add(unit);
            }
        }
        out.sort(Comparator.comparingLong(Estate.Unit::price).thenComparing(Estate.Unit::address));
        shown = out;
        return out;
    }

    private void act(String action, Estate.Unit unit, CompoundTag extra) {
        CompoundTag args = extra == null ? new CompoundTag() : extra;
        if (unit != null) {
            args.putString("id", unit.id());
        }
        Net.sendAction(action, args);
    }

    // --- виджеты ------------------------------------------------------------------

    @Override
    protected void init() {
        if (search != null) {
            searchDraft = search.getValue();
        }
        if (keyBox != null) {
            keyDraft = keyBox.getValue();
        }
        int x = left() + 8;
        int y = top() + 22;
        int fx = x;
        for (String f : FILTERS) {
            Component label = Component.translatable("citylife.realty.filter." + f);
            int bw = font.width(label) + 10;
            Button button = Button.builder(label, b -> {
                filter = f;
                rebuildWidgets();
            }).bounds(fx, y, bw, 14).build();
            button.active = !f.equals(filter);
            addRenderableWidget(button);
            fx += bw + 2;
        }
        search = new EditBox(font, x, y + 17, listW() - 8, 14,
                Component.translatable("citylife.realty.search"));
        search.setHint(Component.translatable("citylife.realty.search"));
        search.setMaxLength(48);
        search.setValue(searchDraft);
        addRenderableWidget(search);

        int px = left() + listW() + 8;
        int pw = w() - listW() - 16;
        int by = top() + h() - 26;
        addRenderableWidget(Button.builder(Component.translatable("citylife.realty.shop"),
                b -> act("realty_shop", null, null)).bounds(px, by, pw, 18).build());

        Estate.Unit unit = selected == null ? null : Estate.get(selected);
        if (unit == null) {
            return;
        }
        int ay = top() + 124;
        CompoundTag owner = owners.get(unit.id());
        if (owner == null) {
            addRenderableWidget(Button.builder(Component.translatable("citylife.realty.buy",
                    Money.format(unit.price())), b -> act("realty_buy", unit, null))
                    .bounds(px, ay, pw, 18).build());
        } else if (owner.getBoolean("m")) {
            long back = unit.price() * snapshot.getInt("sellPercent") / 100L;
            addRenderableWidget(Button.builder(Component.translatable("citylife.realty.sell",
                    Money.format(back)), b -> act("realty_sell", unit, null))
                    .bounds(px, ay, pw, 18).build());
            keyBox = new EditBox(font, px, ay + 44, pw - 50, 14,
                    Component.translatable("citylife.realty.key_hint"));
            keyBox.setHint(Component.translatable("citylife.realty.key_hint"));
            keyBox.setMaxLength(16);
            keyBox.setValue(keyDraft);
            addRenderableWidget(keyBox);
            addRenderableWidget(Button.builder(Component.translatable("citylife.realty.key_give"),
                    b -> {
                        CompoundTag args = new CompoundTag();
                        args.putString("name", keyBox.getValue());
                        keyDraft = "";
                        keyBox.setValue("");
                        act("realty_trust", unit, args);
                    }).bounds(px + pw - 46, ay + 44, 46, 14).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("citylife.realty.route"),
                b -> act("realty_route", unit, null)).bounds(px, ay + 22, pw, 18).build());
    }

    // --- отрисовка ----------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int x = left();
        int y = top();
        g.fill(x - 2, y - 2, x + w() + 2, y + h() + 2, 0x60000000);
        g.fill(x, y, x + w(), y + h(), BODY);
        g.fill(x, y, x + w(), y + 1, ACCENT);
        String agent = snapshot.getString("agent");
        String head = Component.translatable("citylife.realty.title").getString()
                + (agent.isEmpty() ? "" : " · " + agent);
        g.drawString(font, head, x + 8, y + 8, ACCENT, false);
        String balance = Component.translatable("citylife.realty.balance",
                Money.format(snapshot.getLong("balance"))).getString();
        g.drawString(font, balance, x + w() - 8 - font.width(balance), y + 8, TEXT, false);

        // Список.
        List<Estate.Unit> list = units();
        int lx = x + 8;
        int lw = listW() - 8;
        int ly = listTop();
        int rows = listRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        g.fill(lx, ly - 2, lx + lw, ly + rows * ROW + 1, PANEL);
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            Estate.Unit unit = list.get(scroll + i);
            int ry = ly + i * ROW;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= ry && mouseY < ry + ROW;
            if (unit.id().equals(selected)) {
                g.fill(lx, ry, lx + lw, ry + ROW, ROW_PICK);
            } else if (hover) {
                g.fill(lx, ry, lx + lw, ry + ROW, ROW_HOVER);
            }
            int dot = !owners.containsKey(unit.id()) ? FREE : mine(unit.id()) ? MINE : TAKEN;
            g.fill(lx + 3, ry + 5, lx + 6, ry + 8, dot);
            String price = Money.format(unit.price());
            int pw = font.width(price);
            String name = font.plainSubstrByWidth(unit.address(), lw - pw - 16);
            g.drawString(font, name, lx + 9, ry + 3, TEXT, false);
            g.drawString(font, price, lx + lw - 3 - pw, ry + 3, DIM, false);
        }
        String count = Component.translatable("citylife.realty.count", list.size()).getString();
        g.drawString(font, count, lx + lw - font.width(count), top() + 42, DIM, false);

        // Карточка.
        int px = x + listW() + 8;
        int pw = w() - listW() - 16;
        g.fill(px - 2, y + 22, px + pw + 2, y + h() - 30, PANEL);
        Estate.Unit unit = selected == null ? null : Estate.get(selected);
        if (unit == null) {
            drawWrapped(g, Component.translatable("citylife.realty.pick").getString(),
                    px + 2, y + 30, pw - 4, DIM);
        } else {
            int cy = y + 28;
            g.drawString(font, unit.title(), px + 2, cy, ACCENT, false);
            cy = drawWrapped(g, unit.address(), px + 2, cy + 12, pw - 4, TEXT);
            g.drawString(font, unit.rooms(), px + 2, cy + 2, DIM, false);
            g.drawString(font, DISTRICTS.getOrDefault(unit.district(), unit.district()),
                    px + 2, cy + 13, DIM, false);
            g.drawString(font, Money.format(unit.price()), px + 2, cy + 26, MINE, false);
            CompoundTag owner = owners.get(unit.id());
            String status;
            int colour;
            if (owner == null) {
                status = Component.translatable("citylife.realty.free").getString();
                colour = FREE;
            } else if (owner.getBoolean("m")) {
                status = Component.translatable("citylife.realty.yours").getString();
                colour = MINE;
            } else {
                status = Component.translatable("citylife.realty.owner",
                        owner.getString("n")).getString();
                colour = TAKEN;
            }
            g.drawString(font, font.plainSubstrByWidth(status, pw - 4), px + 2, cy + 38,
                    colour, false);
            if (owner != null && owner.getBoolean("m")) {
                int ky = top() + 124 + 62;
                List<String> keys = new ArrayList<>();
                for (Tag item : owner.getList("keys", Tag.TAG_COMPOUND)) {
                    keys.add(((CompoundTag) item).getString("name"));
                }
                String line = keys.isEmpty()
                        ? Component.translatable("citylife.realty.no_keys").getString()
                        : Component.translatable("citylife.realty.keys").getString() + " "
                        + String.join(", ", keys);
                drawWrapped(g, line + (keys.isEmpty() ? "" : "  ✕"), px + 2, ky, pw - 4, DIM);
            }
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int width, int colour) {
        for (var line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, colour, false);
            y += 10;
        }
        return y;
    }

    // --- мышь ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = left() + 8;
        int lw = listW() - 8;
        int ly = listTop();
        if (mx >= lx && mx < lx + lw && my >= ly && my < ly + listRows() * ROW) {
            int index = scroll + (int) ((my - ly) / ROW);
            List<Estate.Unit> list = units();
            if (index >= 0 && index < list.size()) {
                selected = list.get(index).id();
                rebuildWidgets();
                return true;
            }
        }
        // Клик по строке ключей забирает последний выданный ключ.
        Estate.Unit unit = selected == null ? null : Estate.get(selected);
        CompoundTag owner = unit == null ? null : owners.get(unit.id());
        int px = left() + listW() + 8;
        int ky = top() + 124 + 62;
        if (owner != null && owner.getBoolean("m") && mx >= px && my >= ky && my < ky + 20) {
            var keys = owner.getList("keys", Tag.TAG_COMPOUND);
            if (!keys.isEmpty()) {
                CompoundTag args = new CompoundTag();
                args.putUUID("who", keys.getCompound(keys.size() - 1).getUUID("id"));
                act("realty_untrust", unit, args);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 3;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
