package za.co.neroland.nerocreatures.entity.boss;

/**
 * Makes the end of a boss fight happen exactly once, whichever route gets there first. Pure Java.
 *
 * <p>A boss leaves the world by death (then a {@code KILLED} removal), by being discarded
 * ({@code /kill} on some lines, a command, a despawn), or by unloading with its chunk. The first two
 * end the fight: the arena is restored and adds are cleared. Unloading does not end anything — the
 * boss is saved and comes back. Death followed by its removal must not restore or clear twice, and
 * loot is never paid by this path at all (vanilla drops the loot table once, in {@code die}).
 */
public final class FightEndLatch {

    /** Why the boss is leaving. */
    public enum Exit {
        DEATH,
        DISCARD,
        UNLOAD
    }

    private boolean ended;

    /**
     * @return {@code true} exactly once: for the first {@link Exit#DEATH} or {@link Exit#DISCARD}.
     *         Always {@code false} for {@link Exit#UNLOAD}.
     */
    public boolean claim(Exit exit) {
        if (exit == Exit.UNLOAD || this.ended) {
            return false;
        }
        this.ended = true;
        return true;
    }

    public boolean ended() {
        return this.ended;
    }
}
