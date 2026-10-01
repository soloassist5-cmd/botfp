package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Виселица: угадайте слово по буквам, шесть ошибок — и игра окончена. */
class HangmanApp extends PcApp {

    private static final String[] WORDS = {"банкомат", "небоскрёб", "навигатор", "полицейский", "маркетплейс",
            "квартира", "автосалон", "светофор", "пирс", "метро", "курьер", "телефон", "компьютер", "больница",
            "заправка", "риелтор", "магазин", "касса", "дрон", "пляж", "кофейня", "пятнашки", "лицензия",
            "грузовик", "библиотека", "фонарик", "микроавтобус", "вертолёт", "аквариум", "календарь"};
    private static final String ABC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private final Random random = new Random();
    private String word;
    private final Set<Character> tried = new HashSet<>();
    private int mistakes;

    HangmanApp(DeviceScreen screen) {
        super(screen, "hangman");
        reset();
    }

    private void reset() {
        word = WORDS[random.nextInt(WORDS.length)];
        tried.clear();
        mistakes = 0;
    }

    private boolean won() {
        for (char ch : word.toCharArray()) {
            if (!tried.contains(ch)) {
                return false;
            }
        }
        return true;
    }

    private int[] key(int[] area, int i) {
        int top = area[1] + area[3] - 66;
        return Kit.cell(area[0], top, area[2], 64, 11, 3, i, 2);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        boolean over = mistakes >= 6 || won();
        StringBuilder shown = new StringBuilder();
        for (char ch : word.toCharArray()) {
            shown.append(tried.contains(ch) || mistakes >= 6 ? ch : '_').append(' ');
        }
        big(g, shown.toString().trim().toUpperCase(), area[0] + area[2] / 2, area[1] + 6, 2F,
                won() ? Kit.GREEN : mistakes >= 6 ? Kit.RED : Kit.TEXT);
        // Виселица: столб, перекладина, верёвка и человечек по ошибкам.
        int gx = area[0] + area[2] - 70;
        int gy = area[1] + 30;
        g.fill(gx, gy, gx + 2, gy + 44, Kit.DIM);
        g.fill(gx, gy, gx + 26, gy + 2, Kit.DIM);
        g.fill(gx + 24, gy, gx + 26, gy + 8, Kit.DIM);
        if (mistakes > 0) {
            PhoneUi.disc(g, gx + 25, gy + 12, 4, Kit.TEXT);
        }
        if (mistakes > 1) {
            g.fill(gx + 24, gy + 16, gx + 26, gy + 30, Kit.TEXT);
        }
        if (mistakes > 2) {
            g.fill(gx + 18, gy + 19, gx + 24, gy + 21, Kit.TEXT);
        }
        if (mistakes > 3) {
            g.fill(gx + 26, gy + 19, gx + 32, gy + 21, Kit.TEXT);
        }
        if (mistakes > 4) {
            g.fill(gx + 20, gy + 30, gx + 24, gy + 38, Kit.TEXT);
        }
        if (mistakes > 5) {
            g.fill(gx + 26, gy + 30, gx + 30, gy + 38, Kit.TEXT);
        }
        screen.text(g, over ? (won() ? "Угадали! Клик по букве — новое слово" : "Не угадали. Клик — новое слово")
                : "Ошибок: " + mistakes + " из 6", area[0], area[1] + 34, over ? Kit.DIM : Kit.TEXT);
        for (int i = 0; i < ABC.length(); i++) {
            char ch = ABC.charAt(i);
            int[] r = key(area, i);
            boolean used = tried.contains(ch);
            int colour = !used ? Kit.TILE : word.indexOf(ch) >= 0 ? 0xFF1F8F57 : 0xFF5A2A2A;
            Kit.tile(g, screen, r, String.valueOf(ch).toUpperCase(), colour, mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (mistakes >= 6 || won()) {
            reset();
            return true;
        }
        for (int i = 0; i < ABC.length(); i++) {
            if (Kit.inside(mx, my, key(area, i))) {
                char ch = ABC.charAt(i);
                if (tried.add(ch) && word.indexOf(ch) < 0) {
                    mistakes++;
                }
                return true;
            }
        }
        return false;
    }
}
