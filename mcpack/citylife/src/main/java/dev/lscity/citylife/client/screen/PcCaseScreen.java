package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.pc.PcBuild;
import dev.lscity.citylife.pc.PcCaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Окно корпуса: слоты под детали с подписями и живой статус сборки.
 *
 * Статус пересчитывается на клиенте из тех же правил, что и на сервере,
 * поэтому видно сразу: «процессор не подходит к плате», «блок питания
 * слабый» или «компьютер запускается, производительность 140».
 */
@OnlyIn(Dist.CLIENT)
public class PcCaseScreen extends AbstractContainerScreen<PcCaseMenu> {

    private static final String[] LABELS = {"board", "cpu", "cooler", "ram", "ram", "gpu",
            "psu", "storage"};

    public PcCaseScreen(PcCaseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176 + 110;
        imageHeight = 206;
        inventoryLabelY = 112;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        PhoneUi.shadow(g, x, y, imageWidth, imageHeight, 8);
        PhoneUi.roundedGradient(g, x, y, imageWidth, imageHeight, 8, 0xFF2A2E38, 0xFF1A1C22);
        PhoneUi.roundedOutline(g, x, y, imageWidth, imageHeight, 8, 0xFF3C4250);
        // Внутренность корпуса: тёмная панель с «платой».
        PhoneUi.roundedRect(g, x + 6, y + 14, 164, 92, 5, 0xFF12141A);
        for (int i = 0; i < PcCaseMenu.SLOT_XY.length; i++) {
            int sx = x + PcCaseMenu.SLOT_XY[i][0];
            int sy = y + PcCaseMenu.SLOT_XY[i][1];
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF3C4250);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF0B0C10);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 124 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 182);
        }
        PhoneUi.roundedRect(g, x + 176, y + 14, 104, 184, 5, 0xFF12141A);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF3C4250);
        g.fill(x, y, x + 16, y + 16, 0xFF20232B);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 4, 0xFFE8EAF0, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFF9AA0B4, false);
        for (int i = 0; i < PcCaseMenu.SLOT_XY.length; i++) {
            String label = Component.translatable("citylife.pc.slot." + LABELS[i]).getString();
            int cx = PcCaseMenu.SLOT_XY[i][0] + 8;
            int y = PcCaseMenu.SLOT_XY[i][1] + 19;
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(font, label, -font.width(label) / 2, 0, 0xFF9AA0B4, false);
            g.pose().popPose();
        }

        PcBuild.Result build = PcBuild.check(menu.partStacks());
        int x = 182;
        int y = 20;
        g.drawString(font, Component.translatable(build.works()
                ? "citylife.pc.works" : "citylife.pc.not_booting"), x, y,
                build.works() ? 0xFF7BE07B : 0xFFFF6B6B, false);
        y += 14;
        if (build.works()) {
            g.drawString(font, Component.translatable("citylife.pc.score", build.score()), x, y,
                    0xFFE8EAF0, false);
            y += 12;
            g.drawString(font, Component.translatable("citylife.pc.monitor_hint"), x, y,
                    0xFF9AA0B4, false);
        }
        for (Component problem : build.problems()) {
            for (FormattedCharSequence line : font.split(problem, 94)) {
                if (y > 186) {
                    return;
                }
                g.drawString(font, line, x, y, 0xFFFFB0A8, false);
                y += 10;
            }
            y += 2;
        }
        if (build.supply() > 0) {
            g.drawString(font, Component.translatable("citylife.pc.power", build.draw(),
                    build.supply()), x, 188, 0xFF9AA0B4, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
