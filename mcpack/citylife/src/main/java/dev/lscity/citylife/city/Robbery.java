package dev.lscity.citylife.city;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Mail;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.estate.Estate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ограбления: касса магазина и банкомат.
 *
 * Касса: присесть и кликнуть по продавцу с оружием в руке. Продавец жмёт
 * тревожную кнопку — сразу две звезды розыска, полиция выезжает к магазину.
 * Грабитель должен простоять у кассы 30 секунд, не отходя дальше 7 блоков;
 * тогда выручка уходит ему наличными. Если магазин — чей-то бизнес,
 * хозяину приходит сообщение (или письмо, если его нет в игре).
 *
 * Банкомат: присесть и кликнуть по нему отмычкой. Дольше (45 секунд),
 * три звезды и больше денег.
 *
 * После ограбления касса и банкомат пустеют на 20 минут. Ушёл, погиб или
 * задержан — ограбление сорвалось, звёзды остаются.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Robbery {

    /** Насколько можно отойти от кассы, пока идёт ограбление. */
    public static final double RADIUS = 7.0D;

    /** Идущее ограбление. key — что грабят (касса продавца или банкомат). */
    private static final class Heist {
        final ServerPlayer robber;
        final String kind;
        final String key;
        final String place;
        final Vec3 spot;
        final long start;
        final long end;
        final long loot;

        Heist(ServerPlayer robber, String kind, String key, String place, Vec3 spot, long start,
              long end, long loot) {
            this.robber = robber;
            this.kind = kind;
            this.key = key;
            this.place = place;
            this.spot = spot;
            this.start = start;
            this.end = end;
            this.loot = loot;
        }
    }

    private static final Map<UUID, Heist> ACTIVE = new LinkedHashMap<>();
    /** Когда касса или банкомат снова будут с деньгами (игровое время). */
    private static final Map<String, Long> EMPTY_UNTIL = new HashMap<>();
    /** Награбленное, которое полиция изымет при задержании, пока розыск не сошёл. */
    private static final Map<UUID, Long> STOLEN = new HashMap<>();

    private Robbery() {
    }

    /** Оружие в руке: ствол TaCZ, меч или топор. */
    public static boolean armed(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "tacz".equals(id.getNamespace());
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Ограбить кассу продавца. false — если ограбление не началось. */
    public static boolean robShop(ServerPlayer player, Entity clerk, String title) {
        String key = "shop:" + clerk.getUUID();
        Vec3 spot = clerk.position();
        int seconds = CityConfig.CONFIG.shopRobSeconds.get();
        long loot = loot(player, CityConfig.CONFIG.shopRobLoot.get());
        if (!begin(player, "shop", key, title, spot, seconds, loot)) {
            return false;
        }
        Wanted.crime(player, 2, "citylife.wanted.robbery");
        clerk.level().playSound(null, clerk.blockPosition(), SoundEvents.BELL_BLOCK,
                SoundSource.NEUTRAL, 1.5F, 1.6F);
        Component name = clerk.getCustomName() != null ? clerk.getCustomName()
                : clerk.getType().getDescription();
        player.sendSystemMessage(Component.empty()
                .append(name.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("citylife.rob.clerk").withStyle(ChatFormatting.WHITE)));
        warnOwner(player.server, clerk.blockPosition(), title);
        raise(player, spot, title);
        return true;
    }

    /** Взломать банкомат отмычкой. */
    public static boolean robAtm(ServerPlayer player, BlockPos atm) {
        String key = "atm:" + atm.asLong();
        String place = Texts.ru("citylife.rob.atm_place");
        Vec3 spot = Vec3.atCenterOf(atm);
        int seconds = CityConfig.CONFIG.atmRobSeconds.get();
        long loot = loot(player, CityConfig.CONFIG.atmRobLoot.get());
        if (!begin(player, "atm", key, place, spot, seconds, loot)) {
            return false;
        }
        Wanted.crime(player, 3, "citylife.wanted.atm");
        player.level().playSound(null, atm, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5F, 1.9F);
        raise(player, spot, place);
        return true;
    }

    /** Случайная выручка: от половины до полной суммы из настроек. */
    private static long loot(ServerPlayer player, int max) {
        int half = Math.max(1, max / 2);
        return half + player.getRandom().nextInt(Math.max(1, max - half + 1));
    }

    private static boolean begin(ServerPlayer player, String kind, String key, String place,
                                 Vec3 spot, int seconds, long loot) {
        long now = player.level().getGameTime();
        if (ACTIVE.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("citylife.rob.busy")
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        if (LifeData.get(player.server).jailUntil(player.getUUID()) > now) {
            return false;
        }
        for (Heist heist : ACTIVE.values()) {
            if (heist.key.equals(key)) {
                player.displayClientMessage(Component.translatable("citylife.rob.taken")
                        .withStyle(ChatFormatting.YELLOW), false);
                return false;
            }
        }
        long ready = EMPTY_UNTIL.getOrDefault(key, 0L);
        if (ready > now) {
            player.displayClientMessage(Component.translatable("citylife.rob.empty",
                    (ready - now) / 1200 + 1).withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        ACTIVE.put(player.getUUID(), new Heist(player, kind, key, place, spot, now,
                now + seconds * 20L, loot));
        player.sendSystemMessage(Component.translatable("citylife.rob.started", place, seconds,
                (int) RADIUS).withStyle(ChatFormatting.RED));
        CityLife.LOG.info("City Life: {} грабит {} ({})", player.getGameProfile().getName(), place, key);
        return true;
    }

    /** Тревога: полиция едет к месту, город узнаёт из новостей. */
    private static void raise(ServerPlayer player, Vec3 spot, String place) {
        Emergency.alarm(player, spot, place);
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                "citylife.rob.news", place, (int) spot.x, (int) spot.z)
                .withStyle(ChatFormatting.RED), false);
    }

    /** Хозяин бизнеса узнаёт, что его магазин грабят. */
    private static void warnOwner(MinecraftServer server, BlockPos at, String title) {
        Estate.Unit unit = Estate.businessAt(at);
        if (unit == null) {
            return;
        }
        LifeData.Owner owner = LifeData.get(server).owner(unit.id());
        if (owner == null) {
            return;
        }
        String text = Texts.ru("citylife.rob.owner", unit.label(), title);
        ServerPlayer online = server.getPlayerList().getPlayer(owner.id());
        if (online != null) {
            online.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
        } else {
            CityData.get(server).deliverMail(owner.id(), new Mail(Emergency.CITY,
                    Texts.ru("citylife.rob.owner_from"), text, server.overworld().getGameTime(), false));
        }
    }

    /**
     * Проверить ограбление игрока на момент now: ушёл — сорвалось, время
     * вышло — деньги. Раз в секунду это делает сервер, тесты зовут напрямую.
     */
    public static void check(ServerPlayer player, long now) {
        Heist heist = ACTIVE.get(player.getUUID());
        if (heist != null && step(heist, now)) {
            ACTIVE.remove(player.getUUID());
        }
    }

    /** true — ограбление закончилось (так или иначе). */
    private static boolean step(Heist heist, long now) {
        ServerPlayer robber = heist.robber;
        boolean gone = robber.isRemoved() || !robber.isAlive() || robber.hasDisconnected();
        boolean jailed = LifeData.get(robber.server).jailUntil(robber.getUUID()) > now;
        if (gone || jailed || robber.level() != robber.server.overworld()
                || robber.position().distanceTo(heist.spot) > RADIUS) {
            if (!gone) {
                robber.sendSystemMessage(Component.translatable("citylife.rob.failed", heist.place)
                        .withStyle(ChatFormatting.GRAY));
            }
            return true;
        }
        if (now < heist.end) {
            robber.displayClientMessage(Component.translatable("citylife.rob.progress",
                    (now - heist.start) / 20, (heist.end - heist.start) / 20)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        Money.give(robber, heist.loot);
        STOLEN.merge(robber.getUUID(), heist.loot, Long::sum);
        EMPTY_UNTIL.put(heist.key, now + CityConfig.CONFIG.robCooldownMinutes.get() * 1200L);
        robber.sendSystemMessage(Component.translatable("citylife.rob.done", Money.format(heist.loot))
                .withStyle(ChatFormatting.GOLD));
        CityLife.LOG.info("City Life: ограбление {} удалось, {} ₽", heist.key, heist.loot);
        return true;
    }

    /** Строка в углу экрана, пока идёт ограбление. */
    public static String hudLine(ServerPlayer player) {
        Heist heist = ACTIVE.get(player.getUUID());
        if (heist == null) {
            return "";
        }
        long now = player.level().getGameTime();
        return Texts.ru("citylife.rob.hud", heist.place, Math.max(0, (heist.end - now) / 20),
                (int) player.position().distanceTo(heist.spot), (int) RADIUS);
    }

    /**
     * Задержание: полиция забирает награбленное, сколько найдёт наличными.
     * Положил деньги на счёт или потратил — изымать нечего.
     */
    public static long confiscate(ServerPlayer player) {
        Long stolen = STOLEN.remove(player.getUUID());
        long amount = stolen == null ? 0 : Math.min(stolen, Money.cash(player));
        if (amount > 0 && !Money.take(player, amount)) {
            return 0;
        }
        return amount;
    }

    /** Розыск сошёл сам: ушёл от полиции — деньги остаются за грабителем. */
    public static void forget(UUID player) {
        STOLEN.remove(player);
    }

    /** Касса снова полна — для автотестов. */
    public static void refill(String key) {
        EMPTY_UNTIL.remove(key);
    }

    /** Отменить ограбление без последствий — уборка после автотестов. */
    public static void cancel(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || ACTIVE.isEmpty()
                || event.getServer().overworld().getGameTime() % 20 != 0) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        for (Iterator<Heist> it = ACTIVE.values().iterator(); it.hasNext(); ) {
            if (step(it.next(), now)) {
                it.remove();
            }
        }
    }
}
