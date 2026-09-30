package dev.lscity.citylife.net;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.Registration;
import dev.lscity.citylife.block.SmartLockBlock;
import dev.lscity.citylife.block.SmartLockBlockEntity;
import dev.lscity.citylife.data.Ad;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.CityLandmarks;
import dev.lscity.citylife.data.Mail;
import dev.lscity.citylife.data.Message;
import dev.lscity.citylife.data.Order;
import dev.lscity.citylife.data.Waypoint;
import dev.lscity.citylife.device.DeviceModel;
import dev.lscity.citylife.device.DeviceState;
import dev.lscity.citylife.device.Devices;
import dev.lscity.citylife.economy.Money;
import dev.lscity.citylife.item.DeviceItem;
import dev.lscity.citylife.item.SimCardItem;
import dev.lscity.citylife.market.Market;
import dev.lscity.citylife.pc.PcBuild;
import dev.lscity.citylife.pc.PcCaseBlockEntity;
import dev.lscity.citylife.pc.PcPart;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Серверная часть всех гаджетов: телефонов, планшета, ноутбука и компьютера.
 *
 * Клиент только рисует и просит, решения принимает сервер: какое устройство
 * в руках, есть ли в нём сеть, хватает ли денег. Каждое действие приходит с
 * «контекстом» — рука, в которой гаджет, или позиция системного блока.
 */
public final class DeviceServer {

    /** Действия, которым нужен интернет (SIM или провод). */
    private static final List<String> NEEDS_NETWORK = List.of("msg", "pay", "wp_add", "nav_set",
            "lock_toggle", "lock_grant", "lock_pin", "app_install", "market_order", "cam_view",
            "mail_send", "ad_post");

    /** Сколько стоит объявление на городской доске. */
    private static final long AD_PRICE = 100;

    private DeviceServer() {
    }

    /** Устройство, с которым сейчас работает игрок. */
    public record Device(DeviceModel model, CompoundTag state, CompoundTag ctx,
                         PcCaseBlockEntity computer) {

        public int sim() {
            return DeviceState.sim(state);
        }

        public boolean online() {
            return model.kind().wired() || sim() != 0;
        }

        void changed() {
            if (computer != null) {
                computer.deviceChanged();
            }
        }
    }

    // --- контекст -----------------------------------------------------------

    public static CompoundTag handContext(InteractionHand hand) {
        CompoundTag ctx = new CompoundTag();
        ctx.putString("hand", hand == InteractionHand.OFF_HAND ? "off" : "main");
        return ctx;
    }

    public static CompoundTag computerContext(BlockPos pos) {
        CompoundTag ctx = new CompoundTag();
        ctx.putLong("pc", pos.asLong());
        return ctx;
    }

    public static CompoundTag laptopContext(BlockPos pos) {
        CompoundTag ctx = new CompoundTag();
        ctx.putLong("laptop", pos.asLong());
        return ctx;
    }

    public static Device resolve(ServerPlayer player, CompoundTag ctx) {
        if (ctx.contains("laptop")) {
            BlockPos pos = BlockPos.of(ctx.getLong("laptop"));
            if (pos.distToCenterSqr(player.position()) > 100
                    || !(player.level().getBlockEntity(pos)
                    instanceof dev.lscity.citylife.pc.LaptopBlockEntity laptop)
                    || !(laptop.stack().getItem() instanceof DeviceItem item)
                    || !player.level().getBlockState(pos)
                    .getValue(dev.lscity.citylife.pc.LaptopBlock.OPEN)) {
                return null;
            }
            return new Device(item.model(), laptop.device(), ctx, null);
        }
        if (ctx.contains("pc")) {
            BlockPos pos = BlockPos.of(ctx.getLong("pc"));
            if (pos.distToCenterSqr(player.position()) > 100
                    || !(player.level().getBlockEntity(pos) instanceof PcCaseBlockEntity pc)
                    || !pc.build().works()) {
                return null;
            }
            return new Device(Devices.COMPUTER, pc.device(), ctx, pc);
        }
        InteractionHand hand = "off".equals(ctx.getString("hand"))
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof DeviceItem item)) {
            return null;
        }
        return new Device(item.model(), stack.getOrCreateTag(), ctx, null);
    }

    // --- снимок -------------------------------------------------------------

    public static CompoundTag snapshot(ServerPlayer player, Device device) {
        CityData data = CityData.get(player.server);
        UUID id = player.getUUID();
        CompoundTag tag = new CompoundTag();
        tag.put("ctx", device.ctx());
        tag.putString("model", device.model().id());
        tag.putString("kind", device.model().kind().name());
        tag.putInt("sim", device.sim());
        tag.putBoolean("simSlot", device.model().simSlot());
        tag.putBoolean("online", device.online());
        tag.putLong("balance", data.balance(id));
        tag.putLong("cash", Money.cash(player));
        tag.putString("owner", player.getGameProfile().getName());
        tag.putLong("daytime", player.level().getDayTime() % 24000L);
        tag.putLong("gametime", player.level().getGameTime());
        tag.putString("wallpaper", DeviceState.wallpaper(device.state()));
        tag.putString("notes", DeviceState.notes(device.state()));
        tag.putInt("lockRange", CityConfig.CONFIG.phoneLockRange.get());
        tag.putInt("maxWaypoints", CityConfig.CONFIG.maxWaypoints.get());

        ListTag apps = new ListTag();
        DeviceState.apps(device.model(), device.state()).forEach(a -> apps.add(StringTag.valueOf(a)));
        tag.put("apps", apps);
        ListTag store = new ListTag();
        List<String> installed = DeviceState.installed(device.state());
        for (String app : Devices.STORE_APPS) {
            if (device.model().has(app)) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("app", app);
            entry.putBoolean("installed", installed.contains(app));
            store.add(entry);
        }
        tag.put("store", store);
        tag.putInt("storeFree", Math.max(0, device.model().storeSlots() - installed.size()));

        // Переписка этой SIM-карты: и входящие, и отправленные.
        ListTag messages = new ListTag();
        if (device.sim() != 0) {
            for (Message message : data.sms(device.sim())) {
                messages.add(message.save());
            }
        }
        tag.put("messages", messages);

        ListTag contacts = new ListTag();
        ListTag payees = new ListTag();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(id)) {
                continue;
            }
            CompoundTag payee = new CompoundTag();
            payee.putString("name", other.getGameProfile().getName());
            payee.putUUID("id", other.getUUID());
            payees.add(payee);
            int number = primaryNumber(other);
            if (number == 0) {
                continue;
            }
            CompoundTag entry = payee.copy();
            entry.putInt("number", number);
            entry.putInt("dist", (int) Math.sqrt(other.distanceToSqr(player)));
            contacts.add(entry);
        }
        tag.put("contacts", contacts);
        tag.put("payees", payees);

        // Метки: ближайшие сверху — на экране их помещается немного.
        List<CompoundTag> rows = new ArrayList<>();
        for (Waypoint point : CityLandmarks.ALL) {
            rows.add(withDistance(point, player));
        }
        // Своим меткам — номер в личном списке: по нему их удаляют.
        List<Waypoint> own = data.waypoints(id);
        for (int i = 0; i < own.size(); i++) {
            CompoundTag row = withDistance(own.get(i), player);
            row.putInt("index", i);
            rows.add(row);
        }
        rows.sort(Comparator.comparingInt(row -> row.getInt("dist")));
        ListTag waypoints = new ListTag();
        rows.forEach(waypoints::add);
        tag.put("waypoints", waypoints);
        Waypoint route = data.route(id);
        if (route != null) {
            tag.put("route", withDistance(route, player));
        }

        tag.put("locks", locks(player, data));
        tag.put("cameras", cameras(player, data));

        long now = player.level().getGameTime();
        ListTag inbox = new ListTag();
        int unread = 0;
        for (Mail letter : data.mail(id)) {
            CompoundTag entry = letter.save();
            entry.remove("from");
            entry.putLong("ago", Math.max(0, (now - letter.time()) / 20));
            inbox.add(entry);
            unread += letter.read() ? 0 : 1;
        }
        tag.put("mail", inbox);
        tag.putInt("unread", unread);

        // Работа и своё жильё.
        tag.put("jobs", dev.lscity.citylife.jobs.Jobs.snapshot(player));
        ListTag homes = new ListTag();
        var life = dev.lscity.citylife.data.LifeData.get(player.server);
        for (var unit : dev.lscity.citylife.estate.Estate.all()) {
            var owner = life.owner(unit.id());
            boolean mine = owner != null && owner.id().equals(id);
            if (!mine && (owner == null || !life.trusted(unit.id()).contains(id))) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("id", unit.id());
            entry.putString("title", unit.title());
            entry.putString("address", unit.address());
            entry.putString("rooms", unit.rooms());
            entry.putBoolean("mine", mine);
            entry.putString("owner", owner.name());
            ListTag keys = new ListTag();
            for (UUID key : life.trusted(unit.id())) {
                keys.add(net.minecraft.nbt.StringTag.valueOf(life.trustedName(key)));
            }
            entry.put("keys", keys);
            entry.putInt("distance", (int) Math.sqrt(unit.door().distToCenterSqr(player.position())));
            homes.add(entry);
        }
        tag.put("homes", homes);

        ListTag board = new ListTag();
        for (Ad ad : data.ads()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", ad.id());
            entry.putString("author", ad.authorName());
            entry.putString("text", ad.text());
            entry.putLong("ago", Math.max(0, (now - ad.time()) / 20));
            entry.putBoolean("mine", ad.author().equals(id));
            board.add(entry);
        }
        tag.put("ads", board);
        tag.putLong("adPrice", AD_PRICE);

        CompoundTag news = new CompoundTag();
        news.putLong("day", player.level().getDayTime() / 24000L + 1);
        news.putBoolean("rain", player.level().isRaining());
        news.putBoolean("thunder", player.level().isThundering());
        ListTag online = new ListTag();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            online.add(StringTag.valueOf(other.getGameProfile().getName()));
        }
        news.put("online", online);
        tag.put("news", news);

        ListTag orders = new ListTag();
        now = player.level().getGameTime();
        for (Order order : data.orders(id)) {
            CompoundTag entry = order.save();
            entry.putString("status", order.taken() ? "taken" : order.ready(now) ? "ready" : "wait");
            entry.putLong("seconds", Math.max(0, (order.readyAt() - now) / 20));
            orders.add(entry);
        }
        tag.put("orders", orders);

        if (device.computer() != null) {
            PcBuild.Result build = device.computer().build();
            CompoundTag info = new CompoundTag();
            info.putInt("score", build.score());
            info.putInt("draw", build.draw());
            info.putInt("supply", build.supply());
            ListTag parts = new ListTag();
            for (ItemStack stack : device.computer().slots()) {
                PcPart part = PcBuild.part(stack);
                if (part != null) {
                    parts.add(StringTag.valueOf(stack.getHoverName().getString()));
                }
            }
            info.put("parts", parts);
            tag.put("sysinfo", info);
        }
        return tag;
    }

    /** Номер SIM, по которому игрока найдут: сначала гаджет в руке, потом любой в сумке. */
    public static int primaryNumber(ServerPlayer player) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.getItem() instanceof DeviceItem && DeviceState.sim(stack.getTag()) != 0) {
                return DeviceState.sim(stack.getTag());
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DeviceItem && DeviceState.sim(stack.getTag()) != 0) {
                return DeviceState.sim(stack.getTag());
            }
        }
        return 0;
    }

    /** Есть ли у игрока гаджет с этой SIM — тогда SMS дойдёт до него сразу. */
    private static boolean carries(ServerPlayer player, int number) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DeviceItem && DeviceState.sim(stack.getTag()) == number) {
                return true;
            }
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof DeviceItem && DeviceState.sim(off.getTag()) == number;
    }

    private static CompoundTag withDistance(Waypoint point, ServerPlayer player) {
        CompoundTag entry = point.save();
        double dx = point.x() + 0.5 - player.getX();
        double dz = point.z() + 0.5 - player.getZ();
        entry.putInt("dist", (int) Math.sqrt(dx * dx + dz * dz));
        return entry;
    }

    private static ListTag locks(ServerPlayer player, CityData data) {
        UUID id = player.getUUID();
        ListTag locks = new ListTag();
        for (BlockPos pos : List.copyOf(data.locks(id))) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("dist", (int) Math.sqrt(pos.distToCenterSqr(player.position())));
            BlockState state = player.level().getBlockState(pos);
            boolean loaded = player.level().isLoaded(pos);
            if (loaded && !(state.getBlock() instanceof SmartLockBlock)) {
                data.unpairLock(id, pos);
                continue;
            }
            entry.putBoolean("open", loaded && state.hasProperty(SmartLockBlock.POWERED)
                    && state.getValue(SmartLockBlock.POWERED));
            String label = "Замок";
            if (loaded && player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock) {
                label = lock.getLabel();
                entry.putBoolean("owner", lock.isOwner(id));
            }
            entry.putString("label", label);
            locks.add(entry);
        }
        return locks;
    }

    private static ListTag cameras(ServerPlayer player, CityData data) {
        UUID id = player.getUUID();
        ListTag out = new ListTag();
        int index = 0;
        for (BlockPos pos : List.copyOf(data.cameras(id))) {
            index++;
            // Камеру сломали — убираем из списка, но только если чанк загружен:
            // про далёкую камеру мы честно ничего не знаем.
            if (player.level().isLoaded(pos) && !dev.lscity.citylife.city.Cameras.isCamera(
                    player.level(), pos)) {
                data.unpairCamera(id, pos);
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("dist", (int) Math.sqrt(pos.distToCenterSqr(player.position())));
            entry.putString("label", "Камера " + index + " · " + pos.getX() + ", " + pos.getY()
                    + ", " + pos.getZ());
            out.add(entry);
        }
        return out;
    }

    private static void mailSend(ServerPlayer player, CityData data, CompoundTag args) {
        String to = args.getString("to").trim();
        String text = args.getString("text").trim();
        if (to.isEmpty() || text.isEmpty()) {
            return;
        }
        text = text.substring(0, Math.min(text.length(), 240));
        var profile = player.server.getProfileCache() == null ? java.util.Optional.<com.mojang
                .authlib.GameProfile>empty() : player.server.getProfileCache().get(to);
        ServerPlayer live = player.server.getPlayerList().getPlayerByName(to);
        UUID target = live != null ? live.getUUID()
                : profile.map(com.mojang.authlib.GameProfile::getId).orElse(null);
        if (target == null) {
            player.displayClientMessage(Component.translatable("citylife.mail.unknown", to)
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        data.deliverMail(target, new Mail(player.getUUID(), player.getGameProfile().getName(),
                text, player.level().getGameTime(), false));
        player.displayClientMessage(Component.translatable("citylife.mail.sent", to)
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        if (live != null && live != player) {
            live.displayClientMessage(Component.translatable("citylife.mail.new",
                    player.getGameProfile().getName()).withStyle(net.minecraft.ChatFormatting.AQUA),
                    false);
        }
    }

    private static void adPost(ServerPlayer player, CityData data, CompoundTag args) {
        String text = args.getString("text").trim();
        if (text.isEmpty()) {
            return;
        }
        if (!data.withdraw(player.getUUID(), AD_PRICE)) {
            player.displayClientMessage(Component.translatable("citylife.bank.no_money")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        data.postAd(player.getUUID(), player.getGameProfile().getName(),
                text.substring(0, Math.min(text.length(), 120)), player.level().getGameTime());
        player.displayClientMessage(Component.translatable("citylife.ads.posted", AD_PRICE)
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
    }

    private static void cameraView(ServerPlayer player, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        var result = dev.lscity.citylife.city.Cameras.view(player, pos);
        if (result != dev.lscity.citylife.city.Cameras.Result.OK) {
            player.displayClientMessage(Component.translatable("citylife.camera."
                    + result.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    // --- действия -----------------------------------------------------------

    public static void handle(ServerPlayer player, String action, CompoundTag args) {
        CityData data = CityData.get(player.server);
        if (action.equals("pin_try")) {
            // Код замка вводят без гаджета в руках: окно открывает сам замок.
            pinTry(player, data, args);
            return;
        }
        Device device = resolve(player, args.getCompound("ctx"));
        if (device == null) {
            player.displayClientMessage(Component.translatable("citylife.device.gone")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (NEEDS_NETWORK.contains(action) && !device.online()) {
            player.displayClientMessage(Component.translatable("citylife.device.offline")
                    .withStyle(ChatFormatting.RED), true);
            Net.syncDevice(player, device);
            return;
        }
        UUID id = player.getUUID();
        switch (action) {
            case "refresh" -> {
            }
            case "sim_insert" -> insertSim(player, device);
            case "sim_eject" -> ejectSim(player, device);
            case "msg" -> sendMessage(player, data, device, args);
            case "pay" -> pay(player, data, args);
            case "wp_add" -> {
                String name = args.getString("name").trim();
                name = name.isEmpty() ? "Метка" : name.substring(0, Math.min(20, name.length()));
                if (data.addWaypoint(id, Waypoint.of(name, player.blockPosition()))) {
                    player.displayClientMessage(Component.translatable("citylife.map.added", name),
                            true);
                } else {
                    player.displayClientMessage(Component.translatable("citylife.map.full")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            case "wp_del" -> data.removeWaypoint(id, args.getInt("index"));
            case "nav_set" -> {
                Waypoint point = Waypoint.load(args.getCompound("point"));
                data.setRoute(id, point);
                Net.sendRoute(player, point);
                player.displayClientMessage(Component.translatable("citylife.nav.started",
                        point.name()), true);
            }
            case "nav_stop" -> {
                data.setRoute(id, null);
                Net.sendRoute(player, null);
            }
            case "app_install" -> {
                if (DeviceState.install(device.model(), device.state(), args.getString("app"))) {
                    device.changed();
                } else {
                    player.displayClientMessage(Component.translatable("citylife.store.full")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            case "app_remove" -> {
                if (DeviceState.remove(device.state(), args.getString("app"))) {
                    device.changed();
                }
            }
            case "wallpaper" -> {
                device.state().putString(DeviceState.WALLPAPER, args.getString("id"));
                device.changed();
            }
            case "notes_save" -> {
                String text = args.getString("text");
                device.state().putString(DeviceState.NOTES,
                        text.substring(0, Math.min(400, text.length())));
                device.changed();
            }
            case "flashlight" -> {
                if (device.model().has("flashlight")) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0,
                            false, false, true));
                }
            }
            case "market_order" -> order(player, data, args.getString("offer"));
            case "lock_toggle" -> lockToggle(player, data, args);
            case "cam_view" -> cameraView(player, args);
            case "mail_send" -> mailSend(player, data, args);
            case "mail_read" -> data.readAllMail(id);
            case "ad_post" -> adPost(player, data, args);
            case "ad_del" -> data.removeAd(id, args.getInt("id"), player.hasPermissions(2));
            case "cam_del" -> data.unpairCamera(id, BlockPos.of(args.getLong("pos")));
            case "lock_unpair" -> data.unpairLock(id, BlockPos.of(args.getLong("pos")));
            case "lock_grant" -> lockGrant(player, args);
            case "lock_pin" -> lockSetPin(player, args);
            case "job_take" -> dev.lscity.citylife.jobs.Jobs.take(player, args.getString("kind"));
            case "job_quit" -> dev.lscity.citylife.jobs.Jobs.quit(player, false);
            case "home_route" -> dev.lscity.citylife.estate.EstateServer.handle(player,
                    "realty_route", args);
            case "home_trust" -> dev.lscity.citylife.estate.EstateServer.trust(player,
                    dev.lscity.citylife.estate.Estate.get(args.getString("id")),
                    args.getString("name").trim());
            case "sos" -> {
                // 112, как в жизни, дозванивается и без SIM-карты — но только с телефона.
                if (device.model().has("sos")) {
                    emergency(player, args.getString("kind"));
                }
            }
            default -> {
            }
        }
        Net.syncDevice(player, device);
    }

    private static void insertSim(ServerPlayer player, Device device) {
        if (!device.model().simSlot() || device.computer() != null) {
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SimCardItem && SimCardItem.number(stack) != 0) {
                int old = device.sim();
                DeviceState.setSim(device.state(), SimCardItem.number(stack));
                player.displayClientMessage(Component.translatable("citylife.sim.inserted",
                        SimCardItem.format(SimCardItem.number(stack)))
                        .withStyle(ChatFormatting.GREEN), true);
                stack.shrink(1);
                if (old != 0) {
                    giveSim(player, old);
                }
                return;
            }
        }
        player.displayClientMessage(Component.translatable("citylife.sim.no_card")
                .withStyle(ChatFormatting.RED), true);
    }

    private static void ejectSim(ServerPlayer player, Device device) {
        int old = device.sim();
        if (old == 0) {
            return;
        }
        DeviceState.setSim(device.state(), 0);
        giveSim(player, old);
        player.displayClientMessage(Component.translatable("citylife.sim.ejected",
                SimCardItem.format(old)), true);
    }

    private static void giveSim(ServerPlayer player, int number) {
        ItemStack sim = SimCardItem.withNumber(Registration.SIM_CARD.get(), number);
        if (!player.getInventory().add(sim)) {
            player.drop(sim, false);
        }
    }

    private static void sendMessage(ServerPlayer player, CityData data, Device device,
                                    CompoundTag args) {
        int to = args.getInt("to");
        String text = args.getString("text").trim();
        if (device.sim() == 0 || text.isEmpty()) {
            return;
        }
        if (!data.numberExists(to) || to == device.sim()) {
            player.displayClientMessage(Component.translatable("citylife.msg.no_number",
                    SimCardItem.format(Math.max(0, to))).withStyle(ChatFormatting.RED), true);
            return;
        }
        text = text.substring(0, Math.min(180, text.length()));
        String from = player.getGameProfile().getName();
        data.deliver(new Message(device.sim(), from, to, text, player.level().getGameTime()));
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (carries(other, to)) {
                other.sendSystemMessage(Component.translatable("citylife.msg.incoming",
                        SimCardItem.format(device.sim()), from, text)
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    private static void pay(ServerPlayer player, CityData data, CompoundTag args) {
        long amount = args.getLong("amount");
        if (!args.hasUUID("target") || amount <= 0) {
            return;
        }
        ServerPlayer target = player.server.getPlayerList().getPlayer(args.getUUID("target"));
        if (target == null) {
            player.sendSystemMessage(Component.translatable("citylife.bank.offline")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!data.transfer(player.getUUID(), target.getUUID(), amount)) {
            player.sendSystemMessage(Component.translatable("citylife.bank.no_money")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.translatable("citylife.bank.sent", amount,
                target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.translatable("citylife.bank.received", amount,
                player.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
    }

    private static void order(ServerPlayer player, CityData data, String offerId) {
        Market.Offer offer = Market.BY_ID.get(offerId);
        if (offer == null) {
            return;
        }
        if (!data.withdraw(player.getUUID(), offer.price())) {
            player.displayClientMessage(Component.translatable("citylife.market.no_money",
                    Money.format(offer.price())).withStyle(ChatFormatting.RED), false);
            return;
        }
        String title = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new net.minecraft.resources.ResourceLocation(offer.item())))
                .getHoverName().getString();
        if (offer.count() > 1) {
            title = title + " ×" + offer.count();
        }
        long delivery = CityConfig.CONFIG.deliverySeconds.get() * 20L;
        data.placeOrder(player.getUUID(), offer.id(), title, offer.price(),
                player.level().getGameTime() + delivery);
        player.displayClientMessage(Component.translatable("citylife.market.ordered", title,
                Money.format(offer.price()), delivery / 20 / 60 + 1)
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static void lockToggle(ServerPlayer player, CityData data, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        int range = CityConfig.CONFIG.phoneLockRange.get();
        if (pos.distToCenterSqr(player.position()) > (double) range * range) {
            player.sendSystemMessage(Component.translatable("citylife.lock.too_far", range)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SmartLockBlock lock)
                || !(level.getBlockEntity(pos) instanceof SmartLockBlockEntity entity)) {
            data.unpairLock(player.getUUID(), pos);
            return;
        }
        if (!entity.isAllowed(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("citylife.lock.no_access")
                    .withStyle(ChatFormatting.RED));
            entity.note("Отказ (телефон): " + player.getGameProfile().getName());
            return;
        }
        lock.toggle(level, pos, state, entity,
                player.getGameProfile().getName() + " (телефон)", true);
    }

    private static void lockGrant(ServerPlayer player, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!args.hasUUID("target")
                || !(player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock)) {
            return;
        }
        if (!lock.isOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("citylife.lock.not_owner")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        UUID target = args.getUUID("target");
        ServerPlayer targetPlayer = player.server.getPlayerList().getPlayer(target);
        String name = targetPlayer == null ? target.toString().substring(0, 8)
                : targetPlayer.getGameProfile().getName();
        if (lock.isAllowed(target)) {
            lock.deny(target);
            lock.note("Доступ снят: " + name);
            player.sendSystemMessage(Component.translatable("citylife.lock.revoked", name));
        } else {
            lock.allow(target);
            lock.note("Доступ выдан: " + name);
            player.sendSystemMessage(Component.translatable("citylife.lock.granted", name));
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(Component.translatable("citylife.lock.you_granted",
                        lock.getLabel(), player.getGameProfile().getName()));
            }
        }
    }

    private static void lockSetPin(ServerPlayer player, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!(player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock)) {
            return;
        }
        if (!lock.isOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("citylife.lock.not_owner")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String pin = args.getString("pin");
        lock.setPin(pin);
        lock.note(pin.isEmpty() ? "Код снят" : "Код изменён");
        player.sendSystemMessage(Component.translatable(
                pin.isEmpty() ? "citylife.lock.pin_cleared" : "citylife.lock.pin_set"));
    }

    private static void pinTry(ServerPlayer player, CityData data, CompoundTag args) {
        BlockPos pos = BlockPos.of(args.getLong("pos"));
        if (!(player.level().getBlockEntity(pos) instanceof SmartLockBlockEntity lock)
                || pos.distToCenterSqr(player.position()) > 64.0D) {
            return;
        }
        String code = args.getString("code");
        if (lock.checkPin(code)) {
            lock.allow(player.getUUID());
            data.pairLock(player.getUUID(), pos);
            lock.note("Код верный: " + player.getGameProfile().getName());
            player.sendSystemMessage(Component.translatable("citylife.lock.pin_ok")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lock.note("Неверный код: " + player.getGameProfile().getName());
            player.sendSystemMessage(Component.translatable("citylife.lock.pin_bad")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void emergency(ServerPlayer player, String kind) {
        dev.lscity.citylife.city.Emergency.broadcast(player, kind);
        dev.lscity.citylife.city.Emergency.call(player, kind);
    }
}
