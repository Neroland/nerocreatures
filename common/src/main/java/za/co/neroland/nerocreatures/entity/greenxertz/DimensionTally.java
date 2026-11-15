package za.co.neroland.nerocreatures.entity.greenxertz;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * A hard population cap per dimension for short-lived or rare creatures (Bloom Host sporelings,
 * Drift Leviathans). It remembers runtime entity ids only, never a UUID, a name or a position, and
 * nothing is saved: the tracked creatures are themselves never saved ({@code shouldBeSaved} is
 * false for them), so a restart starts from zero and is still correct.
 *
 * <p>Every count first prunes ids whose entity is gone (the {@code stillCounts} test: "is this id
 * still a live creature of the tracked kind in this dimension"). A stale id can therefore only
 * make the count <em>higher</em> for one call, never let the cap be exceeded. Pure Java (no game
 * types), so the cap is unit-tested; the creature supplies the lookup. Server thread only.
 */
public final class DimensionTally {

    private final Map<String, Set<Integer>> live = new HashMap<>();

    /** Live tracked ids in {@code dimension} after pruning. */
    public int count(String dimension, IntPredicate stillCounts) {
        Set<Integer> ids = this.live.get(dimension);
        if (ids == null) {
            return 0;
        }
        Iterator<Integer> it = ids.iterator();
        while (it.hasNext()) {
            if (!stillCounts.test(it.next())) {
                it.remove();
            }
        }
        return ids.size();
    }

    /** Adds {@code id} if the dimension is below {@code cap} (after pruning). Idempotent. */
    public boolean tryAdd(String dimension, int id, int cap, IntPredicate stillCounts) {
        Set<Integer> ids = this.live.computeIfAbsent(dimension, k -> new LinkedHashSet<>());
        if (ids.contains(id)) {
            return true;
        }
        if (cap <= 0 || this.count(dimension, stillCounts) >= cap) {
            return false;
        }
        ids.add(id);
        return true;
    }

    /** Adds {@code id} regardless of the cap (a creature that already exists, e.g. a named one). */
    public void force(String dimension, int id) {
        this.live.computeIfAbsent(dimension, k -> new LinkedHashSet<>()).add(id);
    }

    public void remove(String dimension, int id) {
        Set<Integer> ids = this.live.get(dimension);
        if (ids != null) {
            ids.remove(id);
        }
    }

    /** Test hook. */
    void clear() {
        this.live.clear();
    }
}
