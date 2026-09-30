package dev.lscity.citylife.jobs;

import com.mojang.brigadier.CommandDispatcher;
import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.city.Pedestrians;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.estate.Estate;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Подработки по телефону (приложение «Работа») и командой /job.
 *
 *   Курьер     — забрать посылку в пункте выдачи и отнести к двери по адресу.
 *   Доставка   — забрать пакет с едой в кафе и довезти, пока не остыл.
 *   Такси      — забрать пассажира и довезти (только за рулём).
 *   Смена      — 3 минуты в магазине, на АЗС или в пункте выдачи.
 *   Охранник   — 3 минуты на посту в ТЦ или клубе.
 *   Грузчик    — 3 минуты на складе.
 *   Мусорщик   — собрать мешки с мусором на улицах и сдать на склад.
 *
 * Груз (посылка, пакет, мешки) — настоящие предметы: без них задание не
 * сдать, их можно выронить и украсть. Пассажир такси — живой прохожий,
 * который садится в машину. Навигатор ведёт на каждом этапе сам.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Jobs {

    public static final List<String> KINDS = List.of("courier", "food", "taxi", "shift", "guard",
            "loader", "garbage");

    /**
     * Оплата. Настроена так, чтобы час работы давал 5–9 тысяч: квартира —
     * это 3–4 часа, дом — вечер-два, вилла — неделя игры. Таблица с
     * расчётом — docs/economy.md (build/gen_economy_doc.py, те же числа).
     */
    public static final double COURIER_BASE = 60;
    public static final double COURIER_PER_BLOCK = 0.35;
    public static final double FOOD_BASE = 60;
    public static final double FOOD_PER_BLOCK = 0.25;
    public static final double TAXI_BASE = 80;
    public static final double TAXI_PER_BLOCK = 0.3;
    public static final long SHIFT_PAY = 280;
    public static final long GARBAGE_PAY = 380;

    /** Сколько секунд длится смена. */
    private static final int SHIFT = 180;
    private static final int GARBAGE_BAGS = 5;
    private static final double ARRIVE = 4.0D;
    private static final String PASSENGER_TAG = "citylife_passenger";

    /** Текущее задание игрока. */
    public static final class Job {
        final int id;
        final String kind;
        final String title;
        /** Этапы: точки по порядку (забрать, отвезти, сдать). */
        final List<Waypoint> stops = new ArrayList<>();
        int stage;
        final long pay;
        final long deadline;
        int worked;
        UUID passenger;
        /** Мешки мусорщика: по одному на каждую точку, в том же порядке. */
        final List<UUID> bags = new ArrayList<>();

        Job(int id, String kind, String title, long pay, long deadline) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.pay = pay;
            this.deadline = deadline;
        }

        Waypoint target() {
            return stops.get(Math.min(stage, stops.size() - 1));
        }

        boolean lastStop() {
            return stage >= stops.size() - 1;
        }
    }

    private static final Map<UUID, Job> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Set<UUID> PASSENGERS = new HashSet<>();
    private static int nextJob = 1;

    private Jobs() {
    }

    private static long pay(double base) {
        return Math.max(10, Math.round(base * CityConfig.CONFIG.jobPayPercent.get() / 100D / 10D) * 10);
    }

    private static Waypoint door(Estate.Unit unit) {
        return new Waypoint(unit.address(), unit.door().getX(), unit.door().getY(),
                unit.door().getZ(), "home", false);
    }

    private static Waypoint place(BlockPos pos, String name, String icon) {
        return new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), icon, false);
    }

    /** Случайная дверь на расстоянии от min до max блоков. */
    private static Waypoint randomDoor(RandomSource random, Vec3 from, double min, double max) {
        List<Estate.Unit> all = Estate.all();
        if (all.isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < 400; attempt++) {
            Estate.Unit unit = all.get(random.nextInt(all.size()));
            double d = flat(unit.door(), from);
            if (d >= min && d <= max) {
                return door(unit);
            }
        }
        return door(all.get(random.nextInt(all.size())));
    }

    /** Одно из трёх ближайших мест нужного типа. */
    private static BlockPos nearestPlace(RandomSource random, Vec3 from, String... kinds) {
        List<BlockPos> all = new ArrayList<>();
        for (String kind : kinds) {
            all.addAll(Pedestrians.places(kind));
        }
        if (all.isEmpty()) {
            return null;
        }
        all.sort((a, b) -> Double.compare(flat(a, from), flat(b, from)));
        return all.get(random.nextInt(Math.min(3, all.size())));
    }

    /** Случайная точка у магазинов и кафе на нужном расстоянии: сюда кладём мусор. */
    private static BlockPos streetPoint(RandomSource random, Vec3 from, double min, double max) {
        List<BlockPos> fit = new ArrayList<>();
        for (String kind : new String[]{"shop", "diner", "pickup", "warehouse", "gas"}) {
            for (BlockPos p : Pedestrians.places(kind)) {
                double d = flat(p, from);
                if (d >= min && d <= max) {
                    fit.add(p);
                }
            }
        }
        return fit.isEmpty() ? null : fit.get(random.nextInt(fit.size()));
    }

    /** Расстояние по земле, без высоты: на задании важно, далеко ли идти. */
    private static double flat(BlockPos p, Vec3 from) {
        double dx = p.getX() + 0.5D - from.x;
        double dz = p.getZ() + 0.5D - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double dist(Vec3 from, Waypoint to) {
        return Math.sqrt(from.distanceToSqr(to.x() + 0.5D, to.y(), to.z() + 0.5D));
    }

    // --- взять и бросить ----------------------------------------------------------

    public static void take(ServerPlayer player, String kind) {
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        if (ACTIVE.containsKey(id)) {
            say(player, Component.translatable("citylife.job.busy"), ChatFormatting.YELLOW);
            return;
        }
        Long cool = COOLDOWN.get(id);
        if (cool != null && now < cool) {
            say(player, Component.translatable("citylife.job.cooldown", (cool - now) / 20),
                    ChatFormatting.YELLOW);
            return;
        }
        Job job = plan(player, kind, now);
        if (job == null) {
            return;
        }
        ACTIVE.put(id, job);
        route(player, job.target());
        say(player, Component.translatable("citylife.job.taken." + kind, job.target().name(),
                Money.format(job.pay)), ChatFormatting.GREEN);
    }

    private static Job plan(ServerPlayer player, String kind, long now) {
        RandomSource random = player.getRandom();
        Vec3 here = player.position();
        String title = Texts.ru("citylife.job." + kind + ".title");
        switch (kind) {
            case "courier", "food" -> {
                boolean food = "food".equals(kind);
                BlockPos from = nearestPlace(random, here, food ? "diner" : "pickup");
                Waypoint pickup = from == null ? null : place(from,
                        Texts.ru(food ? "citylife.job.place.diner" : "citylife.job.place.pickup"),
                        food ? "shop" : "pickup");
                Vec3 start = pickup == null ? here : new Vec3(pickup.x(), pickup.y(), pickup.z());
                Waypoint to = randomDoor(random, start, food ? 100 : 150, food ? 500 : 700);
                if (to == null) {
                    return null;
                }
                double d = dist(start, to) + (pickup == null ? 0 : dist(here, pickup));
                long pay = food ? pay(FOOD_BASE + d * FOOD_PER_BLOCK)
                        : pay(COURIER_BASE + d * COURIER_PER_BLOCK);
                long time = food ? (long) (90 + d / 4) : (long) (120 + d / 3);
                Job job = new Job(nextJob++, kind, title, pay, now + time * 20L);
                if (pickup != null) {
                    job.stops.add(pickup);
                } else {
                    give(player, job, to);
                }
                job.stops.add(to);
                return job;
            }
            case "taxi" -> {
                if (!player.isPassenger()) {
                    say(player, Component.translatable("citylife.job.need_car"), ChatFormatting.RED);
                    return null;
                }
                Waypoint from = randomDoor(random, here, 60, 300);
                if (from == null) {
                    return null;
                }
                Vec3 pick = new Vec3(from.x(), from.y(), from.z());
                Waypoint to = randomDoor(random, pick, 300, 900);
                double d = dist(pick, to);
                Job job = new Job(nextJob++, kind, title, pay(TAXI_BASE + d * TAXI_PER_BLOCK),
                        now + (long) (180 + (dist(here, from) + d) / 4) * 20L);
                job.stops.add(from);
                job.stops.add(to);
                return job;
            }
            case "shift", "guard", "loader" -> {
                String[] kinds = switch (kind) {
                    case "guard" -> new String[]{"mall", "club"};
                    case "loader" -> new String[]{"warehouse"};
                    default -> new String[]{"shop", "gas", "pickup"};
                };
                BlockPos at = nearestPlace(random, here, kinds);
                if (at == null) {
                    return null;
                }
                Job job = new Job(nextJob++, kind, title, pay(SHIFT_PAY),
                        now + (long) (SHIFT + 600) * 20L);
                job.stops.add(place(at, Texts.ru("citylife.job.place." + kind), "shop"));
                return job;
            }
            case "garbage" -> {
                BlockPos dump = nearestPlace(random, here, "warehouse");
                if (dump == null) {
                    return null;
                }
                Job job = new Job(nextJob++, kind, title, pay(GARBAGE_PAY), now + 900 * 20L);
                ServerLevel level = player.serverLevel();
                Set<BlockPos> used = new HashSet<>();
                for (int i = 0; i < GARBAGE_BAGS; i++) {
                    BlockPos at = null;
                    for (int tries = 0; tries < 40 && at == null; tries++) {
                        BlockPos p = streetPoint(random, here, 20, 150);
                        if (p != null && used.add(p) && level.isLoaded(p)) {
                            at = p;
                        }
                    }
                    if (at == null) {
                        continue;
                    }
                    ItemStack bag = new ItemStack(Registration.TRASH_BAG.get());
                    bag.getOrCreateTag().putInt("job", job.id);
                    ItemEntity entity = new ItemEntity(level, at.getX() + 0.5D, at.getY() + 0.2D,
                            at.getZ() + 0.5D, bag);
                    entity.setUnlimitedLifetime();
                    entity.setGlowingTag(true);
                    level.addFreshEntity(entity);
                    job.bags.add(entity.getUUID());
                    job.stops.add(place(at, Texts.ru("citylife.job.place.bag"), "pin"));
                }
                if (job.bags.isEmpty()) {
                    return null;
                }
                job.stops.add(place(dump, Texts.ru("citylife.job.place.dump"), "shop"));
                return job;
            }
            default -> {
                return null;
            }
        }
    }

    /** Выдать груз: посылку или пакет с адресом и номером задания. */
    private static void give(ServerPlayer player, Job job, Waypoint to) {
        Item item = "food".equals(job.kind) ? Registration.FOOD_BAG.get() : Registration.PARCEL.get();
        ItemStack cargo = new ItemStack(item);
        cargo.getOrCreateTag().putString("address", to.name());
        cargo.getOrCreateTag().putInt("job", job.id);
        if (!player.getInventory().add(cargo)) {
            player.drop(cargo, false);
        }
    }

    /** Сколько у игрока груза этого задания (и убрать его, если remove). */
    private static int cargo(ServerPlayer player, Job job, Item item, boolean remove) {
        int found = 0;
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.is(item) && stack.hasTag() && stack.getTag().getInt("job") == job.id) {
                found += stack.getCount();
                if (remove) {
                    items.set(i, ItemStack.EMPTY);
                }
            }
        }
        return found;
    }

    public static void quit(ServerPlayer player, boolean failed) {
        Job job = ACTIVE.remove(player.getUUID());
        if (job == null) {
            return;
        }
        cleanup(player.serverLevel(), job);
        cargo(player, job, Registration.PARCEL.get(), true);
        cargo(player, job, Registration.FOOD_BAG.get(), true);
        cargo(player, job, Registration.TRASH_BAG.get(), true);
        COOLDOWN.put(player.getUUID(), player.level().getGameTime() + 60 * 20L);
        CityData.get(player.server).setRoute(player.getUUID(), null);
        Net.sendRoute(player, null);
        say(player, Component.translatable(failed ? "citylife.job.failed" : "citylife.job.quit"),
                ChatFormatting.GRAY);
    }

    /** Убрать то, что задание оставило в мире: пассажира, несобранные мешки. */
    private static void cleanup(ServerLevel level, Job job) {
        if (job.passenger != null) {
            Entity p = level.getEntity(job.passenger);
            if (p != null) {
                p.stopRiding();
                p.discard();
            }
            PASSENGERS.remove(job.passenger);
        }
        for (UUID bag : job.bags) {
            Entity e = level.getEntity(bag);
            if (e != null) {
                e.discard();
            }
        }
    }

    private static void route(ServerPlayer player, Waypoint point) {
        CityData.get(player.server).setRoute(player.getUUID(), point);
        Net.sendRoute(player, point);
    }

    private static void say(ServerPlayer player, Component text, ChatFormatting colour) {
        player.displayClientMessage(text.copy().withStyle(colour), false);
    }

    private static void finish(ServerPlayer player, Job job) {
        ACTIVE.remove(player.getUUID());
        CityData.get(player.server).deposit(player.getUUID(), job.pay,
                Texts.ru("citylife.statement.job", job.title), player.level().getGameTime());
        LifeData.get(player.server).recordJob(player.getUUID(), job.pay);
        CityData.get(player.server).setRoute(player.getUUID(), null);
        Net.sendRoute(player, null);
        say(player, Component.translatable("citylife.job.paid", job.title, Money.format(job.pay)),
                ChatFormatting.GOLD);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void next(ServerPlayer player, Job job) {
        job.stage++;
        route(player, job.target());
    }

    // --- проверка раз в секунду ---------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        check(player);
    }

    /** Короткая строка задания для строки состояния: «Курьер · 230 м · 4:10». */
    public static String hudLine(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        if (job == null) {
            return "";
        }
        long left = Math.max(0, (job.deadline - player.level().getGameTime()) / 20);
        if (isShift(job.kind)) {
            return job.title + " · " + job.worked + "/" + SHIFT + " с";
        }
        return job.title + " · " + (int) dist(player.position(), job.target()) + " м · "
                + left / 60 + ":" + String.format("%02d", left % 60);
    }

    private static boolean isShift(String kind) {
        return "shift".equals(kind) || "guard".equals(kind) || "loader".equals(kind);
    }

    /** Есть ли у игрока задание — для автотестов и телефона. */
    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Куда сейчас ведёт задание. */
    public static Waypoint target(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        return job == null ? null : job.target();
    }

    /** Проверка задания раз в секунду: на месте ли игрок, не вышло ли время. */
    public static void check(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        if (job == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (now > job.deadline) {
            quit(player, true);
            return;
        }
        double d = dist(player.position(), job.target());
        switch (job.kind) {
            case "courier", "food" -> {
                Item item = "food".equals(job.kind) ? Registration.FOOD_BAG.get()
                        : Registration.PARCEL.get();
                if (!job.lastStop()) {
                    if (d <= ARRIVE) {
                        Waypoint to = job.stops.get(job.stops.size() - 1);
                        give(player, job, to);
                        say(player, Component.translatable("citylife.job.cargo_taken", to.name()),
                                ChatFormatting.AQUA);
                        next(player, job);
                    }
                } else if (d <= ARRIVE) {
                    if (cargo(player, job, item, true) > 0) {
                        finish(player, job);
                    } else {
                        player.displayClientMessage(Component.translatable("citylife.job.no_cargo")
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
            }
            case "taxi" -> taxi(player, job, d);
            case "shift", "guard", "loader" -> {
                if (d <= 14) {
                    job.worked++;
                    player.displayClientMessage(Component.translatable("citylife.job.shift.progress",
                            job.worked, SHIFT).withStyle(ChatFormatting.AQUA), true);
                    if (job.worked >= SHIFT) {
                        finish(player, job);
                    }
                }
            }
            case "garbage" -> garbage(player, job);
            default -> {
            }
        }
    }

    /** Такси: у точки посадки ждёт пассажир, садится в машину и выходит на месте. */
    private static void taxi(ServerPlayer player, Job job, double d) {
        ServerLevel level = player.serverLevel();
        Waypoint pickup = job.stops.get(0);
        if (job.stage == 0) {
            BlockPos at = new BlockPos(pickup.x(), pickup.y(), pickup.z());
            if (job.passenger == null && d < 64 && level.isLoaded(at)) {
                Entity p = Pedestrians.spawnWalker(level, at, player.getRandom());
                if (p != null) {
                    p.addTag(PASSENGER_TAG);
                    Pedestrians.pin(p);
                    job.passenger = p.getUUID();
                    PASSENGERS.add(p.getUUID());
                }
            }
            if (d <= ARRIVE + 3) {
                Entity vehicle = player.getVehicle();
                if (vehicle == null) {
                    player.displayClientMessage(Component.translatable("citylife.job.need_car")
                            .withStyle(ChatFormatting.YELLOW), true);
                    return;
                }
                Entity p = job.passenger == null ? null : level.getEntity(job.passenger);
                if (p != null && !p.startRiding(vehicle, true)) {
                    // Свободного места в машине нет: пассажир «садится» и не виден.
                    p.discard();
                    PASSENGERS.remove(job.passenger);
                    job.passenger = null;
                }
                say(player, Component.translatable("citylife.job.taxi.pickup",
                        job.stops.get(1).name()), ChatFormatting.AQUA);
                next(player, job);
            }
            return;
        }
        if (d <= ARRIVE + 3) {
            if (!player.isPassenger()) {
                player.displayClientMessage(Component.translatable("citylife.job.need_car")
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            Entity p = job.passenger == null ? null : level.getEntity(job.passenger);
            if (p != null) {
                p.stopRiding();
                Waypoint to = job.target();
                p.moveTo(to.x() + 0.5D, to.y(), to.z() + 0.5D);
                PASSENGERS.remove(job.passenger);
                job.passenger = null;
                Pedestrians.release(p);
            }
            finish(player, job);
        }
    }

    /** Мусорщик: собрать мешки этого задания и сдать на склад. */
    private static void garbage(ServerPlayer player, Job job) {
        ServerLevel level = player.serverLevel();
        int carried = cargo(player, job, Registration.TRASH_BAG.get(), false);
        int dumpIndex = job.stops.size() - 1;
        Waypoint dump = job.stops.get(dumpIndex);
        if (carried >= job.bags.size()) {
            if (job.stage != dumpIndex) {
                job.stage = dumpIndex;
                route(player, dump);
                say(player, Component.translatable("citylife.job.garbage.full"), ChatFormatting.AQUA);
            }
            if (dist(player.position(), dump) <= ARRIVE + 2) {
                cargo(player, job, Registration.TRASH_BAG.get(), true);
                finish(player, job);
            }
            return;
        }
        // Ведём к ближайшему мешку, который ещё лежит на улице.
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < job.bags.size(); i++) {
            Waypoint stop = job.stops.get(i);
            Entity bag = level.getEntity(job.bags.get(i));
            boolean loaded = level.isLoaded(new BlockPos(stop.x(), stop.y(), stop.z()));
            if (bag == null && loaded) {
                continue;       // мешок уже подобрали
            }
            double dd = dist(player.position(), stop);
            if (dd < bestD) {
                bestD = dd;
                best = i;
            }
        }
        if (best >= 0 && best != job.stage) {
            job.stage = best;
            route(player, job.stops.get(best));
        }
        player.displayClientMessage(Component.translatable("citylife.job.garbage.progress", carried,
                job.bags.size()).withStyle(ChatFormatting.AQUA), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Job job = ACTIVE.remove(player.getUUID());
            if (job != null) {
                cleanup(player.serverLevel(), job);
            }
        }
    }

    /** Пассажиры не переживают перезапуск сервера. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity().getTags().contains(PASSENGER_TAG)
                && !PASSENGERS.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    // --- для телефона -------------------------------------------------------------

    public static CompoundTag snapshot(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        LifeData life = LifeData.get(player.server);
        tag.putLong("earned", life.earned(player.getUUID()));
        tag.putInt("done", life.jobsDone(player.getUUID()));
        tag.putInt("wanted", life.wanted(player.getUUID()));
        tag.putString("duty", Duty.of(player));
        Job job = ACTIVE.get(player.getUUID());
        if (job != null) {
            CompoundTag active = new CompoundTag();
            active.putString("kind", isShift(job.kind) ? "shift" : job.kind);
            active.putString("title", job.title);
            active.putString("target", job.target().name());
            active.putInt("distance", (int) dist(player.position(), job.target()));
            active.putLong("pay", job.pay);
            active.putLong("left", Math.max(0, (job.deadline - player.level().getGameTime()) / 20));
            active.putInt("stage", job.stage);
            active.putInt("worked", job.worked);
            active.putInt("shift", SHIFT);
            tag.put("active", active);
        }
        Long cool = COOLDOWN.get(player.getUUID());
        if (cool != null && cool > player.level().getGameTime()) {
            tag.putLong("cooldown", (cool - player.level().getGameTime()) / 20);
        }
        return tag;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("job").executes(ctx -> status(
                ctx.getSource().getPlayerOrException()));
        for (String kind : KINDS) {
            root.then(Commands.literal(kind).executes(ctx -> {
                take(ctx.getSource().getPlayerOrException(), kind);
                return 1;
            }));
        }
        root.then(Commands.literal("quit").executes(ctx -> {
            quit(ctx.getSource().getPlayerOrException(), false);
            return 1;
        }));
        dispatcher.register(root);
        var duty = Commands.literal("duty");
        for (String service : new String[]{"police", "medic", "fire", "off"}) {
            duty.then(Commands.literal(service).executes(ctx -> {
                Duty.set(ctx.getSource().getPlayerOrException(), service);
                return 1;
            }));
        }
        dispatcher.register(duty);
    }

    private static int status(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        if (job == null) {
            say(player, Component.translatable("citylife.job.help"), ChatFormatting.GRAY);
            return 0;
        }
        say(player, Component.translatable("citylife.job.status", job.title, job.target().name(),
                (int) dist(player.position(), job.target()), Money.format(job.pay)),
                ChatFormatting.AQUA);
        return 1;
    }
}
