package dev.lscity.citylife.trade;

import dev.lscity.citylife.CityLife;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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

    private ShopHandler() {
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        String role = roleOf(target);
        if (role == null) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Риелтор и управдом ведут агентство недвижимости: каталог жилья,
        // а товары для дома — кнопкой в том же окне.
        if ("realtor".equals(role)) {
            dev.lscity.citylife.estate.EstateServer.open(player, target);
            return;
        }
        // Приехавший по вызову 112 наряд: у каждого свой разговор.
        if (role.startsWith("resp_")) {
            dev.lscity.citylife.city.Emergency.talk(player, target, role.substring(5));
            return;
        }
        Shop shop = ShopCatalog.BY_ROLE.get(role);
        if (shop != null) {
            CityTrader trader = new CityTrader(target, shop);
            if (trader.hasGoods()) {
                trader.open(player);
                return;
            }
        }
        player.displayClientMessage(speak(target, role), false);
    }

    /**
     * Роль жителя по тегам, или null — если это не наш NPC.
     *
     * Сначала проверяем метку жителя citylife_npc: теги с тем же префиксом
     * мод ставит и другим сущностям (машина получает citylife_first_fill,
     * когда ей заливают первый бак), и без этой проверки клик по машине
     * перехватывался как разговор с продавцом — сесть в неё было нельзя.
     */
    public static String roleOf(Entity entity) {
        if (!entity.getTags().contains(PREFIX + MARKER)) {
            return null;
        }
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(PREFIX)) {
                continue;
            }
            String role = tag.substring(PREFIX.length());
            if (!MARKER.equals(role)) {
                return role;
            }
        }
        return null;
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
