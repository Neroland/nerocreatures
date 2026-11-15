package za.co.neroland.nerocreatures.entity.ai.shared;

/**
 * A "run this every N ticks" gate for goals that scan the world.
 *
 * <p>Vanilla only evaluates {@code canUse()} of idle goals on every other tick, so a plain
 * {@code gameTime % interval == 0} test can miss its tick forever. This timer instead remembers
 * the next game time it is due and fires on the first check at or after it, then re-arms. The
 * first due time is offset by a per-entity phase so a herd spawned in one tick does not scan in
 * lock-step.
 *
 * <p>Pure Java (no game types) so the rule is unit-tested.
 */
public final class CadenceTimer {

    private final int interval;
    private long nextDue = Long.MIN_VALUE;

    /** @param interval ticks between firings; values below 1 are treated as 1 */
    public CadenceTimer(int interval) {
        this.interval = Math.max(1, interval);
    }

    public int interval() {
        return this.interval;
    }

    /**
     * True at most once per {@link #interval()} ticks.
     *
     * @param gameTime the level's game time
     * @param phaseSeed any stable per-entity number (the entity id); spreads the first firing
     */
    public boolean ready(long gameTime, int phaseSeed) {
        if (this.nextDue == Long.MIN_VALUE) {
            this.nextDue = gameTime + Math.floorMod(phaseSeed, this.interval);
        }
        if (gameTime < this.nextDue) {
            return false;
        }
        this.nextDue = gameTime + this.interval;
        return true;
    }

    /** Forces the next {@link #ready} check to fire (e.g. after the creature was hurt). */
    public void fireNext() {
        this.nextDue = Long.MIN_VALUE + 1;
    }

    /** Pushes the next firing out to {@code gameTime + delay}. */
    public void delay(long gameTime, int delay) {
        this.nextDue = gameTime + Math.max(0, delay);
    }
}
