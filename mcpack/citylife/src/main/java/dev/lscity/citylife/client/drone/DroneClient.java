package dev.lscity.citylife.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.drone.DroneEntity;
import dev.lscity.citylife.drone.DroneType;
import dev.lscity.citylife.drone.Drones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Дрон на клиенте: модель с крутящимися винтами и огнями, OSD камеры. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DroneClient {

    private DroneClient() {
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneModel.LAYER, DroneModel::create);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Drones.DRONE.get(), Renderer::new);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("drone_osd", DroneClient::osd);
    }

    // --- модель -------------------------------------------------------------------

    static final class Renderer extends EntityRenderer<DroneEntity> {
        private final ModelPart root;
        private final ModelPart[] rotors = new ModelPart[4];

        Renderer(EntityRendererProvider.Context context) {
            super(context);
            root = context.bakeLayer(DroneModel.LAYER);
            ModelPart body = root.getChild("body");
            for (int i = 0; i < 4; i++) {
                rotors[i] = body.getChild("arm" + i).getChild("rotor");
            }
            shadowRadius = 0.35F;
        }

        @Override
        public void render(DroneEntity drone, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                           int light) {
            // Свой дрон изнутри не рисуем: камера сидит в нём.
            if (DronePilot.drone() == drone && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                return;
            }
            DroneType type = drone.droneType();
            float t = drone.tickCount + partial;
            pose.pushPose();
            float bob = drone.motors() && !drone.onGround() ? Mth.sin(t * 0.12F) * 0.03F : 0;
            pose.translate(0, 0.125F * type.scale + bob, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, drone.yRotO, drone.getYRot())));
            // Наклон по движению: дрон «клюёт» носом, когда летит вперёд.
            var v = drone.getDeltaMovement();
            if (DronePilot.drone() == drone) {
                v = DronePilot.velocity();
            } else {
                v = drone.position().subtract(drone.xo, drone.yo, drone.zo);
            }
            float yawRad = drone.getYRot() * Mth.DEG_TO_RAD;
            double fwd = -v.x * Mth.sin(yawRad) + v.z * Mth.cos(yawRad);
            double side = v.x * Mth.cos(yawRad) + v.z * Mth.sin(yawRad);
            pose.mulPose(Axis.XP.rotationDegrees((float) Mth.clamp(fwd * 40, -25, 25)));
            pose.mulPose(Axis.ZP.rotationDegrees((float) Mth.clamp(-side * 40, -25, 25)));
            pose.scale(-type.scale, -type.scale, type.scale);
            float spin = drone.motors() ? t * (type == DroneType.RACER ? 2.6F : 1.8F) : 0.3F;
            for (int i = 0; i < 4; i++) {
                rotors[i].yRot = (i % 2 == 0 ? spin : -spin);
            }
            ResourceLocation tex = new ResourceLocation(CityLife.MOD_ID, "textures/entity/drone/" + type.id + ".png");
            ResourceLocation glow = new ResourceLocation(CityLife.MOD_ID,
                    "textures/entity/drone/" + type.id + "_glow.png");
            root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
            float on = drone.motors() ? (drone.lights() ? 1.0F : 0.75F) : 0.25F;
            // Огни мигают, как у настоящих: зелёный/красный по бокам, белые фары.
            float blink = drone.motors() && (drone.tickCount / 10) % 2 == 0 ? 1.0F : 0.7F;
            root.render(pose, buffers.getBuffer(RenderType.eyes(glow)), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    on * blink, on * blink, on * blink, 1.0F);
            pose.popPose();
            if (!drone.cargo().isEmpty()) {
                pose.pushPose();
                pose.translate(0, 0.05F, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-drone.getYRot()));
                pose.scale(0.6F, 0.6F, 0.6F);
                Minecraft.getInstance().getItemRenderer().renderStatic(drone.cargo(), ItemDisplayContext.GROUND,
                        light, OverlayTexture.NO_OVERLAY, pose, buffers, drone.level(), drone.getId());
                pose.popPose();
            }
            super.render(drone, yaw, partial, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(DroneEntity drone) {
            return new ResourceLocation(CityLife.MOD_ID, "textures/entity/drone/" + drone.droneType().id + ".png");
        }
    }

    // --- OSD ----------------------------------------------------------------------

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF7CFF8A;
    private static final int YELLOW = 0xFFFFD050;
    private static final int RED = 0xFFFF5050;

    /** Экран камеры дрона, как у настоящих FPV-очков и камер. */
    static void osd(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        DroneEntity drone = DronePilot.drone();
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || mc.player == null) {
            return;
        }
        Font font = mc.font;
        DroneType type = drone.droneType();
        int range = Math.min(type.range, Math.max(48, (mc.options.renderDistance().get() - 1) * 16));
        double dist = mc.player.distanceTo(drone);
        float signal = (float) Mth.clamp(1 - dist / range, 0, 1);
        // Помехи у края связи: снег на картинке.
        if (signal < 0.25F) {
            int n = (int) ((0.25F - signal) * 3000);
            var rnd = drone.level().random;
            for (int i = 0; i < n; i++) {
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                int c = rnd.nextBoolean() ? 0x60FFFFFF : 0x60000000;
                g.fill(x, y, x + 2, y + 1, c);
            }
        }
        if (DronePilot.flash()) {
            g.fill(0, 0, w, h, 0x90FFFFFF);
        }
        // Рамка кадра и центр.
        int m = 10;
        int c = 0xC0FFFFFF;
        int len = 18;
        g.fill(m, m, m + len, m + 1, c);
        g.fill(m, m, m + 1, m + len, c);
        g.fill(w - m - len, m, w - m, m + 1, c);
        g.fill(w - m - 1, m, w - m, m + len, c);
        g.fill(m, h - m - 1, m + len, h - m, c);
        g.fill(m, h - m - len, m + 1, h - m, c);
        g.fill(w - m - len, h - m - 1, w - m, h - m, c);
        g.fill(w - m - 1, h - m - len, w - m, h - m, c);
        int cx = w / 2;
        int cy = h / 2;
        if (type == DroneType.RACER) {
            // FPV: линия горизонта клонится вместе с дроном.
            float pitch = mc.player.getXRot();
            int hy = cy + Math.round(pitch * 2.2F);
            g.fill(cx - 60, hy, cx - 20, hy + 1, 0xA0FFFFFF);
            g.fill(cx + 20, hy, cx + 60, hy + 1, 0xA0FFFFFF);
            g.fill(cx - 3, cy, cx + 4, cy + 1, WHITE);
            g.fill(cx, cy - 3, cx + 1, cy + 4, WHITE);
        } else {
            g.fill(cx - 8, cy, cx - 3, cy + 1, WHITE);
            g.fill(cx + 4, cy, cx + 9, cy + 1, WHITE);
            g.fill(cx, cy - 8, cx + 1, cy - 3, WHITE);
            g.fill(cx, cy + 4, cx + 1, cy + 9, WHITE);
        }
        // Сверху: модель, запись, время полёта.
        String name = Component.translatable("item.citylife.drone_" + type.id).getString().toUpperCase();
        g.drawString(font, name, m + 6, m + 6, WHITE, true);
        int sec = DronePilot.flightTicks() / 20;
        String rec = String.format("● REC %02d:%02d", sec / 60, sec % 60);
        g.drawString(font, rec, w - m - 6 - font.width(rec), m + 6, (sec % 2 == 0) ? RED : 0xFFB04040, true);
        // Слева: высота над землёй, скорость, до пилота.
        double alt = drone.getY() - groundBelow(drone);
        double speed = DronePilot.velocity().length() * 20 * 3.6;
        int ly = h - m - 46;
        g.drawString(font, Component.translatable("citylife.drone.osd.alt", String.format("%.0f", alt)).getString(),
                m + 6, ly, WHITE, true);
        g.drawString(font, Component.translatable("citylife.drone.osd.speed", String.format("%.0f", speed))
                .getString(), m + 6, ly + 11, WHITE, true);
        g.drawString(font, Component.translatable("citylife.drone.osd.home", String.format("%.0f", dist)).getString(),
                m + 6, ly + 22, signal < 0.25F ? RED : WHITE, true);
        // Справа: батарея и связь.
        float bat = drone.battery();
        int bx = w - m - 6 - 34;
        int by = h - m - 46;
        int bc = bat < 0.15F ? RED : bat < 0.35F ? YELLOW : GREEN;
        g.fill(bx, by, bx + 30, by + 10, 0xC0FFFFFF);
        g.fill(bx + 1, by + 1, bx + 29, by + 9, 0xC0101010);
        g.fill(bx + 30, by + 3, bx + 32, by + 7, 0xC0FFFFFF);
        g.fill(bx + 2, by + 2, bx + 2 + Math.round(26 * bat), by + 8, bc);
        String pct = Math.round(bat * 100) + "%";
        g.drawString(font, pct, bx - 4 - font.width(pct), by + 1, bc, true);
        int bars = Math.round(signal * 5);
        for (int i = 0; i < 5; i++) {
            int bh = 3 + i * 2;
            g.fill(bx + i * 6, by + 26 - bh, bx + i * 6 + 4, by + 26, i < bars ? (bars <= 1 ? RED : WHITE) : 0x60FFFFFF);
        }
        if (DronePilot.zoom() > 1.01F) {
            String z = String.format("×%.1f", DronePilot.zoom());
            g.drawCenteredString(font, z, cx, cy + 16, WHITE);
        }
        if (drone.lights()) {
            g.drawString(font, Component.translatable("citylife.drone.osd.lights").getString(), m + 6, m + 18,
                    YELLOW, true);
        }
        if (!drone.cargo().isEmpty()) {
            g.drawString(font, Component.translatable("citylife.drone.osd.cargo",
                    drone.cargo().getHoverName()).getString(), m + 6, m + 30, YELLOW, true);
        }
        if (bat < 0.15F && (drone.tickCount / 10) % 2 == 0) {
            g.drawCenteredString(font, Component.translatable("citylife.drone.osd.low").getString(), cx, cy - 30, RED);
        }
        if (signal < 0.25F) {
            g.drawCenteredString(font, Component.translatable("citylife.drone.osd.weak").getString(), cx, cy - 42,
                    RED);
        }
        // Подсказка по клавишам — первые секунды полёта.
        if (sec < 10) {
            String key = "citylife.drone.osd.hint." + (type == DroneType.COURIER ? "courier" : "basic");
            g.drawCenteredString(font, Component.translatable(key).getString(), cx, h - m - 26, 0xC0FFFFFF);
            g.drawCenteredString(font, Component.translatable(key + "2").getString(), cx, h - m - 14, 0xC0FFFFFF);
        }
    }

    private static double groundBelow(DroneEntity drone) {
        var level = drone.level();
        int x = Mth.floor(drone.getX());
        int z = Mth.floor(drone.getZ());
        int top = Mth.floor(drone.getY());
        for (int y = top; y > top - 128 && y > level.getMinBuildHeight(); y--) {
            if (!level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).isAir()) {
                return y;
            }
        }
        return top - 128;
    }
}
