package dev.lscity.citylife.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.client.ui.PhoneUi;
import dev.lscity.citylife.data.Waypoint;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Маршрут навигатора прямо в мире: светящаяся дорожка по земле и столб света
 * над целью.
 *
 * Дорожка идёт не напрямик, а по сетке улиц города (шаг 64 блока): сначала
 * выходим на ближайшую улицу, потом едем по ней и сворачиваем к цели —
 * получается путь, по которому действительно можно пройти, а не линия сквозь
 * дома. Точки берутся только в загруженных чанках: дальше высоты земли
 * клиент не знает, и там вместо дорожки видно столб над целью.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class RouteRenderer {

    /** Шаг сетки улиц города. */
    private static final int GRID = 64;
    /** Дальше этого линию не строим: чанки не загружены, земли не видно. */
    private static final int DRAW_RADIUS = 160;
    private static final double WIDTH = 0.35;

    private RouteRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Waypoint target = NavClient.target();
        Minecraft minecraft = Minecraft.getInstance();
        if (target == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        List<double[]> path = buildPath(minecraft.level,
                minecraft.player.getX(), minecraft.player.getZ(), target);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camX, -camY, -camZ);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        ribbon(buffer, matrix, path);
        beam(buffer, matrix, target, minecraft.level);

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** Ломаная по улицам: игрок -> ближайшая улица -> вдоль неё -> цель. */
    private static List<double[]> buildPath(ClientLevel level, double px, double pz,
                                            Waypoint target) {
        double tx = target.x() + 0.5;
        double tz = target.z() + 0.5;
        double streetX = Math.round(px / GRID) * (double) GRID;
        double streetZ = Math.round(tz / GRID) * (double) GRID;

        List<double[]> corners = new ArrayList<>();
        corners.add(new double[]{px, pz});
        corners.add(new double[]{streetX, pz});
        corners.add(new double[]{streetX, streetZ});
        corners.add(new double[]{tx, streetZ});
        corners.add(new double[]{tx, tz});

        // Разбиваем ломаную на точки по блоку и прижимаем каждую к земле.
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < corners.size() - 1; i++) {
            double[] from = corners.get(i);
            double[] to = corners.get(i + 1);
            double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
            int steps = (int) Math.ceil(length);
            for (int step = 0; step <= steps; step++) {
                double t = steps == 0 ? 0 : step / (double) steps;
                double x = from[0] + (to[0] - from[0]) * t;
                double z = from[1] + (to[1] - from[1]) * t;
                if (Math.hypot(x - px, z - pz) > DRAW_RADIUS) {
                    continue;
                }
                double y = level.getHeight(Heightmap.Types.WORLD_SURFACE,
                        (int) Math.floor(x), (int) Math.floor(z)) + 0.06;
                if (points.isEmpty() || dist2(points.get(points.size() - 1), x, z) > 0.04) {
                    points.add(new double[]{x, y, z});
                }
            }
        }
        return points;
    }

    private static double dist2(double[] point, double x, double z) {
        return (point[0] - x) * (point[0] - x) + (point[2] - z) * (point[2] - z);
    }

    /** Лента по земле: каждый сегмент — четырёхугольник со своим цветом. */
    private static void ribbon(BufferBuilder buffer, Matrix4f matrix, List<double[]> points) {
        if (points.size() < 2) {
            return;
        }
        // Бегущая волна: сегменты подсвечиваются по очереди, как в навигаторе.
        double phase = (System.currentTimeMillis() % 1400L) / 1400.0;
        for (int i = 0; i < points.size() - 1; i++) {
            double[] a = points.get(i);
            double[] b = points.get(i + 1);
            double dx = b[0] - a[0];
            double dz = b[2] - a[2];
            double length = Math.hypot(dx, dz);
            if (length < 1.0E-4) {
                continue;
            }
            double nx = -dz / length * WIDTH;
            double nz = dx / length * WIDTH;

            double wave = ((i / 6.0) - phase * 6.0) % 6.0;
            if (wave < 0) {
                wave += 6.0;
            }
            float glow = (float) (0.45 + 0.55 * Math.max(0, 1.0 - wave / 1.5));
            int colour = PhoneUi.lerp(0xFF1F7FA8, 0xFF7BE8FF, glow);
            float alpha = 0.35F + 0.45F * glow;

            vertex(buffer, matrix, a[0] - nx, a[1], a[2] - nz, colour, alpha);
            vertex(buffer, matrix, b[0] - nx, b[1], b[2] - nz, colour, alpha);
            vertex(buffer, matrix, b[0] + nx, b[1], b[2] + nz, colour, alpha);
            vertex(buffer, matrix, a[0] + nx, a[1], a[2] + nz, colour, alpha);
        }
    }

    /** Столб света над целью — видно из любой точки квартала. */
    private static void beam(BufferBuilder buffer, Matrix4f matrix, Waypoint target,
                            ClientLevel level) {
        double x = target.x() + 0.5;
        double z = target.z() + 0.5;
        double ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, target.x(), target.z());
        double top = ground + 24;
        double half = 0.22;
        int colour = 0xFF45D0F0;

        for (int side = 0; side < 2; side++) {
            double ox = side == 0 ? half : 0;
            double oz = side == 0 ? 0 : half;
            for (int face = 0; face < 2; face++) {
                double sign = face == 0 ? 1 : -1;
                vertex(buffer, matrix, x - ox * sign, ground, z - oz * sign, colour, 0.55F);
                vertex(buffer, matrix, x + ox * sign, ground, z + oz * sign, colour, 0.55F);
                vertex(buffer, matrix, x + ox * sign, top, z + oz * sign, colour, 0.0F);
                vertex(buffer, matrix, x - ox * sign, top, z - oz * sign, colour, 0.0F);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z,
                               int colour, float alpha) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(((colour >> 16) & 0xFF) / 255F, ((colour >> 8) & 0xFF) / 255F,
                        (colour & 0xFF) / 255F, alpha)
                .endVertex();
    }
}
