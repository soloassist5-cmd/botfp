package dev.lscity.citylife.city;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Правила города, которые должны работать всегда, а не раз в 15 секунд.
 *
 * Мобы. Датапак раньше убивал враждебных мобов по расписанию, и между
 * проходами крипер успевал подойти, а животные и стражи оставались вовсе.
 * Здесь любой моб отклоняется ещё при входе в мир — при естественном
 * спавне, из яйца, из спавнера и при загрузке старого чанка. Пропускаем
 * жителей Easy NPC (городские продавцы) и технику модов: FPV-дрон и турель
 * SecurityCraft тоже «мобы» для игры, но не звери и не монстры — у них
 * категория MISC. Ванильные мобы не проходят никогда: деревенские жители и
 * големы тоже MISC, но в городе им не место.
 *
 * Взрывы. Гранаты и ракеты TaCZ, мины SecurityCraft и динамит ранят как
 * обычно, но список ломаемых блоков очищается: город не разнести, и от
 * взрыва не начинается пожар.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class CityRules {

    /** Моды, чьи «мобы» — это жители города, а не животные или монстры. */
    private static final String[] ALLOWED_NAMESPACES = {"easy_npc", "fpvdrone", "diligentstalker"};

    private CityRules() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        // Житель прошлого поколения: датапак уже расставил замену, этот уходит,
        // как только его чанк загрузился. Наряды 112 живут своей жизнью.
        if (entity.getTags().contains("citylife_npc") && !entity.getTags().contains("citylife_crew")
                && !entity.getTags().contains(dev.lscity.citylife.trade.ShopCatalog.GEN_TAG)) {
            event.setCanceled(true);
            return;
        }
        if (!CityConfig.CONFIG.noMobs.get()) {
            return;
        }
        if (entity instanceof Mob && !allowed(entity)) {
            event.setCanceled(true);
        }
    }

    private static boolean allowed(Entity entity) {
        if (entity.getTags().contains("citylife_npc")) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null) {
            return false;
        }
        for (String namespace : ALLOWED_NAMESPACES) {
            if (namespace.equals(id.getNamespace())) {
                return true;
            }
        }
        return machine(id, entity.getType().getCategory());
    }

    /** Модовая «техника»: не ванильный моб и не зверь, не монстр, не рыба. */
    public static boolean machine(ResourceLocation id, net.minecraft.world.entity.MobCategory category) {
        return !"minecraft".equals(id.getNamespace()) && category == net.minecraft.world.entity.MobCategory.MISC;
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!event.getLevel().isClientSide() && CityConfig.CONFIG.explosionsKeepBlocks.get()) {
            event.getAffectedBlocks().clear();
        }
    }
}
