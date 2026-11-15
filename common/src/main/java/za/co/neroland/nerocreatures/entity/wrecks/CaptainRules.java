package za.co.neroland.nerocreatures.entity.wrecks;

/**
 * Pure rules for the {@code PirateCaptain}: the jet-dash, the rally, the escort and the
 * {@code captains_ledger} stash mark. No Minecraft types; unit-tested in {@code CaptainRulesTest}.
 */
public final class CaptainRules {

    /** Jet-dash only toward a target at least this far away... */
    public static final double DASH_MIN = 5.0D;
    /** ...and at most this far. */
    public static final double DASH_MAX = 16.0D;
    /** Nacelles flare for this long before the launch (the telegraph). */
    public static final int DASH_TELEGRAPH_TICKS = 12;
    /** The {@code ability} pose: telegraph plus the dash itself. */
    public static final int DASH_POSE_TICKS = 20;
    /** Ticks between dashes. */
    public static final int DASH_COOLDOWN_TICKS = 100;
    /** Horizontal launch speed. */
    public static final double DASH_SPEED = 1.4D;
    /** Upward kick of a dash. */
    public static final double DASH_LIFT = 0.25D;

    /** Ticks between rallies while it has a target. */
    public static final int RALLY_INTERVAL_TICKS = 160;
    /** Rally reach, in blocks. */
    public static final double RALLY_RADIUS = 16.0D;
    /** At most this many pirates rallied at once. */
    public static final int MAX_RALLIED = 6;
    /** How long the rally buff lasts. */
    public static final int RALLY_BUFF_TICKS = 200;
    /** The {@code ability_2} pose (arm raised). */
    public static final int RALLY_POSE_TICKS = 20;

    /** Pirates that come with a naturally spawned captain. */
    public static final int ESCORT_COUNT = 2;

    /** The stash lies at least this far from where the captain fell... */
    public static final int STASH_MIN = 96;
    /** ...and at most this far. */
    public static final int STASH_MAX = 256;
    /** The holder opens the stash within this many blocks (horizontal). */
    public static final double REDEEM_RADIUS = 6.0D;
    /** Guards that appear when a stash is opened. */
    public static final int STASH_GUARDS = 2;

    private static final String[] DIRECTIONS = {
            "south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};

    private CaptainRules() {
    }

    /** Whether a target distance is in the dash band and the dash is off cooldown. */
    public static boolean mayDash(double distance, int ticksSinceDash, boolean onGround) {
        return onGround && ticksSinceDash >= DASH_COOLDOWN_TICKS && distance >= DASH_MIN && distance <= DASH_MAX;
    }

    /** Dash velocity {vx, vy, vz} toward a horizontal offset; zero offset gives a straight hop. */
    public static double[] dashVelocity(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6D) {
            return new double[] {0.0D, DASH_LIFT, 0.0D};
        }
        return new double[] {dx / len * DASH_SPEED, DASH_LIFT, dz / len * DASH_SPEED};
    }

    /** Whether a rally is due. */
    public static boolean rallyDue(boolean hasTarget, int ticksSinceRally) {
        return hasTarget && ticksSinceRally >= RALLY_INTERVAL_TICKS;
    }

    /**
     * Stash offset {dx, dz} from an angle (radians) and a 0..1 roll for distance; always within
     * [{@link #STASH_MIN}, {@link #STASH_MAX}] blocks.
     */
    public static int[] stashOffset(double angle, double distanceRoll) {
        double t = Math.max(0.0D, Math.min(1.0D, distanceRoll));
        double dist = STASH_MIN + t * (STASH_MAX - STASH_MIN);
        return new int[] {(int) Math.round(Math.cos(angle) * dist), (int) Math.round(Math.sin(angle) * dist)};
    }

    /** Whether the holder stands on the stash. */
    public static boolean atStash(double dx, double dz) {
        return dx * dx + dz * dz <= REDEEM_RADIUS * REDEEM_RADIUS;
    }

    /** Horizontal distance, rounded to a whole block. */
    public static int roundedDistance(double dx, double dz) {
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    /**
     * One of eight compass words for the direction of an offset (Minecraft axes: +z south, +x east).
     * Used as a lang key suffix.
     */
    public static String direction(double dx, double dz) {
        if (dx * dx + dz * dz < 1.0E-9D) {
            return "here";
        }
        // atan2 of (-dx, dz): 0 = south, rising clockwise when seen from above (west at 90).
        double deg = Math.toDegrees(Math.atan2(-dx, dz));
        int index = (int) Math.floorMod(Math.round(deg / 45.0D), 8L);
        return DIRECTIONS[index];
    }

    /**
     * Who receives the ledger: the player in the kill credit only (from the damage source), alive and
     * in the captain's level. Never a nearest-player fallback: otherwise nobody does.
     */
    public static boolean ledgerGoesToKiller(boolean killerIsPlayer, boolean sameLevel, boolean alive) {
        return killerIsPlayer && sameLevel && alive;
    }
}
