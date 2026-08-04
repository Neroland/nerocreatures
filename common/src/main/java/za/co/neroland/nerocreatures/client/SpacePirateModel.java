package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Space Pirate — a humanoid on vanilla biped proportions (8×8×8 head, 8×12×4 body, 4×12×4 limbs),
 * plus a backpack rig so a pirate is not mistaken for a player at distance. Programmer art: cube
 * geometry only.
 *
 * <p>The arms sit in a slight forward pose and counter-swing with the legs, which is the humanoid
 * read the plan asks for. <b>Held equipment is not drawn yet</b>: rendering an item in hand needs
 * vanilla's {@code HumanoidModel}/{@code ItemInHandLayer} pair, and this mod's single
 * {@link CreatureRenderer} deliberately keeps one dumb render state for every creature. A pirate's
 * kit is fully functional and fully droppable regardless — it is only invisible until the real art
 * pass replaces these models.
 */
public class SpacePirateModel extends CreatureModel {

    /** Leg swing amplitude, radians. */
    private static final float LEG_SWING = 0.75F;

    /** Arm swing amplitude, radians. */
    private static final float ARM_SWING = 0.65F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public SpacePirateModel(ModelPart root) {
        super(root);
        swingLimb("leg_l", 0F, LEG_SWING);
        swingLimb("leg_r", Mth.PI, LEG_SWING);
        swingLimb("arm_l", Mth.PI, ARM_SWING);
        swingLimb("arm_r", 0F, ARM_SWING);
        idleWave("head", Axis.Y, 0.07F, 0F, 0.18F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F),
                PartPose.offset(0F, 0F, 0F));
        // Air/void rig on the back — the silhouette that says "not a player".
        root.addOrReplaceChild("pack",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3F, 1F, 2F, 6F, 8F, 3F)
                        .addBox(-1F, -1F, 3F, 2F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-4F, -8F, -4F, 8F, 8F, 8F)
                        // Visor brow.
                        .addBox(-4F, -6F, -4.6F, 8F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));

        arm(root, "arm_l", -5F);
        arm(root, "arm_r", 5F);
        leg(root, "leg_l", -2F);
        leg(root, "leg_r", 2F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A shoulder-pivoted arm, posed slightly forward as though carrying something. */
    private static void arm(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -2F, -2F, 4F, 12F, 4F),
                PartPose.offsetAndRotation(x, 2F, 0F, -0.18F, 0F, 0F));
    }

    /** A hip-pivoted leg reaching the ground at {@code y=24}. */
    private static void leg(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-2F, 0F, -2F, 4F, 12F, 4F),
                PartPose.offset(x, 12F, 0F));
    }
}
