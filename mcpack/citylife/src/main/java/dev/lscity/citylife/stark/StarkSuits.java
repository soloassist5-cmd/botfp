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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Джарвис, костюм»: вызов брони Железного человека с телефона.
 *
 * Костюмы — из Sym's Armored Industries. Марка — это четыре части брони
 * (у Mark 5 — кейс в слоте Curios «ожерелье»), а оживает костюм только с
 * дуговым реактором в слоте «тело»: без него броня тяжёлая и не даёт
 * сдвинуться с места. Способности ещё и открываются очками навыков.
 * Разбираться в этом самому непросто, поэтому Джарвис делает всё сам:
 * надевает марку, ставит нужный реактор, открывает все навыки и говорит,
 * какие клавиши жать. Пользоваться может владелец башни STARK и все, у кого
 * есть допуск охраны.
 *
 * Mark 7 и Mark 42 в моде только для подписчиков Patreon автора: их
 * облачение у обычного игрока не запускается. Mark 7 в Зале брони — экспонат.
 * Mark 42 у города свой (Mark42): Джарвис присылает его по частям.
 */
public final class StarkSuits {

    public static final String SYM = "sym_industries";

    /** Марка: номер, прозвище, реактор, нужно ли брать из витрины/Джарвиса. */
    public record Mark(int id, String nick, int reactor, boolean playable) {

        public String title() {
            return nick.isEmpty() ? "Mark " + id : "Mark " + id + " · " + nick;
        }

        /** Предмет, которым марку показывают в списке (нагрудник, у Mark 5 — кейс). */
        public String icon() {
            return switch (id) {
                case 5 -> SYM + ":mark_5_suitcase";
                case 7 -> SYM + ":mark_7_bracelet";
                case 42 -> "citylife:mark42_chestplate";
                default -> SYM + ":mark_" + id + "_chestplate";
            };
        }

        /** Четыре части брони или пусто, если марка надевается одним предметом. */
        public boolean armour() {
            return id != 5 && id != 7;
        }
    }

    public static final List<Mark> MARKS = List.of(
            new Mark(1, "пещерный", 1, true),
            new Mark(2, "прототип", 1, true),
            new Mark(3, "", 1, true),
            new Mark(4, "", 1, true),
            new Mark(5, "кейс", 1, true),
            new Mark(6, "", 2, true),
            new Mark(7, "Мстители", 2, false),
            new Mark(17, "Heartbreaker", 2, true),
            new Mark(25, "Striker", 2, true),
            new Mark(33, "Silver Centurion", 2, true),
            new Mark(38, "Igor", 2, true),
            new Mark(39, "Starboost", 2, true),
            new Mark(42, "Prodigal Son", 0, true));

    private static final Pattern PIECE = Pattern.compile("mark_(\\d+)_(helmet|chestplate|leggings|boots)");
    private static final Map<String, Integer> SINGLE = Map.of(
            "mark_5_suitcase", 5, "mark_7_bracelet", 7, "mark_42_implant", 42);
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    /** Какую марку вызвал игрок последней — для экрана приложения. */
    private static final Map<UUID, Integer> CURRENT = new HashMap<>();

    private StarkSuits() {
    }

    public static Mark mark(int id) {
        for (Mark m : MARKS) {
            if (m.id() == id) {
                return m;
            }
        }
        return null;
    }

    /** Номер марки по предмету костюма; 0 — это не костюм. */
    public static int markOf(ResourceLocation id) {
        if (id != null && "citylife".equals(id.getNamespace()) && id.getPath().startsWith(Mark42.PREFIX)
                && id.getPath().matches("mark42_(helmet|chestplate|leggings|boots)")) {
            return 42;
        }
        if (id == null || !SYM.equals(id.getNamespace())) {
            return 0;
        }
        Integer single = SINGLE.get(id.getPath());
        if (single != null) {
            return single;
        }
        Matcher m = PIECE.matcher(id.getPath());
        if (m.matches() && mark(Integer.parseInt(m.group(1))) != null) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    public static boolean isSuit(ResourceLocation id) {
        return markOf(id) != 0;
    }

    /** Какая марка сейчас на игроке (по нагруднику или кейсу), 0 — никакой. */
    public static int worn(ServerPlayer player) {
        int chest = markOf(ForgeRegistries.ITEMS.getKey(player.getItemBySlot(EquipmentSlot.CHEST).getItem()));
        if (chest != 0) {
            return chest;
        }
        return CURRENT.getOrDefault(player.getUUID(), 0);
    }

    /** Данные для приложения «Джарвис». */
    public static CompoundTag snapshot(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("allowed", StarkSecurity.cleared(player));
        tag.putBoolean("owner", StarkSecurity.ownsTower(player));
        tag.putInt("mark", worn(player));
        return tag;
    }

    /** jarvis_suit {mark} — надеть марку; jarvis_off — снять. */
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
        Mark mark = mark(args.getInt("mark"));
        if (mark == null || !mark.playable()) {
            return;
        }
        summon(player, mark);
    }

    private static ItemStack item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Надеть марку целиком, с реактором и навыками, с эффектом прилёта. true — получилось. */
    public static boolean summon(ServerPlayer player, Mark mark) {
        if (mark.id() == 42) {
            // Свой Mark 42: детали прилетают по одной, сообщения — по прибытии.
            stripSym(player);
            if (Mark42.wears(player) || Mark42.assemble(player, null)) {
                CURRENT.put(player.getUUID(), 42);
                return true;
            }
            return false;
        }
        strip(player);
        if (mark.armour()) {
            for (int i = 0; i < 4; i++) {
                ItemStack piece = item(SYM + ":mark_" + mark.id() + "_" + PIECES[i]);
                if (piece.isEmpty()) {
                    player.displayClientMessage(Component.translatable("citylife.jarvis.failed")
                            .withStyle(ChatFormatting.RED), false);
                    return false;
                }
                // Своя броня игрока не пропадает: уходит в инвентарь (или под ноги).
                ItemStack own = player.getItemBySlot(ARMOUR[i]);
                if (!own.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(own.copy());
                }
                player.setItemSlot(ARMOUR[i], piece);
            }
        } else if (!curio(player, "necklace", item(mark.icon()))) {
            player.displayClientMessage(Component.translatable("citylife.jarvis.failed")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        prepare(player, mark);
        CURRENT.put(player.getUUID(), mark.id());
        String title = mark.title();
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
            if (i == 1 && mark.armour()) {
                continue;
            }
            player.sendSystemMessage(Component.translatable("citylife.jarvis.help." + i)
                    .withStyle(ChatFormatting.GRAY));
        }
        StarkData.get(player.server).log(level.getGameTime(), Texts.ru("citylife.stark.log.summon",
                player.getGameProfile().getName(), title), false);
        return true;
    }

    /**
     * Чтобы костюм ожил: нужный дуговой реактор в слоте «тело» и открытые
     * навыки марки. Зовётся и после витрины Зала брони — там мод надевает
     * только броню.
     */
    public static boolean prepare(ServerPlayer player, Mark mark) {
        if (mark.id() == 42) {
            return true;   // у нашего Mark 42 реактор встроенный
        }
        boolean reactor = curio(player, "body", item(SYM + ":arc_reactor_tier_" + mark.reactor()));
        skills(player);
        return reactor;
    }

    /** Все навыки всех марок на максимум — то же, что «/impointsadmin игрок *». */
    private static void skills(ServerPlayer player) {
        try {
            var helper = Class.forName("com.symbiotespidey.sym_industries.forge.util.IronManHelper");
            var set = helper.getMethod("setSkill", net.minecraft.world.entity.player.Player.class, int.class,
                    int.class);
            for (Mark m : MARKS) {
                set.invoke(null, player, m.id(), 100);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            dev.lscity.citylife.CityLife.LOG.warn("City Life: навыки костюма не выданы: {}", e.toString());
        }
    }

    /** Снять с игрока костюмы Sym (наш Mark 42 остаётся на месте). */
    public static void stripSym(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOUR) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(player.getItemBySlot(slot).getItem());
            if (id != null && SYM.equals(id.getNamespace()) && isSuit(id)) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (String slot : new String[]{"necklace", "head", "body"}) {
            if (isSuitOrReactor(curio(player, slot))) {
                curio(player, slot, ItemStack.EMPTY);
            }
        }
    }

    /** Снять с игрока всё от костюмов: части брони, кейс, реактор. */
    private static void strip(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOUR) {
            if (isSuit(ForgeRegistries.ITEMS.getKey(player.getItemBySlot(slot).getItem()))) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (String slot : new String[]{"necklace", "head", "body"}) {
            if (isSuitOrReactor(curio(player, slot))) {
                curio(player, slot, ItemStack.EMPTY);
            }
        }
    }

    public static void off(ServerPlayer player) {
        if (Mark42.wearsAny(player)) {
            Mark42.home(player, null);   // Mark 42 улетает сам
        }
        strip(player);
        CURRENT.remove(player.getUUID());
        player.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable("citylife.jarvis.off").withStyle(ChatFormatting.AQUA));
    }

    private static boolean isSuitOrReactor(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return isSuit(id) || id != null && SYM.equals(id.getNamespace()) && id.getPath().startsWith("arc_reactor");
    }

    // --- слоты Curios ------------------------------------------------------------
    //
    // Curios подключён к сборке, но не к компиляции мода: обращаемся к его API
    // через отражение. Команда /curios не годится — она ищет игрока по имени
    // в списке игроков сервера, а там нет, например, игроков автотестов.

    private static Object handler(ServerPlayer player) throws ReflectiveOperationException {
        var api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        Object lazy = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class)
                .invoke(null, player);
        var opt = ((net.minecraftforge.common.util.LazyOptional<?>) lazy).resolve();
        return opt.orElse(null);
    }

    /** Положить предмет в первую ячейку слота Curios. true — слот есть. */
    public static boolean curio(ServerPlayer player, String slot, ItemStack stack) {
        try {
            Object h = handler(player);
            if (h == null || curioHandler(h, slot) == null) {
                return false;
            }
            Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                    .getMethod("setEquippedCurio", String.class, int.class, ItemStack.class)
                    .invoke(h, slot, 0, stack);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            dev.lscity.citylife.CityLife.LOG.warn("City Life: слот Curios {} недоступен: {}", slot, e.toString());
            return false;
        }
    }

    /** Что лежит в первой ячейке слота Curios (пусто, если слота нет). */
    public static ItemStack curio(ServerPlayer player, String slot) {
        try {
            Object h = handler(player);
            Object stacks = h == null ? null : curioHandler(h, slot);
            if (stacks == null) {
                return ItemStack.EMPTY;
            }
            // Методы берём у интерфейсов: классы реализации Curios не публичные.
            Object dyn = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler")
                    .getMethod("getStacks").invoke(stacks);
            var items = (net.minecraftforge.items.IItemHandler) dyn;
            return items.getSlots() > 0 ? items.getStackInSlot(0) : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }

    private static Object curioHandler(Object handler, String slot) throws ReflectiveOperationException {
        var opt = (java.util.Optional<?>) Class.forName(
                        "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                .getMethod("getStacksHandler", String.class).invoke(handler, slot);
        return opt.orElse(null);
    }
}
