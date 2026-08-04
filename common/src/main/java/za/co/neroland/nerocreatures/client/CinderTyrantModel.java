package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Cinder Tyrant — the boss silhouette: a slab of a torso, a head sunk between two slag shoulders, a
 * crown of vent spikes, and arms long enough to reach the ground. Programmer art: cube geometry
 * only, deliberately over-scaled so the thing reads as a boss from across an arena rather than as a
 * large monster up close.
 *
 * <p>Everything visible about the fight's <em>state</em> — the phase, the enrage, the wind-up before
 * a slam — is communicated with sound and particles from the server, not with model state. That is
 * what keeps the boss on the same single shared render state as every other creature in the mod and
 * out of the network entirely.
 */
public class CinderTyrantModel extends CreatureModel {

    /** Leg swing amplitude, radians. Slow, ground-shaking strides. */
    private static final float LEG_SWING = 0.45F;

    /** Arm swing amplitude, radians. */
    private static final float ARM_SWING = 0.55F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public CinderTyrantModel(ModelPart root) {
        super(root);
        swingLimb("leg_l", 0F, LEG_SWING);
        swingLimb("leg_r", Mth.PI, LEG_SWING);
        swingLimb("arm_l", Mth.PI, ARM_SWING);
        swingLimb("arm_r", 0F, ARM_SWING);
        // A slow bellows heave: the thing is a furnace, and it never stops breathing.
        idleWave("body", Axis.X, 0.05F, 0F, 0.06F);
        idleWave("head", Axis.X, 0.05F, Mth.PI, 0.10F);
        // The crown vents flare out of phase with the breath.
        idleWave("crown", Axis.Z, 0.07F, 1.2F, 0.12F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        // Torso slab.
                        .addBox(-9F, -18F, -6F, 18F, 22F, 12F)
                        // Waist.
                        .addBox(-7F, 4F, -5F, 14F, 5F, 10F),
                PartPose.offset(0F, -8F, 0F));

        root.addOrReplaceChild("shoulders",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-15F, -20F, -7F, 6F, 9F, 14F)
                        .addBox(9F, -20F, -7F, 6F, 9F, 14F),
                PartPose.offset(0F, -8F, 0F));

        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-5F, -8F, -5F, 10F, 8F, 10F)
                        // Jaw.
                        .addBox(-4F, -2F, -6F, 8F, 3F, 2F),
                PartPose.offset(0F, -26F, 0F));

        root.addOrReplaceChild("crown",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-6F, -13F, -1F, 2F, 6F, 2F)
                        .addBox(-2F, -15F, -1F, 2F, 7F, 2F)
                        .addBox(2F, -15F, -1F, 2F, 7F, 2F)
                        .addBox(4F, -13F, -1F, 2F, 6F, 2F),
                PartPose.offset(0F, -26F, 0F));

        arm(root, "arm_l", -12F);
        arm(root, "arm_r", 12F);
        leg(root, "leg_l", -5F);
        leg(root, "leg_r", 5F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A shoulder-pivoted arm, long enough to drag its fist along the ground. */
    private static void arm(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-3.5F, -3F, -3.5F, 7F, 21F, 7F)
                        // Fist.
                        .addBox(-4.5F, 17F, -4.5F, 9F, 6F, 9F),
                PartPose.offset(x, -22F, 0F));
    }

    /** A hip-pivoted leg reaching the ground at {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-4F, 0F, -4F, 8F, 12F, 8F)
                        // Foot.
                        .addBox(-4.5F, 12F, -6F, 9F, 4F, 11F),
                PartPose.offset(x, 8F, 0F));
    }
}
