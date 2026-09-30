package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * «Телефон»: набрать номер SIM (48-21), принять или сбросить звонок,
 * вызвать такси. Голос идёт через Simple Voice Chat, если он установлен.
 * Экран сам обновляется раз в секунду — видно, что звонят.
 */
class PhoneApp extends DeviceApp {

    private long lastRefresh;

    PhoneApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("phone");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private CompoundTag call() {
        return screen.data().getCompound("call");
    }

    @Override
    public void init(int[] area) {
        screen.input("dial", area[0] + 4, area[1] + 4, area[2] - 90, "citylife.call.number_hint", 7);
    }

    private int[] dialRect(int[] area) {
        return new int[]{area[0] + area[2] - 80, area[1], 80, 16};
    }

    private int[] leftRect(int[] area) {
        return new int[]{area[0], area[1] + 74, area[2] / 2 - 2, 16};
    }

    private int[] rightRect(int[] area) {
        return new int[]{area[0] + area[2] / 2 + 2, area[1] + 74, area[2] / 2 - 2, 16};
    }

    private int[] taxiRect(int[] area) {
        return new int[]{area[0], area[1] + 22, area[2], 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (now - lastRefresh > 1000) {
            lastRefresh = now;
            screen.send("refresh");
        }
        DeviceScreen.Theme t = screen.theme();
        CompoundTag call = call();
        screen.card(g, new int[]{area[0], area[1], area[2] - 84, 16}, false);
        int[] dial = dialRect(area);
        screen.button(g, dial[0], dial[1], dial[2],
                Component.translatable("citylife.call.dial").getString(), t.green(), mouseX, mouseY);
        int[] taxi = taxiRect(area);
        screen.button(g, taxi[0], taxi[1], taxi[2],
                Component.translatable("citylife.taxi.order").getString(), 0xFFE0A21E, mouseX, mouseY);

        String state = call.getString("state");
        int y = area[1] + 44;
        if (!state.isEmpty()) {
            screen.card(g, new int[]{area[0], y, area[2], 48}, false);
            long secs = call.getLong("secs");
            String time = secs / 60 + ":" + String.format("%02d", secs % 60);
            String head = Component.translatable("citylife.call.state." + state).getString();
            screen.text(g, head + " · " + time, area[0] + 5, y + 3, t.accent());
            screen.text(g, screen.trim(call.getString("peer") + " · " + call.getString("number"),
                    area[2] - 10), area[0] + 5, y + 15, t.text());
            if ("incoming".equals(state)) {
                int[] l = leftRect(area);
                screen.button(g, l[0], l[1], l[2], Component.translatable("citylife.call.answer")
                        .getString(), t.green(), mouseX, mouseY);
            }
            int[] r = "incoming".equals(state) ? rightRect(area)
                    : new int[]{area[0], area[1] + 74, area[2], 16};
            screen.button(g, r[0], r[1], r[2], Component.translatable(
                    "incoming".equals(state) ? "citylife.call.decline" : "citylife.call.hangup")
                    .getString(), t.red(), mouseX, mouseY);
            y += 54;
        }
        if (!call.getBoolean("voice")) {
            screen.text(g, screen.trim(Component.translatable("citylife.call.no_voice_hint")
                    .getString(), area[2] - 4), area[0] + 2, y, t.dim());
            y += 12;
        }
        ListTag recent = call.getList("recent", 8);
        if (!recent.isEmpty()) {
            screen.text(g, Component.translatable("citylife.call.recent").getString(), area[0] + 2, y,
                    t.dim());
            y += 12;
            for (int i = 0; i < recent.size() && y + 10 < area[1] + area[3]; i++) {
                screen.text(g, screen.trim(recent.getString(i), area[2] - 4), area[0] + 2, y, t.text());
                y += 11;
            }
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        String state = call().getString("state");
        if (screen.inside(mx, my, dialRect(area))) {
            if (!screen.value("dial").isBlank()) {
                CompoundTag args = new CompoundTag();
                args.putString("number", screen.value("dial"));
                screen.send("call_dial", args);
            }
            return true;
        }
        if (screen.inside(mx, my, taxiRect(area))) {
            screen.send("taxi_order");
            return true;
        }
        if (state.isEmpty()) {
            return false;
        }
        if ("incoming".equals(state) && screen.inside(mx, my, leftRect(area))) {
            screen.send("call_answer");
            return true;
        }
        int[] r = "incoming".equals(state) ? rightRect(area) : new int[]{area[0], area[1] + 74, area[2], 16};
        if (screen.inside(mx, my, r)) {
            screen.send("call_hangup");
            return true;
        }
        return false;
    }
}
