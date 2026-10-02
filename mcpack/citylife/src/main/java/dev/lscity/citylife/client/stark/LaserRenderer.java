package dev.lscity.citylife.client.stark;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lscity.citylife.stark.LaserBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Луч лазерного датчика: голубой, пока охрана ждёт, красный и мигающий при тревоге. */
@OnlyIn(Dist.CLIENT)
public class LaserRenderer implements BlockEntityRenderer<LaserBlockEntity> {

    public LaserRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(LaserBlockEntity be) {
        return true;
    }

    @Override
    public void render(LaserBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!StarkClient.armed || be.getLevel() == null) {
            return;
        }
        int length = be.length();
        if (length <= 0) {
            return;
        }
        Direction dir = be.facing();
        Vec3 n = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 a = new Vec3(0.5, 0.5, 0.5).add(n.scale(0.25));
        Vec3 b = new Vec3(0.5, 0.5, 0.5).add(n.scale(0.5 + length));
        float t = (be.getLevel().getGameTime() + partial) / 20F;
        boolean alarm = StarkClient.alarm;
        float flicker = 0.85F + 0.15F * (float) Math.sin(t * 23 + be.getBlockPos().hashCode());
        if (alarm && (int) (t * 4) % 2 == 0) {
            flicker *= 0.35F;
        }
        int core = alarm ? 0xFFFF3030 : 0xFFFF2A2A;
        int glow = alarm ? 0x66FF2020 : 0x44FF3030;
        Beams.beam(pose, buffers, a, b, 0.025F, scale(core, flicker));
        Beams.beam(pose, buffers, a, b, 0.09F, scale(glow, flicker));
    }

    private static int scale(int argb, float f) {
        int a = Math.round(((argb >>> 24) & 255) * f);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
