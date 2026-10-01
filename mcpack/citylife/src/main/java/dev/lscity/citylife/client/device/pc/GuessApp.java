package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Угадай число от 1 до 100: подсказки «больше / меньше», рекорд по попыткам. */
class GuessApp extends PcApp {

    private final Random random = new Random();
    private int secret;
    private int tries;
    private static int best;
    private String hint = "Я загадал число от 1 до 100";
    private boolean won;

    GuessApp(DeviceScreen screen) {
        super(screen, "guess");
        secret = 1 + random.nextInt(100);
    }

    @Override
    public void init(int[] area) {
        screen.input("guess:n", area[0] + 4, area[1] + 34, area[2] / 2 - 8, "citylife.pc.enter_number", 3);
    }

    private int[] check(int[] area) {
        return new int[]{area[0] + area[2] / 2 + 4, area[1] + 30, area[2] / 2 - 4, 18};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        screen.fitted(g, hint, area[0] + area[2] / 2, area[1] + 6, area[2], won ? Kit.GREEN : Kit.TEXT);
        PhoneUi.roundedRect(g, area[0], area[1] + 30, area[2] / 2 - 4, 18, 4, 0xFF101218);
        Kit.tile(g, screen, check(area), won ? "Ещё раз" : "Проверить", 0xFF1F8F57, mouseX, mouseY);
        screen.text(g, "Попыток: " + tries + (best > 0 ? " · рекорд: " + best : ""), area[0], area[1] + 58, Kit.DIM);
    }

    private void guess() {
        if (won) {
            secret = 1 + random.nextInt(100);
            tries = 0;
            won = false;
            hint = "Новое число загадано";
            return;
        }
        double v = Kit.parse(screen.value("guess:n"));
        if (Double.isNaN(v)) {
            hint = "Введите число";
            return;
        }
        int n = (int) v;
        tries++;
        if (n == secret) {
            won = true;
            hint = "Угадали за " + tries + "!";
            if (best == 0 || tries < best) {
                best = tries;
            }
        } else {
            hint = n < secret ? n + " — моё число больше" : n + " — моё число меньше";
        }
        screen.clear("guess:n");
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, check(area))) {
            guess();
            return true;
        }
        return false;
    }

    @Override
    public boolean key(int code) {
        if (code == 257) {
            guess();
            return true;
        }
        return false;
    }
}
