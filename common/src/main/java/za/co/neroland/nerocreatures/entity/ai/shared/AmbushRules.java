package za.co.neroland.nerocreatures.entity.ai.shared;

/**
 * Pure rules for {@link AmbushGoal}: when an ambusher may hide again, when it erupts, and when it
 * gives up waiting. Unit-tested.
 */
public final class AmbushRules {

    private AmbushRules() {
    }

    /** Whether the cooldown since the last eruption has passed. */
    public static boolean cooldownReady(long now, long lastErupt, int cooldownTicks) {
        return lastErupt == Long.MIN_VALUE || now - lastErupt >= cooldownTicks;
    }

    /**
     * Whether a victim at horizontal squared distance {@code horizontalDistSqr} and vertical offset
     * {@code dy} (victim feet minus ambusher feet) is "walking over" the ambusher.
     */
    public static boolean shouldErupt(double horizontalDistSqr, double dy, double triggerRadius) {
        return triggerRadius > 0
                && horizontalDistSqr <= triggerRadius * triggerRadius
                && dy >= -0.5D && dy <= 2.0D;
    }

    /** Whether a lurk that started at {@code lurkStart} has lasted its maximum. */
    public static boolean lurkExpired(long now, long lurkStart, int maxLurkTicks) {
        return maxLurkTicks > 0 && now - lurkStart >= maxLurkTicks;
    }
}
