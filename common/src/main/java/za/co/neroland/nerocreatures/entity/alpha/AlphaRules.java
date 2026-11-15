package za.co.neroland.nerocreatures.entity.alpha;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure alpha rules: the roll, the cap and weighted picks. Unit-tested.
 */
public final class AlphaRules {

    private AlphaRules() {
    }

    /** Whether a roll in {@code [0,1)} makes an alpha at {@code chance} (clamped to 0-1). */
    public static boolean rolls(double chance, double roll) {
        double c = Math.max(0.0D, Math.min(1.0D, chance));
        return roll < c;
    }

    /** Effective per-dimension cap: data value, optional override (negative = none), hard ceiling. */
    public static int effectiveCap(int dataCap, int override) {
        int cap = override >= 0 ? override : dataCap;
        return Math.max(0, Math.min(AlphaSettings.ABSOLUTE_MAX_PER_DIMENSION, cap));
    }

    /** Whether one more alpha fits under {@code cap} with {@code loaded} already present. */
    public static boolean capAllows(int loaded, int cap) {
        return cap > 0 && loaded < cap;
    }

    /**
     * Weighted pick. {@code roll} is any non-negative int (e.g. {@code random.nextInt(Integer.MAX_VALUE)});
     * weights below 1 count as 1. Returns {@code null} for an empty list.
     */
    public static <T> T pickWeighted(List<T> options, ToIntFunction<T> weight, int roll) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        long total = 0L;
        for (T option : options) {
            total += Math.max(1, weight.applyAsInt(option));
        }
        long point = Math.floorMod(roll, total);
        for (T option : options) {
            point -= Math.max(1, weight.applyAsInt(option));
            if (point < 0L) {
                return option;
            }
        }
        return options.get(options.size() - 1);
    }

    /** Drop-file path for an entity id: {@code ns:path} to its {@code ns} and {@code path}. */
    public static String[] dropFileFor(String entityId) {
        int colon = entityId.indexOf(':');
        if (colon < 0) {
            return new String[] {"minecraft", entityId};
        }
        return new String[] {entityId.substring(0, colon), entityId.substring(colon + 1)};
    }
}
