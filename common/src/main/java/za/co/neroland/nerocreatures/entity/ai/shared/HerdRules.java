package za.co.neroland.nerocreatures.entity.ai.shared;

/**
 * Pure herd-cohesion rules. Unit-tested.
 *
 * <p>A herd member regroups when it has at least one mate in sight and is farther than
 * {@code maxGap} from the mates' centroid (the member itself excluded). It never chases a herd of
 * one, and it stops when within half the gap.
 */
public final class HerdRules {

    private HerdRules() {
    }

    /** Centroid of the given coordinates; {@code null} for an empty herd. */
    public static double[] centroid(double[] xs, double[] zs) {
        int n = Math.min(xs.length, zs.length);
        if (n == 0) {
            return null;
        }
        double sx = 0.0D;
        double sz = 0.0D;
        for (int i = 0; i < n; i++) {
            sx += xs[i];
            sz += zs[i];
        }
        return new double[] {sx / n, sz / n};
    }

    /** Whether a member at squared distance {@code distSqrToCentroid} should regroup. */
    public static boolean shouldRegroup(int mates, double distSqrToCentroid, double maxGap) {
        return mates > 0 && distSqrToCentroid > maxGap * maxGap;
    }

    /** Whether a regrouping member is close enough to stop. */
    public static boolean regrouped(double distSqrToCentroid, double maxGap) {
        double half = maxGap * 0.5D;
        return distSqrToCentroid <= half * half;
    }
}
