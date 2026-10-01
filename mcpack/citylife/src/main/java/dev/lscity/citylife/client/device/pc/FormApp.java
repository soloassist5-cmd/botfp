package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceApp;
import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Калькулятор-форма: несколько полей, результат пересчитывается на лету.
 * Так устроены ИМТ, кредит, вклад, чаевые, проценты и прочие расчёты.
 */
@OnlyIn(Dist.CLIENT)
class FormApp extends DeviceApp {

    /** Поле формы: подпись и значение по умолчанию. */
    record Field(String label, String value) {
    }

    private final String id;
    private final List<Field> fields;
    /** По значениям полей (строки) — строки результата. */
    private final Function<String[], List<String>> compute;

    FormApp(DeviceScreen screen, String id, List<Field> fields, Function<String[], List<String>> compute) {
        super(screen);
        this.id = id;
        this.fields = fields;
        this.compute = compute;
    }

    @Override
    public String title() {
        return screen.appTitle(id);
    }

    private int labelW(int[] area) {
        return Math.min(area[2] / 2, 150);
    }

    @Override
    public void init(int[] area) {
        for (int i = 0; i < fields.size(); i++) {
            var box = screen.input(id + ":" + i, area[0] + labelW(area) + 6, area[1] + 4 + i * 18,
                    area[2] - labelW(area) - 12, "citylife.pc.enter_number", 40);
            if (box.getValue().isEmpty()) {
                box.setValue(fields.get(i).value());
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        String[] values = new String[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            int y = area[1] + i * 18;
            screen.text(g, screen.trim(fields.get(i).label(), labelW(area)), area[0], y + 4, Kit.DIM);
            PhoneUi.roundedRect(g, area[0] + labelW(area), y, area[2] - labelW(area), 16, 4, 0xFF101218);
            values[i] = screen.value(id + ":" + i);
        }
        List<String> out;
        try {
            out = compute.apply(values);
        } catch (RuntimeException bad) {
            out = List.of("Проверьте числа в полях");
        }
        int y = area[1] + fields.size() * 18 + 6;
        PhoneUi.roundedRect(g, area[0], y, area[2], Math.max(14, out.size() * 11 + 8), 6, 0xE01B1F2B);
        y += 5;
        for (String line : out) {
            boolean head = line.startsWith("=");
            screen.text(g, screen.trim(head ? line.substring(1) : line, area[2] - 10), area[0] + 6, y,
                    head ? Kit.GREEN : Kit.TEXT);
            y += 11;
        }
    }

    // --- калькуляторы ------------------------------------------------------------------

    private static double n(String s) {
        double v = Kit.parse(s);
        if (Double.isNaN(v)) {
            throw new IllegalArgumentException(s);
        }
        return v;
    }

    static FormApp bmi(DeviceScreen s) {
        return new FormApp(s, "calc_bmi", List.of(new Field("Вес, кг", "70"), new Field("Рост, см", "175")), v -> {
            double kg = n(v[0]);
            double m = n(v[1]) / 100;
            double bmi = kg / (m * m);
            String kind = bmi < 18.5 ? "недостаток веса" : bmi < 25 ? "норма" : bmi < 30 ? "избыток веса"
                    : "ожирение";
            double lo = 18.5 * m * m;
            double hi = 24.9 * m * m;
            return List.of("=ИМТ: " + Kit.num(Math.round(bmi * 10) / 10.0) + " — " + kind,
                    "Нормальный вес при таком росте: " + Kit.num(Math.round(lo)) + "–" + Kit.num(Math.round(hi)) + " кг");
        });
    }

    static FormApp loan(DeviceScreen s) {
        return new FormApp(s, "calc_loan", List.of(new Field("Сумма, ₽", "500000"),
                new Field("Ставка, % годовых", "18"), new Field("Срок, месяцев", "24")), v -> {
            double sum = n(v[0]);
            double r = n(v[1]) / 100 / 12;
            int months = (int) n(v[2]);
            double pay = r == 0 ? sum / months : sum * r / (1 - Math.pow(1 + r, -months));
            return List.of("=Платёж в месяц: " + Kit.rub(pay), "Всего отдадите: " + Kit.rub(pay * months),
                    "Переплата: " + Kit.rub(pay * months - sum));
        });
    }

    static FormApp deposit(DeviceScreen s) {
        return new FormApp(s, "calc_deposit", List.of(new Field("Вклад, ₽", "100000"),
                new Field("Ставка, % годовых", "12"), new Field("Срок, месяцев", "12"),
                new Field("Пополнение в месяц, ₽", "0")), v -> {
            double total = n(v[0]);
            double r = n(v[1]) / 100 / 12;
            int months = (int) n(v[2]);
            double add = n(v[3]);
            double put = total;
            for (int i = 0; i < months; i++) {
                total = total * (1 + r) + add;
                put += add;
            }
            return List.of("=Через " + months + " мес.: " + Kit.rub(total), "Вложено: " + Kit.rub(put),
                    "Доход по процентам: " + Kit.rub(total - put));
        });
    }

    static FormApp tip(DeviceScreen s) {
        return new FormApp(s, "calc_tip", List.of(new Field("Счёт, ₽", "2400"), new Field("Чаевые, %", "10"),
                new Field("Сколько человек", "3")), v -> {
            double bill = n(v[0]);
            double tip = bill * n(v[1]) / 100;
            int people = Math.max(1, (int) n(v[2]));
            return List.of("=С каждого: " + Kit.rub((bill + tip) / people), "Чаевые: " + Kit.rub(tip),
                    "Всего: " + Kit.rub(bill + tip));
        });
    }

    static FormApp percent(DeviceScreen s) {
        return new FormApp(s, "calc_percent", List.of(new Field("Число", "1500"), new Field("Процент", "15"),
                new Field("Второе число (сколько % от первого)", "300")), v -> {
            double a = n(v[0]);
            double p = n(v[1]);
            double b = n(v[2]);
            return List.of("=" + Kit.num(p) + "% от " + Kit.num(a) + " = " + Kit.num(a * p / 100),
                    Kit.num(a) + " + " + Kit.num(p) + "% = " + Kit.num(a * (1 + p / 100)),
                    Kit.num(a) + " − " + Kit.num(p) + "% = " + Kit.num(a * (1 - p / 100)),
                    Kit.num(b) + " — это " + Kit.num(Math.round(b / a * 10000) / 100.0) + "% от " + Kit.num(a));
        });
    }

    static FormApp discount(DeviceScreen s) {
        return new FormApp(s, "calc_discount", List.of(new Field("Цена, ₽", "9000"), new Field("Скидка, %", "25"),
                new Field("Купон, ₽", "0")), v -> {
            double price = n(v[0]);
            double after = Math.max(0, price * (1 - n(v[1]) / 100) - n(v[2]));
            return List.of("=Цена со скидкой: " + Kit.rub(after), "Экономия: " + Kit.rub(price - after));
        });
    }

    static FormApp vat(DeviceScreen s) {
        return new FormApp(s, "calc_vat", List.of(new Field("Сумма, ₽", "10000"), new Field("НДС, %", "20")), v -> {
            double sum = n(v[0]);
            double rate = n(v[1]) / 100;
            return List.of("=Начислить НДС: " + Kit.rub(sum * (1 + rate)) + " (НДС " + Kit.rub(sum * rate) + ")",
                    "Выделить НДС из суммы: " + Kit.rub(sum * rate / (1 + rate)),
                    "Сумма без НДС: " + Kit.rub(sum / (1 + rate)));
        });
    }

    static FormApp fuel(DeviceScreen s) {
        return new FormApp(s, "calc_fuel", List.of(new Field("Расстояние, км", "300"),
                new Field("Расход, л/100 км", "8"), new Field("Цена литра, ₽", "55")), v -> {
            double litres = n(v[0]) * n(v[1]) / 100;
            return List.of("=Бензин: " + Kit.num(Math.round(litres * 10) / 10.0) + " л",
                    "Стоимость поездки: " + Kit.rub(litres * n(v[2])),
                    "Туда и обратно: " + Kit.rub(2 * litres * n(v[2])));
        });
    }

    static FormApp average(DeviceScreen s) {
        return new FormApp(s, "calc_average", List.of(new Field("Числа через пробел", "4 8 15 16 23 42")), v -> {
            List<Double> list = new ArrayList<>();
            for (String part : v[0].trim().split("[\\s;]+")) {
                if (!part.isEmpty()) {
                    list.add(n(part));
                }
            }
            if (list.isEmpty()) {
                return List.of("Введите числа");
            }
            list.sort(Double::compare);
            double sum = list.stream().mapToDouble(Double::doubleValue).sum();
            int k = list.size();
            double median = k % 2 == 1 ? list.get(k / 2) : (list.get(k / 2 - 1) + list.get(k / 2)) / 2;
            return List.of("=Среднее: " + Kit.num(sum / k), "Медиана: " + Kit.num(median),
                    "Сумма: " + Kit.num(sum) + ", чисел: " + k,
                    "Мин: " + Kit.num(list.get(0)) + ", макс: " + Kit.num(list.get(k - 1)));
        });
    }

    static FormApp proportion(DeviceScreen s) {
        return new FormApp(s, "calc_proportion", List.of(new Field("A", "3"), new Field("B", "4"),
                new Field("C", "9")), v -> {
            double x = n(v[1]) * n(v[2]) / n(v[0]);
            return List.of("A : B = C : X", "=X = " + Kit.num(x));
        });
    }

    static FormApp base(DeviceScreen s) {
        return new FormApp(s, "calc_base", List.of(new Field("Число (можно 0x… или 0b…)", "255")), v -> {
            String text = v[0].trim().toLowerCase();
            long value = text.startsWith("0x") ? Long.parseLong(text.substring(2), 16)
                    : text.startsWith("0b") ? Long.parseLong(text.substring(2), 2) : Long.parseLong(text);
            return List.of("=Десятичная: " + value, "Двоичная: " + Long.toBinaryString(value),
                    "Восьмеричная: " + Long.toOctalString(value),
                    "Шестнадцатеричная: " + Long.toHexString(value).toUpperCase());
        });
    }

    static FormApp roman(DeviceScreen s) {
        return new FormApp(s, "calc_roman", List.of(new Field("Число или римская запись", "2026")), v -> {
            String text = v[0].trim().toUpperCase();
            if (text.matches("[IVXLCDM]+")) {
                return List.of("=" + text + " = " + fromRoman(text));
            }
            int value = (int) n(text);
            if (value < 1 || value > 3999) {
                return List.of("Римские числа — от 1 до 3999");
            }
            return List.of("=" + value + " = " + toRoman(value));
        });
    }

    private static final int[] RV = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] RS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    static String toRoman(int value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < RV.length; i++) {
            while (value >= RV[i]) {
                out.append(RS[i]);
                value -= RV[i];
            }
        }
        return out.toString();
    }

    static int fromRoman(String text) {
        int total = 0;
        int i = 0;
        for (int k = 0; k < RV.length; k++) {
            while (text.startsWith(RS[k], i)) {
                total += RV[k];
                i += RS[k].length();
            }
        }
        return total;
    }
}
