package dev.lscity.citylife.test;

import com.mojang.authlib.GameProfile;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.city.Emergency;
import dev.lscity.citylife.city.Wanted;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.estate.Estate;
import dev.lscity.citylife.estate.EstateGuard;
import dev.lscity.citylife.jobs.Jobs;
import dev.lscity.citylife.pc.LaptopBlock;
import dev.lscity.citylife.pc.LaptopBlockEntity;
import dev.lscity.citylife.trade.ShopCatalog;
import dev.lscity.citylife.trade.ShopHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Автотесты мода: то, что раньше проверялось только руками в игре.
 *
 * Запуск — командой /citylife selftest (нужны права администратора): каждый
 * тест разворачивается на своей площадке высоко над городом, отчёт идёт в
 * чат и в лог. Скрипт citylife/tools/run_gametests.py гоняет их на сервере
 * со всей сборкой перед выкладкой.
 *
 * Живого клиента тут нет, поэтому игрока изображает FakePlayer: клик,
 * поломка блока и открытие двери идут через те же события Forge, что
 * и у настоящего игрока.
 */
public final class CityTests {

    /** Метка теста: такие методы запускает /citylife selftest. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    public @interface SelfTest {
        /** Сколько тиков ждать отложенную проверку. */
        int timeout() default 100;
    }

    private CityTests() {
    }

    private static FakePlayer player(TestKit h, String name) {
        UUID id = UUID.nameUUIDFromBytes(("citylife-test-" + name).getBytes());
        FakePlayer fake = FakePlayerFactory.get(h.getLevel(), new GameProfile(id, name));
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        fake.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0F, 0F);
        return fake;
    }

    private static Entity npc(TestKit h, BlockPos rel, String name, String... tags) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "easy_npc:humanoid");
        tag.putString("CustomName", Component.Serializer.toJson(Component.literal(name)));
        tag.putBoolean("NoAI", true);
        tag.putBoolean("Invulnerable", true);
        ListTag list = new ListTag();
        for (String t : tags) {
            list.add(StringTag.valueOf(t));
        }
        tag.put("Tags", list);
        BlockPos at = h.absolutePos(rel);
        Entity entity = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> {
            e.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0F, 0F);
            return e;
        });
        if (entity == null) {
            throw new IllegalStateException("easy_npc:humanoid не создаётся — нет Easy NPC?");
        }
        h.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static String click(FakePlayer who, Entity target) {
        ShopHandler.lastOutcome = "";
        who.interactOn(target, InteractionHand.MAIN_HAND);
        return ShopHandler.lastOutcome;
    }

    // --- жители -----------------------------------------------------------------

    /** У каждой роли с товаром клик открывает именно её прилавок. */
    @SelfTest
    public static void everyShopOpens(TestKit h) {
        FakePlayer buyer = player(h, "Buyer");
        StringBuilder bad = new StringBuilder();
        int checked = 0;
        for (Map.Entry<String, dev.lscity.citylife.trade.Shop> e : ShopCatalog.BY_ROLE.entrySet()) {
            String role = e.getKey();
            if (e.getValue().offers().isEmpty()) {
                continue;
            }
            Entity npc = npc(h, new BlockPos(3, 1, 3), "Тест " + role, "citylife_npc",
                    "citylife_" + role, ShopCatalog.GEN_TAG);
            String outcome = click(buyer, npc);
            String want = "realtor".equals(role) ? "realty" : "shop:" + role;
            if (!want.equals(outcome)) {
                bad.append(role).append("->").append(outcome).append(' ');
            }
            if (e.getValue().build().isEmpty()) {
                bad.append(role).append(" без товаров ");
            }
            String missing = e.getValue().missing();
            if (!missing.isEmpty()) {
                bad.append(role).append(" нет предметов: ").append(missing).append(' ');
            }
            npc.discard();
            checked++;
        }
        if (bad.length() > 0) {
            h.fail("прилавки не открылись: " + bad);
        }
        CityLife.LOG.info("City Life тест: проверено прилавков {}", checked);
        h.succeed();
    }

    /** Житель без тегов узнаётся по имени, а машина с тем же именем — нет. */
    @SelfTest
    public static void roleByName(TestKit h) {
        FakePlayer buyer = player(h, "Buyer");
        Entity npc = npc(h, new BlockPos(3, 1, 3), "Продавец техники");
        String outcome = click(buyer, npc);
        if (!"shop:trader_tech".equals(outcome)) {
            h.fail("житель без тегов: " + outcome);
        }
        CompoundTag car = new CompoundTag();
        car.putString("id", "vehicle:smart_car");
        car.putString("CustomName", Component.Serializer.toJson(Component.literal("Банкир")));
        BlockPos at = h.absolutePos(new BlockPos(10, 1, 10));
        Entity vehicle = EntityType.loadEntityRecursive(car, h.getLevel(), e -> {
            e.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0F, 0F);
            return e;
        });
        if (vehicle != null) {
            h.getLevel().addFreshEntity(vehicle);
            if (!click(buyer, vehicle).isEmpty()) {
                h.fail("клик по машине перехвачен как разговор");
            }
        }
        h.succeed();
    }

    /** Житель прошлого поколения в мир не попадает, нынешнего — попадает. */
    @SelfTest
    public static void oldGenerationRemoved(TestKit h) {
        Entity fresh = npc(h, new BlockPos(3, 1, 3), "Банкир", "citylife_npc",
                "citylife_banker", ShopCatalog.GEN_TAG);
        Entity old = npc(h, new BlockPos(5, 1, 5), "Банкир", "citylife_npc", "citylife_banker");
        if (!fresh.isAddedToWorld() || h.getLevel().getEntity(fresh.getUUID()) == null) {
            h.fail("житель нового поколения не появился");
        }
        if (h.getLevel().getEntity(old.getUUID()) != null) {
            h.fail("житель старого поколения остался в мире");
        }
        h.succeed();
    }

    /** Прохожий появляется и идёт к цели сам. */
    @SelfTest(timeout = 200)
    public static void walkerWalks(TestKit h) {
        BlockPos from = h.absolutePos(new BlockPos(2, 1, 2));
        Entity walker = dev.lscity.citylife.city.Pedestrians.spawnWalker(h.getLevel(), from,
                h.getLevel().getRandom());
        if (!(walker instanceof net.minecraft.world.entity.Mob mob)) {
            h.fail("прохожий не создался");
            return;
        }
        BlockPos to = h.absolutePos(new BlockPos(13, 1, 13));
        dev.lscity.citylife.city.Pedestrians.sendTo(walker, to);
        h.succeedWhen(() -> {
            if (walker.distanceToSqr(Vec3.atBottomCenterOf(to)) > 4.0D) {
                h.fail("прохожий не дошёл: до цели "
                        + Math.round(Math.sqrt(walker.distanceToSqr(Vec3.atBottomCenterOf(to))))
                        + " блоков, от старта "
                        + Math.round(Math.sqrt(walker.distanceToSqr(Vec3.atBottomCenterOf(from)))));
            }
            walker.discard();
        });
    }

    // --- жильё ------------------------------------------------------------------

    private static Estate.Unit testHouse(TestKit h, String id) {
        BlockPos a = h.absolutePos(new BlockPos(0, 0, 0));
        BlockPos b = h.absolutePos(new BlockPos(15, 5, 15));
        int[] box = {Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ())};
        BlockPos door = h.absolutePos(new BlockPos(5, 1, 0));
        return new Estate.Unit(id, "house", "Дом", "Тестовая, 1", "suburbs", 1000, "тест",
                box, box, door);
    }

    private static boolean rightClickCanceled(FakePlayer who, BlockPos pos) {
        var event = new PlayerInteractEvent.RightClickBlock(who, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static boolean breakCanceled(FakePlayer who, ServerLevel level, BlockPos pos) {
        var event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), who);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    /** Хозяин в сети — чужой не откроет дверь; нет хозяина — откроет; ломать нельзя всегда. */
    @SelfTest
    public static void houseLocks(TestKit h) {
        ServerLevel level = h.getLevel();
        FakePlayer owner = player(h, "Owner");
        FakePlayer guest = player(h, "Guest");
        FakePlayer friend = player(h, "Friend");
        Estate.Unit unit = testHouse(h, "test_house_locks");
        Estate.addTestUnit(unit);
        LifeData life = LifeData.get(level.getServer());
        try {
            h.setBlock(new BlockPos(5, 1, 0), Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, Direction.SOUTH));
            h.setBlock(new BlockPos(8, 1, 8), Blocks.CHEST);
            BlockPos door = h.absolutePos(new BlockPos(5, 1, 0));
            BlockPos chest = h.absolutePos(new BlockPos(8, 1, 8));

            if (rightClickCanceled(guest, door)) {
                h.fail("некупленный дом заперт");
            }
            life.setOwner(unit.id(), owner.getUUID(), "Owner", 0L);
            life.trust(unit.id(), friend.getUUID(), "Friend");

            EstateGuard.TEST_ONLINE.add(owner.getUUID());
            if (!rightClickCanceled(guest, door)) {
                h.fail("хозяин в сети, а чужой открыл дверь");
            }
            if (!rightClickCanceled(guest, chest)) {
                h.fail("хозяин в сети, а чужой открыл сундук");
            }
            if (rightClickCanceled(owner, door) || rightClickCanceled(friend, door)) {
                h.fail("хозяин или друг с ключом не открыл свою дверь");
            }
            EstateGuard.TEST_ONLINE.remove(owner.getUUID());
            if (rightClickCanceled(guest, door)) {
                h.fail("хозяина нет, а дверь заперта");
            }
            int before = life.wanted(guest.getUUID());
            rightClickCanceled(guest, chest);
            if (life.wanted(guest.getUUID()) <= before) {
                h.fail("сундук в чужом доме без хозяина — не кража");
            }
            if (!breakCanceled(guest, level, chest)) {
                h.fail("чужой ломает блоки на участке");
            }
            if (breakCanceled(owner, level, chest)) {
                h.fail("хозяин не может ломать у себя");
            }
        } finally {
            EstateGuard.TEST_ONLINE.remove(owner.getUUID());
            life.clearOwner(unit.id());
            life.setWanted(guest.getUUID(), 0, 0);
            Estate.removeTestUnit(unit.id());
        }
        h.succeed();
    }

    /** Сутки: коммуналка списывается, аренда идёт хозяину; долг — дом отходит городу. */
    @SelfTest
    public static void upkeepAndRent(TestKit h) {
        FakePlayer owner = player(h, "Landlord");
        FakePlayer tenant = player(h, "Tenant");
        var server = h.getLevel().getServer();
        LifeData life = LifeData.get(server);
        CityData bank = CityData.get(server);
        Estate.Unit unit = testHouse(h, "test_house_rent");
        Estate.addTestUnit(unit);
        try {
            long upkeep = dev.lscity.citylife.estate.EstateBills.upkeep(unit);
            life.setOwner(unit.id(), owner.getUUID(), "Landlord", 0L);
            life.setRent(unit.id(), 50);
            life.setTenant(unit.id(), tenant.getUUID(), "Tenant", 0L);
            bank.setBalance(owner.getUUID(), 1000);
            bank.setBalance(tenant.getUUID(), 500);
            dev.lscity.citylife.estate.EstateBills.bill(server, life);
            if (bank.balance(owner.getUUID()) != 1000 - upkeep + 50) {
                h.fail("хозяин: ждали " + (1000 - upkeep + 50) + ", на счёте "
                        + bank.balance(owner.getUUID()));
            }
            if (bank.balance(tenant.getUUID()) != 450) {
                h.fail("арендатор не заплатил: " + bank.balance(tenant.getUUID()));
            }
            if (!life.mayUse(unit.id(), tenant.getUUID())) {
                h.fail("у арендатора нет доступа в дом");
            }
            bank.setBalance(owner.getUUID(), 0);
            bank.setBalance(tenant.getUUID(), 0);
            dev.lscity.citylife.estate.EstateBills.bill(server, life);
            if (life.tenant(unit.id()) != null) {
                h.fail("аренда без денег не закончилась");
            }
            if (life.debt(unit.id()) != upkeep) {
                h.fail("долг не записан: " + life.debt(unit.id()));
            }
            for (int day = 0; day < 10 && life.owner(unit.id()) != null; day++) {
                dev.lscity.citylife.estate.EstateBills.bill(server, life);
            }
            if (life.owner(unit.id()) != null) {
                h.fail("дом с долгом не отошёл городу");
            }
            if (bank.statement(owner.getUUID()).isEmpty()) {
                h.fail("в выписке хозяина нет операций");
            }
        } finally {
            life.clearOwner(unit.id());
            Estate.removeTestUnit(unit.id());
        }
        h.succeed();
    }

    /** Каталог жилья целый: у каждого объекта цена, адрес и дверь на стене дома. */
    @SelfTest
    public static void estateCatalog(TestKit h) {
        int n = 0;
        StringBuilder bad = new StringBuilder();
        for (Estate.Unit unit : Estate.all()) {
            n++;
            int[] b = unit.box();
            BlockPos d = unit.door();
            boolean inside = d.getX() >= b[0] && d.getX() <= b[3] && d.getZ() >= b[2]
                    && d.getZ() <= b[5] && d.getY() >= b[1] && d.getY() <= b[4];
            boolean onEdge = d.getX() == b[0] || d.getX() == b[3] || d.getZ() == b[2]
                    || d.getZ() == b[5];
            if (unit.price() <= 0 || unit.address().isBlank() || !inside || !onEdge) {
                bad.append(unit.id()).append(' ');
            }
            if (Estate.boxAt(unit.outside()) == unit) {
                bad.append(unit.id()).append("(выход внутри) ");
            }
        }
        if (n < 1000) {
            h.fail("в каталоге только " + n + " объектов");
        }
        if (bad.length() > 0) {
            h.fail("кривые объекты: " + bad.substring(0, Math.min(400, bad.length())));
        }
        h.succeed();
    }

    // --- 112 и розыск -------------------------------------------------------------

    /** Наряд приезжает с машиной и двумя людьми и уезжает по «отбою». */
    @SelfTest
    public static void crewArrivesAndLeaves(TestKit h) {
        ServerLevel level = h.getLevel();
        for (String kind : new String[]{"police", "medic", "fire"}) {
            BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
            int id = Emergency.dispatch(level.getServer(), kind, Vec3.atBottomCenterOf(at));
            var ids = Emergency.crewEntities(id);
            long alive = ids.stream().map(level::getEntity).filter(e -> e != null).count();
            if (alive < 3) {
                Emergency.recall(level.getServer(), id);
                h.fail(kind + ": на месте " + alive + " из 3 (машина и двое)");
            }
            Emergency.recall(level.getServer(), id);
            long left = ids.stream().map(level::getEntity).filter(e -> e != null && e.isAlive())
                    .count();
            if (left > 0) {
                h.fail(kind + ": после отбоя остались " + left);
            }
        }
        h.succeed();
    }

    /** Машина встаёт поодаль, а сотрудники сами доходят до места вызова. */
    @SelfTest(timeout = 300)
    public static void crewWalksToScene(TestKit h) {
        ServerLevel level = h.getLevel();
        BlockPos scene = h.absolutePos(new BlockPos(2, 1, 2));
        int id = Emergency.dispatch(level.getServer(), "medic", Vec3.atBottomCenterOf(scene));
        h.succeedWhen(() -> {
            double best = Double.MAX_VALUE;
            for (UUID uuid : Emergency.crewEntities(id)) {
                Entity e = level.getEntity(uuid);
                if (e != null && e.getTags().contains("citylife_resp_medic")) {
                    best = Math.min(best, Math.sqrt(e.distanceToSqr(Vec3.atBottomCenterOf(scene))));
                }
            }
            if (best > 3.0D) {
                h.fail("медики не дошли: ближайший в " + Math.round(best) + " блоках");
            }
            Emergency.recall(level.getServer(), id);
        });
    }

    /** Кража даёт звезду, задержание снимает розыск, штрафует и сажает в камеру. */
    @SelfTest
    public static void wantedAndArrest(TestKit h) {
        FakePlayer thief = player(h, "Thief");
        LifeData life = LifeData.get(h.getLevel().getServer());
        CityData bank = CityData.get(h.getLevel().getServer());
        try {
            bank.setBalance(thief.getUUID(), 5000);
            Wanted.crime(thief, 2, "citylife.wanted.theft");
            if (life.wanted(thief.getUUID()) != 2) {
                h.fail("звёзды не начислены");
            }
            Wanted.arrest(thief, false);
            if (life.wanted(thief.getUUID()) != 0) {
                h.fail("после ареста остался розыск");
            }
            if (life.jailUntil(thief.getUUID()) <= h.getLevel().getGameTime()) {
                h.fail("после ареста не в камере");
            }
            if (bank.balance(thief.getUUID()) >= 5000) {
                h.fail("штраф не списан");
            }
        } finally {
            life.setWanted(thief.getUUID(), 0, 0);
            life.setJail(thief.getUUID(), 0);
        }
        h.succeed();
    }

    // --- работа -------------------------------------------------------------------

    /** Дойти до точки задания и дать ему проверить (как раз в секунду в игре). */
    private static void arrive(FakePlayer who, Waypoint point) {
        who.moveTo(point.x() + 0.5D, point.y(), point.z() + 0.5D);
        Jobs.check(who);
    }

    private static void deliveryPays(TestKit h, String kind, net.minecraft.world.item.Item cargo) {
        FakePlayer worker = player(h, "Worker_" + kind);
        CityData bank = CityData.get(h.getLevel().getServer());
        long before = bank.balance(worker.getUUID());
        Jobs.take(worker, kind);
        Waypoint pickup = Jobs.target(worker);
        if (pickup == null) {
            h.fail(kind + ": задание не выдано");
            return;
        }
        arrive(worker, pickup);
        boolean hasCargo = worker.getInventory().items.stream().anyMatch(st -> st.is(cargo));
        Waypoint door = Jobs.target(worker);
        if (!hasCargo || door == null || door.equals(pickup)) {
            Jobs.quit(worker, false);
            h.fail(kind + ": на месте выдачи груз не выдан");
        }
        arrive(worker, door);
        if (Jobs.active(worker)) {
            Jobs.quit(worker, false);
            h.fail(kind + ": у двери задание не засчитано");
        }
        if (bank.balance(worker.getUUID()) <= before) {
            h.fail(kind + ": за доставку не заплатили");
        }
        if (worker.getInventory().items.stream().anyMatch(st -> st.is(cargo))) {
            h.fail(kind + ": груз не забрали при сдаче");
        }
    }

    /** Курьер: посылка в пункте выдачи, сдача у двери, оплата. */
    @SelfTest
    public static void courierPays(TestKit h) {
        deliveryPays(h, "courier", Registration.PARCEL.get());
        h.succeed();
    }

    /** Доставка еды: пакет в кафе, сдача у двери, оплата. */
    @SelfTest
    public static void foodPays(TestKit h) {
        deliveryPays(h, "food", Registration.FOOD_BAG.get());
        h.succeed();
    }

    /** Мусорщик: мешки на улицах, собрал — сдал на склад — оплата. */
    @SelfTest
    public static void garbagePays(TestKit h) {
        FakePlayer worker = player(h, "Garbage");
        ServerLevel level = h.getLevel();
        CityData bank = CityData.get(level.getServer());
        long before = bank.balance(worker.getUUID());
        Jobs.take(worker, "garbage");
        if (!Jobs.active(worker)) {
            h.fail("мусорщик: задание не выдано (нет загруженных точек?)");
        }
        // Подбираем все светящиеся мешки этого задания.
        var bags = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(worker.blockPosition()).inflate(300),
                e -> e.getItem().is(Registration.TRASH_BAG.get()));
        if (bags.isEmpty()) {
            List<String> where = new java.util.ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ItemEntity item && item.getItem().is(Registration.TRASH_BAG.get())) {
                    where.add(e.blockPosition().toShortString());
                }
            }
            Waypoint first = Jobs.target(worker);
            Jobs.quit(worker, false);
            h.fail("мусорщик: мешков рядом нет; всего в мире " + where + ", игрок "
                    + worker.blockPosition().toShortString() + ", первая точка "
                    + (first == null ? "-" : first.x() + "," + first.y() + "," + first.z()));
        }
        for (ItemEntity bag : bags) {
            worker.getInventory().add(bag.getItem().copy());
            bag.discard();
        }
        Jobs.check(worker);
        Waypoint dump = Jobs.target(worker);
        arrive(worker, dump);
        if (Jobs.active(worker)) {
            Jobs.quit(worker, false);
            h.fail("мусорщик: на складе не засчитано");
        }
        if (bank.balance(worker.getUUID()) <= before) {
            h.fail("мусорщик: не заплатили");
        }
        h.succeed();
    }

    /** Такси без машины не берётся. */
    @SelfTest
    public static void taxiNeedsCar(TestKit h) {
        FakePlayer driver = player(h, "Driver");
        Jobs.take(driver, "taxi");
        if (Jobs.active(driver)) {
            Jobs.quit(driver, false);
            h.fail("такси взято без машины");
        }
        h.succeed();
    }

    /** Дежурный полицейский задерживает разыскиваемого кликом и получает награду. */
    @SelfTest
    public static void dutyPoliceArrests(TestKit h) {
        FakePlayer officer = player(h, "Officer");
        FakePlayer thief = player(h, "Robber");
        var server = h.getLevel().getServer();
        LifeData life = LifeData.get(server);
        CityData bank = CityData.get(server);
        try {
            dev.lscity.citylife.jobs.Duty.set(officer, "police");
            life.setWanted(thief.getUUID(), 2, h.getLevel().getGameTime() + 6000);
            long before = bank.balance(officer.getUUID());
            officer.interactOn(thief, InteractionHand.MAIN_HAND);
            if (life.wanted(thief.getUUID()) != 0 || life.jailUntil(thief.getUUID()) == 0) {
                h.fail("разыскиваемый не задержан");
            }
            if (bank.balance(officer.getUUID()) <= before) {
                h.fail("полицейскому не заплатили за задержание");
            }
        } finally {
            dev.lscity.citylife.jobs.Duty.set(officer, "off");
            life.setWanted(thief.getUUID(), 0, 0);
            life.setJail(thief.getUUID(), 0);
        }
        h.succeed();
    }

    // --- ноутбук ------------------------------------------------------------------

    /** Сломанный ноутбук выпадает целым, с данными. */
    @SelfTest
    public static void laptopDropsWithData(TestKit h) {
        BlockPos rel = new BlockPos(4, 1, 4);
        h.setBlock(rel, Registration.LAPTOP_BLOCK.get().defaultBlockState()
                .setValue(LaptopBlock.OPEN, true));
        ItemStack laptop = new ItemStack(Registration.DEVICES.get("laptop").get());
        laptop.getOrCreateTag().putString("notes", "проверка");
        if (!(h.getBlockEntity(rel) instanceof LaptopBlockEntity be)) {
            h.fail("нет блок-сущности ноутбука");
            return;
        }
        var laptopItem = laptop.getItem();
        be.setStack(laptop);
        h.destroyBlock(rel);
        BlockPos at = h.absolutePos(rel);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(at).inflate(2));
        boolean ok = drops.stream().anyMatch(item -> item.getItem().is(laptopItem)
                && "проверка".equals(item.getItem().getOrCreateTag().getString("notes")));
        if (!ok) {
            h.fail("ноутбук не выпал или потерял данные: блок="
                    + h.getLevel().getBlockState(at) + " выпало=" + drops.stream()
                    .map(item -> item.getItem() + String.valueOf(item.getItem().getTag())).toList());
        }
        h.succeed();
    }

    // --- звонки и такси -----------------------------------------------------------

    /** Телефон с SIM в руку тестовому игроку; номер один на игрока. */
    private static int giveSim(FakePlayer player) {
        CityData data = CityData.get(player.server);
        int number = data.numberOf(player.getUUID());
        if (number == 0) {
            number = data.issueNumber(player.getUUID());
        }
        ItemStack phone = new ItemStack(Registration.DEVICES.get("smartphone").get());
        dev.lscity.citylife.device.DeviceState.setSim(phone.getOrCreateTag(), number);
        player.getInventory().clearContent();
        player.getInventory().add(phone);
        return number;
    }

    /** Звонок: гудки у второго, ответ, разговор, сброс; занято, если уже говорит. */
    @SelfTest
    public static void phoneCallConnects(TestKit h) {
        FakePlayer alice = player(h, "Alice");
        FakePlayer bob = player(h, "Bob");
        FakePlayer carol = player(h, "Carol");
        dev.lscity.citylife.city.Online.TEST.addAll(List.of(alice, bob, carol));
        try {
            int from = giveSim(alice);
            int to = giveSim(bob);
            giveSim(carol);
            if (dev.lscity.citylife.net.DeviceServer.simInInventory(alice) != from) {
                h.fail("номер SIM в телефоне не виден");
            }
            dev.lscity.citylife.phone.Calls.dial(alice, from, to);
            var call = dev.lscity.citylife.phone.Calls.of(bob.getUUID());
            if (call == null || !"incoming".equals(dev.lscity.citylife.phone.Calls.snapshot(bob)
                    .getString("state"))) {
                h.fail("у второго нет входящего звонка");
            }
            dev.lscity.citylife.phone.Calls.dial(carol,
                    dev.lscity.citylife.net.DeviceServer.simInInventory(carol), to);
            if (dev.lscity.citylife.phone.Calls.of(carol.getUUID()) != null) {
                h.fail("дозвонились до занятого абонента");
            }
            dev.lscity.citylife.phone.Calls.answer(bob);
            if (!"active".equals(dev.lscity.citylife.phone.Calls.snapshot(alice).getString("state"))) {
                h.fail("после ответа разговор не начался");
            }
            dev.lscity.citylife.phone.Calls.hangup(alice);
            if (dev.lscity.citylife.phone.Calls.of(bob.getUUID()) != null) {
                h.fail("после сброса звонок висит у второго");
            }
            if (dev.lscity.citylife.phone.Calls.snapshot(bob).getList("recent", 8).isEmpty()) {
                h.fail("звонка нет в недавних");
            }
        } finally {
            dev.lscity.citylife.phone.Calls.hangup(alice);
            dev.lscity.citylife.phone.Calls.hangup(carol);
            dev.lscity.citylife.city.Online.TEST.removeAll(List.of(alice, bob, carol));
        }
        h.succeed();
    }

    /** Такси: заказ уходит таксисту на смене, он принимает, подача оплачивается. */
    @SelfTest(timeout = 300)
    public static void taxiOrderReachesDriver(TestKit h) {
        FakePlayer client = player(h, "Passenger");
        FakePlayer driver = player(h, "Cabbie");
        CityData bank = CityData.get(h.getLevel().getServer());
        dev.lscity.citylife.city.Online.TEST.addAll(List.of(client, driver));
        Runnable cleanup = () -> {
            dev.lscity.citylife.jobs.Duty.set(driver, "off");
            dev.lscity.citylife.city.Online.TEST.removeAll(List.of(client, driver));
        };
        try {
            orderTaxi(h, client, driver, bank, cleanup);
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
    }

    private static void orderTaxi(TestKit h, FakePlayer client, FakePlayer driver, CityData bank,
                                  Runnable cleanup) {
        if (dev.lscity.citylife.phone.Taxi.order(client) != 0) {
            h.fail("заказ принят, хотя таксистов на смене нет");
        }
        dev.lscity.citylife.jobs.Duty.set(driver, "taxi");
        int id = dev.lscity.citylife.phone.Taxi.order(client);
        if (id == 0) {
            h.fail("таксист на смене не получил заказ");
        }
        if (dev.lscity.citylife.phone.Taxi.accept(driver, id) != 1) {
            h.fail("таксист не смог принять заказ");
        }
        if (bank.route(driver.getUUID()) == null) {
            h.fail("у таксиста нет маршрута к клиенту");
        }
        long before = bank.balance(driver.getUUID());
        driver.moveTo(client.getX() + 3, client.getY(), client.getZ());
        h.succeedWhen(() -> {
            if (bank.balance(driver.getUUID()) < before + dev.lscity.citylife.phone.Taxi.PICKUP_PAY) {
                h.fail("подачу такси не оплатили");
            }
            cleanup.run();
        });
    }

    // --- резервные копии ----------------------------------------------------------

    /** Копия мира пишется в backups/ целиком (с level.dat) и не мешает серверу. */
    @SelfTest(timeout = 1200)
    public static void backupMakesZip(TestKit h) {
        var server = h.getLevel().getServer();
        String[] result = new String[1];
        if (!dev.lscity.citylife.data.Backups.make(server, message -> result[0] = message)) {
            h.fail("копия уже делается");
        }
        h.succeedWhen(() -> {
            if (result[0] == null) {
                h.fail("копия ещё пишется");
            }
            var list = dev.lscity.citylife.data.Backups.list(server);
            if (list.isEmpty()) {
                h.fail("архива нет: " + result[0]);
            }
            java.nio.file.Path zip = list.get(0);
            try (var file = new java.util.zip.ZipFile(zip.toFile())) {
                String root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath().normalize().getFileName().toString();
                if (file.getEntry(root + "/level.dat") == null) {
                    h.fail("в архиве нет level.dat");
                }
                if (h.getLevel().noSave) {
                    h.fail("автосохранение не вернулось после копии");
                }
            } catch (java.io.IOException e) {
                h.fail("архив не читается: " + e.getMessage());
            } finally {
                try {
                    java.nio.file.Files.deleteIfExists(zip);
                } catch (java.io.IOException ignored) {
                    // не страшно
                }
            }
        });
    }

    // --- свои машины --------------------------------------------------------------

    /** Запертая машина не пускает чужого; открытая пускает, но это угон со звездой. */
    @SelfTest
    public static void carLockAndTheft(TestKit h) {
        FakePlayer owner = player(h, "CarOwner");
        FakePlayer thief = player(h, "CarThief");
        LifeData life = LifeData.get(h.getLevel().getServer());
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "vehicle:smart_car");
        BlockPos at = h.absolutePos(new BlockPos(6, 1, 6));
        Entity vehicle = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> {
            e.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0F, 0F);
            return e;
        });
        if (vehicle == null || !dev.lscity.citylife.vehicle.Garage.isVehicle(vehicle)) {
            h.fail("машина мода Vehicle не создаётся");
            return;
        }
        h.getLevel().addFreshEntity(vehicle);
        var garage = dev.lscity.citylife.vehicle.GarageData.get(h.getLevel().getServer());
        try {
            var car = dev.lscity.citylife.vehicle.Garage.claim(owner, vehicle);
            dev.lscity.citylife.vehicle.Garage.setLocked(owner, car, true);
            if (thief.startRiding(vehicle, true)) {
                h.fail("чужой сел в запертую машину");
            }
            if (!owner.startRiding(vehicle, true)) {
                h.fail("хозяин не сел в свою машину");
            }
            owner.stopRiding();
            dev.lscity.citylife.vehicle.Garage.setLocked(owner, car, false);
            life.setWanted(thief.getUUID(), 0, 0);
            if (!thief.startRiding(vehicle, true)) {
                h.fail("в открытую машину не сесть");
            }
            if (life.wanted(thief.getUUID()) == 0) {
                h.fail("угон открытой машины без розыска");
            }
            thief.stopRiding();
            if (dev.lscity.citylife.vehicle.Garage.snapshot(owner).isEmpty()) {
                h.fail("машины нет в «Моём транспорте»");
            }
        } finally {
            life.setWanted(thief.getUUID(), 0, 0);
            vehicle.discard();
        }
        if (garage.car(vehicle.getUUID()) != null) {
            h.fail("разобранная машина осталась в гараже");
        }
        h.succeed();
    }

    // --- больница -----------------------------------------------------------------

    /** Без кровати — к больнице, счёт за лечение и маршрут к месту гибели. */
    @SelfTest
    public static void hospitalRespawn(TestKit h) {
        FakePlayer patient = player(h, "Patient");
        CityData bank = CityData.get(h.getLevel().getServer());
        bank.deposit(patient.getUUID(), 1000);
        long before = bank.balance(patient.getUUID());
        BlockPos died = patient.blockPosition();
        BlockPos spot = dev.lscity.citylife.city.Hospital.admit(patient, died);
        if (spot == null) {
            h.fail("игрока без кровати не отправили в больницу");
            return;
        }
        // FakePlayer не телепортируется (нет соединения) — проверяем саму точку.
        Waypoint door = dev.lscity.citylife.city.Hospital.entrance();
        if (spot.distSqr(new BlockPos(door.x(), door.y(), door.z())) > 6 * 6) {
            h.fail("возрождение не у больницы: " + spot);
        }
        long bill = dev.lscity.citylife.CityConfig.CONFIG.hospitalBill.get();
        if (bank.balance(patient.getUUID()) != before - bill) {
            h.fail("счёт за лечение не списан");
        }
        Waypoint route = bank.route(patient.getUUID());
        if (route == null || route.x() != died.getX() || route.z() != died.getZ()) {
            h.fail("нет маршрута к месту гибели");
        }
        bank.setRoute(patient.getUUID(), null);
        patient.setRespawnPosition(h.getLevel().dimension(), died, 0F, true, false);
        if (dev.lscity.citylife.city.Hospital.admit(patient, died) != null) {
            h.fail("игрока с кроватью увезли в больницу");
        }
        h.succeed();
    }
}
