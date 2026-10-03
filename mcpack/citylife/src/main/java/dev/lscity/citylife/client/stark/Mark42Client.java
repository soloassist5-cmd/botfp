package dev.lscity.citylife.client.stark;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.stark.Mark42;
import dev.lscity.citylife.stark.Mark42GantryBlock;
import dev.lscity.citylife.stark.Mark42GantryBlockEntity;
import dev.lscity.citylife.stark.Mark42Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * Mark 42 на клиенте: модель брони, свечение (глаза, реактор, репульсоры,
 * сопла), энергощит, летящие детали, стенд сборки и HUD шлема.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Mark42Client {

    static final ResourceLocation OUTER_TEX = new ResourceLocation(CityLife.MOD_ID,
            "textures/models/armor/mark42_outer.png");
    static final ResourceLocation INNER_TEX = new ResourceLocation(CityLife.MOD_ID,
            "textures/models/armor/mark42_inner.png");
    static final ResourceLocation GLOW_TEX = new ResourceLocation(CityLife.MOD_ID,
            "textures/models/armor/mark42_outer_glow.png");
    static final ResourceLocation SHIELD_TEX = new ResourceLocation(CityLife.MOD_ID,
            "textures/models/armor/mark42_shield.png");
    static final ResourceLocation POWER = new ResourceLocation(CityLife.MOD_ID, "mark42");

    private static HumanoidModel<LivingEntity> outer;
    private static HumanoidModel<LivingEntity> inner;
    /** Отдельные экземпляры для стенда и летящих деталей: им не нужна поза игрока. */
    private static HumanoidModel<LivingEntity> standOuter;
    private static HumanoidModel<LivingEntity> standInner;

    private Mark42Client() {
    }

    // --- регистрация ------------------------------------------------------------

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(Mark42Model.OUTER, Mark42Model::outer);
        event.registerLayerDefinition(Mark42Model.INNER, Mark42Model::inner);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Mark42.PIECE.get(), PieceRenderer::new);
        event.registerBlockEntityRenderer(Mark42.GANTRY_BE.get(), GantryRenderer::new);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new GlowLayer(renderer));
            }
        }
        LivingEntityRenderer stand = event.getRenderer(EntityType.ARMOR_STAND);
        if (stand != null) {
            stand.addLayer(new GlowLayer(stand));
        }
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HELMET.id(), "mark42_hud", Mark42Hud::render);
    }

    // --- модели -----------------------------------------------------------------

    private static void bake() {
        if (outer == null) {
            var models = Minecraft.getInstance().getEntityModels();
            outer = new HumanoidModel<>(models.bakeLayer(Mark42Model.OUTER));
            inner = new HumanoidModel<>(models.bakeLayer(Mark42Model.INNER));
            standOuter = new HumanoidModel<>(models.bakeLayer(Mark42Model.OUTER));
            standInner = new HumanoidModel<>(models.bakeLayer(Mark42Model.INNER));
        }
    }

    /** Модель для слоя брони (Mark42Armor). Забрало поднято — лицо видно. */
    public static HumanoidModel<?> armorModel(LivingEntity entity, EquipmentSlot slot) {
        bake();
        if (slot == EquipmentSlot.LEGS) {
            return inner;
        }
        outer.head.getChild("face").visible = !faceOpen(entity);
        return outer;
    }

    // --- способности Palladium (через отражение: на компиляции Palladium нет) ----

    private static Method isEnabled;
    private static boolean palladiumMissing;

    /** Включена ли способность силы citylife:mark42 у существа. */
    public static boolean ability(LivingEntity entity, String name) {
        if (palladiumMissing || entity == null) {
            return false;
        }
        try {
            if (isEnabled == null) {
                isEnabled = Class.forName("net.threetag.palladium.power.ability.AbilityUtil")
                        .getMethod("isEnabled", LivingEntity.class, ResourceLocation.class, String.class);
            }
            return (Boolean) isEnabled.invoke(null, entity, POWER, name);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            palladiumMissing = true;
            return false;
        }
    }

    static boolean faceOpen(LivingEntity entity) {
        return ability(entity, "faceplate");
    }

    // --- свечение и щит на игроке и стойке для брони -----------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class GlowLayer extends RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> {

        GlowLayer(RenderLayerParent parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limb,
                           float limbAmount, float partial, float age, float yaw, float pitch) {
            boolean head = entity.getItemBySlot(EquipmentSlot.HEAD).getItem() == Mark42.HELMET.get();
            boolean chest = entity.getItemBySlot(EquipmentSlot.CHEST).getItem() == Mark42.CHESTPLATE.get();
            boolean feet = entity.getItemBySlot(EquipmentSlot.FEET).getItem() == Mark42.BOOTS.get();
            if (!head && !chest && !feet) {
                return;
            }
            bake();
            HumanoidModel<LivingEntity> m = outer;
            getParentModel().copyPropertiesTo(m);
            m.setAllVisible(false);
            m.head.visible = head;
            m.head.getChild("face").visible = head && !faceOpen(entity);
            m.body.visible = chest;
            m.rightArm.visible = chest;
            m.leftArm.visible = chest;
            m.rightLeg.visible = feet;
            m.leftLeg.visible = feet;
            // Свет пульсирует чуть-чуть, как у реактора в фильме.
            float pulse = 0.85F + 0.15F * (float) Math.sin(age * 0.15F);
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW_TEX));
            m.renderToBuffer(pose, glow, 0xF000F0, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse, 1.0F);
            if (chest && ability(entity, "shield")) {
                float t = age + partial;
                VertexConsumer swirl = buffers.getBuffer(RenderType.energySwirl(SHIELD_TEX,
                        (t * 0.01F) % 1.0F, (t * 0.008F) % 1.0F));
                pose.pushPose();
                pose.scale(1.08F, 1.04F, 1.08F);
                m.setAllVisible(true);
                m.head.visible = head;
                m.renderToBuffer(pose, swirl, 0xF000F0, OverlayTexture.NO_OVERLAY, 0.45F, 0.75F, 1.0F, 1.0F);
                pose.popPose();
            }
            m.setAllVisible(true);
        }
    }

    // --- общий рисовальщик «костюм без хозяина» -------------------------------------

    /** Нейтральная поза: руки чуть в стороны, как на стенде у Старка. */
    private static void stance(HumanoidModel<LivingEntity> m) {
        for (ModelPart part : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
            part.xRot = 0;
            part.yRot = 0;
            part.zRot = 0;
        }
        m.rightArm.zRot = 0.12F;
        m.leftArm.zRot = -0.12F;
        m.head.getChild("face").visible = true;
    }

    /** Нарисовать части костюма. Поза — модельные координаты, пятки на y = 0 после переворота. */
    static void drawSuit(PoseStack pose, MultiBufferSource buffers, int light, boolean head, boolean chest,
                         boolean legs, boolean feet, float alpha, boolean hologram, float time) {
        bake();
        stance(standOuter);
        stance(standInner);
        if (hologram) {
            VertexConsumer swirl = buffers.getBuffer(RenderType.energySwirl(SHIELD_TEX, (time * 0.01F) % 1.0F,
                    (time * 0.006F) % 1.0F));
            visible(standOuter, true, true, false, true);
            standOuter.renderToBuffer(pose, swirl, 0xF000F0, OverlayTexture.NO_OVERLAY, 0.35F, 0.65F, 1.0F, 1.0F);
            visible(standOuter, true, true, true, true);
            return;
        }
        if (legs) {
            visible(standInner, false, true, true, false);
            standInner.body.visible = true;
            standInner.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(INNER_TEX)), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
            visible(standInner, true, true, true, true);
        }
        visible(standOuter, head, chest, false, feet);
        standOuter.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(OUTER_TEX)), light,
                OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
        standOuter.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW_TEX)), 0xF000F0,
                OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        visible(standOuter, true, true, true, true);
    }

    private static void visible(HumanoidModel<LivingEntity> m, boolean head, boolean chest, boolean legs,
                                boolean feet) {
        m.setAllVisible(false);
        m.head.visible = head;
        m.body.visible = chest || legs;
        m.rightArm.visible = chest;
        m.leftArm.visible = chest;
        m.rightLeg.visible = legs || feet;
        m.leftLeg.visible = legs || feet;
    }

    // --- летящая деталь -------------------------------------------------------------

    static final class PieceRenderer extends EntityRenderer<Mark42Piece> {

        PieceRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.25F;
        }

        @Override
        public void render(Mark42Piece piece, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                           int light) {
            EquipmentSlot slot = piece.slot();
            var at = piece.position(partial);
            pose.pushPose();
            // Сущность рисуется в своих координатах; деталь — в точке пути на этот кадр.
            pose.translate(at.x - net.minecraft.util.Mth.lerp(partial, piece.xo, piece.getX()),
                    at.y - net.minecraft.util.Mth.lerp(partial, piece.yo, piece.getY()),
                    at.z - net.minecraft.util.Mth.lerp(partial, piece.zo, piece.getZ()));
            LivingEntity target = piece.target();
            float face = target != null && piece.progress(partial) > 0.6
                    ? -net.minecraft.util.Mth.rotLerp(partial, target.yBodyRotO, target.yBodyRot) : -piece.getYRot();
            pose.mulPose(Axis.YP.rotationDegrees(face + 180.0F));
            double p = piece.progress(partial);
            // В полёте деталь слегка покачивает, у цели она выравнивается.
            pose.mulPose(Axis.ZP.rotationDegrees((float) (Math.sin((piece.age() + partial) * 0.4) * 12 * (1 - p))));
            pose.scale(-1.0F, -1.0F, 1.0F);
            boolean low = slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
            if (low) {
                pose.translate(0, -0.75F, 0);
            }
            drawSuit(pose, buffers, light, slot == EquipmentSlot.HEAD, slot == EquipmentSlot.CHEST,
                    slot == EquipmentSlot.LEGS, slot == EquipmentSlot.FEET, 1.0F, false, 0);
            pose.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(Mark42Piece piece) {
            return OUTER_TEX;
        }
    }

    // --- стенд сборки ---------------------------------------------------------------

    static final class GantryRenderer implements BlockEntityRenderer<Mark42GantryBlockEntity> {

        GantryRenderer(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(Mark42GantryBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
            var state = be.getBlockState();
            float rot = state.hasProperty(Mark42GantryBlock.FACING)
                    ? state.getValue(Mark42GantryBlock.FACING).toYRot() : 0;
            float time = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partial;
            pose.pushPose();
            pose.translate(0.5, 0.25, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - rot));
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.translate(0, -1.501F, 0);
            int lit = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(),
                    be.getBlockPos().above());
            drawSuit(pose, buffers, lit, true, true, true, true, 1.0F, be.away(), time);
            pose.popPose();
        }

        @Override
        public boolean shouldRenderOffScreen(Mark42GantryBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }
}
