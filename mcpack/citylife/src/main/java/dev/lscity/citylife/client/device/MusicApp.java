package dev.lscity.citylife.client.device;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Плеер: музыка с пластинок прямо из гаджета, в наушниках — слышит только
 * владелец. Играет и после закрытия экрана, пока не нажмёшь «Стоп».
 */
class MusicApp extends DeviceApp {

    private record Track(String title, SoundEvent sound) {
    }

    private static final List<Track> TRACKS = List.of(
            new Track("C418 — 13", SoundEvents.MUSIC_DISC_13),
            new Track("C418 — cat", SoundEvents.MUSIC_DISC_CAT),
            new Track("C418 — blocks", SoundEvents.MUSIC_DISC_BLOCKS),
            new Track("C418 — chirp", SoundEvents.MUSIC_DISC_CHIRP),
            new Track("C418 — far", SoundEvents.MUSIC_DISC_FAR),
            new Track("C418 — mall", SoundEvents.MUSIC_DISC_MALL),
            new Track("C418 — mellohi", SoundEvents.MUSIC_DISC_MELLOHI),
            new Track("C418 — stal", SoundEvents.MUSIC_DISC_STAL),
            new Track("C418 — strad", SoundEvents.MUSIC_DISC_STRAD),
            new Track("C418 — ward", SoundEvents.MUSIC_DISC_WARD),
            new Track("C418 — wait", SoundEvents.MUSIC_DISC_WAIT),
            new Track("Lena Raine — Pigstep", SoundEvents.MUSIC_DISC_PIGSTEP),
            new Track("Lena Raine — otherside", SoundEvents.MUSIC_DISC_OTHERSIDE),
            new Track("Aaron Cherof — Relic", SoundEvents.MUSIC_DISC_RELIC));

    /** Что играет сейчас: общее для всех гаджетов, чтобы не наложить два трека. */
    private static SoundInstance playing;
    private static int current = -1;
    private int offset;

    MusicApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("music");
    }

    private int[] row(int[] area, int index) {
        return new int[]{area[0], area[1] + 20 + (index - offset) * 19, area[2], 17};
    }

    private int[] stopRect(int[] area) {
        return new int[]{area[0] + area[2] - 50, area[1], 50, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        boolean on = playing != null && Minecraft.getInstance().getSoundManager().isActive(playing);
        String now = on && current >= 0 ? "▶ " + TRACKS.get(current).title() : "—";
        screen.text(g, screen.trim(now, area[2] - 58), area[0] + 2, area[1] + 4,
                on ? t.green() : t.dim());
        int[] stop = stopRect(area);
        screen.button(g, stop[0], stop[1], stop[2], "Стоп", 0xFFC02B3F, mouseX, mouseY);
        for (int i = offset; i < TRACKS.size(); i++) {
            int[] r = row(area, i);
            if (r[1] + r[3] > area[1] + area[3]) {
                break;
            }
            screen.card(g, r, screen.inside(mouseX, mouseY, r) || (on && i == current));
            screen.text(g, TRACKS.get(i).title(), r[0] + 8, r[1] + 5,
                    on && i == current ? t.green() : t.text());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        var sounds = Minecraft.getInstance().getSoundManager();
        if (screen.inside(mx, my, stopRect(area))) {
            if (playing != null) {
                sounds.stop(playing);
            }
            playing = null;
            return true;
        }
        for (int i = offset; i < TRACKS.size(); i++) {
            if (screen.inside(mx, my, row(area, i))) {
                if (playing != null) {
                    sounds.stop(playing);
                }
                playing = SimpleSoundInstance.forMusic(TRACKS.get(i).sound());
                current = i;
                sounds.play(playing);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        offset = Math.max(0, Math.min(TRACKS.size() - 1, offset - (int) Math.signum(delta)));
        return true;
    }
}
