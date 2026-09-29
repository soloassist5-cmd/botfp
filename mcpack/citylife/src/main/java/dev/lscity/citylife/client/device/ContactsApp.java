package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.item.SimCardItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Контакты: кто в сети с телефоном, его номер и расстояние. Клик — написать. */
class ContactsApp extends DeviceApp {

    private int scroll;

    ContactsApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("contacts");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private int[] row(int[] area, int index) {
        return new int[]{area[0], area[1] + index * 27, area[2], 24};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        ListTag contacts = screen.list("contacts");
        if (contacts.isEmpty()) {
            screen.empty(g, area, "citylife.phone.no_contacts");
            return;
        }
        for (int i = scroll, shown = 0; i < contacts.size(); i++, shown++) {
            int[] r = row(area, shown);
            if (r[1] + r[3] > area[1] + area[3]) {
                break;
            }
            CompoundTag entry = contacts.getCompound(i);
            screen.card(g, r, screen.inside(mouseX, mouseY, r));
            PhoneUi.disc(g, r[0] + 12, r[1] + 12, 8, 0xFF6F3FD0);
            String initial = entry.getString("name").isEmpty() ? "?"
                    : entry.getString("name").substring(0, 1).toUpperCase();
            screen.text(g, initial, r[0] + 12 - screen.font().width(initial) / 2, r[1] + 8,
                    0xFFFFFFFF);
            screen.text(g, entry.getString("name"), r[0] + 25, r[1] + 3, t.text());
            screen.text(g, SimCardItem.format(entry.getInt("number")) + " · "
                    + entry.getInt("dist") + " м", r[0] + 25, r[1] + 13, t.dim());
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        ListTag contacts = screen.list("contacts");
        for (int i = scroll, shown = 0; i < contacts.size(); i++, shown++) {
            if (screen.inside(mx, my, row(area, shown))) {
                screen.openChat(contacts.getCompound(i).getInt("number"));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        int max = Math.max(0, screen.list("contacts").size() - 4);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }
}
