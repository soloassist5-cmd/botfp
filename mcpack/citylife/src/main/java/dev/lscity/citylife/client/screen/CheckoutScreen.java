package dev.lscity.citylife.client.screen;

import com.mojang.math.Axis;
import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Касса: товары слева, корзина справа, оплата картой или наличными.
 *
 * Оплата показывается как в жизни. «Приложить» — карта подлетает к
 * терминалу, над ним бегут волны бесконтакта, терминал пищит. «Провести» —
 * карта проходит сквозь щель сбоку терминала сверху вниз. Наличными —
 * купюры ложатся в лоток. После анимации касса спрашивает сервер, и экран
 * терминала показывает «Одобрено» или причину отказа.
 */
@OnlyIn(Dist.CLIENT)
public class CheckoutScreen extends Screen {

    private static final int ROW = 22;
    private static final long ANIM_MS = 1500;
    private static final long RESULT_MS = 2200;

    private enum Stage { IDLE, ANIM, WAIT, RESULT }

    private CompoundTag data;
    private final Map<Integer, Integer> cart = new LinkedHashMap<>();
    private int scroll;
    /** Выбранный раздел прилавка ("" — все товары). */
    private String section = "";
    private EditBox search;
    private String query = "";

    private Stage stage = Stage.IDLE;
    private String method = "";
    private long since;
    private boolean resultOk;
    /** Сколько списали по последней оплате: корзина к этому времени уже пуста. */
    private long resultTotal;
    private String resultText = "";
    private boolean beeped;

    public CheckoutScreen(CompoundTag data) {
        super(Component.translatable("citylife.checkout.title", data.getString("title")));
        this.data = data;
    }

    /** Для кадров справки: открыть сразу с разделом и строкой поиска. */
    public CheckoutScreen preset(String group, String text) {
        section = group;
        query = text;
        return this;
    }

    public void update(CompoundTag fresh) {
        data = fresh;
        if (fresh.contains("result")) {
            CompoundTag result = fresh.getCompound("result");
            resultOk = result.getBoolean("ok");
            resultText = result.getString("message");
            resultTotal = result.getLong("total");
            stage = Stage.RESULT;
            since = System.currentTimeMillis();
            playSound(resultOk);
            if (resultOk) {
                cart.clear();
            }
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    // --- размеры и данные ---------------------------------------------------------

    private int w() {
        return Math.min(width - 16, grouped() ? 540 : 430);
    }

    private int h() {
        return Math.min(height - 16, grouped() ? 300 : 250);
    }

    private int left() {
        return (width - w()) / 2;
    }

    private int top() {
        return (height - h()) / 2;
    }

    /** Ширина колонки разделов слева (0, если у прилавка нет разделов). */
    private int side() {
        return grouped() ? Math.min(100, w() / 5) : 0;
    }

    private int listX() {
        return left() + 6 + side();
    }

    private int listW() {
        return (w() - side()) * (grouped() ? 52 : 54) / 100;
    }

    private int cartX() {
        return listX() + listW() + 6;
    }

    private int cartW() {
        return left() + w() - 8 - cartX();
    }

    private int rows() {
        return Math.max(1, (h() - 40) / ROW);
    }

    private ListTag items() {
        return data.getList("items", Tag.TAG_COMPOUND);
    }

    /** Разделы прилавка в порядке каталога. */
    private List<String> groups() {
        List<String> out = new ArrayList<>();
        ListTag items = items();
        for (int i = 0; i < items.size(); i++) {
            String g = items.getCompound(i).getString("g");
            if (!g.isEmpty() && !out.contains(g)) {
                out.add(g);
            }
        }
        return out;
    }

    private boolean grouped() {
        return groups().size() > 1;
    }

    /** Есть ли поиск: у больших прилавков. */
    private boolean searchable() {
        return items().size() > 14;
    }

    private static String norm(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
    }

    /** Товары выбранного раздела, подходящие под поиск. */
    private List<CompoundTag> shown() {
        List<CompoundTag> out = new ArrayList<>();
        ListTag items = items();
        String q = norm(query.trim());
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            if (!q.isEmpty()) {
                // Поиск идёт по всему прилавку, а не только по открытому разделу.
                ItemStack stack = ItemStack.of(entry.getCompound("stack"));
                if (!norm(stack.getHoverName().getString()).contains(q)
                        && !norm(entry.getString("g")).contains(q)) {
                    continue;
                }
            } else if (!section.isEmpty() && !section.equals(entry.getString("g"))) {
                continue;
            }
            out.add(entry);
        }
        return out;
    }

    /** Строки колонки разделов: «Все товары» и разделы. */
    private List<String> sideRows() {
        List<String> out = new ArrayList<>();
        out.add("");
        out.addAll(groups());
        return out;
    }

    /** Высота строки раздела: все разделы влезают в окно по высоте. */
    private int sideRow() {
        return Math.max(10, Math.min(15, (h() - 30) / Math.max(1, sideRows().size())));
    }

    private CompoundTag item(int index) {
        ListTag items = items();
        for (int i = 0; i < items.size(); i++) {
            if (items.getCompound(i).getInt("i") == index) {
                return items.getCompound(i);
            }
        }
        return null;
    }

    private long total() {
        long sum = 0;
        for (var e : cart.entrySet()) {
            CompoundTag entry = item(e.getKey());
            if (entry != null) {
                sum += entry.getLong("price") * e.getValue();
            }
        }
        return sum;
    }

    private boolean hasCard() {
        return data.contains("card");
    }

    // --- кнопки ----------------------------------------------------------------------

    @Override
    protected void init() {
        search = null;
        if (stage != Stage.IDLE) {
            return;
        }
        if (searchable()) {
            int sw = Math.min(130, listW() - 10);
            search = new EditBox(font, listX() + listW() - sw, top() + 5, sw, 12,
                    Component.translatable("citylife.checkout.search"));
            search.setHint(Component.translatable("citylife.checkout.search"));
            search.setValue(query);
            search.setResponder(text -> {
                query = text;
                scroll = 0;
            });
            addRenderableWidget(search);
        }
        int x = cartX();
        int bw = cartW();
        int y = top() + h() - 26;
        boolean any = !cart.isEmpty();
        Button cash = Button.builder(Component.translatable("citylife.checkout.pay_cash"),
                b -> start("cash")).bounds(x, y, bw, 18).build();
        cash.active = any;
        addRenderableWidget(cash);
        int half = bw / 2 - 2;
        // В узком окне — короткие подписи, чтобы не обрезались.
        boolean narrow = font.width(Component.translatable("citylife.checkout.tap")) + 8 > half;
        Button tap = Button.builder(Component.translatable(narrow ? "citylife.checkout.tap_short"
                : "citylife.checkout.tap"), b -> start("tap")).bounds(x, y - 22, half, 18).build();
        Button swipe = Button.builder(Component.translatable(narrow ? "citylife.checkout.swipe_short"
                : "citylife.checkout.swipe"),
                b -> start("swipe")).bounds(x + half + 4, y - 22, half, 18).build();
        tap.active = any && hasCard();
        swipe.active = any && hasCard();
        addRenderableWidget(tap);
        addRenderableWidget(swipe);
        Button clear = Button.builder(Component.translatable("citylife.checkout.clear"), b -> {
            cart.clear();
            rebuildWidgets();
        }).bounds(x, y - 44, data.getBoolean("barter") ? half : bw, 16).build();
        clear.active = any;
        addRenderableWidget(clear);
        if (data.getBoolean("barter")) {
            addRenderableWidget(Button.builder(Component.translatable("citylife.checkout.barter"), b -> {
                CompoundTag args = new CompoundTag();
                args.putUUID("clerk", data.getUUID("clerk"));
                Net.sendAction("checkout_barter", args);
            }).bounds(x + half + 4, y - 44, half, 16).build());
        }
    }

    private void start(String how) {
        method = how;
        stage = Stage.ANIM;
        since = System.currentTimeMillis();
        beeped = false;
        rebuildWidgets();
    }

    private void send() {
        CompoundTag args = new CompoundTag();
        args.putUUID("clerk", data.getUUID("clerk"));
        args.putString("method", "cash".equals(method) ? "cash" : "card");
        ListTag list = new ListTag();
        for (var e : cart.entrySet()) {
            CompoundTag line = new CompoundTag();
            line.putInt("i", e.getKey());
            line.putInt("n", e.getValue());
            list.add(line);
        }
        args.put("cart", list);
        Net.sendAction("checkout_pay", args);
    }

    private void playSound(boolean ok) {
        if (minecraft == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                ok ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
                ok ? 2.0F : 0.6F));
    }

    // --- отрисовка -------------------------------------------------------------------

    @Override
    public void tick() {
        long age = System.currentTimeMillis() - since;
        if (stage == Stage.ANIM && age >= ANIM_MS) {
            stage = Stage.WAIT;
            since = System.currentTimeMillis();
            send();
        } else if (stage == Stage.RESULT && age >= RESULT_MS) {
            stage = Stage.IDLE;
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int x = left();
        int y = top();
        PhoneUi.roundedRect(g, x, y, w(), h(), 8, 0xF0141824);
        g.drawString(font, title, x + 10, y + 9, 0xFFEDEFF7, false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("citylife.checkout.hint")
                .getString(), listW()), listX(), y + h() - 12, 0xFF5A6078, false);

        // Разделы прилавка.
        if (grouped()) {
            List<String> side = sideRows();
            for (int k = 0; k < side.size(); k++) {
                int[] r = {x + 6, y + 24 + k * sideRow(), side() - 6, sideRow() - 2};
                boolean active = query.isBlank() && side.get(k).equals(section);
                boolean hover = idle() && inside(mouseX, mouseY, r);
                PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 4,
                        active ? 0xFF1F6FD0 : hover ? 0xFF262B3B : 0xFF1B1F2B);
                String label = side.get(k).isEmpty()
                        ? Component.translatable("citylife.checkout.all").getString() : side.get(k);
                g.drawString(font, font.plainSubstrByWidth(label, r[2] - 8), r[0] + 5, r[1] + (r[3] - 8) / 2,
                        active ? 0xFFFFFFFF : 0xFFC8CDDA, false);
            }
        }

        // Товары.
        List<CompoundTag> items = shown();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, items.size() - rows())));
        ItemStack hovered = ItemStack.EMPTY;
        if (items.isEmpty()) {
            g.drawString(font, Component.translatable("citylife.checkout.nothing"), listX() + 6,
                    y + 30, 0xFF9AA0B4, false);
        }
        for (int i = scroll; i < items.size() && i - scroll < rows(); i++) {
            CompoundTag entry = items.get(i);
            int ry = y + 24 + (i - scroll) * ROW;
            int[] r = {listX(), ry, listW(), ROW - 2};
            boolean hover = idle() && inside(mouseX, mouseY, r);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 5, hover ? 0xFF262B3B : 0xFF1B1F2B);
            ItemStack stack = ItemStack.of(entry.getCompound("stack"));
            g.renderItem(stack, r[0] + 3, r[1] + 2);
            g.renderItemDecorations(font, stack, r[0] + 3, r[1] + 2);
            String price = Money.format(entry.getLong("price"));
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(),
                    r[2] - 30 - font.width(price) - 8);
            g.drawString(font, name, r[0] + 24, r[1] + 6, 0xFFEDEFF7, false);
            g.drawString(font, price, r[0] + r[2] - font.width(price) - 6, r[1] + 6, 0xFF7BE07B, false);
            Integer qty = cart.get(entry.getInt("i"));
            if (qty != null) {
                String badge = "×" + qty;
                PhoneUi.roundedRect(g, r[0] + 14, r[1] + 11, font.width(badge) + 4, 9, 3, 0xFF1F6FD0);
                g.drawString(font, badge, r[0] + 16, r[1] + 12, 0xFFFFFFFF, false);
            }
            if (hover) {
                hovered = stack;
            }
        }

        // Корзина и деньги.
        int cx = cartX();
        int cw = cartW();
        g.drawString(font, Component.translatable("citylife.checkout.cart"), cx, y + 24, 0xFF9AA0B4, false);
        int ly = y + 36;
        List<Integer> keys = new ArrayList<>(cart.keySet());
        int maxLines = Math.max(1, (h() - 160) / 10);
        for (int k = 0; k < keys.size() && k < maxLines; k++) {
            CompoundTag entry = item(keys.get(k));
            if (entry == null) {
                continue;
            }
            int qty = cart.get(keys.get(k));
            ItemStack stack = ItemStack.of(entry.getCompound("stack"));
            String sum = Money.format(entry.getLong("price") * qty);
            String line = font.plainSubstrByWidth(qty + "× " + stack.getHoverName().getString(),
                    cw - font.width(sum) - 6);
            g.drawString(font, line, cx, ly, 0xFFEDEFF7, false);
            g.drawString(font, sum, cx + cw - font.width(sum), ly, 0xFFB8C0D0, false);
            ly += 10;
        }
        if (keys.size() > maxLines) {
            g.drawString(font, "…", cx, ly, 0xFF9AA0B4, false);
        }
        int my = top() + h() - 26 - 44 - 34;
        g.fill(cx, my - 4, cx + cw, my - 3, 0x33FFFFFF);
        g.drawString(font, Component.translatable("citylife.checkout.total", Money.format(total())),
                cx, my, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("citylife.checkout.balance",
                Money.format(data.getLong("balance"))), cx, my + 11, 0xFF9AA0B4, false);
        g.drawString(font, hasCard() ? Component.translatable("citylife.checkout.cash",
                Money.format(data.getLong("cash"))) : Component.translatable(
                "citylife.checkout.nocard_hint"), cx, my + 21, 0xFF9AA0B4, false);

        super.render(g, mouseX, mouseY, partial);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
        if (stage != Stage.IDLE) {
            renderPayment(g);
        }
    }

    private boolean idle() {
        return stage == Stage.IDLE;
    }

    private static boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    /** Оплата: терминал по центру, карта или купюры, надпись на экране терминала. */
    private void renderPayment(GuiGraphics g) {
        g.fill(left(), top(), left() + w(), top() + h(), 0xC0080A10);
        int tx = left() + w() / 2 + 20;
        int ty = top() + h() / 2 - 52;
        int tw = 62;
        int th = 104;
        long age = System.currentTimeMillis() - since;
        float t = Math.min(1F, age / (float) ANIM_MS);

        // Терминал: корпус, экран, кнопки.
        PhoneUi.roundedRect(g, tx, ty, tw, th, 8, 0xFF1D2029);
        PhoneUi.roundedOutline(g, tx, ty, tw, th, 8, 0xFF3A3F50);
        int sx = tx + 6;
        int sy = ty + 8;
        int sw = tw - 12;
        int sh = 38;
        int screenColour = switch (stage) {
            case RESULT -> resultOk ? 0xFF1E6B3A : 0xFF7A1F1F;
            default -> 0xFF0E2A3A;
        };
        PhoneUi.roundedRect(g, sx, sy, sw, sh, 3, screenColour);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                PhoneUi.roundedRect(g, tx + 9 + c * 16, ty + 52 + r * 12, 12, 9, 2,
                        r == 3 && c == 2 ? 0xFF2F9E4F : r == 3 && c == 0 ? 0xFFB03434 : 0xFF3A3F50);
            }
        }
        String text;
        if (stage == Stage.RESULT) {
            text = (resultOk ? "✔ " : "✖ ") + resultText;
        } else if (stage == Stage.WAIT || t > 0.85F) {
            text = Component.translatable("citylife.checkout.processing").getString();
        } else if ("cash".equals(method)) {
            text = Component.translatable("citylife.checkout.cash_wait").getString();
        } else {
            text = Component.translatable("swipe".equals(method) ? "citylife.checkout.insert"
                    : "citylife.checkout.present").getString();
        }
        String sum = Money.format(stage == Stage.RESULT ? resultTotal : total());
        if (stage != Stage.RESULT || resultOk) {
            g.drawString(font, sum, sx + (sw - font.width(sum)) / 2, sy + 6, 0xFFFFFFFF, false);
        }
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), sw - 4);
        for (int i = 0; i < lines.size() && i < 2; i++) {
            g.drawString(font, lines.get(i), sx + 2, sy + 18 + i * 9, 0xFFDDF4FF, false);
        }
        if (stage == Stage.RESULT) {
            return;
        }

        if ("cash".equals(method)) {
            // Купюры по одной ложатся в лоток под терминалом.
            for (int n = 0; n < 4; n++) {
                float k = Math.max(0, Math.min(1, t * 1.6F - n * 0.18F));
                float e = PhoneUi.ease(k);
                int bx = (int) (left() + 40 + (tx + 6 - left() - 40) * e);
                int by = (int) (top() + h() - 40 + (ty + th + 4 - top() - h() + 40) * e) - n * 2;
                PhoneUi.roundedRect(g, bx, by, 50, 22, 2, n % 2 == 0 ? 0xFF4E8B5A : 0xFF6A7FB0);
                g.drawString(font, "₽", bx + 21, by + 7, 0xFFFFFFFF, false);
            }
            return;
        }

        int cardW = 64;
        int cardH = 40;
        int colour = "mastercard".equals(data.getString("card")) ? 0xFFE8703A : 0xFF2E86DE;
        if ("swipe".equals(method)) {
            // Карта стоит вертикально и проходит сквозь щель справа от терминала.
            int slotX = tx + tw + 2;
            g.fill(slotX - 1, ty + 6, slotX + 3, ty + th - 6, 0xFF0A0B0F);
            float e = PhoneUi.ease(t);
            int cy = (int) (ty - cardW - 10 + (th + cardW) * e);
            g.enableScissor(slotX - 40, top(), slotX + 60, top() + h());
            g.pose().pushPose();
            g.pose().translate(slotX + 2, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(90));
            drawCard(g, 0, 0, cardW, cardH, colour);
            g.pose().popPose();
            g.disableScissor();
            if (t > 0.5F && !beeped) {
                beeped = true;
                beep();
            }
            return;
        }
        // Приложить: карта подлетает к верху терминала, бегут волны бесконтакта.
        float e = PhoneUi.ease(Math.min(1, t * 1.4F));
        int startX = left() + 30;
        int startY = top() + h() - 20;
        int endX = tx + (tw - cardW) / 2;
        int endY = ty - cardH / 2 - 6;
        int cxp = (int) (startX + (endX - startX) * e);
        int cyp = (int) (startY + (endY - startY) * e);
        if (t > 0.65F) {
            int waves = (int) ((age / 120) % 4);
            for (int i = 0; i <= waves; i++) {
                int r = 6 + i * 6;
                PhoneUi.roundedOutline(g, tx + tw / 2 - r, ty - 18 - r / 2, r * 2, r, r / 2,
                        PhoneUi.alpha(0xFF45D0F0, 1F - i * 0.22F));
            }
            if (!beeped) {
                beeped = true;
                beep();
            }
        }
        drawCard(g, cxp, cyp, cardW, cardH, colour);
    }

    private void beep() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_BIT.value(), 1.8F));
        }
    }

    /** Банковская карта: цвет платёжной системы, чип, последние цифры номера. */
    private void drawCard(GuiGraphics g, int x, int y, int w, int h, int colour) {
        PhoneUi.shadow(g, x, y, w, h, 5);
        PhoneUi.roundedGradient(g, x, y, w, h, 5, PhoneUi.lerp(colour, 0xFFFFFFFF, 0.2F), colour);
        PhoneUi.roundedRect(g, x + 6, y + 10, 11, 8, 2, 0xFFE0C068);
        g.fill(x + 6, y + 13, x + 17, y + 14, 0xFFB89A48);
        String number = data.getString("cardNumber");
        String last = number.length() >= 4 ? "•••• " + number.substring(number.length() - 4) : "••••";
        g.drawString(font, last, x + 6, y + h - 12, 0xFFFFFFFF, false);
        if ("mastercard".equals(data.getString("card"))) {
            PhoneUi.disc(g, x + w - 16, y + 10, 5, 0xFFEB001B);
            PhoneUi.disc(g, x + w - 10, y + 10, 5, 0xCCF79E1B);
        } else {
            g.drawString(font, "МИР", x + w - font.width("МИР") - 5, y + 5, 0xFFFFFFFF, false);
        }
    }

    // --- ввод ------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!idle()) {
            return true;
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (grouped()) {
            List<String> side = sideRows();
            for (int k = 0; k < side.size(); k++) {
                if (inside(mx, my, new int[]{left() + 6, top() + 24 + k * sideRow(), side() - 6,
                        sideRow() - 2})) {
                    section = side.get(k);
                    scroll = 0;
                    if (search != null && !query.isEmpty()) {
                        search.setValue("");
                    }
                    return true;
                }
            }
        }
        List<CompoundTag> items = shown();
        for (int i = scroll; i < items.size() && i - scroll < rows(); i++) {
            int ry = top() + 24 + (i - scroll) * ROW;
            if (inside(mx, my, new int[]{listX(), ry, listW(), ROW - 2})) {
                int index = items.get(i).getInt("i");
                int step = hasShiftDown() ? 5 : 1;
                int qty = cart.getOrDefault(index, 0) + (button == 1 ? -step : step);
                if (qty <= 0) {
                    cart.remove(index);
                } else {
                    cart.put(index, Math.min(64, qty));
                }
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
        if (!idle() && code != 256) {
            return true;
        }
        return super.keyPressed(code, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
