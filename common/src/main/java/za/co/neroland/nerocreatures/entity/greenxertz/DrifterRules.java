package za.co.neroland.nerocreatures.entity.greenxertz;

/**
 * The Spore Drifter's rules as pure arithmetic, unit-tested without a world.
 *
 * <ul>
 *   <li><b>Growth ticks</b> (decision 006, key {@code sporeDrifterGrowth}, {@code Change.REPLACE}).
 *       At most one extra random tick per {@link #GROWTH_INTERVAL} ticks (4 s) on one crop in
 *       {@code #minecraft:crops} in the 3 x 3 column below it, at most {@link #MAX_DEPTH} blocks
 *       down. Denied: particles only.</li>
 *   <li><b>Area cap.</b> Leashing many drifters over one field does not stack without bound: each
 *       drifter counts its neighbours within {@link #AREA_RADIUS} blocks (at most
 *       {@link #MAX_COUNTED}) and stretches its own interval so one area receives at most
 *       {@link #AREA_BOOSTERS} growth ticks per {@link #GROWTH_INTERVAL}.</li>
 *   <li><b>Bottling.</b> A glass bottle on an adult drifter gives one {@code drifter_spores}, then
 *       the drifter needs {@link #BOTTLE_COOLDOWN_TICKS} to refill. A wild drifter can be bottled
 *       by anyone; a tamed one only by its owner.</li>
 * </ul>
 */
public final class DrifterRules {

    /** Ticks between growth ticks for a lone drifter: 4 s (decision 006). */
    public static final int GROWTH_INTERVAL = 80;
    /** Radius in which drifters share the area cap. */
    public static final double AREA_RADIUS = 6.0D;
    /** Growth ticks one area receives per {@link #GROWTH_INTERVAL}, however many drifters hover there. */
    public static final int AREA_BOOSTERS = 4;
    /** Neighbour query cap. */
    public static final int MAX_COUNTED = 16;
    /** Half-width of the footprint below the drifter (1 = 3 x 3). */
    public static final int FOOTPRINT = 1;
    /** How far below the drifter a crop may be. */
    public static final int MAX_DEPTH = 8;
    /** Refill time after bottling: 5 minutes. */
    public static final int BOTTLE_COOLDOWN_TICKS = 6000;
    /** The {@code ability} pose for a growth tick (tendrils reach down). */
    public static final int GROWTH_POSE_TICKS = 20;

    /** What one growth pulse does. */
    public enum Growth {
        /** No crop below: nothing. */
        NONE,
        /** Allowed: one extra random tick on the crop. */
        TICK,
        /** Denied by the gate: spore particles only (decision 006 fallback). */
        PARTICLES
    }

    private DrifterRules() {
    }

    /** This drifter's growth interval when {@code otherDrifters} share its area. */
    public static int interval(int otherDrifters) {
        int here = Math.max(0, Math.min(MAX_COUNTED, otherDrifters)) + 1;
        int share = (here + AREA_BOOSTERS - 1) / AREA_BOOSTERS;
        return GROWTH_INTERVAL * Math.max(1, share);
    }

    /** Growth ticks per {@link #GROWTH_INTERVAL} an area of {@code drifters} receives in total. */
    public static double areaTicksPerInterval(int drifters) {
        if (drifters <= 0) {
            return 0.0D;
        }
        return (double) drifters * GROWTH_INTERVAL / interval(drifters - 1);
    }

    /** The pulse outcome; {@code allowed} is {@code CreatureGriefing.mayAlter(.., REPLACE)}. */
    public static Growth growth(boolean cropFound, boolean allowed) {
        if (!cropFound) {
            return Growth.NONE;
        }
        return allowed ? Growth.TICK : Growth.PARTICLES;
    }

    /** Whether {@code player} may bottle the drifter now. */
    public static boolean mayBottle(boolean baby, boolean tame, boolean playerIsOwner, long now, long readyAt) {
        if (baby || now < readyAt) {
            return false;
        }
        return !tame || playerIsOwner;
    }

    /** The game time a drifter bottled at {@code now} may be bottled again. */
    public static long nextBottle(long now) {
        return now + BOTTLE_COOLDOWN_TICKS;
    }
}
