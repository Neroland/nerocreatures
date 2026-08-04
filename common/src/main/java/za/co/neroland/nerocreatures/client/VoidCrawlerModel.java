package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Void Crawler — a low, wide six-legged body with a forward-thrust head. Programmer art: cube
 * geometry only.
 *
 * <p>The legs are hip-pivoted (pivot at the shoulder, cubes hanging to the ground at {@code y=24})
 * and swing in two opposed tripods, which is what makes it skitter rather than shuffle. The head
 * carries a slow idle sway so a waiting crawler still reads as alive.
 */
public class VoidCrawlerModel extends CreatureModel {

    /** Leg swing amplitude, radians. */
    private static final float LEG_SWING = 0.55F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public VoidCrawlerModel(ModelPart root) {
        super(root);
        // Two opposed tripods: left-front/right-middle/left-rear against their mirrors.
        swingLimb("leg_l0", 0F, LEG_SWING);
        swingLimb("leg_r1", 0F, LEG_SWING);
        swingLimb("leg_l2", 0F, LEG_SWING);
        swingLimb("leg_r0", Mth.PI, LEG_SWING);
        swingLimb("leg_l1", Mth.PI, LEG_SWING);
        swingLimb("leg_r2", Mth.PI, LEG_SWING);
        idleWave("head", Axis.Y, 0.07F, 0F, 0.12F);
        idleWave("abdomen", Axis.X, 0.05F, 1.2F, 0.06F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("thorax",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 15F, -6F, 8F, 5F, 10F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -2.5F, 0F, 6F, 5F, 6F),
                PartPose.offset(0F, 17.5F, 4F));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3F, -2.5F, -5F, 6F, 5F, 5F),
                PartPose.offset(0F, 17F, -6F));

        // Three leg pairs down the thorax; each is one hip-pivoted part reaching the ground.
        float[] legZ = {-4F, 0F, 4F};
        for (int i = 0; i < legZ.length; i++) {
            leg(root, "leg_l" + i, -4F, legZ[i], 0.35F);
            leg(root, "leg_r" + i, 4F, legZ[i], -0.35F);
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One hip-pivoted leg: upper segment splayed outward, lower segment down to {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x, float z, float roll) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-1F, 0F, -1F, 2F, 4F, 2F)
                        .texOffs(0, 32).addBox(-1F, 4F, -1F, 2F, 5F, 2F),
                PartPose.offsetAndRotation(x, 19F, z, 0F, 0F, roll));
    }
}
