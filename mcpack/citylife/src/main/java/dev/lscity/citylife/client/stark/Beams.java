package dev.lscity.citylife.client.stark;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/** Светящиеся лучи: крест из двух лент со сложением цвета, как у молнии. */
@OnlyIn(Dist.CLIENT)
public final class Beams {

    private Beams() {
    }

    /** Луч из a в b (координаты относительно блока), толщиной width. */
    public static void beam(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, float width, int argb) {
        VertexConsumer c = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 up = Math.abs(dir.normalize().y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(up).normalize().scale(width / 2);
        Vec3 s2 = dir.cross(s1).normalize().scale(width / 2);
        ribbon(c, m, a, b, s1, argb);
        ribbon(c, m, a, b, s2, argb);
    }

    private static void ribbon(VertexConsumer c, Matrix4f m, Vec3 a, Vec3 b, Vec3 s, int argb) {
        Vec3[] q = {a.add(s), a.subtract(s), b.subtract(s), b.add(s)};
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int bl = argb & 255;
        int al = (argb >>> 24) & 255;
        for (int i = 0; i < 4; i++) {
            c.vertex(m, (float) q[i].x, (float) q[i].y, (float) q[i].z).color(r, g, bl, al).endVertex();
        }
        for (int i = 3; i >= 0; i--) {
            c.vertex(m, (float) q[i].x, (float) q[i].y, (float) q[i].z).color(r, g, bl, al).endVertex();
        }
    }
}
