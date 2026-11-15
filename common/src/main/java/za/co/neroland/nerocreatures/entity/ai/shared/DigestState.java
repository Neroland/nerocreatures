package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The pure "stomach" behind item-eating (Slag Beetle). Unit-tested.
 *
 * <p>Holds at most {@code capacity} eaten items as {@code itemId -> readyAt} entries, in eating
 * order. Digestion is sequential: each item takes {@code digestTicks} after the previous one
 * finished (or after it was eaten, if the stomach was empty). Times are game times, so nothing
 * needs ticking: {@link #pollReady} returns whatever has finished.
 *
 * <p>Persisted by the creature as one string ({@link #encode}/{@link #decode}); item ids only, no
 * player data. A save restored with a game time far behind {@code readyAt} simply finishes early.
 */
public final class DigestState {

    private static final char ENTRY = ';';
    private static final char FIELD = '@';

    private final int capacity;
    private final int digestTicks;
    private final Deque<Entry> queue = new ArrayDeque<>();

    /** One eaten item. */
    public record Entry(String itemId, long readyAt) {
    }

    public DigestState(int capacity, int digestTicks) {
        this.capacity = Math.max(1, capacity);
        this.digestTicks = Math.max(1, digestTicks);
    }

    public boolean hasRoom() {
        return this.queue.size() < this.capacity;
    }

    public int size() {
        return this.queue.size();
    }

    public boolean isEmpty() {
        return this.queue.isEmpty();
    }

    /** Swallows one item at {@code now}. Returns false when full. */
    public boolean eat(String itemId, long now) {
        if (!this.hasRoom() || itemId == null || itemId.isEmpty()) {
            return false;
        }
        long start = this.queue.isEmpty() ? now : Math.max(now, this.queue.peekLast().readyAt());
        this.queue.addLast(new Entry(itemId, start + this.digestTicks));
        return true;
    }

    /** Removes and returns every entry finished by {@code now}, oldest first. */
    public List<String> pollReady(long now) {
        List<String> done = new ArrayList<>(2);
        while (!this.queue.isEmpty() && this.queue.peekFirst().readyAt() <= now) {
            done.add(this.queue.pollFirst().itemId());
        }
        return done;
    }

    /**
     * Digestion progress of the head item, 0-3, for {@code CreatureVisuals.stage}; 0 when empty.
     */
    public int stage(long now) {
        Entry head = this.queue.peekFirst();
        if (head == null) {
            return 0;
        }
        long left = Math.max(0L, head.readyAt() - now);
        long done = this.digestTicks - Math.min(left, this.digestTicks);
        return 1 + (int) Math.min(2L, done * 3L / this.digestTicks);
    }

    /** Empties the stomach and returns what was in it (e.g. to drop on death). */
    public List<String> drain() {
        List<String> all = new ArrayList<>(this.queue.size());
        for (Entry e : this.queue) {
            all.add(e.itemId());
        }
        this.queue.clear();
        return all;
    }

    /** Compact save form: {@code id@readyAt;id@readyAt}. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : this.queue) {
            if (sb.length() > 0) {
                sb.append(ENTRY);
            }
            sb.append(e.itemId()).append(FIELD).append(e.readyAt());
        }
        return sb.toString();
    }

    /** Restores {@link #encode} output; malformed or excess entries are dropped, never thrown. */
    public void decode(String encoded) {
        this.queue.clear();
        if (encoded == null || encoded.isEmpty()) {
            return;
        }
        for (String part : encoded.split(String.valueOf(ENTRY))) {
            int at = part.lastIndexOf(FIELD);
            if (at <= 0 || !this.hasRoom()) {
                continue;
            }
            try {
                this.queue.addLast(new Entry(part.substring(0, at), Long.parseLong(part.substring(at + 1))));
            } catch (NumberFormatException ignored) {
                // A bad entry is lost, never a failed entity load.
            }
        }
    }
}
