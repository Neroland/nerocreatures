package za.co.neroland.nerocreatures.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure rules for the Feeding Trough: on a cadence, it feeds <b>two adults of one
 * species</b> in range from its inventory with that species' breeding food, which puts them in love so
 * they breed by vanilla's own path. It never feeds babies and never spawns a baby itself.
 *
 * <ul>
 *   <li><b>Husbandry.</b> Only <em>husbanded</em> livestock (an animal a player already keeps; see
 *       {@code HusbandryRules}) is fed. A trough is not a way to farm a wild herd without ever
 *       touching it, and predators' wild prey stays wild.</li>
 *   <li><b>Bounded.</b> A radius of {@value #RADIUS} blocks ({@value #VERTICAL} up and down), at most
 *       {@value #MAX_SCANNED} animals looked at and at most one pair per pulse.</li>
 *   <li><b>Cadence.</b> One pulse every {@code feedingTroughIntervalTicks} (default
 *       {@value #DEFAULT_INTERVAL_TICKS}); 0 switches troughs off.</li>
 * </ul>
 */
public final class TroughRules {

    public static final int RADIUS = 6;

    public static final int VERTICAL = 2;

    public static final int MAX_SCANNED = 32;

    public static final int DEFAULT_INTERVAL_TICKS = 200;

    private TroughRules() {
    }

    /** What the trough knows about one animal in range. */
    public record Candidate<T>(T animal, String species, boolean baby, boolean husbanded, boolean readyToLove) {
    }

    /** Whether one animal may be fed: an adult, husbanded, able to fall in love right now. */
    public static boolean eligible(boolean baby, boolean husbanded, boolean readyToLove) {
        return !baby && husbanded && readyToLove;
    }

    /**
     * The pair to feed this pulse: the first species (in scan order) with two eligible adults and food in
     * the trough for it, or an empty list.
     *
     * @param hasFood whether the trough holds at least two of that species' food
     */
    public static <T> List<T> pickPair(List<Candidate<T>> candidates, Predicate<String> hasFood) {
        Map<String, List<T>> bySpecies = new LinkedHashMap<>();
        int scanned = 0;
        for (Candidate<T> candidate : candidates) {
            if (++scanned > MAX_SCANNED) {
                break;
            }
            if (eligible(candidate.baby(), candidate.husbanded(), candidate.readyToLove())) {
                List<T> group = bySpecies.computeIfAbsent(candidate.species(), key -> new ArrayList<>(2));
                group.add(candidate.animal());
                if (group.size() == 2 && hasFood.test(candidate.species())) {
                    return List.copyOf(group);
                }
            }
        }
        return List.of();
    }

    /** Whether a pulse is due; an interval of 0 or less switches the trough off. */
    public static boolean pulseDue(long gameTime, int intervalTicks, long offset) {
        return intervalTicks > 0 && Math.floorMod(gameTime + offset, (long) intervalTicks) == 0L;
    }
}
