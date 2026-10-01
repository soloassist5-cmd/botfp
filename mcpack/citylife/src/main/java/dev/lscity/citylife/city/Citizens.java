package dev.lscity.citylife.city;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.CityLandmarks;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Горожане-прохожие: внешность, разговор, нападение и смерть.
 *
 * Внешность: скин, а к нему имя по полу и происхождению — мужчину не
 * зовут Ольгой, а у армянина с бородой имя армянское.
 * Разговор: клик по прохожему — он останавливается, отвечает и даёт
 * варианты в чате (как дела, дорога до банка или больницы, попросить
 * денег). Проходя мимо, здороваются; от вооружённого шарахаются.
 * Нападение: прохожий кричит и убегает, соседи тоже; ударившему — звезда.
 * Смерть: падают наличные из кошелька, убийце — две звезды и тревога 112.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Citizens {

    /** Тег патрульного полицейского среди прохожих. */
    public static final String PATROL_TAG = "citylife_patrol";

    /**
     * Имена по происхождению: у каждого скина своё лицо, и имя должно ему
     * подходить. Ключ — происхождение, значение — мужские и женские имена.
     */
    private static final Map<String, String[][]> NAMES = Map.of(
            "slavic", new String[][]{{"Максим", "Иван", "Артём", "Павел", "Денис", "Дмитрий",
                    "Олег", "Сергей", "Андрей", "Виктор"}, {"Анна", "Ольга", "Мария", "Софья",
                    "Алина", "Ирина", "Екатерина", "Дарья", "Елена", "Полина"}},
            "caucasus", new String[][]{{"Ашот", "Арсен", "Тигран", "Гурген", "Адам", "Рустам",
                    "Тимур", "Давид", "Руслан", "Мурад"}, {"Ануш", "Карина", "Мадина", "Нана",
                    "Лаура", "Зарина", "Лиана", "Сона"}},
            "latino", new String[][]{{"Карлос", "Хосе", "Луис", "Диего", "Мигель", "Рауль"},
                    {"Лусия", "Камила", "Изабелла", "Габриэла", "Валентина", "Кармен"}},
            "african", new String[][]{{"Малик", "Джамал", "Кофи", "Деон", "Тайрон", "Омар"},
                    {"Аиша", "Имани", "Ниа", "Зури", "Амара", "Кения"}},
            "english", new String[][]{{"Дэвид", "Майкл", "Джон", "Томас", "Джеймс", "Гарри"},
                    {"Эмили", "Джессика", "Сара", "Кейт", "Хлоя", "Элис"}});
    /** Происхождение каждого скина прохожего (см. tools/gen_skins.py: тон кожи, волосы). */
    private static final Map<String, String> ORIGIN = Map.ofEntries(
            Map.entry("man_1", "slavic"), Map.entry("man_2", "african"),
            Map.entry("man_3", "english"), Map.entry("man_4", "caucasus"),
            Map.entry("man_5", "latino"), Map.entry("man_6", "slavic"),
            Map.entry("man_7", "african"), Map.entry("man_8", "english"),
            Map.entry("woman_1", "slavic"), Map.entry("woman_2", "latino"),
            Map.entry("woman_3", "english"), Map.entry("woman_4", "african"),
            Map.entry("woman_5", "caucasus"), Map.entry("woman_6", "slavic"),
            Map.entry("woman_7", "african"), Map.entry("woman_8", "slavic"));
    private static final String[] PATROL_NAMES = {"Сержант Петров", "Офицер Ковальски",
            "Сержант Рамирес", "Лейтенант Орлов", "Офицер Ким", "Сержант Волков"};
    private static final int SKINS_PER_GENDER = 8;

    private static final List<String> GREETINGS = List.of("Привет!", "Добрый день.",
            "Здравствуй.", "Хорошего дня!", "О, привет.");
    private static final List<String> CHAT = List.of("Хорошая погода сегодня, правда?",
            "Ищешь работу — открой «Работу» в телефоне. Курьерам неплохо платят.",
            "Говорят, в мэрии продают квартиры. Недёшево.", "На пляже вечером красиво, сходи.",
            "Метро ходит, три станции. Удобно.", "Вчера у банка опять сирены были. Неспокойно.",
            "Купил себе машину — теперь без неё никуда.", "Кофейня у метро — лучшая в городе.",
            "Слышал, магазины тоже можно купить. Доход каждый день!",
            "Если что случится — звони 112, приезжают быстро.");
    private static final List<String> SCARED = List.of("Убери оружие!", "Не стреляй!",
            "Он вооружён!", "Помогите!");
    private static final List<String> HURT = List.of("Ай! Помогите!", "Полиция!",
            "Ты что творишь?!", "Не надо!");

    /** Куда прохожий может подсказать дорогу: значок метки и название в кнопке. */
    private static final Map<String, String> WAYS = new java.util.LinkedHashMap<>();

    static {
        WAYS.put("bank", "банк");
        WAYS.put("atm", "банкомат");
        WAYS.put("hospital", "больница");
        WAYS.put("police", "полиция");
        WAYS.put("metro", "метро");
        WAYS.put("food", "поесть");
    }

    /** Кто кого и когда последний раз бил — звезда за нападение раз в минуту. */
    private static final Map<UUID, Long> FIGHTS = new HashMap<>();
    /** Когда прохожий последний раз здоровался с игроком. */
    private static final Map<UUID, Long> GREETED = new HashMap<>();
    /** Кто у кого просил денег (игрок -> время): раз в 5 минут. */
    private static final Map<UUID, Long> BEGGED = new HashMap<>();

    /** Чем закончился последний ответ прохожего — для автотестов. */
    public static volatile String lastOutcome = "";

    private Citizens() {
    }

    // --- внешность ----------------------------------------------------------------

    /** Внешность прохожего: скин, имя, полицейский ли он. */
    public record Look(String skin, String name, boolean patrol) {
    }

    public static Look pick(RandomSource random) {
        int share = CityConfig.CONFIG.patrolPercent.get();
        if (share > 0 && random.nextInt(100) < share) {
            return new Look("police", PATROL_NAMES[random.nextInt(PATROL_NAMES.length)], true);
        }
        boolean female = random.nextBoolean();
        String skin = (female ? "woman_" : "man_") + (1 + random.nextInt(SKINS_PER_GENDER));
        String[] names = names(skin);
        return new Look(skin, names[random.nextInt(names.length)], false);
    }

    private static String[] names(String skin) {
        String[][] origin = NAMES.get(ORIGIN.getOrDefault(skin, "slavic"));
        return skin.startsWith("woman_") ? origin[1] : origin[0];
    }

    /** Подходит ли имя к скину: для автотестов. */
    public static boolean fits(Look look) {
        if (look.patrol()) {
            return List.of(PATROL_NAMES).contains(look.name());
        }
        return List.of(names(look.skin())).contains(look.name());
    }

    public static boolean isWalker(Entity entity) {
        return entity.getTags().contains(Pedestrians.WALKER_TAG);
    }

    private static Component name(Entity npc) {
        return npc.getCustomName() != null ? npc.getCustomName() : npc.getType().getDescription();
    }

    /** «Анна: текст» — реплика жителя. */
    public static Component says(Entity npc, String text) {
        return Component.empty()
                .append(name(npc).copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    private static String any(List<String> lines, RandomSource random) {
        return lines.get(random.nextInt(lines.size()));
    }

    // --- разговор -----------------------------------------------------------------

    /** Клик по прохожему: остановился, ответил, предложил варианты. */
    public static void talk(ServerPlayer player, Entity npc) {
        if (Robbery.armed(player)) {
            lastOutcome = "scared";
            player.sendSystemMessage(says(npc, any(SCARED, player.getRandom())));
            Pedestrians.flee(npc, player.position());
            return;
        }
        Pedestrians.pause(npc, player, 20 * 20);
        if (npc.getTags().contains(PATROL_TAG)) {
            lastOutcome = "talk:patrol";
            player.sendSystemMessage(says(npc, "Всё спокойно. Нужна помощь — звони 112."));
            player.sendSystemMessage(options(npc, "way:police", "way:hospital", "bye"));
            return;
        }
        lastOutcome = "talk:citizen";
        player.sendSystemMessage(says(npc, any(GREETINGS, player.getRandom())));
        player.sendSystemMessage(options(npc, "chat", "way:bank", "way:atm", "way:hospital",
                "way:metro", "way:food", "beg", "bye"));
    }

    private static Component options(Entity npc, String... actions) {
        MutableComponent line = Component.literal("  ");
        for (String action : actions) {
            String label = action.startsWith("way:")
                    ? Component.translatable("citylife.citizen.way",
                    WAYS.get(action.substring(4))).getString()
                    : Component.translatable("citylife.citizen." + action).getString();
            String command = "/citizen " + npc.getUUID() + " " + action;
            line.append(Component.literal("[" + label + "]").withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal(label))))).append(" ");
        }
        return line;
    }

    /** Ближайшая метка навигатора с таким значком. */
    public static Waypoint nearest(String icon, Vec3 from) {
        Waypoint best = null;
        double bestDist = Double.MAX_VALUE;
        for (Waypoint point : CityLandmarks.ALL) {
            if (!icon.equals(point.icon())) {
                continue;
            }
            double d = from.distanceToSqr(point.x() + 0.5D, point.y(), point.z() + 0.5D);
            if (d < bestDist) {
                bestDist = d;
                best = point;
            }
        }
        return best;
    }

    /** Нажатие варианта в чате. */
    public static int answer(ServerPlayer player, Entity npc, String action) {
        RandomSource random = player.getRandom();
        if (action.startsWith("way:")) {
            Waypoint point = nearest(action.substring(4), player.position());
            if (point == null) {
                player.sendSystemMessage(says(npc, "Не знаю, честно."));
                return 0;
            }
            int metres = (int) Math.sqrt(player.position().distanceToSqr(point.x(), point.y(),
                    point.z()));
            CityData.get(player.server).setRoute(player.getUUID(), point);
            dev.lscity.citylife.net.Net.sendRoute(player, point);
            lastOutcome = "way:" + point.name();
            player.sendSystemMessage(says(npc, "«" + point.name() + "» — метров " + metres
                    + " отсюда. Поставлю тебе маршрут в навигаторе."));
            return 1;
        }
        switch (action) {
            case "chat" -> {
                lastOutcome = "chat";
                player.sendSystemMessage(says(npc, any(CHAT, random)));
                player.sendSystemMessage(options(npc, "chat", "bye"));
            }
            case "beg" -> {
                long now = player.level().getGameTime();
                Long last = BEGGED.get(player.getUUID());
                if (last != null && now - last < 20L * 300) {
                    lastOutcome = "beg:again";
                    player.sendSystemMessage(says(npc, "Ты уже просил. Иди работай!"));
                } else if (random.nextInt(100) < 35) {
                    BEGGED.put(player.getUUID(), now);
                    long gift = 50 + random.nextInt(11) * 10;
                    Money.give(player, gift);
                    lastOutcome = "beg:gave";
                    player.sendSystemMessage(says(npc, "Держи " + Money.format(gift)
                            + ". Только не на ерунду."));
                } else {
                    BEGGED.put(player.getUUID(), now);
                    lastOutcome = "beg:no";
                    player.sendSystemMessage(says(npc, "Самому не хватает, извини."));
                }
            }
            default -> {
                lastOutcome = "bye";
                player.sendSystemMessage(says(npc, "Пока!"));
                Pedestrians.resume(npc);
            }
        }
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("citizen")
                .then(Commands.argument("npc", StringArgumentType.word())
                        .then(Commands.argument("action", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    UUID id;
                                    try {
                                        id = UUID.fromString(StringArgumentType.getString(ctx, "npc"));
                                    } catch (IllegalArgumentException bad) {
                                        return 0;
                                    }
                                    Entity npc = player.serverLevel().getEntity(id);
                                    if (npc == null || !npc.isAlive() || npc.distanceTo(player) > 10) {
                                        player.displayClientMessage(Component.translatable(
                                                "citylife.citizen.gone").withStyle(
                                                ChatFormatting.GRAY), true);
                                        return 0;
                                    }
                                    return answer(player, npc,
                                            StringArgumentType.getString(ctx, "action"));
                                }))));
    }

    // --- нападение и смерть ----------------------------------------------------------

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        Entity victim = event.getEntity();
        if (victim.level().isClientSide() || !isWalker(victim)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        Vec3 from = source != null ? source.position() : victim.position();
        panic(victim, from);
        if (!(source instanceof ServerPlayer attacker)) {
            return;
        }
        long now = attacker.level().getGameTime();
        Long last = FIGHTS.get(attacker.getUUID());
        if (last == null || now - last > 1200) {
            FIGHTS.put(attacker.getUUID(), now);
            boolean officer = victim.getTags().contains(PATROL_TAG);
            Wanted.crime(attacker, officer ? 2 : 1, officer ? "citylife.wanted.assault_officer"
                    : "citylife.wanted.assault_citizen");
        }
    }

    /** Прохожий кричит и убегает; соседи рядом тоже разбегаются. */
    private static void panic(Entity victim, Vec3 from) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (!victim.getTags().contains(PATROL_TAG)) {
            Pedestrians.flee(victim, from);
            shout(level, victim, any(HURT, level.random));
        }
        for (Mob other : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(12),
                e -> e != victim && isWalker(e) && !e.getTags().contains(PATROL_TAG))) {
            Pedestrians.flee(other, from);
        }
    }

    /** Реплика для всех игроков в 16 блоках. */
    private static void shout(ServerLevel level, Entity npc, String text) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                npc.getBoundingBox().inflate(16))) {
            player.sendSystemMessage(says(npc, text));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !isWalker(victim)) {
            return;
        }
        long cash = dropCash(level, victim);
        CityLife.LOG.info("City Life: прохожий {} погиб, выпало {} ₽", name(victim).getString(), cash);
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            boolean officer = victim.getTags().contains(PATROL_TAG);
            Wanted.crime(killer, officer ? 3 : 2, officer ? "citylife.wanted.kill_officer"
                    : "citylife.wanted.murder_citizen");
            Emergency.alarm(killer, victim.position(), dev.lscity.citylife.data.Texts.ru(
                    "citylife.citizen.alarm_place"));
        }
    }

    /** Кошелёк прохожего падает на землю наличными. Возвращает сумму. */
    public static long dropCash(ServerLevel level, Entity victim) {
        int max = CityConfig.CONFIG.citizenCashMax.get();
        if (max <= 0) {
            return 0;
        }
        long cash = Math.max(10, (long) (max * (0.1D + 0.9D * level.random.nextDouble())) / 10 * 10);
        for (ItemStack stack : Money.stacks(cash)) {
            ItemEntity item = new ItemEntity(level, victim.getX(), victim.getY() + 0.5D,
                    victim.getZ(), stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        return cash;
    }

    /** У прохожих и патрульных ничего, кроме кошелька, не выпадает (пистолет тоже). */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (isWalker(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // --- город вокруг -----------------------------------------------------------------

    /** Раз в 2 секунды: прохожие здороваются с проходящими и боятся оружия. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null
                || event.getServer().overworld().getGameTime() % 40 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level || player.isSpectator()) {
                continue;
            }
            boolean armed = Robbery.armed(player);
            AABB around = player.getBoundingBox().inflate(armed ? 7 : 3);
            for (Mob npc : level.getEntitiesOfClass(Mob.class, around,
                    e -> isWalker(e) && !e.getTags().contains(PATROL_TAG))) {
                if (armed) {
                    if (Pedestrians.flee(npc, player.position()) && level.random.nextInt(3) == 0) {
                        player.displayClientMessage(says(npc, any(SCARED, level.random)), true);
                    }
                    continue;
                }
                Long last = GREETED.get(player.getUUID());
                if ((last == null || now - last > 20L * 45) && level.random.nextInt(4) == 0) {
                    GREETED.put(player.getUUID(), now);
                    npc.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                            player.getEyePosition());
                    player.displayClientMessage(says(npc, any(GREETINGS, level.random)), true);
                    level.playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_AMBIENT,
                            SoundSource.NEUTRAL, 0.4F, 1.3F);
                }
                break;
            }
        }
    }
}
