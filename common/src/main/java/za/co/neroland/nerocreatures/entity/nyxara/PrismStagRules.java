package za.co.neroland.nerocreatures.entity.nyxara;

/**
 * Pure rules for the Prism Stag (Nyxara livestock): antler shedding and the herd regeneration
 * aura. No game types, so every rule and cap is unit-tested.
 *
 * <h2>Antlers</h2>
 * A {@code ProductTimer} with {@value #ANTLER_STAGES} stages, advanced by
 * {@value #GROWTH_INTERVAL} ticks on a cadence while the stag is loaded and adult. Visual
 * {@code stage}: 0 bare (just shed), 1 half-grown, 2 full ({@link #visualStage}). When the timer is
 * ready the stag sheds: {@value #SHED_BASE} antlers (one per side) times the Bountiful roll, capped at
 * {@value #SHED_CAP}, and the timer restarts. The regrow time is the species' data-driven
 * {@code product_interval_ticks} ({@link #shedTicks}).
 *
 * <h2>Herd aura</h2>
 * Every {@value #AURA_INTERVAL} ticks an adult counts adult stags within {@value #HERD_RADIUS}
 * blocks (itself included, one bounded box query). At night, with at least {@value #MIN_HERD}, the
 * aura is live: every member lights its antlers ({@code form} {@value #FORM_AURA}) and only the herd
 * leader (lowest entity id among the counted adults) gives Regeneration I for
 * {@value #AURA_EFFECT_TICKS} ticks to at most {@value #AURA_MAX_PLAYERS} players within its radius.
 * A bigger herd never makes it stronger or longer; Keen widens the radius up to
 * {@value #AURA_MAX_RADIUS} blocks.
 */
public final class PrismStagRules {

    /** Default regrow time when {@code species.json} names none: half a Minecraft day of loaded time. */
    public static final int DEFAULT_SHED_TICKS = 12_000;
    /** Floor on any configured regrow time (one minute), so a data typo cannot make an antler fountain. */
    public static final int MIN_SHED_TICKS = 1_200;
    /** Timer stages: 0 bare, 1 half, 2 full, 3 ripe (sheds on the next cadence). */
    public static final int ANTLER_STAGES = 3;
    /** Highest visual stage (full antlers). */
    public static final int MAX_VISUAL_STAGE = 2;
    /** Ticks between growth checks. */
    public static final int GROWTH_INTERVAL = 20;
    /** Antlers per shed before Bountiful (one per side). */
    public static final int SHED_BASE = 2;
    /** Hard cap on antlers per shed, after Bountiful. */
    public static final int SHED_CAP = 4;
    /** Head-toss ({@code ability}) pose length. */
    public static final int SHED_POSE_TICKS = 20;

    /** Ticks between herd checks. */
    public static final int AURA_INTERVAL = 80;
    /** Radius (blocks) in which herd mates are counted. */
    public static final double HERD_RADIUS = 10.0D;
    /** Adult stags (the counting stag included) needed for the aura. */
    public static final int MIN_HERD = 3;
    /** Base radius (blocks) of the aura around the leader. */
    public static final double AURA_RADIUS = 8.0D;
    /** Largest aura radius after Keen. */
    public static final double AURA_MAX_RADIUS = 12.0D;
    /** Regeneration duration per pulse; slightly longer than the interval so it does not flicker. */
    public static final int AURA_EFFECT_TICKS = 100;
    /** Regeneration amplifier (0 = Regeneration I); never raised by herd size. */
    public static final int AURA_AMPLIFIER = 0;
    /** Hard cap on players given the aura per pulse. */
    public static final int AURA_MAX_PLAYERS = 8;
    /** Aura ({@code ability_2}: head lowered, antlers lit) pose length. */
    public static final int AURA_POSE_TICKS = 30;
    /** {@code form} while the aura is live: the model lights the antlers. */
    public static final int FORM_AURA = 1;

    private PrismStagRules() {
    }

    /** The regrow time for a species value: the default when unset, never below the floor. */
    public static int shedTicks(int configured) {
        return configured <= 0 ? DEFAULT_SHED_TICKS : Math.max(MIN_SHED_TICKS, configured);
    }

    /** The synced antler stage for a timer stage: 0 bare, 1 half, 2 full (ripe draws as full). */
    public static int visualStage(int timerStage) {
        return Math.max(0, Math.min(MAX_VISUAL_STAGE, timerStage));
    }

    /** Only an adult with ripe antlers sheds. */
    public static boolean mayShed(boolean ready, boolean adult) {
        return ready && adult;
    }

    /** Antlers in one shed: {@code base x rolls}, clamped to {@code 0..cap}. */
    public static int shedYield(int base, int rolls, int cap) {
        long yield = (long) Math.max(0, base) * Math.max(1, rolls);
        return (int) Math.max(0, Math.min(Math.max(0, cap), yield));
    }

    /** The aura is live at night, when enabled, with at least {@value #MIN_HERD} adults counted. */
    public static boolean auraLive(boolean enabled, boolean night, int adultsCounted) {
        return enabled && night && adultsCounted >= MIN_HERD;
    }

    /** One stag per herd applies the effect: the one with the lowest entity id among those counted. */
    public static boolean isLeader(int selfId, int lowestCountedId) {
        return selfId <= lowestCountedId;
    }

    /** Aura radius after the Keen multiplier, kept within {@code AURA_RADIUS..AURA_MAX_RADIUS}. */
    public static double auraRadius(double rangeMultiplier) {
        double r = AURA_RADIUS * (Double.isFinite(rangeMultiplier) ? rangeMultiplier : 1.0D);
        return Math.max(AURA_RADIUS, Math.min(AURA_MAX_RADIUS, r));
    }

    /** How many of {@code found} to affect, never more than {@code cap}. */
    public static int take(int found, int cap) {
        return Math.max(0, Math.min(found, cap));
    }
}
