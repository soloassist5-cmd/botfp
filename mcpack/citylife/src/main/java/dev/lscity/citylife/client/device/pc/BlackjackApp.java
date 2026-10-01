package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.client.ui.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Блэкджек на фишки (не на рубли): взять, хватит, удвоить; дилер добирает до 17. */
class BlackjackApp extends PcApp {

    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "В", "Д", "К", "Т"};
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    private final List<Integer> deck = new ArrayList<>();
    private final List<Integer> mine = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private static int chips = 1000;
    private int bet = 50;
    private boolean playing;
    private String status = "Ставка 50 фишек — «Раздать»";

    BlackjackApp(DeviceScreen screen) {
        super(screen, "blackjack");
    }

    private int draw() {
        if (deck.isEmpty()) {
            for (int i = 0; i < 52; i++) {
                deck.add(i);
            }
            Collections.shuffle(deck);
        }
        return deck.remove(deck.size() - 1);
    }

    static int score(List<Integer> hand) {
        int total = 0;
        int aces = 0;
        for (int card : hand) {
            int rank = card % 13;
            total += rank == 12 ? 11 : rank >= 9 ? 10 : rank + 2;
            aces += rank == 12 ? 1 : 0;
        }
        while (total > 21 && aces-- > 0) {
            total -= 10;
        }
        return total;
    }

    private void deal() {
        mine.clear();
        dealer.clear();
        mine.add(draw());
        dealer.add(draw());
        mine.add(draw());
        dealer.add(draw());
        playing = true;
        status = "Взять ещё или хватит?";
        if (score(mine) == 21) {
            finish();
        }
    }

    private void finish() {
        playing = false;
        while (score(dealer) < 17) {
            dealer.add(draw());
        }
        int me = score(mine);
        int them = score(dealer);
        if (me > 21) {
            chips -= bet;
            status = "Перебор: −" + bet;
        } else if (them > 21 || me > them) {
            int win = me == 21 && mine.size() == 2 ? bet * 3 / 2 : bet;
            chips += win;
            status = "Победа: +" + win;
        } else if (me == them) {
            status = "Ничья";
        } else {
            chips -= bet;
            status = "Дилер выиграл: −" + bet;
        }
        if (chips <= 0) {
            chips = 1000;
            status += " · фишки закончились, выдано 1000";
        }
        bet = Math.min(50, Math.max(10, chips));
    }

    private void hand(GuiGraphics g, List<Integer> cards, int x, int y, boolean hideSecond) {
        for (int i = 0; i < cards.size(); i++) {
            int cx = x + i * 26;
            PhoneUi.roundedRect(g, cx, y, 24, 34, 3, 0xFFF4F4F4);
            if (hideSecond && i == 1) {
                PhoneUi.roundedRect(g, cx + 2, y + 2, 20, 30, 2, 0xFF2F55D6);
                continue;
            }
            int card = cards.get(i);
            int colour = card / 13 == 1 || card / 13 == 2 ? 0xFFC02B3F : 0xFF15171F;
            screen.text(g, RANKS[card % 13], cx + 3, y + 3, colour);
            screen.text(g, SUITS[card / 13], cx + 12, y + 22, colour);
        }
    }

    private int[] button(int[] area, int i) {
        return Kit.cell(area[0], area[1] + area[3] - 20, area[2], 20, 4, 1, i, 5);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        g.fill(area[0], area[1], area[0] + area[2], area[1] + area[3] - 24, 0xFF14462C);
        screen.text(g, "Дилер" + (playing ? "" : ": " + score(dealer)), area[0] + 6, area[1] + 4, 0xFFEDEFF7);
        hand(g, dealer, area[0] + 6, area[1] + 16, playing);
        screen.text(g, "Вы: " + (mine.isEmpty() ? "—" : score(mine)), area[0] + 6, area[1] + 58, 0xFFEDEFF7);
        hand(g, mine, area[0] + 6, area[1] + 70, false);
        int tx = area[0] + area[2] - 130;
        screen.text(g, "Фишки: " + chips, tx, area[1] + 4, 0xFFFFE27A);
        screen.text(g, "Ставка: " + bet, tx, area[1] + 16, 0xFFEDEFF7);
        Kit.wrap(g, screen, status, tx, area[1] + 32, 126, 0xFFEDEFF7, area[1] + 80);
        String[] labels = playing ? new String[]{"Взять", "Хватит", "Удвоить", ""}
                : new String[]{"Раздать", "Ставка −", "Ставка +", ""};
        for (int i = 0; i < 3; i++) {
            Kit.tile(g, screen, button(area, i), labels[i], i == 0 ? 0xFF1F8F57 : Kit.TILE, mouseX, mouseY);
        }
    }

    @Override
    public boolean click(double mx, double my, int[] area) {
        int pressed = -1;
        for (int i = 0; i < 3; i++) {
            if (Kit.inside(mx, my, button(area, i))) {
                pressed = i;
            }
        }
        if (pressed < 0) {
            return false;
        }
        if (!playing) {
            if (pressed == 0) {
                deal();
            } else {
                bet = Math.max(10, Math.min(chips, bet + (pressed == 1 ? -10 : 10)));
            }
        } else if (pressed == 0) {
            mine.add(draw());
            if (score(mine) >= 21) {
                finish();
            }
        } else if (pressed == 1) {
            finish();
        } else if (mine.size() == 2 && chips >= bet * 2) {
            bet *= 2;
            mine.add(draw());
            finish();
        }
        return true;
    }
}
