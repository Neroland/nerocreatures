package za.co.neroland.nerocreatures.block;

/**
 * What an Incubator hatchling is (decision 003, PROGRESS "Stage 4 egg-baby marking"). Pure.
 *
 * <ul>
 *   <li>Always a baby, carrying the egg's traits.</li>
 *   <li>A tameable species' hatchling is untamed and flagged: its first feed by any player tames it at
 *       100% through the normal cap-checked path.</li>
 *   <li>Livestock is born <b>husbanded</b>: the egg path spawns no baby at breeding time (vanilla's
 *       finalize step sees a null offspring), so the mark that live babies inherit is set at hatch
 *       instead. Only player-fed (so husbanded) parents breed, so this is the same rule.</li>
 * </ul>
 *
 * @param baby      spawn as a baby
 * @param hatchling first feed tames at 100%
 * @param husbanded mark as kept livestock
 */
public record HatchRules(boolean baby, boolean hatchling, boolean husbanded) {

    public static HatchRules of(boolean tameable, boolean livestock) {
        return new HatchRules(true, tameable && !livestock, livestock);
    }
}
