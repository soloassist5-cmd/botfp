package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * «LS Сеть» — городской портал.
 *
 * Две вкладки. «Новости» собираются из того, что происходит на сервере:
 * день и погода, кто сейчас в сети. «Объявления» — общая доска: игрок
 * платит сотню со счёта и вешает строку текста, её видят все с интернетом.
 * Своё объявление можно снять, админ снимает любое.
 */
class BrowserApp extends DeviceApp {

    private final boolean board;
    private int offset;

    BrowserApp(DeviceScreen screen, boolean board) {
        super(screen);
        this.board = board;
    }

    @Override
    public String title() {
        return screen.appTitle("browser");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void init(int[] area) {
        if (board) {
            screen.input("ad", area[0] + 4, area[1] + 24, area[2] - 70, "citylife.ads.hint", 120);
        }
    }

    private int[] tab(int[] area, int index) {
        return new int[]{area[0] + index * (area[2] / 2), area[1], area[2] / 2 - 2, 16};
    }

    private int[] postRect(int[] area) {
        return new int[]{area[0] + area[2] - 62, area[1] + 20, 62, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        String[] names = {Component.translatable("citylife.browser.news").getString(),
                          Component.translatable("citylife.browser.board").getString()};
        for (int i = 0; i < 2; i++) {
            int[] r = tab(area, i);
            screen.button(g, r[0], r[1], r[2], names[i], (i == 1) == board ? t.button() : 0xFF3A3F50,
                    mouseX, mouseY);
        }
        if (board) {
            renderBoard(g, area, mouseX, mouseY, t);
        } else {
            renderNews(g, area, t);
        }
    }

    private void renderNews(GuiGraphics g, int[] area, DeviceScreen.Theme t) {
        CompoundTag news = screen.data().getCompound("news");
        int y = area[1] + 24;
        String weather = news.getBoolean("thunder") ? "гроза" : news.getBoolean("rain")
                ? "дождь" : "ясно";
        String[] lines = {
            "§lЛос-Сантос сегодня",
            "День " + news.getLong("day") + " · погода: " + weather,
            "",
            "§lВ сети (" + news.getList("online", 8).size() + ")",
        };
        for (String line : lines) {
            screen.text(g, line, area[0] + 4, y, t.text());
            y += 11;
        }
        ListTag online = news.getList("online", 8);
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < online.size(); i++) {
            row.append(i == 0 ? "" : ", ").append(online.getString(i));
        }
        for (var line : screen.font().split(Component.literal(row.toString()), area[2] - 8)) {
            if (y > area[1] + area[3] - 34) {
                break;
            }
            g.drawString(screen.font(), line, area[0] + 4, y, t.dim(), false);
            y += 10;
        }
        y += 6;
        ListTag ads = screen.list("ads");
        screen.text(g, "§lСвежие объявления", area[0] + 4, y, t.text());
        y += 11;
        for (int i = 0; i < Math.min(2, ads.size()); i++) {
            CompoundTag ad = ads.getCompound(i);
            screen.text(g, screen.trim(ad.getString("author") + ": " + ad.getString("text"),
                    area[2] - 8), area[0] + 4, y, t.dim());
            y += 10;
        }
    }

    private void renderBoard(GuiGraphics g, int[] area, int mouseX, int mouseY,
                             DeviceScreen.Theme t) {
        screen.card(g, new int[]{area[0], area[1] + 20, area[2] - 66, 16}, false);
        int[] post = postRect(area);
        screen.button(g, post[0], post[1], post[2],
                screen.data().getLong("adPrice") + " ₽", 0xFF1F8F57, mouseX, mouseY);
        ListTag ads = screen.list("ads");
        if (ads.isEmpty()) {
            screen.empty(g, new int[]{area[0], area[1] + 40, area[2], area[3] - 40},
                    "citylife.ads.none");
            return;
        }
        int y = area[1] + 42;
        for (int i = offset; i < ads.size(); i++) {
            CompoundTag ad = ads.getCompound(i);
            var lines = screen.font().split(Component.literal(ad.getString("text")), area[2] - 24);
            int h = 12 + lines.size() * 10;
            if (y + h > area[1] + area[3]) {
                break;
            }
            screen.card(g, new int[]{area[0], y, area[2], h}, false);
            screen.text(g, ad.getString("author") + " · " + ago(ad.getLong("ago")), area[0] + 5,
                    y + 2, t.accent());
            int ly = y + 12;
            for (var line : lines) {
                g.drawString(screen.font(), line, area[0] + 5, ly, t.text(), false);
                ly += 10;
            }
            if (ad.getBoolean("mine")) {
                screen.text(g, "×", area[0] + area[2] - 10, y + 2, t.red());
            }
            y += h + 3;
        }
    }

    static String ago(long seconds) {
        if (seconds < 60) {
            return "только что";
        }
        if (seconds < 3600) {
            return seconds / 60 + " мин назад";
        }
        return seconds / 3600 + " ч назад";
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        for (int i = 0; i < 2; i++) {
            if (screen.inside(mx, my, tab(area, i))) {
                // Вкладка с полем ввода — отдельный экран, чтобы поле появилось и исчезло.
                if ((i == 1) != board) {
                    screen.open(i == 1 ? "browser_board" : "browser");
                }
                return true;
            }
        }
        if (!board) {
            return false;
        }
        if (screen.inside(mx, my, postRect(area)) && !screen.value("ad").isBlank()) {
            CompoundTag args = new CompoundTag();
            args.putString("text", screen.value("ad"));
            screen.send("ad_post", args);
            screen.clear("ad");
            return true;
        }
        ListTag ads = screen.list("ads");
        int y = area[1] + 42;
        for (int i = offset; i < ads.size(); i++) {
            CompoundTag ad = ads.getCompound(i);
            int h = 12 + screen.font().split(Component.literal(ad.getString("text")),
                    area[2] - 24).size() * 10;
            if (ad.getBoolean("mine") && screen.inside(mx, my,
                    new int[]{area[0] + area[2] - 14, y, 14, 12})) {
                CompoundTag args = new CompoundTag();
                args.putInt("id", ad.getInt("id"));
                screen.send("ad_del", args);
                return true;
            }
            y += h + 3;
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        if (!board) {
            return false;
        }
        offset = Math.max(0, Math.min(screen.list("ads").size() - 1, offset - (int) Math.signum(delta)));
        return true;
    }
}
