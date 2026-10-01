package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Камень, ножницы, бумага против компьютера; счёт серии. */
class RpsApp extends PcApp {

    private static final String[] NAMES = {"Камень", "Ножницы", "Бумага"};
    private final Random random = new Random();
    private int mine = -1;
    private int theirs = -1;
    private int score;
    private int theirScore;

    RpsApp(DeviceScreen screen) {
        super(screen, "rps");
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + area[3] - 26, area[2], 24, 3, 1, i, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        big(g, score + " : " + theirScore, area[0] + area[2] / 2, area[1] + 4, 2F, Kit.TEXT);
        if (mine >= 0) {
            screen.fitted(g, "Вы: " + NAMES[mine] + "   ·   Компьютер: " + NAMES[theirs], area[0] + area[2] / 2,
                    area[1] + 34, area[2], Kit.DIM);
            int result = (mine - theirs + 3) % 3;
            String text = result == 0 ? "Ничья" : result == 2 ? "Победа!" : "Поражение";
            big(g, text, area[0] + area[2] / 2, area[1] + 52, 2F, result == 2 ? Kit.GREEN : result == 1 ? Kit.RED : Kit.DIM);
        } else {
            screen.fitted(g, "Выберите жест", area[0] + area[2] / 2, area[1] + 40, area[2], Kit.DIM);
        }
        for (int i = 0; i < 3; i++) {
            Kit.tile(g, screen, button(area, i), NAMES[i], 0xFF1F6FD0, mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int i = 0; i < 3; i++) {
            if (Kit.inside(mx, my, button(area, i))) {
                mine = i;
                theirs = random.nextInt(3);
                int result = (mine - theirs + 3) % 3;
                if (result == 2) {
                    score++;
                } else if (result == 1) {
                    theirScore++;
                }
                return true;
            }
        }
        return false;
    }
}
