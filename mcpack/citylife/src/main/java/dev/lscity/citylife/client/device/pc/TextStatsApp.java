package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Счётчик слов: символы, слова, гласные, самое длинное слово, время чтения. */
class TextStatsApp extends PcApp {

    TextStatsApp(DeviceScreen screen) {
        super(screen, "textstats");
    }

    @Override
    public void init(int[] area) {
        screen.input("stats:text", area[0] + 4, area[1] + 4, area[2] - 8, "citylife.pc.text_hint", 256);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        PhoneUi.roundedRect(g, area[0], area[1], area[2], 16, 4, 0xFF101218);
        String text = screen.value("stats:text");
        String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
        int letters = 0;
        int vowels = 0;
        for (char ch : text.toLowerCase().toCharArray()) {
            if (Character.isLetter(ch)) {
                letters++;
                if ("аеёиоуыэюяaeiouy".indexOf(ch) >= 0) {
                    vowels++;
                }
            }
        }
        String longest = "";
        for (String w : words) {
            String clean = w.replaceAll("[^\\p{L}\\p{N}-]", "");
            if (clean.length() > longest.length()) {
                longest = clean;
            }
        }
        String[] lines = {
                "Символов: " + text.length() + " (без пробелов " + text.replace(" ", "").length() + ")",
                "Слов: " + words.length,
                "Букв: " + letters + ", из них гласных " + vowels,
                "Самое длинное слово: " + (longest.isEmpty() ? "—" : longest + " (" + longest.length() + ")"),
                "Чтение вслух: ~" + Math.max(0, Math.round(words.length / 2.5)) + " с",
        };
        int y = area[1] + 26;
        for (String line : lines) {
            screen.text(g, screen.trim(line, area[2]), area[0], y, Kit.TEXT);
            y += 13;
        }
    }
}
