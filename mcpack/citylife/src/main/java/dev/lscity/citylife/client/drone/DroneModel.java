package dev.lscity.citylife.client.drone;

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
 * Модель дрона: корпус, подвес камеры, четыре луча с моторами и винтами.
 * Собрана citylife/tools/gen_drones.py вместе с текстурами.
 */
@OnlyIn(Dist.CLIENT)
public final class DroneModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation("citylife", "drone"), "main");

    private DroneModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -2F, -4F, 6F, 2F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
        body.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(29, 0).addBox(-2F, -3F, -3F, 4F, 1F, 5F, new CubeDeformation(0F)), PartPose.ZERO);
        body.addOrReplaceChild("battery", CubeListBuilder.create().texOffs(48, 0).addBox(-2F, 0F, -1F, 4F, 1F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
        body.addOrReplaceChild("gimbal", CubeListBuilder.create().texOffs(0, 11).addBox(-1F, 0F, -5F, 2F, 2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
        body.addOrReplaceChild("led_front", CubeListBuilder.create().texOffs(9, 11).addBox(-2.5F, -1.6F, -4.3F, 5F, 1F, 1F, new CubeDeformation(-0.3F)), PartPose.ZERO);
        body.addOrReplaceChild("led_back", CubeListBuilder.create().texOffs(22, 11).addBox(-2.5F, -1.6F, 3.3F, 5F, 1F, 1F, new CubeDeformation(-0.3F)), PartPose.ZERO);
        body.addOrReplaceChild("skid_r", CubeListBuilder.create().texOffs(35, 11).addBox(-3.5F, 0F, -3F, 1F, 2F, 6F, new CubeDeformation(-0.2F)), PartPose.ZERO);
        body.addOrReplaceChild("skid_l", CubeListBuilder.create().texOffs(50, 11).addBox(2.5F, 0F, -3F, 1F, 2F, 6F, new CubeDeformation(-0.2F)), PartPose.ZERO);
        PartDefinition arm0 = body.addOrReplaceChild("arm0", CubeListBuilder.create(), PartPose.rotation(0F, 0.79F, 0F));
        arm0.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -1.5F, 0F, 1F, 1F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        arm0.addOrReplaceChild("motor", CubeListBuilder.create().texOffs(17, 20).addBox(-1F, -2.5F, 6F, 2F, 2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rotor0 = arm0.addOrReplaceChild("rotor", CubeListBuilder.create(), PartPose.offset(0F, -2.6F, 7F));
        rotor0.addOrReplaceChild("blade_a", CubeListBuilder.create().texOffs(26, 20).addBox(-3.5F, 0F, -0.5F, 7F, 0F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        rotor0.addOrReplaceChild("blade_b", CubeListBuilder.create().texOffs(43, 20).addBox(-0.5F, 0F, -3.5F, 1F, 0F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition arm1 = body.addOrReplaceChild("arm1", CubeListBuilder.create(), PartPose.rotation(0F, 2.36F, 0F));
        arm1.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -1.5F, 0F, 1F, 1F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        arm1.addOrReplaceChild("motor", CubeListBuilder.create().texOffs(17, 20).addBox(-1F, -2.5F, 6F, 2F, 2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rotor1 = arm1.addOrReplaceChild("rotor", CubeListBuilder.create(), PartPose.offset(0F, -2.6F, 7F));
        rotor1.addOrReplaceChild("blade_a", CubeListBuilder.create().texOffs(26, 20).addBox(-3.5F, 0F, -0.5F, 7F, 0F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        rotor1.addOrReplaceChild("blade_b", CubeListBuilder.create().texOffs(43, 20).addBox(-0.5F, 0F, -3.5F, 1F, 0F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition arm2 = body.addOrReplaceChild("arm2", CubeListBuilder.create(), PartPose.rotation(0F, 3.93F, 0F));
        arm2.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -1.5F, 0F, 1F, 1F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        arm2.addOrReplaceChild("motor", CubeListBuilder.create().texOffs(17, 20).addBox(-1F, -2.5F, 6F, 2F, 2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rotor2 = arm2.addOrReplaceChild("rotor", CubeListBuilder.create(), PartPose.offset(0F, -2.6F, 7F));
        rotor2.addOrReplaceChild("blade_a", CubeListBuilder.create().texOffs(26, 20).addBox(-3.5F, 0F, -0.5F, 7F, 0F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        rotor2.addOrReplaceChild("blade_b", CubeListBuilder.create().texOffs(43, 20).addBox(-0.5F, 0F, -3.5F, 1F, 0F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition arm3 = body.addOrReplaceChild("arm3", CubeListBuilder.create(), PartPose.rotation(0F, 5.5F, 0F));
        arm3.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -1.5F, 0F, 1F, 1F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        arm3.addOrReplaceChild("motor", CubeListBuilder.create().texOffs(17, 20).addBox(-1F, -2.5F, 6F, 2F, 2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rotor3 = arm3.addOrReplaceChild("rotor", CubeListBuilder.create(), PartPose.offset(0F, -2.6F, 7F));
        rotor3.addOrReplaceChild("blade_a", CubeListBuilder.create().texOffs(26, 20).addBox(-3.5F, 0F, -0.5F, 7F, 0F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
        rotor3.addOrReplaceChild("blade_b", CubeListBuilder.create().texOffs(43, 20).addBox(-0.5F, 0F, -3.5F, 1F, 0F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
