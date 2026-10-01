package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.security.SecureRandom;

/** Генератор паролей: длина, цифры, символы; оценка стойкости; клик — в буфер обмена. */
class PasswordApp extends PcApp {

    private static final SecureRandom RANDOM = new SecureRandom();
    private int length = 14;
    private boolean digits = true;
    private boolean symbols = true;
    private String password = "";
    private long copiedAt;

    PasswordApp(DeviceScreen screen) {
        super(screen, "password");
        generate();
    }

    private void generate() {
        String pool = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ" + (digits ? "23456789" : "")
                + (symbols ? "!@#$%&*?-_+=" : "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            out.append(pool.charAt(RANDOM.nextInt(pool.length())));
        }
        password = out.toString();
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + 44, area[2], 20, 4, 1, i, 6);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int[] box = {area[0], area[1], area[2], 30};
        PhoneUi.roundedRect(g, box[0], box[1], box[2], box[3], 6, 0xFF101218);
        screen.fitted(g, password, area[0] + area[2] / 2, area[1] + 11, area[2] - 10, Kit.GREEN);
        Kit.tile(g, screen, button(area, 0), "Длина " + length, Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 1), (digits ? "✔ " : "") + "Цифры", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 2), (symbols ? "✔ " : "") + "Символы", Kit.TILE, mouseX, mouseY);
        Kit.tile(g, screen, button(area, 3), "Новый", 0xFF1F6FD0, mouseX, mouseY);
        double bits = length * Math.log(50 + (digits ? 8 : 0) + (symbols ? 12 : 0)) / Math.log(2);
        String strength = bits < 50 ? "слабый" : bits < 75 ? "хороший" : "очень стойкий";
        screen.text(g, "Стойкость: " + strength + " (" + (int) bits + " бит)", area[0], area[1] + 74,
                bits < 50 ? Kit.RED : Kit.GREEN);
        screen.text(g, System.currentTimeMillis() - copiedAt < 2000 ? "Скопировано в буфер обмена"
                : "Клик по паролю — скопировать", area[0], area[1] + 88, Kit.DIM);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (Kit.inside(mx, my, new int[]{area[0], area[1], area[2], 30})) {
            mc().keyboardHandler.setClipboard(password);
            copiedAt = System.currentTimeMillis();
            return true;
        }
        if (Kit.inside(mx, my, button(area, 0))) {
            length = length >= 32 ? 8 : length + 2;
        } else if (Kit.inside(mx, my, button(area, 1))) {
            digits = !digits;
        } else if (Kit.inside(mx, my, button(area, 2))) {
            symbols = !symbols;
        } else if (!Kit.inside(mx, my, button(area, 3))) {
            return false;
        }
        generate();
        return true;
    }
}
