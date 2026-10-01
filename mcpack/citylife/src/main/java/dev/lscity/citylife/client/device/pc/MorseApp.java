package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;

/** Азбука Морзе: текст ↔ точки и тире (русский и латиница), кнопка «прослушать». */
class MorseApp extends PcApp {

    private static final Map<Character, String> CODE = new HashMap<>();

    static {
        String[] ru = {"А.-", "Б-...", "В.--", "Г--.", "Д-..", "Е.", "Ж...-", "З--..", "И..", "Й.---", "К-.-",
                "Л.-..", "М--", "Н-.", "О---", "П.--.", "Р.-.", "С...", "Т-", "У..-", "Ф..-.", "Х....", "Ц-.-.",
                "Ч---.", "Ш----", "Щ--.-", "Ъ--.--", "Ы-.--", "Ь-..-", "Э..-..", "Ю..--", "Я.-.-"};
        String[] en = {"A.-", "B-...", "C-.-.", "D-..", "E.", "F..-.", "G--.", "H....", "I..", "J.---", "K-.-",
                "L.-..", "M--", "N-.", "O---", "P.--.", "Q--.-", "R.-.", "S...", "T-", "U..-", "V...-", "W.--",
                "X-..-", "Y-.--", "Z--..", "1.----", "2..---", "3...--", "4....-", "5.....", "6-....",
                "7--...", "8---..", "9----.", "0-----"};
        for (String s : ru) {
            CODE.put(s.charAt(0), s.substring(1));
        }
        for (String s : en) {
            CODE.putIfAbsent(s.charAt(0), s.substring(1));
        }
    }

    private String playing = "";
    private long playStart;
    private int lastBeep = -1;

    MorseApp(DeviceScreen screen) {
        super(screen, "morse");
    }

    @Override
    public void init(int[] area) {
        var box = screen.input("morse:text", area[0] + 4, area[1] + 4, area[2] - 8, "citylife.pc.text_hint", 80);
        if (box.getValue().isEmpty()) {
            box.setValue("SOS");
        }
    }

    static String encode(String text) {
        StringBuilder out = new StringBuilder();
        for (char ch : text.toUpperCase().replace('Ё', 'Е').toCharArray()) {
            if (ch == ' ') {
                out.append(" / ");
            } else if (CODE.containsKey(ch)) {
                out.append(CODE.get(ch)).append(' ');
            }
        }
        return out.toString().trim();
    }

    private int[] play(int[] area) {
        return new int[]{area[0], area[1] + area[3] - 20, area[2], 20};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        PhoneUi.roundedRect(g, area[0], area[1], area[2], 16, 4, 0xFF101218);
        String code = encode(screen.value("morse:text"));
        Kit.wrap(g, screen, code.replace('.', '•').replace('-', '—'), area[0], area[1] + 24, area[2],
                Kit.ACCENT, area[1] + area[3] - 26);
        Kit.tile(g, screen, play(area), playing.isEmpty() ? "Прослушать" : "Играет…", 0xFF1F6FD0, mouseX, mouseY);
        tickSound();
    }

    /** Точка — 1 доля, тире — 3, пауза между знаками — 1, между буквами — 3. */
    private void tickSound() {
        if (playing.isEmpty()) {
            return;
        }
        int unit = 90;
        long t = (System.currentTimeMillis() - playStart) / unit;
        long cursor = 0;
        int index = 0;
        for (char ch : playing.toCharArray()) {
            int len = ch == '.' ? 1 : ch == '-' ? 3 : 0;
            int gap = ch == ' ' ? 2 : ch == '/' ? 2 : 1;
            if (len > 0 && t >= cursor && t < cursor + len && lastBeep != index) {
                lastBeep = index;
                mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(),
                        len == 1 ? 1.6F : 1.2F));
            }
            cursor += len + gap;
            index++;
        }
        if (t > cursor) {
            playing = "";
            lastBeep = -1;
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, play(area)) && playing.isEmpty()) {
            playing = encode(screen.value("morse:text"));
            playStart = System.currentTimeMillis();
            return true;
        }
        return false;
    }
}
