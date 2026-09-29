package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.AppIcons;
import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.client.ui.Wallpapers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Рабочий стол: сетка значков.
 *
 * Подпись под значком лежит на подложке и ужимается под ширину ячейки,
 * поэтому длинные названия («Сообщения», «Маркетплейс») не залезают на
 * соседей, а текст читается на любых обоях.
 */
class HomeApp extends DeviceApp {

    HomeApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return "";
    }

    private List<String> apps() {
        List<String> out = new ArrayList<>();
        ListTag list = screen.strings("apps");
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    /** Плашка «вставьте SIM» сверху стола, если слот пуст. */
    private boolean simBanner() {
        return screen.data().getBoolean("simSlot") && screen.sim() == 0;
    }

    private int[] bannerRect(int[] area) {
        return new int[]{area[0], area[1], area[2], 28};
    }

    private int[] iconRect(int index, int[] area) {
        DeviceFrame frame = screen.frame();
        int icon = frame.icon;
        int columns = frame.columns;
        int top = area[1] + 6 + (simBanner() ? 34 : 0);
        int cell = area[2] / columns;
        int col = index % columns;
        int row = index / columns;
        return new int[]{area[0] + col * cell + (cell - icon) / 2, top + row * (icon + 20),
                icon, icon};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        if (simBanner()) {
            int[] r = bannerRect(area);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 7,
                    screen.inside(mouseX, mouseY, r) ? 0xE0B03434 : 0xD0902A2A);
            screen.fitted(g, Component.translatable("citylife.sim.banner").getString(),
                    r[0] + r[2] / 2, r[1] + 5, r[2] - 10, 0xFFFFFFFF);
            screen.fitted(g, Component.translatable("citylife.sim.banner_hint").getString(),
                    r[0] + r[2] / 2, r[1] + 16, r[2] - 10, 0xCCFFFFFF);
        }
        List<String> apps = apps();
        int cell = area[2] / screen.frame().columns;
        String wallpaper = screen.wallpaper();
        for (int i = 0; i < apps.size(); i++) {
            int[] r = iconRect(i, area);
            boolean hover = screen.inside(mouseX, mouseY, r);
            int lift = hover ? 1 : 0;
            AppIcons.draw(g, apps.get(i), r[0], r[1] - lift, r[2]);
            String name = screen.appTitle(apps.get(i));
            int labelW = Math.min(cell - 2, screen.font().width(name) + 6);
            int cx = r[0] + r[2] / 2;
            int ly = r[1] + r[3] + 3 - lift;
            if (screen.frame().monochrome()) {
                screen.fitted(g, name, cx, ly + 1, cell - 4, t.text());
            } else {
                PhoneUi.roundedRect(g, cx - labelW / 2, ly - 1, labelW, 11, 5,
                        Wallpapers.labelPlate(wallpaper));
                screen.fitted(g, name, cx, ly + 1, cell - 8,
                        hover ? 0xFFFFFFFF : Wallpapers.labelText(wallpaper));
            }
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (simBanner() && screen.inside(mx, my, bannerRect(area))) {
            screen.send("sim_insert");
            return true;
        }
        List<String> apps = apps();
        for (int i = 0; i < apps.size(); i++) {
            if (screen.inside(mx, my, iconRect(i, area))) {
                screen.open(apps.get(i));
                return true;
            }
        }
        return false;
    }
}
