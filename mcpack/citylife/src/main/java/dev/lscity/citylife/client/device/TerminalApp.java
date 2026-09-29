package dev.lscity.citylife.client.device;

import dev.lscity.citylife.item.SimCardItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Терминал: командная строка гаджета. Отвечает по данным устройства,
 * которые уже пришли с сервера, поэтому работает мгновенно и без сети.
 * help — список команд.
 */
class TerminalApp extends DeviceApp {

    /** История сессии живёт, пока открыт экран: как у настоящего терминала. */
    private static final List<String> LOG = new ArrayList<>(List.of(
            "LS OS 1.0 — введи help и нажми Enter"));

    TerminalApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("terminal");
    }

    @Override
    public void init(int[] area) {
        screen.input("term", area[0] + 12, area[1] + area[3] - 12, area[2] - 14,
                "citylife.terminal.hint", 64);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        g.fill(area[0] - 2, area[1], area[0] + area[2] + 2, area[1] + area[3], 0xF00B0E0B);
        int rows = (area[3] - 16) / 10;
        int from = Math.max(0, LOG.size() - rows);
        int y = area[1] + 3;
        for (int i = from; i < LOG.size(); i++) {
            screen.text(g, screen.trim(LOG.get(i), area[2] - 4), area[0] + 2, y, 0xFF7BE07B);
            y += 10;
        }
        screen.text(g, ">", area[0] + 2, area[1] + area[3] - 12, 0xFF7BE07B);
    }

    @Override
    public boolean key(int code) {
        if (code != GLFW.GLFW_KEY_ENTER && code != GLFW.GLFW_KEY_KP_ENTER) {
            return false;
        }
        String line = screen.value("term").trim();
        screen.clear("term");
        if (line.isEmpty()) {
            return true;
        }
        LOG.add("> " + line);
        run(line.toLowerCase(java.util.Locale.ROOT));
        while (LOG.size() > 200) {
            LOG.remove(0);
        }
        return true;
    }

    private void out(String text) {
        LOG.add(text);
    }

    private void run(String line) {
        CompoundTag d = screen.data();
        String[] parts = line.split("\\s+", 2);
        switch (parts[0]) {
            case "help" -> {
                out("whoami   — кто я и какой номер");
                out("balance  — счёт и наличные");
                out("time     — время и день в городе");
                out("online   — кто в сети");
                out("orders   — мои заказы маркетплейса");
                out("mail     — сколько непрочитанных писем");
                out("neofetch — об устройстве");
                out("echo, clear, exit");
            }
            case "whoami" -> {
                int sim = d.getInt("sim");
                out(d.getString("owner") + (sim == 0 ? " · без SIM" : " · номер "
                        + SimCardItem.format(sim)));
            }
            case "balance" -> out("счёт: " + d.getLong("balance") + " ₽ · наличные: "
                    + d.getLong("cash") + " ₽");
            case "time" -> {
                long t = (d.getLong("daytime") + 6000) % 24000;
                out(String.format("%02d:%02d · день %d", t / 1000, t % 1000 * 60 / 1000,
                        d.getCompound("news").getLong("day")));
            }
            case "online" -> {
                ListTag online = d.getCompound("news").getList("online", 8);
                out(online.size() + " в сети");
                for (int i = 0; i < online.size(); i++) {
                    out("  " + online.getString(i));
                }
            }
            case "orders" -> {
                ListTag orders = d.getList("orders", 10);
                if (orders.isEmpty()) {
                    out("заказов нет");
                }
                for (int i = 0; i < orders.size(); i++) {
                    CompoundTag o = orders.getCompound(i);
                    out("#" + o.getInt("id") + " " + o.getString("title") + " — "
                            + o.getString("status"));
                }
            }
            case "mail" -> out("непрочитанных писем: " + d.getInt("unread"));
            case "neofetch" -> {
                out("  ___   " + d.getString("owner") + "@" + d.getString("model"));
                out(" |LS |  устройство: " + d.getString("kind").toLowerCase());
                out(" |OS |  сеть: " + (d.getBoolean("online") ? "есть" : "нет"));
                CompoundTag info = d.getCompound("sysinfo");
                if (!info.isEmpty()) {
                    out("  ‾‾‾   сборка: " + info.getInt("score") + " очков, "
                            + info.getInt("draw") + "/" + info.getInt("supply") + " Вт");
                }
            }
            case "echo" -> out(parts.length > 1 ? parts[1] : "");
            case "clear" -> LOG.clear();
            case "exit" -> screen.open("home");
            default -> out(parts[0] + ": команда не найдена (help)");
        }
    }
}
