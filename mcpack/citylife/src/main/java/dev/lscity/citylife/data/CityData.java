package dev.lscity.citylife.data;

import dev.lscity.citylife.CityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Городские данные сервера.
 *
 * Банковский счёт принадлежит игроку (его нику), а не телефону: телефон можно
 * потерять или продать, деньги остаются. Сообщения, наоборот, принадлежат
 * номеру SIM-карты — кто держит карту, тот и читает переписку.
 *
 * Хранится в data/citylife_city.dat мира, поэтому переживает перезапуск.
 */
public class CityData extends SavedData {
    private static final String NAME = "citylife_city";

    private final Map<UUID, Long> balances = new LinkedHashMap<>();
    private final Map<Integer, UUID> numbers = new LinkedHashMap<>();
    private final Map<Integer, List<Message>> sms = new LinkedHashMap<>();
    private final Map<UUID, List<Waypoint>> waypoints = new LinkedHashMap<>();
    private final Map<UUID, List<BlockPos>> pairedLocks = new LinkedHashMap<>();
    private final Map<UUID, List<BlockPos>> pairedCameras = new LinkedHashMap<>();
    private final Map<UUID, List<Mail>> mail = new LinkedHashMap<>();
    private final List<Ad> ads = new ArrayList<>();
    /** Выписка по счёту: последние операции с суммой и назначением. */
    private final Map<UUID, List<Statement>> statements = new LinkedHashMap<>();
    public static final int MAX_STATEMENT = 60;

    /** Строка выписки: когда (игровое время), сколько (+/-) и за что. */
    public record Statement(long time, long amount, String text) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("time", time);
            tag.putLong("amount", amount);
            tag.putString("text", text);
            return tag;
        }

        public static Statement load(CompoundTag tag) {
            return new Statement(tag.getLong("time"), tag.getLong("amount"), tag.getString("text"));
        }
    }
    private int nextAd = 1;
    private final Map<UUID, Waypoint> routes = new LinkedHashMap<>();
    private final Map<UUID, List<Order>> orders = new LinkedHashMap<>();
    private int nextOrder = 1;

    public static CityData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("City Life: нет мира overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(CityData::load, CityData::new, NAME);
    }

    // --- банк ---------------------------------------------------------------

    public long balance(UUID player) {
        Long value = balances.get(player);
        if (value == null) {
            value = CityConfig.CONFIG.startingBalance.get();
            balances.put(player, value);
            setDirty();
        }
        return value;
    }

    public void setBalance(UUID player, long amount) {
        balances.put(player, Math.max(0L, amount));
        setDirty();
    }

    public void deposit(UUID player, long amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(player, balance(player) + amount);
    }

    /** Списать со счёта. Возвращает false, если денег не хватило. */
    public boolean withdraw(UUID player, long amount) {
        if (amount <= 0) {
            return false;
        }
        long current = balance(player);
        if (current < amount) {
            return false;
        }
        setBalance(player, current - amount);
        return true;
    }

    /** Положить на счёт и записать в выписку. */
    public void deposit(UUID player, long amount, String reason, long time) {
        if (amount <= 0) {
            return;
        }
        deposit(player, amount);
        record(player, amount, reason, time);
    }

    /** Списать со счёта с записью в выписку. */
    public boolean withdraw(UUID player, long amount, String reason, long time) {
        if (!withdraw(player, amount)) {
            return false;
        }
        record(player, -amount, reason, time);
        return true;
    }

    /** Запись в выписку без движения денег (когда деньги уже переведены). */
    public void record(UUID player, long amount, String reason, long time) {
        List<Statement> list = statements.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(0, new Statement(time, amount, reason));
        while (list.size() > MAX_STATEMENT) {
            list.remove(list.size() - 1);
        }
        setDirty();
    }

    public List<Statement> statement(UUID player) {
        return statements.getOrDefault(player, List.of());
    }

    public boolean transfer(UUID from, UUID to, long amount) {
        if (from.equals(to) || amount <= 0 || !withdraw(from, amount)) {
            return false;
        }
        deposit(to, amount);
        return true;
    }

    // --- номера и SMS -------------------------------------------------------

    /** Новый свободный номер из четырёх цифр. */
    public int issueNumber(UUID owner) {
        Random random = new Random();
        for (int attempt = 0; attempt < 20000; attempt++) {
            int number = 1000 + random.nextInt(9000);
            if (!numbers.containsKey(number)) {
                numbers.put(number, owner);
                setDirty();
                return number;
            }
        }
        throw new IllegalStateException("City Life: свободных номеров не осталось");
    }

    public boolean numberExists(int number) {
        return numbers.containsKey(number);
    }

    public List<Message> sms(int number) {
        return sms.computeIfAbsent(number, key -> new ArrayList<>());
    }

    /** Положить сообщение и получателю, и отправителю: переписка видна с обеих сторон. */
    public void deliver(Message message) {
        int limit = CityConfig.CONFIG.maxMessages.get();
        for (int box : new int[]{message.toNumber(), message.fromNumber()}) {
            List<Message> list = sms(box);
            list.add(0, message);
            while (list.size() > limit) {
                list.remove(list.size() - 1);
            }
        }
        setDirty();
    }

    // --- заказы маркетплейса -------------------------------------------------

    public List<Order> orders(UUID player) {
        return orders.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public Order placeOrder(UUID player, String offer, String title, long price, long readyAt) {
        Order order = new Order(nextOrder++, offer, title, price, readyAt, false);
        List<Order> list = orders(player);
        list.add(0, order);
        // Историю держим короткой: полученные старше двадцати заказов забываем.
        while (list.size() > 30 && list.get(list.size() - 1).taken()) {
            list.remove(list.size() - 1);
        }
        setDirty();
        return order;
    }

    public void markTaken(UUID player, int id) {
        List<Order> list = orders(player);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == id) {
                list.set(i, list.get(i).take());
                setDirty();
                return;
            }
        }
    }

    // --- метки и маршрут ----------------------------------------------------

    public Waypoint route(UUID player) {
        return routes.get(player);
    }

    public Map<UUID, Waypoint> routes() {
        return routes;
    }

    public void setRoute(UUID player, Waypoint point) {
        if (point == null) {
            routes.remove(player);
        } else {
            routes.put(player, point);
        }
        setDirty();
    }

    public List<Waypoint> waypoints(UUID player) {
        return waypoints.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public boolean addWaypoint(UUID player, Waypoint point) {
        List<Waypoint> list = waypoints(player);
        if (list.size() >= CityConfig.CONFIG.maxWaypoints.get()) {
            return false;
        }
        list.add(point);
        setDirty();
        return true;
    }

    public boolean removeWaypoint(UUID player, int index) {
        List<Waypoint> list = waypoints(player);
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        setDirty();
        return true;
    }

    // --- привязка замков к телефону ----------------------------------------

    public List<BlockPos> locks(UUID player) {
        return pairedLocks.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public boolean pairLock(UUID player, BlockPos pos) {
        List<BlockPos> list = locks(player);
        BlockPos immutable = pos.immutable();
        if (list.contains(immutable)) {
            return false;
        }
        list.add(immutable);
        setDirty();
        return true;
    }

    public void unpairLock(UUID player, BlockPos pos) {
        if (locks(player).remove(pos.immutable())) {
            setDirty();
        }
    }

    // --- камеры видеонаблюдения в приложении ---------------------------------

    /** Сколько камер можно держать в приложении — как у монитора SecurityCraft. */
    public static final int MAX_CAMERAS = 30;

    public List<BlockPos> cameras(UUID player) {
        return pairedCameras.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public boolean pairCamera(UUID player, BlockPos pos) {
        List<BlockPos> list = cameras(player);
        BlockPos immutable = pos.immutable();
        if (list.contains(immutable) || list.size() >= MAX_CAMERAS) {
            return false;
        }
        list.add(immutable);
        setDirty();
        return true;
    }

    public void unpairCamera(UUID player, BlockPos pos) {
        if (cameras(player).remove(pos.immutable())) {
            setDirty();
        }
    }

    // --- почта и объявления ------------------------------------------------------

    public static final int MAX_MAIL = 40;
    public static final int MAX_ADS = 40;

    public List<Mail> mail(UUID player) {
        return mail.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public void deliverMail(UUID to, Mail letter) {
        List<Mail> box = mail(to);
        box.add(0, letter);
        while (box.size() > MAX_MAIL) {
            box.remove(box.size() - 1);
        }
        setDirty();
    }

    public void readAllMail(UUID player) {
        List<Mail> box = mail(player);
        box.replaceAll(Mail::markRead);
        setDirty();
    }

    public List<Ad> ads() {
        return ads;
    }

    public void postAd(UUID author, String name, String text, long time) {
        ads.add(0, new Ad(nextAd++, author, name, text, time));
        while (ads.size() > MAX_ADS) {
            ads.remove(ads.size() - 1);
        }
        setDirty();
    }

    public boolean removeAd(UUID author, int id, boolean admin) {
        boolean removed = ads.removeIf(ad -> ad.id() == id && (admin || ad.author().equals(author)));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // --- сохранение ---------------------------------------------------------

    public static CityData load(CompoundTag tag) {
        CityData data = new CityData();
        for (CompoundTag entry : compounds(tag, "accounts")) {
            data.balances.put(entry.getUUID("id"), entry.getLong("balance"));
        }
        for (CompoundTag entry : compounds(tag, "numbers")) {
            data.numbers.put(entry.getInt("number"), entry.getUUID("owner"));
        }
        for (CompoundTag entry : compounds(tag, "sms")) {
            List<Message> list = new ArrayList<>();
            for (CompoundTag item : compounds(entry, "items")) {
                list.add(Message.load(item));
            }
            data.sms.put(entry.getInt("number"), list);
        }
        for (CompoundTag entry : compounds(tag, "orders")) {
            List<Order> list = new ArrayList<>();
            for (CompoundTag item : compounds(entry, "items")) {
                list.add(Order.load(item));
            }
            data.orders.put(entry.getUUID("id"), list);
        }
        data.nextOrder = Math.max(1, tag.getInt("nextOrder"));
        for (CompoundTag entry : compounds(tag, "routes")) {
            data.routes.put(entry.getUUID("id"), Waypoint.load(entry.getCompound("point")));
        }
        for (CompoundTag entry : compounds(tag, "waypoints")) {
            List<Waypoint> list = new ArrayList<>();
            for (CompoundTag item : compounds(entry, "items")) {
                list.add(Waypoint.load(item));
            }
            data.waypoints.put(entry.getUUID("id"), list);
        }
        for (CompoundTag entry : compounds(tag, "locks")) {
            List<BlockPos> list = new ArrayList<>();
            for (long packed : entry.getLongArray("positions")) {
                list.add(BlockPos.of(packed));
            }
            data.pairedLocks.put(entry.getUUID("id"), list);
        }
        for (CompoundTag entry : compounds(tag, "cameras")) {
            List<BlockPos> list = new ArrayList<>();
            for (long packed : entry.getLongArray("positions")) {
                list.add(BlockPos.of(packed));
            }
            data.pairedCameras.put(entry.getUUID("id"), list);
        }
        for (CompoundTag entry : compounds(tag, "mail")) {
            List<Mail> list = new ArrayList<>();
            for (CompoundTag item : compounds(entry, "items")) {
                list.add(Mail.load(item));
            }
            data.mail.put(entry.getUUID("id"), list);
        }
        for (CompoundTag entry : compounds(tag, "ads")) {
            data.ads.add(Ad.load(entry));
        }
        data.nextAd = Math.max(1, tag.getInt("nextAd"));
        for (CompoundTag entry : compounds(tag, "statements")) {
            List<Statement> list = new ArrayList<>();
            for (Tag item : entry.getList("items", Tag.TAG_COMPOUND)) {
                list.add(Statement.load((CompoundTag) item));
            }
            data.statements.put(entry.getUUID("id"), list);
        }
        return data;
    }

    private static List<CompoundTag> compounds(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getCompound(i));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag accounts = new ListTag();
        balances.forEach((id, balance) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putLong("balance", balance);
            accounts.add(entry);
        });
        tag.put("accounts", accounts);

        ListTag issued = new ListTag();
        numbers.forEach((number, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("number", number);
            entry.putUUID("owner", owner);
            issued.add(entry);
        });
        tag.put("numbers", issued);

        ListTag boxes = new ListTag();
        sms.forEach((number, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("number", number);
            ListTag items = new ListTag();
            list.forEach(message -> items.add(message.save()));
            entry.put("items", items);
            boxes.add(entry);
        });
        tag.put("sms", boxes);

        ListTag bought = new ListTag();
        orders.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ListTag items = new ListTag();
            list.forEach(order -> items.add(order.save()));
            entry.put("items", items);
            bought.add(entry);
        });
        tag.put("orders", bought);
        tag.putInt("nextOrder", nextOrder);

        ListTag marks = new ListTag();
        waypoints.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ListTag items = new ListTag();
            list.forEach(point -> items.add(point.save()));
            entry.put("items", items);
            marks.add(entry);
        });
        tag.put("waypoints", marks);

        ListTag active = new ListTag();
        routes.forEach((id, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.put("point", point.save());
            active.add(entry);
        });
        tag.put("routes", active);

        ListTag pairs = new ListTag();
        pairedLocks.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            long[] packed = new long[list.size()];
            for (int i = 0; i < list.size(); i++) {
                packed[i] = list.get(i).asLong();
            }
            entry.putLongArray("positions", packed);
            pairs.add(entry);
        });
        tag.put("locks", pairs);
        tag.put("cameras", positions(pairedCameras));
        ListTag letters = new ListTag();
        mail.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ListTag items = new ListTag();
            list.forEach(letter -> items.add(letter.save()));
            entry.put("items", items);
            letters.add(entry);
        });
        tag.put("mail", letters);
        ListTag board = new ListTag();
        ads.forEach(ad -> board.add(ad.save()));
        tag.put("ads", board);
        ListTag books = new ListTag();
        statements.forEach((id, list) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ListTag items = new ListTag();
            list.forEach(line -> items.add(line.save()));
            entry.put("items", items);
            books.add(entry);
        });
        tag.put("statements", books);
        tag.putInt("nextAd", nextAd);
        return tag;
    }

    private static ListTag positions(Map<UUID, List<BlockPos>> map) {
        ListTag out = new ListTag();
        map.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putLongArray("positions", list.stream().mapToLong(BlockPos::asLong).toArray());
            out.add(entry);
        });
        return out;
    }
}
