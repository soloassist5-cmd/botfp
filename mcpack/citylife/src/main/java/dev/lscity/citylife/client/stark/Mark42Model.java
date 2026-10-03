package dev.lscity.citylife.client.stark;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Модель брони Mark 42: внешний слой (шлем с забралом, кираса с реактором,
 * руки с наплечниками и репульсорами, ботинки с соплами) и внутренний (поножи).
 *
 * Файл собран citylife/tools/gen_mark42.py вместе с текстурами — править
 * там, иначе развёртка разойдётся с картинкой.
 */
@OnlyIn(Dist.CLIENT)
public final class Mark42Model {

    public static final ModelLayerLocation OUTER =
            new ModelLayerLocation(new ResourceLocation("citylife", "mark42"), "outer");
    public static final ModelLayerLocation INNER =
            new ModelLayerLocation(new ResourceLocation("citylife", "mark42"), "inner");

    private Mark42Model() {
    }

    public static LayerDefinition outer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        PartDefinition rightarm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftarm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
        head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(33, 0).addBox(-1F, -9.6F, -4.6F, 2F, 1F, 8F, new CubeDeformation(0.1F)), PartPose.ZERO);
        head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(54, 0).addBox(-5.7F, -5F, -1.5F, 1F, 3F, 3F, new CubeDeformation(0.05F)), PartPose.ZERO);
        head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(63, 0).addBox(4.7F, -5F, -1.5F, 1F, 3F, 3F, new CubeDeformation(0.05F)), PartPose.ZERO);
        PartDefinition head_face = head.addOrReplaceChild("face", CubeListBuilder.create(), PartPose.ZERO);
        head_face.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(72, 0).addBox(-3.5F, -6.8F, -5.4F, 7F, 6F, 1F, new CubeDeformation(0.15F)), PartPose.ZERO);
        head_face.addOrReplaceChild("chin", CubeListBuilder.create().texOffs(89, 0).addBox(-2.5F, -1F, -5.2F, 5F, 1F, 1F, new CubeDeformation(0.1F)), PartPose.ZERO);
        body.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(102, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1.01F)), PartPose.ZERO);
        body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 17).addBox(-4.5F, 0.3F, -3.6F, 9F, 5F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        body.addOrReplaceChild("reactor", CubeListBuilder.create().texOffs(21, 17).addBox(-1.5F, 1.4F, -4.1F, 3F, 3F, 1F, new CubeDeformation(-0.3F)), PartPose.ZERO);
        body.addOrReplaceChild("abs", CubeListBuilder.create().texOffs(30, 17).addBox(-3F, 6F, -3.35F, 6F, 5F, 1F, new CubeDeformation(-0.2F)), PartPose.ZERO);
        body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(45, 17).addBox(-4F, 0.5F, 2.6F, 8F, 8F, 1F, new CubeDeformation(0.1F)), PartPose.ZERO);
        rightarm.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(64, 17).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
        rightarm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(81, 17).addBox(-4.5F, -3.3F, -3.5F, 6F, 3F, 7F, new CubeDeformation(-0.1F)), PartPose.ZERO);
        rightarm.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 34).addBox(-4F, -4F, -3F, 5F, 1F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
        rightarm.addOrReplaceChild("gauntlet", CubeListBuilder.create().texOffs(23, 34).addBox(-4.5F, 5.6F, -3.5F, 7F, 4F, 7F, new CubeDeformation(-0.1F)), PartPose.ZERO);
        rightarm.addOrReplaceChild("palm", CubeListBuilder.create().texOffs(52, 34).addBox(-2.5F, 10.7F, -1.5F, 3F, 1F, 3F, new CubeDeformation(-0.2F)), PartPose.ZERO);
        rightleg.addOrReplaceChild("boot", CubeListBuilder.create().texOffs(65, 34).addBox(-2F, 6F, -2F, 4F, 6F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
        rightleg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(82, 34).addBox(-2.6F, 3.8F, -3.3F, 5F, 4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        rightleg.addOrReplaceChild("sole", CubeListBuilder.create().texOffs(95, 34).addBox(-2.5F, 12.6F, -2.5F, 5F, 1F, 5F, new CubeDeformation(-0.2F)), PartPose.ZERO);
        leftarm.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(64, 17).mirror().addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
        leftarm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(81, 17).mirror().addBox(-1.5F, -3.3F, -3.5F, 6F, 3F, 7F, new CubeDeformation(-0.1F)), PartPose.ZERO);
        leftarm.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 34).mirror().addBox(-1F, -4F, -3F, 5F, 1F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
        leftarm.addOrReplaceChild("gauntlet", CubeListBuilder.create().texOffs(23, 34).mirror().addBox(-2.5F, 5.6F, -3.5F, 7F, 4F, 7F, new CubeDeformation(-0.1F)), PartPose.ZERO);
        leftarm.addOrReplaceChild("palm", CubeListBuilder.create().texOffs(52, 34).mirror().addBox(-0.5F, 10.7F, -1.5F, 3F, 1F, 3F, new CubeDeformation(-0.2F)), PartPose.ZERO);
        leftleg.addOrReplaceChild("boot", CubeListBuilder.create().texOffs(65, 34).mirror().addBox(-2F, 6F, -2F, 4F, 6F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
        leftleg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(82, 34).mirror().addBox(-2.4F, 3.8F, -3.3F, 5F, 4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        leftleg.addOrReplaceChild("sole", CubeListBuilder.create().texOffs(95, 34).mirror().addBox(-2.5F, 12.6F, -2.5F, 5F, 1F, 5F, new CubeDeformation(-0.2F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition inner() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        PartDefinition rightarm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftarm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 9.5F, -2F, 8F, 3F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
        rightleg.addOrReplaceChild("leg", CubeListBuilder.create().texOffs(25, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
        rightleg.addOrReplaceChild("knee", CubeListBuilder.create().texOffs(42, 0).addBox(-2.5F, 5.2F, -2.95F, 5F, 2F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        leftleg.addOrReplaceChild("leg", CubeListBuilder.create().texOffs(25, 0).mirror().addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
        leftleg.addOrReplaceChild("knee", CubeListBuilder.create().texOffs(42, 0).mirror().addBox(-2.5F, 5.2F, -2.95F, 5F, 2F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
