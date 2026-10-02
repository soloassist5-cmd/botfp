package dev.lscity.citylife.client.stark;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lscity.citylife.stark.PrinterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Печать на 3D-принтере: предмет растёт слоями снизу вверх, над ним —
 * голубой каркас ещё не напечатанной части, по порталу бегает печатающая
 * головка и светит лучом в текущий слой.
 */
@OnlyIn(Dist.CLIENT)
public class PrinterRenderer implements BlockEntityRenderer<PrinterBlockEntity> {

    private static final float BED = 3F / 16F;
    private static final float TALL = 0.62F;
    private static final BlockState ROD = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState HEAD = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();

    public PrinterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PrinterBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float t = (be.getLevel().getGameTime() + partial) / 20F;
        ItemStack job = be.job();
        float p = be.progress(partial);
        boolean busy = !job.isEmpty();
        float hx = busy ? 0.5F + 0.2F * (float) Math.sin(t * 9) : 0.18F;
        float hz = busy ? 0.5F + 0.2F * (float) Math.cos(t * 7.3) : 0.18F;
        float hy = busy ? BED + TALL * p + 0.06F : BED + TALL + 0.06F;

        if (busy) {
            pose.pushPose();
            pose.translate(0.5, BED, 0.5);
            pose.scale(1F, Math.max(0.02F, p), 1F);
            pose.translate(0, TALL / 2, 0);
            pose.mulPose(Axis.YP.rotationDegrees(t * 20));
            pose.scale(0.62F, 0.62F, 0.62F);
            Minecraft.getInstance().getItemRenderer().renderStatic(job, ItemDisplayContext.FIXED, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
            pose.popPose();
            // Каркас ещё не напечатанной части.
            float y0 = BED + TALL * p;
            float y1 = BED + TALL;
            int ghost = 0x6657D8FF;
            float a = 0.22F;
            float b = 0.78F;
            for (float[] c : new float[][]{{a, a}, {a, b}, {b, a}, {b, b}}) {
                Beams.beam(pose, buffers, new Vec3(c[0], y0, c[1]), new Vec3(c[0], y1, c[1]), 0.012F, ghost);
            }
            Beams.beam(pose, buffers, new Vec3(a, y1, a), new Vec3(b, y1, a), 0.012F, ghost);
            Beams.beam(pose, buffers, new Vec3(a, y1, b), new Vec3(b, y1, b), 0.012F, ghost);
            Beams.beam(pose, buffers, new Vec3(a, y1, a), new Vec3(a, y1, b), 0.012F, ghost);
            Beams.beam(pose, buffers, new Vec3(b, y1, a), new Vec3(b, y1, b), 0.012F, ghost);
            int layer = 0xAA9BE8FF;
            Beams.beam(pose, buffers, new Vec3(a, y0, a), new Vec3(b, y0, a), 0.02F, layer);
            Beams.beam(pose, buffers, new Vec3(a, y0, b), new Vec3(b, y0, b), 0.02F, layer);
            Beams.beam(pose, buffers, new Vec3(a, y0, a), new Vec3(a, y0, b), 0.02F, layer);
            Beams.beam(pose, buffers, new Vec3(b, y0, a), new Vec3(b, y0, b), 0.02F, layer);
            // Луч головки в текущий слой.
            Beams.beam(pose, buffers, new Vec3(hx, hy - 0.04F, hz), new Vec3(hx, y0, hz), 0.03F, 0xDDE8FBFF);
            Beams.beam(pose, buffers, new Vec3(hx, hy - 0.04F, hz), new Vec3(hx, y0, hz), 0.09F, 0x5557D8FF);
        }

        // Портал: две штанги крест-накрест и головка на их пересечении.
        var blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        pose.translate(1.5F / 16F, hy + 0.02F, hz - 0.015F);
        pose.scale(13F / 16F, 0.03F, 0.03F);
        blocks.renderSingleBlock(ROD, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        pose.pushPose();
        pose.translate(hx - 0.015F, hy + 0.05F, 1.5F / 16F);
        pose.scale(0.03F, 0.03F, 13F / 16F);
        blocks.renderSingleBlock(ROD, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        pose.pushPose();
        pose.translate(hx - 0.05F, hy - 0.04F, hz - 0.05F);
        pose.scale(0.1F, 0.1F, 0.1F);
        blocks.renderSingleBlock(HEAD, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
