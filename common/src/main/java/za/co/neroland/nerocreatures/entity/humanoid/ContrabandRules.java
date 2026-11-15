package za.co.neroland.nerocreatures.entity.humanoid;

/**
 * The pure rules of a Space Pirate grabbing loose {@code contraband} (existing-12 polish): it carries
 * at most {@link #CARRY_LIMIT} in one carried-loot slot (never its weapon hand), scans on a
 * {@link #SCAN_INTERVAL_TICKS} cadence within {@link #SCAN_RADIUS} blocks, never while fighting, and
 * only through {@code CreatureGriefing.mayAlter(ITEM)} with its own key. The slot drops on death.
 */
public final class ContrabandRules {

    public static final int CARRY_LIMIT = 16;
    public static final double SCAN_RADIUS = 8.0D;
    public static final int SCAN_INTERVAL_TICKS = 20;
    /** Reach for the grab, in blocks. */
    public static final double REACH = 1.5D;

    private ContrabandRules() {
    }

    /** How many of {@code offered} fit beside {@code carried}. */
    public static int takeAmount(int carried, int offered, int limit) {
        return Math.max(0, Math.min(offered, limit - carried));
    }

    /** Whether the pirate goes looking at all. */
    public static boolean mayLook(boolean keyConfigured, boolean hasTarget, int carried) {
        return keyConfigured && !hasTarget && carried < CARRY_LIMIT;
    }

    /** Whether one grab happens: the griefing gate passed, the item is free to take, and there is room. */
    public static boolean mayGrab(boolean griefingAllowed, boolean pickUpDelay, int carried, int offered) {
        return griefingAllowed && !pickUpDelay && takeAmount(carried, offered, CARRY_LIMIT) > 0;
    }
}
