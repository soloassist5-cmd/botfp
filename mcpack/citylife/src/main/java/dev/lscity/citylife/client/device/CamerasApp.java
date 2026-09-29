package dev.lscity.citylife.client.device;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Камеры видеонаблюдения SecurityCraft.
 *
 * Привязка — клик гаджетом по своей камере. Строка списка: клик по ней
 * включает просмотр (дальше управление родное: мышь — поворот, колесо —
 * зум, Shift — выйти), крестик справа убирает камеру из списка.
 */
class CamerasApp extends DeviceApp {

    CamerasApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("cameras");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private int[] row(int[] area, int index) {
        return new int[]{area[0], area[1] + 14 + index * 27, area[2], 24};
    }

    private int[] cross(int[] row) {
        return new int[]{row[0] + row[2] - 20, row[1] + 4, 16, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        screen.text(g, screen.trim(Component.translatable("citylife.camera.hint").getString(),
                area[2]), area[0], area[1] + 2, t.dim());
        ListTag cams = screen.list("cameras");
        if (cams.isEmpty()) {
            screen.empty(g, new int[]{area[0], area[1] + 14, area[2], area[3] - 14},
                    "citylife.camera.none");
            return;
        }
        for (int i = 0; i < cams.size(); i++) {
            int[] r = row(area, i);
            if (r[1] + r[3] > area[1] + area[3]) {
                break;
            }
            CompoundTag entry = cams.getCompound(i);
            screen.card(g, r, screen.inside(mouseX, mouseY, r));
            screen.text(g, screen.trim(entry.getString("label"), r[2] - 32), r[0] + 8, r[1] + 3,
                    t.text());
            screen.text(g, entry.getInt("dist") + " м · "
                    + Component.translatable("citylife.camera.watch").getString(),
                    r[0] + 8, r[1] + 13, t.accent());
            int[] c = cross(r);
            boolean hot = screen.inside(mouseX, mouseY, c);
            screen.text(g, "×", c[0] + 5, c[1] + 4, hot ? t.red() : t.dim());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        ListTag cams = screen.list("cameras");
        for (int i = 0; i < cams.size(); i++) {
            int[] r = row(area, i);
            if (!screen.inside(mx, my, r)) {
                continue;
            }
            CompoundTag args = new CompoundTag();
            args.putLong("pos", cams.getCompound(i).getLong("pos"));
            if (screen.inside(mx, my, cross(r))) {
                screen.send("cam_del", args);
            } else {
                screen.send("cam_view", args);
                // Экран гаджета закрываем: дальше картинку показывает сама камера.
                Minecraft.getInstance().setScreen(null);
            }
            return true;
        }
        return false;
    }
}
