package dev.lscity.citylife.city;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Служба 112: полиция, скорая и пожарные приезжают на вызов.
 *
 * Звонок из телефона (приложение «112») или командой /sos ставит вызов
 * в очередь. Через 30 секунд у звонящего останавливается машина службы
 * с сиреной, из неё выходят двое. С нарядом говорят кликом: в чате
 * появляются варианты — вылечить, задержать нарушителя, потушить пожар,
 * «отбой». Полиция сама задерживает разыскиваемых рядом, пожарные сами
 * тушат огонь вокруг. Через 3 минуты (или после «отбоя») наряд уезжает.
 *
 * Люди — гуманоиды Easy NPC с тегами citylife_npc и citylife_resp_<служба>,
 * поэтому клик по ним ловит тот же ShopHandler, что и у продавцов.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Emergency {

    private static final String CREW_TAG = "citylife_crew";

    /** Вызов, который ещё едет. */
    private record Call(String kind, UUID caller, Vec3 where, long due) {
    }

    /** Наряд на месте. */
    private static final class Crew {
        final int id;
        final String kind;
        final UUID caller;
        final List<UUID> people = new ArrayList<>();
        UUID vehicle;
        BlockPos pos;
        long arrived;
        long leaveAt;
        /** Кому полиция крикнула «стоять» и когда задержит. */
        final Map<UUID, Long> stop = new LinkedHashMap<>();

        Crew(int id, String kind, UUID caller) {
            this.id = id;
            this.kind = kind;
            this.caller = caller;
        }
    }

    private static final List<Call> CALLS = new ArrayList<>();
    private static final Map<Integer, Crew> CREWS = new LinkedHashMap<>();
    private static int nextCrew = 1;

    private Emergency() {
    }

    private static String service(String kind) {
        return Component.translatable("citylife.sos." + kind).getString();
    }

    // --- вызов --------------------------------------------------------------------

    /** Принять вызов. Возвращает false, если такая служба уже едет к игроку. */
    public static boolean call(ServerPlayer player, String kind) {
        if (!List.of("police", "medic", "fire").contains(kind)) {
            return false;
        }
        for (Call call : CALLS) {
            if (call.caller().equals(player.getUUID()) && call.kind().equals(kind)) {
                player.displayClientMessage(Component.translatable("citylife.sos.already",
                        service(kind)).withStyle(ChatFormatting.YELLOW), false);
                return false;
            }
        }
        for (Crew crew : CREWS.values()) {
            if (crew.caller.equals(player.getUUID()) && crew.kind.equals(kind)) {
                player.displayClientMessage(Component.translatable("citylife.sos.on_site",
                        service(kind)).withStyle(ChatFormatting.YELLOW), false);
                return false;
            }
        }
        int delay = CityConfig.CONFIG.responderDelay.get();
        CALLS.add(new Call(kind, player.getUUID(), player.position(),
                player.level().getGameTime() + delay * 20L));
        player.displayClientMessage(Component.translatable("citylife.sos.accepted",
                service(kind), delay).withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    // --- приезд -------------------------------------------------------------------

    private static final Map<String, String[]> NAMES = Map.of(
            "police", new String[]{"Сержант Петров", "Лейтенант Смирнова", "Офицер Ковальски",
                    "Сержант Рамирес"},
            "medic", new String[]{"Фельдшер Иванова", "Врач Соколов", "Фельдшер Джонсон",
                    "Врач Ли"},
            "fire", new String[]{"Пожарный Кузнецов", "Пожарный Миллер", "Командир Орлов",
                    "Пожарный Гарсия"});
    private static final Map<String, String> SKIN = Map.of(
            "police", "police", "medic", "medic", "fire", "firefighter");
    private static final Map<String, String> VEHICLE = Map.of(
            "police", "vehicle:off_roader", "medic", "vehicle:mini_bus", "fire", "vehicle:mini_bus");
    private static final Map<String, Integer> COLOUR = Map.of(
            "police", 0x1C2B5A, "medic", 0xF4F4F4, "fire", 0xC8201E);

    /** Прислать наряд сразу, без ожидания: для /sos dispatch и автотестов. */
    public static int dispatch(MinecraftServer server, String kind, Vec3 where) {
        return arrive(server, new Call(kind, new UUID(0L, 0L), where, 0L));
    }

    /** Сущности наряда (люди и машина), пока он на месте. */
    public static List<UUID> crewEntities(int id) {
        Crew crew = CREWS.get(id);
        List<UUID> out = new ArrayList<>();
        if (crew != null) {
            out.addAll(crew.people);
            if (crew.vehicle != null) {
                out.add(crew.vehicle);
            }
        }
        return out;
    }

    /** Отпустить наряд немедленно. */
    public static void recall(MinecraftServer server, int id) {
        Crew crew = CREWS.remove(id);
        if (crew != null) {
            leave(server, crew);
        }
    }

    private static int arrive(MinecraftServer server, Call call) {
        ServerPlayer caller = server.getPlayerList().getPlayer(call.caller());
        ServerLevel level = server.overworld();
        Vec3 target = call.where();
        if (caller != null && caller.level() == level
                && caller.position().distanceToSqr(call.where()) < 150 * 150) {
            target = caller.position();
        }
        BlockPos spot = findSpot(level, BlockPos.containing(target));
        if (spot == null) {
            spot = BlockPos.containing(target);
        }
        Crew crew = new Crew(nextCrew++, call.kind(), call.caller());
        crew.pos = spot;
        crew.arrived = level.getGameTime();
        crew.leaveAt = crew.arrived + CityConfig.CONFIG.responderStay.get() * 20L;
        // Регистрируем наряд до спавна: onJoin пропускает только живые наряды.
        CREWS.put(crew.id, crew);

        float yaw = (float) (Mth.atan2(target.z - spot.getZ(), target.x - spot.getX())
                * (180F / Math.PI)) - 90F;
        Entity car = spawnVehicle(level, crew, spot, yaw);
        if (car != null) {
            crew.vehicle = car.getUUID();
        }
        String[] names = NAMES.get(call.kind());
        int first = level.random.nextInt(names.length);
        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? -1.8D : 1.8D;
            double rad = Math.toRadians(yaw);
            double x = spot.getX() + 0.5D + Math.cos(rad) * side - Math.sin(rad) * 2.2D;
            double z = spot.getZ() + 0.5D + Math.sin(rad) * side + Math.cos(rad) * 2.2D;
            Entity npc = spawnPerson(level, crew, names[(first + i) % names.length],
                    new Vec3(x, groundY(level, x, spot.getY(), z), z), yaw);
            if (npc != null) {
                crew.people.add(npc.getUUID());
            }
        }
        if (caller != null) {
            caller.sendSystemMessage(Component.translatable("citylife.sos.arrived",
                    service(call.kind())).withStyle(ChatFormatting.AQUA));
            caller.sendSystemMessage(Component.translatable("citylife.sos.talk_hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        if ("fire".equals(crew.kind)) {
            extinguish(level, crew.pos, 24);
        }
        return crew.id;
    }

    /** Ровное место под машину в 4–12 блоках от точки вызова. */
    private static BlockPos findSpot(ServerLevel level, BlockPos near) {
        for (int r = 4; r <= 12; r += 2) {
            for (int step = 0; step < 16; step++) {
                double a = step * Math.PI / 8;
                int x = near.getX() + (int) Math.round(Math.cos(a) * r);
                int z = near.getZ() + (int) Math.round(Math.sin(a) * r);
                for (int y = near.getY() + 4; y >= near.getY() - 6; y--) {
                    if (flat(level, x, y, z)) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean flat(ServerLevel level, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = new BlockPos(x + dx, y - 1, z + dz);
                if (!level.getBlockState(floor).isFaceSturdy(level, floor,
                        net.minecraft.core.Direction.UP)) {
                    return false;
                }
                for (int dy = 0; dy <= 2; dy++) {
                    if (!level.getBlockState(new BlockPos(x + dx, y + dy, z + dz))
                            .getCollisionShape(level, floor).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static double groundY(ServerLevel level, double x, int y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos pos = base.above(dy);
            if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
                    net.minecraft.core.Direction.UP)
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos).isEmpty()) {
                return pos.getY();
            }
        }
        return y;
    }

    private static ListTag rotation(float yaw) {
        ListTag list = new ListTag();
        list.add(FloatTag.valueOf(yaw));
        list.add(FloatTag.valueOf(0F));
        return list;
    }

    private static ListTag tags(Crew crew, String... extra) {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(CREW_TAG));
        list.add(StringTag.valueOf(CREW_TAG + "_" + crew.id));
        for (String tag : extra) {
            list.add(StringTag.valueOf(tag));
        }
        return list;
    }

    private static Entity spawnVehicle(ServerLevel level, Crew crew, BlockPos spot, float yaw) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", VEHICLE.get(crew.kind));
        tag.putInt("Color", COLOUR.get(crew.kind));
        CompoundTag engine = new CompoundTag();
        engine.putString("id", "vehicle:iron_large_engine");
        engine.putByte("Count", (byte) 1);
        tag.put("EngineStack", engine);
        CompoundTag wheel = new CompoundTag();
        wheel.putString("id", "vehicle:standard_wheel");
        wheel.putByte("Count", (byte) 1);
        tag.put("WheelStack", wheel);
        tag.putBoolean("Invulnerable", true);
        tag.put("Tags", tags(crew));
        tag.put("Rotation", rotation(yaw));
        return spawn(level, tag, new Vec3(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D), yaw);
    }

    private static Entity spawnPerson(ServerLevel level, Crew crew, String name, Vec3 at,
                                      float yaw) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "easy_npc:humanoid");
        tag.putString("CustomName", Component.Serializer.toJson(Component.literal(name)));
        tag.putBoolean("CustomNameVisible", true);
        tag.put("Tags", tags(crew, "citylife_npc", "citylife_resp_" + crew.kind));
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("NoAI", true);
        tag.put("Rotation", rotation(yaw + 180F));
        CompoundTag skin = new CompoundTag();
        skin.putString("Type", "RESOURCE_LOCATION");
        skin.putString("Texture", "citylife:textures/entity/npc/" + SKIN.get(crew.kind) + ".png");
        skin.putString("Name", "");
        skin.putString("URL", "");
        tag.put("SkinData", skin);
        return spawn(level, tag, at, yaw + 180F);
    }

    private static Entity spawn(ServerLevel level, CompoundTag tag, Vec3 at, float yaw) {
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(at.x, at.y, at.z, yaw, 0F);
            e.setYHeadRot(yaw);
            return e;
        });
        if (entity == null) {
            CityLife.LOG.warn("City Life: не создать {} для наряда 112", tag.getString("id"));
            return null;
        }
        return level.tryAddFreshEntityWithPassengers(entity) ? entity : null;
    }

    // --- отъезд -------------------------------------------------------------------

    private static void leave(MinecraftServer server, Crew crew) {
        ServerLevel level = server.overworld();
        List<UUID> all = new ArrayList<>(crew.people);
        if (crew.vehicle != null) {
            all.add(crew.vehicle);
        }
        for (UUID id : all) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.ejectPassengers();
                entity.discard();
            }
        }
        level.playSound(null, crew.pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL,
                0.4F, 0.6F);
    }

    /** Старые наряды после перезапуска сервера в мир не возвращаются. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.getEntity().getTags().contains(CREW_TAG)) {
            return;
        }
        for (String tag : event.getEntity().getTags()) {
            if (tag.startsWith(CREW_TAG + "_")) {
                try {
                    if (CREWS.containsKey(Integer.parseInt(tag.substring(CREW_TAG.length() + 1)))) {
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // чужой тег — значит, наряд старый
                }
            }
        }
        event.setCanceled(true);
    }

    // --- каждый тик ---------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();

        for (Iterator<Call> it = CALLS.iterator(); it.hasNext(); ) {
            Call call = it.next();
            if (now >= call.due()) {
                it.remove();
                arrive(server, call);
            } else if ((call.due() - now) % 20 == 0) {
                ServerPlayer caller = server.getPlayerList().getPlayer(call.caller());
                if (caller != null) {
                    caller.displayClientMessage(Component.translatable("citylife.sos.eta",
                            service(call.kind()), (call.due() - now) / 20)
                            .withStyle(ChatFormatting.AQUA), true);
                }
            }
        }

        for (Iterator<Crew> it = CREWS.values().iterator(); it.hasNext(); ) {
            Crew crew = it.next();
            long age = now - crew.arrived;
            if (age < 60 && age % 5 == 0) {
                siren(level, crew, age);
            }
            if (now >= crew.leaveAt) {
                leave(server, crew);
                it.remove();
                continue;
            }
            if (age % 20 != 0) {
                continue;
            }
            if ("police".equals(crew.kind)) {
                patrol(server, crew, now);
            } else if ("fire".equals(crew.kind) && age % 100 == 0) {
                extinguish(level, crew.pos, 24);
            }
        }
    }

    private static void siren(ServerLevel level, Crew crew, long age) {
        float pitch = (age / 5) % 2 == 0 ? 1.5F : 1.1F;
        var sound = "fire".equals(crew.kind) ? SoundEvents.NOTE_BLOCK_BELL
                : SoundEvents.NOTE_BLOCK_BIT;
        level.playSound(null, crew.pos, sound.value(), SoundSource.NEUTRAL, 2.0F, pitch);
    }

    /** Полиция на месте: «стоять!» разыскиваемым рядом, через 5 секунд — задержание. */
    private static void patrol(MinecraftServer server, Crew crew, long now) {
        LifeData life = LifeData.get(server);
        Vec3 centre = Vec3.atCenterOf(crew.pos);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != server.overworld() || life.jailUntil(player.getUUID()) > 0) {
                continue;
            }
            double dist = player.position().distanceTo(centre);
            Long deadline = crew.stop.get(player.getUUID());
            if (deadline == null) {
                if (life.wanted(player.getUUID()) > 0 && dist < 24) {
                    crew.stop.put(player.getUUID(), now + 100);
                    player.sendSystemMessage(Component.translatable("citylife.police.freeze",
                            player.getGameProfile().getName()).withStyle(ChatFormatting.BLUE));
                    player.sendSystemMessage(options(crew, "surrender"));
                }
            } else if (now >= deadline) {
                crew.stop.remove(player.getUUID());
                if (life.wanted(player.getUUID()) == 0) {
                    continue;
                }
                if (dist < 16) {
                    Wanted.arrest(player, false);
                } else {
                    Wanted.crime(player, 1, "citylife.wanted.fled");
                }
            }
        }
    }

    private static int extinguish(ServerLevel level, BlockPos centre, int radius) {
        int out = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -8, -radius),
                centre.offset(radius, 16, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BaseFireBlock) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                out++;
            }
        }
        for (Entity entity : level.getEntities((Entity) null,
                new net.minecraft.world.phys.AABB(centre).inflate(radius), Entity::isOnFire)) {
            entity.clearFire();
            out++;
        }
        if (out > 0) {
            level.playSound(null, centre, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    1.0F, 1.0F);
        }
        return out;
    }

    // --- разговор -----------------------------------------------------------------

    private static Crew crewOf(Entity npc) {
        for (Crew crew : CREWS.values()) {
            if (crew.people.contains(npc.getUUID())) {
                return crew;
            }
        }
        return null;
    }

    /** Кнопка в чате: клик выполняет /sos act <наряд> <действие>. */
    private static MutableComponent button(Crew crew, String action) {
        String command = "/sos act " + crew.id + " " + action;
        return Component.literal("[")
                .append(Component.translatable("citylife.crew." + action,
                        Money.format(CityConfig.CONFIG.medicFee.get())))
                .append("]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(command))));
    }

    private static Component options(Crew crew, String... actions) {
        MutableComponent line = Component.literal("  ");
        for (String action : actions) {
            line.append(button(crew, action)).append(" ");
        }
        return line;
    }

    /** Клик по сотруднику службы. */
    public static void talk(ServerPlayer player, Entity npc, String kind) {
        Crew crew = crewOf(npc);
        Component name = npc.getCustomName() == null ? Component.literal(service(kind))
                : npc.getCustomName();
        MutableComponent head = Component.empty()
                .append(name.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY));
        if (crew == null) {
            player.sendSystemMessage(head.append(Component.translatable("citylife.crew.busy")));
            return;
        }
        LifeData life = LifeData.get(player.server);
        switch (kind) {
            case "medic" -> {
                player.sendSystemMessage(head.append(Component.translatable("citylife.crew.medic_hi")));
                player.sendSystemMessage(options(crew, "heal", "dismiss"));
            }
            case "fire" -> {
                player.sendSystemMessage(head.append(Component.translatable("citylife.crew.fire_hi")));
                player.sendSystemMessage(options(crew, "extinguish", "dismiss"));
            }
            default -> {
                if (life.wanted(player.getUUID()) > 0) {
                    player.sendSystemMessage(head.append(Component.translatable(
                            "citylife.crew.police_wanted", Wanted.stars(life.wanted(player.getUUID())))));
                    player.sendSystemMessage(options(crew, "surrender"));
                } else {
                    player.sendSystemMessage(head.append(Component.translatable(
                            "citylife.crew.police_hi")));
                    player.sendSystemMessage(options(crew, "report", "dismiss"));
                }
            }
        }
    }

    /** Нажатие варианта в чате. */
    private static int act(ServerPlayer player, int id, String action) {
        Crew crew = CREWS.get(id);
        if (crew == null) {
            player.displayClientMessage(Component.translatable("citylife.crew.gone")
                    .withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        if (player.position().distanceTo(Vec3.atCenterOf(crew.pos)) > 16) {
            player.displayClientMessage(Component.translatable("citylife.crew.too_far")
                    .withStyle(ChatFormatting.RED), true);
            return 0;
        }
        LifeData life = LifeData.get(player.server);
        switch (action) {
            case "heal" -> {
                if (!"medic".equals(crew.kind)) {
                    return 0;
                }
                long fee = CityConfig.CONFIG.medicFee.get();
                boolean paid = fee == 0 || CityData.get(player.server).withdraw(player.getUUID(), fee);
                player.setHealth(player.getMaxHealth());
                player.getFoodData().eat(20, 1.0F);
                player.clearFire();
                List<MobEffect> bad = player.getActiveEffects().stream()
                        .map(e -> e.getEffect())
                        .filter(e -> e.getCategory() == MobEffectCategory.HARMFUL).toList();
                bad.forEach(player::removeEffect);
                player.sendSystemMessage(Component.translatable(paid
                        ? "citylife.crew.healed" : "citylife.crew.healed_free",
                        Money.format(fee)).withStyle(ChatFormatting.GREEN));
            }
            case "extinguish" -> {
                if (!"fire".equals(crew.kind)) {
                    return 0;
                }
                int count = extinguish(player.serverLevel(), player.blockPosition(), 24)
                        + extinguish(player.serverLevel(), crew.pos, 24);
                player.sendSystemMessage(Component.translatable("citylife.crew.extinguished", count)
                        .withStyle(ChatFormatting.GREEN));
            }
            case "report" -> {
                if (!"police".equals(crew.kind)) {
                    return 0;
                }
                ServerPlayer suspect = null;
                double best = 48 * 48;
                for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
                    double d = other.distanceToSqr(player);
                    if (other != player && life.wanted(other.getUUID()) > 0
                            && other.level() == player.level() && d < best) {
                        best = d;
                        suspect = other;
                    }
                }
                if (suspect == null) {
                    player.sendSystemMessage(Component.translatable("citylife.crew.no_suspect")
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    // Наряд едет к подозреваемому: он получает «стоять!» и 5 секунд.
                    crew.pos = suspect.blockPosition();
                    crew.stop.put(suspect.getUUID(), player.level().getGameTime() + 100);
                    suspect.sendSystemMessage(Component.translatable("citylife.police.freeze",
                            suspect.getGameProfile().getName()).withStyle(ChatFormatting.BLUE));
                    suspect.sendSystemMessage(options(crew, "surrender"));
                    player.sendSystemMessage(Component.translatable("citylife.crew.reported",
                            suspect.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
                }
            }
            case "surrender" -> {
                if (!"police".equals(crew.kind) || life.wanted(player.getUUID()) == 0) {
                    return 0;
                }
                crew.stop.remove(player.getUUID());
                Wanted.arrest(player, true);
            }
            case "dismiss" -> {
                crew.leaveAt = Math.min(crew.leaveAt, player.level().getGameTime() + 60);
                player.sendSystemMessage(Component.translatable("citylife.crew.bye")
                        .withStyle(ChatFormatting.GRAY));
            }
            default -> {
                return 0;
            }
        }
        return 1;
    }

    // --- команда ------------------------------------------------------------------

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("sos");
        for (String kind : new String[]{"police", "medic", "fire"}) {
            root.then(Commands.literal(kind).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                broadcast(player, kind);
                return call(player, kind) ? 1 : 0;
            }));
        }
        // Для администратора и ивентов: прислать наряд в любую точку.
        root.then(Commands.literal("dispatch").requires(source -> source.hasPermission(2))
                .then(Commands.argument("kind", StringArgumentType.word())
                        .then(Commands.argument("pos",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .blockPos())
                                .executes(ctx -> {
                                    String kind = StringArgumentType.getString(ctx, "kind");
                                    BlockPos pos = net.minecraft.commands.arguments.coordinates
                                            .BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    if (!List.of("police", "medic", "fire").contains(kind)) {
                                        return 0;
                                    }
                                    dispatch(ctx.getSource().getServer(), kind,
                                            Vec3.atBottomCenterOf(pos));
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Наряд " + kind + " на месте: " + pos.toShortString()
                                                    + ", нарядов всего " + CREWS.size()), true);
                                    return 1;
                                }))));
        root.then(Commands.literal("act")
                .then(Commands.argument("crew", IntegerArgumentType.integer(1))
                        .then(Commands.argument("action", StringArgumentType.word())
                                .executes(ctx -> act(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "crew"),
                                        StringArgumentType.getString(ctx, "action"))))));
        dispatcher.register(root);
    }

    /** Сообщение в общий чат, как и раньше: вызов видят игроки-«дежурные». */
    public static void broadcast(ServerPlayer player, String kind) {
        Component message = Component.translatable("citylife.sos.broadcast", service(kind),
                player.getGameProfile().getName(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ())
                .withStyle(ChatFormatting.RED);
        if (CityConfig.CONFIG.emergencyToEveryone.get()) {
            player.server.getPlayerList().broadcastSystemMessage(message, false);
        } else {
            player.server.getPlayerList().getPlayers().stream()
                    .filter(candidate -> player.server.getPlayerList()
                            .isOp(candidate.getGameProfile()))
                    .forEach(candidate -> candidate.sendSystemMessage(message));
            player.sendSystemMessage(message);
        }
    }
}
