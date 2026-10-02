package dev.lscity.citylife.client.controls;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * делает. Цвет клавиши — мод (легенда внизу), красная рамка — две команды
 * на одной клавише. Слои «Alt / Ctrl / Shift» показывают сочетания, справа —
 * мышь. Наведи на клавишу — полный список её действий; клик по моду в
 * легенде оставляет подсвеченными только его клавиши.
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

    private final Screen parent;
    private String layer = "none";
    private String focus = "";
    /** Что было под курсором при прошлом кадре — для панели подробностей. */
    private String hovered = "";
    private final Map<String, Integer> colours = new LinkedHashMap<>();
    private String status = "";

    public ControlsScreen(Screen parent) {
        super(Component.translatable("citylife.controls.title"));
        this.parent = parent;
    }

    /** Открыть сразу нужный слой: «none», «alt», «ctrl», «shift». */
    public ControlsScreen layer(String layer) {
        this.layer = layer;
        return this;
    }

    // --- данные ------------------------------------------------------------------

    private KeyModifier modifier() {
        return switch (layer) {
            case "alt" -> KeyModifier.ALT;
            case "ctrl" -> KeyModifier.CONTROL;
            case "shift" -> KeyModifier.SHIFT;
            default -> KeyModifier.NONE;
        };
    }

    /** Действия на клавише в текущем слое модификатора. */
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

    private Set<String> conflicted() {
        Set<String> out = new HashSet<>();
        if (minecraft != null) {
            for (KeyMapping[] pair : Controls.clashes(minecraft)) {
                if (pair[0].getKeyModifier() == modifier()) {
                    out.add(pair[0].getKey().getName());
                }
            }
        }
        return out;
    }

    private int colour(String category) {
        return colours.computeIfAbsent(category, c -> {
            int known = switch (c) {
                case "key.categories.movement" -> 0xFF4E6A8C;
                case "key.categories.gameplay" -> 0xFF5C7A4E;
                case "key.categories.inventory" -> 0xFF7A6A4E;
                case "key.categories.multiplayer" -> 0xFF6A4E7A;
                case "key.categories.misc", "key.categories.ui", "key.categories.creative" -> 0xFF5A5F6B;
                default -> 0;
            };
            if (known != 0) {
                return known;
            }
            // Остальные моды — свой оттенок по имени категории, сочный, но тёмный.
            float hue = (c.hashCode() & 0xFFFF) / 65535F;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.55F, 0.62F);
            return 0xFF000000 | rgb & 0xFFFFFF;
        });
    }

    private static String name(KeyMapping km) {
        return I18n.get(km.getName());
    }

    // --- раскладка экрана -------------------------------------------------------

    private float unit() {
        return Math.min((width - 16) / 23.0F, (height - top() - 34) / 6.6F);
    }

    private int left() {
        return (int) ((width - unit() * 23) / 2);
    }

    private int top() {
        return 42;
    }

    @Override
    protected void init() {
        int x = left();
        int y = 20;
        for (String l : LAYERS) {
            Component label = Component.translatable("citylife.controls.layer." + l);
            int w = font.width(label) + 12;
            Button b = Button.builder(label, btn -> {
                layer = l;
                rebuildWidgets();
            }).bounds(x, y, w, 16).build();
            b.active = !l.equals(layer);
            addRenderableWidget(b);
            x += w + 3;
        }
        int bx = left() + (int) (unit() * 23);
        Component reset = Component.translatable("citylife.controls.reset");
        int rw = font.width(reset) + 12;
        addRenderableWidget(Button.builder(reset, b -> {
            int n = Controls.apply(minecraft, true);
            status = Component.translatable("citylife.controls.reset_done", n).getString();
        }).bounds(bx - rw, y, rw, 16).build());
        Component edit = Component.translatable("citylife.controls.edit");
        int ew = font.width(edit) + 12;
        addRenderableWidget(Button.builder(edit, b -> minecraft.setScreen(new KeyBindsScreen(this,
                minecraft.options))).bounds(bx - rw - ew - 4, y, ew, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xC0080B10);
        g.drawString(font, title, left(), 6, 0xFF57D8FF, false);
        String hint = Component.translatable("citylife.controls.hint").getString();
        int hx = left() + font.width(title) + 12;
        int room = left() + (int) (unit() * 23) - hx;
        if (room > 40) {
            g.drawString(font, font.plainSubstrByWidth(hint, room), hx, 6, 0xFF7FA6B8, false);
        }
        super.render(g, mouseX, mouseY, partial);

        float u = unit();
        int x0 = left();
        int y0 = top();
        Set<String> bad = conflicted();
        hovered = "";
        // Основной блок.
        float y = y0;
        for (int r = 0; r < ROWS.size(); r++) {
            float x = x0;
            for (Cap cap : ROWS.get(r)) {
                x += cap.gap() * u;
                cap(g, cap.key(), cap.label(), x, y, cap.w() * u, u, bad, mouseX, mouseY);
                x += cap.w() * u;
            }
            y += u + (r == 0 ? u * 0.25F : 0);
        }
        // Блок стрелок и Home/End.
        y = y0;
        for (int r = 0; r < NAV.size(); r++) {
            float x = x0 + u * 15.25F;
            for (Cap cap : NAV.get(r)) {
                x += cap.gap() * u;
                cap(g, cap.key(), cap.label(), x, y, u, u, bad, mouseX, mouseY);
                x += u;
            }
            y += u + (r == 0 ? u * 0.25F : 0);
        }
        mouse(g, x0 + u * 18.75F, y0, u, bad, mouseX, mouseY);
        legend(g, x0, (int) (y0 + u * 6.4F), mouseX, mouseY);
        details(g, mouseX, mouseY);
        if (!status.isEmpty()) {
            g.drawString(font, status, left(), height - 12, 0xFF5CFF9D, false);
        }
    }

    private void cap(GuiGraphics g, String key, String label, float fx, float fy, float fw, float fh,
                     Set<String> bad, int mouseX, int mouseY) {
        int x = Math.round(fx) + 1;
        int y = Math.round(fy) + 1;
        int w = Math.round(fw) - 2;
        int h = Math.round(fh) - 2;
        List<KeyMapping> acts = on(key);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (hover) {
            hovered = key;
        }
        boolean held = "alt".equals(layer) && key.endsWith(".alt") || "ctrl".equals(layer) && key.endsWith(".control")
                || "shift".equals(layer) && key.endsWith(".shift");
        boolean dim = !focus.isEmpty() && acts.stream().noneMatch(a -> a.getCategory().equals(focus));
        int base = acts.isEmpty() ? 0xFF1A1E26 : colour(acts.get(0).getCategory());
        if (held) {
            base = 0xFF2F8F9F;
        }
        if (dim) {
            base = PhoneUi.lerp(base, 0xFF101318, 0.75F);
        }
        PhoneUi.roundedRect(g, x, y, w, h, 3, hover ? PhoneUi.lerp(base, 0xFFFFFFFF, 0.2F) : base);
        // Полоски снизу: по одной на каждое действие, цветом его мода.
        if (acts.size() > 1) {
            int sw = Math.max(2, (w - 4) / acts.size());
            for (int i = 0; i < acts.size(); i++) {
                g.fill(x + 2 + i * sw, y + h - 3, x + 2 + (i + 1) * sw - 1, y + h - 1,
                        colour(acts.get(i).getCategory()));
            }
        }
        boolean conflict = bad.contains(key) && !acts.isEmpty();
        PhoneUi.roundedOutline(g, x, y, w, h, 3, conflict ? (System.currentTimeMillis() / 400 % 2 == 0
                ? 0xFFFF3B3B : 0xFFAA2020) : 0x40FFFFFF);
        float scale = Math.max(0.5F, Math.min(1.0F, h / 30F));
        text(g, label, x + 2, y + 2, scale, 0xFFE8FBFF);
        String cyr = CYR.get(key);
        if (cyr != null) {
            text(g, cyr, x + w - 2 - font.width(cyr) * scale * 0.85F, y + 2, scale * 0.85F, 0x99E8FBFF);
        }
        if (!acts.isEmpty()) {
            float small = Math.max(0.5F, scale * 0.62F);
            String what = name(acts.get(0)) + (acts.size() > 1 ? " +" + (acts.size() - 1) : "");
            int lineW = (int) ((w - 4) / small);
            List<String> lines = wrap(what, lineW, 2);
            float ty = y + h - 3 - lines.size() * 9 * small - (acts.size() > 1 ? 2 : 0);
            for (String line : lines) {
                text(g, line, x + 2, ty, small, dim ? 0x66FFFFFF : 0xFFFFFFFF);
                ty += 9 * small;
            }
        }
    }

    private void mouse(GuiGraphics g, float fx, float fy, float u, Set<String> bad, int mouseX, int mouseY) {
        int x = Math.round(fx);
        int y = Math.round(fy);
        int w = Math.round(u * 4);
        int h = Math.round(u * 5.6F);
        PhoneUi.roundedRect(g, x, y, w, h, Math.round(u * 1.4F), 0xFF141820);
        PhoneUi.roundedOutline(g, x, y, w, h, Math.round(u * 1.4F), 0x40FFFFFF);
        text(g, Component.translatable("citylife.controls.mouse").getString(), x + 4, y + h + 3, 1, 0xFF7FA6B8);
        float half = u * 1.8F;
        cap(g, MOUSE[0], "ЛКМ", x + u * 0.2F, y + u * 0.2F, half, u * 2.0F, bad, mouseX, mouseY);
        cap(g, MOUSE[1], "ПКМ", x + u * 2.0F, y + u * 0.2F, half, u * 2.0F, bad, mouseX, mouseY);
        cap(g, MOUSE[2], "Колесо", x + u * 1.2F, y + u * 2.3F, u * 1.6F, u * 1.1F, bad, mouseX, mouseY);
        cap(g, MOUSE[3], "Бок. 4", x + u * 0.2F, y + u * 3.5F, u * 1.7F, u * 0.95F, bad, mouseX, mouseY);
        cap(g, MOUSE[4], "Бок. 5", x + u * 0.2F, y + u * 4.5F, u * 1.7F, u * 0.95F, bad, mouseX, mouseY);
    }

    /** Легенда: моды и их цвета; клик — подсветить только этот мод. */
    private final List<Object[]> legendHits = new ArrayList<>();

    private void legend(GuiGraphics g, int x0, int y, int mouseX, int mouseY) {
        legendHits.clear();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (KeyMapping km : minecraft.options.keyMappings) {
            if (!km.isUnbound() && km.getKeyModifier() == modifier()) {
                counts.merge(km.getCategory(), 1, Integer::sum);
            }
        }
        int x = x0;
        int maxX = x0 + (int) (unit() * 23);
        for (var e : counts.entrySet()) {
            String label = I18n.get(e.getKey()) + " " + e.getValue();
            int w = font.width(label) + 16;
            if (x + w > maxX) {
                x = x0;
                y += 13;
            }
            boolean on = e.getKey().equals(focus);
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 11;
            PhoneUi.roundedRect(g, x, y, w, 11, 4, on ? 0x60FFFFFF : hover ? 0x30FFFFFF : 0x18FFFFFF);
            g.fill(x + 3, y + 3, x + 9, y + 8, colour(e.getKey()));
            g.drawString(font, label, x + 12, y + 2, 0xFFE8FBFF, false);
            legendHits.add(new Object[]{x, y, w, e.getKey()});
            x += w + 3;
        }
    }

    /** Под курсором клавиша — справа внизу полный список её действий. */
    private void details(GuiGraphics g, int mouseX, int mouseY) {
        if (hovered.isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        InputConstants.Key key = InputConstants.getKey(hovered);
        String prefix = switch (layer) {
            case "alt" -> "Alt + ";
            case "ctrl" -> "Ctrl + ";
            case "shift" -> "Shift + ";
            default -> "";
        };
        lines.add(Component.literal(prefix + key.getDisplayName().getString())
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        List<KeyMapping> acts = on(hovered);
        if (acts.isEmpty()) {
            lines.add(Component.translatable("citylife.controls.free").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        for (KeyMapping km : acts) {
            lines.add(Component.literal("■ ").withStyle(s -> s.withColor(colour(km.getCategory()) & 0xFFFFFF))
                    .append(Component.literal(I18n.get(km.getCategory()) + ": ")
                            .withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal(name(km))));
        }
        if (acts.size() > 1) {
            boolean clash = false;
            for (int i = 0; i < acts.size(); i++) {
                for (int j = i + 1; j < acts.size(); j++) {
                    clash |= Controls.clash(acts.get(i), acts.get(j));
                }
            }
            lines.add(Component.translatable(clash ? "citylife.controls.clash" : "citylife.controls.shared")
                    .withStyle(clash ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GREEN));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Object[] hit : legendHits) {
            int x = (int) hit[0];
            int y = (int) hit[1];
            int w = (int) hit[2];
            if (mx >= x && mx < x + w && my >= y && my < y + 11) {
                String cat = (String) hit[3];
                focus = cat.equals(focus) ? "" : cat;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void text(GuiGraphics g, String s, float x, float y, float scale, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Разбить подпись на строки шириной width (в пикселях шрифта), не больше max строк. */
    private List<String> wrap(String text, int width, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) <= width) {
                line = new StringBuilder(next);
                continue;
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
            }
            line = new StringBuilder(word);
            if (out.size() == max) {
                break;
            }
        }
        if (out.size() < max && !line.isEmpty()) {
            out.add(line.toString());
        }
        for (int i = 0; i < out.size(); i++) {
            if (font.width(out.get(i)) > width) {
                out.set(i, font.plainSubstrByWidth(out.get(i), width));
            }
        }
        return out.size() > max ? out.subList(0, max) : out;
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
