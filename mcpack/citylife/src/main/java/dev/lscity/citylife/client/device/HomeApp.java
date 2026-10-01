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
 * Значков бывает больше, чем влезает: стол листается колесом мыши или
 * перетаскиванием (как пальцем). Правый клик по значку из магазина —
 * «Удалить?»; встроенные приложения удалить нельзя, об этом и скажем.
 *
 * Подпись под значком лежит на подложке и ужимается под ширину ячейки,
 * поэтому длинные названия («Сообщения», «Маркетплейс») не залезают на
 * соседей, а текст читается на любых обоях.
 */
class HomeApp extends DeviceApp {

    /** Сдвиг стола вверх, пикселей. */
    private int offset;
    /** Где нажали и насколько успели протащить: короткое нажатие — открыть. */
    private double pressX = -1;
    private double pressY = -1;
    private double dragged;
    /** Приложение, которое спрашиваем «удалить?»; сообщение для встроенного. */
    private String confirm;
    private String notice;
    private long noticeUntil;

    HomeApp(DeviceScreen screen) {
        super(screen);
    }

    private java.util.Set<String> removable() {
        java.util.Set<String> out = new java.util.HashSet<>();
        ListTag list = screen.strings("removable");
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    private int rowH() {
        return screen.frame().icon + 20;
    }

    private int top(int[] area) {
        return area[1] + 6 + (simBanner() ? 34 : 0);
    }

    /** Высота всей сетки значков. */
    private int content(int[] area) {
        int rows = (apps().size() + screen.frame().columns - 1) / screen.frame().columns;
        return top(area) - area[1] + rows * rowH();
    }

    private void clamp(int[] area) {
        offset = Math.max(0, Math.min(offset, Math.max(0, content(area) - area[3])));
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
        int cell = area[2] / columns;
        int col = index % columns;
        int row = index / columns;
        return new int[]{area[0] + col * cell + (cell - icon) / 2,
                top(area) + row * rowH() - offset, icon, icon};
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
        clamp(area);
        int cell = area[2] / screen.frame().columns;
        String wallpaper = screen.wallpaper();
        g.enableScissor(area[0] - 4, area[1], area[0] + area[2] + 4, area[1] + area[3]);
        for (int i = 0; i < apps.size(); i++) {
            int[] r = iconRect(i, area);
            if (r[1] + r[3] + 14 < area[1] || r[1] > area[1] + area[3]) {
                continue;
            }
            boolean hover = screen.inside(mouseX, mouseY, r) && confirm == null;
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
        g.disableScissor();
        screen.scrollbar(g, area, offset, content(area));
        if (confirm != null) {
            int[] box = confirmBox(area);
            PhoneUi.roundedRect(g, box[0], box[1], box[2], box[3], 8, 0xF0181B26);
            screen.fitted(g, Component.translatable("citylife.home.remove_ask",
                    screen.appTitle(confirm)).getString(), box[0] + box[2] / 2, box[1] + 7,
                    box[2] - 10, t.text());
            int[][] buttons = confirmButtons(area);
            screen.button(g, buttons[0][0], buttons[0][1], buttons[0][2],
                    Component.translatable("citylife.market.remove").getString(), 0xFFB03434,
                    mouseX, mouseY);
            screen.button(g, buttons[1][0], buttons[1][1], buttons[1][2],
                    Component.translatable("citylife.home.cancel").getString(), 0xFF3A3F50,
                    mouseX, mouseY);
        } else if (notice != null && System.currentTimeMillis() < noticeUntil) {
            int[] box = confirmBox(area);
            PhoneUi.roundedRect(g, box[0], box[1] + 10, box[2], 20, 8, 0xE0181B26);
            screen.fitted(g, notice, box[0] + box[2] / 2, box[1] + 16, box[2] - 10, t.dim());
        }
    }

    private int[] confirmBox(int[] area) {
        int h = 46;
        return new int[]{area[0] + 4, area[1] + area[3] - h - 4, area[2] - 8, h};
    }

    private int[][] confirmButtons(int[] area) {
        int[] box = confirmBox(area);
        int w = (box[2] - 18) / 2;
        return new int[][]{{box[0] + 6, box[1] + 24, w, 16}, {box[0] + 12 + w, box[1] + 24, w, 16}};
    }

    private int iconAt(double mx, double my, int[] area) {
        if (my < area[1] || my > area[1] + area[3]) {
            return -1;
        }
        List<String> apps = apps();
        for (int i = 0; i < apps.size(); i++) {
            if (screen.inside(mx, my, iconRect(i, area))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean scroll(double delta) {
        offset -= (int) Math.signum(delta) * rowH();
        return true;
    }

    @Override
    public boolean drag(double mx, double my, double dy, int[] area) {
        if (pressY < 0) {
            return false;
        }
        dragged += Math.abs(dy);
        offset -= (int) Math.round(dy);
        clamp(area);
        return true;
    }

    @Override
    public boolean release(double mx, double my, int[] area) {
        if (pressY < 0) {
            return false;
        }
        boolean tap = dragged < 4;
        double x = pressX;
        double y = pressY;
        pressX = pressY = -1;
        dragged = 0;
        if (tap) {
            int index = iconAt(x, y, area);
            if (index >= 0) {
                screen.open(apps().get(index));
            }
        }
        return true;
    }

    @Override
    public boolean rightClick(double mx, double my, int[] area) {
        int index = iconAt(mx, my, area);
        if (index < 0) {
            return false;
        }
        String app = apps().get(index);
        if (removable().contains(app)) {
            confirm = app;
        } else {
            notice = Component.translatable("citylife.home.builtin", screen.appTitle(app)).getString();
            noticeUntil = System.currentTimeMillis() + 2500;
        }
        return true;
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (simBanner() && screen.inside(mx, my, bannerRect(area))) {
            screen.send("sim_insert");
            return true;
        }
        if (confirm != null) {
            int[][] buttons = confirmButtons(area);
            if (screen.inside(mx, my, buttons[0])) {
                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                args.putString("app", confirm);
                screen.send("app_remove", args);
                notice = Component.translatable("citylife.home.removed",
                        screen.appTitle(confirm)).getString();
                noticeUntil = System.currentTimeMillis() + 2500;
            }
            confirm = null;
            return true;
        }
        if (iconAt(mx, my, area) < 0 && (my < area[1] || my > area[1] + area[3])) {
            return false;
        }
        // Открываем на отпускании: если палец потащили, это прокрутка.
        pressX = mx;
        pressY = my;
        dragged = 0;
        return true;
    }
}
