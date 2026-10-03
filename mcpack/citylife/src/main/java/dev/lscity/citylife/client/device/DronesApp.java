package dev.lscity.citylife.client.device;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * «Дроны»: свои дроны в этом мире — модель, заряд, расстояние, что делает.
 * Тап по дрону — взять управление (телефон работает как пульт), «Домой» —
 * он прилетит и сядет рядом; внизу — «Все домой».
 */
@OnlyIn(Dist.CLIENT)
class DronesApp extends DeviceApp {

    private static final int ROW = 26;
    private int offset;

    DronesApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("drones");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private int[] list(int[] area) {
        return new int[]{area[0], area[1], area[2], area[3] - 22};
    }

    private int[] allButton(int[] area) {
        return new int[]{area[0], area[1] + area[3] - 18, area[2], 16};
    }

    private int homeWidth() {
        return screen.buttonWidth(Component.translatable("citylife.drones.app.home").getString());
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        ListTag drones = screen.list("drones");
        int[] box = list(area);
        if (drones.isEmpty()) {
            int y = box[1] + 8;
            for (var line : screen.font().split(Component.translatable("citylife.drones.app.empty"), box[2])) {
                g.drawString(screen.font(), line, box[0], y, t.dim(), false);
                y += 10;
            }
        } else {
            offset = Math.max(0, Math.min(offset, Math.max(0, drones.size() * ROW - box[3])));
            g.enableScissor(box[0], box[1], box[0] + box[2], box[1] + box[3]);
            int hw = homeWidth();
            for (int i = 0; i < drones.size(); i++) {
                CompoundTag d = drones.getCompound(i);
                int[] r = {box[0], box[1] + i * ROW - offset, box[2] - 4, ROW - 3};
                if (r[1] + r[3] < box[1] || r[1] > box[1] + box[3]) {
                    continue;
                }
                screen.card(g, r, screen.inside(mouseX, mouseY, r) && mouseX < r[0] + r[2] - hw - 6);
                var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("citylife", "drone_" + d.getString("type")));
                if (item != null) {
                    g.renderItem(new ItemStack(item), r[0] + 3, r[1] + 3);
                }
                String name = Component.translatable("item.citylife.drone_" + d.getString("type")).getString();
                screen.text(g, screen.trim(name, r[2] - hw - 30), r[0] + 22, r[1] + 3, t.text());
                float bat = d.getFloat("battery");
                int bc = bat < 0.15F ? t.red() : bat < 0.35F ? 0xFFFFD166 : t.green();
                String status = Component.translatable("citylife.drones.app.status." + d.getString("status")).getString();
                String line = Math.round(bat * 100) + "% · " + d.getInt("dist") + " м · " + status;
                screen.text(g, screen.trim(line, r[2] - hw - 30), r[0] + 22, r[1] + 13,
                        d.getInt("dist") > d.getInt("range") ? t.dim() : bc);
                screen.button(g, r[0] + r[2] - hw - 3, r[1] + 4, 0,
                        Component.translatable("citylife.drones.app.home").getString(), 0xFF2F9C95, mouseX, mouseY);
            }
            g.disableScissor();
            screen.scrollbar(g, box, offset, drones.size() * ROW);
        }
        int[] all = allButton(area);
        screen.button(g, all[0], all[1], all[2], Component.translatable("citylife.drones.app.all_home").getString(),
                t.button(), mouseX, mouseY);
    }

    @Override
    public boolean scroll(double delta) {
        offset -= (int) Math.signum(delta) * ROW;
        return true;
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        if (screen.inside(mx, my, allButton(area))) {
            screen.send("drone_home");
            return true;
        }
        int[] box = list(area);
        if (!screen.inside(mx, my, box)) {
            return false;
        }
        ListTag drones = screen.list("drones");
        int i = (int) ((my - box[1] + offset) / ROW);
        if (i < 0 || i >= drones.size()) {
            return false;
        }
        CompoundTag args = new CompoundTag();
        args.putInt("id", drones.getCompound(i).getInt("id"));
        boolean home = mx >= box[0] + box[2] - 4 - homeWidth() - 6;
        screen.send(home ? "drone_recall" : "drone_fly", args);
        return true;
    }
}
