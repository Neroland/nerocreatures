package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Glacite Wisp — a floating knot of ice shards around a small bright core. Programmer art: cube
 * geometry only.
 *
 * <p>Nothing here swings with the walk cycle, because a wisp does not walk: it drifts (a fraction of
 * normal gravity, see {@code entity/tame/GlaciteWisp}). Instead the shards carry slow, staggered,
 * non-fading rotations on different axes, so the whole cluster keeps turning gently whether the wisp
 * is hovering by its owner or crossing a crater.
 */
public class GlaciteWispModel extends CreatureModel {

    /** Shard rotation speed, radians per tick. Slow: this is drifting ice, not a rotor. */
    private static final float SHARD_SPEED = 0.06F;

    /** Shard rotation amplitude, radians. */
    private static final float SHARD_SWEEP = 0.28F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public GlaciteWispModel(ModelPart root) {
        super(root);
        // fadeWithWalk = false: the drift IS the wisp's locomotion, so it must never go rigid.
        wave("shard_n", Axis.X, SHARD_SPEED, 0F, SHARD_SWEEP, false);
        wave("shard_e", Axis.Z, SHARD_SPEED, Mth.HALF_PI, SHARD_SWEEP, false);
        wave("shard_s", Axis.X, SHARD_SPEED, Mth.PI, SHARD_SWEEP, false);
        wave("shard_w", Axis.Z, SHARD_SPEED, -Mth.HALF_PI, SHARD_SWEEP, false);
        wave("core", Axis.Y, 0.05F, 0F, Mth.PI, false);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -2.5F, 5F, 5F, 5F),
                PartPose.offset(0F, 18F, 0F));

        shard(root, "shard_n", 0F, -4.5F);
        shard(root, "shard_e", 4.5F, 0F);
        shard(root, "shard_s", 0F, 4.5F);
        shard(root, "shard_w", -4.5F, 0F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One splinter of ice, pivoting about the core rather than about itself. */
    private static void shard(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 32).addBox(-1F, -3.5F, -1F, 2F, 7F, 2F),
                PartPose.offset(x, 18F, z));
    }
}
