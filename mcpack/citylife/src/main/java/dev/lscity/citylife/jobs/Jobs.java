package dev.lscity.citylife.jobs;

import com.mojang.brigadier.CommandDispatcher;
import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.CityLandmarks;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.estate.Estate;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Подработки по телефону (приложение «Работа») и командой /job.
 *
 *   Курьер   — отнести посылку к двери дома или квартиры по адресу.
 *              Платят за расстояние, есть срок доставки.
 *   Такси    — забрать пассажира в одной точке и довезти до другой.
 *              Работает только за рулём: без машины пассажир не сядет.
 *   Смена    — отработать 3 минуты в магазине, на АЗС или пункте выдачи.
 *
 * Навигатор сам ведёт к цели, задание засчитывается, когда игрок на месте.
 * Кроме этого деньги дают скупщики у NPC (руда, урожай, рыба) и /work.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Jobs {

    public static final List<String> KINDS = List.of("courier", "taxi", "shift");

    /** Текущее задание игрока. */
    public static final class Job {
        final String kind;
        final String title;
        Waypoint target;
        Waypoint dropoff;
        final long pay;
        final long deadline;
        int stage;
        int worked;

        Job(String kind, String title, Waypoint target, Waypoint dropoff, long pay, long deadline) {
            this.kind = kind;
            this.title = title;
            this.target = target;
            this.dropoff = dropoff;
            this.pay = pay;
            this.deadline = deadline;
        }
    }

    private static final Map<UUID, Job> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    /**
     * Оплата. Настроена так, чтобы час работы давал 5–7 тысяч: квартира —
     * это 3–4 часа, дом — вечер-два, вилла — неделя игры. Таблица с
     * расчётом — docs/economy.md (build/gen_economy_doc.py, те же числа).
     */
    public static final double COURIER_BASE = 60;
    public static final double COURIER_PER_BLOCK = 0.35;
    public static final double TAXI_BASE = 80;
    public static final double TAXI_PER_BLOCK = 0.3;
    public static final long SHIFT_PAY = 280;

    /** Сколько секунд длится смена. */
    private static final int SHIFT = 180;
    private static final double ARRIVE = 4.0D;

    private Jobs() {
    }

    private static long pay(double base) {
        return Math.max(10, Math.round(base * CityConfig.CONFIG.jobPayPercent.get() / 100D / 10D) * 10);
    }

    private static Waypoint door(Estate.Unit unit) {
        return new Waypoint(unit.address(), unit.door().getX(), unit.door().getY(),
                unit.door().getZ(), "home", false);
    }

    /** Случайная дверь на расстоянии от min до max блоков. */
    private static Waypoint randomDoor(RandomSource random, Vec3 from, double min, double max) {
        List<Estate.Unit> all = Estate.all();
        if (all.isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < 400; attempt++) {
            Estate.Unit unit = all.get(random.nextInt(all.size()));
            double d = Math.sqrt(unit.door().distToCenterSqr(from));
            if (d >= min && d <= max) {
                return door(unit);
            }
        }
        return door(all.get(random.nextInt(all.size())));
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
        RandomSource random = player.getRandom();
        Vec3 here = player.position();
        Job job;
        switch (kind) {
            case "courier" -> {
                Waypoint target = randomDoor(random, here, 150, 700);
                if (target == null) {
                    return;
                }
                double d = dist(here, target);
                job = new Job(kind, dev.lscity.citylife.data.Texts.ru("citylife.job.courier.title"),
                        target, null, pay(COURIER_BASE + d * COURIER_PER_BLOCK),
                        now + (long) (120 + d / 3) * 20L);
            }
            case "taxi" -> {
                if (!player.isPassenger()) {
                    say(player, Component.translatable("citylife.job.need_car"), ChatFormatting.RED);
                    return;
                }
                Waypoint from = randomDoor(random, here, 60, 300);
                if (from == null) {
                    return;
                }
                Vec3 pick = new Vec3(from.x(), from.y(), from.z());
                Waypoint to = randomDoor(random, pick, 300, 900);
                double d = dist(pick, to);
                job = new Job(kind, dev.lscity.citylife.data.Texts.ru("citylife.job.taxi.title"),
                        from, to, pay(TAXI_BASE + d * TAXI_PER_BLOCK),
                        now + (long) (180 + (dist(here, from) + d) / 4) * 20L);
            }
            case "shift" -> {
                List<Waypoint> places = new ArrayList<>();
                for (Waypoint point : CityLandmarks.ALL) {
                    if (List.of("shop", "mall", "pickup", "gas", "club").contains(point.icon())
                            && dist(here, point) < 900) {
                        places.add(point);
                    }
                }
                if (places.isEmpty()) {
                    places.addAll(CityLandmarks.ALL);
                }
                Waypoint target = places.get(random.nextInt(places.size()));
                job = new Job(kind, dev.lscity.citylife.data.Texts.ru("citylife.job.shift.title"),
                        target, null, pay(SHIFT_PAY), now + (long) (SHIFT + 600) * 20L);
            }
            default -> {
                return;
            }
        }
        ACTIVE.put(id, job);
        route(player, job.target);
        say(player, Component.translatable("citylife.job.taken." + kind, job.target.name(),
                Money.format(job.pay)), ChatFormatting.GREEN);
    }

    public static void quit(ServerPlayer player, boolean failed) {
        Job job = ACTIVE.remove(player.getUUID());
        if (job == null) {
            return;
        }
        COOLDOWN.put(player.getUUID(), player.level().getGameTime() + 60 * 20L);
        CityData.get(player.server).setRoute(player.getUUID(), null);
        Net.sendRoute(player, null);
        say(player, Component.translatable(failed ? "citylife.job.failed" : "citylife.job.quit"),
                ChatFormatting.GRAY);
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
        CityData.get(player.server).deposit(player.getUUID(), job.pay, dev.lscity.citylife.data.Texts.ru(
                "citylife.statement.job", job.title), player.level().getGameTime());
        LifeData.get(player.server).recordJob(player.getUUID(), job.pay);
        CityData.get(player.server).setRoute(player.getUUID(), null);
        Net.sendRoute(player, null);
        say(player, Component.translatable("citylife.job.paid", job.title, Money.format(job.pay)),
                ChatFormatting.GOLD);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 1.0F);
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
        if ("shift".equals(job.kind)) {
            return job.title + " · " + job.worked + "/" + SHIFT + " с";
        }
        return job.title + " · " + (int) dist(player.position(), job.target) + " м · "
                + left / 60 + ":" + String.format("%02d", left % 60);
    }

    /** Есть ли у игрока задание — для автотестов и телефона. */
    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Куда сейчас ведёт задание. */
    public static Waypoint target(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        return job == null ? null : job.target;
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
        double d = dist(player.position(), job.target);
        switch (job.kind) {
            case "courier" -> {
                if (d <= ARRIVE) {
                    finish(player, job);
                }
            }
            case "taxi" -> {
                if (d <= ARRIVE + 2 && player.isPassenger()) {
                    if (job.stage == 0) {
                        job.stage = 1;
                        say(player, Component.translatable("citylife.job.taxi.pickup",
                                job.dropoff.name()), ChatFormatting.AQUA);
                        job.target = job.dropoff;
                        route(player, job.target);
                    } else {
                        finish(player, job);
                    }
                } else if (d <= ARRIVE + 2 && !player.isPassenger()) {
                    player.displayClientMessage(Component.translatable("citylife.job.need_car")
                            .withStyle(ChatFormatting.YELLOW), true);
                }
            }
            case "shift" -> {
                if (d <= 14) {
                    job.worked++;
                    player.displayClientMessage(Component.translatable("citylife.job.shift.progress",
                            job.worked, SHIFT).withStyle(ChatFormatting.AQUA), true);
                    if (job.worked >= SHIFT) {
                        finish(player, job);
                    }
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    // --- для телефона -------------------------------------------------------------

    public static CompoundTag snapshot(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        LifeData life = LifeData.get(player.server);
        tag.putLong("earned", life.earned(player.getUUID()));
        tag.putInt("done", life.jobsDone(player.getUUID()));
        tag.putInt("wanted", life.wanted(player.getUUID()));
        Job job = ACTIVE.get(player.getUUID());
        if (job != null) {
            CompoundTag active = new CompoundTag();
            active.putString("kind", job.kind);
            active.putString("title", job.title);
            active.putString("target", job.target.name());
            active.putInt("distance", (int) dist(player.position(), job.target));
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
    }

    private static int status(ServerPlayer player) {
        Job job = ACTIVE.get(player.getUUID());
        if (job == null) {
            say(player, Component.translatable("citylife.job.help"), ChatFormatting.GRAY);
            return 0;
        }
        say(player, Component.translatable("citylife.job.status", job.title, job.target.name(),
                (int) dist(player.position(), job.target), Money.format(job.pay)),
                ChatFormatting.AQUA);
        return 1;
    }
}
