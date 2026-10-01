package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceApp;
import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Конвертер величин: вводишь число, выбираешь единицу — справа сразу все
 * остальные. Клик по строке делает её исходной единицей.
 */
@OnlyIn(Dist.CLIENT)
class ConverterApp extends DeviceApp {

    /** Единица: название и перевод в базовую единицу и обратно. */
    record Unit(String name, DoubleUnaryOperator toBase, DoubleUnaryOperator fromBase) {
        static Unit linear(String name, double factor) {
            return new Unit(name, v -> v * factor, v -> v / factor);
        }
    }

    private final String id;
    private final String note;
    private final List<Unit> units;
    private int from;

    ConverterApp(DeviceScreen screen, String id, String note, List<Unit> units) {
        super(screen);
        this.id = id;
        this.note = note;
        this.units = units;
    }

    @Override
    public String title() {
        return screen.appTitle(id);
    }

    @Override
    public void init(int[] area) {
        var box = screen.input(id + ":v", area[0] + 4, area[1] + 4, area[2] / 2 - 8,
                "citylife.pc.enter_number", 24);
        if (box.getValue().isEmpty()) {
            box.setValue("1");
        }
    }

    private int[] row(int[] area, int i) {
        int cols = units.size() > 5 ? 2 : 1;
        int rowsPerCol = (units.size() + cols - 1) / cols;
        int w = (area[2] - 6 * (cols - 1)) / cols;
        int h = Math.min(18, (area[3] - 40) / Math.max(1, rowsPerCol));
        return new int[]{area[0] + (i / rowsPerCol) * (w + 6), area[1] + 28 + (i % rowsPerCol) * h, w, h - 2};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        PhoneUi.roundedRect(g, area[0], area[1], area[2] / 2, 16, 5, 0xFF101218);
        screen.text(g, units.get(from).name(), area[0] + area[2] / 2 + 6, area[1] + 4, Kit.ACCENT);
        double value = Kit.parse(screen.value(id + ":v"));
        double base = Double.isNaN(value) ? Double.NaN : units.get(from).toBase().applyAsDouble(value);
        for (int i = 0; i < units.size(); i++) {
            int[] r = row(area, i);
            boolean sel = i == from;
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 4, sel ? 0xFF1F4F8F
                    : Kit.inside(mouseX, mouseY, r) ? 0xFF262B3B : 0xFF1B1F2B);
            String shown = Double.isNaN(base) ? "—" : Kit.num(units.get(i).fromBase().applyAsDouble(base));
            screen.text(g, units.get(i).name(), r[0] + 5, r[1] + (r[3] - 8) / 2, Kit.DIM);
            String trimmed = screen.trim(shown, r[2] - 60);
            screen.text(g, trimmed, r[0] + r[2] - 5 - screen.font().width(trimmed),
                    r[1] + (r[3] - 8) / 2, Kit.TEXT);
        }
        if (!note.isEmpty()) {
            screen.text(g, screen.trim(note, area[2]), area[0], area[1] + area[3] - 9, Kit.DIM);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int i = 0; i < units.size(); i++) {
            if (Kit.inside(mx, my, row(area, i))) {
                from = i;
                return true;
            }
        }
        return false;
    }

    // --- величины --------------------------------------------------------------------

    static List<Unit> length() {
        return List.of(Unit.linear("мм", 0.001), Unit.linear("см", 0.01), Unit.linear("м", 1),
                Unit.linear("км", 1000), Unit.linear("дюйм", 0.0254), Unit.linear("фут", 0.3048),
                Unit.linear("ярд", 0.9144), Unit.linear("миля", 1609.344),
                Unit.linear("морская миля", 1852), Unit.linear("блок", 1));
    }

    static List<Unit> mass() {
        return List.of(Unit.linear("мг", 1e-6), Unit.linear("г", 0.001), Unit.linear("кг", 1),
                Unit.linear("т", 1000), Unit.linear("унция", 0.0283495), Unit.linear("фунт", 0.453592),
                Unit.linear("стоун", 6.35029), Unit.linear("карат", 0.0002));
    }

    static List<Unit> temperature() {
        return List.of(new Unit("°C", v -> v, v -> v),
                new Unit("°F", v -> (v - 32) * 5 / 9, v -> v * 9 / 5 + 32),
                new Unit("K", v -> v - 273.15, v -> v + 273.15),
                new Unit("°Ré (Реомюр)", v -> v * 1.25, v -> v * 0.8));
    }

    static List<Unit> speed() {
        return List.of(Unit.linear("м/с", 1), Unit.linear("км/ч", 1 / 3.6), Unit.linear("миль/ч", 0.44704),
                Unit.linear("узел", 0.514444), Unit.linear("фут/с", 0.3048), Unit.linear("блок/тик", 20),
                Unit.linear("Мах", 343));
    }

    static List<Unit> area() {
        return List.of(Unit.linear("мм²", 1e-6), Unit.linear("см²", 1e-4), Unit.linear("м²", 1),
                Unit.linear("сотка", 100), Unit.linear("гектар", 1e4), Unit.linear("км²", 1e6),
                Unit.linear("акр", 4046.86), Unit.linear("фут²", 0.092903), Unit.linear("чанк", 256));
    }

    static List<Unit> volume() {
        return List.of(Unit.linear("мл", 0.001), Unit.linear("л", 1), Unit.linear("м³", 1000),
                Unit.linear("стакан", 0.25), Unit.linear("ч. ложка", 0.005), Unit.linear("ст. ложка", 0.015),
                Unit.linear("галлон США", 3.78541), Unit.linear("пинта", 0.473176),
                Unit.linear("баррель", 158.987), Unit.linear("ведро", 1000));
    }

    static List<Unit> time() {
        return List.of(Unit.linear("мс", 0.001), Unit.linear("с", 1), Unit.linear("мин", 60),
                Unit.linear("ч", 3600), Unit.linear("сутки", 86400), Unit.linear("неделя", 604800),
                Unit.linear("год", 31557600), Unit.linear("игровой тик", 0.05),
                Unit.linear("игровые сутки", 1200));
    }

    static List<Unit> data() {
        return List.of(Unit.linear("бит", 0.125), Unit.linear("байт", 1), Unit.linear("КБ", 1024),
                Unit.linear("МБ", 1048576), Unit.linear("ГБ", 1073741824D),
                Unit.linear("ТБ", 1099511627776D));
    }

    static List<Unit> pressure() {
        return List.of(Unit.linear("Па", 1), Unit.linear("кПа", 1000), Unit.linear("бар", 1e5),
                Unit.linear("атм", 101325), Unit.linear("мм рт. ст.", 133.322), Unit.linear("psi", 6894.76));
    }

    static List<Unit> energy() {
        return List.of(Unit.linear("Дж", 1), Unit.linear("кДж", 1000), Unit.linear("кал", 4.184),
                Unit.linear("ккал", 4184), Unit.linear("Вт·ч", 3600), Unit.linear("кВт·ч", 3.6e6),
                Unit.linear("эВ", 1.602e-19));
    }

    static List<Unit> currency() {
        return List.of(Unit.linear("₽ рубль", 1), Unit.linear("$ доллар", 92), Unit.linear("€ евро", 100),
                Unit.linear("£ фунт", 117), Unit.linear("¥ юань", 12.8), Unit.linear("₸ тенге", 0.19),
                Unit.linear("֏ драм", 0.24));
    }

    static List<Unit> angle() {
        return List.of(Unit.linear("градус", 1), Unit.linear("радиан", 57.2957795),
                Unit.linear("оборот", 360), Unit.linear("град (гон)", 0.9),
                Unit.linear("угл. минута", 1 / 60.0), Unit.linear("угл. секунда", 1 / 3600.0));
    }
}
