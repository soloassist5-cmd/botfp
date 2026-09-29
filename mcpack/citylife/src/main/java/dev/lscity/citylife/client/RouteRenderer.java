package dev.lscity.citylife.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.Waypoint;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Маршрут навигатора в мире: бегущие стрелки по земле и маркер цели.
 *
 * Путь идёт по сетке улиц города (шаг 64 блока): выходим на ближайшую
 * улицу, едем по ней, сворачиваем к цели — так получается дорога, по которой
 * можно пройти, а не линия сквозь дома.
 *
 * Высоту берём не с карты высот мира, а от уровня, на котором идёт игрок:
 * ищем землю в паре блоков выше и ниже предыдущей точки. Раньше линия
 * запрыгивала на крыши, кроны и эстакаду, потому что брала самый верхний
 * блок столба.
 *
 * Цель стоит на своей высоте: на месте — светящееся кольцо, луч и ромб над
 * ним. Войдёшь в кольцо — сервер сам снимет маршрут.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class RouteRenderer {

    private static final int GRID = 64;
    /** Дальше этого стрелки не рисуем: там чанки уже не загружены. */
    private static final int DRAW_RADIUS = 96;
    /** Шаг между стрелками, блоков. */
    private static final double SPACING = 1.6;
    private static final int ACCENT = 0xFF45D0F0;
    private static final int GLOW = 0xFFB8F4FF;

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
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        double px = minecraft.player.getX();
        double pz = minecraft.player.getZ();
        double time = System.currentTimeMillis() / 1000.0;
        List<double[]> path = buildPath(minecraft.level, px, minecraft.player.getY(), pz, target);
        chevrons(buffer, matrix, path, px, pz, time);
        marker(buffer, matrix, target, time);

        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    // --- путь ---------------------------------------------------------------

    /** Ломаная по улицам: игрок -> ближайшая улица -> вдоль неё -> цель. */
    private static List<double[]> buildPath(ClientLevel level, double px, double py, double pz,
                                            Waypoint target) {
        double tx = target.x() + 0.5;
        double tz = target.z() + 0.5;
        double streetX = Math.round(px / GRID) * (double) GRID;
        double streetZ = Math.round(tz / GRID) * (double) GRID;
        List<double[]> corners = new ArrayList<>();
        corners.add(new double[]{px, pz});
        // Короткий путь — напрямик: гнать через улицу ради двадцати блоков незачем.
        if (Math.hypot(tx - px, tz - pz) > 40) {
            corners.add(new double[]{streetX, pz});
            corners.add(new double[]{streetX, streetZ});
            corners.add(new double[]{tx, streetZ});
        }
        corners.add(new double[]{tx, tz});

        List<double[]> points = new ArrayList<>();
        double y = Math.floor(py);
        double walked = 0;
        for (int i = 0; i < corners.size() - 1; i++) {
            double[] from = corners.get(i);
            double[] to = corners.get(i + 1);
            double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
            int steps = (int) Math.ceil(length);
            for (int step = 0; step < steps; step++) {
                double t = step / (double) steps;
                double x = from[0] + (to[0] - from[0]) * t;
                double z = from[1] + (to[1] - from[1]) * t;
                if (Math.hypot(x - px, z - pz) > DRAW_RADIUS) {
                    walked += 1;
                    continue;
                }
                y = ground(level, x, z, y);
                points.add(new double[]{x, y + 0.04, z, walked});
                walked += 1;
            }
        }
        // Последний отрезок заканчиваем у кольца цели, а не в его центре.
        double ringY = target.y();
        points.removeIf(p -> Math.hypot(p[0] - tx, p[2] - tz) < 1.6
                && Math.abs(p[1] - ringY) < 3);
        return points;
    }

    /**
     * Земля рядом с уровнем ref: первый твёрдый блок, над которым два свободных,
     * в диапазоне трёх блоков вверх и шести вниз. Если ничего нет — держим ref.
     */
    private static double ground(ClientLevel level, double x, double z, double ref) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) ref + 3;
        for (int yy = top; yy >= ref - 6; yy--) {
            BlockPos below = new BlockPos(bx, yy - 1, bz);
            BlockPos feet = new BlockPos(bx, yy, bz);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above())
                    .isEmpty()) {
                return yy;
            }
        }
        return ref;
    }

    // --- стрелки -----------------------------------------------------------

    /** Шевроны «›» вдоль пути: бегут к цели и гаснут вдали от игрока. */
    private static void chevrons(BufferBuilder buffer, Matrix4f matrix, List<double[]> path,
                                 double px, double pz, double time) {
        if (path.size() < 3) {
            return;
        }
        double flow = (time * 3.0) % SPACING;
        double next = SPACING - flow;
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1);
            double[] b = path.get(i);
            if (b[3] < next) {
                continue;
            }
            next += SPACING;
            double dx = b[0] - a[0];
            double dz = b[2] - a[2];
            double len = Math.hypot(dx, dz);
            if (len < 1.0E-4 || Math.abs(b[1] - a[1]) > 1.1) {
                continue;
            }
            dx /= len;
            dz /= len;
            double dist = Math.hypot(b[0] - px, b[2] - pz);
            float alpha = (float) Math.max(0.15, 0.9 - dist / DRAW_RADIUS);
            // Ближние стрелки — светлее: видно, куда шагать прямо сейчас.
            int colour = dist < 12 ? GLOW : ACCENT;
            chevron(buffer, matrix, b[0], b[1], b[2], dx, dz, colour, alpha);
        }
    }

    private static void chevron(BufferBuilder buffer, Matrix4f matrix, double x, double y,
                                double z, double dx, double dz, int colour, float alpha) {
        double nx = -dz;
        double nz = dx;
        double arm = 0.42;
        double back = 0.30;
        double thick = 0.16;
        double tipX = x + dx * 0.22;
        double tipZ = z + dz * 0.22;
        for (int side = -1; side <= 1; side += 2) {
            double endX = tipX - dx * back + nx * arm * side;
            double endZ = tipZ - dz * back + nz * arm * side;
            vertex(buffer, matrix, tipX, y, tipZ, colour, alpha);
            vertex(buffer, matrix, tipX - dx * thick, y, tipZ - dz * thick, colour, alpha);
            vertex(buffer, matrix, endX - dx * thick, y, endZ - dz * thick, colour, alpha * 0.8F);
            vertex(buffer, matrix, endX, y, endZ, colour, alpha * 0.8F);
        }
    }

    // --- маркер цели -------------------------------------------------------

    private static void marker(BufferBuilder buffer, Matrix4f matrix, Waypoint target,
                               double time) {
        double x = target.x() + 0.5;
        double y = target.y() + 0.03;
        double z = target.z() + 0.5;
        double pulse = 0.5 + 0.5 * Math.sin(time * 3.0);

        // Кольцо на земле: сюда нужно войти.
        ring(buffer, matrix, x, y, z, 1.05, 1.35, ACCENT, 0.75F);
        ring(buffer, matrix, x, y, z, 1.35 + pulse * 0.5, 1.45 + pulse * 0.5, GLOW,
                (float) (0.45 * (1 - pulse)));
        disc(buffer, matrix, x, y, z, 1.05, ACCENT, 0.18F);

        // Луч вверх — видно из-за домов, пока идёшь по кварталу.
        double half = 0.12;
        for (int side = 0; side < 2; side++) {
            double ox = side == 0 ? half : 0;
            double oz = side == 0 ? 0 : half;
            vertex(buffer, matrix, x - ox, y, z - oz, ACCENT, 0.55F);
            vertex(buffer, matrix, x + ox, y, z + oz, ACCENT, 0.55F);
            vertex(buffer, matrix, x + ox, y + 40, z + oz, ACCENT, 0.0F);
            vertex(buffer, matrix, x - ox, y + 40, z - oz, ACCENT, 0.0F);
        }

        // Ромб над кольцом: вращается и покачивается.
        double cy = y + 1.9 + Math.sin(time * 2.0) * 0.12;
        double angle = time * 1.6;
        double r = 0.34;
        double h = 0.5;
        double[][] corners = new double[4][];
        for (int i = 0; i < 4; i++) {
            double a = angle + i * Math.PI / 2;
            corners[i] = new double[]{x + Math.cos(a) * r, z + Math.sin(a) * r};
        }
        for (int i = 0; i < 4; i++) {
            double[] c1 = corners[i];
            double[] c2 = corners[(i + 1) % 4];
            int shade = i % 2 == 0 ? GLOW : ACCENT;
            // Две треугольные грани (верх и низ) как вырожденные четырёхугольники.
            vertex(buffer, matrix, x, cy + h, z, shade, 0.95F);
            vertex(buffer, matrix, c1[0], cy, c1[1], shade, 0.95F);
            vertex(buffer, matrix, c2[0], cy, c2[1], shade, 0.95F);
            vertex(buffer, matrix, x, cy + h, z, shade, 0.95F);
            vertex(buffer, matrix, x, cy - h, z, ACCENT, 0.9F);
            vertex(buffer, matrix, c1[0], cy, c1[1], ACCENT, 0.9F);
            vertex(buffer, matrix, c2[0], cy, c2[1], ACCENT, 0.9F);
            vertex(buffer, matrix, x, cy - h, z, ACCENT, 0.9F);
        }
    }

    private static void ring(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z,
                             double inner, double outer, int colour, float alpha) {
        int segments = 40;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.PI * 2 * i / segments;
            double a2 = Math.PI * 2 * (i + 1) / segments;
            vertex(buffer, matrix, x + Math.cos(a1) * inner, y, z + Math.sin(a1) * inner, colour, alpha);
            vertex(buffer, matrix, x + Math.cos(a1) * outer, y, z + Math.sin(a1) * outer, colour, alpha);
            vertex(buffer, matrix, x + Math.cos(a2) * outer, y, z + Math.sin(a2) * outer, colour, alpha);
            vertex(buffer, matrix, x + Math.cos(a2) * inner, y, z + Math.sin(a2) * inner, colour, alpha);
        }
    }

    private static void disc(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z,
                             double radius, int colour, float alpha) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.PI * 2 * i / segments;
            double a2 = Math.PI * 2 * (i + 1) / segments;
            vertex(buffer, matrix, x, y, z, colour, alpha);
            vertex(buffer, matrix, x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius, colour, alpha);
            vertex(buffer, matrix, x + Math.cos(a2) * radius, y, z + Math.sin(a2) * radius, colour, alpha);
            vertex(buffer, matrix, x, y, z, colour, alpha);
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
