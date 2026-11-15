package za.co.neroland.nerocreatures.client;

/**
 * The per-frame inputs of {@link CreatureRig#evaluate}: clocks, walk cycle, head angles and the synced
 * visual state. Plain Java (no game classes) so the evaluator is unit-testable; the renderer fills one
 * from the entity (1.21.1) or into the render state (1.21.2+).
 *
 * <p>Field names follow the {@code state} keys of the reference evaluator, {@code tools/art/spec.py}
 * {@code evaluate()} ({@code fromPose}, {@code fromTicks} and {@code since} are {@code from_pose},
 * {@code from_ticks} and {@code since}).
 */
public final class AnimInput {

    public static final int FLAG_SADDLED = 1;
    public static final int FLAG_PANNIERS = 1 << 1;
    public static final int FLAG_RADIANT = 1 << 2;
    public static final int FLAG_ALPHA = 1 << 3;

    /** Ticks alive, including the partial tick. */
    public double age;
    /** Vanilla walk animation position. */
    public double walkPos;
    /** Vanilla walk animation speed, 0..1 (clamped by the evaluator). */
    public double walkSpeed;
    /** Net head yaw and head pitch in degrees, for {@code look}. Not part of the reference evaluator. */
    public double headYaw;
    public double headPitch;
    /** Synced pose ordinal ({@code VisualPose}): 0 idle, 1 ability, 2 ability_2, 3 rest. */
    public int pose;
    /** Ticks since the pose began, including the partial tick. */
    public double poseTicks;
    /** Boss phase 0-3. */
    public int phase;
    /** Ticks into the hurt flinch, or negative when not hurt. */
    public double hurtTicks = -1.0;
    public int stage;
    public int variant;
    public int form;
    /** {@code FLAG_*} bits. */
    public int flags;
    /**
     * The blend source: the pose that was showing when the current one began (0 idle), and its
     * clock at that moment. Tracked client-side per entity ({@link CreatureModel#fillVisuals}).
     */
    public int fromPose;
    public double fromTicks;
    /** Ticks since the current pose began, by the client clock, or negative when unknown. */
    public double since = -1.0;

    /** Back to the defaults (idle, no walk, not hurt). */
    public AnimInput reset() {
        age = 0.0;
        walkPos = 0.0;
        walkSpeed = 0.0;
        headYaw = 0.0;
        headPitch = 0.0;
        pose = 0;
        poseTicks = 0.0;
        phase = 0;
        hurtTicks = -1.0;
        stage = 0;
        variant = 0;
        form = 0;
        flags = 0;
        fromPose = 0;
        fromTicks = 0.0;
        since = -1.0;
        return this;
    }

    public boolean flag(int flag) {
        return (flags & flag) != 0;
    }
}
