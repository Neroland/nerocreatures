package za.co.neroland.nerocreatures.entity.boss;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * A hard-capped set of block positions (packed with {@code BlockPos.asLong}) that a boss arena has
 * changed and must give back. Pure Java so the caps and the restore-once rule are unit-tested.
 *
 * <p>Every arena block is also a self-reverting {@code ephemeral_crust}, so the world heals even if
 * this ledger is lost (server stop, crash). The ledger exists so a fight that <em>ends</em> — death,
 * discard — restores its arena at once instead of waiting for the decay timer.
 */
public final class ArenaLedger {

    private final int cap;
    private final Set<Long> positions = new LinkedHashSet<>();

    public ArenaLedger(int cap) {
        if (cap <= 0) {
            throw new IllegalArgumentException("cap must be positive");
        }
        this.cap = cap;
    }

    /** Records a position if there is room. A position already recorded counts as accepted. */
    public boolean tryAdd(long pos) {
        if (this.positions.contains(pos)) {
            return true;
        }
        if (this.positions.size() >= this.cap) {
            return false;
        }
        this.positions.add(pos);
        return true;
    }

    /** Whether one more position would fit. */
    public boolean hasRoom() {
        return this.positions.size() < this.cap;
    }

    public int size() {
        return this.positions.size();
    }

    public int cap() {
        return this.cap;
    }

    public boolean contains(long pos) {
        return this.positions.contains(pos);
    }

    /** Drops every position whose block is no longer ours (decayed, broken, blown up). */
    public int prune(LongPredicate stillOurs) {
        int before = this.positions.size();
        this.positions.removeIf(pos -> !stillOurs.test(pos));
        return before - this.positions.size();
    }

    /**
     * Hands back every position and empties the ledger, so a second call (a death followed by a
     * removal) restores nothing twice.
     */
    public List<Long> drain() {
        List<Long> out = new ArrayList<>(this.positions);
        this.positions.clear();
        return out;
    }
}
