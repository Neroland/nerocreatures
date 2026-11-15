package za.co.neroland.nerocreatures.entity.ai.shared;

/**
 * Pure rules for the ecosystem loop: predators hunt prey (Lunar Stalker and Frost Lurker hunt
 * grazers; Echo Shrikes hunt moths), prey herds flee. Unit-tested.
 *
 * <p>The caps that keep the loop from emptying a herd:
 *
 * <ul>
 *   <li><b>Satiation:</b> after a kill a predator does not hunt again for {@code satiationTicks}.</li>
 *   <li><b>Pursuit timeout:</b> a chase longer than {@code maxChaseTicks} is abandoned and counts as
 *       a (shorter) satiation, so a predator does not orbit a herd forever.</li>
 *   <li><b>Pressure cap:</b> no more than {@link #MAX_HUNTERS_PER_PREY} predators chase one prey.</li>
 * </ul>
 */
public final class PredatorPreyRules {

    /** The most predators that may chase one prey at a time. */
    public static final int MAX_HUNTERS_PER_PREY = 2;

    private PredatorPreyRules() {
    }

    /** Whether a predator that last ate at {@code lastMeal} may hunt at {@code now}. */
    public static boolean hungry(long now, long lastMeal, int satiationTicks) {
        return lastMeal == Long.MIN_VALUE || now - lastMeal >= satiationTicks;
    }

    /** Whether a chase that began at {@code chaseStart} has run too long. */
    public static boolean chaseTimedOut(long now, long chaseStart, int maxChaseTicks) {
        return maxChaseTicks > 0 && now - chaseStart >= maxChaseTicks;
    }

    /** Whether one more predator may chase a prey already chased by {@code currentHunters}. */
    public static boolean pressureAllows(int currentHunters) {
        return currentHunters < MAX_HUNTERS_PER_PREY;
    }

    /**
     * Index of the nearest candidate within {@code radius}, or -1. Ties keep the first.
     *
     * @param distSqr squared distance of each candidate
     */
    public static int nearestWithin(double[] distSqr, double radius) {
        double limit = radius * radius;
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < distSqr.length; i++) {
            if (distSqr[i] <= limit && distSqr[i] < bestDist) {
                best = i;
                bestDist = distSqr[i];
            }
        }
        return best;
    }

    /** Whether a herd alarm raised until {@code alarmUntil} is still active. */
    public static boolean alarmActive(long now, long alarmUntil) {
        return now < alarmUntil;
    }
}
