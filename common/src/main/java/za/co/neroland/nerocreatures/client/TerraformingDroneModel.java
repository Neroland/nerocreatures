package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Terraforming Drone — a squat chassis on stubby legs, with a seed hopper on its back and two work
 * arms that fold down in front of it. Programmer art: cube geometry only.
 *
 * <p>Visually it is deliberately the <em>opposite</em> of its hostile cousin the Rogue Drone: wide
 * and low instead of compact and quick, legs instead of rotors, arms instead of a weapon lens. A
 * player should be able to tell at a glance which machine in a crater is theirs.
 *
 * <p>The arms carry a slow non-fading sweep — the drone is always working, even while it strolls —
 * and the hopper a gentle idle bob.
 */
public class TerraformingDroneModel extends CreatureModel {

    /** Leg swing amplitude, radians. Short and mechanical. */
    private static final float LEG_SWING = 0.35F;

    /** Work-arm sweep speed, radians per tick. */
    private static final float ARM_SPEED = 0.10F;

    /** Work-arm sweep amplitude, radians. */
    private static final float ARM_SWEEP = 0.35F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public TerraformingDroneModel(ModelPart root) {
        super(root);
        swingLimb("leg_fl", 0F, LEG_SWING);
        swingLimb("leg_rr", 0F, LEG_SWING);
        swingLimb("leg_fr", Mth.PI, LEG_SWING);
        swingLimb("leg_rl", Mth.PI, LEG_SWING);
        // fadeWithWalk = false: the arms keep working while the chassis repositions.
        wave("arm_l", Axis.X, ARM_SPEED, 0F, ARM_SWEEP, false);
        wave("arm_r", Axis.X, ARM_SPEED, Mth.PI, ARM_SWEEP, false);
        idleWave("hopper", Axis.Z, 0.05F, 0F, 0.06F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("chassis",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5F, -4F, -6F, 10F, 7F, 12F)
                        // Forward sensor bar.
                        .addBox(-3F, -2F, -7F, 6F, 2F, 1F),
                PartPose.offset(0F, 17F, 0F));
        root.addOrReplaceChild("hopper",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -8F, -2F, 6F, 4F, 6F),
                PartPose.offset(0F, 17F, 0F));

        arm(root, "arm_l", -5.5F);
        arm(root, "arm_r", 5.5F);

        leg(root, "leg_fl", -3.5F, -4F);
        leg(root, "leg_fr", 3.5F, -4F);
        leg(root, "leg_rl", -3.5F, 4F);
        leg(root, "leg_rr", 3.5F, 4F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A shoulder-pivoted work arm ending in a flat spreader plate. */
    private static void arm(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-1F, 0F, -1F, 2F, 6F, 2F)
                        .addBox(-2F, 6F, -2F, 4F, 1F, 4F),
                PartPose.offset(x, 15F, -3F));
    }

    /** A hip-pivoted leg reaching the ground at {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, 0F, -1.5F, 3F, 4F, 3F),
                PartPose.offset(x, 20F, z));
    }
}
