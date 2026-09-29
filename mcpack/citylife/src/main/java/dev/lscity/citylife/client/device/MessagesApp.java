package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.item.SimCardItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SMS: список переписок по номерам и сам чат.
 *
 * Сообщения принадлежат SIM-карте: сервер присылает все письма этого
 * номера, а здесь они раскладываются по собеседникам. Новую переписку
 * можно начать, набрав четыре цифры номера.
 */
class MessagesApp extends DeviceApp {

    private final boolean chat;
    private int scroll;

    MessagesApp(DeviceScreen screen, boolean chat) {
        super(screen);
        this.chat = chat;
    }

    @Override
    public String title() {
        if (!chat) {
            return screen.appTitle("messages");
        }
        String name = nameOf(screen.chatNumber);
        return SimCardItem.format(screen.chatNumber) + (name.isEmpty() ? "" : " · " + name);
    }

    @Override
    public String back() {
        return chat ? "messages" : "home";
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void init(int[] area) {
        int y = area[1] + area[3] - 18;
        if (chat) {
            screen.input("chat", area[0], y + 2, area[2] - sendWidth() - 8,
                    "citylife.phone.message", 180);
        } else {
            screen.input("dial", area[0], area[1] + 4, area[2] - newWidth() - 8,
                    "citylife.phone.number", 4);
        }
    }

    private int sendWidth() {
        return screen.buttonWidth(Component.translatable("citylife.button.send").getString());
    }

    private int newWidth() {
        return screen.buttonWidth(Component.translatable("citylife.button.write").getString());
    }

    private int other(CompoundTag message) {
        int mine = screen.sim();
        return message.getInt("fromNumber") == mine ? message.getInt("toNumber")
                : message.getInt("fromNumber");
    }

    /** Имя собеседника: из подписи его последнего письма или из контактов. */
    private String nameOf(int number) {
        for (CompoundTag contact : compounds("contacts")) {
            if (contact.getInt("number") == number) {
                return contact.getString("name");
            }
        }
        for (CompoundTag message : compounds("messages")) {
            if (message.getInt("fromNumber") == number) {
                return message.getString("from");
            }
        }
        return "";
    }

    private List<CompoundTag> compounds(String key) {
        ListTag list = screen.list(key);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getCompound(i));
        }
        return out;
    }

    /** Переписки: собеседник -> последнее письмо (список с сервера уже от новых к старым). */
    private Map<Integer, CompoundTag> threads() {
        Map<Integer, CompoundTag> out = new LinkedHashMap<>();
        for (CompoundTag message : compounds("messages")) {
            out.putIfAbsent(other(message), message);
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        if (chat) {
            renderChat(g, area, t, mouseX, mouseY);
            return;
        }
        int[] field = {area[0] - 3, area[1], area[2] - newWidth() - 2, 16};
        screen.card(g, field, false);
        screen.button(g, area[0] + area[2] - newWidth(), area[1],
                0, Component.translatable("citylife.button.write").getString(), t.button(),
                mouseX, mouseY);

        Map<Integer, CompoundTag> threads = threads();
        if (threads.isEmpty()) {
            screen.empty(g, area, "citylife.phone.no_messages");
            return;
        }
        int y = area[1] + 24;
        int shown = 0;
        int index = 0;
        for (Map.Entry<Integer, CompoundTag> entry : threads.entrySet()) {
            if (index++ < scroll) {
                continue;
            }
            if (y + 26 > area[1] + area[3]) {
                break;
            }
            int[] r = {area[0], y, area[2], 24};
            screen.card(g, r, screen.inside(mouseX, mouseY, r));
            PhoneUi.disc(g, r[0] + 12, r[1] + 12, 8, 0xFF3A4055);
            String name = nameOf(entry.getKey());
            screen.text(g, SimCardItem.format(entry.getKey()) + (name.isEmpty() ? "" : " · " + name),
                    r[0] + 25, r[1] + 3, t.accent());
            screen.text(g, screen.trim(entry.getValue().getString("text"), r[2] - 32),
                    r[0] + 25, r[1] + 13, t.text());
            y += 27;
            shown++;
        }
    }

    private void renderChat(GuiGraphics g, int[] area, DeviceScreen.Theme t, int mouseX,
                            int mouseY) {
        List<CompoundTag> lines = new ArrayList<>();
        for (CompoundTag message : compounds("messages")) {
            if (other(message) == screen.chatNumber) {
                lines.add(0, message);
            }
        }
        int bottom = area[1] + area[3] - 24;
        int y = bottom;
        // Снизу вверх: свежие у поля ввода, как в любом мессенджере.
        for (int i = lines.size() - 1 - scroll; i >= 0; i--) {
            CompoundTag message = lines.get(i);
            boolean mine = message.getInt("fromNumber") == screen.sim();
            String text = screen.trim(message.getString("text"), area[2] * 3 / 4 - 12);
            int w = screen.font().width(text) + 12;
            y -= 18;
            if (y < area[1]) {
                break;
            }
            int x = mine ? area[0] + area[2] - w : area[0];
            PhoneUi.roundedRect(g, x, y, w, 15, 7, mine ? 0xFF1F6FD0 : 0xFF2A2F3F);
            screen.text(g, text, x + 6, y + 4, 0xFFFFFFFF);
        }
        if (lines.isEmpty()) {
            screen.empty(g, area, "citylife.phone.chat_empty");
        }
        int[] field = {area[0] - 3, area[1] + area[3] - 18, area[2] - sendWidth() - 2, 16};
        screen.card(g, field, false);
        screen.button(g, area[0] + area[2] - sendWidth(), area[1] + area[3] - 18, 0,
                Component.translatable("citylife.button.send").getString(), t.button(),
                mouseX, mouseY);
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (chat) {
            int[] send = {area[0] + area[2] - sendWidth(), area[1] + area[3] - 18,
                    sendWidth(), 16};
            if (screen.inside(mx, my, send) && !screen.value("chat").isBlank()) {
                CompoundTag args = new CompoundTag();
                args.putInt("to", screen.chatNumber);
                args.putString("text", screen.value("chat"));
                screen.send("msg", args);
                screen.clear("chat");
                return true;
            }
            return false;
        }
        int[] write = {area[0] + area[2] - newWidth(), area[1], newWidth(), 16};
        if (screen.inside(mx, my, write)) {
            String digits = screen.value("dial").replaceAll("[^0-9]", "");
            if (digits.length() == 4) {
                screen.clear("dial");
                screen.openChat(Integer.parseInt(digits));
            }
            return true;
        }
        int y = area[1] + 24;
        int index = 0;
        for (Integer number : threads().keySet()) {
            if (index++ < scroll) {
                continue;
            }
            if (screen.inside(mx, my, new int[]{area[0], y, area[2], 24})) {
                screen.openChat(number);
                return true;
            }
            y += 27;
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }
}
