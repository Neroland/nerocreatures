package za.co.neroland.nerocreatures.entity.asteroid;

/**
 * The pure rules of the Gravity Leech tether. Unit-tested; no game types.
 *
 * <p>The tether (shared {@code TetherGoal} / {@code TetherState}) latches within
 * {@value #LATCH_RANGE} blocks and breaks on distance ({@value #BREAK_DISTANCE} blocks), damage to the
 * leech ({@value #BREAK_DAMAGE}) or time ({@value #MAX_HOLD_TICKS} ticks), then cools down
 * {@value #COOLDOWN_TICKS} ticks. While held it puts a transient {@code minecraft:gravity} modifier
 * ({@code add_multiplied_total}) on the target:
 * <ul>
 *   <li>{@link Mode#LIGHTEN}: {@value #LIGHTEN_AMOUNT} (30% gravity: long floaty jumps, slow falls);</li>
 *   <li>{@link Mode#FLIP} ({@value #FLIP_CHANCE} of latches): {@value #FLIP_AMOUNT} (gravity reversed at
 *       half strength) for the first {@value #FLIP_TICKS} ticks only, a lift of a few blocks, then
 *       {@code LIGHTEN} for the rest of the hold.</li>
 * </ul>
 * Every hold pulse renews a lease of {@value #LEASE_TICKS} ticks; a lease that is not renewed (the
 * leech stopped ticking) expires and the sweep removes the modifier, so the effect always ends on its
 * own.
 */
public final class GravityTetherRules {

    /** How the tether bends the target's gravity this latch. */
    public enum Mode {
        LIGHTEN,
        FLIP
    }

    public static final double LATCH_RANGE = 8.0D;
    public static final int PULSE_TICKS = 5;
    public static final double BREAK_DISTANCE = 12.0D;
    public static final float BREAK_DAMAGE = 6.0F;
    public static final int MAX_HOLD_TICKS = 100;
    public static final int COOLDOWN_TICKS = 120;

    public static final double LIGHTEN_AMOUNT = -0.7D;
    public static final double FLIP_AMOUNT = -1.5D;
    public static final int FLIP_TICKS = 20;
    public static final double FLIP_CHANCE = 0.35D;

    /** A lease must outlive two missed pulses before it expires. */
    public static final int LEASE_TICKS = 30;
    /** Hard cap on tracked leases (one per tethered target). */
    public static final int MAX_LEASES = 256;

    /** Wind-up (telegraph) before the latch: the latch lands at this tick of the 16-tick clip. */
    public static final int LATCH_TICK = 13;
    /** Length of the {@code ability} clip; the model clamps on its last frame while held. */
    public static final int ABILITY_CLIP_TICKS = 16;

    /** Leech hold distance while latched (the body stretches along the tether). */
    public static final double HOLD_DISTANCE = 3.5D;
    /** At most this many trail particles per pulse. */
    public static final int MAX_TRAIL_POINTS = 8;

    private GravityTetherRules() {
    }

    /** Whether a wind-up begun {@code elapsed} ticks ago may latch now. */
    public static boolean windupDone(long elapsed) {
        return elapsed >= LATCH_TICK;
    }

    /** The mode for a latch from a roll in [0, 1). */
    public static Mode chooseMode(double roll) {
        return roll < FLIP_CHANCE ? Mode.FLIP : Mode.LIGHTEN;
    }

    /** The modifier amount {@code heldTicks} into a hold. */
    public static double amount(Mode mode, long heldTicks) {
        return mode == Mode.FLIP && heldTicks >= 0 && heldTicks < FLIP_TICKS ? FLIP_AMOUNT : LIGHTEN_AMOUNT;
    }

    /** The resulting gravity, as vanilla computes {@code add_multiplied_total} on one modifier. */
    public static double effectiveGravity(double base, double amount) {
        return base * (1.0D + amount);
    }

    /** Whether a lease renewed until {@code until} has run out at {@code now}. */
    public static boolean leaseExpired(long now, long until) {
        return now >= until;
    }

    /** The lease end for a renewal at {@code now}. */
    public static long leaseUntil(long now) {
        return now + LEASE_TICKS;
    }

    /** Whether another lease may be tracked. A full table refuses new latches, never old ones. */
    public static boolean hasLeaseRoom(int tracked) {
        return tracked < MAX_LEASES;
    }

    /** Trail particles for a tether of {@code distance} blocks: one per 1.5 blocks, 2..8. */
    public static int trailPoints(double distance) {
        int points = (int) Math.ceil(Math.max(0.0D, distance) / 1.5D);
        return Math.max(2, Math.min(MAX_TRAIL_POINTS, points));
    }
}
