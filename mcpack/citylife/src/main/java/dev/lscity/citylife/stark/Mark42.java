package dev.lscity.citylife.stark;

import com.mojang.brigadier.CommandDispatcher;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mark 42 «Prodigal Son» — собственный костюм города.
 *
 * Броня — наши предметы (модель и текстуры собирает tools/gen_mark42.py).
 * Способности — сила Palladium citylife:mark42: она выдаётся, пока надет
 * нагрудник, а работает, когда надеты все четыре части. Открыты сразу,
 * без прокачки: репульсор, юнибим, микроракеты, полёт, энергощит, забрало,
 * ночное зрение, сканер и «Протокол „Дом“».
 *
 * Фишка Mark 42 из фильма — броня прилетает к хозяину по частям. Так и
 * здесь: Джарвис или стенд сборки отправляет к игроку четыре детали
 * (Mark42Piece), они летят по дуге и защёлкиваются одна за другой — сначала
 * ботинки, потом поножи, кираса и шлем. «Протокол „Дом“» — наоборот:
 * детали срываются с игрока и улетают.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Mark42 {

    public static final String PREFIX = "mark42_";

    /** Порядок прилёта и слоты: ботинки, поножи, кираса, шлем. */
    static final EquipmentSlot[] ORDER = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST,
            EquipmentSlot.HEAD};
    /** Через сколько тиков после вызова вылетает деталь и сколько летит. */
    static final int[] DELAY = {0, 5, 10, 16};
    static final int FLIGHT = 22;

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 4;
                case CHESTPLATE -> 9;
                case LEGGINGS -> 7;
                case BOOTS -> 4;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 20;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return CityLife.MOD_ID + ":mark42";
        }

        @Override
        public float getToughness() {
            return 4.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.2F;
        }
    };

    public static final RegistryObject<Item> HELMET = Registration.ITEMS.register(PREFIX + "helmet",
            () -> new Mark42Armor(ArmorItem.Type.HELMET));
    public static final RegistryObject<Item> CHESTPLATE = Registration.ITEMS.register(PREFIX + "chestplate",
            () -> new Mark42Armor(ArmorItem.Type.CHESTPLATE));
    public static final RegistryObject<Item> LEGGINGS = Registration.ITEMS.register(PREFIX + "leggings",
            () -> new Mark42Armor(ArmorItem.Type.LEGGINGS));
    public static final RegistryObject<Item> BOOTS = Registration.ITEMS.register(PREFIX + "boots",
            () -> new Mark42Armor(ArmorItem.Type.BOOTS));
    /** Только как внешний вид снаряда Palladium. */
    public static final RegistryObject<Item> MISSILE = Registration.ITEMS.register(PREFIX + "missile",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Block> GANTRY = Registration.BLOCKS.register(PREFIX + "gantry",
            () -> new Mark42GantryBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
                    .lightLevel(s -> 7)));
    public static final RegistryObject<Item> GANTRY_ITEM = Registration.ITEMS.register(PREFIX + "gantry",
            () -> new BlockItem(GANTRY.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<BlockEntityType<Mark42GantryBlockEntity>> GANTRY_BE =
            Registration.BLOCK_ENTITIES.register(PREFIX + "gantry", () -> BlockEntityType.Builder
                    .of(Mark42GantryBlockEntity::new, GANTRY.get()).build(null));

    public static final RegistryObject<EntityType<Mark42Piece>> PIECE = Registration.ENTITIES.register(
            PREFIX + "piece", () -> EntityType.Builder.<Mark42Piece>of(Mark42Piece::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F).noSummon().fireImmune().clientTrackingRange(10).updateInterval(20)
                    .build(PREFIX + "piece"));

    /** Сколько деталей ещё летит к игроку: второй вызов во время сборки не нужен. */
    private static final Map<UUID, Integer> INBOUND = new HashMap<>();

    private Mark42() {
    }

    /** Подгрузить класс до регистрации: поля выше и есть регистрация. */
    public static void init() {
    }

    public static Item piece(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HELMET.get();
            case CHEST -> CHESTPLATE.get();
            case LEGS -> LEGGINGS.get();
            default -> BOOTS.get();
        };
    }

    public static boolean isPiece(ItemStack stack) {
        return stack.getItem() instanceof Mark42Armor;
    }

    /** Надет ли костюм целиком. */
    public static boolean wears(LivingEntity entity) {
        for (EquipmentSlot slot : ORDER) {
            if (entity.getItemBySlot(slot).getItem() != piece(slot)) {
                return false;
            }
        }
        return true;
    }

    public static boolean wearsAny(LivingEntity entity) {
        for (EquipmentSlot slot : ORDER) {
            if (isPiece(entity.getItemBySlot(slot))) {
                return true;
            }
        }
        return false;
    }

    public static boolean assembling(ServerPlayer player) {
        return INBOUND.getOrDefault(player.getUUID(), 0) > 0;
    }

    /** Точка на теле, куда садится деталь: шея для шлема и кирасы, таз для поножей и ботинок. */
    static Vec3 mount(LivingEntity entity, EquipmentSlot slot) {
        double y = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ? 1.5 : 0.75;
        return entity.position().add(0, y * entity.getScale(), 0);
    }

    /**
     * Вызвать костюм: детали вылетают и собираются на игроке. from — откуда
     * летят (стенд сборки); null — Джарвис шлёт их из-за спины, с высоты.
     * Свою броню игрок не теряет: она уходит в инвентарь, когда садится деталь.
     */
    public static boolean assemble(ServerPlayer player, Vec3 from) {
        if (assembling(player)) {
            return false;
        }
        StarkSuits.stripSym(player);
        ServerLevel level = player.serverLevel();
        Vec3 back = Vec3.directionFromRotation(0, player.getYRot()).scale(-1);
        Vec3 side = new Vec3(-back.z, 0, back.x);
        int sent = 0;
        for (int i = 0; i < ORDER.length; i++) {
            EquipmentSlot slot = ORDER[i];
            if (player.getItemBySlot(slot).getItem() == piece(slot)) {
                continue;   // эта часть уже на месте
            }
            Vec3 start;
            if (from != null) {
                start = from.add(0, slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ? 1.5 : 0.75, 0);
            } else {
                start = player.position().add(back.scale(9 + i)).add(side.scale((i % 2 == 0 ? -1 : 1) * (2 + i)))
                        .add(0, 7 + i, 0);
            }
            Mark42Piece p = new Mark42Piece(PIECE.get(), level);
            p.launch(start, slot, player, null, DELAY[i], FLIGHT);
            level.addFreshEntity(p);
            sent++;
        }
        if (sent == 0) {
            return false;
        }
        INBOUND.put(player.getUUID(), sent);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 12));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("citylife.mark42.inbound")
                .withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("J.A.R.V.I.S.")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        return true;
    }

    /** Деталь долетела: защёлкнуть на игроке. */
    static void attach(ServerPlayer player, EquipmentSlot slot) {
        ItemStack own = player.getItemBySlot(slot);
        if (!own.isEmpty() && own.getItem() != piece(slot)) {
            if (StarkSuits.isSuit(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(own.getItem()))) {
                own = ItemStack.EMPTY;   // чужой костюм из Зала — не в карман, он и так «уехал»
            } else {
                player.getInventory().placeItemBackInInventory(own.copy());
            }
        }
        player.setItemSlot(slot, new ItemStack(piece(slot)));
        ServerLevel level = player.serverLevel();
        Vec3 at = mount(player, slot);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.12);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS,
                1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS,
                0.7F, 1.5F);
        int left = INBOUND.getOrDefault(player.getUUID(), 1) - 1;
        if (left <= 0) {
            INBOUND.remove(player.getUUID());
            if (wears(player)) {
                online(player);
            }
        } else {
            INBOUND.put(player.getUUID(), left);
        }
    }

    /** Деталь не долетела (игрок вышел, умер, ушёл в другое измерение). */
    static void lost(UUID player) {
        int left = INBOUND.getOrDefault(player, 1) - 1;
        if (left <= 0) {
            INBOUND.remove(player);
        } else {
            INBOUND.put(player, left);
        }
    }

    private static void online(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1, player.getZ(), 24,
                0.4, 0.8, 0.4, 0.04);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F,
                1.2F);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 40, 12));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("citylife.mark42.online_sub")
                .withStyle(ChatFormatting.AQUA)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("MARK 42")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.translatable("citylife.mark42.online").withStyle(ChatFormatting.GOLD));
        for (int i = 1; i <= 3; i++) {
            player.sendSystemMessage(Component.translatable("citylife.mark42.help." + i)
                    .withStyle(ChatFormatting.GRAY));
        }
        StarkData.get(player.server).log(level.getGameTime(), dev.lscity.citylife.data.Texts.ru(
                "citylife.stark.log.summon", player.getGameProfile().getName(), "Mark 42"), false);
    }

    /**
     * «Протокол „Дом“»: детали срываются с игрока и улетают — в небо или
     * обратно на стенд сборки (to). true — было что снимать.
     */
    public static boolean home(ServerPlayer player, Vec3 to) {
        ServerLevel level = player.serverLevel();
        Vec3 away = Vec3.directionFromRotation(-35, player.getYRot() + 160).scale(18);
        boolean any = false;
        for (int i = ORDER.length - 1; i >= 0; i--) {
            EquipmentSlot slot = ORDER[i];
            if (!isPiece(player.getItemBySlot(slot))) {
                continue;
            }
            player.setItemSlot(slot, ItemStack.EMPTY);
            Vec3 start = mount(player, slot);
            Vec3 end = to != null ? to.add(0, slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ? 1.5 : 0.75, 0)
                    : start.add(away).add((i - 1.5) * 3, i * 2, 0);
            Mark42Piece p = new Mark42Piece(PIECE.get(), level);
            p.launch(start, slot, null, end, (ORDER.length - 1 - i) * 3, to != null ? 18 : 30);
            level.addFreshEntity(p);
            any = true;
        }
        if (any) {
            INBOUND.remove(player.getUUID());
            player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F,
                    1.4F);
            player.displayClientMessage(Component.translatable("citylife.mark42.home")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return any;
    }

    // --- команды ------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * /mark42 home — «Протокол „Дом“» (его зовёт и способность костюма);
     * /mark42 summon — вызвать костюм, как из «Джарвиса» (нужен допуск охраны).
     */
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mark42")
                .then(Commands.literal("home").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return home(player, null) ? 1 : 0;
                }))
                .then(Commands.literal("summon").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!StarkSecurity.cleared(player)) {
                        ctx.getSource().sendFailure(Component.translatable("citylife.jarvis.denied"));
                        return 0;
                    }
                    return assemble(player, null) ? 1 : 0;
                })));
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        INBOUND.remove(event.getEntity().getUUID());
    }

    /** На всякий случай: если деталь потерялась, счётчик не держит игрока вечно. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 200 == 0) {
            INBOUND.keySet().removeIf(id -> event.getServer().getPlayerList().getPlayer(id) == null);
        }
    }

    static double ease(double t) {
        t = Mth.clamp(t, 0, 1);
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Для отладки и тестов: ближайший игрок-получатель детали. */
    static ServerPlayer owner(Entity piece, UUID id) {
        return id == null || piece.getServer() == null ? null : piece.getServer().getPlayerList().getPlayer(id);
    }
}
