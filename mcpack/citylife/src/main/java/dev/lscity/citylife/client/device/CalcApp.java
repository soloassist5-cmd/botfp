package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Калькулятор: четыре действия, работает без сети и без SIM. */
class CalcApp extends DeviceApp {

    private static final String[] KEYS = {"7", "8", "9", "÷", "4", "5", "6", "×",
            "1", "2", "3", "−", "C", "0", "=", "+"};

    private String display = "0";
    private double stored;
    private char pending;
    private boolean fresh = true;

    CalcApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("calc");
    }

    private int[] key(int[] area, int index) {
        int top = area[1] + 32;
        int w = (area[2] - 12) / 4;
        int h = Math.min(26, (area[1] + area[3] - top - 12) / 4);
        return new int[]{area[0] + (index % 4) * (w + 4), top + (index / 4) * (h + 4), w, h};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        PhoneUi.roundedRect(g, area[0], area[1], area[2], 26, 6, 0xFF101218);
        String shown = screen.trim(display, area[2] - 12);
        screen.text(g, shown, area[0] + area[2] - 6 - screen.font().width(shown), area[1] + 9,
                0xFF7BE07B);
        for (int i = 0; i < KEYS.length; i++) {
            int[] r = key(area, i);
            boolean op = "÷×−+=".contains(KEYS[i]);
            int colour = op ? 0xFFD06A1E : KEYS[i].equals("C") ? 0xFFB03434 : 0xFF2A2F3F;
            if (screen.inside(mouseX, mouseY, r)) {
                colour = PhoneUi.lerp(colour, 0xFFFFFFFF, 0.15F);
            }
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6, colour);
            screen.text(g, KEYS[i], r[0] + (r[2] - screen.font().width(KEYS[i])) / 2,
                    r[1] + (r[3] - 8) / 2, t.text() | 0xFF000000);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int i = 0; i < KEYS.length; i++) {
            if (screen.inside(mx, my, key(area, i))) {
                press(KEYS[i]);
                return true;
            }
        }
        return false;
    }

    private void press(String key) {
        switch (key) {
            case "C" -> {
                display = "0";
                stored = 0;
                pending = 0;
                fresh = true;
            }
            case "=" -> {
                compute();
                pending = 0;
            }
            case "÷", "×", "−", "+" -> {
                compute();
                pending = key.charAt(0);
            }
            default -> {
                if (fresh || display.equals("0")) {
                    display = key;
                } else if (display.length() < 12) {
                    display += key;
                }
                fresh = false;
            }
        }
    }

    private void compute() {
        double value = parse(display);
        if (pending != 0) {
            value = switch (pending) {
                case '÷' -> value == 0 ? Double.NaN : stored / value;
                case '×' -> stored * value;
                case '−' -> stored - value;
                default -> stored + value;
            };
        }
        stored = value;
        display = format(value);
        fresh = true;
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "Ошибка";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e12) {
            return String.valueOf((long) value);
        }
        return String.format("%.6f", value).replaceAll("0+$", "").replaceAll("[.,]$", "");
    }
}
