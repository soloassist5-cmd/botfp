package dev.lscity.citylife.client.screen;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.net.Net;
import dev.lscity.citylife.stark.Printer;
import dev.lscity.citylife.stark.PrinterBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Каталог 3D-принтера: все предметы всех модов с поиском и вкладками.
 * Выбрал, задал количество, «Печать» — и принтер строит предмет слоями.
 */
@OnlyIn(Dist.CLIENT)
public class PrinterScreen extends Screen {

    private static final int CYAN = 0xFF57D8FF;
    private static final int TEXT = 0xFFE8FBFF;
    private static final int DIM = 0xFF7FA6B8;
    private static final int RED = 0xFFFF4A4A;
    private static final int CELL = 20;

    /** Вкладка «Старк»: телефон, техника башни и любимые марки брони. */
    private static final String[] FEATURED = {"citylife:phone_stark", "citylife:holo_screen",
            "citylife:laser_sensor", "citylife:security_console", "sym_industries:mark_17_chestplate",
            "sym_industries:mark_25_chestplate", "sym_industries:mark_5_suitcase", "sym_industries:mark_3_chestplate",
            "sym_industries:mark_33_chestplate", "sym_industries:mark_39_chestplate",
            "sym_industries:arc_reactor_tier_1", "sym_industries:arc_reactor_tier_2", "sym_industries:repulsor",
            "sym_industries:rocket", "sym_industries:jarvis_module", "sym_industries:display_case",
            "sym_industries:stark_computer", "sym_industries:titanium_ingot"};
    private static final String[] TABS = {"stark", "all", "blocks", "items", "suits"};

    private final BlockPos pos;
    private final boolean cleared;
    private final List<ItemStack> all = new ArrayList<>();
    private final List<ItemStack> shown = new ArrayList<>();
    private EditBox search;
    private String tab = "stark";
    private int scroll;
    private ItemStack selected = ItemStack.EMPTY;
    private int count = 1;
    private Button print;

    public PrinterScreen(CompoundTag data) {
        super(Component.translatable("citylife.printer.title"));
        this.pos = BlockPos.of(data.getLong("pos"));
        this.cleared = data.getBoolean("cleared");
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (Printer.allowed(item)) {
                all.add(new ItemStack(item));
            }
        }
    }

    private int w() {
        return Math.min(width - 16, 440);
    }

    private int h() {
        return Math.min(height - 16, 260);
    }

    private int gridX() {
        return (width - w()) / 2 + 10;
    }

    private int gridY() {
        return (height - h()) / 2 + 62;
    }

    private int cols() {
        return Math.max(4, (w() - 170) / CELL);
    }

    private int rows() {
        return Math.max(2, (h() - 72) / CELL);
    }

    @Override
    protected void init() {
        int x = (width - w()) / 2;
        int y = (height - h()) / 2;
        String old = search == null ? "" : search.getValue();
        search = new EditBox(font, x + 10, y + 26, cols() * CELL, 14, Component.translatable("citylife.printer.search"));
        search.setValue(old);
        search.setHint(Component.translatable("citylife.printer.search"));
        search.setResponder(s -> {
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        int tx = x + 10;
        for (String id : TABS) {
            Component label = Component.translatable("citylife.printer.tab." + id);
            int tw = font.width(label) + 10;
            Button button = Button.builder(label, b -> {
                tab = id;
                scroll = 0;
                filter();
                rebuildWidgets();
            }).bounds(tx, y + 43, tw, 14).build();
            button.active = !tab.equals(id);
            addRenderableWidget(button);
            tx += tw + 3;
        }
        int px = x + w() - 150;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> count = Math.max(1, count - 1))
                .bounds(px, y + 150, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> count = Math.min(Printer.MAX_COUNT, count + 1))
                .bounds(px + 64, y + 150, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("×16"), b -> count = 16)
                .bounds(px + 88, y + 150, 26, 16).build());
        addRenderableWidget(Button.builder(Component.literal("×64"), b -> count = Printer.MAX_COUNT)
                .bounds(px + 116, y + 150, 26, 16).build());
        print = Button.builder(Component.translatable("citylife.printer.print"), b -> {
            if (selected.isEmpty()) {
                return;
            }
            CompoundTag args = new CompoundTag();
            args.putLong("pos", pos.asLong());
            args.putString("item", String.valueOf(ForgeRegistries.ITEMS.getKey(selected.getItem())));
            args.putInt("count", Math.min(count, Printer.MAX_COUNT));
            Net.sendAction("printer_print", args);
        }).bounds(px, y + 172, 142, 22).build();
        addRenderableWidget(print);
        filter();
    }

    private void filter() {
        shown.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (tab.equals("stark") && q.isEmpty()) {
            for (String id : FEATURED) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
                if (item != null && Printer.allowed(item)) {
                    shown.add(new ItemStack(item));
                }
            }
            return;
        }
        for (ItemStack stack : all) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            String path = id == null ? "" : id.toString();
            boolean block = stack.getItem() instanceof BlockItem;
            boolean suit = dev.lscity.citylife.stark.StarkSuits.isSuit(id);
            boolean ok = switch (tab) {
                case "blocks" -> block;
                case "items" -> !block;
                case "suits" -> suit;
                default -> true;
            };
            if (ok && (q.isEmpty() || path.contains(q)
                    || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q))) {
                shown.add(stack);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, (shown.size() + cols() - 1) / cols() - rows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = cellAt(mx, my);
        if (i >= 0) {
            selected = shown.get(i).copy();
            count = Math.min(count, Math.max(1, selected.getMaxStackSize() * 4));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private int cellAt(double mx, double my) {
        int col = (int) ((mx - gridX()) / CELL);
        int row = (int) ((my - gridY()) / CELL);
        if (mx < gridX() || my < gridY() || col >= cols() || row >= rows()) {
            return -1;
        }
        int i = (row + scroll) * cols() + col;
        return i < shown.size() ? i : -1;
    }

    private PrinterBlockEntity printer() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof PrinterBlockEntity be ? be : null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int x = (width - w()) / 2;
        int y = (height - h()) / 2;
        PhoneUi.roundedRect(g, x, y, w(), h(), 8, 0xF2061420);
        PhoneUi.roundedOutline(g, x, y, w(), h(), 8, PhoneUi.alpha(CYAN, 0.8F));
        g.drawString(font, title, x + 10, y + 9, CYAN, false);
        String sub = Component.translatable("citylife.printer.subtitle", all.size()).getString();
        g.drawString(font, sub, x + w() - 10 - font.width(sub), y + 9, DIM, false);
        g.fill(x + 8, y + 21, x + w() - 8, y + 22, 0x8057D8FF);

        // Сетка предметов.
        int gx = gridX();
        int gy = gridY();
        int hover = cellAt(mouseX, mouseY);
        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < cols(); c++) {
                int i = (r + scroll) * cols() + c;
                int cx = gx + c * CELL;
                int cy = gy + r * CELL;
                boolean sel = i < shown.size() && !selected.isEmpty()
                        && ItemStack.isSameItem(shown.get(i), selected);
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, sel ? 0x6057D8FF : i == hover ? 0x40FFFFFF : 0x22000000);
                if (i < shown.size()) {
                    g.renderItem(shown.get(i), cx + 1, cy + 1);
                }
            }
        }
        int total = (shown.size() + cols() - 1) / cols();
        if (total > rows()) {
            int barH = rows() * CELL;
            int knob = Math.max(10, barH * rows() / total);
            int ky = gy + (barH - knob) * scroll / Math.max(1, total - rows());
            g.fill(gx + cols() * CELL + 1, gy, gx + cols() * CELL + 3, gy + barH, 0x3357D8FF);
            g.fill(gx + cols() * CELL + 1, ky, gx + cols() * CELL + 3, ky + knob, CYAN);
        }
        if (shown.isEmpty()) {
            g.drawString(font, Component.translatable("citylife.printer.nothing"), gx, gy + 4, DIM, false);
        }

        // Правая панель: выбранный предмет крупно.
        int px = x + w() - 150;
        PhoneUi.roundedRect(g, px, y + 26, 142, 118, 6, 0x40000000);
        if (!selected.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(px + 71 - 24, y + 34, 0);
            g.pose().scale(3F, 3F, 1F);
            g.renderItem(selected, 0, 0);
            g.pose().popPose();
            int ny = y + 90;
            for (var part : font.split(selected.getHoverName(), 134)) {
                g.drawCenteredString(font, part, px + 71, ny, TEXT);
                ny += 10;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(selected.getItem());
            g.drawCenteredString(font, font.plainSubstrByWidth(String.valueOf(id), 134), px + 71, ny + 2, DIM);
        } else {
            g.drawCenteredString(font, Component.translatable("citylife.printer.pick"), px + 71, y + 80, DIM);
        }
        g.drawCenteredString(font, String.valueOf(count), px + 42, y + 154, TEXT);

        PrinterBlockEntity be = printer();
        boolean busy = be != null && be.busy();
        print.active = cleared && !selected.isEmpty() && !busy;
        int status = y + 200;
        if (!cleared) {
            for (var part : font.split(Component.translatable("citylife.printer.no_access"), 142)) {
                g.drawString(font, part, px, status, RED, false);
                status += 10;
            }
        } else if (busy) {
            float p = be.progress(partial);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("citylife.printer.printing",
                    be.job().getHoverName()).getString(), 142), px, status, TEXT, false);
            g.fill(px, status + 12, px + 142, status + 18, 0x4057D8FF);
            g.fill(px, status + 12, px + (int) (142 * p), status + 18, CYAN);
            g.drawString(font, (int) (p * 100) + "%", px + 146 - 30, status + 21, DIM, false);
        } else {
            g.drawString(font, Component.translatable("citylife.printer.ready"), px, status, DIM, false);
        }
        super.render(g, mouseX, mouseY, partial);
        if (hover >= 0) {
            g.renderTooltip(font, shown.get(hover), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
