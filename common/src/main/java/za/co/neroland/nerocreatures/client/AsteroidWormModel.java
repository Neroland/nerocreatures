package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Asteroid Worm — <b>this is where the segments live</b>.
 *
 * <p>On the server the worm is a single entity with one long hitbox (see
 * {@code entity.hostile.AsteroidWorm} for why the first cut is deliberately conservative). The
 * segmented body is purely visual: a maw plus {@value #SEGMENT_COUNT} tapering ring segments laid
 * out along the entity's Z axis, each yawing on its own phase of one travelling sine. Because the
 * phases are offset by a constant, the wave visibly runs from head to tail.
 *
 * <p>The undulation deliberately does <b>not</b> fade with walk speed — a worm that goes rigid the
 * moment it starts moving would look broken, so these waves are registered with
 * {@code fadeWithWalk = false}.
 *
 * <p>Nothing here is load-bearing for gameplay: a client that draws none of it still fights the
 * same worm.
 */
public class AsteroidWormModel extends CreatureModel {

    /** Body segments behind the maw. */
    private static final int SEGMENT_COUNT = 5;

    /** Spacing between segment pivots, in model units. */
    private static final float SEGMENT_SPACING = 5.5F;

    /** Travelling-wave phase step per segment, radians. */
    private static final float WAVE_PHASE_STEP = 0.9F;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public AsteroidWormModel(ModelPart root) {
        super(root);
        // One travelling wave from maw to tail: constant frequency, phase stepped per segment.
        wave("maw", Axis.Y, 0.18F, 0F, 0.10F, false);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float amplitude = 0.16F + 0.03F * i;
            wave("segment_" + i, Axis.Y, 0.18F, (i + 1) * WAVE_PHASE_STEP, amplitude, false);
            // A gentle vertical roll on top, half the rate, so the body writhes rather than snakes
            // flat along the ground.
            wave("segment_" + i, Axis.X, 0.09F, (i + 1) * WAVE_PHASE_STEP, 0.07F, false);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The maw: a blunt drilling head with a jaw plate under it.
        root.addOrReplaceChild("maw",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-4.5F, -4.5F, -9F, 9F, 9F, 9F)
                        .addBox(-3F, 3F, -11F, 6F, 3F, 3F),
                PartPose.offset(0F, 17F, -11F));

        // Tapering ring segments running back along Z.
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            // Whole-number box sizes (8, 7, 6, 5, 4) so the generated UV grid stays on pixel edges.
            float half = 4.0F - 0.5F * i;
            root.addOrReplaceChild("segment_" + i,
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-half, -half, -2.5F, half * 2F, half * 2F, 5F),
                    PartPose.offset(0F, 17F, -6F + SEGMENT_SPACING * i));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }
}
