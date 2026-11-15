package za.co.neroland.nerocreatures.entity.wrecks;

/**
 * Pure rules for the {@link StaticSwarm} and its bottled form, the {@code static_jar}: the drain pulse
 * and its caps, the bottling threshold, the overworld storm rule (decision 005), and the thrown
 * jar's arc and burst caps. No Minecraft types; unit-tested in {@code SwarmRulesTest}.
 */
public final class SwarmRules {

    // --- drain -----------------------------------------------------------------------------

    /** Ticks between drain pulses. */
    public static final int PULSE_TICKS = 40;
    /** Drain reach, in blocks, from the swarm's core. */
    public static final double DRAIN_RADIUS = 4.0D;
    /** At most this many players are drained per pulse. */
    public static final int MAX_PLAYERS = 3;
    /** At most this many charge units are drained from one player per pulse. */
    public static final int MAX_UNITS_PER_PLAYER = 1;
    /** Health the swarm regains per unit drained. */
    public static final float HEAL_PER_UNIT = 2.0F;
    /** The {@code ability} pose (rings contract, then burst) while a pulse drains something. */
    public static final int DRAIN_POSE_TICKS = 20;

    // --- shock (its only attack) -------------------------------------------------------------

    /** Contact shock range, in blocks. */
    public static final double SHOCK_RANGE = 1.8D;
    /** Ticks between shocks. */
    public static final int SHOCK_INTERVAL_TICKS = 20;

    // --- bottling ---------------------------------------------------------------------------

    /** A swarm can be bottled once it is at or below this fraction of its health. */
    public static final float BOTTLE_HEALTH_FRACTION = 0.5F;

    // --- overworld (decision 005) -----------------------------------------------------------

    /** How often an overworld swarm checks the storm and the light. */
    public static final int STORM_CHECK_TICKS = 20;
    /** In the overworld a swarm never stays in block light at or above this. */
    public static final int OVERWORLD_LIGHT_LIMIT = 10;
    /** Per-attempt chance of the overworld storm rule. */
    public static final double STORM_CHANCE = 0.10D;
    /** Hard cap: no new swarm where this many already drift within {@link #AREA_RADIUS} blocks. */
    public static final int MAX_PER_AREA = 2;
    /** The hard-cap area, in blocks. */
    public static final double AREA_RADIUS = 32.0D;

    // --- static jar ------------------------------------------------------------------------

    /** Burst radius of a thrown jar, in blocks. */
    public static final double JAR_RADIUS = 5.0D;
    /** How long a burst disables a mechanical creature (5 s). */
    public static final int JAR_DISABLE_TICKS = 100;
    /** At most this many mechanical creatures are disabled by one burst. */
    public static final int MAX_JAR_TARGETS = 12;
    /** Use cooldown of the jar item. */
    public static final int JAR_COOLDOWN_TICKS = 20;
    /** Launch speed of a jar, blocks per tick. */
    public static final double JAR_SPEED = 1.2D;
    /** Gravity per tick on a thrown jar (as a vanilla thrown item). */
    public static final double JAR_GRAVITY = 0.05D;
    /** Air drag per tick on a thrown jar. */
    public static final double JAR_DRAG = 0.99D;
    /** The arc is followed for at most this many ticks before it bursts in mid-air. */
    public static final int JAR_MAX_STEPS = 40;

    private SwarmRules() {
    }

    /** Whether the drain runs at all ({@code staticSwarmDrain}). Off: the swarm only shocks. */
    public static boolean drainEnabled(boolean keyOn) {
        return keyOn;
    }

    /** The first {@code min(found, cap)} of a bounded query; never negative. */
    public static int take(int found, int cap) {
        return Math.max(0, Math.min(found, cap));
    }

    /**
     * The built-in drain for a stack with no energy handler: a {@code plasma_cell} is a charged cell,
     * so one cell per unit is consumed. Anything else drains nothing without a handler.
     *
     * @return units drained, at most {@code wanted} and at most the stack size
     */
    public static int builtInDrain(boolean isPlasmaCell, int stackCount, int wanted) {
        if (!isPlasmaCell || stackCount <= 0 || wanted <= 0) {
            return 0;
        }
        return Math.min(stackCount, wanted);
    }

    /** Whether a swarm is weak enough to bottle. */
    public static boolean mayBottle(float health, float maxHealth) {
        return maxHealth > 0.0F && health > 0.0F && health <= maxHealth * BOTTLE_HEALTH_FRACTION;
    }

    /**
     * Decision 005: an overworld swarm discards itself once the thunderstorm ends, unless it is in a
     * station or wreck habitat. Outside the overworld it never does.
     */
    public static boolean vanishes(boolean overworld, boolean thundering, boolean wreckHabitat) {
        return overworld && !thundering && !wreckHabitat;
    }

    /** Decision 005: in the overworld the swarm is a storm nuisance and never enters block light 10+. */
    public static boolean tooBright(boolean overworld, int blockLight) {
        return overworld && blockLight >= OVERWORLD_LIGHT_LIMIT;
    }

    /** The overworld rule's spot: a thunderstorm, open sky, and the spot below the light limit. */
    public static boolean stormSpot(boolean thundering, boolean canSeeSky, int blockLight) {
        return thundering && canSeeSky && blockLight < OVERWORLD_LIGHT_LIMIT;
    }

    /** A uniform roll in {@code [0, 1)} passes a chance. */
    public static boolean roll(double uniform, double chance) {
        return uniform < chance;
    }

    /** The hard area cap. */
    public static boolean roomInArea(int swarmsNearby) {
        return swarmsNearby < MAX_PER_AREA;
    }

    /**
     * One tick of a thrown jar's arc, in place: position advances by velocity, then velocity takes
     * drag and gravity (the vanilla thrown-item order).
     *
     * @param pos {x, y, z}, advanced
     * @param vel {vx, vy, vz}, updated
     */
    public static void step(double[] pos, double[] vel) {
        pos[0] += vel[0];
        pos[1] += vel[1];
        pos[2] += vel[2];
        vel[0] *= JAR_DRAG;
        vel[1] = vel[1] * JAR_DRAG - JAR_GRAVITY;
        vel[2] *= JAR_DRAG;
    }

    /** Launch velocity from a unit look vector. */
    public static double[] launch(double lookX, double lookY, double lookZ) {
        double len = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (len < 1.0E-6D) {
            return new double[] {0.0D, 0.0D, 0.0D};
        }
        double s = JAR_SPEED / len;
        return new double[] {lookX * s, lookY * s, lookZ * s};
    }
}
