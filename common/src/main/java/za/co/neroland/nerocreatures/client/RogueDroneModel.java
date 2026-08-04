package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Rogue Drone — a small chassis with four stubby rotor booms and a single forward lens. Programmer
 * art: cube geometry only.
 *
 * <p>Nothing here swings with the walk cycle, because the drone barely walks: its motion is the hop
 * and the long low-gravity glide (see {@code entity/mechanical/RogueDrone}). Instead the booms carry
 * a fast, non-fading yaw wave, so the rotors keep turning whether the drone is hovering in place or
 * crossing a crater.
 */
public class RogueDroneModel extends CreatureModel {

    /** Rotor wave speed, radians per tick. Fast enough to read as spinning. */
    private static final float ROTOR_SPEED = 0.5F;

    /** Rotor wave amplitude, radians. */
    private static final float ROTOR_SWEEP = 0.5F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public RogueDroneModel(ModelPart root) {
        super(root);
        // fadeWithWalk = false: the rotors are the drone's locomotion, not an idle flourish.
        wave("rotor_fl", Axis.Y, ROTOR_SPEED, 0F, ROTOR_SWEEP, false);
        wave("rotor_fr", Axis.Y, ROTOR_SPEED, Mth.PI, ROTOR_SWEEP, false);
        wave("rotor_rl", Axis.Y, ROTOR_SPEED, Mth.HALF_PI, ROTOR_SWEEP, false);
        wave("rotor_rr", Axis.Y, ROTOR_SPEED, -Mth.HALF_PI, ROTOR_SWEEP, false);
        idleWave("core", Axis.X, 0.11F, 0F, 0.08F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4F, -4F, -4F, 8F, 7F, 8F)
                        // Forward lens.
                        .addBox(-1.5F, -2F, -5F, 3F, 3F, 1F),
                PartPose.offset(0F, 17F, 0F));

        rotor(root, "rotor_fl", -4.5F, -4.5F);
        rotor(root, "rotor_fr", 4.5F, -4.5F);
        rotor(root, "rotor_rl", -4.5F, 4.5F);
        rotor(root, "rotor_rr", 4.5F, 4.5F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One boom with a flat blade on the end, pivoting about its own vertical axis. */
    private static void rotor(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-1F, -1F, -1F, 2F, 2F, 2F)
                        .texOffs(0, 32).addBox(-4F, -2F, -0.5F, 8F, 1F, 1F),
                PartPose.offset(x, 14F, z));
    }
}
