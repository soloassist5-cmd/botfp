package dev.lscity.citylife.trade;

import dev.lscity.citylife.CityLife;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Клик по жителю: прилавок у продавца, реплика у всех остальных.
 *
 * Роль записана в тег сущности при расстановке (citylife_<роль>), поэтому
 * датапаку достаточно поставить жителя — цены, товар и реплики живут в моде.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class ShopHandler {

    private static final String PREFIX = "citylife_";
    /** Тег «это городской житель» роли не задаёт. */
    private static final String MARKER = "npc";

    /** Чем закончился последний клик по жителю — для автотестов. */
    public static volatile String lastOutcome = "";

    private ShopHandler() {
    }

    /**
     * Первый этап клика — «куда именно попали». Easy NPC может забрать клик
     * себе уже здесь, и тогда основной этап до сервера не доходит вовсе.
     * Поэтому для городских жителей этот этап гасим с PASS: игра идёт
     * дальше, к обычному взаимодействию, которое ловит onInteract.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (roleOf(event.getTarget()) != null) {
            event.setCancellationResult(InteractionResult.PASS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        String role = roleOf(target);
        if (role == null) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        dev.lscity.citylife.cmd.CityCommands.note("role=" + role);

        // Риелтор и управдом ведут агентство недвижимости: каталог жилья,
        // а товары для дома — кнопкой в том же окне.
        if ("realtor".equals(role)) {
            lastOutcome = "realty";
            dev.lscity.citylife.estate.EstateServer.open(player, target);
            return;
        }
        // Приехавший по вызову 112 наряд: у каждого свой разговор.
        if (role.startsWith("resp_")) {
            lastOutcome = "crew:" + role.substring(5);
            dev.lscity.citylife.city.Emergency.talk(player, target, role.substring(5));
            return;
        }
        Shop shop = ShopCatalog.BY_ROLE.get(role);
        if (shop != null) {
            CityTrader trader = new CityTrader(target, shop);
            if (trader.hasGoods()) {
                lastOutcome = "shop:" + role;
                trader.open(player);
                dev.lscity.citylife.cmd.CityCommands.note("shop=" + shop.title());
                return;
            }
            if (!shop.offers().isEmpty()) {
                // Товар есть в каталоге, но предметов нет в игре: говорим прямо,
                // чего не хватает, а не отмахиваемся репликой.
                String missing = shop.missing();
                lastOutcome = "empty:" + role;
                CityLife.LOG.warn("City Life: прилавок {} ({}) пуст, нет предметов: {}",
                        role, shop.title(), missing);
                player.displayClientMessage(Component.translatable("citylife.shop.empty",
                        shop.title(), missing).withStyle(ChatFormatting.RED), false);
                return;
            }
        }
        lastOutcome = "line:" + role;
        player.displayClientMessage(speak(target, role), false);
    }

    /**
     * Роль жителя, или null — если это не наш NPC.
     *
     * Основной путь — тег citylife_<роль> рядом с меткой citylife_npc: теги с
     * тем же префиксом мод ставит и другим сущностям (машина получает
     * citylife_first_fill, когда ей заливают первый бак), поэтому без метки
     * жителя роль не читаем.
     *
     * Запасной путь — имя жителя («Продавец техники», «Банкир»): теги не
     * передаются клиенту, а имя видно везде, поэтому клик распознаётся и на
     * стороне игрока. Он же выручает жителей, у которых тег потерялся.
     */
    public static String roleOf(Entity entity) {
        if (entity.getTags().contains(PREFIX + MARKER)) {
            for (String tag : entity.getTags()) {
                if (!tag.startsWith(PREFIX)) {
                    continue;
                }
                String role = tag.substring(PREFIX.length());
                if (!MARKER.equals(role)) {
                    return role;
                }
            }
        }
        if (entity.getCustomName() == null
                || entity instanceof net.minecraft.world.entity.player.Player) {
            return null;
        }
        net.minecraft.resources.ResourceLocation type = net.minecraftforge.registries
                .ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (type == null || !"easy_npc".equals(type.getNamespace())) {
            return null;
        }
        return ShopCatalog.BY_NAME.get(entity.getCustomName().getString());
    }

    /** Прилавок жителя, или null — если он ничем не торгует. */
    public static Shop shopOf(Entity entity) {
        String role = roleOf(entity);
        return role == null ? null : ShopCatalog.BY_ROLE.get(role);
    }

    /**
     * Реплика жителя: имя горожанина, двоеточие, строка из каталога.
     *
     * Фраза выбирается по номеру сущности, а не случайно: один и тот же
     * человек на посту отвечает одинаково, и город не выглядит болтливой
     * рулеткой.
     */
    private static Component speak(Entity npc, String role) {
        List<String> lines = ShopCatalog.LINES.getOrDefault(role, ShopCatalog.DEFAULT_LINES);
        String line = lines.get(Math.floorMod(npc.getId(), lines.size()));
        Component name = npc.getCustomName() != null
                ? npc.getCustomName() : npc.getType().getDescription();
        return Component.empty()
                .append(name.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE));
    }
}
