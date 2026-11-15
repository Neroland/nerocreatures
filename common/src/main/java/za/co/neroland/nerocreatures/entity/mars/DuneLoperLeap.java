package za.co.neroland.nerocreatures.entity.mars;

/**
 * The Dune Loper's bounding leap, as pure numbers so the rule and its caps are testable without a
 * world. {@link DuneLoper} feeds it the jump-bar strength and the gravity attribute.
 *
 * <ul>
 *   <li>The leap is a forward bound: it always carries the mount forward along its facing, harder
 *       with a fuller jump bar. Height and reach are both capped.</li>
 *   <li>For {@value #LOW_GRAVITY_WINDOW_TICKS} ticks after take-off (or until it lands) the mount
 *       falls at {@value #LOW_GRAVITY_FACTOR} of its normal gravity: each airborne tick hands back
 *       the rest of that tick's gravity as lift. The window is a hard cap, so the leap can never
 *       become flight.</li>
 *   <li>No fall damage is taken for the same window plus a short landing grace.</li>
 * </ul>
 */
public final class DuneLoperLeap {

    /** Ticks of low gravity after take-off; the hard cap on any one leap's hang time. */
    public static final int LOW_GRAVITY_WINDOW_TICKS = 40;

    /** Extra ticks after the window during which fall distance is still forgiven. */
    public static final int LANDING_GRACE_TICKS = 20;

    /** Fraction of normal gravity felt during the window. */
    public static final double LOW_GRAVITY_FACTOR = 0.35D;

    /** Vertical speed of a tap and of a full bar (blocks per tick). */
    public static final double MIN_UP = 0.55D;
    public static final double MAX_UP = 0.95D;

    /** Forward speed of a tap and of a full bar (blocks per tick). */
    public static final double MIN_FORWARD = 0.45D;
    public static final double MAX_FORWARD = 1.25D;

    /** How long the leap pose plays: crouch telegraph, extension, tucked landing. */
    public static final int LEAP_POSE_TICKS = 16;

    private DuneLoperLeap() {
    }

    /** Clamps a jump-bar strength into 0..1 (NaN reads as 0). */
    public static double clampStrength(double strength) {
        if (Double.isNaN(strength) || strength <= 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, strength);
    }

    /** Take-off vertical speed for a jump-bar strength (0..1), capped at {@link #MAX_UP}. */
    public static double upwardSpeed(double strength) {
        double s = clampStrength(strength);
        return MIN_UP + (MAX_UP - MIN_UP) * s;
    }

    /**
     * Take-off forward speed. Backing up ({@code forwardInput < 0}) gives a short hop; otherwise a
     * full bound along the facing. Capped at {@link #MAX_FORWARD}.
     */
    public static double forwardSpeed(double strength, double forwardInput) {
        double s = clampStrength(strength);
        double speed = MIN_FORWARD + (MAX_FORWARD - MIN_FORWARD) * s;
        return forwardInput < 0.0D ? speed * 0.25D : speed;
    }

    /**
     * The lift added back on one airborne tick of the window: the share of gravity the mount does
     * not feel. Zero outside the window, on the ground, or for a non-positive gravity.
     */
    public static double liftPerTick(double gravity, int windowTicksLeft, boolean onGround) {
        if (windowTicksLeft <= 0 || onGround || !(gravity > 0.0D)) {
            return 0.0D;
        }
        return gravity * (1.0D - LOW_GRAVITY_FACTOR);
    }

    /** The window after one more tick: counts down, ends on landing (after the take-off tick). */
    public static int nextWindow(int windowTicksLeft, boolean onGround, int ticksSinceTakeOff) {
        if (windowTicksLeft <= 0) {
            return 0;
        }
        if (onGround && ticksSinceTakeOff > 2) {
            return 0;
        }
        return Math.min(LOW_GRAVITY_WINDOW_TICKS, windowTicksLeft - 1);
    }

    /** Whether fall distance is still forgiven this many ticks after take-off. */
    public static boolean fallForgiven(int ticksSinceTakeOff) {
        return ticksSinceTakeOff >= 0 && ticksSinceTakeOff <= LOW_GRAVITY_WINDOW_TICKS + LANDING_GRACE_TICKS;
    }
}
