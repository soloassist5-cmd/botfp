package dev.lscity.citylife;

import dev.lscity.citylife.block.AtmBlock;
import dev.lscity.citylife.block.SmartLockBlock;
import dev.lscity.citylife.block.SmartLockBlockEntity;
import dev.lscity.citylife.device.DeviceModel;
import dev.lscity.citylife.device.Devices;
import dev.lscity.citylife.economy.BankCardItem;
import dev.lscity.citylife.economy.MoneyItem;
import dev.lscity.citylife.item.DeviceItem;
import dev.lscity.citylife.item.LockpickItem;
import dev.lscity.citylife.item.SimCardItem;
import dev.lscity.citylife.market.PickupPointBlock;
import dev.lscity.citylife.pc.DeskBlock;
import dev.lscity.citylife.pc.MonitorBlock;
import dev.lscity.citylife.pc.PcCaseBlock;
import dev.lscity.citylife.pc.PcCaseBlockEntity;
import dev.lscity.citylife.pc.PcCaseMenu;
import dev.lscity.citylife.pc.PcPart;
import dev.lscity.citylife.pc.PcPartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Все объекты мода в одном месте. */
public final class Registration {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CityLife.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CityLife.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CityLife.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CityLife.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CityLife.MOD_ID);

    // --- замки --------------------------------------------------------------

    public static final RegistryObject<Block> SMART_LOCK = BLOCKS.register("smart_lock",
            () -> new SmartLockBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> SMART_LOCK_ITEM = ITEMS.register("smart_lock",
            () -> new BlockItem(SMART_LOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> LOCKPICK = ITEMS.register("lockpick",
            () -> new LockpickItem(new Item.Properties().durability(24)));

    // --- гаджеты и связь ------------------------------------------------------
    // Телефоны, планшет и ноутбук — один класс предмета с разными моделями.
    // Старый «smartphone» сохраняет своё имя, чтобы телефоны в мирах не пропали.

    public static final Map<String, RegistryObject<Item>> DEVICES = new LinkedHashMap<>();

    static {
        for (DeviceModel model : Devices.items()) {
            DEVICES.put(model.id(), ITEMS.register(model.id(),
                    () -> new DeviceItem(model, new Item.Properties().stacksTo(1))));
        }
    }

    public static final RegistryObject<Item> SMARTPHONE = DEVICES.get("smartphone");

    public static final RegistryObject<Item> SIM_CARD = ITEMS.register("sim_card",
            () -> new SimCardItem(new Item.Properties().stacksTo(16)));

    // --- компьютер ----------------------------------------------------------

    public static final Map<String, RegistryObject<Item>> PC_PARTS = new LinkedHashMap<>();

    static {
        for (PcPart part : PcPart.ALL) {
            PC_PARTS.put(part.id(), ITEMS.register(part.id(),
                    () -> new PcPartItem(part, new Item.Properties().stacksTo(16))));
        }
    }

    private static BlockBehaviour.Properties desk() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F)
                .sound(SoundType.METAL).noOcclusion();
    }

    public static final RegistryObject<Block> PC_CASE = BLOCKS.register("pc_case",
            () -> new PcCaseBlock(desk().strength(2.0F)));
    public static final RegistryObject<Block> MONITOR = BLOCKS.register("monitor",
            () -> new MonitorBlock(desk()));
    public static final RegistryObject<Block> KEYBOARD = BLOCKS.register("keyboard",
            () -> new DeskBlock(desk(), 1, 0, 4, 15, 1.5, 11));
    public static final RegistryObject<Block> MOUSE = BLOCKS.register("mouse",
            () -> new DeskBlock(desk(), 6, 0, 5, 10, 2, 11));
    public static final RegistryObject<Block> HEADSET = BLOCKS.register("headset",
            () -> new DeskBlock(desk(), 3, 0, 5, 13, 9, 11));

    /** Ноутбук на столе: отдельного предмета нет, ставится самим ноутбуком. */
    public static final RegistryObject<Block> LAPTOP_BLOCK = BLOCKS.register("laptop_block",
            () -> new dev.lscity.citylife.pc.LaptopBlock(desk().strength(0.5F)));

    public static final RegistryObject<Block> PICKUP_POINT = BLOCKS.register("pickup_point",
            () -> new PickupPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(4.0F, 12.0F)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops()));

    static {
        for (RegistryObject<Block> block : java.util.List.of(PC_CASE, MONITOR, KEYBOARD, MOUSE,
                HEADSET, PICKUP_POINT)) {
            ITEMS.register(block.getId().getPath(),
                    () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static final RegistryObject<BlockEntityType<PcCaseBlockEntity>> PC_CASE_BE =
            BLOCK_ENTITIES.register("pc_case", () -> BlockEntityType.Builder
                    .of(PcCaseBlockEntity::new, PC_CASE.get()).build(null));

    public static final RegistryObject<BlockEntityType<dev.lscity.citylife.pc.LaptopBlockEntity>>
            LAPTOP_BE = BLOCK_ENTITIES.register("laptop_block", () -> BlockEntityType.Builder
                    .of(dev.lscity.citylife.pc.LaptopBlockEntity::new, LAPTOP_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<PcCaseMenu>> PC_CASE_MENU =
            MENUS.register("pc_case", () -> IForgeMenuType.create(PcCaseMenu::new));

    // --- деньги -------------------------------------------------------------
    // Номиналы держим отдельными предметами: так они складываются в стопки,
    // видны в инвентаре по цвету и работают в любых рецептах и сундуках.
    public static final RegistryObject<Item> COIN_1 = ITEMS.register("coin_1",
            () -> new MoneyItem(1, new Item.Properties()));
    public static final RegistryObject<Item> COIN_10 = ITEMS.register("coin_10",
            () -> new MoneyItem(10, new Item.Properties()));
    public static final RegistryObject<Item> BANKNOTE_50 = ITEMS.register("banknote_50",
            () -> new MoneyItem(50, new Item.Properties()));
    public static final RegistryObject<Item> BANKNOTE_100 = ITEMS.register("banknote_100",
            () -> new MoneyItem(100, new Item.Properties()));
    public static final RegistryObject<Item> BANKNOTE_500 = ITEMS.register("banknote_500",
            () -> new MoneyItem(500, new Item.Properties()));
    public static final RegistryObject<Item> BANKNOTE_1000 = ITEMS.register("banknote_1000",
            () -> new MoneyItem(1000, new Item.Properties()));
    public static final RegistryObject<Item> BANKNOTE_5000 = ITEMS.register("banknote_5000",
            () -> new MoneyItem(5000, new Item.Properties()));

    public static final RegistryObject<Item> CARD_MIR = ITEMS.register("card_mir",
            () -> new BankCardItem(BankCardItem.Kind.MIR, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CARD_MASTERCARD = ITEMS.register("card_mastercard",
            () -> new BankCardItem(BankCardItem.Kind.MASTERCARD,
                    new Item.Properties().stacksTo(1)));

    // Транспорт: машины продаются ящиками мода машин, канистра заправляет их на месте.
    public static final RegistryObject<Item> FUEL_CANISTER = ITEMS.register("fuel_canister",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Block> ATM = BLOCKS.register("atm",
            () -> new AtmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> ATM_ITEM = ITEMS.register("atm",
            () -> new BlockItem(ATM.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<SmartLockBlockEntity>> SMART_LOCK_BE =
            BLOCK_ENTITIES.register("smart_lock", () -> BlockEntityType.Builder
                    .of(SmartLockBlockEntity::new, SMART_LOCK.get()).build(null));

    // --- вкладки творческого режима -------------------------------------------

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("city_life",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.citylife"))
                    .icon(() -> new ItemStack(SMARTPHONE.get()))
                    .displayItems((params, output) -> {
                        output.accept(SIM_CARD.get());
                        output.accept(SMART_LOCK_ITEM.get());
                        output.accept(LOCKPICK.get());
                        output.accept(ATM_ITEM.get());
                        output.accept(item("pickup_point"));
                        output.accept(CARD_MIR.get());
                        output.accept(CARD_MASTERCARD.get());
                        output.accept(COIN_1.get());
                        output.accept(COIN_10.get());
                        output.accept(BANKNOTE_50.get());
                        output.accept(BANKNOTE_100.get());
                        output.accept(BANKNOTE_500.get());
                        output.accept(BANKNOTE_1000.get());
                        output.accept(BANKNOTE_5000.get());
                        output.accept(FUEL_CANISTER.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> GADGETS_TAB = TABS.register("gadgets",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.citylife.gadgets"))
                    .icon(() -> new ItemStack(PC_PARTS.get("gpu_gx4090").get()))
                    .withTabsBefore(TAB.getId())
                    .displayItems((params, output) -> {
                        DEVICES.values().forEach(device -> output.accept(device.get()));
                        output.accept(SIM_CARD.get());
                        for (String id : new String[]{"pc_case", "monitor", "keyboard", "mouse",
                                "headset"}) {
                            output.accept(item(id));
                        }
                        PC_PARTS.values().forEach(part -> output.accept(part.get()));
                    })
                    .build());

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(
                CityLife.MOD_ID, id));
    }

    private Registration() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }
}
