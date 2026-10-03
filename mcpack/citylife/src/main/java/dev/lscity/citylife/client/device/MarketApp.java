package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.market.Market;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Маркетплейс: видеокарты, комплектующие, гаджеты — с доставкой в постамат.
 *
 * Деньги списываются со счёта в банке, а не из кошелька. Заказ едет пару
 * минут и ждёт в любом пункте выдачи города (они есть в навигаторе).
 */
class MarketApp extends DeviceApp {

    private static final String ORDERS = "orders";
    private static int tab;
    private int scroll;

    MarketApp(DeviceScreen screen) {
        super(screen);
    }

    @Override
    public String title() {
        return screen.appTitle("marketplace");
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    private List<String> tabs() {
        List<String> out = new ArrayList<>(Market.CATEGORIES);
        out.add(ORDERS);
        return out;
    }

    private String tabLabel(String id) {
        return Component.translatable("citylife.market.tab." + id).getString();
    }

    /** Вкладки в две строки, если экран узкий. */
    private List<int[]> tabRects(int[] area) {
        List<int[]> out = new ArrayList<>();
        int x = area[0];
        int y = area[1];
        for (String id : tabs()) {
            int w = screen.font().width(tabLabel(id)) + 10;
            if (x + w > area[0] + area[2]) {
                x = area[0];
                y += 15;
            }
            out.add(new int[]{x, y, w, 13});
            x += w + 3;
        }
        return out;
    }

    private int listTop(int[] area) {
        List<int[]> rects = tabRects(area);
        return rects.get(rects.size() - 1)[1] + 18;
    }

    private static ItemStack stackOf(Market.Offer offer) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(offer.item()));
        return item == null ? ItemStack.EMPTY : new ItemStack(item, offer.count());
    }

    private int rowsFit(int[] area) {
        return Math.max(1, (area[1] + area[3] - listTop(area)) / 24);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        DeviceScreen.Theme t = screen.theme();
        List<String> tabs = tabs();
        List<int[]> rects = tabRects(area);
        for (int i = 0; i < tabs.size(); i++) {
            int[] r = rects.get(i);
            PhoneUi.roundedRect(g, r[0], r[1], r[2], r[3], 6,
                    i == tab ? 0xFF7B2FBF : screen.inside(mouseX, mouseY, r) ? t.cardHover() : t.card());
            screen.text(g, tabLabel(tabs.get(i)), r[0] + 5, r[1] + 3, 0xFFFFFFFF);
        }
        String balance = Money.format(screen.data().getLong("balance"));
        int top = listTop(area);
        if (tabs.get(tab).equals(ORDERS)) {
            renderOrders(g, area, top, t, mouseX, mouseY);
            return;
        }
        List<Market.Offer> offers = Market.in(tabs.get(tab));
        String buy = Component.translatable("citylife.market.buy").getString();
        int bw = screen.buttonWidth(buy);
        for (int i = scroll, shown = 0; i < offers.size() && shown < rowsFit(area); i++, shown++) {
            Market.Offer offer = offers.get(i);
            int[] r = {area[0], top + shown * 24, area[2], 22};
            screen.card(g, r, screen.inside(mouseX, mouseY, r));
            ItemStack stack = stackOf(offer);
            g.renderItem(stack, r[0] + 3, r[1] + 3);
            String name = stack.getHoverName().getString() + (offer.count() > 1 ? " ×" + offer.count() : "");
            screen.text(g, screen.trim(name, r[2] - bw - 34), r[0] + 23, r[1] + 3, t.text());
            screen.text(g, Money.format(offer.price()), r[0] + 23, r[1] + 12, 0xFFFFD166);
            screen.button(g, r[0] + r[2] - bw - 3, r[1] + 3, 0, buy, 0xFF7B2FBF, mouseX, mouseY);
        }
        screen.fitted(g, Component.translatable("citylife.market.balance", balance).getString(),
                area[0] + area[2] / 2, area[1] + area[3] - 8, area[2], t.dim());
    }

    private void renderOrders(GuiGraphics g, int[] area, int top, DeviceScreen.Theme t, int mouseX, int mouseY) {
        ListTag orders = screen.list("orders");
        if (orders.isEmpty()) {
            screen.empty(g, area, "citylife.market.no_orders");
            return;
        }
        for (int i = scroll, shown = 0; i < orders.size() && shown < rowsFit(area); i++, shown++) {
            CompoundTag order = orders.getCompound(i);
            int[] r = {area[0], top + shown * 24, area[2], 22};
            screen.card(g, r, false);
            screen.text(g, screen.trim(order.getString("title"), r[2] - 12), r[0] + 6, r[1] + 3,
                    t.text());
            String status = order.getString("status");
            String line = switch (status) {
                case "ready" -> Component.translatable("citylife.market.status.ready").getString();
                case "taken" -> Component.translatable("citylife.market.status.taken").getString();
                default -> Component.translatable("citylife.market.status.wait",
                        order.getLong("seconds") / 60, order.getLong("seconds") % 60).getString();
            };
            int colour = switch (status) {
                case "ready" -> t.green();
                case "taken" -> t.dim();
                default -> 0xFFFFD166;
            };
            screen.text(g, line, r[0] + 6, r[1] + 12, colour);
            if (status.equals("ready")) {
                // Готовый заказ можно не забирать самому: привезёт курьерский дрон.
                String drone = Component.translatable("citylife.market.drone").getString();
                int bw = screen.buttonWidth(drone);
                screen.button(g, r[0] + r[2] - bw - 3, r[1] + 3, 0, drone, 0xFF2F9C95, mouseX, mouseY);
            }
        }
        screen.fitted(g, Component.translatable("citylife.market.pickup_hint").getString(),
                area[0] + area[2] / 2, area[1] + area[3] - 8, area[2], t.dim());
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        List<int[]> rects = tabRects(area);
        for (int i = 0; i < rects.size(); i++) {
            if (screen.inside(mx, my, rects.get(i))) {
                tab = i;
                scroll = 0;
                return true;
            }
        }
        List<String> tabs = tabs();
        if (tabs.get(tab).equals(ORDERS)) {
            ListTag orders = screen.list("orders");
            int bw = screen.buttonWidth(Component.translatable("citylife.market.drone").getString());
            int top = listTop(area);
            for (int i = scroll, shown = 0; i < orders.size() && shown < rowsFit(area); i++, shown++) {
                int[] button = {area[0] + area[2] - bw - 3, top + shown * 24 + 3, bw, 16};
                if (orders.getCompound(i).getString("status").equals("ready") && screen.inside(mx, my, button)) {
                    CompoundTag args = new CompoundTag();
                    args.putInt("order", orders.getCompound(i).getInt("id"));
                    screen.send("market_drone", args);
                    return true;
                }
            }
            return false;
        }
        List<Market.Offer> offers = Market.in(tabs.get(tab));
        int bw = screen.buttonWidth(Component.translatable("citylife.market.buy").getString());
        int top = listTop(area);
        for (int i = scroll, shown = 0; i < offers.size() && shown < rowsFit(area); i++, shown++) {
            int[] button = {area[0] + area[2] - bw - 3, top + shown * 24 + 3, bw, 16};
            if (screen.inside(mx, my, button)) {
                CompoundTag args = new CompoundTag();
                args.putString("offer", offers.get(i).id());
                screen.send("market_order", args);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(double delta) {
        List<String> tabs = tabs();
        int size = tabs.get(tab).equals(ORDERS) ? screen.list("orders").size()
                : Market.in(tabs.get(tab)).size();
        scroll = Math.max(0, Math.min(Math.max(0, size - 3), scroll - (int) Math.signum(delta)));
        return true;
    }
}
