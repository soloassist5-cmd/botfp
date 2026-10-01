package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Слепая печать: перепечатайте фразу; скорость в знаках в минуту и точность. */
class TypingApp extends PcApp {

    private static final String[] PHRASES = {"Курьер принёс посылку к двери вовремя",
            "В запертую машину не сядет даже хозяин", "Банкомат стоит у входа в метро",
            "Полиция приезжает через тридцать секунд", "Над пирсом горят фонари и кричат чайки",
            "Приложите карту к терминалу и дождитесь сигнала", "Небоскрёбы центра видно с любого холма",
            "Съешь же ещё этих мягких французских булок да выпей чаю"};
    private final Random random = new Random();
    private String phrase;
    private long start;
    private String result = "";
    private String last = "";

    TypingApp(DeviceScreen screen) {
        super(screen, "typing");
        phrase = PHRASES[random.nextInt(PHRASES.length)];
    }

    @Override
    public void init(int[] area) {
        screen.input("typing:text", area[0] + 4, area[1] + 44, area[2] - 8, "citylife.pc.typing_hint", 120);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        String typed = screen.value("typing:text");
        if (!typed.isEmpty() && last.isEmpty()) {
            start = System.currentTimeMillis();
        }
        last = typed;
        // Фраза: верно набранное — зелёным, ошибка — красным.
        int x = area[0];
        for (int i = 0; i < phrase.length(); i++) {
            String ch = String.valueOf(phrase.charAt(i));
            int colour = i < typed.length() ? (typed.charAt(i) == phrase.charAt(i) ? Kit.GREEN : Kit.RED) : Kit.TEXT;
            if (x + screen.font().width(ch) > area[0] + area[2]) {
                break;
            }
            g.drawString(screen.font(), ch, x, area[1] + 6, colour, false);
            x += screen.font().width(ch);
        }
        PhoneUi.roundedRect(g, area[0], area[1] + 40, area[2], 16, 4, 0xFF101218);
        if (typed.length() >= phrase.length() && result.isEmpty() && start > 0) {
            double minutes = Math.max(1, System.currentTimeMillis() - start) / 60000.0;
            int right = 0;
            for (int i = 0; i < phrase.length(); i++) {
                right += typed.charAt(i) == phrase.charAt(i) ? 1 : 0;
            }
            result = Math.round(phrase.length() / minutes) + " зн/мин · точность " + right * 100 / phrase.length() + "%";
        }
        screen.text(g, result.isEmpty() ? "Начните печатать — время пойдёт само" : result + " · Enter — новая фраза",
                area[0], area[1] + 64, result.isEmpty() ? Kit.DIM : Kit.ACCENT);
    }

    @Override
    public boolean key(int code) {
        if (code == 257 && !result.isEmpty()) {
            phrase = PHRASES[random.nextInt(PHRASES.length)];
            result = "";
            start = 0;
            last = "";
            screen.clear("typing:text");
            return true;
        }
        return false;
    }
}
