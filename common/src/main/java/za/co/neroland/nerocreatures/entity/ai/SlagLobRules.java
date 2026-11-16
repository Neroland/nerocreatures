package za.co.neroland.nerocreatures.entity.ai;

/**
 * The Magma Wader's slag lob as pure arithmetic (unit-tested without a world).
 *
 * <ul>
 *   <li><b>Range and cadence.</b> It lobs at a target between {@link #MIN_RANGE} and
 *       {@link #MAX_RANGE} blocks, with a {@link #WINDUP_TICKS}-tick telegraph (the contract asks for
 *       at least 10) and a cooldown of {@link #COOLDOWN_MIN_TICKS}..{@link #COOLDOWN_MAX_TICKS}.</li>
 *   <li><b>Flight.</b> The slag lands where the target stood at launch, after
 *       {@link #flightTicks(double)} ticks, so sidestepping dodges it.</li>
 *   <li><b>Caps.</b> At most {@link #MAX_IN_FLIGHT} lobs in the air per wader, at most
 *       {@link #MAX_CRUST_PER_LOB} crust blocks per landing and at most {@link #MAX_LIVE_CRUST} live
 *       crust blocks per wader. {@link #crustBudget} turns those into "how many blocks may this
 *       landing place", and returns 0 when {@code CreatureGriefing} said no: the fallback is the
 *       fire damage alone.</li>
 *   <li><b>Decay.</b> Each crust block reverts after {@link #DECAY_MIN_TICKS}..{@link #DECAY_MAX_TICKS}
 *       (30-60 s), through {@code EphemeralCrustBlock}'s scheduled tick.</li>
 * </ul>
 */
public final class SlagLobRules {

    public static final double MIN_RANGE = 3.0D;
    public static final double MAX_RANGE = 14.0D;
    public static final int WINDUP_TICKS = 14;
    public static final int POSE_TICKS = 20;
    public static final int COOLDOWN_MIN_TICKS = 80;
    public static final int COOLDOWN_MAX_TICKS = 120;
    public static final int MIN_FLIGHT_TICKS = 10;
    public static final int MAX_FLIGHT_TICKS = 24;
    public static final int MAX_IN_FLIGHT = 2;
    public static final int MAX_CRUST_PER_LOB = 3;
    public static final int MAX_LIVE_CRUST = 9;
    public static final int DECAY_MIN_TICKS = 600;
    public static final int DECAY_MAX_TICKS = 1200;
    public static final double SPLASH_RADIUS = 1.6D;
    public static final float SPLASH_DAMAGE = 4.0F;
    public static final float IGNITE_SECONDS = 3.0F;

    /** What a crust block reverts to; mirrors {@code EphemeralCrustBlock.Restore} without MC types. */
    public enum Target {
        /** Air (or a replaceable) became crust; it reverts to air. */
        AIR,
        /** A lava source became crust; it reverts to lava. */
        LAVA,
        /** Nothing may be placed here. */
        NONE
    }

    private SlagLobRules() {
    }

    /** Whether a target this far away (blocks) is in lobbing range. */
    public static boolean inRange(double distance) {
        return distance >= MIN_RANGE && distance <= MAX_RANGE;
    }

    /** Ticks in the air for a lob over {@code distance} blocks. */
    public static int flightTicks(double distance) {
        int ticks = (int) Math.round(Math.max(0.0D, distance) * 1.5D);
        return Math.max(MIN_FLIGHT_TICKS, Math.min(MAX_FLIGHT_TICKS, ticks));
    }

    /** Arc height at {@code fraction} (0..1) of the flight, for the particle trail. */
    public static double arcHeight(double distance, double fraction) {
        double f = Math.max(0.0D, Math.min(1.0D, fraction));
        double peak = Math.min(4.0D, distance * 0.35D) + 1.0D;
        return 4.0D * peak * f * (1.0D - f);
    }

    /** A cooldown in {@code [COOLDOWN_MIN_TICKS, COOLDOWN_MAX_TICKS]} from a 0..1 roll. */
    public static int cooldown(double roll) {
        double r = Math.max(0.0D, Math.min(1.0D, roll));
        return COOLDOWN_MIN_TICKS + (int) Math.round(r * (COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS));
    }

    /** A decay time in {@code [DECAY_MIN_TICKS, DECAY_MAX_TICKS]} from a 0..1 roll. */
    public static int decayTicks(double roll) {
        double r = Math.max(0.0D, Math.min(1.0D, roll));
        return DECAY_MIN_TICKS + (int) Math.round(r * (DECAY_MAX_TICKS - DECAY_MIN_TICKS));
    }

    /** Whether another lob may launch with {@code inFlight} already in the air. */
    public static boolean mayLaunch(int inFlight) {
        return inFlight < MAX_IN_FLIGHT;
    }

    /**
     * How many crust blocks one landing may place.
     *
     * @param griefingAllowed the {@code CreatureGriefing} answer for this wader at the landing site
     * @param liveCrust       crust blocks this wader placed that have not decayed yet
     */
    public static int crustBudget(boolean griefingAllowed, int liveCrust) {
        if (!griefingAllowed) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_CRUST_PER_LOB, MAX_LIVE_CRUST - Math.max(0, liveCrust)));
    }

    /**
     * What one column of the landing site can take: a lava source just below the impact level is
     * crusted over ({@link Target#LAVA}); otherwise an empty cell at impact level over sturdy ground
     * gets a crust ({@link Target#AIR}). Never a cell an entity is in.
     */
    public static Target classify(boolean belowIsLavaSource, boolean belowOccupied, boolean cellIsEmpty,
            boolean belowIsSturdy, boolean cellOccupied) {
        if (belowIsLavaSource) {
            return belowOccupied ? Target.NONE : Target.LAVA;
        }
        if (cellIsEmpty && belowIsSturdy && !cellOccupied) {
            return Target.AIR;
        }
        return Target.NONE;
    }
}
