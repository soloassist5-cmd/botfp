package dev.lscity.citylife.client.device;

import dev.lscity.citylife.economy.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * «Работа»: курьер, такси, смена. Сверху — текущее задание (куда, сколько
 * осталось, оплата), ниже — список подработок с кнопкой «Взять».
 * Экран сам обновляется раз в две секунды, чтобы видно было расстояние.
 */
class JobsApp extends DeviceApp {

    private static final String[] KINDS = {"courier", "taxi", "shift"};
    private long lastRefresh;

    JobsApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("jobs");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private CompoundTag jobs() {
        return screen.data().getCompound("jobs");
    }

    private int[] quitRect(int[] area) {
        return new int[]{area[0] + area[2] - 70, area[1] + 42, 64, 16};
    }

    private int[] takeRect(int[] area, int i) {
        int top = area[1] + (jobs().contains("active") ? 64 : 22);
        return new int[]{area[0] + area[2] - 56, top + i * 40 + 12, 52, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (now - lastRefresh > 2000) {
            lastRefresh = now;
            screen.send("refresh");
        }
        DeviceScreen.Theme t = screen.theme();
        CompoundTag jobs = jobs();
        String stats = Component.translatable("citylife.job.stats", jobs.getInt("done"),
                Money.format(jobs.getLong("earned"))).getString();
        screen.text(g, screen.trim(stats, area[2] - 8), area[0] + 4, area[1] + 4, t.dim());
        int y = area[1] + 18;
        if (jobs.contains("active")) {
            CompoundTag a = jobs.getCompound("active");
            screen.card(g, new int[]{area[0], y, area[2], 42}, false);
            screen.text(g, a.getString("title") + " · " + Money.format(a.getLong("pay")),
                    area[0] + 5, y + 3, t.accent());
            screen.text(g, screen.trim(a.getString("target"), area[2] - 10), area[0] + 5, y + 14,
                    t.text());
            String info = "shift".equals(a.getString("kind"))
                    ? Component.translatable("citylife.job.shift.info", a.getInt("worked"),
                    a.getInt("shift"), a.getInt("distance")).getString()
                    : Component.translatable("citylife.job.info", a.getInt("distance"),
                    a.getLong("left") / 60, a.getLong("left") % 60).getString();
            screen.text(g, screen.trim(info, area[2] - 80), area[0] + 5, y + 26, t.dim());
            int[] q = quitRect(area);
            screen.button(g, q[0], q[1], q[2],
                    Component.translatable("citylife.job.quit_button").getString(), t.red(),
                    mouseX, mouseY);
        }
        for (int i = 0; i < KINDS.length; i++) {
            int[] take = takeRect(area, i);
            int top = take[1] - 12;
            screen.card(g, new int[]{area[0], top, area[2], 36}, false);
            screen.text(g, Component.translatable("citylife.job." + KINDS[i] + ".title").getString(),
                    area[0] + 5, top + 3, t.text());
            String about = Component.translatable("citylife.job." + KINDS[i] + ".about").getString();
            screen.text(g, screen.trim(about, area[2] - 66), area[0] + 5, top + 15, t.dim());
            if (!jobs.contains("active")) {
                screen.button(g, take[0], take[1], take[2],
                        Component.translatable("citylife.job.take").getString(), t.button(),
                        mouseX, mouseY);
            }
        }
        if (jobs.getLong("cooldown") > 0 && !jobs.contains("active")) {
            screen.text(g, Component.translatable("citylife.job.cooldown", jobs.getLong("cooldown"))
                    .getString(), area[0] + 4, area[1] + area[3] - 10, t.dim());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        CompoundTag jobs = jobs();
        if (jobs.contains("active")) {
            int[] q = quitRect(area);
            if (screen.inside(mx, my, q)) {
                screen.send("job_quit");
                return true;
            }
            return false;
        }
        for (int i = 0; i < KINDS.length; i++) {
            if (screen.inside(mx, my, takeRect(area, i))) {
                CompoundTag args = new CompoundTag();
                args.putString("kind", KINDS[i]);
                screen.send("job_take", args);
                return true;
            }
        }
        return false;
    }
}
