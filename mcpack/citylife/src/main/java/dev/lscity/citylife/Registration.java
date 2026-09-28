package dev.lscity.citylife;

import dev.lscity.citylife.block.AtmBlock;
import dev.lscity.citylife.block.SmartLockBlock;
import dev.lscity.citylife.economy.BankCardItem;
import dev.lscity.citylife.economy.CarKeyItem;
import dev.lscity.citylife.economy.MoneyItem;
import dev.lscity.citylife.block.SmartLockBlockEntity;
import dev.lscity.citylife.item.LockpickItem;
import dev.lscity.citylife.item.SmartphoneItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Все объекты мода в одном месте. */
public final class Registration {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CityLife.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CityLife.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CityLife.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CityLife.MOD_ID);

    public static final RegistryObject<Block> SMART_LOCK = BLOCKS.register("smart_lock",
            () -> new SmartLockBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> SMART_LOCK_ITEM = ITEMS.register("smart_lock",
            () -> new BlockItem(SMART_LOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> SMARTPHONE = ITEMS.register("smartphone",
            () -> new SmartphoneItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> LOCKPICK = ITEMS.register("lockpick",
            () -> new LockpickItem(new Item.Properties().durability(24)));

    public static final RegistryObject<Item> SIM_CARD = ITEMS.register("sim_card",
            () -> new Item(new Item.Properties()));

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

    // Транспорт: ключ выдаёт готовую машину, канистра заправляет её на месте.
    public static final RegistryObject<Item> CAR_KEY = ITEMS.register("car_key",
            () -> new CarKeyItem(new Item.Properties().stacksTo(1)));
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

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("city_life",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.citylife"))
                    .icon(() -> new ItemStack(SMARTPHONE.get()))
                    .displayItems((params, output) -> {
                        output.accept(SMARTPHONE.get());
                        output.accept(SIM_CARD.get());
                        output.accept(SMART_LOCK_ITEM.get());
                        output.accept(LOCKPICK.get());
                        output.accept(ATM_ITEM.get());
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
                        output.accept(CAR_KEY.get());
                    })
                    .build());

    private Registration() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
    }
}
