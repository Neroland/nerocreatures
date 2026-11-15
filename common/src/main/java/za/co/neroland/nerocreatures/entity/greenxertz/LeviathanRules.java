package za.co.neroland.nerocreatures.entity.greenxertz;

/**
 * The Drift Leviathan's rules as pure arithmetic, unit-tested without a world.
 *
 * <h2>The deck (how players stand on it)</h2>
 *
 * <p>No passengers and no carrying: the back is a <b>collidable top surface</b>. On 1.21.6+ the
 * leviathan answers {@code canBeCollidedWith(other)} with "only from above" ({@link #collidesFromAbove}),
 * on both sides, so a player who lands on it stands on it and one beside or below it passes freely;
 * before 1.21.6 the no-argument form makes the whole box solid. Moving a player on top of a moving
 * collider is not reliable across lines (clients own their movement), so the leviathan instead
 * <b>holds still</b> while any player is on its deck ({@link #onDeck}, re-checked every tick, held
 * {@link #STILL_TICKS} after the last sighting), the way vanilla does it for its own floating
 * platform mob. Unoccupied, it drifts slowly. Its deck therefore always moves with it, and never
 * moves under someone standing on it.
 *
 * <h2>Population</h2>
 *
 * <p>At most {@link #MAX_PER_DIMENSION} loaded per dimension (spawn gate). It is saved with its chunk
 * like any mob, barnacles and deck axis included (Stage 2 review M3: an unsaved leviathan lost its
 * barnacle progress on every unload, so barnacles were never harvestable). It never takes vanilla's
 * random despawn ({@link #mayDespawnAt}); it leaves only past vanilla's hard 128-block range, and a
 * named one is kept.
 *
 * <h2>Barnacles</h2>
 *
 * <p>A natural leviathan arrives with barnacles {@link #SEED_MIN_PERCENT}-100% grown
 * ({@link #seedBarnacles}), so a player who finds one can usually harvest soon.
 */
public final class LeviathanRules {

    public static final int MAX_PER_DIMENSION = 2;

    /** Walkable deck (art): 56 px wide, 140 px long, its top 3.0 blocks above the entity origin. */
    public static final double DECK_WIDTH = 3.5D;
    public static final double DECK_LENGTH = 8.75D;
    public static final double DECK_HEIGHT = 3.0D;

    /** A collider counts as "above" when its feet are no lower than the deck minus this. */
    public static final double DECK_EPSILON = 0.05D;
    /** How high above the deck a player still counts as standing on it (a jump). */
    public static final double DECK_HEADROOM = 1.5D;
    /** Horizontal slack around the deck for "on deck". */
    public static final double DECK_MARGIN = 0.5D;
    /** Ticks it stays still after the last player left its deck. */
    public static final int STILL_TICKS = 10;

    /** Cruising altitude above the ground under it. */
    public static final int MIN_ALTITUDE = 12;
    public static final int MAX_ALTITUDE = 40;
    /** Headroom below the build limit. */
    public static final int CEILING_MARGIN = 8;
    /** How far a new drift target may be, horizontally. */
    public static final int DRIFT_RANGE = 32;
    /** Ticks between drift-target picks. */
    public static final int DRIFT_INTERVAL = 200;
    /** Speed added per tick toward the target, blocks per tick (it never hurries). */
    public static final double DRIFT_ACCEL = 0.004D;
    public static final double MAX_SPEED = 0.08D;

    /** Barnacles: one Minecraft day to regrow, three visual stages. */
    public static final int BARNACLE_REGROW_TICKS = 24000;
    public static final int BARNACLE_STAGES = 3;
    public static final int BARNACLE_BASE_YIELD = 2;
    public static final int BARNACLE_MAX_YIELD = 3;
    /** Barnacle growth is advanced this many ticks at a time. */
    public static final int GROWTH_STEP = 20;
    /** A natural spawn's barnacles start at least this grown, in percent. */
    public static final int SEED_MIN_PERCENT = 50;
    /** Vanilla's hard despawn range (blocks); closer than this the leviathan never despawns. */
    public static final double HARD_DESPAWN_RANGE = 128.0D;

    /**
     * The "song" pose (mouth opens, body arches): the art clip is 6 s, longer than the contract's
     * 3 s guidance for timed poses; acceptable here because at most two leviathans are loaded per
     * dimension and it sings at most once every 30 s.
     */
    public static final int SONG_POSE_TICKS = 120;

    private LeviathanRules() {
    }

    /** Whether a collider with feet at {@code feetY} rests on a deck whose top is {@code deckTop}. */
    public static boolean collidesFromAbove(double feetY, double deckTop) {
        return feetY >= deckTop - DECK_EPSILON;
    }

    /** Whether a player stands on (or just jumped from) the deck. */
    public static boolean onDeck(double feetY, double x, double z, double deckTop,
            double minX, double maxX, double minZ, double maxZ) {
        return feetY >= deckTop - DECK_EPSILON && feetY <= deckTop + DECK_HEADROOM
                && x >= minX - DECK_MARGIN && x <= maxX + DECK_MARGIN
                && z >= minZ - DECK_MARGIN && z <= maxZ + DECK_MARGIN;
    }

    /** The still timer after one tick: refreshed while occupied, else counting down to 0. */
    public static int stillTimer(int current, boolean occupied) {
        return occupied ? STILL_TICKS : Math.max(0, current - 1);
    }

    /** A cruising altitude for {@code desiredY} over ground at {@code groundY}. */
    public static double clampAltitude(double desiredY, int groundY, int buildTop) {
        double low = groundY + MIN_ALTITUDE;
        double high = Math.min(groundY + MAX_ALTITUDE, buildTop - CEILING_MARGIN);
        if (high < low) {
            return low;
        }
        return Math.max(low, Math.min(high, desiredY));
    }

    /**
     * Whether a body at {@code yawDegrees} lies along the x axis (vanilla yaw 0 faces +z). The
     * collision box is the deck's footprint snapped to the nearer axis.
     */
    public static boolean alongX(float yawDegrees) {
        double r = Math.toRadians(yawDegrees);
        return Math.abs(Math.sin(r)) > Math.abs(Math.cos(r));
    }

    /** Half extents {@code {x, z}} of the deck footprint for the snapped axis. */
    public static double[] halfExtents(boolean alongX) {
        double half = DECK_WIDTH / 2.0D;
        double halfLong = DECK_LENGTH / 2.0D;
        return alongX ? new double[] {halfLong, half} : new double[] {half, halfLong};
    }

    /** The spawn gate. */
    public static boolean maySpawnNaturally(int loaded) {
        return loaded < MAX_PER_DIMENSION;
    }

    /**
     * Seeded barnacle progress for a natural spawn: {@link #SEED_MIN_PERCENT}% to 100% of
     * {@code regrowTicks} for a uniform {@code fraction} in {@code [0, 1]}.
     */
    public static int seedBarnacles(int regrowTicks, double fraction) {
        if (regrowTicks <= 0) {
            return 0;
        }
        double f = Math.max(0.0D, Math.min(1.0D, fraction));
        double min = SEED_MIN_PERCENT / 100.0D;
        return (int) Math.round(regrowTicks * (min + (1.0D - min) * f));
    }

    /** Only vanilla's hard range removes a leviathan; the 32-128 block random despawn never does. */
    public static boolean mayDespawnAt(double distanceToClosestPlayerSqr) {
        return distanceToClosestPlayerSqr > HARD_DESPAWN_RANGE * HARD_DESPAWN_RANGE;
    }

    /** Barnacle yield for one harvest. */
    public static int barnacleYield(int base, int bonus) {
        return Math.max(0, Math.min(BARNACLE_MAX_YIELD, base + Math.max(0, bonus)));
    }

    /** The next horizontal speed component, clamped. */
    public static double accelerate(double current, double direction) {
        return Math.max(-MAX_SPEED, Math.min(MAX_SPEED, current + direction * DRIFT_ACCEL));
    }
}
