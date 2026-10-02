package dev.lscity.citylife.stark;

import dev.lscity.citylife.data.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * «Джарвис, костюм»: вызов брони Железного человека с телефона.
 *
 * Костюм Satsu — браслет (кейс, нано-модуль), который работает только в
 * особом слоте Curios «технологическая броня», да ещё с зарядом реактора.
 * Найти слот, понять про заряд и колесо способностей без подсказки
 * непросто, поэтому Джарвис делает это сам: кладёт выбранную марку прямо в
 * слот (Халкбастер — в свой), заряжает её полностью и говорит, какие
 * клавиши жать. Пользоваться может владелец башни STARK и все, у кого
 * есть допуск охраны.
 */
public final class StarkSuits {

    public static final String SATSU = "satsu_iron_man_addon";
    /** Полный заряд реактора костюма (так его ограничивает сам мод). */
    public static final int ENERGY = 42000;
    private static final Pattern SUIT = Pattern.compile(
            "(war_machine/|iron_heart/)?marks/[^/]+/(bracelet|briefcase|main|[a-z0-9_]+_black_suit)");
    private static final String HULKBUSTER = SATSU + ":marks/mark_44/bracelet";

    /** Какую марку вызвал игрок последней — для экрана приложения. */
    private static final Map<UUID, String> CURRENT = new HashMap<>();

    private StarkSuits() {
    }

    public static boolean isSuit(ResourceLocation id) {
        return id != null && SATSU.equals(id.getNamespace()) && SUIT.matcher(id.getPath()).matches();
    }

    /** Слот Curios для костюма: у Халкбастера свой. */
    public static String slot(String item) {
        return HULKBUSTER.equals(item) ? "hulkbuster_armor" : "tecnology_armor";
    }

    public static String current(ServerPlayer player) {
        return CURRENT.getOrDefault(player.getUUID(), "");
    }

    /** Данные для приложения «Джарвис». */
    public static CompoundTag snapshot(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("allowed", StarkSecurity.cleared(player));
        tag.putBoolean("owner", StarkSecurity.ownsTower(player));
        tag.putString("suit", current(player));
        return tag;
    }

    /** jarvis_suit {item} — надеть марку; jarvis_off — снять. */
    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        if (!StarkSecurity.cleared(player)) {
            player.displayClientMessage(Component.translatable("citylife.jarvis.denied")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if ("jarvis_off".equals(action)) {
            off(player);
            return;
        }
        if (!"jarvis_suit".equals(action)) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(args.getString("item"));
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (!isSuit(id) || item == null || item == net.minecraft.world.item.Items.AIR) {
            return;
        }
        summon(player, id.toString());
    }

    /** Положить костюм в слот, заряженным, с эффектом прилёта. true — получилось. */
    public static boolean summon(ServerPlayer player, String item) {
        String previous = current(player);
        if (!previous.isEmpty() && !slot(previous).equals(slot(item))) {
            curios(player, "replace " + slot(previous) + " 0 " + name(player) + " with minecraft:air");
        }
        boolean ok = curios(player, "replace " + slot(item) + " 0 " + name(player) + " with " + item
                + "{Energy:" + ENERGY + "} 1");
        if (!ok) {
            player.displayClientMessage(Component.translatable("citylife.jarvis.failed")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        CURRENT.put(player.getUUID(), item);
        String title = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item)))
                .getHoverName().getString();
        var level = player.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                60, 0.6, 1.0, 0.6, 0.15);
        level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1, player.getZ(),
                30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS,
                1.0F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS,
                1.0F, 1.0F);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                "citylife.jarvis.arrived_sub", title).withStyle(ChatFormatting.AQUA)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("J.A.R.V.I.S.")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.translatable("citylife.jarvis.arrived", title)
                .withStyle(ChatFormatting.AQUA));
        for (int i = 1; i <= 4; i++) {
            player.sendSystemMessage(Component.translatable("citylife.jarvis.help." + i)
                    .withStyle(ChatFormatting.GRAY));
        }
        StarkData.get(player.server).log(level.getGameTime(), Texts.ru("citylife.stark.log.summon",
                player.getGameProfile().getName(), title), false);
        return true;
    }

    public static void off(ServerPlayer player) {
        String previous = current(player);
        for (String slot : new String[]{"tecnology_armor", "hulkbuster_armor"}) {
            if (previous.isEmpty() || slot.equals(slot(previous))) {
                curios(player, "replace " + slot + " 0 " + name(player) + " with minecraft:air");
            }
        }
        CURRENT.remove(player.getUUID());
        player.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable("citylife.jarvis.off").withStyle(ChatFormatting.AQUA));
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /** Команда Curios от имени сервера (без вывода в чат). true — выполнилась. */
    private static boolean curios(ServerPlayer player, String args) {
        var server = player.server;
        var source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4)
                .withLevel(player.serverLevel());
        return server.getCommands().performPrefixedCommand(source, "curios " + args) > 0;
    }
}
