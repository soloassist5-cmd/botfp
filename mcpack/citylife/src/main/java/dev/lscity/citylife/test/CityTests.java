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

import java.util.ArrayList;
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
        // Внешность фиксированная: случайный прохожий мог выйти патрульным,
        // а патрульный ходит своим маршрутом.
        Entity walker = dev.lscity.citylife.city.Pedestrians.spawnWalker(h.getLevel(), from,
                h.getLevel().getRandom(), new dev.lscity.citylife.city.Citizens.Look("man_2",
                        "Артём", false));
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
                h.absolutePos(new BlockPos(9, 1, 8)), h.getLevel().getRandom(),
                new dev.lscity.citylife.city.Citizens.Look("man_1", "Максим", false));
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
            dev.lscity.citylife.city.Citizens.answer(talker, walker, "way:realty");
            route = bank.route(talker.getUUID());
            if (route == null || !"realty".equals(route.icon())) {
                h.fail("прохожий не подсказал дорогу до агентства недвижимости");
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

    /** На компьютере не меньше сотни программ, и ни одна не повторяется. */
    @SelfTest
    public static void computerHasHundredApps(TestKit h) {
        var apps = dev.lscity.citylife.device.Devices.COMPUTER.apps();
        if (apps.size() < 100) {
            h.fail("программ на компьютере " + apps.size() + ", нужно не меньше 100");
        }
        if (new java.util.HashSet<>(apps).size() != apps.size()) {
            h.fail("в списке программ компьютера есть повторы");
        }
        h.succeed();
    }

    /** Лифт: кнопки друг над другом — одна шахта, игрок выходит на полу выбранного этажа. */
    @SelfTest
    public static void elevatorRides(TestKit h) {
        FakePlayer rider = player(h, "Rider");
        var panel = Registration.ELEVATOR.get().defaultBlockState()
                .setValue(dev.lscity.citylife.block.ElevatorBlock.FACING, net.minecraft.core.Direction.NORTH);
        // Над площадкой могли остаться кнопки от прошлых прогонов: площадка
        // чистится только до высоты 7, а лишняя кнопка — лишний этаж шахты.
        for (int y = 8; y <= 16; y++) {
            for (int x = 3; x <= 5; x++) {
                for (int z = 2; z <= 4; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        // Два этажа (площадка теста — до высоты 7): пол на 0 и 4, стена шахты
        // сзади, кнопка на высоте груди.
        for (int fy : new int[]{0, 4}) {
            for (int x = 3; x <= 5; x++) {
                for (int z = 2; z <= 4; z++) {
                    h.setBlock(new BlockPos(x, fy, z), Blocks.STONE);
                }
            }
            for (int y = fy + 1; y <= fy + 3; y++) {
                h.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
                h.setBlock(new BlockPos(4, y, 3), Blocks.AIR);
            }
            h.setBlock(new BlockPos(4, fy + 2, 3), panel);
        }
        BlockPos ground = h.absolutePos(new BlockPos(4, 2, 3));
        var floors = dev.lscity.citylife.building.Elevator.floors(h.getLevel(), ground);
        if (floors.size() != 2) {
            h.fail("кнопок в шахте " + floors.size() + ", нужно 2");
        }
        // Куда нет кнопки — не едет.
        rider.moveTo(ground.getX() + 0.5, ground.getY() - 1, ground.getZ() - 0.5);
        CompoundTag args = new CompoundTag();
        args.putLong("pos", ground.asLong());
        args.putInt("y", ground.getY() + 3);
        double before = rider.getY();
        dev.lscity.citylife.building.Elevator.handle(rider, "elevator_go", args);
        if (rider.getY() != before) {
            h.fail("лифт увёз туда, где нет кнопки");
        }
        args.putInt("y", ground.getY() + 4);
        dev.lscity.citylife.building.Elevator.handle(rider, "elevator_go", args);
        int expected = h.absolutePos(new BlockPos(4, 5, 2)).getY();
        if ((int) Math.floor(rider.getY()) != expected) {
            h.fail("лифт привёз на высоту " + rider.getY() + ", а пол второго этажа — " + expected
                    + "; кнопки " + floors + ", игрок " + rider.blockPosition()
                    + ", до кнопки " + rider.distanceToSqr(ground.getX() + 0.5, ground.getY() + 0.5,
                    ground.getZ() + 0.5));
        }
        h.succeed();
    }

    // --- башня STARK -------------------------------------------------------------

    /** Пульт охраны на площадке: зона — вся площадка. */
    private static dev.lscity.citylife.stark.SecurityConsoleBlockEntity console(TestKit h) {
        h.setBlock(new BlockPos(1, 1, 1), Registration.SECURITY_CONSOLE.get());
        var be = (dev.lscity.citylife.stark.SecurityConsoleBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
        BlockPos a = h.absolutePos(new BlockPos(0, 0, 0));
        BlockPos b = h.absolutePos(new BlockPos(16, 6, 16));
        be.setZones(java.util.List.of(new net.minecraft.world.phys.AABB(a, b)));
        return be;
    }

    /** Охрана: посторонний получает отсчёт, потом тревогу и розыск; с допуском — ничего. */
    @SelfTest
    public static void starkIntruderAlarm(TestKit h) {
        FakePlayer stranger = player(h, "Intruder");
        FakePlayer tony = player(h, "Tony");
        var server = h.getLevel().getServer();
        var data = dev.lscity.citylife.stark.StarkData.get(server);
        LifeData life = LifeData.get(server);
        console(h);
        boolean armed = data.armed();
        try {
            data.setArmed(true);
            dev.lscity.citylife.stark.StarkSecurity.resetForTests();
            data.grant(tony.getUUID(), "Tony");
            long now = h.getLevel().getGameTime();
            int grace = dev.lscity.citylife.stark.StarkSecurity.GRACE;
            if (!dev.lscity.citylife.stark.StarkSecurity.check(stranger, data, now)) {
                h.fail("игрок на площадке не в охраняемой зоне");
            }
            dev.lscity.citylife.stark.StarkSecurity.check(stranger, data, now + grace - 20);
            if (life.wanted(stranger.getUUID()) != 0) {
                h.fail("тревога раньше конца отсчёта");
            }
            dev.lscity.citylife.stark.StarkSecurity.check(tony, data, now + grace + 5);
            if (life.wanted(tony.getUUID()) != 0) {
                h.fail("охрана подняла тревогу на игрока с допуском");
            }
            dev.lscity.citylife.stark.StarkSecurity.check(stranger, data, now + grace + 5);
            if (life.wanted(stranger.getUUID()) < 3) {
                h.fail("после отсчёта нет розыска: " + life.wanted(stranger.getUUID()));
            }
            if (!dev.lscity.citylife.stark.StarkSecurity.alarm(now + grace + 10)) {
                h.fail("тревога не включилась");
            }
        } finally {
            life.setWanted(stranger.getUUID(), 0, 0);
            data.revoke(tony.getUUID());
            data.setArmed(armed);
            dev.lscity.citylife.stark.StarkSecurity.removeZones(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)));
            dev.lscity.citylife.stark.StarkSecurity.resetForTests();
        }
        h.succeed();
    }

    /** 3D-принтер: печатает телефон Старка с допуском, без допуска и деньги — нет. */
    @SelfTest(timeout = 240)
    public static void starkPrinterPrints(TestKit h) {
        FakePlayer tony = player(h, "TonyPrinter");
        FakePlayer stranger = player(h, "NoAccess");
        var data = dev.lscity.citylife.stark.StarkData.get(h.getLevel().getServer());
        BlockPos rel = new BlockPos(8, 1, 6);
        h.setBlock(rel, Registration.PRINTER.get());
        BlockPos pos = h.absolutePos(rel);
        var printer = (dev.lscity.citylife.stark.PrinterBlockEntity) h.getBlockEntity(rel);
        CompoundTag args = new CompoundTag();
        args.putLong("pos", pos.asLong());
        args.putString("item", "citylife:banknote_5000");
        args.putInt("count", 1);
        data.grant(tony.getUUID(), "TonyPrinter");
        dev.lscity.citylife.stark.Printer.handle(tony, "printer_print", args);
        if (printer.busy()) {
            data.revoke(tony.getUUID());
            h.fail("принтер напечатал деньги");
        }
        args.putString("item", "citylife:phone_stark");
        dev.lscity.citylife.stark.Printer.handle(stranger, "printer_print", args);
        if (printer.busy()) {
            data.revoke(tony.getUUID());
            h.fail("принтер печатает без допуска");
        }
        dev.lscity.citylife.stark.Printer.handle(tony, "printer_print", args);
        data.revoke(tony.getUUID());
        if (!printer.busy()) {
            h.fail("принтер не начал печать телефона");
        }
        h.succeedWhen(() -> {
            var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2),
                    e -> e.getItem().is(Registration.DEVICES.get("phone_stark").get()));
            if (drops.isEmpty()) {
                h.fail("телефон Старка не напечатан");
            }
        });
    }

    /** Зал брони: опустевшая витрина получает свою марку обратно. */
    @SelfTest
    public static void starkSuitRestock(TestKit h) {
        BlockPos at = h.absolutePos(new BlockPos(5, 1, 5));
        var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(
                dev.lscity.citylife.stark.StarkSecurity.CASE));
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            h.fail("нет витрины Sym's Armored Industries");
            return;
        }
        h.getLevel().setBlock(at, block.defaultBlockState(), 3);
        var suit = new dev.lscity.citylife.stark.SecurityConsoleBlockEntity.Suit(
                new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), 180F, "33", "case");
        boolean filled = dev.lscity.citylife.stark.StarkSecurity.stock(h.getLevel(), suit);
        var state = h.getLevel().getBlockState(at);
        int mark = -1;
        for (var prop : state.getProperties()) {
            if ("mark_id".equals(prop.getName())) {
                mark = (Integer) state.getValue(prop);
            }
        }
        if (!filled || mark != 33) {
            h.fail("пустая витрина не получила Mark 33 обратно (mark_id=" + mark + ")");
        }
        h.succeed();
    }

    /**
     * Свой Mark 42: Palladium разобрал силу (все способности на месте), а
     * вызов собирает костюм по частям — четыре детали долетают и садятся
     * на игрока; «Протокол „Дом“» снимает их обратно.
     */
    @SelfTest
    public static void mark42Assembles(TestKit h) {
        try {
            var manager = Class.forName("net.threetag.palladium.power.PowerManager")
                    .getMethod("getInstance", net.minecraft.world.level.Level.class).invoke(null, h.getLevel());
            Object power = manager.getClass().getMethod("getPower", net.minecraft.resources.ResourceLocation.class)
                    .invoke(manager, new net.minecraft.resources.ResourceLocation("citylife", "mark42"));
            if (power == null) {
                h.fail("Palladium не загрузил силу citylife:mark42 (ошибка в powers/mark42.json?)");
                return;
            }
            int abilities = ((List<?>) power.getClass().getMethod("getAbilities").invoke(power)).size();
            if (abilities < 30) {
                h.fail("в силе Mark 42 только " + abilities + " способностей — часть не разобралась");
                return;
            }
        } catch (ReflectiveOperationException e) {
            h.fail("Palladium недоступен: " + e);
            return;
        }
        FakePlayer tony = player(h, "TonyMark42");
        tony.getInventory().clearContent();
        tony.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        if (!dev.lscity.citylife.stark.Mark42.assemble(tony, null)) {
            h.fail("вызов Mark 42 не запустил сборку");
            return;
        }
        h.succeedWhen(() -> {
            if (!dev.lscity.citylife.stark.Mark42.wears(tony)) {
                h.fail("детали ещё летят или не сели");
            }
            if (tony.getInventory().countItem(net.minecraft.world.item.Items.IRON_HELMET) != 1) {
                h.fail("свой шлем игрока должен уйти в инвентарь, а не пропасть");
            }
            if (!dev.lscity.citylife.stark.Mark42.home(tony, null) || dev.lscity.citylife.stark.Mark42.wearsAny(tony)) {
                h.fail("«Протокол „Дом“» не снял костюм");
            }
            tony.getInventory().clearContent();
        });
    }

    /** Способности костюмов Sym открыты сразу: Palladium видит наши файлы сил, а не файлы мода. */
    @SelfTest
    public static void symAbilitiesUnlocked(TestKit h) {
        var resources = h.getLevel().getServer().getResourceManager();
        var powers = resources.listResources("palladium/powers",
                id -> id.getNamespace().equals("sym_industries") && id.getPath().endsWith(".json"));
        if (powers.isEmpty()) {
            h.fail("силы Sym's Armored Industries не загружены");
            return;
        }
        List<String> locked = new ArrayList<>();
        for (var entry : powers.entrySet()) {
            String name = entry.getKey().getPath().replace("palladium/powers/", "");
            if (name.equals("mk7.json") || name.equals("mk42.json")) {
                continue;   // марки для подписчиков автора не трогаем
            }
            try (var in = entry.getValue().open()) {
                if (new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .contains("symindustries_iron_man_level")) {
                    locked.add(name + " (" + entry.getValue().sourcePackId() + ")");
                }
            } catch (java.io.IOException e) {
                locked.add(name + ": " + e);
            }
        }
        if (!locked.isEmpty()) {
            h.fail("у костюмов остались способности под замком: " + locked);
            return;
        }
        h.succeed();
    }

    /** Джарвис надевает марку целиком: броня, реактор нужного поколения, кейс у Mark 5. */
    @SelfTest
    public static void jarvisSuitsUp(TestKit h) {
        FakePlayer tony = player(h, "TonyJarvis");
        try {
            var mk3 = dev.lscity.citylife.stark.StarkSuits.mark(3);
            if (!dev.lscity.citylife.stark.StarkSuits.summon(tony, mk3)) {
                h.fail("Mark 3 не наделся");
                return;
            }
            String chest = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                    tony.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()));
            String reactor = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                    dev.lscity.citylife.stark.StarkSuits.curio(tony, "body").getItem()));
            if (!"sym_industries:mark_3_chestplate".equals(chest)) {
                h.fail("на игроке не нагрудник Mark 3, а " + chest);
            }
            if (!"sym_industries:arc_reactor_tier_1".equals(reactor)) {
                h.fail("нет дугового реактора первого поколения в слоте «тело», а " + reactor);
            }
            dev.lscity.citylife.stark.StarkSuits.summon(tony, dev.lscity.citylife.stark.StarkSuits.mark(5));
            String suitcase = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                    dev.lscity.citylife.stark.StarkSuits.curio(tony, "necklace").getItem()));
            if (!"sym_industries:mark_5_suitcase".equals(suitcase)
                    || !tony.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()) {
                h.fail("Mark 5 — это кейс в слоте «ожерелье», а броня Mark 3 должна уйти: " + suitcase);
            }
            dev.lscity.citylife.stark.StarkSuits.summon(tony, dev.lscity.citylife.stark.StarkSuits.mark(33));
            reactor = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                    dev.lscity.citylife.stark.StarkSuits.curio(tony, "body").getItem()));
            if (!"sym_industries:arc_reactor_tier_2".equals(reactor)
                    || !dev.lscity.citylife.stark.StarkSuits.curio(tony, "necklace").isEmpty()) {
                h.fail("Mark 33 нужен реактор второго поколения, а кейс Mark 5 должен уйти: " + reactor);
            }
            dev.lscity.citylife.stark.StarkSuits.off(tony);
            if (!tony.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                    || !dev.lscity.citylife.stark.StarkSuits.curio(tony, "body").isEmpty()) {
                h.fail("«Снять» оставило костюм или реактор");
            }
            if (dev.lscity.citylife.stark.StarkSuits.mark(7).playable()) {
                h.fail("Mark 7 — только для подписчиков мода, Джарвис не должен его предлагать");
            }
            if (!dev.lscity.citylife.stark.StarkSuits.mark(42).playable()
                    || !dev.lscity.citylife.stark.StarkSuits.mark(42).icon().startsWith("citylife:")) {
                h.fail("Mark 42 в списке Джарвиса должен быть свой, citylife");
            }
        } finally {
            tony.getInventory().clearContent();
        }
        h.succeed();
    }

    /** Лазерный датчик: луч до первого твёрдого блока; подвальные этажи лифта — «-1». */
    @SelfTest
    public static void starkLaserAndBasement(TestKit h) {
        h.setBlock(new BlockPos(2, 1, 8), Registration.LASER.get().defaultBlockState()
                .setValue(dev.lscity.citylife.stark.LaserBlock.FACING, net.minecraft.core.Direction.EAST));
        h.setBlock(new BlockPos(7, 1, 8), Blocks.STONE);
        var laser = (dev.lscity.citylife.stark.LaserBlockEntity) h.getBlockEntity(new BlockPos(2, 1, 8));
        if (laser.length() != 4) {
            h.fail("длина луча " + laser.length() + ", нужно 4");
        }
        var beam = laser.beam();
        BlockPos mid = h.absolutePos(new BlockPos(5, 1, 8));
        if (beam == null || !beam.contains(mid.getCenter())) {
            h.fail("луч не проходит через клетку между датчиком и стеной");
        }
        java.util.List<BlockPos> floors = java.util.List.of(new BlockPos(0, 46, 0), new BlockPos(0, 62, 0),
                new BlockPos(0, 70, 0), new BlockPos(0, 74, 0));
        String labels = floors.stream().map(p -> dev.lscity.citylife.building.Elevator.label(floors, p))
                .collect(java.util.stream.Collectors.joining(","));
        if (!labels.equals("-2,-1,1,2")) {
            h.fail("подписи этажей " + labels + ", нужно -2,-1,1,2");
        }
        h.succeed();
    }

    // --- розыск, дроны, прохожие, башня STARK ---------------------------------------

    /** Розыск: откуп снимает звёзды за деньги, сразу после нарушения — нельзя; админ снимает командой. */
    @SelfTest
    public static void wantedPayAndClear(TestKit h) {
        FakePlayer bandit = player(h, "Bandit");
        LifeData life = LifeData.get(h.getLevel().getServer());
        CityData bank = CityData.get(h.getLevel().getServer());
        try {
            Wanted.clear(h.getLevel().getServer(), bandit.getUUID());
            bank.setBalance(bandit.getUUID(), 100_000);
            Wanted.crime(bandit, 2, "citylife.wanted.theft");
            if (Wanted.pay(bandit) != Wanted.Pay.TOO_SOON) {
                h.fail("откупился сразу после нарушения");
            }
            Wanted.clear(h.getLevel().getServer(), bandit.getUUID());
            life.setWanted(bandit.getUUID(), 2, h.getLevel().getGameTime() + 6000);
            long before = bank.balance(bandit.getUUID());
            if (Wanted.pay(bandit) != Wanted.Pay.OK || life.wanted(bandit.getUUID()) != 0) {
                h.fail("откуп не снял розыск");
            }
            if (before - bank.balance(bandit.getUUID()) != Wanted.bail(2)) {
                h.fail("за откуп списано " + (before - bank.balance(bandit.getUUID())) + ", нужно " + Wanted.bail(2));
            }
            life.setWanted(bandit.getUUID(), 3, h.getLevel().getGameTime() + 6000);
            life.setJail(bandit.getUUID(), h.getLevel().getGameTime() + 6000);
            Wanted.clear(h.getLevel().getServer(), bandit.getUUID());
            if (life.wanted(bandit.getUUID()) != 0 || life.jailUntil(bandit.getUUID()) > 0) {
                h.fail("команда не сняла розыск и камеру");
            }
        } finally {
            Wanted.clear(h.getLevel().getServer(), bandit.getUUID());
        }
        h.succeed();
    }

    /** Заказ с маркетплейса привозит курьерский дрон: спускается к игроку и сбрасывает посылку. */
    @SelfTest(timeout = 900)
    public static void courierDelivers(TestKit h) {
        FakePlayer buyer = player(h, "DroneBuyer");
        buyer.getInventory().clearContent();
        var data = dev.lscity.citylife.data.CityData.get(h.getLevel().getServer());
        var offer = dev.lscity.citylife.market.Market.BY_ID.values().iterator().next();
        data.deposit(buyer.getUUID(), 10000);
        long now = h.getLevel().getGameTime();
        data.placeOrder(buyer.getUUID(), offer.id(), "тест", 1, now - 1);
        var order = data.orders(buyer.getUUID()).stream().filter(o -> o.ready(now)).reduce((a, b) -> b).orElse(null);
        if (order == null) {
            h.fail("заказ не появился");
            return;
        }
        long before = data.balance(buyer.getUUID());
        dev.lscity.citylife.drone.Courier.deliver(buyer, data, order.id());
        if (data.balance(buyer.getUUID()) != before - dev.lscity.citylife.drone.Courier.FEE) {
            h.fail("за доставку дроном не списано " + dev.lscity.citylife.drone.Courier.FEE + " ₽"
                    + " (небо над площадкой закрыто?)");
            return;
        }
        net.minecraft.world.item.Item goods = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation(offer.item()));
        h.succeedWhen(() -> {
            var near = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    buyer.getBoundingBox().inflate(2), e -> e.getItem().getItem() == goods);
            if (near.isEmpty() && buyer.getInventory().countItem(goods) == 0) {
                h.fail("посылка ещё не долетела");
            }
            near.forEach(Entity::discard);
            buyer.getInventory().clearContent();
        });
    }

    /**
     * Свои дроны: встают в мир (правило «без мобов» их не трогает), пилот
     * двигает дрон, но прыжок дальше возможного сервер не принимает, батарея
     * садится и роняет дрон, хозяин подбирает дрон вместе с зарядом.
     * Зомби в город по-прежнему не пускают.
     */
    @SelfTest
    public static void dronesFly(TestKit h) {
        BlockPos at = h.absolutePos(new BlockPos(6, 4, 6));
        FakePlayer pilot = player(h, "DronePilot");
        pilot.getInventory().clearContent();
        var drone = dev.lscity.citylife.drone.Drones.spawn(h.getLevel(), pilot,
                dev.lscity.citylife.drone.DroneType.CAMERA,
                new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), 0, 1.0F);
        if (drone == null || drone.isRemoved() || !drone.isAddedToWorld()) {
            h.fail("дрон не встал в мир: правило «без мобов» его убрало");
            return;
        }
        dev.lscity.citylife.drone.Drones.start(pilot, drone);
        double x0 = drone.getX();
        dev.lscity.citylife.drone.Drones.move(pilot, new dev.lscity.citylife.drone.DroneMovePacket(drone.getId(),
                x0 + 0.4, drone.getY() + 0.3, drone.getZ(), 0, 0, 0));
        if (Math.abs(drone.getX() - (x0 + 0.4)) > 1.0E-3) {
            h.fail("сервер не принял честный шаг пилота");
        }
        dev.lscity.citylife.drone.Drones.move(pilot, new dev.lscity.citylife.drone.DroneMovePacket(drone.getId(),
                x0 + 60, drone.getY(), drone.getZ(), 0, 0, 0));
        if (drone.getX() > x0 + 5) {
            h.fail("сервер принял прыжок дрона на 60 блоков за тик");
        }
        dev.lscity.citylife.drone.Drones.stop(drone, null);
        if (dev.lscity.citylife.drone.Drones.session(pilot) != null) {
            h.fail("после отключения пилот всё ещё на связи");
        }
        drone.setBattery(0.3F);
        drone.pickUp(pilot);
        var stack = pilot.getInventory().items.stream()
                .filter(s -> s.getItem() instanceof dev.lscity.citylife.drone.DroneItem).findFirst().orElse(null);
        if (stack == null || Math.abs(dev.lscity.citylife.drone.DroneItem.battery(stack) - 0.3F) > 0.01F
                || !drone.isRemoved()) {
            h.fail("хозяин не подобрал дрон с его зарядом");
        }
        pilot.getInventory().clearContent();
        // Батарея кончилась в воздухе — моторы встают, дрон падает.
        var falling = dev.lscity.citylife.drone.Drones.spawn(h.getLevel(), pilot,
                dev.lscity.citylife.drone.DroneType.RACER,
                new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5), 0, 0.0005F);
        falling.setHoming(true);
        double y0 = falling.getY();
        Entity zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        boolean zombieAdded = h.getLevel().addFreshEntity(zombie) && !zombie.isRemoved();
        zombie.discard();
        if (zombieAdded) {
            h.fail("зомби появился в городе");
        }
        h.succeedWhen(() -> {
            if (falling.battery() > 0 || falling.motors() || falling.getY() > y0 - 1) {
                h.fail("дрон с пустой батареей не упал");
            }
            falling.discard();
        });
    }

    /** Застрявший прохожий: не сдвинулся за проверку — замечен и выручается; пошёл — счётчик сброшен. */
    @SelfTest(timeout = 200)
    public static void walkerUnsticks(TestKit h) {
        BlockPos at = h.absolutePos(new BlockPos(4, 1, 8));
        // Внешность фиксированная: патрульный ходит своим маршрутом (см. walkerWalks).
        Entity walker = dev.lscity.citylife.city.Pedestrians.spawnWalker(h.getLevel(), at, h.getLevel().getRandom(),
                new dev.lscity.citylife.city.Citizens.Look("man_2", "Артём", false));
        if (walker == null) {
            h.fail("прохожий не появился");
            return;
        }
        dev.lscity.citylife.city.Pedestrians.sendTo(walker, h.absolutePos(new BlockPos(13, 1, 8)));
        h.succeedWhen(() -> {
            // Путь строится на тике после появления — ждём его.
            if (!dev.lscity.citylife.city.Pedestrians.walking(walker)) {
                h.fail("нет пути: " + dev.lscity.citylife.city.Pedestrians.debug(walker));
            }
            try {
                // Первая проверка запоминает место, вторая — видит, что он стоит (в том же тике).
                dev.lscity.citylife.city.Pedestrians.checkStuck(walker);
                dev.lscity.citylife.city.Pedestrians.checkStuck(walker);
                int fails = dev.lscity.citylife.city.Pedestrians.fails(walker);
                if (fails < 1) {
                    throw new IllegalStateException("стоящий на месте прохожий не замечен; "
                            + dev.lscity.citylife.city.Pedestrians.debug(walker));
                }
                walker.moveTo(walker.getX() + 2, walker.getY(), walker.getZ());
                dev.lscity.citylife.city.Pedestrians.checkStuck(walker);
                walker.moveTo(walker.getX() + 2, walker.getY(), walker.getZ());
                dev.lscity.citylife.city.Pedestrians.checkStuck(walker);
                if (dev.lscity.citylife.city.Pedestrians.fails(walker) != 0) {
                    throw new IllegalStateException("прохожий идёт, а считается застрявшим");
                }
            } finally {
                walker.discard();
            }
        });
    }

    /** Башня STARK в агентстве: 10 000 000 ₽, у владельца — допуск охраны и костюмы. */
    @SelfTest
    public static void starkTowerOwner(TestKit h) {
        var unit = Estate.get(dev.lscity.citylife.stark.StarkSecurity.TOWER_UNIT);
        if (unit == null || unit.price() != 10_000_000L || !unit.landmark()) {
            h.fail("башни STARK нет в агентстве или цена не 10 000 000: " + unit);
            return;
        }
        FakePlayer tony = player(h, "TonyOwner");
        LifeData life = LifeData.get(h.getLevel().getServer());
        var previous = life.owner(unit.id());
        try {
            if (dev.lscity.citylife.stark.StarkSecurity.cleared(tony)) {
                h.fail("допуск без покупки башни");
            }
            life.setOwner(unit.id(), tony.getUUID(), "TonyOwner", h.getLevel().getGameTime());
            if (!dev.lscity.citylife.stark.StarkSecurity.cleared(tony)) {
                h.fail("у владельца башни нет допуска охраны");
            }
            var sym = "sym_industries";
            if (dev.lscity.citylife.stark.StarkSuits.markOf(new net.minecraft.resources.ResourceLocation(
                    sym, "mark_33_helmet")) != 33
                    || dev.lscity.citylife.stark.StarkSuits.markOf(new net.minecraft.resources.ResourceLocation(
                    sym, "mark_5_suitcase")) != 5
                    || dev.lscity.citylife.stark.StarkSuits.isSuit(new net.minecraft.resources.ResourceLocation(
                    sym, "titanium_ingot"))) {
                h.fail("Джарвис путает костюмы и прочие предметы");
            }
        } finally {
            if (previous == null) {
                life.clearOwner(unit.id());
            } else {
                life.setOwner(unit.id(), previous.id(), previous.name(), previous.since());
            }
        }
        h.succeed();
    }
}
