package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Реакция: дождитесь зелёного и кликните как можно быстрее. Рано — фальстарт. */
class ReactionApp extends PcApp {

    private enum State { IDLE, WAIT, GO, RESULT, EARLY }

    private State state = State.IDLE;
    private long goAt;
    private long result;
    private static long best;

    ReactionApp(DeviceScreen screen) {
        super(screen, "reaction");
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (state == State.WAIT && System.currentTimeMillis() >= goAt) {
            state = State.GO;
        }
        int colour = switch (state) {
            case WAIT -> 0xFFB03434;
            case GO -> 0xFF1F8F57;
            case EARLY -> 0xFF8A6A1A;
            default -> 0xFF1F6FD0;
        };
        PhoneUi.roundedRect(g, area[0], area[1], area[2], area[3], 10, colour);
        String text = switch (state) {
            case IDLE -> "Клик — начать";
            case WAIT -> "Ждите зелёного…";
            case GO -> "ЖМИ!";
            case EARLY -> "Фальстарт! Клик — ещё раз";
            case RESULT -> result + " мс";
        };
        big(g, text, area[0] + area[2] / 2, area[1] + area[3] / 2 - 12, 2.2F, 0xFFFFFFFF);
        if (state == State.RESULT) {
            String verdict = result < 200 ? "Молниеносно!" : result < 260 ? "Отлично" : result < 350 ? "Хорошо"
                    : "Можно быстрее";
            screen.fitted(g, verdict + (best > 0 ? " · рекорд " + best + " мс" : "") + " · клик — ещё раз",
                    area[0] + area[2] / 2, area[1] + area[3] / 2 + 14, area[2] - 10, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        long now = System.currentTimeMillis();
        switch (state) {
            case IDLE, RESULT, EARLY -> {
                state = State.WAIT;
                goAt = now + 1200 + (long) (Math.random() * 2500);
            }
            case WAIT -> state = now >= goAt ? State.GO : State.EARLY;
            case GO -> {
                result = now - goAt;
                if (best == 0 || result < best) {
                    best = result;
                }
                state = State.RESULT;
            }
        }
        return true;
    }
}
