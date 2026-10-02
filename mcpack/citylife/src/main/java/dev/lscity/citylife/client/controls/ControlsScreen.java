package dev.lscity.citylife.client.controls;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Справочник управления: копия клавиатуры, на каждой клавише — что она
 * делает, короткой понятной подписью («Колесо брони», «Перезарядка»).
 *
 * Занятые клавиши окрашены цветом группы (костюм, оружие, транспорт,
 * голос, карта, дроны…), свободные — приглушены. Справа — панель: что на
 * клавише под курсором (клик закрепляет), а пока ничего не выбрано —
 * «Главное»: самые нужные клавиши сборки с тем, куда они назначены сейчас,
 * и мышь. Поиск по названию действия подсвечивает его клавишу; вкладки
 * Alt / Ctrl / Shift — сочетания; красная рамка с «!» — две команды
 * срабатывают от одного нажатия.
 *
 * Данные берутся из настроек игры прямо сейчас, поэтому справочник всегда
 * совпадает с тем, что реально назначено, в том числе после переназначения.
 */
@OnlyIn(Dist.CLIENT)
public class ControlsScreen extends Screen {

    /** Клавиша на схеме: имя GLFW, подпись, ширина в долях клавиши, отступ перед ней. */
    private record Cap(String key, String label, float w, float gap) {
    }

    private static final List<List<Cap>> ROWS = new ArrayList<>();
    private static final List<List<Cap>> NAV = new ArrayList<>();
    private static final Map<String, String> CYR = new HashMap<>();
    private static final String[] MOUSE = {"key.mouse.left", "key.mouse.right", "key.mouse.middle",
            "key.mouse.4", "key.mouse.5"};

    static {
        ROWS.add(row("escape:Esc", "f1:F1:1", "f2:F2", "f3:F3", "f4:F4", "f5:F5:0.5", "f6:F6", "f7:F7", "f8:F8",
                "f9:F9:0.5", "f10:F10", "f11:F11", "f12:F12"));
        ROWS.add(row("grave.accent:`", "1:1", "2:2", "3:3", "4:4", "5:5", "6:6", "7:7", "8:8", "9:9", "0:0",
                "minus:-", "equal:=", "backspace:Backspace/2"));
        ROWS.add(row("tab:Tab/1.5", "q:Q", "w:W", "e:E", "r:R", "t:T", "y:Y", "u:U", "i:I", "o:O", "p:P",
                "left.bracket:[", "right.bracket:]", "backslash:\\/1.5"));
        ROWS.add(row("caps.lock:Caps/1.75", "a:A", "s:S", "d:D", "f:F", "g:G", "h:H", "j:J", "k:K", "l:L",
                "semicolon:;", "apostrophe:'", "enter:Enter/2.25"));
        ROWS.add(row("left.shift:Shift/2.25", "z:Z", "x:X", "c:C", "v:V", "b:B", "n:N", "m:M", "comma:,",
                "period:.", "slash:/", "right.shift:Shift/2.75"));
        ROWS.add(row("left.control:Ctrl/1.25", "left.win:Win/1.25", "left.alt:Alt/1.25", "space:Пробел/6.25",
                "right.alt:Alt/1.25", "right.win:Win/1.25", "menu:Меню/1.25", "right.control:Ctrl/1.25"));
        NAV.add(row("print.screen:PrtSc", "scroll.lock:ScrLk", "pause:Pause"));
        NAV.add(row("insert:Ins", "home:Home", "page.up:PgUp"));
        NAV.add(row("delete:Del", "end:End", "page.down:PgDn"));
        NAV.add(row());
        NAV.add(row("up:↑:1"));
        NAV.add(row("left:←", "down:↓", "right:→"));
        String lat = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";
        String cyr = "ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮЁ";
        String[] names = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "left.bracket", "right.bracket", "a",
                "s", "d", "f", "g", "h", "j", "k", "l", "semicolon", "apostrophe", "z", "x", "c", "v", "b", "n",
                "m", "comma", "period", "grave.accent"};
        for (int i = 0; i < names.length && i < cyr.length(); i++) {
            CYR.put("key.keyboard." + names[i], String.valueOf(cyr.charAt(i)));
        }
        assert lat.length() == names.length;
    }

    /** «name:label[/width][:gap]». */
    private static List<Cap> row(String... specs) {
        List<Cap> out = new ArrayList<>();
        for (String spec : specs) {
            String[] parts = spec.split(":");
            String name = parts[0];
            String label = parts[1];
            float w = 1;
            int slash = label.lastIndexOf('/');
            if (slash > 0 && label.length() > 1) {
                w = Float.parseFloat(label.substring(slash + 1));
                label = label.substring(0, slash);
            }
            float gap = parts.length > 2 ? Float.parseFloat(parts[2]) : 0;
            out.add(new Cap("key.keyboard." + name, label, w, gap));
        }
        return out;
    }

    private static final String[] LAYERS = {"none", "alt", "ctrl", "shift"};
    private static final int TEXT = 0xFFEAF2F8;
    private static final int DIM = 0xFF8A97A8;
    private static final int ACCENT = 0xFF57D8FF;
    private static final int PANEL = 0xE0121720;

    private final Screen parent;
    private String layer = "none";
    /** Группа, выбранная в легенде, или пусто. */
    private String focus = "";
    /** Клавиша под курсором в этом кадре и закреплённая кликом. */
    private String hovered = "";
    private String pinned = "";
    private EditBox search;
    private String query = "";
    private String status = "";
    private final List<Object[]> legendHits = new ArrayList<>();
    private final Map<String, int[]> capRects = new HashMap<>();

    public ControlsScreen(Screen parent) {
        super(Component.translatable("citylife.controls.title"));
        this.parent = parent;
    }

    /** Открыть сразу нужный слой: «none», «alt», «ctrl», «shift». */
    public ControlsScreen layer(String layer) {
        this.layer = layer;
        return this;
    }

    /** Закрепить клавишу в панели (для кадров справочника). */
    public ControlsScreen pin(String key) {
        this.pinned = key;
        return this;
    }

    /** Сразу с поиском (для кадров справочника). */
    public ControlsScreen search(String text) {
        this.query = text;
        return this;
    }

    // --- данные ------------------------------------------------------------------

    private KeyModifier modifier(String l) {
        return switch (l) {
            case "alt" -> KeyModifier.ALT;
            case "ctrl" -> KeyModifier.CONTROL;
            case "shift" -> KeyModifier.SHIFT;
            default -> KeyModifier.NONE;
        };
    }

    private KeyModifier modifier() {
        return modifier(layer);
    }

    private List<KeyMapping> on(String key) {
        List<KeyMapping> out = new ArrayList<>();
        if (minecraft == null) {
            return out;
        }
        for (KeyMapping km : minecraft.options.keyMappings) {
            if (!km.isUnbound() && km.getKey().getName().equals(key) && km.getKeyModifier() == modifier()) {
                out.add(km);
            }
        }
        return out;
    }

    private int count(String l) {
        int n = 0;
        for (KeyMapping km : minecraft.options.keyMappings) {
            if (!km.isUnbound() && km.getKeyModifier() == modifier(l)) {
                n++;
            }
        }
        return n;
    }

    private Set<String> conflicted() {
        Set<String> out = new HashSet<>();
        for (KeyMapping[] pair : Controls.clashes(minecraft)) {
            if (pair[0].getKeyModifier() == modifier()) {
                out.add(pair[0].getKey().getName());
            }
        }
        return out;
    }

    private boolean matches(KeyMapping km) {
        if (query.isEmpty()) {
            return true;
        }
        String q = query.toLowerCase(java.util.Locale.ROOT);
        return KeyNames.shortName(km).toLowerCase(java.util.Locale.ROOT).contains(q)
                || KeyNames.fullName(km).toLowerCase(java.util.Locale.ROOT).contains(q)
                || KeyNames.groupTitle(KeyNames.group(km)).toLowerCase(java.util.Locale.ROOT).contains(q);
    }

    /** Подходит ли клавиша под поиск и фильтр группы. */
    private boolean lit(List<KeyMapping> acts) {
        if (query.isEmpty() && focus.isEmpty()) {
            return true;
        }
        for (KeyMapping km : acts) {
            if ((focus.isEmpty() || KeyNames.group(km).id().equals(focus)) && matches(km)) {
                return true;
            }
        }
        return false;
    }

    // --- раскладка экрана -------------------------------------------------------

    /**
     * Во сколько раз уменьшить рисунок клавиатуры: шапка с кнопками — в
     * пикселях интерфейса, а клавиатура — по возможности в настоящих
     * пикселях экрана. При «Масштабе интерфейса 2» окно в 854 точки — это
     * всего 427 точек интерфейса, и подписи на клавишах не влезали. Берём
     * такой множитель, чтобы точка шрифта была целым числом пикселей экрана
     * (буквы остаются чёткими) и по ширине вышло не меньше ~800 точек.
     */
    private float zoom() {
        if (minecraft == null) {
            return 1F;
        }
        int gui = (int) Math.round(minecraft.getWindow().getGuiScale());
        int real = minecraft.getWindow().getWidth();
        for (int k = gui; k >= 1; k--) {
            if (real / k >= 800 || k == 1) {
                return (float) k / gui;
            }
        }
        return 1F;
    }

    private float vw() {
        return width / zoom();
    }

    private float vh() {
        return height / zoom();
    }

    /** Высота полосы под клавиатурой: «Главное» или выбранная клавиша и мышь. */
    private static final int PANEL_H = 96;
    private static final int LEGEND_H = 28;

    private float unit() {
        float byW = (vw() - 20) / 18.5F;
        float byH = (vh() - top() - LEGEND_H - PANEL_H - 8) / 6.45F;
        return Math.max(14, Math.min(byW, byH));
    }

    private int left() {
        return (int) Math.max(10, (vw() - unit() * 18.5F) / 2);
    }

    private int panelW() {
        return (int) (unit() * 18.5F);
    }

    /** Верх клавиатуры в точках рисунка: под шапкой с кнопками. */
    private int top() {
        return (int) (44 / zoom());
    }

    private int panelX() {
        return left();
    }

    /** Края области клавиатуры в точках интерфейса — для кнопок шапки. */
    private int guiLeft() {
        return (int) (left() * zoom());
    }

    private int guiRight() {
        return (int) ((left() + panelW()) * zoom());
    }

    @Override
    protected void init() {
        int x = guiLeft();
        int y = 22;
        for (String l : LAYERS) {
            Component label = Component.translatable("citylife.controls.layer." + l)
                    .append(Component.literal(" " + count(l)));
            int w = font.width(label) + 12;
            Button b = Button.builder(label, btn -> {
                layer = l;
                pinned = "";
                rebuildWidgets();
            }).bounds(x, y, w, 16).build();
            b.active = !l.equals(layer);
            addRenderableWidget(b);
            x += w + 3;
        }
        int right = guiRight();
        Component reset = Component.translatable("citylife.controls.reset");
        int rw = font.width(reset) + 12;
        addRenderableWidget(Button.builder(reset, b -> {
            int n = Controls.apply(minecraft, true);
            status = Component.translatable("citylife.controls.reset_done", n).getString();
            rebuildWidgets();
        }).bounds(right - rw, 4, rw, 14).build());
        Component edit = Component.translatable("citylife.controls.edit");
        int ew = font.width(edit) + 12;
        addRenderableWidget(Button.builder(edit, b -> minecraft.setScreen(new KeyBindsScreen(this,
                minecraft.options))).bounds(right - rw - ew - 4, 4, ew, 14).build());
        int sx = x + 8;
        int sw = Math.max(80, right - sx);
        search = new EditBox(font, sx, y + 1, sw, 14, Component.translatable("citylife.controls.search"));
        search.setHint(Component.translatable("citylife.controls.search"));
        search.setValue(query);
        search.setResponder(v -> query = v.trim());
        addRenderableWidget(search);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xD007090D);
        g.drawString(font, title, guiLeft(), 7, ACCENT, false);
        super.render(g, mouseX, mouseY, partial);

        float z = zoom();
        g.pose().pushPose();
        g.pose().scale(z, z, 1);
        mouseX = (int) (mouseX / z);
        mouseY = (int) (mouseY / z);
        float u = unit();
        int x0 = left();
        int y0 = top();
        Set<String> bad = conflicted();
        hovered = "";
        capRects.clear();
        float y = y0;
        for (int r = 0; r < ROWS.size(); r++) {
            float x = x0;
            for (Cap cap : ROWS.get(r)) {
                x += cap.gap() * u;
                cap(g, cap.key(), cap.label(), x, y, cap.w() * u, u, bad, mouseX, mouseY);
                x += cap.w() * u;
            }
            y += u + (r == 0 ? u * 0.3F : 0);
        }
        y = y0;
        for (int r = 0; r < NAV.size(); r++) {
            float x = x0 + u * 15.5F;
            for (Cap cap : NAV.get(r)) {
                x += cap.gap() * u;
                cap(g, cap.key(), cap.label(), x, y, u, u, bad, mouseX, mouseY);
                x += u;
            }
            y += u + (r == 0 ? u * 0.3F : 0);
        }
        int ly = (int) (y0 + u * 6.45F);
        int legendBottom = legend(g, x0, ly, panelW(), mouseX, mouseY);
        int py = legendBottom + 4;
        panel(g, x0, py, panelW(), (int) Math.max(70, vh() - py - 6), bad, mouseX, mouseY);
        g.pose().popPose();
        if (!status.isEmpty()) {
            g.drawString(font, status, guiLeft(), height - 11, 0xFF5CFF9D, false);
        }
    }

    /** Клавиша: объёмная, занятая — цвета группы, со значками конфликта и числа действий. */
    private void cap(GuiGraphics g, String key, String label, float fx, float fy, float fw, float fh,
                     Set<String> bad, int mouseX, int mouseY) {
        int x = Math.round(fx) + 1;
        int y = Math.round(fy) + 1;
        int w = Math.round(fw) - 2;
        int h = Math.round(fh) - 2;
        capRects.put(key, new int[]{x, y, w, h});
        List<KeyMapping> acts = on(key);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (hover) {
            hovered = key;
        }
        boolean held = "alt".equals(layer) && key.endsWith(".alt") || "ctrl".equals(layer) && key.endsWith(".control")
                || "shift".equals(layer) && key.endsWith(".shift");
        boolean on = !acts.isEmpty();
        boolean lit = lit(acts) && (on || query.isEmpty() && focus.isEmpty());
        int colour = on ? KeyNames.group(acts.get(0)).colour() : 0xFF262C36;
        if (held) {
            colour = 0xFF2F8F9F;
        }
        int face = on || held ? PhoneUi.lerp(0xFF1B2029, colour, 0.62F) : 0xFF181C23;
        int edge = on || held ? PhoneUi.lerp(colour, 0xFF000000, 0.45F) : 0xFF0F1217;
        if (!lit) {
            face = PhoneUi.lerp(face, 0xFF0B0D11, 0.7F);
            edge = PhoneUi.lerp(edge, 0xFF0B0D11, 0.7F);
        }
        if (hover || key.equals(pinned)) {
            face = PhoneUi.lerp(face, 0xFFFFFFFF, 0.14F);
        }
        // Объём: тёмный «бортик» снизу и светлая кромка сверху.
        PhoneUi.roundedRect(g, x, y, w, h, 3, edge);
        PhoneUi.roundedRect(g, x, y, w, h - 2, 3, face);
        g.fill(x + 3, y + 1, x + w - 3, y + 2, PhoneUi.alpha(0xFFFFFFFF, on && lit ? 0.18F : 0.06F));
        if (key.equals(pinned)) {
            PhoneUi.roundedOutline(g, x - 1, y - 1, w + 2, h + 2, 4, ACCENT);
        } else if (!query.isEmpty() && on && lit) {
            PhoneUi.roundedOutline(g, x - 1, y - 1, w + 2, h + 2, 4, 0xFFFFD35C);
        }
        boolean conflict = bad.contains(key) && on;
        if (conflict) {
            PhoneUi.roundedOutline(g, x, y, w, h, 3, System.currentTimeMillis() / 450 % 2 == 0
                    ? 0xFFFF4040 : 0xFFB02020);
        }
        int textColour = !lit ? 0x55FFFFFF : on ? TEXT : 0xFF6B7584;
        float s1 = Math.max(0.6F, Math.min(1.0F, h / 30F));
        text(g, label, x + 3, y + 2, s1, textColour);
        String cyr = CYR.get(key);
        if (cyr != null && w > 14) {
            float sc = s1 * 0.8F;
            text(g, cyr, x + w - 3 - font.width(cyr) * sc, y + 2, sc, PhoneUi.alpha(textColour, 0.55F));
        }
        // Число действий на клавише и «!» при конфликте — в правом нижнем углу.
        if (conflict || acts.size() > 1) {
            String badge = conflict ? "!" : String.valueOf(acts.size());
            int bw = Math.max(7, (int) (font.width(badge) * 0.6F) + 4);
            PhoneUi.roundedRect(g, x + w - bw - 1, y + h - 9, bw, 7, 3, conflict ? 0xFFFF4040 : 0xCC000000);
            text(g, badge, x + w - bw + 1, y + h - 8, 0.6F, 0xFFFFFFFF);
        }
        if (on && h >= 20) {
            float s2 = Math.max(0.6F, Math.min(0.85F, h / 40F));
            String what = KeyNames.shortName(acts.get(0));
            int lineW = (int) ((w - 6) / s2);
            List<String> lines = wrap(what, lineW, h >= 30 ? 2 : 1);
            float ty = y + h - 4 - lines.size() * 9 * s2;
            for (String line : lines) {
                float lw = font.width(line) * s2;
                text(g, line, x + (w - lw) / 2, ty, s2, lit ? 0xFFFFFFFF : 0x55FFFFFF);
                ty += 9 * s2;
            }
        }
    }

    /** Полоса под клавиатурой: слева выбранная клавиша или «Главное», справа — мышь. */
    private void panel(GuiGraphics g, int x, int y, int w, int h, Set<String> bad, int mouseX, int mouseY) {
        PhoneUi.roundedRect(g, x, y, w, h, 6, PANEL);
        PhoneUi.roundedOutline(g, x, y, w, h, 6, 0x3357D8FF);
        int mouseW = Math.min(230, w / 3);
        int listW = w - mouseW - 18;
        int bottom = y + h - 4;
        String key = !hovered.isEmpty() ? hovered : pinned;
        int ty = y + 6;
        if (key.isEmpty()) {
            g.drawString(font, Component.translatable("citylife.controls.main"), x + 8, ty, ACCENT, false);
            ty += 13;
            // «Главное» в две колонки: действие слева, его клавиша — плашкой справа.
            int colW = listW / 2 - 6;
            int col = 0;
            int rowY = ty;
            for (String name : KeyNames.ESSENTIALS) {
                KeyMapping km = find(name);
                if (km == null) {
                    continue;
                }
                if (rowY + 10 > bottom - 10) {
                    if (col == 1) {
                        break;
                    }
                    col = 1;
                    rowY = ty;
                }
                int cx = x + 8 + col * (colW + 12);
                String keyText = km.isUnbound() ? "—" : km.getTranslatedKeyMessage().getString();
                int kw = font.width(keyText) + 8;
                PhoneUi.roundedRect(g, cx + colW - kw, rowY - 1, kw, 11, 3,
                        PhoneUi.lerp(0xFF1B2029, KeyNames.group(km).colour(), 0.65F));
                g.drawString(font, keyText, cx + colW - kw + 4, rowY + 1, TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(KeyNames.shortName(km), colW - kw - 6), cx, rowY + 1,
                        TEXT, false);
                rowY += 12;
            }
            String tip = Component.translatable("citylife.controls.tip").getString();
            g.drawString(font, font.plainSubstrByWidth(tip, listW), x + 8, bottom - 9, DIM, false);
        } else {
            InputConstants.Key k = InputConstants.getKey(key);
            String prefix = switch (layer) {
                case "alt" -> "Alt + ";
                case "ctrl" -> "Ctrl + ";
                case "shift" -> "Shift + ";
                default -> "";
            };
            g.drawString(font, prefix + k.getDisplayName().getString()
                    + (key.equals(pinned) ? "  · " + Component.translatable("citylife.controls.pinned").getString()
                    : ""), x + 8, ty, ACCENT, false);
            ty += 14;
            List<KeyMapping> acts = on(key);
            if (acts.isEmpty()) {
                g.drawString(font, Component.translatable("citylife.controls.free"), x + 8, ty, DIM, false);
            }
            for (KeyMapping km : acts) {
                KeyNames.Group grp = KeyNames.group(km);
                if (ty + 10 > bottom) {
                    break;
                }
                PhoneUi.disc(g, x + 11, ty + 4, 3, grp.colour());
                String head = KeyNames.shortName(km);
                g.drawString(font, head, x + 18, ty, TEXT, false);
                String sub = "  " + KeyNames.groupTitle(grp) + " · " + KeyNames.fullName(km);
                g.drawString(font, font.plainSubstrByWidth(sub, listW - 22 - font.width(head)),
                        x + 18 + font.width(head), ty, DIM, false);
                ty += 11;
            }
            if (acts.size() > 1 && ty + 10 <= bottom) {
                boolean clash = bad.contains(key);
                g.drawString(font, font.plainSubstrByWidth(Component.translatable(clash ? "citylife.controls.clash"
                        : "citylife.controls.shared").getString(), listW), x + 8, ty + 2,
                        clash ? 0xFFFF6B6B : 0xFF5CFF9D, false);
            }
        }
        g.fill(x + listW + 10, y + 6, x + listW + 11, y + h - 6, 0x2257D8FF);
        mouse(g, x + listW + 16, y + 6, mouseW - 6, h - 12, bad, mouseX, mouseY);
    }

    private KeyMapping find(String name) {
        for (KeyMapping km : minecraft.options.keyMappings) {
            if (km.getName().equals(name)) {
                return km;
            }
        }
        return null;
    }

    private void mouse(GuiGraphics g, int x, int y, int w, int h, Set<String> bad, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("citylife.controls.mouse"), x, y, DIM, false);
        int mw = Math.min(w / 2, (int) (h * 0.62F));
        int mx = x + 2;
        int my = y + 11;
        int mh = h - 12;
        PhoneUi.roundedRect(g, mx, my, mw, mh, mw / 3, 0xFF181C23);
        PhoneUi.roundedOutline(g, mx, my, mw, mh, mw / 3, 0x40FFFFFF);
        float half = mw / 2F;
        cap(g, MOUSE[0], "Л", mx, my, half, mh * 0.42F, bad, mouseX, mouseY);
        cap(g, MOUSE[1], "П", mx + half, my, half, mh * 0.42F, bad, mouseX, mouseY);
        cap(g, MOUSE[2], "◎", mx + half - mw * 0.14F, my + mh * 0.08F, mw * 0.28F, mh * 0.26F, bad, mouseX, mouseY);
        cap(g, MOUSE[3], "4", mx - 2, my + mh * 0.5F, mw * 0.3F, mh * 0.2F, bad, mouseX, mouseY);
        cap(g, MOUSE[4], "5", mx - 2, my + mh * 0.72F, mw * 0.3F, mh * 0.2F, bad, mouseX, mouseY);
        // Подписи кнопок мыши справа от неё: «ЛКМ — Удар / ломать», по строке на кнопку.
        int lx = mx + mw + 6;
        int ly = my + 1;
        int step = Math.max(10, Math.min(14, mh / 5));
        String[] names = {"ЛКМ", "ПКМ", "Колесо", "Бок. 4", "Бок. 5"};
        for (int i = 0; i < MOUSE.length; i++) {
            List<KeyMapping> acts = on(MOUSE[i]);
            String what = acts.isEmpty() ? "—" : KeyNames.shortName(acts.get(0))
                    + (acts.size() > 1 ? " +" + (acts.size() - 1) : "");
            g.drawString(font, names[i], lx, ly, DIM, false);
            int nx = lx + font.width("Колесо ") ;
            g.drawString(font, font.plainSubstrByWidth(what, Math.max(10, x + w - nx)), nx, ly,
                    acts.isEmpty() ? DIM : TEXT, false);
            ly += step;
        }
    }

    /** Легенда групп: цвет, название, сколько клавиш; клик — только эта группа. */
    private int legend(GuiGraphics g, int x0, int y, int maxW, int mouseX, int mouseY) {
        legendHits.clear();
        Map<KeyNames.Group, Integer> counts = new LinkedHashMap<>();
        for (KeyNames.Group grp : KeyNames.GROUPS) {
            counts.put(grp, 0);
        }
        for (KeyMapping km : minecraft.options.keyMappings) {
            if (!km.isUnbound() && km.getKeyModifier() == modifier()) {
                counts.merge(KeyNames.group(km), 1, Integer::sum);
            }
        }
        int x = x0;
        for (var e : counts.entrySet()) {
            if (e.getValue() == 0) {
                continue;
            }
            String label = KeyNames.groupTitle(e.getKey()) + " " + e.getValue();
            int w = font.width(label) + 16;
            if (x + w > x0 + maxW) {
                x = x0;
                y += 13;
            }
            boolean on = e.getKey().id().equals(focus);
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 11;
            PhoneUi.roundedRect(g, x, y, w, 11, 5, on ? PhoneUi.alpha(e.getKey().colour(), 0.55F)
                    : hover ? 0x30FFFFFF : 0x18FFFFFF);
            PhoneUi.disc(g, x + 6, y + 5, 3, e.getKey().colour());
            g.drawString(font, label, x + 12, y + 2, TEXT, false);
            legendHits.add(new Object[]{x, y, w, e.getKey().id()});
            x += w + 3;
        }
        return y + 12;
    }

    @Override
    public boolean mouseClicked(double gx, double gy, int button) {
        if (super.mouseClicked(gx, gy, button)) {
            return true;
        }
        double mx = gx / zoom();
        double my = gy / zoom();
        for (Object[] hit : legendHits) {
            int x = (int) hit[0];
            int y = (int) hit[1];
            int w = (int) hit[2];
            if (mx >= x && mx < x + w && my >= y && my < y + 11) {
                String id = (String) hit[3];
                focus = id.equals(focus) ? "" : id;
                return true;
            }
        }
        for (var e : capRects.entrySet()) {
            int[] r = e.getValue();
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                pinned = e.getKey().equals(pinned) ? "" : e.getKey();
                return true;
            }
        }
        pinned = "";
        return false;
    }

    private void text(GuiGraphics g, String s, float x, float y, float scale, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Разбить подпись на строки шириной width, не больше max строк; лишнее — многоточием. */
    private List<String> wrap(String text, int width, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++) {
            String next = line.isEmpty() ? words[i] : line + " " + words[i];
            if (font.width(next) <= width || line.isEmpty()) {
                line = new StringBuilder(next);
                continue;
            }
            out.add(line.toString());
            line = new StringBuilder(words[i]);
            if (out.size() == max) {
                line = new StringBuilder();
                out.set(max - 1, out.get(max - 1) + "…");
                break;
            }
        }
        if (out.size() < max && !line.isEmpty()) {
            out.add(line.toString());
        }
        for (int i = 0; i < out.size(); i++) {
            if (font.width(out.get(i)) > width) {
                out.set(i, font.plainSubstrByWidth(out.get(i), Math.max(0, width - font.width("…"))) + "…");
            }
        }
        return out;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
