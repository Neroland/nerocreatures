package za.co.neroland.nerocreatures.block;

/**
 * {@link EphemeralCrustBlock}'s rules as pure logic (unit-tested without a world).
 *
 * <p>A crust block always heals the world by itself: its scheduled tick turns it back into its
 * {@code restore} block. Two safety nets cover the cases a scheduled tick cannot:
 * <ul>
 *   <li>a random tick that finds no pending scheduled tick (a tick list lost to a crash or an
 *       external edit) reverts the block at once, so no crust is ever permanent;</li>
 *   <li>a crust that stood in a lava or water source and is broken by a player or an explosion puts
 *       the source back instead of leaving a hole: the world never loses a fluid source to it.</li>
 * </ul>
 */
public final class EphemeralCrustRules {

    /** Shortest and longest decay any caller may ask for, in ticks (1 s .. 10 min). */
    public static final int MIN_DECAY_TICKS = 20;
    public static final int MAX_DECAY_TICKS = 12_000;

    private EphemeralCrustRules() {
    }

    /** Clamps a requested decay time into the allowed window. */
    public static int clampDecay(int requestedTicks) {
        return Math.max(MIN_DECAY_TICKS, Math.min(MAX_DECAY_TICKS, requestedTicks));
    }

    /** A random tick reverts the block only when its scheduled tick has gone missing. */
    public static boolean revertOnRandomTick(boolean scheduledTickPending) {
        return !scheduledTickPending;
    }

    /**
     * After the block was broken (by a player or an explosion): whether to put its fluid source back.
     *
     * @param restoresFluid the block's {@code restore} is lava or water
     * @param cellNowEmpty  the cell is air after the break (nothing else has taken it)
     */
    public static boolean refillAfterBreak(boolean restoresFluid, boolean cellNowEmpty) {
        return restoresFluid && cellNowEmpty;
    }
}
