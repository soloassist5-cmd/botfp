package dev.lscity.citylife.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * FPS в плотном городе.
 *
 * Мелочи интерьеров — блоки со своим рисованием (block entity): в мире их
 * 20 тысяч — 11 тысяч полок с товарами, 3,5 тысячи кроватей, 3 тысячи
 * табличек, плиты и корзины. Каждая такая штука рисуется отдельно, каждый
 * кадр, и по умолчанию до 64 блоков — то есть сотни полок с предметами на
 * них сразу. Видеокарта тут ни при чём: упирается процессор, и даже мощный
 * ПК проседает. Поэтому мы оборачиваем их рисовальщики и рисуем мелочи
 * только вблизи: товары на полке — за 14 блоков (весь зал магазина), кровать —
 * за 32, надпись — за 24 (CityConfig.detailDistance меняет всё разом). Все
 * 11 тысяч полок в городе — в магазинах, и на каждой по четыре товара: в
 * плотном квартале это было до 10 тысяч предметов на кадр.
 *
 * Второе — Distant Horizons: по умолчанию он строит дальний вид на 256
 * чанков (4 км — это весь город и ещё столько же пустоты) и держит
 * «сбалансированную» нагрузку на процессор. У нас 64 чанка и щадящий режим,
 * если игрок сам эти настройки не менял.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class RenderTuning {

    /** Тип блок-сущности → дальность при detailDistance = 1. */
    private static final Map<String, Integer> LIMITS = Map.ofEntries(
            Map.entry("another_furniture:shelf", 14),
            Map.entry("minecraft:bed", 32),
            Map.entry("minecraft:sign", 24),
            Map.entry("minecraft:hanging_sign", 24),
            Map.entry("farmersdelight:stove", 16),
            Map.entry("farmersdelight:basket", 16),
            Map.entry("farmersdelight:cutting_board", 16),
            Map.entry("farmersdelight:skillet", 16),
            Map.entry("supplementaries:item_shelf", 14),
            Map.entry("supplementaries:jar", 16),
            Map.entry("supplementaries:pedestal", 24),
            Map.entry("supplementaries:globe", 32),
            Map.entry("supplementaries:sign_post", 32),
            Map.entry("handcrafted:shelf", 14),
            Map.entry("handcrafted:counter", 20),
            Map.entry("minecraft:chest", 32),
            Map.entry("minecraft:barrel", 24),
            Map.entry("minecraft:lectern", 24),
            Map.entry("minecraft:decorated_pot", 24),
            Map.entry("minecraft:chiseled_bookshelf", 20));

    private static Field renderersField;
    private static Map<?, ?> wrappedFrom;
    private static double appliedScale = -1;
    private static boolean dhDone;

    private RenderTuning() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        wrapRenderers(mc.getBlockEntityRenderDispatcher());
        if (!dhDone && mc.level != null) {
            dhDone = true;
            if (CityConfig.CONFIG.tuneDistantHorizonsClient.get()) {
                tuneDistantHorizons();
            }
        }
    }

    // --- блок-сущности ------------------------------------------------------------

    /**
     * Рисовальщики создаются заново при каждой перезагрузке ресурсов (F3+T,
     * смена пакета текстур): тогда в диспетчере новая таблица — оборачиваем её.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wrapRenderers(BlockEntityRenderDispatcher dispatcher) {
        double scale = CityConfig.CONFIG.detailDistance.get();
        try {
            if (renderersField == null) {
                for (Field f : BlockEntityRenderDispatcher.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        renderersField = f;
                    }
                }
                if (renderersField == null) {
                    return;
                }
            }
            Map<BlockEntityType<?>, BlockEntityRenderer<?>> current =
                    (Map<BlockEntityType<?>, BlockEntityRenderer<?>>) renderersField.get(dispatcher);
            if (current == wrappedFrom && scale == appliedScale || current.isEmpty()) {
                return;
            }
            Map<BlockEntityType<?>, BlockEntityRenderer<?>> out = new HashMap<>(current);
            int n = 0;
            for (var entry : current.entrySet()) {
                ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(entry.getKey());
                Integer limit = id == null ? null : LIMITS.get(id.toString());
                BlockEntityRenderer<?> renderer = entry.getValue();
                if (renderer instanceof Near near) {
                    renderer = near.inner;   // уже обёрнут (сменился масштаб)
                }
                if (limit != null) {
                    out.put(entry.getKey(), new Near(renderer, (int) Math.round(limit * scale)));
                    n++;
                }
            }
            renderersField.set(dispatcher, out);
            wrappedFrom = out;
            appliedScale = scale;
            CityLife.LOG.info("City Life: дальность мелочей интерьера ограничена у {} типов (×{})", n, scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CityLife.LOG.warn("City Life: не удалось ограничить дальность блок-сущностей: {}", e.toString());
            renderersField = null;
            wrappedFrom = null;
        }
    }

    /** Обёртка: тот же рисовальщик, но только ближе limit блоков. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class Near implements BlockEntityRenderer {
        final BlockEntityRenderer inner;
        private final int limit;

        Near(BlockEntityRenderer inner, int limit) {
            this.inner = inner;
            this.limit = limit;
        }

        @Override
        public void render(BlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light,
                           int overlay) {
            inner.render(be, partial, pose, buffers, light, overlay);
        }

        @Override
        public boolean shouldRenderOffScreen(BlockEntity be) {
            return inner.shouldRenderOffScreen(be);
        }

        @Override
        public int getViewDistance() {
            return Math.min(limit, inner.getViewDistance());
        }

        @Override
        public boolean shouldRender(BlockEntity be, Vec3 camera) {
            return Vec3.atCenterOf(be.getBlockPos()).closerThan(camera, getViewDistance())
                    && inner.shouldRender(be, camera);
        }
    }

    // --- Distant Horizons -----------------------------------------------------------

    private static final String DH = "com.seibel.distanthorizons.core.config.Config$Client";

    private static void tuneDistantHorizons() {
        int radius = (int) Math.round(64 * CityConfig.CONFIG.detailDistance.get());
        radius = Math.max(32, Math.min(256, radius));
        StringBuilder done = new StringBuilder();
        set(DH + "$Advanced$Graphics$Quality", "lodChunkRenderDistanceRadius", radius, done);
        try {
            Object low = Enum.valueOf((Class) Class.forName(
                    "com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiThreadPreset"), "LOW_IMPACT");
            set(DH, "threadPresetSetting", low, done);
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
            // другая версия Distant Horizons — оставляем как есть
        }
        if (done.length() > 0) {
            CityLife.LOG.info("City Life: Distant Horizons для города —{}", done);
        }
    }

    private static void set(String cls, String field, Object value, StringBuilder done) {
        try {
            Object entry = Class.forName(cls).getField(field).get(null);
            Object now = entry.getClass().getMethod("get").invoke(entry);
            Object def = entry.getClass().getMethod("getDefaultValue").invoke(entry);
            if (now == null || !now.equals(def) || now.equals(value)) {
                return;   // игрок уже настроил сам — не трогаем
            }
            entry.getClass().getMethod("set", Object.class).invoke(entry, value);
            done.append(' ').append(field).append(' ').append(now).append(" → ").append(value);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            CityLife.LOG.debug("City Life: настройка Distant Horizons {} недоступна: {}", field, e.toString());
        }
    }
}
