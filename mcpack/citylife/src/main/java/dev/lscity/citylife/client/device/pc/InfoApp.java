package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceApp;
import dev.lscity.citylife.client.device.DeviceScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Справочник: разделы с заголовками, текст листается колесом. Если
 * разделов много и один показывается за раз (анекдоты, факты, гороскоп),
 * кнопки «‹ ›» листают их по одному.
 */
@OnlyIn(Dist.CLIENT)
class InfoApp extends DeviceApp {

    /** Раздел: заголовок и текст. */
    record Section(String title, String text) {
    }

    private final String id;
    private final List<Section> sections;
    private final boolean cards;
    private int page;
    private int scroll;

    InfoApp(DeviceScreen screen, String id, List<Section> sections, boolean cards) {
        super(screen);
        this.id = id;
        this.sections = sections;
        this.cards = cards;
        if (cards) {
            page = (int) ((System.currentTimeMillis() / 86_400_000L) % sections.size());
        }
    }

    @Override
    public String title() {
        return screen.appTitle(id);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        int bottom = area[1] + area[3] - (cards ? 22 : 0);
        g.enableScissor(area[0], area[1], area[0] + area[2], bottom);
        int y = area[1] - scroll;
        List<Section> shown = cards ? List.of(sections.get(page)) : sections;
        for (Section s : shown) {
            if (!s.title().isEmpty()) {
                screen.text(g, s.title(), area[0], y, Kit.ACCENT);
                y += 13;
            }
            y = Kit.wrap(g, screen, s.text(), area[0], y, area[2] - 4, Kit.TEXT, Integer.MAX_VALUE) + 8;
        }
        g.disableScissor();
        int content = y + scroll - area[1];
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (bottom - area[1]))));
        screen.scrollbar(g, new int[]{area[0], area[1], area[2] - 2, bottom - area[1]}, scroll, content);
        if (cards) {
            int[] prev = prev(area);
            int[] next = next(area);
            Kit.tile(g, screen, prev, "‹", Kit.TILE, mouseX, mouseY);
            Kit.tile(g, screen, next, "›", Kit.TILE, mouseX, mouseY);
            screen.fitted(g, (page + 1) + " / " + sections.size(), area[0] + area[2] / 2,
                    prev[1] + 5, 60, Kit.DIM);
        }
    }

    private int[] prev(int[] area) {
        return new int[]{area[0], area[1] + area[3] - 18, 40, 18};
    }

    private int[] next(int[] area) {
        return new int[]{area[0] + area[2] - 40, area[1] + area[3] - 18, 40, 18};
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (!cards) {
            return false;
        }
        if (Kit.inside(mx, my, prev(area))) {
            page = (page + sections.size() - 1) % sections.size();
            scroll = 0;
            return true;
        }
        if (Kit.inside(mx, my, next(area))) {
            page = (page + 1) % sections.size();
            scroll = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        scroll -= (int) Math.signum(delta) * 20;
        return true;
    }
}
