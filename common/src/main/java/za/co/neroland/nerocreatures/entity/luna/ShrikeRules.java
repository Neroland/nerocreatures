package za.co.neroland.nerocreatures.entity.luna;

/**
 * Echo Shrike tuning and its target rule. Pure and unit-tested; the loudness rules themselves are
 * the shared {@code SoundHuntRules}.
 *
 * <ul>
 *   <li>Blind: no sight-based target goal. It targets only what it hears (players, wild moths) or
 *       what hurts it, and drops a target that stays quiet for {@link #MEMORY_TICKS}.</li>
 *   <li>Sneaking players and creative or spectator players are never targets by sound.</li>
 *   <li>Hunts wild moths ({@code #nerocreatures:prey/moths}) with the shared predator caps.</li>
 * </ul>
 */
public final class ShrikeRules {

    /** Hearing radius for the loudest noise (landing, mining); sprint steps carry two thirds. */
    public static final int HEARING_RADIUS = 24;
    /** How long a noise (and a quiet target) is remembered. */
    public static final int MEMORY_TICKS = 120;
    /** Ticks between sound-hunt decisions. */
    public static final int DECISION_INTERVAL = 10;
    /** Moth hunting: radius, scan interval, satiation and chase cap. */
    public static final double MOTH_HUNT_RADIUS = 12.0D;
    public static final int MOTH_SCAN_INTERVAL = 60;
    public static final int MOTH_SATIATION_TICKS = 1200;
    public static final int MOTH_MAX_CHASE_TICKS = 300;
    /** The lunge ({@code ability} pose, Art-Luna clip): 26 ticks, leap at 8, hit at 12 (window 10-14). */
    public static final int LUNGE_POSE_TICKS = 26;
    public static final int LUNGE_LEAP_TICK = 8;
    public static final int LUNGE_HIT_TICK = 12;
    /** Ticks after a lunge before the next may start. */
    public static final int LUNGE_COOLDOWN_TICKS = 14;
    /** A lunge starts within this distance, blocks. */
    public static final double LUNGE_START_DISTANCE = 3.0D;
    /** The hit lands only if the target is still within this distance, blocks. */
    public static final double LUNGE_REACH = 3.2D;

    private ShrikeRules() {
    }

    /** Whether a lunge may start at squared distance {@code distSqr} with {@code cooldown} ticks left. */
    public static boolean mayStartLunge(double distSqr, int cooldown) {
        return cooldown <= 0 && distSqr <= LUNGE_START_DISTANCE * LUNGE_START_DISTANCE;
    }

    /** Whether the lunge connects at squared distance {@code distSqr}. */
    public static boolean inReach(double distSqr) {
        return distSqr <= LUNGE_REACH * LUNGE_REACH;
    }

    /**
     * Whether the searching pose ({@code ability_2}, held) should still be held: the target was lost
     * at {@code lostAt} and the memory window has not run out, and nothing new was heard.
     */
    public static boolean stillSearching(long lostAt, long now, boolean hasTarget) {
        return !hasTarget && lostAt != Long.MIN_VALUE && now - lostAt <= MEMORY_TICKS;
    }

    /**
     * Whether a heard entity may become the shrike's target.
     *
     * @param isPlayer           a player
     * @param creativeOrSpectator a player in creative or spectator
     * @param isMoth             in {@code #nerocreatures:prey/moths}
     * @param wild               not tamed, owned or named
     */
    public static boolean huntable(boolean isPlayer, boolean creativeOrSpectator, boolean isMoth, boolean wild) {
        if (isPlayer) {
            return !creativeOrSpectator;
        }
        return isMoth && wild;
    }
}
