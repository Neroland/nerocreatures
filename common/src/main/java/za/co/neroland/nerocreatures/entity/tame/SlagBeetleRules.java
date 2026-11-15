package za.co.neroland.nerocreatures.entity.tame;

/**
 * The Slag Beetle's rules as pure arithmetic, so they are unit-tested without a world.
 *
 * <ul>
 *   <li><b>Output.</b> One digested raw-ore item becomes the vanilla smelting result, plus one
 *       extra item per bonus roll that hits {@link #BONUS_CHANCE}. Every digestion gets one bonus
 *       roll; a Bountiful extra roll ({@code rolls} = 2) adds a second roll of the same chance, never
 *       a multiplier (Stage 2 review M6). Expected output is at most {@link #MAX_EXPECTED_RATIO} of a
 *       furnace ({@link #expectedOutput}); the total is capped at {@link #maxOutput(int)}.</li>
 *   <li><b>Inputs</b> are raw-ore items only ({@code nerocreatures:slag_beetle_ores}: vanilla raw
 *       iron, copper and gold plus {@code #c:raw_materials}); ore blocks are not food.</li>
 *   <li><b>Eating gate.</b> A beetle eats only when it is tamed, its owner threw the item, the item
 *       is beetle food, and {@code CreatureGriefing} allowed the pickup ({@code Change.ITEM},
 *       {@code slagBeetleSmelting}). Any "no" means it ignores the item (decision 006 fallback).</li>
 *   <li><b>Feed claim</b> (decision 007, Magnet Crab clash). A ground item is reserved for beetles,
 *       and a tamed Magnet Crab must leave it alone, when it is younger than
 *       {@link #FRESH_ITEM_TICKS} ticks (any item: it was just thrown or just dropped), or when it is
 *       beetle food lying within {@link #FEED_RADIUS} blocks of a tamed Slag Beetle.</li>
 * </ul>
 */
public final class SlagBeetleRules {

    /** How far a beetle looks for thrown ore, and the radius of its feed claim, in blocks. */
    public static final double FEED_RADIUS = 6.0D;

    /** Items younger than this (ticks) are never vacuumed by a Magnet Crab. Two seconds. */
    public static final int FRESH_ITEM_TICKS = 40;

    /** Digestion time per item, in ticks: exactly a vanilla furnace's 10 seconds. */
    public static final int DIGEST_TICKS = 200;

    /** How many items the stomach holds at once. A furnace's worth of patience, not a smeltery. */
    public static final int STOMACH_CAPACITY = 8;

    /** The chance of one extra smelted item, per bonus roll. */
    public static final double BONUS_CHANCE = 0.10D;

    /** At most this many bonus rolls per item: the base roll and one Bountiful roll. */
    public static final int MAX_BONUS_ROLLS = 2;

    /** Design bound: expected output per item never exceeds this times a furnace's. */
    public static final double MAX_EXPECTED_RATIO = 1.25D;

    private SlagBeetleRules() {
    }

    /** Whether a beetle may eat a ground item. Every input must be true. */
    public static boolean mayEat(boolean tamed, boolean thrownByOwner, boolean beetleFood, boolean griefingAllowed) {
        return tamed && thrownByOwner && beetleFood && griefingAllowed;
    }

    /** The most items one digestion can produce for a recipe that yields {@code baseCount}. */
    public static int maxOutput(int baseCount) {
        if (baseCount <= 0) {
            return 0;
        }
        return Math.min(64, baseCount + MAX_BONUS_ROLLS);
    }

    /** Bonus rolls for a trait roll count: 1, or 2 on a Bountiful extra roll (clamped to 1..2). */
    public static int bonusRolls(int rolls) {
        return Math.max(1, Math.min(MAX_BONUS_ROLLS, rolls));
    }

    /**
     * Items produced by one digestion.
     *
     * @param baseCount the smelting recipe's result count (0 = not smeltable: nothing is produced,
     *                  the eaten item is returned instead)
     * @param bonusHits how many bonus rolls hit (clamped to {@code 0..MAX_BONUS_ROLLS})
     */
    public static int outputCount(int baseCount, int bonusHits) {
        if (baseCount <= 0) {
            return 0;
        }
        int hits = Math.max(0, Math.min(MAX_BONUS_ROLLS, bonusHits));
        return Math.min(baseCount + hits, maxOutput(baseCount));
    }

    /** Expected items per digested item for {@code rolls} bonus rolls. */
    public static double expectedOutput(int baseCount, int rolls) {
        if (baseCount <= 0) {
            return 0.0D;
        }
        return baseCount + bonusRolls(rolls) * BONUS_CHANCE;
    }

    /**
     * The feed-claim rule WP-Asteroid's tamed Magnet Crab honours.
     *
     * @param itemAgeTicks      {@code ItemEntity.getAge()}
     * @param beetleFood        the item is in {@code nerocreatures:slag_beetle_ores}
     * @param tamedBeetleNearby a tamed Slag Beetle is within {@link #FEED_RADIUS} blocks of the item
     */
    public static boolean reserved(int itemAgeTicks, boolean beetleFood, boolean tamedBeetleNearby) {
        if (itemAgeTicks < FRESH_ITEM_TICKS) {
            return true;
        }
        return beetleFood && tamedBeetleNearby;
    }
}
