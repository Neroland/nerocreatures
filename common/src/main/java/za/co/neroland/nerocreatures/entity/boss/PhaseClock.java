package za.co.neroland.nerocreatures.entity.boss;

/**
 * The 30-tick phase transition: when the boss's phase number changes it
 * holds still for {@value #TRANSITION_TICKS} ticks (the new phase pose blends in) and starts no
 * ability. Pure Java; the boss ticks it once per server tick with the controller's phase number.
 */
public final class PhaseClock {

    /** Length of a phase transition, in ticks. */
    public static final int TRANSITION_TICKS = 30;

    private int phase;
    private int transition;

    /**
     * Advances one tick.
     *
     * @param currentPhase the controller's 1-based phase number this tick
     * @return {@code true} if this tick started a transition (the phase changed)
     */
    public boolean tick(int currentPhase) {
        if (this.transition > 0) {
            this.transition--;
        }
        if (currentPhase == this.phase) {
            return false;
        }
        boolean first = this.phase == 0;
        this.phase = currentPhase;
        // The very first phase (spawn or load) is not a transition: the fight just starts.
        if (!first) {
            this.transition = TRANSITION_TICKS;
            return true;
        }
        return false;
    }

    /** Whether the boss is mid-transition and must not start an ability. */
    public boolean busy() {
        return this.transition > 0;
    }

    public int phase() {
        return this.phase;
    }
}
