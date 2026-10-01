package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Random;

/** Слоты на очки (не на деньги): три барабана, «777» — джекпот. */
class SlotsApp extends PcApp {

    private static final String[] SYMBOLS = {"7", "♦", "★", "♥", "♣", "☀"};
    private static final int[] PAY = {100, 30, 20, 15, 10, 5};
    private final Random random = new Random();
    private final int[] reels = {0, 1, 2};
    private long spinUntil;
    private static int points = 200;
    private String status = "Ставка — 5 очков";
    private boolean paid = true;

    SlotsApp(DeviceScreen screen) {
        super(screen, "slots");
    }

    private int[] lever(int[] area) {
        return new int[]{area[0] + area[2] / 4, area[1] + area[3] - 22, area[2] / 2, 20};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        boolean spinning = now < spinUntil;
        if (!spinning && !paid) {
            paid = true;
            if (reels[0] == reels[1] && reels[1] == reels[2]) {
                int win = PAY[reels[0]] * 5;
                points += win;
                status = (reels[0] == 0 ? "ДЖЕКПОТ! " : "Три в ряд! ") + "+" + win;
                mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
            } else if (reels[0] == reels[1] || reels[1] == reels[2] || reels[0] == reels[2]) {
                points += 8;
                status = "Пара: +8";
            } else {
                status = "Мимо";
            }
        }
        int w = (area[2] - 40) / 3;
        for (int i = 0; i < 3; i++) {
            int x = area[0] + 10 + i * (w + 10);
            PhoneUi.roundedRect(g, x, area[1] + 10, w, 60, 8, 0xFFF4F4F4);
            int symbol = spinning && now < spinUntil - (2 - i) * 250 ? random.nextInt(SYMBOLS.length) : reels[i];
            big(g, SYMBOLS[symbol], x + w / 2, area[1] + 26, 3F, symbol == 0 ? 0xFFC02B3F : 0xFF15171F);
        }
        screen.fitted(g, "Очки: " + points + " · " + status, area[0] + area[2] / 2, area[1] + 80, area[2], Kit.TEXT);
        Kit.tile(g, screen, lever(area), spinning ? "Крутится…" : "Крутить", 0xFFB0245A, mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (!Kit.inside(mx, my, lever(area)) || System.currentTimeMillis() < spinUntil) {
            return false;
        }
        if (points < 5) {
            points = 200;
            status = "Очки кончились — выдано 200";
            return true;
        }
        points -= 5;
        for (int i = 0; i < 3; i++) {
            reels[i] = random.nextInt(SYMBOLS.length);
        }
        spinUntil = System.currentTimeMillis() + 1200;
        paid = false;
        return true;
    }
}
