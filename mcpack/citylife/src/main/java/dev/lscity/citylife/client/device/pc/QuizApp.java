package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Викторина: 10 вопросов о городе и обо всём; четыре варианта ответа. */
class QuizApp extends PcApp {

    private record Question(String text, String[] options, int right) {
    }

    private static final List<Question> ALL = List.of(
            new Question("Сколько звёзд даёт ограбление кассы?", new String[]{"1", "2", "3", "5"}, 1),
            new Question("Чем платят в кассе «картой»?", new String[]{"Наличными", "Со счёта в банке", "Фишками", "Ничем"}, 1),
            new Question("Какая клавиша открывает «Мой транспорт»?", new String[]{"K", "E", "Q", "Tab"}, 0),
            new Question("Сколько длится игровой день?", new String[]{"10 минут", "20 минут", "1 час", "24 часа"}, 1),
            new Question("Какой процент цены приносит бизнес в сутки?", new String[]{"0,3%", "1,5%", "5%", "10%"}, 1),
            new Question("Сколько стоит лечение в больнице?", new String[]{"Бесплатно", "200 ₽", "1 000 ₽", "5 000 ₽"}, 1),
            new Question("Самый высокий небоскрёб города?", new String[]{"MERIDIAN", "SUNSET PLAZA", "STARK TOWER", "Мэрия"}, 2),
            new Question("Столица Австралии?", new String[]{"Сидней", "Мельбурн", "Канберра", "Перт"}, 2),
            new Question("Сколько планет в Солнечной системе?", new String[]{"7", "8", "9", "10"}, 1),
            new Question("Химический символ золота?", new String[]{"Ag", "Au", "Gd", "Go"}, 1),
            new Question("Кто написал «Войну и мир»?", new String[]{"Достоевский", "Чехов", "Толстой", "Пушкин"}, 2),
            new Question("Сколько байт в килобайте (двоичном)?", new String[]{"1000", "1024", "512", "2048"}, 1),
            new Question("Самая длинная река мира?", new String[]{"Амазонка", "Нил", "Янцзы", "Волга"}, 0),
            new Question("Сколько сторон у шестиугольника?", new String[]{"5", "6", "7", "8"}, 1),
            new Question("Что падает, если под ним пусто?", new String[]{"Камень", "Доски", "Гравий", "Стекло"}, 2),
            new Question("Как долго сходит одна звезда розыска?", new String[]{"1 минута", "5 минут", "1 час", "Никогда"}, 1));

    private final List<Question> round = new ArrayList<>();
    private int index;
    private int score;
    private int chosen = -1;

    QuizApp(DeviceScreen screen) {
        super(screen, "quiz");
        reset();
    }

    private void reset() {
        round.clear();
        round.addAll(ALL);
        Collections.shuffle(round);
        round.subList(10, round.size()).clear();
        index = 0;
        score = 0;
        chosen = -1;
    }

    private int[] option(int[] area, int i) {
        return Kit.cell(area[0], area[1] + 40, area[2], area[3] - 64, 2, 2, i, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (index >= round.size()) {
            big(g, "Правильно: " + score + " из " + round.size(), area[0] + area[2] / 2, area[1] + 20, 2F, Kit.GREEN);
            screen.fitted(g, "Клик — сыграть ещё", area[0] + area[2] / 2, area[1] + 50, area[2], Kit.DIM);
            return;
        }
        Question q = round.get(index);
        screen.text(g, "Вопрос " + (index + 1) + " из " + round.size() + " · счёт " + score, area[0], area[1], Kit.DIM);
        Kit.wrap(g, screen, q.text(), area[0], area[1] + 14, area[2], Kit.TEXT, area[1] + 38);
        for (int i = 0; i < 4; i++) {
            int colour = Kit.TILE;
            if (chosen >= 0) {
                colour = i == q.right() ? 0xFF1F8F57 : i == chosen ? 0xFFB03434 : Kit.TILE;
            }
            Kit.tile(g, screen, option(area, i), q.options()[i], colour, mouseX, mouseY);
        }
        if (chosen >= 0) {
            screen.fitted(g, "Клик — следующий вопрос", area[0] + area[2] / 2, area[1] + area[3] - 12, area[2], Kit.DIM);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (index >= round.size()) {
            reset();
            return true;
        }
        if (chosen >= 0) {
            index++;
            chosen = -1;
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (Kit.inside(mx, my, option(area, i))) {
                chosen = i;
                if (i == round.get(index).right()) {
                    score++;
                }
                return true;
            }
        }
        return false;
    }
}
