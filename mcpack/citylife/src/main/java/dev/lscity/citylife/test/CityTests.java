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
            if (unit.business()) {
                // Вход бизнеса — точка на тротуаре перед зданием, рядом с ним.
                inside = d.getX() >= b[0] - 3 && d.getX() <= b[3] + 3 && d.getZ() >= b[2] - 3
                        && d.getZ() <= b[5] + 3;
                onEdge = true;
            }
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
            if (owner.startRiding(vehicle, true)) {
                h.fail("хозяин сел в запертую машину, не открыв её");
            }
            owner.stopRiding();
            dev.lscity.citylife.vehicle.Garage.setLocked(owner, car, false);
            if (!owner.startRiding(vehicle, true)) {
                h.fail("хозяин не сел в свою открытую машину");
            }
            var panel = dev.lscity.citylife.vehicle.Garage.panel(owner);
            if (!vehicle.getUUID().toString().equals(panel.getString("riding"))
                    || panel.getList("cars", 10).isEmpty()) {
                h.fail("окно «Мой транспорт» не видит машину, в которой сидит хозяин");
            }
            owner.stopRiding();
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

    // --- бизнесы ------------------------------------------------------------------

    /** Бизнес приносит доход раз в сутки, без коммуналки и без замков на двери. */
    @SelfTest
    public static void businessIncome(TestKit h) {
        FakePlayer owner = player(h, "Tycoon");
        var server = h.getLevel().getServer();
        LifeData life = LifeData.get(server);
        CityData bank = CityData.get(server);
        Estate.Unit shop = Estate.all().stream().filter(Estate.Unit::business).findFirst().orElse(null);
        if (shop == null) {
            h.fail("в каталоге нет бизнесов");
            return;
        }
        if (life.owner(shop.id()) != null) {
            h.succeed();
            return;
        }
        try {
            life.setOwner(shop.id(), owner.getUUID(), "Tycoon", 0L);
            bank.setBalance(owner.getUUID(), 0);
            dev.lscity.citylife.estate.EstateBills.bill(server, life);
            long income = dev.lscity.citylife.estate.EstateBills.income(shop);
            if (income <= 0 || bank.balance(owner.getUUID()) != income) {
                h.fail("доход бизнеса: ждали " + income + ", на счёте " + bank.balance(owner.getUUID()));
            }
            if (Estate.plotAt(shop.door()) != null) {
                h.fail("у бизнеса есть защита участка — магазин закроется для покупателей");
            }
        } finally {
            life.clearOwner(shop.id());
        }
        h.succeed();
    }

    // --- ограбления ---------------------------------------------------------------

    /** Роль первого продавца с товаром (кроме риелтора, у него своё окно). */
    private static String shopRole() {
        return ShopCatalog.BY_ROLE.entrySet().stream()
                .filter(e -> !e.getValue().offers().isEmpty() && !"realtor".equals(e.getKey()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    private static void cleanRobber(FakePlayer robber, Vec3 spot) {
        dev.lscity.citylife.city.Robbery.cancel(robber);
        LifeData.get(robber.server).setWanted(robber.getUUID(), 0, 0);
        Emergency.cancelAt(spot, 8);
        robber.getInventory().clearContent();
        robber.setShiftKeyDown(false);
    }

    /** С оружием и присев — касса: две звезды, тревога, через 30 с наличные. */
    @SelfTest
    public static void shopRobbery(TestKit h) {
        FakePlayer robber = player(h, "Robber");
        String role = shopRole();
        Entity clerk = npc(h, new BlockPos(9, 1, 8), "Тест " + role, "citylife_npc",
                "citylife_" + role, ShopCatalog.GEN_TAG);
        LifeData life = LifeData.get(h.getLevel().getServer());
        try {
            robber.setShiftKeyDown(true);
            if (!("shop:" + role).equals(click(robber, clerk))) {
                h.fail("без оружия присевший не попал к прилавку");
            }
            robber.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            String outcome = click(robber, clerk);
            if (!("rob:" + role).equals(outcome)) {
                h.fail("ограбление не началось: " + outcome);
            }
            if (life.wanted(robber.getUUID()) < 2) {
                h.fail("за ограбление нет двух звёзд");
            }
            if (!Emergency.pendingAt(clerk.position(), 4)) {
                h.fail("полиция не выехала к магазину");
            }
            long now = h.getLevel().getGameTime();
            dev.lscity.citylife.city.Robbery.check(robber, now + 20);
            if (!dev.lscity.citylife.city.Robbery.active(robber)) {
                h.fail("ограбление закончилось раньше времени");
            }
            if (dev.lscity.citylife.city.Robbery.hudLine(robber).isEmpty()) {
                h.fail("в углу экрана нет строки ограбления");
            }
            long before = dev.lscity.citylife.economy.Money.cash(robber);
            dev.lscity.citylife.city.Robbery.check(robber, now + 20L * 600);
            if (dev.lscity.citylife.city.Robbery.active(robber)) {
                h.fail("ограбление не закончилось по времени");
            }
            if (dev.lscity.citylife.economy.Money.cash(robber) <= before) {
                h.fail("грабитель не получил наличных");
            }
            if (!("norob:" + role).equals(click(robber, clerk))) {
                h.fail("пустую кассу ограбили второй раз");
            }
            Wanted.arrest(robber, false);
            if (dev.lscity.citylife.economy.Money.cash(robber) != before) {
                h.fail("при задержании награбленное не изъяли");
            }
        } finally {
            life.setJail(robber.getUUID(), 0);
            dev.lscity.citylife.city.Robbery.refill("shop:" + clerk.getUUID());
            cleanRobber(robber, clerk.position());
            clerk.discard();
        }
        h.succeed();
    }

    /** Отошёл от кассы — ограбление сорвалось, денег нет, розыск остался. */
    @SelfTest
    public static void robberyFailsWhenLeaving(TestKit h) {
        FakePlayer robber = player(h, "Runaway");
        String role = shopRole();
        Entity clerk = npc(h, new BlockPos(9, 1, 8), "Тест " + role, "citylife_npc",
                "citylife_" + role, ShopCatalog.GEN_TAG);
        try {
            robber.setShiftKeyDown(true);
            robber.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(net.minecraft.world.item.Items.IRON_AXE));
            if (!("rob:" + role).equals(click(robber, clerk))) {
                h.fail("ограбление не началось");
            }
            robber.moveTo(robber.getX() + 20, robber.getY(), robber.getZ());
            dev.lscity.citylife.city.Robbery.check(robber, h.getLevel().getGameTime() + 20L * 600);
            if (dev.lscity.citylife.city.Robbery.active(robber)) {
                h.fail("ушедший всё ещё грабит");
            }
            if (dev.lscity.citylife.economy.Money.cash(robber) > 0) {
                h.fail("сбежавший получил деньги");
            }
            if (LifeData.get(robber.server).wanted(robber.getUUID()) == 0) {
                h.fail("после сорванного ограбления пропал розыск");
            }
        } finally {
            dev.lscity.citylife.city.Robbery.refill("shop:" + clerk.getUUID());
            cleanRobber(robber, clerk.position());
            clerk.discard();
        }
        h.succeed();
    }

    /** Отмычка по банкомату: три звезды, тревога, деньги через 45 с. */
    @SelfTest
    public static void atmRobbery(TestKit h) {
        FakePlayer robber = player(h, "Safecracker");
        BlockPos atm = h.absolutePos(new BlockPos(9, 1, 8));
        h.setBlock(new BlockPos(9, 1, 8), Registration.ATM.get());
        Vec3 spot = Vec3.atCenterOf(atm);
        try {
            ItemStack pick = new ItemStack(Registration.LOCKPICK.get());
            robber.setItemInHand(InteractionHand.MAIN_HAND, pick);
            pick.useOn(new net.minecraft.world.item.context.UseOnContext(robber,
                    InteractionHand.MAIN_HAND, new BlockHitResult(spot, Direction.WEST, atm, false)));
            if (!dev.lscity.citylife.city.Robbery.active(robber)) {
                h.fail("взлом банкомата не начался");
            }
            if (LifeData.get(robber.server).wanted(robber.getUUID()) < 3) {
                h.fail("за банкомат нет трёх звёзд");
            }
            if (!Emergency.pendingAt(spot, 4)) {
                h.fail("полиция не выехала к банкомату");
            }
            dev.lscity.citylife.city.Robbery.check(robber, h.getLevel().getGameTime() + 20L * 600);
            if (dev.lscity.citylife.economy.Money.cash(robber) <= 0) {
                h.fail("из банкомата не выдали денег");
            }
        } finally {
            dev.lscity.citylife.city.Robbery.refill("atm:" + atm.asLong());
            cleanRobber(robber, spot);
        }
        h.succeed();
    }

    // --- горожане -----------------------------------------------------------------

    /** Имя прохожего подходит к скину: по полу и происхождению; есть и патрульные. */
    @SelfTest
    public static void citizenNamesFit(TestKit h) {
        var random = net.minecraft.util.RandomSource.create(7);
        boolean women = false, men = false, patrol = false;
        for (int i = 0; i < 400; i++) {
            var look = dev.lscity.citylife.city.Citizens.pick(random);
            if (!dev.lscity.citylife.city.Citizens.fits(look)) {
                h.fail("имя не подходит к скину: " + look.name() + " / " + look.skin());
            }
            women |= look.skin().startsWith("woman_");
            men |= look.skin().startsWith("man_");
            patrol |= look.patrol();
        }
        if (!women || !men || !patrol) {
            h.fail("не все виды прохожих: женщины " + women + ", мужчины " + men
                    + ", патрульные " + patrol);
        }
        h.succeed();
    }

    /** Прохожего можно ранить и убить: он убегает, падают деньги, убийце — розыск. */
    @SelfTest
    public static void citizenDiesDropsCash(TestKit h) {
        FakePlayer killer = player(h, "Killer");
        LifeData life = LifeData.get(h.getLevel().getServer());
        Entity walker = dev.lscity.citylife.city.Pedestrians.spawnWalker(h.getLevel(),
                h.absolutePos(new BlockPos(9, 1, 8)), h.getLevel().getRandom());
        if (!(walker instanceof net.minecraft.world.entity.LivingEntity living)) {
            h.fail("прохожий не создался");
            return;
        }
        try {
            float before = living.getHealth();
            living.hurt(h.getLevel().damageSources().playerAttack(killer), 2F);
            if (living.getHealth() >= before) {
                h.fail("прохожий не получает урон (Easy NPC неуязвим)");
            }
            if (!dev.lscity.citylife.city.Pedestrians.fleeing(walker)) {
                h.fail("раненый прохожий не убегает");
            }
            if (life.wanted(killer.getUUID()) < 1) {
                h.fail("за нападение на прохожего нет звезды");
            }
            living.hurt(h.getLevel().damageSources().playerAttack(killer), 1000F);
            if (living.isAlive()) {
                h.fail("прохожий не умирает");
            }
            if (life.wanted(killer.getUUID()) < 2) {
                h.fail("за убийство прохожего нет розыска");
            }
            Entity body = h.getLevel().getEntitiesOfClass(Entity.class,
                    walker.getBoundingBox().inflate(2), dev.lscity.citylife.city.Citizens::isCorpse)
                    .stream().findFirst().orElse(null);
            if (body == null) {
                h.fail("тело не осталось на месте гибели");
                return;
            }
            if (h.getLevel().getEntitiesOfClass(ItemEntity.class, walker.getBoundingBox().inflate(3))
                    .stream().anyMatch(e -> dev.lscity.citylife.economy.Money.value(e.getItem()) > 0)) {
                h.fail("деньги рассыпались по земле, а должны лежать на теле");
            }
            long cashBefore = dev.lscity.citylife.economy.Money.cash(killer);
            if (!"corpse".equals(click(killer, body))) {
                h.fail("клик по телу не обыскал его: " + ShopHandler.lastOutcome);
            }
            if (dev.lscity.citylife.economy.Money.cash(killer) <= cashBefore) {
                h.fail("в карманах тела не было денег");
            }
            body.hurt(h.getLevel().damageSources().playerAttack(killer), 1000F);
            if (!body.isAlive()) {
                h.fail("тело можно убить ещё раз");
            }
            body.discard();
        } finally {
            life.setWanted(killer.getUUID(), 0, 0);
            Emergency.cancelAt(walker.position(), 16);
            killer.getInventory().clearContent();
            walker.discard();
        }
        h.succeed();
    }

    /** Двое идут навстречу по одной линии и расходятся, а не проходят сквозь. */
    @SelfTest(timeout = 300)
    public static void walkersPassEachOther(TestKit h) {
        var level = h.getLevel();
        Entity a = dev.lscity.citylife.city.Pedestrians.spawnWalker(level,
                h.absolutePos(new BlockPos(1, 1, 8)), level.getRandom());
        Entity b = dev.lscity.citylife.city.Pedestrians.spawnWalker(level,
                h.absolutePos(new BlockPos(14, 1, 8)), level.getRandom());
        if (a == null || b == null) {
            h.fail("прохожие не создались");
            return;
        }
        dev.lscity.citylife.city.Pedestrians.sendTo(a, h.absolutePos(new BlockPos(14, 1, 8)));
        dev.lscity.citylife.city.Pedestrians.sendTo(b, h.absolutePos(new BlockPos(1, 1, 8)));
        double[] closest = {Double.MAX_VALUE};
        h.succeedWhen(() -> {
            double dx = a.getX() - b.getX();
            double dz = a.getZ() - b.getZ();
            closest[0] = Math.min(closest[0], Math.sqrt(dx * dx + dz * dz));
            if (closest[0] < 0.45D) {
                a.discard();
                b.discard();
                throw new IllegalStateException("прохожие прошли друг сквозь друга: "
                        + String.format("%.2f", closest[0]));
            }
            boolean passed = (a.getX() - b.getX()) * Math.signum(
                    h.absolutePos(new BlockPos(14, 0, 0)).getX() - h.absolutePos(BlockPos.ZERO).getX()) > 1;
            if (!passed) {
                h.fail("ещё не разошлись, ближе всего " + String.format("%.2f", closest[0]));
            }
            a.discard();
            b.discard();
        });
    }

    /** Клик по прохожему — разговор с вариантами; дорогу до банка ставит в навигатор. */
    @SelfTest
    public static void citizenTalks(TestKit h) {
        FakePlayer talker = player(h, "Talker");
        Entity walker = dev.lscity.citylife.city.Pedestrians.spawnWalker(h.getLevel(),
                h.absolutePos(new BlockPos(9, 1, 8)), h.getLevel().getRandom());
        if (walker == null) {
            h.fail("прохожий не создался");
            return;
        }
        CityData bank = CityData.get(h.getLevel().getServer());
        try {
            if (!"citizen".equals(click(talker, walker))) {
                h.fail("клик по прохожему не начал разговор: " + ShopHandler.lastOutcome);
            }
            if (!dev.lscity.citylife.city.Citizens.lastOutcome.startsWith("talk:")) {
                h.fail("прохожий не ответил: " + dev.lscity.citylife.city.Citizens.lastOutcome);
            }
            dev.lscity.citylife.city.Citizens.answer(talker, walker, "way:bank");
            Waypoint route = bank.route(talker.getUUID());
            if (route == null || !"bank".equals(route.icon())) {
                h.fail("прохожий не подсказал дорогу до банка");
            }
            dev.lscity.citylife.city.Citizens.answer(talker, walker, "chat");
            if (!"chat".equals(dev.lscity.citylife.city.Citizens.lastOutcome)) {
                h.fail("на «как дела» нет ответа");
            }
            talker.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            click(talker, walker);
            if (!"scared".equals(dev.lscity.citylife.city.Citizens.lastOutcome)) {
                h.fail("прохожий не испугался оружия");
            }
        } finally {
            bank.setRoute(talker.getUUID(), null);
            talker.getInventory().clearContent();
            walker.discard();
        }
        h.succeed();
    }

    /** Полицейский окликает игрока с оружием, через 3 секунды стреляет; безоружного — нет. */
    @SelfTest
    public static void policeShootsArmed(TestKit h) {
        FakePlayer gunman = player(h, "Gunman");
        Entity cop = npc(h, new BlockPos(2, 1, 8), "Сержант Тест", "citylife_npc", "citylife_cop",
                ShopCatalog.GEN_TAG);
        try {
            if (!(cop instanceof net.minecraft.world.entity.Mob officer)
                    || !dev.lscity.citylife.city.Police.isOfficer(cop)) {
                h.fail("постовой не считается полицейским");
                return;
            }
            long now = h.getLevel().getGameTime();
            int before = dev.lscity.citylife.city.Police.shotsAt(gunman);
            dev.lscity.citylife.city.Police.check(officer, gunman, now);
            if (dev.lscity.citylife.city.Police.shotsAt(gunman) != before) {
                h.fail("стрелял в безоружного");
            }
            gunman.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            dev.lscity.citylife.city.Police.check(officer, gunman, now);
            if (dev.lscity.citylife.city.Police.shotsAt(gunman) != before) {
                h.fail("выстрелил без предупреждения");
            }
            if (officer.getMainHandItem().isEmpty()) {
                h.fail("у полицейского нет пистолета в руке");
            }
            dev.lscity.citylife.city.Police.check(officer, gunman, now + 80);
            if (dev.lscity.citylife.city.Police.shotsAt(gunman) != before + 1) {
                h.fail("не выстрелил в того, кто не убрал оружие");
            }
            gunman.getInventory().clearContent();
            dev.lscity.citylife.city.Police.check(officer, gunman, now + 200);
            if (dev.lscity.citylife.city.Police.shotsAt(gunman) != before + 1) {
                h.fail("стрелял после того, как оружие убрали");
            }
        } finally {
            gunman.getInventory().clearContent();
            cop.discard();
        }
        h.succeed();
    }

    // --- сидеть -------------------------------------------------------------------

    /** Клик по ступеньке — сел на невидимое сиденье; встал — сиденье пропало. */
    @SelfTest
    public static void sitOnStairs(TestKit h) {
        FakePlayer sitter = player(h, "Sitter");
        BlockPos stairs = h.absolutePos(new BlockPos(8, 1, 6));
        h.setBlock(new BlockPos(8, 1, 6), Blocks.OAK_STAIRS);
        if (!rightClickCanceled(sitter, stairs)) {
            h.fail("клик пустой рукой по ступеньке не посадил");
        }
        if (!(sitter.getVehicle() instanceof dev.lscity.citylife.sit.SeatEntity seat)) {
            h.fail("игрок не сидит на сиденье");
            return;
        }
        sitter.stopRiding();
        h.succeedWhen(() -> {
            if (seat.isAlive()) {
                h.fail("сиденье осталось после того, как встали");
            }
        });
    }

    // --- телефон ------------------------------------------------------------------

    /** Приложение из магазина ставится и удаляется; встроенное удалить нельзя. */
    @SelfTest
    public static void phoneAppRemove(TestKit h) {
        FakePlayer owner = player(h, "PhoneOwner");
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("citylife", "smartphone"));
        if (item == null) {
            h.fail("нет предмета citylife:smartphone");
            return;
        }
        ItemStack phone = new ItemStack(item);
        owner.setItemInHand(InteractionHand.MAIN_HAND, phone);
        try {
            CompoundTag args = new CompoundTag();
            args.put("ctx", dev.lscity.citylife.net.DeviceServer.handContext(InteractionHand.MAIN_HAND));
            args.putString("app", "tetris");
            // Магазину нужен интернет: без SIM телефон оффлайн — вставим номер.
            dev.lscity.citylife.device.DeviceState.setSim(phone.getOrCreateTag(), 4821);
            dev.lscity.citylife.net.DeviceServer.handle(owner, "app_install", args);
            var state = owner.getMainHandItem().getOrCreateTag();
            if (!dev.lscity.citylife.device.DeviceState.installed(state).contains("tetris")) {
                h.fail("приложение не установилось");
            }
            dev.lscity.citylife.net.DeviceServer.handle(owner, "app_remove", args);
            if (dev.lscity.citylife.device.DeviceState.installed(state).contains("tetris")) {
                h.fail("приложение не удалилось");
            }
            args.putString("app", "bank");
            dev.lscity.citylife.net.DeviceServer.handle(owner, "app_remove", args);
            if (!dev.lscity.citylife.device.DeviceState.apps(dev.lscity.citylife.device.Devices.LS_PHONE,
                    state).contains("bank")) {
                h.fail("встроенное приложение пропало");
            }
        } finally {
            owner.getInventory().clearContent();
        }
        h.succeed();
    }

    // --- мир ------------------------------------------------------------------

    /** Гравий на крыше в старом мире заменяется туфом и больше не падает. */
    @SelfTest
    public static void gravelRoofFixed(TestKit h) {
        BlockPos roof = h.absolutePos(new BlockPos(4, 4, 4));
        h.getLevel().setBlock(roof, Blocks.GRAVEL.defaultBlockState(), 2 | 16);
        dev.lscity.citylife.city.WorldFixes.fix(h.getLevel(), h.getLevel().getChunkAt(roof));
        if (!h.getLevel().getBlockState(roof).is(Blocks.TUFF)) {
            h.fail("гравий над пустотой не заменён: " + h.getLevel().getBlockState(roof));
        }
        h.getLevel().setBlock(roof, Blocks.AIR.defaultBlockState(), 2);
        h.succeed();
    }

    /** Сбитый машиной прохожий получает урон по скорости; продавца машина не убьёт. */
    @SelfTest
    public static void carHitsWalker(TestKit h) {
        FakePlayer driver = player(h, "Driver");
        Entity walker = dev.lscity.citylife.city.Pedestrians.spawnWalker(h.getLevel(),
                h.absolutePos(new BlockPos(9, 1, 8)), h.getLevel().getRandom());
        Entity clerk = npc(h, new BlockPos(12, 1, 8), "Продавец", "citylife_npc", "citylife_clerk",
                ShopCatalog.GEN_TAG);
        LifeData life = LifeData.get(h.getLevel().getServer());
        try {
            if (!(walker instanceof net.minecraft.world.entity.LivingEntity living)) {
                h.fail("прохожий не создался");
                return;
            }
            float before = living.getHealth();
            int hits = dev.lscity.citylife.city.Citizens.hitWalkers(h.getLevel(), driver, walker,
                    0.5D, new Vec3(1, 0, 0), h.getLevel().getGameTime());
            if (hits != 1 || living.getHealth() >= before) {
                h.fail("машина не сбила прохожего: ударов " + hits);
            }
            clerk.hurt(h.getLevel().damageSources().playerAttack(driver), 1000F);
            if (!clerk.isAlive()) {
                h.fail("продавца можно убить");
            }
        } finally {
            life.setWanted(driver.getUUID(), 0, 0);
            walker.discard();
            clerk.discard();
        }
        h.succeed();
    }

    // --- касса ----------------------------------------------------------------

    /** Касса встаёт на прилавок у продавца; картой платят со счёта, чужая карта не проходит. */
    @SelfTest
    public static void checkoutPaysByCard(TestKit h) {
        FakePlayer buyer = player(h, "Shopper");
        FakePlayer stranger = player(h, "CardThief");
        String role = shopRole();
        // Продавец за прилавком: прилавок — блок кварца перед ним, за прилавком проход.
        Entity clerk = npc(h, new BlockPos(8, 1, 10), "Тест " + role, "citylife_npc",
                "citylife_" + role, ShopCatalog.GEN_TAG);
        h.setBlock(new BlockPos(8, 1, 9), Blocks.QUARTZ_BLOCK);
        CityData bank = CityData.get(h.getLevel().getServer());
        try {
            BlockPos register = dev.lscity.citylife.trade.Registers.place(h.getLevel(),
                    (net.minecraft.world.entity.Mob) clerk);
            if (register == null || !(h.getLevel().getBlockState(register).getBlock()
                    instanceof dev.lscity.citylife.block.CashRegisterBlock)) {
                h.fail("касса не встала на прилавок");
            }
            if (dev.lscity.citylife.block.CashRegisterBlock.nearestClerk(h.getLevel(), register) != clerk) {
                h.fail("касса не нашла своего продавца");
            }
            var shop = ShopHandler.shopOf(clerk);
            var line = dev.lscity.citylife.trade.Checkout.lines(shop).get(0);
            ListTag cart = new ListTag();
            CompoundTag entry = new CompoundTag();
            entry.putInt("i", line.index());
            entry.putInt("n", 2);
            cart.add(entry);
            buyer.moveTo(clerk.getX(), clerk.getY(), clerk.getZ() - 2);
            stranger.moveTo(clerk.getX(), clerk.getY(), clerk.getZ() - 2);
            bank.setBalance(buyer.getUUID(), line.price() * 3);
            var noCard = dev.lscity.citylife.trade.Checkout.pay(buyer, clerk, cart, "card");
            if (noCard.ok()) {
                h.fail("оплата картой прошла без карты");
            }
            ItemStack card = dev.lscity.citylife.economy.BankCardItem.issue(Registration.CARD_MIR.get(), buyer);
            buyer.getInventory().add(card.copy());
            var paid = dev.lscity.citylife.trade.Checkout.pay(buyer, clerk, cart, "card");
            if (!paid.ok() || bank.balance(buyer.getUUID()) != line.price()) {
                h.fail("картой не списалось: " + paid.message() + ", на счёте "
                        + bank.balance(buyer.getUUID()));
            }
            if (buyer.getInventory().items.stream().noneMatch(st -> ItemStack.isSameItem(st, line.goods()))) {
                h.fail("товар не выдан после оплаты картой");
            }
            var broke = dev.lscity.citylife.trade.Checkout.pay(buyer, clerk, cart, "card");
            if (broke.ok()) {
                h.fail("оплата прошла при нехватке денег на счёте");
            }
            stranger.getInventory().add(card.copy());
            bank.setBalance(stranger.getUUID(), 0);
            var stolen = dev.lscity.citylife.trade.Checkout.pay(stranger, clerk, cart, "card");
            if (stolen.ok()) {
                h.fail("терминал принял чужую карту");
            }
            dev.lscity.citylife.economy.Money.give(stranger, line.price() * 2);
            var cash = dev.lscity.citylife.trade.Checkout.pay(stranger, clerk, cart, "cash");
            if (!cash.ok() || dev.lscity.citylife.economy.Money.cash(stranger) != 0) {
                h.fail("наличными не оплатилось: " + cash.message());
            }
            if (register != null) {
                h.getLevel().setBlock(register, Blocks.AIR.defaultBlockState(), 2);
            }
        } finally {
            buyer.getInventory().clearContent();
            stranger.getInventory().clearContent();
            clerk.discard();
        }
        h.succeed();
    }
}
