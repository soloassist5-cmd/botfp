package dev.lscity.citylife.city;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Вооружённая полиция: патрульные среди прохожих, наряды 112, постовые
 * и охранники держат пистолет и стреляют в игрока с оружием в руках.
 *
 * Сначала окрик «Полиция! Брось оружие!» — 3 секунды, чтобы убрать ствол
 * (сменить слот). Не убрал и офицер его видит — выстрел раз в полторы
 * секунды: чем дальше, тем чаще мимо. Дежурных полицейских-игроков не
 * трогают. Пули настоящие только по урону: ствол у NPC для вида, патроны
 * не тратятся и не выпадают.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Police {

    /** Пистолет TaCZ в руке полицейского. */
    private static final String PISTOL =
            "{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:glock_17\","
                    + "GunFireMode:\"SEMI\",GunCurrentAmmoCount:17,HasBulletInBarrel:1b}}";
    /** Роли жителей-полицейских из датапака. */
    private static final Set<String> ROLES = Set.of("police", "cop", "guard", "security");
    private static final long WARN_TICKS = 60;
    private static final long SHOT_TICKS = 30;

    /** Когда офицер окликнул игрока: ключ — офицер и игрок. */
    private static final Map<String, Long> WARNED = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SHOT = new HashMap<>();
    /** Сколько раз полиция стреляла в игрока — для автотестов. */
    private static final Map<UUID, Integer> SHOTS = new HashMap<>();

    private Police() {
    }

    public static ItemStack pistol() {
        try {
            return ItemStack.of(TagParser.parseTag(PISTOL));
        } catch (Exception error) {
            return ItemStack.EMPTY;
        }
    }

    /** Патроны 9 мм к пистолету полицейского. */
    public static ItemStack ammo(int count) {
        try {
            ItemStack stack = ItemStack.of(TagParser.parseTag(
                    "{id:\"tacz:ammo\",Count:1b,tag:{AmmoId:\"tacz:9mm\"}}"));
            stack.setCount(Math.max(1, Math.min(64, count)));
            return stack;
        } catch (Exception error) {
            return ItemStack.EMPTY;
        }
    }

    /** HandItems для NBT сущности: пистолет в правой, левая пустая. */
    public static ListTag handItems() {
        ListTag hands = new ListTag();
        hands.add(pistol().save(new CompoundTag()));
        hands.add(new CompoundTag());
        return hands;
    }

    public static boolean isOfficer(net.minecraft.world.entity.Entity entity) {
        if (entity.getTags().contains(Citizens.PATROL_TAG)
                || entity.getTags().contains("citylife_resp_police")) {
            return true;
        }
        String role = dev.lscity.citylife.trade.ShopHandler.roleOf(entity);
        return role != null && ROLES.contains(role);
    }

    public static int shotsAt(ServerPlayer player) {
        return SHOTS.getOrDefault(player.getUUID(), 0);
    }

    /** Нужно ли полиции реагировать на игрока. */
    private static boolean threat(ServerPlayer player) {
        return Robbery.armed(player) && !player.isCreative() && !player.isSpectator()
                && !"police".equals(dev.lscity.citylife.jobs.Duty.of(player));
    }

    /**
     * Офицер и игрок: окрик, потом выстрелы, пока игрок держит оружие и
     * офицер его видит. Сервер зовёт раз в полсекунды, тесты — напрямую.
     */
    public static void check(Mob officer, ServerPlayer target, long now) {
        String key = officer.getUUID() + "/" + target.getUUID();
        if (!officer.isAlive() || !threat(target) || !officer.hasLineOfSight(target)
                || officer.distanceTo(target) > CityConfig.CONFIG.policeRange.get()) {
            WARNED.remove(key);
            return;
        }
        if (officer.getMainHandItem().isEmpty()) {
            officer.setItemSlot(EquipmentSlot.MAINHAND, pistol());
        }
        officer.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        officer.setYHeadRot(officer.getYRot());
        officer.setYBodyRot(officer.getYRot());
        Pedestrians.pause(officer, target, 40);
        Long warned = WARNED.get(key);
        if (warned == null) {
            WARNED.put(key, now);
            target.sendSystemMessage(Citizens.says(officer, Component.translatable(
                    "citylife.police.warn").getString()).copy().withStyle(ChatFormatting.RED));
            return;
        }
        if (now - warned < WARN_TICKS || now < NEXT_SHOT.getOrDefault(officer.getUUID(), 0L)) {
            return;
        }
        NEXT_SHOT.put(officer.getUUID(), now + SHOT_TICKS);
        fire(officer, target);
    }

    private static void fire(Mob officer, ServerPlayer target) {
        if (!(officer.level() instanceof ServerLevel level)) {
            return;
        }
        SHOTS.merge(target.getUUID(), 1, Integer::sum);
        level.playSound(null, officer.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 0.5F, 1.9F);
        Vec3 from = officer.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 step = to.subtract(from).scale(1.0D / 12);
        for (int i = 1; i <= 12; i++) {
            Vec3 p = from.add(step.scale(i));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        double distance = officer.distanceTo(target);
        double chance = Math.max(0.25D, 0.85D - distance / 40D);
        if (level.random.nextDouble() < chance) {
            target.hurt(level.damageSources().mobAttack(officer),
                    CityConfig.CONFIG.policeDamage.get().floatValue());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null
                || !CityConfig.CONFIG.policeShoot.get()
                || event.getServer().overworld().getGameTime() % 10 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        int range = CityConfig.CONFIG.policeRange.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level || !threat(player)) {
                continue;
            }
            for (Mob officer : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(range), Police::isOfficer)) {
                check(officer, player, now);
            }
        }
        if (now % 1200 == 0) {
            WARNED.entrySet().removeIf(e -> now - e.getValue() > 1200);
            NEXT_SHOT.entrySet().removeIf(e -> e.getValue() < now);
        }
    }
}
