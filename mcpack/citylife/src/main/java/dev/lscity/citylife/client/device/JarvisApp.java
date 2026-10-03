package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.stark.StarkClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * «J.A.R.V.I.S.»: вызов костюма Железного человека.
 *
 * Список марок; тап — Джарвис присылает костюм и сам надевает его вместе
 * с дуговым реактором и открытыми навыками. Внизу — «Снять костюм» и «Управление»: короткая
 * памятка по клавишам. Доступно владельцу башни STARK и всем с допуском.
 */
@OnlyIn(Dist.CLIENT)
class JarvisApp extends DeviceApp {

    private static final int ROW = 22;
    private int offset;
    private boolean help;

    JarvisApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("jarvis");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private CompoundTag state() {
        return screen.data().getCompound("jarvis");
    }

    private int[] list(int[] area) {
        return new int[]{area[0], area[1] + 26, area[2], area[3] - 26 - 22};
    }

    private int[] offButton(int[] area) {
        return new int[]{area[0], area[1] + area[3] - 18, area[2] / 2 - 2, 16};
    }

    private int[] helpButton(int[] area) {
        return new int[]{area[0] + area[2] / 2 + 2, area[1] + area[3] - 18, area[2] / 2 - 2, 16};
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        CompoundTag st = state();
        boolean allowed = st.getBoolean("allowed");
        String status = Component.translatable(st.getBoolean("owner") ? "citylife.jarvis.app.owner"
                : allowed ? "citylife.jarvis.app.cleared" : "citylife.jarvis.app.locked").getString();
        screen.text(g, screen.trim(status, area[2]), area[0], area[1] + 2, allowed ? t.accent() : t.red());
        int suit = st.getInt("mark");
        String now = suit == 0 ? Component.translatable("citylife.jarvis.app.none").getString()
                : StarkClient.suitName(suit);
        screen.text(g, screen.trim(Component.translatable("citylife.jarvis.app.current", now).getString(),
                area[2]), area[0], area[1] + 13, t.dim());
        int[] box = list(area);
        if (help) {
            int y = box[1];
            for (int i = 1; i <= 6; i++) {
                for (var line : screen.font().split(Component.translatable("citylife.jarvis.keys." + i), box[2])) {
                    if (y + 9 > box[1] + box[3]) {
                        break;
                    }
                    g.drawString(screen.font(), line, box[0], y, i == 1 ? t.accent() : t.text(), false);
                    y += 10;
                }
                y += 3;
            }
        } else if (!allowed) {
            int y = box[1] + 6;
            for (var line : screen.font().split(Component.translatable("citylife.jarvis.app.how"), box[2])) {
                g.drawString(screen.font(), line, box[0], y, t.text(), false);
                y += 10;
            }
        } else {
            List<StarkClient.Suit> suits = StarkClient.suitItems();
            clamp(box, suits.size());
            g.enableScissor(box[0], box[1], box[0] + box[2], box[1] + box[3]);
            for (int i = 0; i < suits.size(); i++) {
                int[] r = {box[0], box[1] + i * ROW - offset, box[2] - 4, ROW - 3};
                if (r[1] + r[3] < box[1] || r[1] > box[1] + box[3]) {
                    continue;
                }
                StarkClient.Suit s = suits.get(i);
                boolean on = s.mark() == suit;
                screen.card(g, r, screen.inside(mouseX, mouseY, r) && mouseY >= box[1]
                        && mouseY <= box[1] + box[3]);
                g.renderItem(s.stack(), r[0] + 2, r[1] + 1);
                screen.text(g, screen.trim(s.name(), r[2] - 26), r[0] + 22, r[1] + 6, on ? t.green() : t.text());
            }
            g.disableScissor();
            screen.scrollbar(g, box, offset, suits.size() * ROW);
        }
        screen.button(g, offButton(area)[0], offButton(area)[1], offButton(area)[2],
                Component.translatable("citylife.jarvis.app.off").getString(), 0xFFB03434, mouseX, mouseY);
        screen.button(g, helpButton(area)[0], helpButton(area)[1], helpButton(area)[2],
                Component.translatable(help ? "citylife.jarvis.app.list" : "citylife.jarvis.app.help")
                        .getString(), t.button(), mouseX, mouseY);
    }

    private void clamp(int[] box, int count) {
        offset = Math.max(0, Math.min(offset, Math.max(0, count * ROW - box[3])));
    }

    @Override
    public boolean scroll(double delta) {
        offset -= (int) Math.signum(delta) * ROW * 2;
        return true;
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (screen.inside(mx, my, offButton(area))) {
            screen.send("jarvis_off");
            return true;
        }
        if (screen.inside(mx, my, helpButton(area))) {
            help = !help;
            return true;
        }
        int[] box = list(area);
        if (help || !state().getBoolean("allowed") || !screen.inside(mx, my, box)) {
            return false;
        }
        int i = (int) ((my - box[1] + offset) / ROW);
        List<StarkClient.Suit> suits = StarkClient.suitItems();
        if (i >= 0 && i < suits.size()) {
            CompoundTag args = new CompoundTag();
            args.putInt("mark", suits.get(i).mark());
            screen.send("jarvis_suit", args);
            return true;
        }
        return false;
    }
}
