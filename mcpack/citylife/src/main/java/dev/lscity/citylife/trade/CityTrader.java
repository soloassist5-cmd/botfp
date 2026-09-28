package dev.lscity.citylife.trade;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;

/**
 * Торговец, привязанный к житель-сущности.
 *
 * Сам житель — гуманоид Easy NPC и торговать не умеет, поэтому прилавок
 * держит этот объект: он живёт, пока открыт экран, и знает только цены.
 */
public class CityTrader implements Merchant {

    private final Entity owner;
    private final Shop shop;
    private final MerchantOffers offers;
    private Player customer;

    public CityTrader(Entity owner, Shop shop) {
        this.owner = owner;
        this.shop = shop;
        this.offers = shop.build();
    }

    public Component title() {
        return Component.literal(shop.title());
    }

    public boolean hasGoods() {
        return !offers.isEmpty();
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.customer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return customer;
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers replacement) {
        // Ассортимент задаётся каталогом и не меняется игроком.
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        // Товар в городе не кончается: лавка работает и после сотой покупки.
        offer.resetUses();
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.VILLAGER_YES,
                owner.getSoundSource(), 0.8F, 1.0F);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int value) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return owner.level().isClientSide;
    }

    /** Открыть экран торговли у игрока. */
    public void open(ServerPlayer player) {
        setTradingPlayer(player);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, who) -> new net.minecraft.world.inventory.MerchantMenu(
                        id, inventory, this), title()));
        // Клиенту нужен сам список: без него экран открывается пустым.
        player.sendMerchantOffers(player.containerMenu.containerId, getOffers(), 1,
                getVillagerXp(), showProgressBar(), false);
    }
}
