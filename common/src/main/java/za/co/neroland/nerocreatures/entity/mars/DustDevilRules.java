package za.co.neroland.nerocreatures.entity.mars;

/**
 * The Dust Devil's whirlwind cycle, as pure rules and numbers so the cycle, its caps and the
 * griefing fallback are testable without a world.
 *
 * <pre>
 * CALM --(target within ENGAGE_RANGE, cooldown over)--> SPIN_UP (invulnerable, telegraph)
 *      --SPIN_UP_TICKS--> WHIRL (lifts, swirls, blinds) --WHIRL_TICKS--> THROW --> CALM (cooldown)
 * </pre>
 *
 * <p>Caps: at most {@value #MAX_PLAYERS} players and {@value #MAX_ITEMS} item entities are lifted
 * at once, never higher than {@value #MAX_LIFT_HEIGHT} blocks above the devil (a short fall, not a
 * death drop), within {@value #LIFT_RADIUS} blocks. Item entities only when the griefing gate
 * ({@code dustDevilThrowsItems}, mob griefing, claims) allows it; denied, it throws players only.
 */
public final class DustDevilRules {

    public enum Phase { CALM, SPIN_UP, WHIRL, THROW }

    /** Spin-up length; doubles as the telegraph (at least 10 ticks before anything is lifted). */
    public static final int SPIN_UP_TICKS = 30;
    public static final int WHIRL_TICKS = 60;
    public static final int THROW_TICKS = 10;
    public static final int COOLDOWN_TICKS = 100;

    public static final double ENGAGE_RANGE = 6.0D;
    public static final double LIFT_RADIUS = 3.5D;
    public static final double MAX_LIFT_HEIGHT = 3.0D;
    public static final double VISIBILITY_RADIUS = 6.0D;

    public static final int MAX_PLAYERS = 4;
    public static final int MAX_ITEMS = 8;

    /** Upward speed while being lifted (blocks per tick). */
    public static final double LIFT_SPEED = 0.22D;
    /** Swirl speed around the devil (blocks per tick). */
    public static final double SWIRL_SPEED = 0.18D;
    /** Pull toward the axis, as a fraction of the horizontal offset per tick. */
    public static final double PULL = 0.08D;
    /** Throw speeds. */
    public static final double THROW_SPEED = 1.1D;
    public static final double THROW_UP = 0.35D;

    /** Blindness length refreshed during the whirl (ticks). */
    public static final int BLIND_TICKS = 40;

    private DustDevilRules() {
    }

    /** How long a phase lasts (CALM: the cooldown). */
    public static int duration(Phase phase) {
        return switch (phase) {
            case CALM -> COOLDOWN_TICKS;
            case SPIN_UP -> SPIN_UP_TICKS;
            case WHIRL -> WHIRL_TICKS;
            case THROW -> THROW_TICKS;
        };
    }

    /**
     * The phase after one more tick in {@code phase}, having spent {@code ticksInPhase} ticks in it
     * (after this tick). CALM leaves only with a target in range and the cooldown served.
     */
    public static Phase next(Phase phase, int ticksInPhase, boolean targetInRange) {
        return switch (phase) {
            case CALM -> ticksInPhase >= COOLDOWN_TICKS && targetInRange ? Phase.SPIN_UP : Phase.CALM;
            case SPIN_UP -> ticksInPhase >= SPIN_UP_TICKS ? Phase.WHIRL : Phase.SPIN_UP;
            case WHIRL -> ticksInPhase >= WHIRL_TICKS ? Phase.THROW : Phase.WHIRL;
            case THROW -> ticksInPhase >= THROW_TICKS ? Phase.CALM : Phase.THROW;
        };
    }

    /** Cannot be hit while spinning up. */
    public static boolean invulnerable(Phase phase) {
        return phase == Phase.SPIN_UP;
    }

    /** How many item entities may be lifted this whirl: the cap when allowed, none when denied. */
    public static int itemCap(boolean griefingAllowed) {
        return griefingAllowed ? MAX_ITEMS : 0;
    }

    /** At most {@code cap} of {@code available}. */
    public static int take(int available, int cap) {
        return Math.max(0, Math.min(available, cap));
    }

    /** Vertical speed for something lifted {@code heightAboveDevil} blocks: rise, then hover at the cap. */
    public static double liftVelocity(double currentVy, double heightAboveDevil) {
        if (heightAboveDevil < MAX_LIFT_HEIGHT) {
            return Math.max(currentVy, LIFT_SPEED);
        }
        return Math.min(currentVy, 0.0D);
    }

    /**
     * Horizontal velocity {@code {vx, vz}} for something at offset {@code (dx, dz)} from the axis: a
     * counter-clockwise swirl plus a pull toward the axis. Magnitude bounded by
     * {@code SWIRL_SPEED + PULL * LIFT_RADIUS}.
     */
    public static double[] swirl(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            return new double[] {0.0D, 0.0D};
        }
        double r = Math.min(len, LIFT_RADIUS);
        double ux = dx / len;
        double uz = dz / len;
        return new double[] {-uz * SWIRL_SPEED - ux * PULL * r, ux * SWIRL_SPEED - uz * PULL * r};
    }

    /** Throw velocity {@code {vx, vy, vz}}: straight out from the axis (along +x when centred). */
    public static double[] throwVelocity(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = len < 1.0E-4D ? 1.0D : dx / len;
        double uz = len < 1.0E-4D ? 0.0D : dz / len;
        return new double[] {ux * THROW_SPEED, THROW_UP, uz * THROW_SPEED};
    }
}
