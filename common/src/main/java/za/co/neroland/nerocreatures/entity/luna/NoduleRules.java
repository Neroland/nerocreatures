package za.co.neroland.nerocreatures.entity.luna;

/**
 * The pure rules of Lode Tortoise nodules. Unit-tested.
 *
 * <ul>
 *   <li>Every graze reads the nearest block in a {@code nerocreatures:lode_tortoise/*} tag within
 *       {@link #GRAZE_RADIUS} blocks. <b>It never breaks or changes it.</b></li>
 *   <li>Nodules grow only while the last graze found ore. <b>The family is fixed once nodules
 *       show</b> (stage 1 or more): grazing another ore then keeps growing the same family at that
 *       family's rate, so a tortoise walking a mixed cave never loses progress (Stage 2 review M2).
 *       Only at stage 0, where nothing is visible yet, does a different ore switch the family, and
 *       that hidden progress restarts (no diamonds grown on iron progress). Ready nodules keep their
 *       family until harvested.</li>
 *   <li>Natural spawns start with a random, low-weighted stage ({@link #seedStage}) of a common
 *       family ({@link #seedFamily}), so wild tortoises visibly carry nodules.</li>
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
    /**
     * Default ticks of ore grazing from bare shell to ready nodules for a common family: one and a
     * half days (rare families six). Raised from one day in the Stage 2 review (M2): husbanded
     * tortoises no longer despawn, so a bred herd beside one placed diamond ore scales the yield,
     * and the brief asks for slow, low-yield ore.
     */
    public static final int DEFAULT_REGROW_TICKS = 36_000;
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
     * @param stage   the visible nodule stage now ({@code 0..MAX_STAGE})
     * @param ready   whether the current nodules are ready to harvest
     * @param found   the family of the nearest ore grazed now, or null when none was in reach
     */
    public static GrazeResult onGraze(NoduleFamily current, int stage, boolean ready, NoduleFamily found) {
        if (found == null || ready) {
            return new GrazeResult(current, false, false);
        }
        if (current == null || current == found) {
            return new GrazeResult(found, false, true);
        }
        if (stage <= 0) {
            // Nothing visible yet: take the new family and restart the hidden progress.
            return new GrazeResult(found, true, true);
        }
        // Nodules already show: keep the family and the stage, keep growing at its own rate.
        return new GrazeResult(current, false, true);
    }

    /** The spawn stage for a uniform roll in {@code 0..99}: 45% bare, 35% one, 15% two, 5% ready. */
    public static int seedStage(int roll100) {
        int r = Math.floorMod(roll100, 100);
        if (r < 45) {
            return 0;
        }
        if (r < 80) {
            return 1;
        }
        if (r < 95) {
            return 2;
        }
        return MAX_STAGE;
    }

    /**
     * Timer progress for a seeded stage: the start of that stage plus {@code fraction} of one stage
     * (clamped to {@code [0, 1)}), or full for the ready stage. Stage 0 seeds nothing.
     */
    public static int seedProgress(int stage, int regrowTicks, double fraction) {
        if (stage <= 0 || regrowTicks <= 0) {
            return 0;
        }
        if (stage >= MAX_STAGE) {
            return regrowTicks;
        }
        double f = Math.max(0.0D, Math.min(0.999D, fraction));
        long perStage = (long) regrowTicks / MAX_STAGE;
        // +1 so integer stage maths in ProductTimer never rounds a stage-1 seed down to stage 0.
        return (int) Math.min(regrowTicks - 1L, perStage * stage + 1L + (long) (perStage * f));
    }

    /** A spawn family: common families only (rare ones are grown, never spawned). Null at stage 0. */
    public static NoduleFamily seedFamily(int stage, int roll) {
        if (stage <= 0) {
            return null;
        }
        NoduleFamily[] common = {NoduleFamily.COAL, NoduleFamily.COPPER, NoduleFamily.IRON,
                NoduleFamily.REDSTONE, NoduleFamily.LAPIS};
        return common[Math.floorMod(roll, common.length)];
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
