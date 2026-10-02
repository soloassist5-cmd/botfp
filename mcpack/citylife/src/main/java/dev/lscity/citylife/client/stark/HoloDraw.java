package dev.lscity.citylife.client.stark;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Рисование голограмм в мире: прямоугольники, линии, дуги и текст в
 * «пикселях» экрана (x вправо, y вниз). Всё светится в полную яркость и
 * видно с обеих сторон.
 */
@OnlyIn(Dist.CLIENT)
public final class HoloDraw {
    public static final int FULL_BRIGHT = 0xF000F0;

    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final Font font;
    private float z;

    public HoloDraw(PoseStack pose, MultiBufferSource buffers, Font font) {
        this.pose = pose;
        this.buffers = buffers;
        this.font = font;
    }

    /** Следующий слой чуть ближе к зрителю: так слои не мерцают. */
    public void layer() {
        z += 0.35F;
    }

    private VertexConsumer quads() {
        return buffers.getBuffer(RenderType.textBackground());
    }

    private static void v(VertexConsumer c, Matrix4f m, float x, float y, float z, int argb) {
        c.vertex(m, x, y, z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255)
                .uv2(FULL_BRIGHT).endVertex();
    }

    /** Четырёхугольник по углам в любом порядке обхода — с обеих сторон. */
    public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        Matrix4f m = pose.last().pose();
        VertexConsumer c = quads();
        v(c, m, x0, y0, z, argb);
        v(c, m, x1, y1, z, argb);
        v(c, m, x2, y2, z, argb);
        v(c, m, x3, y3, z, argb);
        v(c, m, x3, y3, z, argb);
        v(c, m, x2, y2, z, argb);
        v(c, m, x1, y1, z, argb);
        v(c, m, x0, y0, z, argb);
    }

    public void rect(float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x0, y1, x1, y1, x1, y0, argb);
    }

    public void frame(float x0, float y0, float x1, float y1, float t, int argb) {
        rect(x0, y0, x1, y0 + t, argb);
        rect(x0, y1 - t, x1, y1, argb);
        rect(x0, y0 + t, x0 + t, y1 - t, argb);
        rect(x1 - t, y0 + t, x1, y1 - t, argb);
    }

    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) {
            return;
        }
        float nx = -dy / len * width / 2;
        float ny = dx / len * width / 2;
        quad(x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, argb);
    }

    /** Кольцевая дуга от угла a0 до a1 (градусы, 0 — вверх, по часовой). */
    public void arc(float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        int steps = Math.max(2, (int) (Math.abs(a1 - a0) / 6));
        for (int i = 0; i < steps; i++) {
            double b0 = Math.toRadians(a0 + (a1 - a0) * i / steps);
            double b1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            float s0 = (float) Math.sin(b0);
            float c0 = (float) -Math.cos(b0);
            float s1 = (float) Math.sin(b1);
            float c1 = (float) -Math.cos(b1);
            quad(cx + s0 * r0, cy + c0 * r0, cx + s0 * r1, cy + c0 * r1,
                    cx + s1 * r1, cy + c1 * r1, cx + s1 * r0, cy + c1 * r0, argb);
        }
    }

    public void ring(float cx, float cy, float r0, float r1, int argb) {
        arc(cx, cy, r0, r1, 0, 360, argb);
    }

    public int text(String s, float x, float y, int argb) {
        z += 0.1F;
        pose.pushPose();
        pose.translate(0, 0, z);
        int w = font.drawInBatch(s, x, y, argb, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        pose.popPose();
        return w;
    }

    /** Текст крупнее в scale раз, левый верхний угол в (x, y). */
    public void bigText(String s, float x, float y, float scale, int argb) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        text(s, 0, 0, argb);
        pose.popPose();
    }

    public int width(String s) {
        return font.width(s);
    }

    /** Обрезать строку по ширине без многоточия. */
    public String clip(String s, int width) {
        return font.plainSubstrByWidth(s, Math.max(0, width));
    }

    /** Обрезать строку по ширине с многоточием. */
    public String fit(String s, int width) {
        if (font.width(s) <= width) {
            return s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…";
    }
}
