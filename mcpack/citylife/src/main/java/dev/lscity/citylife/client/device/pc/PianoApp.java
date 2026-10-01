package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Пианино: две октавы нотных блоков, белые и чёрные клавиши; играть мышью
 * или с клавиатуры (A S D F G H J K — белые, W E T Y U — чёрные).
 * Внизу — смена инструмента: пианино, гитара, флейта, колокольчик, бас.
 */
class PianoApp extends PcApp {

    private static final String[] NAMES = {"Пианино", "Гитара", "Флейта", "Колокольчик", "Бас", "Ксилофон"};
    @SuppressWarnings("unchecked")
    private static final Holder<SoundEvent>[] SOUNDS = new Holder[]{SoundEvents.NOTE_BLOCK_HARP,
            SoundEvents.NOTE_BLOCK_GUITAR, SoundEvents.NOTE_BLOCK_FLUTE, SoundEvents.NOTE_BLOCK_BELL,
            SoundEvents.NOTE_BLOCK_BASS, SoundEvents.NOTE_BLOCK_XYLOPHONE};
    /** Полутона белых клавиш от фа-диеза нотного блока (0) — до двух октав. */
    private static final int[] WHITE = {0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21, 23};
    private static final int[] BLACK_AFTER = {0, 1, 3, 4, 5, 7, 8, 10, 11, 12};
    private static final int[] KEYS_WHITE = {65, 83, 68, 70, 71, 72, 74, 75};
    private static final int[] KEYS_BLACK = {87, 69, 84, 89, 85};

    private int instrument;
    private int pressed = -1;
    private long pressedAt;

    PianoApp(DeviceScreen screen) {
        super(screen, "piano");
    }

    private void play(int semitone) {
        float pitch = (float) Math.pow(2, (semitone - 12) / 12.0);
        mc().getSoundManager().play(SimpleSoundInstance.forUI(SOUNDS[instrument].value(), pitch));
        pressed = semitone;
        pressedAt = System.currentTimeMillis();
    }

    private int[] white(int[] area, int i) {
        int w = area[2] / WHITE.length;
        return new int[]{area[0] + i * w, area[1], w - 1, area[3] - 26};
    }

    private int[] black(int[] area, int k) {
        int w = area[2] / WHITE.length;
        int after = BLACK_AFTER[k];
        return new int[]{area[0] + (after + 1) * w - w / 3, area[1], w * 2 / 3, (area[3] - 26) * 6 / 10};
    }

    private int blackTone(int k) {
        return WHITE[BLACK_AFTER[k]] + 1;
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        boolean recent = System.currentTimeMillis() - pressedAt < 180;
        for (int i = 0; i < WHITE.length; i++) {
            int[] r = white(area, i);
            boolean down = recent && pressed == WHITE[i];
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 3, down ? 0xFFBFD8FF : 0xFFF4F4F4);
        }
        for (int k = 0; k < BLACK_AFTER.length; k++) {
            int[] r = black(area, k);
            boolean down = recent && pressed == blackTone(k);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 2, down ? 0xFF3A5A9A : 0xFF15171F);
        }
        for (int i = 0; i < NAMES.length; i++) {
            int[] r = Kit.cell(area[0], area[1] + area[3] - 20, area[2], 18, NAMES.length, 1, i, 3);
            Kit.tile(g, screen, r, NAMES[i], i == instrument ? 0xFF1F6FD0 : Kit.TILE, mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int k = 0; k < BLACK_AFTER.length; k++) {
            if (Kit.inside(mx, my, black(area, k))) {
                play(blackTone(k));
                return true;
            }
        }
        for (int i = 0; i < WHITE.length; i++) {
            if (Kit.inside(mx, my, white(area, i))) {
                play(WHITE[i]);
                return true;
            }
        }
        for (int i = 0; i < NAMES.length; i++) {
            if (Kit.inside(mx, my, Kit.cell(area[0], area[1] + area[3] - 20, area[2], 18, NAMES.length, 1, i, 3))) {
                instrument = i;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean key(int code) {
        for (int i = 0; i < KEYS_WHITE.length; i++) {
            if (KEYS_WHITE[i] == code) {
                play(WHITE[i]);
                return true;
            }
        }
        for (int i = 0; i < KEYS_BLACK.length; i++) {
            if (KEYS_BLACK[i] == code) {
                play(blackTone(i));
                return true;
            }
        }
        return false;
    }
}
