package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Lunar Stalker — a lean quadruped built to lope: long legs, a low slung body, a forward head on a
 * short neck, and a counterweight tail. Programmer art: cube geometry only.
 *
 * <p>The legs swing in diagonal pairs (front-left with rear-right), which is the gait that reads as
 * a stalking predator rather than a horse. The tail sways on the idle sine and the head tracks with
 * it, so a pack standing still still looks like it is deciding something.
 */
public class LunarStalkerModel extends CreatureModel {

    /** Leg swing amplitude, radians. */
    private static final float LEG_SWING = 0.7F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public LunarStalkerModel(ModelPart root) {
        super(root);
        // Diagonal pairs.
        swingLimb("leg_fl", 0F, LEG_SWING);
        swingLimb("leg_rr", 0F, LEG_SWING);
        swingLimb("leg_fr", Mth.PI, LEG_SWING);
        swingLimb("leg_rl", Mth.PI, LEG_SWING);
        idleWave("tail", Axis.Y, 0.09F, 0F, 0.22F);
        idleWave("head", Axis.Y, 0.06F, 0.8F, 0.14F);
        idleWave("neck", Axis.X, 0.06F, 0.8F, 0.05F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 7F, -7F, 8F, 7F, 14F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(0, 32).addBox(-2.5F, -2F, -5F, 5F, 5F, 5F),
                PartPose.offsetAndRotation(0F, 9F, -7F, -0.25F, 0F, 0F));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-3F, -3F, -6F, 6F, 6F, 6F)
                        // Snout.
                        .addBox(-1.5F, -0.5F, -9F, 3F, 3F, 3F),
                PartPose.offset(0F, 7F, -11F));
        root.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 32).addBox(-1F, -1F, 0F, 2F, 2F, 9F),
                PartPose.offsetAndRotation(0F, 9F, 7F, -0.3F, 0F, 0F));

        leg(root, "leg_fl", -2.5F, -4.5F);
        leg(root, "leg_fr", 2.5F, -4.5F);
        leg(root, "leg_rl", -2.5F, 4.5F);
        leg(root, "leg_rr", 2.5F, 4.5F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One hip-pivoted leg: thigh, shin and a small paw, reaching the ground at {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-1.5F, 0F, -1.5F, 3F, 6F, 3F)
                        .texOffs(0, 32).addBox(-1F, 6F, -1F, 2F, 4F, 2F)
                        .texOffs(0, 32).addBox(-1.5F, 9F, -3F, 3F, 2F, 4F),
                PartPose.offset(x, 13F, z));
    }
}
