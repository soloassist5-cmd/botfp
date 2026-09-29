package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Приложение гаджета.
 *
 * Каждое приложение — отдельный класс: рисует себя в рабочей области экрана
 * и обрабатывает свои клики. Общие вещи (корпус, строка состояния, шапка,
 * сеть, отправка действий на сервер) делает DeviceScreen.
 */
@OnlyIn(Dist.CLIENT)
public abstract class DeviceApp {

    protected final DeviceScreen screen;

    protected DeviceApp(DeviceScreen screen) {
        this.screen = screen;
    }

    /** Заголовок в шапке. */
    public abstract String title();

    /** Добавить поля ввода: вызывается при каждом открытии и обновлении. */
    public void init(int[] area) {
    }

    public abstract void render(GuiGraphics g, int[] area, int mouseX, int mouseY);

    public boolean click(double mx, double my, int[] area) {
        return false;
    }

    public boolean scroll(double delta) {
        return false;
    }

    public boolean key(int code) {
        return false;
    }

    /** Куда ведёт стрелка «назад» в шапке. */
    public String back() {
        return "home";
    }

    /** Приложению нужен интернет: без сети вместо него открывается заглушка. */
    public boolean needsNetwork() {
        return false;
    }
}
