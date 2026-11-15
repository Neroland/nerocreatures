package za.co.neroland.nerocreatures.entity.luna;

/**
 * The Lumen Moth's reveal pulse: a tamed moth near its owner outlines hidden creatures nearby with
 * vanilla Glowing. Pure and unit-tested.
 *
 * <ul>
 *   <li>Revealed: burrowed creatures ({@code Burrower}), lurking ambushers ({@code Ambusher}),
 *       invisible ("cloaked") mobs, and anything in {@code #nerocreatures:lumen_reveals} (the Cache
 *       Mimic, for one).</li>
 *   <li><b>Never players.</b> An invisible player stays invisible; a pet is not a way to find
 *       people.</li>
 *   <li>Capped at {@link #MAX_REVEALED_PER_PULSE} per pulse; radius {@link #BASE_RADIUS} blocks,
 *       scaled by the Keen trait and clamped to {@link #MAX_RADIUS}.</li>
 * </ul>
 */
public final class RevealRules {

    public static final double BASE_RADIUS = 10.0D;
    public static final double MAX_RADIUS = 16.0D;
    public static final int MAX_REVEALED_PER_PULSE = 8;
    /** Glowing lasts a little longer than the 40-tick perk interval, so it does not blink. */
    public static final int GLOW_TICKS = 60;

    private RevealRules() {
    }

    public static boolean shouldReveal(boolean isPlayer, boolean invisible, boolean burrowed, boolean lurking,
            boolean tagged) {
        if (isPlayer) {
            return false;
        }
        return invisible || burrowed || lurking || tagged;
    }

    /** Reveal radius for an ability-range multiplier (Keen is 1.25); never below 1, never above max. */
    public static double radius(double rangeMultiplier) {
        double m = Double.isFinite(rangeMultiplier) && rangeMultiplier > 0.0D ? rangeMultiplier : 1.0D;
        return Math.min(MAX_RADIUS, BASE_RADIUS * Math.max(1.0D, m));
    }

    /** How many of {@code candidates} one pulse may reveal. */
    public static int take(int candidates) {
        return Math.max(0, Math.min(candidates, MAX_REVEALED_PER_PULSE));
    }
}
