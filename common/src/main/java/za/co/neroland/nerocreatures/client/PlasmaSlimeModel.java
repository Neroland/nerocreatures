package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Plasma Slime — a blob with a visible charged core. Programmer art: cube geometry only.
 *
 * <p>The outer shell is one cube; the core is a smaller cube inside it that tumbles slowly on two
 * axes, which is what sells "there is something live in there" without a shader or a glow layer.
 *
 * <p>There is deliberately <b>no size handling here</b>. A split slime is smaller because its
 * {@code minecraft:scale} attribute is smaller, and vanilla applies that attribute to the hitbox and
 * the rendered model alike — so the model, the renderer and the network stay ignorant of size
 * entirely.
 */
public class PlasmaSlimeModel extends CreatureModel {

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public PlasmaSlimeModel(ModelPart root) {
        super(root);
        // The core tumbles; the shell wobbles a fraction of that, a beat behind.
        idleWave("core", Axis.Y, 0.10F, 0F, 0.55F);
        idleWave("core", Axis.X, 0.07F, 1.4F, 0.30F);
        idleWave("shell", Axis.Z, 0.06F, 0.7F, 0.05F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("shell",
                CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -6F, -6F, 12F, 12F, 12F),
                PartPose.offset(0F, 18F, 0F));
        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3F, -3F, -3F, 6F, 6F, 6F),
                PartPose.offset(0F, 18F, 0F));
        // Two eye nubs on the leading face, so the blob has a front.
        root.addOrReplaceChild("eyes",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-3.5F, -2F, -7F, 2F, 2F, 1F)
                        .addBox(1.5F, -2F, -7F, 2F, 2F, 1F),
                PartPose.offset(0F, 18F, 0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
