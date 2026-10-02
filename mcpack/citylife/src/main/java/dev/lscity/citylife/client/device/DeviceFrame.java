package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.device.DeviceModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Корпус гаджета: размеры, число колонок на рабочем столе и отрисовка.
 *
 * Экран у всех устройств один, а корпус разный — это и делает «Нокту»
 * кнопочной звонилкой, «Фолд» широким, а ноутбук ноутбуком.
 */
@OnlyIn(Dist.CLIENT)
public final class DeviceFrame {

    public enum Style { PHONE, KEYPAD, FOLD, TABLET, LAPTOP, MONITOR, GLASS }

    public final Style style;
    public final int baseW;
    public final int baseH;
    public final int columns;
    public final int icon;
    public final int body;

    // Положение на экране после масштабирования под окно.
    public int x;
    public int y;
    public int w;
    public int h;

    private DeviceFrame(Style style, int baseW, int baseH, int columns, int icon, int body) {
        this.style = style;
        this.baseW = baseW;
        this.baseH = baseH;
        this.columns = columns;
        this.icon = icon;
        this.body = body;
    }

    public static DeviceFrame of(DeviceModel model) {
        int body = model.frame();
        return switch (model.kind()) {
            case TABLET -> new DeviceFrame(Style.TABLET, 420, 290, 6, 36, body);
            case LAPTOP -> new DeviceFrame(Style.LAPTOP, 440, 300, 7, 34, body);
            case COMPUTER -> new DeviceFrame(Style.MONITOR, 460, 300, 7, 36, body);
            default -> switch (model.id()) {
                case "phone_nokta" -> new DeviceFrame(Style.KEYPAD, 150, 320, 2, 30, body);
                case "phone_fold" -> new DeviceFrame(Style.FOLD, 280, 340, 4, 34, body);
                case "phone_mini" -> new DeviceFrame(Style.PHONE, 164, 300, 3, 30, body);
                case "phone_stark" -> new DeviceFrame(Style.GLASS, 188, 340, 3, 34, body);
                default -> new DeviceFrame(Style.PHONE, 188, 340, 3, 34, body);
            };
        };
    }

    /** Вписать корпус в окно игры: уменьшаем пропорционально, но не увеличиваем. */
    public void layout(int screenW, int screenH) {
        float scale = Math.min(1F, Math.min((screenH - 16F) / baseH, (screenW - 16F) / baseW));
        w = Math.max(120, Math.round(baseW * scale));
        h = Math.max(160, Math.round(baseH * scale));
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;
    }

    /** Экран внутри корпуса: x, y, ширина, высота. */
    public int[] screen() {
        return switch (style) {
            case KEYPAD -> new int[]{x + 12, y + 26, w - 24, h * 52 / 100};
            case LAPTOP -> new int[]{x + 10, y + 10, w - 20, h - 58};
            case MONITOR -> new int[]{x + 7, y + 7, w - 14, h - 50};
            case TABLET -> new int[]{x + 12, y + 12, w - 24, h - 24};
            default -> new int[]{x + 6, y + 6, w - 12, h - 12};
        };
    }

    public int screenRadius() {
        return switch (style) {
            case KEYPAD, LAPTOP, MONITOR -> 3;
            case TABLET -> 9;
            default -> 13;
        };
    }

    public void draw(GuiGraphics g) {
        int rim = PhoneUi.lerp(body, 0xFFFFFFFF, 0.18F);
        if (style == Style.GLASS) {
            drawGlass(g);
            return;
        }
        switch (style) {
            case LAPTOP -> {
                int lidH = h - 40;
                PhoneUi.shadow(g, x, y, w, h, 12);
                PhoneUi.roundedRect(g, x, y, w, lidH, 10, body);
                PhoneUi.roundedOutline(g, x, y, w, lidH, 10, rim);
                // Шарнир и корпус с клавиатурой — трапеция, расширяющаяся к нам.
                g.fill(x + 20, y + lidH, x + w - 20, y + lidH + 3, 0xFF0A0B0E);
                for (int row = 0; row < 36; row++) {
                    int inset = 18 - row / 2;
                    g.fill(x + inset, y + lidH + 3 + row, x + w - inset, y + lidH + 4 + row,
                            PhoneUi.lerp(rim, body, row / 36F));
                }
                int keysY = y + lidH + 8;
                for (int row = 0; row < 3; row++) {
                    for (int key = 0; key < 18; key++) {
                        int kx = x + 40 + key * (w - 80) / 18;
                        g.fill(kx, keysY + row * 7, kx + (w - 80) / 18 - 2, keysY + row * 7 + 5,
                                0x66000000);
                    }
                }
                PhoneUi.roundedRect(g, x + w / 2 - 30, y + h - 12, 60, 8, 3, 0x33000000);
            }
            case MONITOR -> {
                int panelH = h - 36;
                PhoneUi.shadow(g, x, y, w, panelH, 6);
                PhoneUi.roundedRect(g, x, y, w, panelH, 5, 0xFF101114);
                PhoneUi.roundedOutline(g, x, y, w, panelH, 5, 0xFF2B2F3A);
                g.fill(x + w / 2 - 8, y + panelH, x + w / 2 + 8, y + h - 10, 0xFF22252E);
                PhoneUi.roundedRect(g, x + w / 2 - 60, y + h - 12, 120, 10, 4, 0xFF2B2F3A);
                PhoneUi.disc(g, x + w - 16, y + panelH - 4, 1, 0xFF7BE07B);
            }
            case KEYPAD -> {
                PhoneUi.shadow(g, x, y, w, h, 22);
                PhoneUi.roundedGradient(g, x, y, w, h, 22, rim, body);
                PhoneUi.roundedOutline(g, x, y, w, h, 22, PhoneUi.lerp(rim, 0xFFFFFFFF, 0.2F));
                PhoneUi.roundedRect(g, x + w / 2 - 16, y + 10, 32, 4, 2, 0x88000000);
                int[] s = screen();
                int top = s[1] + s[3] + 12;
                PhoneUi.roundedRect(g, x + w / 2 - 22, top, 44, 18, 8, 0xFF2A3440);
                PhoneUi.roundedRect(g, x + 14, top + 2, 22, 12, 5, 0xFF2F8F4E);
                PhoneUi.roundedRect(g, x + w - 36, top + 2, 22, 12, 5, 0xFFB03434);
                int keyTop = top + 26;
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 3; col++) {
                        int kw = (w - 36) / 3;
                        PhoneUi.roundedRect(g, x + 14 + col * (kw + 4), keyTop + row * 16,
                                kw, 12, 4, 0xFF3A4652);
                    }
                }
            }
            default -> {
                int radius = style == Style.TABLET ? 16 : 18;
                PhoneUi.shadow(g, x, y, w, h, radius);
                PhoneUi.roundedRect(g, x, y, w, h, radius, body);
                PhoneUi.roundedOutline(g, x, y, w, h, radius, rim);
                if (style == Style.FOLD) {
                    // Сгиб посередине: тонкая тень поперёк экрана.
                    g.fill(x + w / 2, y + 2, x + w / 2 + 1, y + h - 2, 0x55000000);
                }
                if (style != Style.TABLET) {
                    g.fill(x + w, y + 60, x + w + 2, y + 96, rim);
                    g.fill(x - 2, y + 70, x, y + 96, rim);
                }
            }
        }
        int[] s = screen();
        PhoneUi.roundedRect(g, s[0], s[1], s[2], s[3], screenRadius(), 0xFF05070C);
    }

    /**
     * Стеклянный телефон Старка: корпуса почти нет — пластина прозрачного
     * стекла с голубой кромкой, бликом наискосок и светящимся «реактором»
     * камеры. Под экраном нет подложки, сквозь него видно мир.
     */
    private void drawGlass(GuiGraphics g) {
        int radius = 18;
        long now = System.currentTimeMillis();
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 600.0);
        PhoneUi.roundedRect(g, x, y, w, h, radius, 0x269FDCFF);
        PhoneUi.roundedOutline(g, x, y, w, h, radius, PhoneUi.alpha(0xFF9BE8FF, 0.75F + 0.25F * pulse));
        PhoneUi.roundedOutline(g, x + 2, y + 2, w - 4, h - 4, radius - 2, 0x5584E1FF);
        // Блик: светлая полоса наискосок через всё стекло.
        for (int i = 0; i < h; i++) {
            int bx = x + w - 30 - i * 2 / 3;
            if (bx > x + 8 && bx + 14 < x + w - 4) {
                g.fill(bx, y + i, bx + 14, y + i + 1, 0x12FFFFFF);
                g.fill(bx + 18, y + i, bx + 22, y + i + 1, 0x0CFFFFFF);
            }
        }
        // Кнопки на торцах — тонкие светящиеся риски.
        g.fill(x + w, y + 64, x + w + 1, y + 96, 0x999BE8FF);
        g.fill(x - 1, y + 72, x, y + 92, 0x999BE8FF);
        // Камера-«реактор» сверху и тонкая полоска динамика.
        int cx = x + w / 2;
        PhoneUi.ring(g, cx, y + 12, 4, 1, PhoneUi.alpha(0xFF9BE8FF, 0.6F + 0.4F * pulse));
        PhoneUi.disc(g, cx, y + 12, 2, 0xFFE6FBFF);
        PhoneUi.roundedRect(g, cx - 18, y + 4, 10, 2, 1, 0x669BE8FF);
        PhoneUi.roundedRect(g, cx + 8, y + 4, 10, 2, 1, 0x669BE8FF);
        int[] s = screen();
        PhoneUi.roundedOutline(g, s[0], s[1], s[2], s[3], screenRadius(), 0x339BE8FF);
    }

    public boolean glass() {
        return style == Style.GLASS;
    }

    /** У кнопочной «Нокты» экран монохромный: зелёная подсветка вместо обоев. */
    public boolean monochrome() {
        return style == Style.KEYPAD;
    }

    /** У ноутбука и монитора вместо полоски «домой» — панель задач. */
    public boolean taskbar() {
        return style == Style.LAPTOP || style == Style.MONITOR;
    }
}
