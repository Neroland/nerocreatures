package za.co.neroland.nerocreatures.entity.glacira;

/**
 * Pure rules for the Frost Lurker (Glacira hostile): the ambush trigger, the telegraphed eruption,
 * the freezing it applies and its cap, and when it gives up a chase and goes back to lying in wait.
 * No game types, so every rule and cap is unit-tested.
 */
public final class FrostLurkerRules {

    /** Horizontal radius that counts as "walked over" (shared {@code AmbushGoal}). */
    public static final double TRIGGER_RADIUS = 1.5D;
    /** Ticks between trigger checks while buried. */
    public static final int SCAN_INTERVAL = 4;
    /** Ticks after an eruption before it may bury itself again. */
    public static final int REBURY_COOLDOWN_TICKS = 200;
    /** Longest single wait before it re-checks its hole (it re-buries at once if still on snow or ice). */
    public static final int MAX_LURK_TICKS = 2400;
    /** The eruption telegraph: spines burst through the snow this long before the bite lands (at least 10). */
    public static final int TELEGRAPH_TICKS = 12;
    /** The {@code ability} (erupt) pose length; the bite lands at {@link #TELEGRAPH_TICKS} (under 3 s). */
    public static final int ERUPT_POSE_TICKS = 24;
    /** A victim who stepped further than this away during the telegraph is missed. */
    public static final double STRIKE_REACH = 2.5D;
    public static final float STRIKE_DAMAGE = 5.0F;
    /** Frozen ticks past a full freeze that the bite leaves (vanilla thaws 2 per tick: about 4 s frozen). */
    public static final int FREEZE_EXTRA_TICKS = 160;
    /** Hard cap: never more than this many ticks past a full freeze, however often it bites. */
    public static final int MAX_FREEZE_OVER_TICKS = 200;
    /** It drops a chase beyond this distance ... */
    public static final double LEASH = 16.0D;
    /** ... or after this long, and goes back to its trap. */
    public static final int MAX_CHASE_TICKS = 400;
    /** Grazer hunting (shared {@code PredatorHuntGoal}): radius, scan, satiation, chase. */
    public static final double HUNT_RADIUS = 12.0D;
    public static final int HUNT_SCAN_TICKS = 100;
    public static final int HUNT_SATIATION_TICKS = 2400;
    public static final int HUNT_MAX_CHASE_TICKS = 300;

    private FrostLurkerRules() {
    }

    /** Whether the bite lands on a victim at squared distance {@code distSqr} when the telegraph ends. */
    public static boolean strikeLands(double distSqr) {
        return distSqr >= 0.0D && distSqr <= STRIKE_REACH * STRIKE_REACH;
    }

    /**
     * The victim's frozen ticks after a bite: at least a full freeze plus {@link #FREEZE_EXTRA_TICKS},
     * never above {@code ticksToFullFreeze + MAX_FREEZE_OVER_TICKS}, and never lower than it was.
     */
    public static int freezeTicks(int current, int ticksToFullFreeze) {
        int full = Math.max(0, ticksToFullFreeze);
        int cap = full + MAX_FREEZE_OVER_TICKS;
        int raised = Math.max(Math.max(0, current), full + FREEZE_EXTRA_TICKS);
        return Math.min(cap, raised);
    }

    /** Whether to give up the current chase. */
    public static boolean dropTarget(double distSqr, long chaseTicks) {
        return distSqr > LEASH * LEASH || chaseTicks > MAX_CHASE_TICKS;
    }
}
