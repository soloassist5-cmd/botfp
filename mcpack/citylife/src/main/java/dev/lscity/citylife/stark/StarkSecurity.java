package dev.lscity.citylife.stark;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.city.Wanted;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Охрана башни STARK — «Джарвис».
 *
 * Охраняемые зоны задаёт пульт охраны: в его данных лежат коробки
 * (подвал с Залом брони и мастерской). Пока пульт загружен, зоны работают.
 * Кто вошёл без допуска, слышит предупреждение и видит обратный отсчёт;
 * не ушёл за {@link #GRACE} тиков — тревога: сирена, красные мониторы и
 * лучи, звёзды розыска, запись в журнал. Пересёк луч лазерного датчика или
 * снял костюм со стойки — тревога сразу, без отсчёта.
 *
 * Допуск покупается у пульта (или выдаётся командой /stark access): с ним
 * можно ходить по зонам, печатать на 3D-принтере и брать костюмы.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class StarkSecurity {

    /** Сколько стоит допуск Stark Industries. */
    public static final long ACCESS_PRICE = 250_000;
    /** Отсчёт до тревоги: 10 секунд. */
    public static final int GRACE = 200;
    /** Тревога держится ещё 30 секунд после того, как нарушитель ушёл. */
    public static final int ALARM_TAIL = 600;
    /** Как далеко от пульта слышна сирена и видно состояние на мониторах. */
    public static final double RANGE = 96.0D;
    /** Стойки с костюмами Зала брони: метка и префикс метки с предметом. */
    public static final String SUIT_TAG = "citylife_suit";
    public static final String SUIT_ITEM = "citylife_suit=";

    private static final Map<GlobalPos, List<AABB>> ZONES = new HashMap<>();
    private static final Set<GlobalPos> LASERS = new HashSet<>();
    /** Когда нарушитель впервые замечен в зоне. */
    private static final Map<UUID, Long> SEEN = new HashMap<>();
    /** Кого уже приветствовали, чтобы не повторять на каждом шаге. */
    private static final Set<UUID> GREETED = new HashSet<>();
    private static long alarmUntil;
    private static String alarmBy = "";
    private static boolean lastSentAlarm;
    private static boolean lastSentArmed = true;

    private StarkSecurity() {
    }

    // --- регистрация зон и датчиков -------------------------------------------

    public static void addZones(Level level, BlockPos console, List<AABB> boxes) {
        if (!level.isClientSide) {
            ZONES.put(GlobalPos.of(level.dimension(), console), List.copyOf(boxes));
        }
    }

    public static void removeZones(Level level, BlockPos console) {
        ZONES.remove(GlobalPos.of(level.dimension(), console));
    }

    public static void addLaser(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            LASERS.add(GlobalPos.of(level.dimension(), pos));
        }
    }

    public static void removeLaser(Level level, BlockPos pos) {
        LASERS.remove(GlobalPos.of(level.dimension(), pos));
    }

    public static boolean inZone(Level level, net.minecraft.world.phys.Vec3 at) {
        for (var entry : ZONES.entrySet()) {
            if (!entry.getKey().dimension().equals(level.dimension())) {
                continue;
            }
            for (AABB box : entry.getValue()) {
                if (box.contains(at)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Свой ли игрок для охраны: допуск, творческий режим или наблюдатель. */
    public static boolean cleared(ServerPlayer player) {
        return player.isCreative() || player.isSpectator()
                || StarkData.get(player.server).cleared(player.getUUID());
    }

    public static boolean alarm(long now) {
        return now < alarmUntil;
    }

    // --- тревога ---------------------------------------------------------------

    /** Поднять тревогу из-за игрока: розыск, журнал, сирена. */
    public static void raise(ServerPlayer player, int stars, String reasonKey) {
        StarkData data = StarkData.get(player.server);
        long now = player.level().getGameTime();
        boolean fresh = !alarm(now) || !alarmBy.equals(player.getGameProfile().getName());
        alarmUntil = now + ALARM_TAIL;
        alarmBy = player.getGameProfile().getName();
        if (fresh) {
            data.log(now, Texts.ru("citylife.stark.log.alarm", alarmBy, Texts.ru(reasonKey)), true);
            Wanted.crime(player, stars, reasonKey);
            title(player, Component.translatable("citylife.stark.alarm_title")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.translatable("citylife.stark.alarm_sub"));
        }
        sync(player.server, true);
    }

    private static void title(ServerPlayer player, Component title, Component sub) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || ZONES.isEmpty() && LASERS.isEmpty()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (now % 5 != 0) {
            return;
        }
        StarkData data = StarkData.get(server);
        if (data.armed()) {
            watch(server, data, now);
            if (now % 10 == 0) {
                lasers(server);
            }
        } else {
            SEEN.clear();
        }
        if (alarm(now) && now % 20 == 0) {
            siren(server, now);
        }
        boolean alarm = alarm(now);
        if (alarm != lastSentAlarm || data.armed() != lastSentArmed || now % 100 == 0) {
            sync(server, false);
        }
        if (now % 100 == 0) {
            restock(server, now);
        }
    }

    /** Кто стоит в зонах: свои — приветствие, чужие — отсчёт и тревога. */
    private static void watch(MinecraftServer server, StarkData data, long now) {
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (check(player, data, now)) {
                inside.add(player.getUUID());
            }
        }
        SEEN.keySet().retainAll(inside);
        GREETED.retainAll(inside);
    }

    /** Проверить одного игрока; true — он в охраняемой зоне. */
    public static boolean check(ServerPlayer player, StarkData data, long now) {
        if (!inZone(player.level(), player.position())) {
            SEEN.remove(player.getUUID());
            GREETED.remove(player.getUUID());
            return false;
        }
        if (cleared(player)) {
            SEEN.remove(player.getUUID());
            if (GREETED.add(player.getUUID())) {
                player.displayClientMessage(Component.translatable("citylife.stark.welcome",
                        player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA), true);
            }
            return true;
        }
        long since = SEEN.computeIfAbsent(player.getUUID(), id -> {
            data.log(now, Texts.ru("citylife.stark.log.intruder", player.getGameProfile().getName()), false);
            title(player, Component.translatable("citylife.stark.warn_title")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatable("citylife.stark.warn_sub"));
            return now;
        });
        long left = GRACE - (now - since);
        if (left > 0) {
            if (now % 20 == 0) {
                player.displayClientMessage(Component.translatable("citylife.stark.countdown",
                        (left + 19) / 20).withStyle(ChatFormatting.GOLD), true);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8F, 1.6F);
            }
        } else {
            raise(player, 3, "citylife.wanted.stark_intrusion");
        }
        return true;
    }

    /** Лучи лазерных датчиков: кто без допуска пересёк — тревога сразу. */
    private static void lasers(MinecraftServer server) {
        for (GlobalPos pos : LASERS) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null || !level.isLoaded(pos.pos())
                    || !(level.getBlockEntity(pos.pos()) instanceof LaserBlockEntity laser)) {
                continue;
            }
            AABB beam = laser.beam();
            if (beam == null) {
                continue;
            }
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, beam)) {
                if (!cleared(player)) {
                    raise(player, 3, "citylife.wanted.stark_laser");
                }
            }
        }
    }

    private static void siren(MinecraftServer server, long now) {
        boolean high = now / 20 % 2 == 0;
        for (GlobalPos console : ZONES.keySet()) {
            ServerLevel level = server.getLevel(console.dimension());
            if (level == null) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.position().distanceTo(console.pos().getCenter()) < RANGE) {
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS,
                            1.0F, high ? 1.2F : 0.8F);
                }
            }
        }
    }

    /** Состояние охраны — на мониторы и лучи всем, кто рядом с пультом. */
    public static void sync(MinecraftServer server, boolean force) {
        StarkData data = StarkData.get(server);
        long now = server.overworld().getGameTime();
        lastSentAlarm = alarm(now);
        lastSentArmed = data.armed();
        CompoundTag state = state(data, now);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (force || near(player)) {
                Net.sendPanel(player, "stark", state, false);
            }
        }
    }

    private static boolean near(ServerPlayer player) {
        for (GlobalPos console : ZONES.keySet()) {
            if (console.dimension().equals(player.level().dimension())
                    && player.position().distanceTo(console.pos().getCenter()) < RANGE * 2) {
                return true;
            }
        }
        return false;
    }

    static CompoundTag state(StarkData data, long now) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("armed", data.armed());
        tag.putBoolean("alarm", alarm(now));
        tag.putString("by", alarmBy);
        tag.putInt("intruders", SEEN.size());
        tag.putInt("cleared", data.access().size());
        ListTag log = new ListTag();
        for (StarkData.Entry entry : data.log()) {
            CompoundTag line = new CompoundTag();
            line.putString("t", clock(entry.time()));
            line.putString("s", entry.text());
            line.putBoolean("a", entry.alarm());
            log.add(line);
        }
        tag.put("log", log);
        return tag;
    }

    /** Игровое время строкой «ЧЧ:ММ», как на часах в игре. */
    public static String clock(long time) {
        long day = (time + 6000) % 24000;
        return String.format("%02d:%02d", day / 1000, day % 1000 * 60 / 1000);
    }

    // --- пульт ------------------------------------------------------------------

    public static void openConsole(ServerPlayer player, BlockPos pos) {
        StarkData data = StarkData.get(player.server);
        CompoundTag tag = state(data, player.level().getGameTime());
        tag.putLong("pos", pos.asLong());
        tag.putBoolean("me", cleared(player));
        tag.putBoolean("bought", data.cleared(player.getUUID()));
        tag.putLong("price", ACCESS_PRICE);
        tag.putLong("balance", CityData.get(player.server).balance(player.getUUID()));
        ListTag cams = new ListTag();
        if (player.level().getBlockEntity(pos) instanceof SecurityConsoleBlockEntity console) {
            int n = 1;
            for (BlockPos cam : console.cameras()) {
                CompoundTag c = new CompoundTag();
                c.putLong("pos", cam.asLong());
                c.putString("name", console.cameraName(cam, n++));
                cams.add(c);
            }
        }
        tag.put("cams", cams);
        Net.sendPanel(player, "security", tag, true);
    }

    /** stark_*: действия на пульте. Игрок должен стоять у пульта. */
    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!(player.level().getBlockEntity(pos) instanceof SecurityConsoleBlockEntity console)
                || player.distanceToSqr(pos.getCenter()) > 64) {
            return;
        }
        StarkData data = StarkData.get(player.server);
        long now = player.level().getGameTime();
        String name = player.getGameProfile().getName();
        switch (action) {
            case "stark_buy" -> {
                if (data.cleared(player.getUUID())) {
                    break;
                }
                if (!CityData.get(player.server).withdraw(player.getUUID(), ACCESS_PRICE,
                        Texts.ru("citylife.statement.stark"), now)) {
                    player.displayClientMessage(Component.translatable("citylife.stark.no_money",
                            Money.format(ACCESS_PRICE)).withStyle(ChatFormatting.RED), false);
                    break;
                }
                data.grant(player.getUUID(), name);
                data.log(now, Texts.ru("citylife.stark.log.access", name), false);
                player.displayClientMessage(Component.translatable("citylife.stark.bought")
                        .withStyle(ChatFormatting.AQUA), false);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            case "stark_arm" -> {
                if (!cleared(player)) {
                    break;
                }
                boolean on = args.getBoolean("on");
                data.setArmed(on);
                data.log(now, Texts.ru(on ? "citylife.stark.log.armed" : "citylife.stark.log.disarmed", name),
                        false);
                if (!on) {
                    alarmUntil = 0;
                }
            }
            case "stark_reset" -> {
                if (!cleared(player) || !alarm(now)) {
                    break;
                }
                alarmUntil = 0;
                data.log(now, Texts.ru("citylife.stark.log.reset", name), false);
            }
            case "stark_camera" -> {
                if (!cleared(player)) {
                    break;
                }
                BlockPos cam = BlockPos.of(args.getLong("cam"));
                if (console.cameras().contains(cam)) {
                    dev.lscity.citylife.city.Cameras.view(player, cam, true);
                }
                return;
            }
            default -> {
                return;
            }
        }
        sync(player.server, true);
        openConsole(player, pos);
    }

    // --- Зал брони ---------------------------------------------------------------

    /** Снял костюм со стойки без допуска — это кража из башни Старка. */
    @SubscribeEvent
    public static void onStand(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide || !(event.getTarget() instanceof ArmorStand stand)
                || !stand.getTags().contains(SUIT_TAG)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return;
        }
        ItemStack suit = stand.getItemBySlot(EquipmentSlot.CHEST);
        StarkData data = StarkData.get(player.server);
        long now = player.level().getGameTime();
        if (cleared(player) || !data.armed()) {
            data.log(now, Texts.ru("citylife.stark.log.took", player.getGameProfile().getName(),
                    suit.getHoverName().getString()), false);
        } else {
            raise(player, 4, "citylife.wanted.stark_theft");
        }
    }

    /**
     * Стойки Зала брони. Генератор карты не ставит сущности: места стоек
     * записаны в пульте, и мод сам ставит недостающие стойки, как только
     * чанк с ними загрузился, а пустые раз в минуту получают костюм обратно.
     */
    private static void restock(MinecraftServer server, long now) {
        for (GlobalPos console : ZONES.keySet()) {
            ServerLevel level = server.getLevel(console.dimension());
            if (level == null || !(level.getBlockEntity(console.pos()) instanceof SecurityConsoleBlockEntity be)
                    || be.suits().isEmpty() || be.checked >= 0 && now - be.checked < 1200) {
                continue;
            }
            boolean all = true;
            for (SecurityConsoleBlockEntity.Suit suit : be.suits()) {
                BlockPos at = BlockPos.containing(suit.pos());
                if (!level.isPositionEntityTicking(at)) {
                    all = false;
                    continue;
                }
                stock(level, suit);
            }
            if (all) {
                be.checked = now;
            }
        }
    }

    /** Поставить стойку на место, если её нет, и вернуть костюм на пустую. */
    public static ArmorStand stock(ServerLevel level, SecurityConsoleBlockEntity.Suit suit) {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(suit.item()));
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        EquipmentSlot slot = "head".equals(suit.slot()) ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
        List<ArmorStand> found = level.getEntitiesOfClass(ArmorStand.class, new AABB(suit.pos(), suit.pos())
                .inflate(0.45), s -> s.getTags().contains(SUIT_TAG));
        ArmorStand stand;
        if (found.isEmpty()) {
            stand = new ArmorStand(level, suit.pos().x, suit.pos().y, suit.pos().z);
            stand.setYRot(suit.yaw());
            stand.setYBodyRot(suit.yaw());
            stand.setYHeadRot(suit.yaw());
            CompoundTag extra = new CompoundTag();
            stand.addAdditionalSaveData(extra);
            extra.putBoolean("ShowArms", true);
            extra.putBoolean("NoBasePlate", true);
            // Брать и ставить можно только костюм; остальные слоты заперты.
            extra.putInt("DisabledSlots", slot == EquipmentSlot.HEAD ? 47 : 55);
            stand.readAdditionalSaveData(extra);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.addTag(SUIT_TAG);
            stand.addTag(SUIT_ITEM + suit.item());
            level.addFreshEntity(stand);
        } else {
            stand = found.get(0);
        }
        if (stand.getItemBySlot(slot).isEmpty()) {
            stand.setItemSlot(slot, new ItemStack(item));
        }
        return stand;
    }

    // --- команды -------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("stark")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("access")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> access(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))
                        .then(Commands.literal("revoke").executes(ctx ->
                                access(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false)))));
        root.then(Commands.literal("arm").executes(ctx -> arm(ctx.getSource(), true)));
        root.then(Commands.literal("disarm").executes(ctx -> arm(ctx.getSource(), false)));
        root.then(Commands.literal("reset").executes(ctx -> {
            alarmUntil = 0;
            sync(ctx.getSource().getServer(), true);
            ctx.getSource().sendSuccess(() -> Component.translatable("citylife.stark.cmd.reset"), true);
            return 1;
        }));
        event.getDispatcher().register(root);
    }

    private static int access(CommandSourceStack source, ServerPlayer target, boolean grant) {
        StarkData data = StarkData.get(source.getServer());
        String name = target.getGameProfile().getName();
        if (grant) {
            data.grant(target.getUUID(), name);
        } else {
            data.revoke(target.getUUID());
        }
        source.sendSuccess(() -> Component.translatable(grant ? "citylife.stark.cmd.granted"
                : "citylife.stark.cmd.revoked", name), true);
        return 1;
    }

    private static int arm(CommandSourceStack source, boolean on) {
        StarkData.get(source.getServer()).setArmed(on);
        if (!on) {
            alarmUntil = 0;
        }
        sync(source.getServer(), true);
        source.sendSuccess(() -> Component.translatable(on ? "citylife.stark.cmd.armed"
                : "citylife.stark.cmd.disarmed"), true);
        return 1;
    }

    /** Для самопроверок: забыть нарушителей и тревогу. */
    public static void resetForTests() {
        SEEN.clear();
        GREETED.clear();
        alarmUntil = 0;
        alarmBy = "";
    }

    public static List<GlobalPos> consoles() {
        return new ArrayList<>(ZONES.keySet());
    }
}
