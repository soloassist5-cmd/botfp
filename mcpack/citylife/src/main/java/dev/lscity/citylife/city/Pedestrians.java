package dev.lscity.citylife.city;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.trade.ShopCatalog;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Живой город: прохожие и продавцы, которые замечают игрока.
 *
 * Прохожие появляются вокруг игроков (не у них на глазах, а в 20–44
 * блоках) и ходят от входа к входу: точки — тротуар перед каждым участком,
 * их выгружает генератор города (citylife/tools/gen_walk.py). Путь между
 * точками ищет обычный поиск пути, поэтому люди идут по тротуарам и
 * переходам. Ушедших далеко от всех игроков мод убирает — нагрузка на
 * сервер не растёт с размером города.
 *
 * Продавцы стоят на месте (у них выключен ИИ), но поворачиваются лицом
 * к игроку, который подошёл к прилавку.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Pedestrians {

    public static final String WALKER_TAG = "citylife_walker";

    private static final String[] SKINS = {"citizen_a", "citizen_b", "citizen_c", "citizen_d",
            "clerk", "realtor", "dealer", "shopkeeper"};
    private static final String[] NAMES = {"Анна", "Максим", "Ольга", "Дэвид", "Мария", "Иван",
            "Карлос", "Лиза", "Артём", "Джессика", "Павел", "Софья", "Майкл", "Ника", "Руслан",
            "Эмили", "Денис", "Алина", "Хосе", "Виктор"};

    /**
     * Прохожий: куда идёт и по какому пути. Путь строит обычный поиск пути
     * Minecraft, а шагает прохожего мод — у жителей Easy NPC свой ИИ, который
     * сбрасывает навигацию, поэтому их ИИ выключен, а позицию двигаем сами.
     */
    private static final class Walker {
        final UUID id;
        BlockPos target;
        net.minecraft.world.level.pathfinder.Path path;
        int stuck;
        /** Не убирать вдали от игроков (наряды 112, самотесты). */
        boolean pinned;
        /** Сам выбирает новую цель, когда дошёл (прохожие), или ждёт команды. */
        boolean wander = true;
        double speed = STEP;

        Walker(UUID id) {
            this.id = id;
        }
    }

    /** Скорость прохожего, блоков за тик (~2,4 блока в секунду). */
    private static final double STEP = 0.12D;

    private static final Map<UUID, Walker> WALKERS = new HashMap<>();
    private static List<BlockPos> points;

    private Pedestrians() {
    }

    // --- точки ------------------------------------------------------------------

    private static synchronized List<BlockPos> points() {
        if (points != null) {
            return points;
        }
        List<BlockPos> out = new ArrayList<>();
        try (InputStream in = Pedestrians.class.getResourceAsStream("/data/citylife/walk.json")) {
            if (in != null) {
                JsonArray array = JsonParser.parseReader(new InputStreamReader(in,
                        StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("points");
                for (JsonElement element : array) {
                    JsonArray p = element.getAsJsonArray();
                    out.add(new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(),
                            p.get(2).getAsInt()));
                }
            }
        } catch (Exception error) {
            CityLife.LOG.error("City Life: не прочитать точки прохожих", error);
        }
        points = out;
        return out;
    }

    /** Случайная точка на расстоянии от min до max блоков от центра, или null. */
    private static BlockPos pointNear(RandomSource random, Vec3 centre, double min, double max) {
        List<BlockPos> all = points();
        List<BlockPos> fit = new ArrayList<>();
        for (BlockPos p : all) {
            double d = Math.sqrt(p.distToCenterSqr(centre));
            if (d >= min && d <= max) {
                fit.add(p);
            }
        }
        return fit.isEmpty() ? null : fit.get(random.nextInt(fit.size()));
    }

    // --- тик --------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long tick = level.getGameTime();
        if (tick % 10 == 0 && CityConfig.CONFIG.npcFacePlayers.get()) {
            faceCustomers(server);
        }
        step(level);
        if (tick % 20 != 0) {
            return;
        }
        walk(server, level);
        if (tick % 60 == 0) {
            spawn(server, level);
        }
    }

    private static List<ServerPlayer> players(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() == server.overworld() && !p.isSpectator()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Раз в секунду: убрать ушедших далеко, дать новую цель дошедшим. */
    private static void walk(MinecraftServer server, ServerLevel level) {
        List<ServerPlayer> players = players(server);
        for (Iterator<Walker> it = WALKERS.values().iterator(); it.hasNext(); ) {
            Walker w = it.next();
            Entity entity = level.getEntity(w.id);
            if (!(entity instanceof Mob mob) || !entity.isAlive()) {
                it.remove();
                continue;
            }
            if (!w.pinned) {
                double nearest = Double.MAX_VALUE;
                for (ServerPlayer p : players) {
                    nearest = Math.min(nearest, p.distanceToSqr(entity));
                }
                if (nearest > 72 * 72) {
                    entity.discard();
                    it.remove();
                    continue;
                }
            }
            if (!w.wander) {
                continue;
            }
            if (w.path == null || w.path.isDone() || w.stuck > 40) {
                BlockPos next = w.pinned && w.target != null && w.path == null ? w.target
                        : pointNear(mob.getRandom(), entity.position(), 10, 40);
                route(mob, w, next);
            }
        }
    }

    private static void route(Mob mob, Walker w, BlockPos to) {
        w.target = to;
        w.stuck = 0;
        w.path = to == null ? null : mob.getNavigation().createPath(to, 1);
    }

    /** Каждый тик: шаг к следующему узлу пути, лицом по ходу. */
    private static void step(ServerLevel level) {
        for (Walker w : WALKERS.values()) {
            if (w.path == null || w.path.isDone()) {
                continue;
            }
            Entity entity = level.getEntity(w.id);
            if (entity == null) {
                continue;
            }
            Vec3 node = Vec3.atBottomCenterOf(w.path.getNextNodePos());
            Vec3 pos = entity.position();
            Vec3 delta = node.subtract(pos);
            double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            if (flat < 0.15D && Math.abs(delta.y) < 1.1D) {
                w.path.advance();
                continue;
            }
            double k = Math.min(1.0D, w.speed / Math.max(flat, 1.0E-4D));
            // По высоте — сразу на уровень узла: ступеньки и бордюры.
            double y = Math.abs(delta.y) > 0.01D ? node.y : pos.y;
            float yaw = (float) (Math.atan2(delta.z, delta.x) * 180.0D / Math.PI) - 90.0F;
            entity.moveTo(pos.x + delta.x * k, y, pos.z + delta.z * k, yaw, 0F);
            entity.setYHeadRot(yaw);
            if (entity instanceof Mob mob) {
                mob.setYBodyRot(yaw);
            }
            w.stuck = flat > 0.15D && k < 1.0D ? 0 : w.stuck + 1;
        }
    }

    /**
     * Вести любого жителя (сотрудника 112) к точке: мод шагает его по пути,
     * как прохожего, но сам он цель не меняет и вдали от игроков не пропадает.
     * speed — блоков за тик (0.12 — шаг, 0.22 — бег).
     */
    public static void lead(Entity entity, BlockPos to, double speed) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        Walker w = WALKERS.computeIfAbsent(entity.getUUID(), id -> {
            makeWalker(entity);
            Walker fresh = new Walker(id);
            fresh.pinned = true;
            fresh.wander = false;
            return fresh;
        });
        w.speed = speed;
        if (w.target == null || !w.target.equals(to) || w.path == null || w.path.isDone()) {
            route(mob, w, to);
        }
    }

    /** Остановить ведомого жителя. */
    public static void halt(Entity entity) {
        Walker w = WALKERS.get(entity.getUUID());
        if (w != null) {
            w.path = null;
        }
    }

    /** Отправить прохожего в точку (для самотестов): его не уберут вдали от игроков. */
    public static void sendTo(Entity entity, BlockPos to) {
        Walker w = WALKERS.get(entity.getUUID());
        if (w != null && entity instanceof Mob mob) {
            w.pinned = true;
            route(mob, w, to);
        }
    }

    private static void spawn(MinecraftServer server, ServerLevel level) {
        int perPlayer = CityConfig.CONFIG.pedestrians.get();
        int max = CityConfig.CONFIG.pedestriansMax.get();
        if (perPlayer <= 0 || WALKERS.size() >= max) {
            return;
        }
        List<ServerPlayer> players = players(server);
        for (ServerPlayer player : players) {
            if (WALKERS.size() >= max) {
                return;
            }
            AABB around = player.getBoundingBox().inflate(48);
            int near = level.getEntitiesOfClass(Mob.class, around,
                    e -> e.getTags().contains(WALKER_TAG)).size();
            if (near >= perPlayer) {
                continue;
            }
            BlockPos at = pointNear(player.getRandom(), player.position(), 20, 44);
            if (at == null || !level.isLoaded(at)) {
                continue;
            }
            boolean seen = false;
            for (ServerPlayer other : players) {
                seen |= other.distanceToSqr(Vec3.atCenterOf(at)) < 16 * 16;
            }
            if (!seen) {
                spawnWalker(level, at, player.getRandom());
            }
        }
    }

    /** Поставить прохожего в точку. Возвращает сущность или null. */
    public static Entity spawnWalker(ServerLevel level, BlockPos at, RandomSource random) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "easy_npc:humanoid");
        tag.putString("CustomName", Component.Serializer.toJson(
                Component.literal(NAMES[random.nextInt(NAMES.length)])));
        tag.putBoolean("CustomNameVisible", false);
        tag.putBoolean("Invulnerable", true);
        ListTag tags = new ListTag();
        for (String t : new String[]{WALKER_TAG, "citylife_npc", "citylife_citizen",
                ShopCatalog.GEN_TAG}) {
            tags.add(StringTag.valueOf(t));
        }
        tag.put("Tags", tags);
        CompoundTag skin = new CompoundTag();
        skin.putString("Type", "RESOURCE_LOCATION");
        skin.putString("Texture", "citylife:textures/entity/npc/"
                + SKINS[random.nextInt(SKINS.length)] + ".png");
        skin.putString("Name", "");
        skin.putString("URL", "");
        tag.put("SkinData", skin);
        float yaw = random.nextFloat() * 360F;
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, yaw, 0F);
            return e;
        });
        if (entity == null) {
            return null;
        }
        makeWalker(entity);
        WALKERS.put(entity.getUUID(), new Walker(entity.getUUID()));
        if (!level.addFreshEntity(entity)) {
            WALKERS.remove(entity.getUUID());
            return null;
        }
        return entity;
    }

    /**
     * Поиску пути нужна дальность обзора (FOLLOW_RANGE): у жителя Easy NPC
     * она нулевая, и путь не строится. ИИ оставляем выключенным — шагает
     * прохожего мод, а свой ИИ Easy NPC сбивал бы навигацию.
     */
    public static void makeWalker(Entity entity) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            var speed = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getBaseValue() < 0.2D) {
                speed.setBaseValue(0.25D);
            }
            // Дальность поиска пути: у статуи она нулевая, и путь не строится.
            var range = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            if (range != null && range.getBaseValue() < 48D) {
                range.setBaseValue(48D);
            }
        }
    }

    public static int count() {
        return WALKERS.size();
    }

    /** Прохожие не переживают перезапуск: появятся новые вокруг игроков. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity().getTags().contains(WALKER_TAG)
                && !WALKERS.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    // --- продавцы смотрят на покупателя ------------------------------------------

    private static void faceCustomers(MinecraftServer server) {
        for (ServerPlayer player : players(server)) {
            AABB around = player.getBoundingBox().inflate(6);
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, around,
                    e -> e.isNoAi() && e.getTags().contains("citylife_npc")
                            && !e.getTags().contains(WALKER_TAG))) {
                mob.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
                mob.setYHeadRot(mob.getYRot());
                mob.setYBodyRot(mob.getYRot());
            }
        }
    }
}
