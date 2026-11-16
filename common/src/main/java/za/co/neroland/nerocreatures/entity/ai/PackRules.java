package za.co.neroland.nerocreatures.entity.ai;

/**
 * Pure pack rules for the Lunar Stalker polish : the howl on a shared target
 * and the last survivor's retreat. Unit-tested ({@code PackRulesTest}).
 */
public final class PackRules {

    /** Below this fraction of its maximum health a lone stalker gives up and runs. */
    public static final float FLEE_HEALTH_FRACTION = 0.3F;

    /** How long pack-mates glow after a howl, in ticks (a brief "here they come" cue). */
    public static final int HOWL_GLOW_TICKS = 40;

    /** Most pack-mates one howl lights up (matches the broadcast's own recruit cap). */
    public static final int MAX_HOWL_GLOWS = 8;

    /** Ticks between the flee goal's checks (the mate count is one bounded entity query). */
    public static final int FLEE_CHECK_TICKS = 20;

    private PackRules() {
    }

    /** A howl only when the broadcast actually recruited someone: a lone stalker stays quiet. */
    public static boolean howls(int recruited) {
        return recruited > 0;
    }

    /** The last survivor flees: badly hurt and no living pack-mate nearby. */
    public static boolean shouldFlee(float health, float maxHealth, int matesNearby) {
        return maxHealth > 0.0F && matesNearby == 0 && health < maxHealth * FLEE_HEALTH_FRACTION;
    }

    /** Glows granted for a howl, never more than {@link #MAX_HOWL_GLOWS}. */
    public static int glowCount(int mates) {
        return Math.max(0, Math.min(mates, MAX_HOWL_GLOWS));
    }
}
