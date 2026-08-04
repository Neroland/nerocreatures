package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Xertz Forager — a low four-legged grazer with a quartz crest along its back and a blunt snout it
 * roots with. Programmer art: cube geometry only.
 *
 * <p>The gait is the opposite of the Crystal Golem's: short body, quick legs, a big swing amplitude,
 * so even as untextured cubes it reads as something small and busy. Diagonal pairs swing together
 * the way a real quadruped's do, and the crest carries a slow idle shimmer that fades out while it
 * is trotting.
 */
public class XertzForagerModel extends CreatureModel {

    /** Leg swing amplitude, radians. Generous: it is a scurrier. */
    private static final float LEG_SWING = 0.9F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public XertzForagerModel(ModelPart root) {
        super(root);
        // Diagonal pairs share a phase, which is what makes a four-legged walk read as a walk.
        swingLimb("leg_fl", 0F, LEG_SWING);
        swingLimb("leg_rr", 0F, LEG_SWING);
        swingLimb("leg_fr", Mth.PI, LEG_SWING);
        swingLimb("leg_rl", Mth.PI, LEG_SWING);
        idleWave("head", Axis.X, 0.09F, 0F, 0.14F);
        idleWave("crest", Axis.Z, 0.06F, 0.8F, 0.08F);
        idleWave("tail", Axis.Y, 0.12F, 0F, 0.30F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -6F, 6F, 6F, 12F),
                PartPose.offset(0F, 15F, 0F));
        // The crest: three quartz blades of falling height, the feature the perk is named for.
        root.addOrReplaceChild("crest",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1F, -6F, -4F, 2F, 3F, 2F)
                        .addBox(-1F, -5.5F, -1F, 2F, 3F, 2F)
                        .addBox(-1F, -5F, 2F, 2F, 2F, 2F),
                PartPose.offset(0F, 15F, 0F));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-2.5F, -2.5F, -4F, 5F, 5F, 4F)
                        // Blunt rooting snout.
                        .addBox(-1.5F, -1F, -6F, 3F, 2F, 2F),
                PartPose.offset(0F, 14F, -6F));
        root.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 32).addBox(-1F, -1F, 0F, 2F, 2F, 5F),
                PartPose.offset(0F, 14F, 6F));

        leg(root, "leg_fl", -2F, -4F);
        leg(root, "leg_fr", 2F, -4F);
        leg(root, "leg_rl", -2F, 4F);
        leg(root, "leg_rr", 2F, 4F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A hip-pivoted leg reaching the ground at {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-1F, 0F, -1F, 2F, 6F, 2F),
                PartPose.offset(x, 18F, z));
    }
}
