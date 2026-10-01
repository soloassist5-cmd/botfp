package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Устный счёт: 60 секунд, сколько примеров успеете решить. Ответ — Enter. */
class MathQuizApp extends PcApp {

    private final Random random = new Random();
    private String question = "";
    private int answer;
    private int score;
    private long endsAt;
    private static int best;
    private String feedback = "";

    MathQuizApp(DeviceScreen screen) {
        super(screen, "mathquiz");
    }

    @Override
    public void init(int[] area) {
        screen.input("math:a", area[0] + 4, area[1] + 48, area[2] / 2 - 8, "citylife.pc.enter_number", 6);
    }

    private void next() {
        int op = random.nextInt(4);
        int a = 2 + random.nextInt(48);
        int b = 2 + random.nextInt(12);
        switch (op) {
            case 0 -> {
                question = a + " + " + b;
                answer = a + b;
            }
            case 1 -> {
                question = (a + b) + " − " + b;
                answer = a;
            }
            case 2 -> {
                question = b + " × " + (a % 12 + 2);
                answer = b * (a % 12 + 2);
            }
            default -> {
                int c = a % 12 + 2;
                question = (b * c) + " ÷ " + b;
                answer = c;
            }
        }
    }

    private int[] start(int[] area) {
        return new int[]{area[0] + area[2] / 2 + 4, area[1] + 44, area[2] / 2 - 4, 18};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long left = Math.max(0, endsAt - System.currentTimeMillis());
        boolean running = left > 0;
        if (!running && endsAt > 0) {
            best = Math.max(best, score);
        }
        big(g, running ? question + " = ?" : endsAt > 0 ? "Время! Решено: " + score : "Готовы?",
                area[0] + area[2] / 2, area[1] + 8, 2.2F, Kit.TEXT);
        PhoneUi.roundedRect(g, area[0], area[1] + 44, area[2] / 2 - 4, 18, 4, 0xFF101218);
        Kit.tile(g, screen, start(area), running ? left / 1000 + " с" : "Старт", 0xFF1F8F57, mouseX, mouseY);
        screen.text(g, "Счёт: " + score + " · рекорд: " + best + "  " + feedback, area[0], area[1] + 70, Kit.DIM);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, start(area)) && System.currentTimeMillis() >= endsAt) {
            score = 0;
            feedback = "";
            endsAt = System.currentTimeMillis() + 60_000;
            next();
            return true;
        }
        return false;
    }

    @Override
    public boolean key(int code) {
        if (code != 257 || System.currentTimeMillis() >= endsAt) {
            return false;
        }
        double v = Kit.parse(screen.value("math:a"));
        if (!Double.isNaN(v) && (int) v == answer) {
            score++;
            feedback = "✔";
        } else {
            feedback = "✖ было " + answer;
        }
        screen.clear("math:a");
        next();
        return true;
    }
}
