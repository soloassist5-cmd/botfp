package dev.lscity.citylife.client.device;

import dev.lscity.citylife.economy.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * «Работа»: подработки и дежурства в службах 112.
 *
 * Сверху — текущее задание (куда, сколько осталось, оплата) и кнопка
 * «Бросить». Ниже — ряд дежурства (полиция, скорая, пожарные) и список
 * подработок с кнопкой «Взять»; список прокручивается колесом. Экран сам
 * обновляется раз в две секунды, чтобы видно было расстояние.
 */
class JobsApp extends DeviceApp {

    private static final String[] KINDS = {"courier", "food", "taxi", "shift", "guard", "loader",
            "garbage"};
    private static final String[] SERVICES = {"police", "medic", "fire"};
    private static final int ROW = 34;
    private long lastRefresh;
    private int offset;

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

    private int listTop(int[] area) {
        return area[1] + (jobs().contains("active") ? 62 : 18) + 22;
    }

    private int[] quitRect(int[] area) {
        return new int[]{area[0] + area[2] - 70, area[1] + 42, 64, 16};
    }

    private int[] dutyRect(int[] area, int i) {
        int w = (area[2] - 6) / 4;
        int y = area[1] + (jobs().contains("active") ? 62 : 18);
        return new int[]{area[0] + i * (w + 2), y, w, 16};
    }

    private int[] takeRect(int[] area, int row) {
        return new int[]{area[0] + area[2] - 56, listTop(area) + row * ROW + 10, 52, 16};
    }

    private int rows(int[] area) {
        return Math.max(1, (area[1] + area[3] - listTop(area)) / ROW);
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
                    a.getLong("left") / 60 + ":" + String.format("%02d", a.getLong("left") % 60))
                    .getString();
            screen.text(g, screen.trim(info, area[2] - 80), area[0] + 5, y + 28, t.dim());
            int[] q = quitRect(area);
            screen.button(g, q[0], q[1], q[2],
                    Component.translatable("citylife.job.quit_button").getString(), t.red(),
                    mouseX, mouseY);
        }

        // Дежурство: три службы и «снять».
        String duty = jobs.getString("duty");
        for (int i = 0; i < 4; i++) {
            int[] r = dutyRect(area, i);
            boolean on = i < 3 ? SERVICES[i].equals(duty) : duty.isEmpty();
            String label = i < 3 ? Component.translatable("citylife.duty.short." + SERVICES[i])
                    .getString() : Component.translatable("citylife.duty.short.off").getString();
            screen.button(g, r[0], r[1], r[2], label, on ? t.green() : t.button(), mouseX, mouseY);
        }

        int rows = rows(area);
        offset = Math.max(0, Math.min(offset, KINDS.length - rows));
        for (int row = 0; row < rows && offset + row < KINDS.length; row++) {
            String kind = KINDS[offset + row];
            int top = listTop(area) + row * ROW;
            screen.card(g, new int[]{area[0], top, area[2], ROW - 3}, false);
            screen.text(g, Component.translatable("citylife.job." + kind + ".title").getString(),
                    area[0] + 5, top + 3, t.text());
            String about = Component.translatable("citylife.job." + kind + ".about").getString();
            screen.text(g, screen.trim(about, area[2] - 66), area[0] + 5, top + 15, t.dim());
            if (!jobs.contains("active")) {
                int[] take = takeRect(area, row);
                screen.button(g, take[0], take[1] - 4, take[2],
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
        for (int i = 0; i < 4; i++) {
            if (screen.inside(mx, my, dutyRect(area, i))) {
                CompoundTag args = new CompoundTag();
                args.putString("service", i < 3 ? SERVICES[i] : "off");
                screen.send("duty_set", args);
                return true;
            }
        }
        if (jobs.contains("active")) {
            if (screen.inside(mx, my, quitRect(area))) {
                screen.send("job_quit");
                return true;
            }
            return false;
        }
        int rows = rows(area);
        for (int row = 0; row < rows && offset + row < KINDS.length; row++) {
            int[] take = takeRect(area, row);
            if (screen.inside(mx, my, new int[]{take[0], take[1] - 4, take[2], take[3]})) {
                CompoundTag args = new CompoundTag();
                args.putString("kind", KINDS[offset + row]);
                screen.send("job_take", args);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        offset = Math.max(0, Math.min(KINDS.length - 1, offset - (int) Math.signum(delta)));
        return true;
    }
}
