package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Crystal Golem — a heavy, faceted biped: a slab of a torso with shoulder shards, short thick legs
 * and long arms that hang. Programmer art: cube geometry only.
 *
 * <p>The gait is deliberately narrow and slow-looking — small leg swing, arms counter-swinging — so
 * that even before the animation pass a golem reads as something you can walk away from. A slow
 * shoulder-shard shimmer on the idle sine keeps it from looking switched off while it is neutral.
 */
public class CrystalGolemModel extends CreatureModel {

    /** Leg swing amplitude, radians. Short strides: it is not chasing anyone quickly. */
    private static final float LEG_SWING = 0.45F;

    /** Arm swing amplitude, radians. */
    private static final float ARM_SWING = 0.35F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public CrystalGolemModel(ModelPart root) {
        super(root);
        swingLimb("leg_l", 0F, LEG_SWING);
        swingLimb("leg_r", Mth.PI, LEG_SWING);
        // Arms oppose the leg on their own side, the way a walking biped does.
        swingLimb("arm_l", Mth.PI, ARM_SWING);
        swingLimb("arm_r", 0F, ARM_SWING);
        idleWave("head", Axis.Y, 0.04F, 0F, 0.10F);
        idleWave("shards", Axis.Z, 0.05F, 0.6F, 0.06F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7F, -12F, -4.5F, 14F, 22F, 9F),
                PartPose.offset(0F, 0F, 0F));
        // Shoulder shards: the "seam" the pickaxe bonus is about.
        root.addOrReplaceChild("shards",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-9F, -14F, -2F, 4F, 5F, 4F)
                        .addBox(5F, -14F, -2F, 4F, 5F, 4F)
                        .addBox(-2F, -16F, -1.5F, 4F, 5F, 3F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32).addBox(-4F, -8F, -4F, 8F, 8F, 8F),
                PartPose.offset(0F, -12F, 0F));

        arm(root, "arm_l", -8.5F);
        arm(root, "arm_r", 8.5F);
        leg(root, "leg_l", -3.5F);
        leg(root, "leg_r", 3.5F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A shoulder-pivoted arm, hanging to roughly hip height. */
    private static void arm(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-2.5F, -1F, -2.5F, 5F, 17F, 5F),
                PartPose.offset(x, -9F, 0F));
    }

    /** A hip-pivoted leg reaching the ground at {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-2.5F, 0F, -2.5F, 5F, 14F, 5F),
                PartPose.offset(x, 10F, 0F));
    }
}
