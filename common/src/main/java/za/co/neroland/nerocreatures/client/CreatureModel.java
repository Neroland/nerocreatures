package za.co.neroland.nerocreatures.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
//?}
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.entity.visual.VisualsSnapshot;

/**
 * Shared base for every NeroCreatures model.
 *
 * <p><b>Generated models</b> ({@code tools/gen_entity_models.py}, from {@code art/models/<id>.json})
 * pass a {@link CreatureRig}. Each frame the rig is evaluated from the {@link AnimInput} (walk cycle,
 * age, head angles, and the synced {@code CreatureVisuals}: pose, ability ticks, phase, stage, variant,
 * form, flags) and the result is added to every part's rest pose; parts with {@code visible_when}
 * rules are shown or hidden. Parts are looked up through the tree, so nested parts animate too.
 *
 * <p><b>Pre-pipeline models</b> (the hand-written ones whose art has not landed) keep the two original
 * motions: {@link #swingLimb} (walk swing on a top-level part's x axis) and {@link #idleWave} /
 * {@link #wave} (an age sine on one axis, faded by walk speed). They are replaced, not extended, when a
 * creature's spec lands.
 *
 * <p>Client-only in effect, but it lives in {@code common/} like every shared class.
 */
//? if >=1.21.2 {
public abstract class CreatureModel extends EntityModel<CreatureRenderState> {
//?} else {
/*// 1.21.1 models are driven by the entity itself (no render state); HierarchicalModel supplies the
// root-part rendering that EntityModel(ModelPart) provides from 1.21.2 on.
public abstract class CreatureModel extends net.minecraft.client.model.HierarchicalModel<net.minecraft.world.entity.Mob> {
*///?}

    /** Which rotation a registered idle wave drives. */
    protected enum Axis {
        X, Y, Z
    }

    private record Swing(ModelPart part, float baseXRot, float phase, float amplitude) {
    }

    private record Wave(ModelPart part, Axis axis, float base, float frequency, float phase,
            float amplitude, boolean swung, boolean fadeWithWalk) {
    }

    /**
     * What each creature showed last, so a new pose can blend from it ({@code AnimInput.fromPose}).
     * Client render thread only; entries go with their entity.
     */
    private static final class PoseMemory {
        int pose = -1;
        double ticks;
        double changedAt = Double.NaN;
        int fromPose;
        double fromTicks;
    }

    private static final Map<Entity, PoseMemory> POSE_MEMORY = new WeakHashMap<>();
    /** A restarted pose shows as its clock running backwards by more than this. */
    private static final double RESTART_TICKS = 1.5;

    private final List<Swing> swings = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();

    @Nullable
    private final CreatureRig rig;
    private final ModelPart[] rigParts;
    private final double[] rot;
    private final double[] pos;

    //? if <1.21.2 {
    /*private final ModelPart rootPart;
    private final AnimInput input = new AnimInput();
    // 1.21.1 has no model-tint hook: the renderer sets this before drawing and the body draw (the
    // first renderToBuffer of the frame, before any layer) consumes it.
    private int pendingBodyTint = -1;
    *///?}

    /** A pre-pipeline model: register motion with {@link #swingLimb} and {@link #idleWave}. */
    protected CreatureModel(ModelPart root) {
        this(root, null);
    }

    /** A generated model driven by {@code rig}. */
    protected CreatureModel(ModelPart root, @Nullable CreatureRig rig) {
        //? if >=1.21.2 {
        super(root);
        //?} else {
        /*this.rootPart = root;
        *///?}
        this.rig = rig;
        int count = rig == null ? 0 : rig.partCount();
        this.rigParts = new ModelPart[count];
        if (rig != null) {
            for (int i = 0; i < count; i++) {
                int parent = rig.parentOf(i);
                this.rigParts[i] = (parent < 0 ? root : this.rigParts[parent]).getChild(rig.partName(i));
            }
        }
        this.rot = new double[count * 3];
        this.pos = new double[count * 3];
    }

    //? if <1.21.2 {
    /*@Override
    public ModelPart root() {
        return this.rootPart;
    }

    // Sets the ARGB tint for the next body draw only (1.21.1).
    void setPendingBodyTint(int argb) {
        this.pendingBodyTint = argb;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        int tint = this.pendingBodyTint;
        this.pendingBodyTint = -1;
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay,
                tint == -1 ? color : CreatureArt.multiplyArgb(color, tint));
    }
    *///?}

    /** The rig, or null for a pre-pipeline model. */
    @Nullable
    public CreatureRig rig() {
        return this.rig;
    }

    /** A part of a generated model by name, wherever it sits in the tree. */
    protected final ModelPart part(String name) {
        int index = this.rig == null ? -1 : this.rig.indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("no rig part " + name);
        }
        return this.rigParts[index];
    }

    /**
     * Registers a hip-pivoted limb to swing with the walk cycle (pre-pipeline models).
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
     * Registers an idle sine on one axis of a part (pre-pipeline models).
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

    /**
     * Copies the entity's synced visuals, hurt flinch and alpha state into {@code in}. Clocks, walk
     * and head angles are the caller's. Shared by the renderer (1.21.2+), the model and the glow
     * layer (1.21.1).
     */
    public static void fillVisuals(AnimInput in, Entity entity, float partialTick) {
        VisualsSnapshot visuals = entity instanceof CreatureVisualsHolder holder
                ? holder.visuals().snapshot(partialTick) : VisualsSnapshot.EMPTY;
        in.pose = visuals.pose().ordinal();
        in.poseTicks = visuals.abilityTicks();
        in.phase = visuals.phase();
        in.stage = visuals.stage();
        in.variant = visuals.variant();
        in.form = visuals.form();
        boolean alpha = visuals.alpha() || (entity instanceof NeroCreatureEntity creature && creature.isAlpha());
        in.flags = (visuals.saddled() ? AnimInput.FLAG_SADDLED : 0)
                | (visuals.panniers() ? AnimInput.FLAG_PANNIERS : 0)
                | (visuals.radiant() ? AnimInput.FLAG_RADIANT : 0)
                | (alpha ? AnimInput.FLAG_ALPHA : 0);
        in.hurtTicks = -1.0;
        if (entity instanceof LivingEntity living && living.hurtTime > 0) {
            in.hurtTicks = Math.max(0.0F, living.hurtDuration - living.hurtTime + partialTick);
        }
        trackPose(in, entity, entity.tickCount + partialTick);
    }

    /**
     * Fills the blend source ({@code fromPose}, {@code fromTicks}, {@code since}) from what this
     * entity showed before its current pose began, and remembers the current one. Idempotent within
     * a frame, so the several 1.21.1 callers can each call it.
     */
    static void trackPose(AnimInput in, Entity entity, double clock) {
        PoseMemory memory = POSE_MEMORY.computeIfAbsent(entity, e -> new PoseMemory());
        if (memory.pose < 0) {
            memory.pose = in.pose;
        } else if (in.pose != memory.pose || (in.pose != 0 && in.poseTicks < memory.ticks - RESTART_TICKS)) {
            memory.fromPose = memory.pose;
            memory.fromTicks = memory.ticks;
            memory.changedAt = clock;
            memory.pose = in.pose;
        }
        memory.ticks = in.poseTicks;
        in.fromPose = memory.fromPose;
        in.fromTicks = memory.fromTicks;
        in.since = Double.isNaN(memory.changedAt) ? -1.0 : Math.max(0.0, clock - memory.changedAt);
    }

    @Override
    //? if >=1.21.2 {
    public void setupAnim(CreatureRenderState state) {
        super.setupAnim(state);
        AnimInput in = state.anim;
    //?} else {
    /*public void setupAnim(net.minecraft.world.entity.Mob entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 1.21.2+ EntityModel.setupAnim resets every part to its baked pose first; do the same here.
        this.root().getAllParts().forEach(ModelPart::resetPose);
        AnimInput in = this.input.reset();
        in.age = ageInTicks;
        in.walkPos = limbSwing;
        in.walkSpeed = limbSwingAmount;
        in.headYaw = netHeadYaw;
        in.headPitch = headPitch;
        fillVisuals(in, entity, ageInTicks - entity.tickCount);
    *///?}
        animateLegacy((float) in.walkPos, Math.min(1.0F, (float) in.walkSpeed), (float) in.age);
        animateRig(in);
    }

    private void animateRig(AnimInput in) {
        CreatureRig r = this.rig;
        if (r == null) {
            return;
        }
        r.evaluate(in, this.rot, this.pos);
        boolean visibility = r.hasVisibilityRules();
        for (int i = 0; i < this.rigParts.length; i++) {
            ModelPart part = this.rigParts[i];
            int b = i * 3;
            part.xRot += (float) Math.toRadians(this.rot[b]);
            part.yRot += (float) Math.toRadians(this.rot[b + 1]);
            part.zRot += (float) Math.toRadians(this.rot[b + 2]);
            part.x += (float) this.pos[b];
            part.y += (float) this.pos[b + 1];
            part.z += (float) this.pos[b + 2];
            if (visibility) {
                part.visible = r.visible(i, in);
            }
        }
    }

    private void animateLegacy(float walkPos, float walkSpeed, float ageInTicks) {
        for (Swing swing : this.swings) {
            swing.part().xRot = swing.baseXRot()
                    + Mth.cos(walkPos * 0.6662F + swing.phase()) * swing.amplitude() * walkSpeed;
        }
        float idle = 1.0F - walkSpeed;
        for (Wave wave : this.waves) {
            float strength = wave.fadeWithWalk() ? idle : 1.0F;
            float delta = Mth.sin(ageInTicks * wave.frequency() + wave.phase())
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
