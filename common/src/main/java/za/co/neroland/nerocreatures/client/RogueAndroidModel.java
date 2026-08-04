package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Rogue Android — the heavy frame: a boxy chassis, a low sensor head sunk between two shoulder
 * plates, and piston legs. Programmer art: cube geometry only.
 *
 * <p>The shoulder plates are the shield, visually — but only visually. The shield's actual state
 * lives entirely on the server (see {@code entity/mechanical/RogueAndroid}) and is communicated with
 * sound and particles, so nothing here has to be synced and the renderer keeps the single shared
 * render state every other creature uses.
 */
public class RogueAndroidModel extends CreatureModel {

    /** Leg swing amplitude, radians. Heavy, short strides. */
    private static final float LEG_SWING = 0.5F;

    /** Arm swing amplitude, radians. */
    private static final float ARM_SWING = 0.4F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public RogueAndroidModel(ModelPart root) {
        super(root);
        swingLimb("leg_l", 0F, LEG_SWING);
        swingLimb("leg_r", Mth.PI, LEG_SWING);
        swingLimb("arm_l", Mth.PI, ARM_SWING);
        swingLimb("arm_r", 0F, ARM_SWING);
        // A slow scan sweep: the sensor head never quite stops looking around.
        idleWave("head", Axis.Y, 0.06F, 0F, 0.30F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-6F, -10F, -4F, 12F, 19F, 8F)
                        // Chest housing.
                        .addBox(-3F, -6F, -5F, 6F, 6F, 1F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("plates",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-10F, -12F, -4.5F, 4F, 6F, 9F)
                        .addBox(6F, -12F, -4.5F, 4F, 6F, 9F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-4F, -6F, -4F, 8F, 6F, 8F)
                        // Sensor bar.
                        .addBox(-4F, -4F, -4.6F, 8F, 2F, 1F),
                PartPose.offset(0F, -10F, 0F));

        arm(root, "arm_l", -8F);
        arm(root, "arm_r", 8F);
        leg(root, "leg_l", -3F);
        leg(root, "leg_r", 3F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A shoulder-pivoted piston arm. */
    private static void arm(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-2F, -2F, -2F, 4F, 15F, 4F),
                PartPose.offset(x, -7F, 0F));
    }

    /** A hip-pivoted leg reaching the ground at {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-2.5F, 0F, -2.5F, 5F, 12F, 5F)
                        .addBox(-3F, 12F, -3.5F, 6F, 3F, 7F),
                PartPose.offset(x, 9F, 0F));
    }
}
