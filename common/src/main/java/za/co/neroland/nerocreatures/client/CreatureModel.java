package za.co.neroland.nerocreatures.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Shared base for the NeroCreatures programmer-art models.
 *
 * <p>The art pass is deliberately deferred (see the roadmap), so these models are cube geometry with
 * exactly two kinds of motion, both driven from the render state and both cheap:
 *
 * <ul>
 *   <li><b>Walk swing</b> — {@link #swingLimb} registers a hip-pivoted part (pivot at the joint,
 *       cubes hanging below it) that rotates fore/aft with {@code walkAnimationPos}, scaled by
 *       {@code walkAnimationSpeed}. A registered limb's {@code xRot} is owned by the walk cycle.</li>
 *   <li><b>Idle wave</b> — {@link #idleWave} registers a sine on one part's chosen axis driven by
 *       {@code ageInTicks}, faded out as walk speed rises so it never fights the stride.</li>
 * </ul>
 *
 * <p>This is a much smaller surface than a full animation system on purpose: a model that only ever
 * writes rotations it registered up front cannot drift out of sync with its geometry, and the real
 * art pass will replace all of it.
 *
 * <p>Client-only in effect — models are never touched on a dedicated server — but the class lives in
 * {@code common/} like every other shared class, since {@code common/} is the only source set spliced
 * into all six cells.
 */
public abstract class CreatureModel extends EntityModel<LivingEntityRenderState> {

    /** Which rotation a registered idle wave drives. */
    protected enum Axis {
        X, Y, Z
    }

    private record Swing(ModelPart part, float baseXRot, float phase, float amplitude) {
    }

    private record Wave(ModelPart part, Axis axis, float base, float frequency, float phase,
            float amplitude, boolean swung, boolean fadeWithWalk) {
    }

    private final List<Swing> swings = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();

    protected CreatureModel(ModelPart root) {
        super(root);
    }

    /**
     * Registers a hip-pivoted limb to swing with the walk cycle.
     *
     * @param name      child part name from {@code createBodyLayer}
     * @param phase     phase offset in radians ({@link Mth#PI} to oppose another limb)
     * @param amplitude swing amplitude in radians
     */
    protected final void swingLimb(String name, float phase, float amplitude) {
        ModelPart part = this.root().getChild(name);
        this.swings.add(new Swing(part, part.xRot, phase, amplitude));
    }

    /**
     * Registers an idle sine on one axis of a part.
     *
     * @param name      child part name from {@code createBodyLayer}
     * @param axis      rotation axis to drive
     * @param frequency radians per tick (~0.04 slow … ~0.15 lively)
     * @param phase     phase offset in radians — stagger siblings for a ripple
     * @param amplitude amplitude in radians; keep subtle (~0.03–0.20)
     */
    protected final void idleWave(String name, Axis axis, float frequency, float phase, float amplitude) {
        wave(name, axis, frequency, phase, amplitude, true);
    }

    /**
     * As {@link #idleWave}, but with control over whether the motion fades out while walking. A
     * creature whose motion <em>is</em> its locomotion — the Asteroid Worm's undulation — passes
     * {@code false}, or it would go rigid the moment it started moving.
     */
    protected final void wave(String name, Axis axis, float frequency, float phase, float amplitude,
            boolean fadeWithWalk) {
        ModelPart part = this.root().getChild(name);
        boolean swung = this.swings.stream().anyMatch(swing -> swing.part() == part);
        float base = switch (axis) {
            case X -> part.xRot;
            case Y -> part.yRot;
            case Z -> part.zRot;
        };
        this.waves.add(new Wave(part, axis, base, frequency, phase, amplitude, swung, fadeWithWalk));
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float walkPos = state.walkAnimationPos;
        float walkSpeed = Math.min(1.0F, state.walkAnimationSpeed);
        for (Swing swing : this.swings) {
            swing.part().xRot = swing.baseXRot()
                    + Mth.cos(walkPos * 0.6662F + swing.phase()) * swing.amplitude() * walkSpeed;
        }
        float idle = 1.0F - walkSpeed;
        for (Wave wave : this.waves) {
            float strength = wave.fadeWithWalk() ? idle : 1.0F;
            float delta = Mth.sin(state.ageInTicks * wave.frequency() + wave.phase())
                    * wave.amplitude() * strength;
            ModelPart part = wave.part();
            switch (wave.axis()) {
                // X is the walk axis: stack on the walk pose for a swung limb (its xRot was just set
                // absolutely above), pose absolutely otherwise.
                case X -> part.xRot = (wave.swung() ? part.xRot : wave.base()) + delta;
                case Y -> part.yRot = wave.base() + delta;
                case Z -> part.zRot = wave.base() + delta;
            }
        }
    }
}
