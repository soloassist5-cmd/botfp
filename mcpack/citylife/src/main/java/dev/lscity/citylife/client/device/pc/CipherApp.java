package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

/** Шифратор: шифр Цезаря со сдвигом, ROT13 и «наоборот»; для записок другу. */
class CipherApp extends PcApp {

    private static final String RU = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String EN = "abcdefghijklmnopqrstuvwxyz";
    private int shift = 3;
    private int mode;

    CipherApp(DeviceScreen screen) {
        super(screen, "cipher");
    }

    @Override
    public void init(int[] area) {
        var box = screen.input("cipher:text", area[0] + 4, area[1] + 4, area[2] - 8, "citylife.pc.text_hint", 120);
        if (box.getValue().isEmpty()) {
            box.setValue("Встречаемся у пирса в полночь");
        }
    }

    static String caesar(String text, int shift) {
        StringBuilder out = new StringBuilder();
        for (char ch : text.toCharArray()) {
            out.append(rotate(ch, shift));
        }
        return out.toString();
    }

    private static char rotate(char ch, int shift) {
        for (String abc : new String[]{RU, EN}) {
            int i = abc.indexOf(Character.toLowerCase(ch));
            if (i >= 0) {
                char r = abc.charAt(Math.floorMod(i + shift, abc.length()));
                return Character.isUpperCase(ch) ? Character.toUpperCase(r) : r;
            }
        }
        return ch;
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + 24, area[2], 18, 4, 1, i, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        PhoneUi.roundedRect(g, area[0], area[1], area[2], 16, 4, 0xFF101218);
        String[] modes = {"Зашифровать", "Расшифровать", "ROT13", "Задом наперёд"};
        for (int i = 0; i < 4; i++) {
            Kit.tile(g, screen, button(area, i), modes[i], i == mode ? 0xFF1F6FD0 : Kit.TILE, mouseX, mouseY);
        }
        String text = screen.value("cipher:text");
        String out = switch (mode) {
            case 0 -> caesar(text, shift);
            case 1 -> caesar(text, -shift);
            case 2 -> caesar(text, 13);
            default -> new StringBuilder(text).reverse().toString();
        };
        if (mode < 2) {
            screen.text(g, "Сдвиг: " + shift + "  (клик по строке — сменить)", area[0], area[1] + 48, Kit.DIM);
        }
        PhoneUi.roundedRect(g, area[0], area[1] + 62, area[2], area[3] - 80, 6, 0xE01B1F2B);
        Kit.wrap(g, screen, out, area[0] + 6, area[1] + 68, area[2] - 12, Kit.GREEN, area[1] + area[3] - 20);
        screen.text(g, "Клик по результату — скопировать", area[0], area[1] + area[3] - 9, Kit.DIM);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int i = 0; i < 4; i++) {
            if (Kit.inside(mx, my, button(area, i))) {
                mode = i;
                return true;
            }
        }
        if (Kit.inside(mx, my, new int[]{area[0], area[1] + 46, area[2], 12})) {
            shift = shift % 32 + 1;
            return true;
        }
        if (Kit.inside(mx, my, new int[]{area[0], area[1] + 62, area[2], area[3] - 80})) {
            String text = screen.value("cipher:text");
            mc().keyboardHandler.setClipboard(switch (mode) {
                case 0 -> caesar(text, shift);
                case 1 -> caesar(text, -shift);
                case 2 -> caesar(text, 13);
                default -> new StringBuilder(text).reverse().toString();
            });
            return true;
        }
        return false;
    }
}
