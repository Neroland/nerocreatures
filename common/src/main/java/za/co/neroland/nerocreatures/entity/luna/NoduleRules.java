package za.co.neroland.nerocreatures.entity.luna;

/**
 * The pure rules of Lode Tortoise nodules. Unit-tested.
 *
 * <ul>
 *   <li>Every graze reads the nearest block in a {@code nerocreatures:lode_tortoise/*} tag within
 *       {@link #GRAZE_RADIUS} blocks. <b>It never breaks or changes it.</b></li>
 *   <li>Nodules grow only while the last graze found ore. Grazing a different family before the
 *       nodules are ready switches the family and restarts growth (no growing diamonds on iron
 *       progress). Ready nodules keep their family until harvested.</li>
 *   <li>Rare families grow {@link NoduleFamily#regrowMultiplier()} times slower.</li>
 *   <li>A harvest yields {@code baseYield x rolls} (rolls is 2 on a Bountiful extra roll), capped at
 *       the configured per-harvest cap; a cap of 0 disables yield.</li>
 * </ul>
 */
public final class NoduleRules {

    /** Visual nodule stages; the last means ready. */
    public static final int MAX_STAGE = 3;
    /** Default per-harvest cap. */
    public static final int DEFAULT_YIELD_CAP = 4;
    /** Default ticks from bare shell to ready nodules for a common family (one day). */
    public static final int DEFAULT_REGROW_TICKS = 24_000;
    /** Ticks between grazes. */
    public static final int GRAZE_INTERVAL = 600;
    /** Horizontal graze radius, blocks (decision 006: four). */
    public static final int GRAZE_RADIUS = 4;
    /** Vertical graze radius, blocks. */
    public static final int GRAZE_VERTICAL = 2;
    /** Chance that a harvest also sheds one {@code lode_scute}. */
    public static final float SCUTE_SHED_CHANCE = 0.2F;

    /** The result of one graze. */
    public record GrazeResult(NoduleFamily family, boolean resetProgress, boolean growing) {
    }

    private NoduleRules() {
    }

    /** Nodules grow only with the balance key on, and only on adults. */
    public static boolean mayGrow(boolean keyOn, boolean baby) {
        return keyOn && !baby;
    }

    /**
     * @param current the family on the shell now, or null
     * @param ready   whether the current nodules are ready to harvest
     * @param found   the family of the nearest ore grazed now, or null when none was in reach
     */
    public static GrazeResult onGraze(NoduleFamily current, boolean ready, NoduleFamily found) {
        if (found == null || ready) {
            return new GrazeResult(current, false, false);
        }
        if (current == null || current == found) {
            return new GrazeResult(found, false, true);
        }
        return new GrazeResult(found, true, true);
    }

    /** Growth ticks credited for {@code elapsed} ticks of grazing on {@code family}. */
    public static int growthTicks(int elapsed, NoduleFamily family) {
        if (elapsed <= 0 || family == null) {
            return 0;
        }
        return elapsed / Math.max(1, family.regrowMultiplier());
    }

    /** One harvest: {@code base x rolls}, clamped to {@code 0..cap}. A cap of 0 or less yields nothing. */
    public static int harvestYield(int base, int rolls, int cap) {
        if (cap <= 0 || base <= 0) {
            return 0;
        }
        long total = (long) base * Math.max(1, rolls);
        return (int) Math.min(cap, total);
    }

    /** Whether a harvest also sheds a scute, for a uniform roll in {@code [0, 1)}. */
    public static boolean shedsScute(float roll) {
        return roll < SCUTE_SHED_CHANCE;
    }
}
