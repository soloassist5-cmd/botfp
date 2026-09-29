package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Почта: письма по нику игрока.
 *
 * Сообщениям нужен номер, а у ноутбука и компьютера SIM нет — поэтому
 * у них своя почта. Письмо дойдёт и тому, кто сейчас не в игре.
 * Вверху поля «Кому» и «Текст», ниже — входящие.
 */
class MailApp extends DeviceApp {

    private int offset;
    private boolean marked;

    MailApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("mail");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void init(int[] area) {
        screen.input("mail_to", area[0] + 4, area[1] + 4, area[2] / 3, "citylife.mail.to", 16);
        screen.input("mail_text", area[0] + area[2] / 3 + 10, area[1] + 4,
                area[2] - area[2] / 3 - 76, "citylife.mail.text", 240);
    }

    private int[] sendRect(int[] area) {
        return new int[]{area[0] + area[2] - 60, area[1], 60, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (!marked && screen.data().getInt("unread") > 0) {
            // Открыл почту — значит прочитал: снимаем значок непрочитанного.
            screen.send("mail_read");
            marked = true;
        }
        DeviceScreen.Theme t = screen.theme();
        screen.card(g, new int[]{area[0], area[1], area[2] / 3 + 4, 16}, false);
        screen.card(g, new int[]{area[0] + area[2] / 3 + 6, area[1], area[2] - area[2] / 3 - 70, 16},
                false);
        int[] send = sendRect(area);
        screen.button(g, send[0], send[1], send[2],
                Component.translatable("citylife.mail.send").getString(), t.button(), mouseX, mouseY);
        ListTag inbox = screen.list("mail");
        if (inbox.isEmpty()) {
            screen.empty(g, new int[]{area[0], area[1] + 20, area[2], area[3] - 20},
                    "citylife.mail.empty");
            return;
        }
        int y = area[1] + 22;
        for (int i = offset; i < inbox.size(); i++) {
            CompoundTag letter = inbox.getCompound(i);
            var lines = screen.font().split(Component.literal(letter.getString("text")),
                    area[2] - 12);
            int h = 12 + Math.min(3, lines.size()) * 10;
            if (y + h > area[1] + area[3]) {
                break;
            }
            screen.card(g, new int[]{area[0], y, area[2], h}, false);
            screen.text(g, (letter.getBoolean("read") ? "" : "● ") + letter.getString("fromName")
                    + " · " + BrowserApp.ago(letter.getLong("ago")), area[0] + 5, y + 2,
                    letter.getBoolean("read") ? t.dim() : t.accent());
            int ly = y + 12;
            for (int l = 0; l < Math.min(3, lines.size()); l++) {
                g.drawString(screen.font(), lines.get(l), area[0] + 5, ly, t.text(), false);
                ly += 10;
            }
            y += h + 3;
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (screen.inside(mx, my, sendRect(area))) {
            if (screen.value("mail_to").isBlank() || screen.value("mail_text").isBlank()) {
                return true;
            }
            CompoundTag args = new CompoundTag();
            args.putString("to", screen.value("mail_to"));
            args.putString("text", screen.value("mail_text"));
            screen.send("mail_send", args);
            screen.clear("mail_text");
            return true;
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        offset = Math.max(0, Math.min(screen.list("mail").size() - 1, offset - (int) Math.signum(delta)));
        return true;
    }
}
