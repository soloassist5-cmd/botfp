package dev.lscity.citylife.estate;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.net.Net;
import dev.lscity.citylife.trade.CityTrader;
import dev.lscity.citylife.trade.Shop;
import dev.lscity.citylife.trade.ShopCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Агентство недвижимости: каталог, покупка, продажа, ключи.
 *
 * Покупают только у риелтора: клик по нему открывает экран агентства
 * и запоминает, с кем игрок разговаривает. Сделка проходит, пока игрок
 * стоит рядом с этим риелтором, — из дома через телефон дом не купить.
 */
public final class EstateServer {

    /** С каким риелтором (id сущности) игрок сейчас оформляет сделку. */
    private static final Map<UUID, Integer> SESSIONS = new HashMap<>();
    private static final double DESK_RANGE = 10.0D;

    private EstateServer() {
    }

    /** Клик по риелтору: открыть агентство. focus — подсказка для поиска. */
    public static void open(ServerPlayer player, Entity realtor) {
        SESSIONS.put(player.getUUID(), realtor.getId());
        CompoundTag snapshot = snapshot(player);
        snapshot.putString("focus", focusFor(realtor));
        snapshot.putString("agent", realtor.getCustomName() == null ? ""
                : realtor.getCustomName().getString());
        Net.sendPanel(player, "realty", snapshot, true);
    }

    /**
     * Управдом у многоквартирного дома сразу показывает квартиры своего
     * дома: ищем ближайшую квартиру и берём адрес без номера квартиры.
     */
    private static String focusFor(Entity realtor) {
        Estate.Unit best = null;
        double bestDist = 24.0D * 24.0D;
        for (Estate.Unit unit : Estate.all()) {
            if (!"flat".equals(unit.kind())) {
                continue;
            }
            double d = unit.door().distToCenterSqr(realtor.position());
            if (d < bestDist) {
                bestDist = d;
                best = unit;
            }
        }
        if (best == null) {
            return "";
        }
        String address = best.address();
        int cut = address.lastIndexOf(", кв.");
        return cut > 0 ? address.substring(0, cut) + "," : address;
    }

    public static CompoundTag snapshot(ServerPlayer player) {
        LifeData life = LifeData.get(player.server);
        CompoundTag tag = new CompoundTag();
        tag.putLong("balance", CityData.get(player.server).balance(player.getUUID()));
        tag.putInt("limit", CityConfig.CONFIG.maxHomes.get());
        tag.putInt("sellPercent", CityConfig.CONFIG.homeSellPercent.get());
        ListTag owners = new ListTag();
        life.owners().forEach((unit, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("u", unit);
            entry.putString("n", owner.name());
            boolean mine = owner.id().equals(player.getUUID());
            entry.putBoolean("m", mine);
            if (mine) {
                ListTag keys = new ListTag();
                for (UUID id : life.trusted(unit)) {
                    CompoundTag key = new CompoundTag();
                    key.putUUID("id", id);
                    key.putString("name", life.trustedName(id));
                    keys.add(key);
                }
                entry.put("keys", keys);
            } else if (life.trusted(unit).contains(player.getUUID())) {
                entry.putBoolean("k", true);
            }
            owners.add(entry);
        });
        tag.put("owners", owners);
        return tag;
    }

    private static void sync(ServerPlayer player) {
        Net.sendPanel(player, "realty", snapshot(player), false);
    }

    /** Рядом ли риелтор, с которым открыто окно. */
    private static Entity agent(ServerPlayer player) {
        Integer id = SESSIONS.get(player.getUUID());
        if (id == null) {
            return null;
        }
        Entity entity = player.level().getEntity(id);
        if (entity == null || entity.distanceTo(player) > DESK_RANGE) {
            return null;
        }
        return entity;
    }

    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        Estate.Unit unit = Estate.get(args.getString("id"));
        switch (action) {
            case "realty_buy" -> buy(player, unit);
            case "realty_sell" -> sell(player, unit);
            case "realty_route" -> route(player, unit);
            case "realty_trust" -> trust(player, unit, args.getString("name").trim());
            case "realty_untrust" -> {
                if (unit != null && owns(player, unit) && args.hasUUID("who")) {
                    LifeData.get(player.server).untrust(unit.id(), args.getUUID("who"));
                }
            }
            case "realty_shop" -> {
                Entity entity = agent(player);
                Shop shop = ShopCatalog.BY_ROLE.get("realtor");
                if (entity != null && shop != null) {
                    new CityTrader(entity, shop).open(player);
                }
                return;
            }
            default -> {
            }
        }
        sync(player);
    }

    private static boolean owns(ServerPlayer player, Estate.Unit unit) {
        LifeData.Owner owner = LifeData.get(player.server).owner(unit.id());
        return owner != null && owner.id().equals(player.getUUID());
    }

    private static void say(ServerPlayer player, Component text, ChatFormatting colour) {
        player.displayClientMessage(text.copy().withStyle(colour), false);
    }

    private static void buy(ServerPlayer player, Estate.Unit unit) {
        if (unit == null) {
            return;
        }
        if (agent(player) == null) {
            say(player, Component.translatable("citylife.realty.need_agent"), ChatFormatting.RED);
            return;
        }
        LifeData life = LifeData.get(player.server);
        if (life.owner(unit.id()) != null) {
            say(player, Component.translatable("citylife.realty.taken"), ChatFormatting.RED);
            return;
        }
        int limit = CityConfig.CONFIG.maxHomes.get();
        if (life.ownedBy(player.getUUID()).size() >= limit) {
            say(player, Component.translatable("citylife.realty.limit", limit), ChatFormatting.RED);
            return;
        }
        CityData bank = CityData.get(player.server);
        if (!bank.withdraw(player.getUUID(), unit.price())) {
            say(player, Component.translatable("citylife.realty.no_money",
                    Money.format(unit.price())), ChatFormatting.RED);
            return;
        }
        life.setOwner(unit.id(), player.getUUID(), player.getGameProfile().getName(),
                player.level().getGameTime());
        say(player, Component.translatable("citylife.realty.bought", unit.label(),
                Money.format(unit.price())), ChatFormatting.GREEN);
        say(player, Component.translatable("citylife.realty.bought_hint"), ChatFormatting.GRAY);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        route(player, unit);
    }

    private static void sell(ServerPlayer player, Estate.Unit unit) {
        if (unit == null || !owns(player, unit)) {
            return;
        }
        if (agent(player) == null) {
            say(player, Component.translatable("citylife.realty.need_agent"), ChatFormatting.RED);
            return;
        }
        long back = unit.price() * CityConfig.CONFIG.homeSellPercent.get() / 100L;
        LifeData.get(player.server).clearOwner(unit.id());
        CityData.get(player.server).deposit(player.getUUID(), back);
        say(player, Component.translatable("citylife.realty.sold", unit.label(),
                Money.format(back)), ChatFormatting.GOLD);
    }

    private static void route(ServerPlayer player, Estate.Unit unit) {
        if (unit == null) {
            return;
        }
        Waypoint point = new Waypoint(unit.address(), unit.door().getX(), unit.door().getY(),
                unit.door().getZ(), "home", false);
        CityData.get(player.server).setRoute(player.getUUID(), point);
        Net.sendRoute(player, point);
        player.displayClientMessage(Component.translatable("citylife.nav.started",
                point.name()), true);
    }

    /** Дать ключи: по нику, игрок может быть и не в сети, если уже заходил. */
    public static void trust(ServerPlayer player, Estate.Unit unit, String name) {
        if (unit == null || name.isEmpty() || !owns(player, unit)) {
            return;
        }
        ServerPlayer live = player.server.getPlayerList().getPlayerByName(name);
        UUID target = live != null ? live.getUUID() : player.server.getProfileCache() == null
                ? null : player.server.getProfileCache().get(name)
                .map(com.mojang.authlib.GameProfile::getId).orElse(null);
        if (target == null || target.equals(player.getUUID())) {
            say(player, Component.translatable("citylife.mail.unknown", name), ChatFormatting.RED);
            return;
        }
        String shown = live != null ? live.getGameProfile().getName() : name;
        LifeData.get(player.server).trust(unit.id(), target, shown);
        say(player, Component.translatable("citylife.realty.trusted", shown, unit.address()),
                ChatFormatting.GREEN);
        if (live != null) {
            say(live, Component.translatable("citylife.realty.got_keys",
                    player.getGameProfile().getName(), unit.address()), ChatFormatting.AQUA);
        }
    }

    /** Объекты игрока — для команды /house и телефона. */
    public static List<Estate.Unit> homes(ServerPlayer player) {
        return LifeData.get(player.server).ownedBy(player.getUUID()).stream()
                .map(Estate::get).filter(java.util.Objects::nonNull).toList();
    }

    public static void forget(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }
}
