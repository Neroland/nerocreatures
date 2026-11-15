package za.co.neroland.nerocreatures.entity.glacira;

/**
 * Pure rules for the Rime Yak (Glacira livestock): the cold aura's cadence, radius and caps, the
 * water-freezing budget, the mob chill that never reaches a full freeze, and the wool and milk
 * products. No game types, so every rule and cap is unit-tested.
 */
public final class RimeYakRules {

    /** Aura cadence: one pass every 2 s (decision 006: at most one frozen block per 2 s). */
    public static final int AURA_INTERVAL = 40;
    /** Entities within this many blocks are cooled (players) or chilled (hostile mobs). */
    public static final double AURA_RADIUS = 6.0D;
    /** Most entities one aura pass touches (a bounded query). */
    public static final int MAX_AURA_TARGETS = 12;
    /** Horizontal reach of water freezing (decision 006: radius 2). */
    public static final int FREEZE_RADIUS = 2;
    /** Hard cap: frozen water blocks per aura pass. */
    public static final int MAX_FREEZE_PER_PASS = 1;
    /** Hard cap: frosted ice blocks one yak has made that are still counted (see {@link #FROSTED_TRACK_TICKS}). */
    public static final int MAX_LIVE_FROSTED = 8;
    /** How long a block this yak froze counts against {@link #MAX_LIVE_FROSTED}. */
    public static final int FROSTED_TRACK_TICKS = 1200;
    /** Frosted ice melt check delay, as vanilla Frost Walker schedules it. */
    public static final int FROSTED_MELT_MIN_TICKS = 60;
    public static final int FROSTED_MELT_MAX_TICKS = 120;
    /** Frozen ticks the aura gives a hostile mob: slows it, always below a full freeze, so no damage. */
    public static final int MOB_CHILL_TICKS = 100;
    /** Aura edge ring of frost motes every 5 s (decision 007, section 1.4). */
    public static final int RING_EVERY_TICKS = 100;
    /** Breath vapour about every 4 s. */
    public static final int BREATH_EVERY_TICKS = 80;

    /** Wool growth: three visible stages; stage 3 is ready to shear, stage 0 is shorn. */
    public static final int WOOL_STAGES = 3;
    public static final int DEFAULT_WOOL_REGROW_TICKS = 6000;
    public static final int MIN_WOOL_REGROW_TICKS = 1200;
    public static final int WOOL_BASE_YIELD = 2;
    public static final int WOOL_YIELD_CAP = 6;
    /** Products advance in 20-tick steps from the server AI step (an unloaded yak does not grow). */
    public static final int GROWTH_STEP = 20;
    public static final int DEFAULT_MILK_COOLDOWN_TICKS = 2400;
    public static final int MIN_MILK_COOLDOWN_TICKS = 200;
    /** The shear and milk "shiver" pose length (hurt-length, decision 007 section 4.2). */
    public static final int SHIVER_TICKS = 6;

    private RimeYakRules() {
    }

    /** Whether something at squared distance {@code distSqr} is inside the aura. */
    public static boolean inAura(double distSqr) {
        return distSqr >= 0.0D && distSqr <= AURA_RADIUS * AURA_RADIUS;
    }

    /**
     * How many more blocks this pass may freeze: 0 when the griefing gate denied it, else bounded by
     * the per-pass cap and the live cap.
     */
    public static int freezeBudget(boolean allowed, int frozenThisPass, int live) {
        if (!allowed) {
            return 0;
        }
        int perPass = MAX_FREEZE_PER_PASS - Math.max(0, frozenThisPass);
        int liveRoom = MAX_LIVE_FROSTED - Math.max(0, live);
        return Math.max(0, Math.min(perPass, liveRoom));
    }

    /** Only a still water source with open air above and nothing standing in it is frozen. */
    public static boolean freezable(boolean stillWaterSource, boolean airAbove, boolean occupied) {
        return stillWaterSource && airAbove && !occupied;
    }

    /**
     * The frozen-tick value the aura leaves on a hostile mob: raised to {@link #MOB_CHILL_TICKS}
     * but always one short of {@code ticksToFullFreeze}, so the chill slows and never hurts.
     */
    public static int chillTicks(int current, int ticksToFullFreeze) {
        int ceiling = Math.max(0, ticksToFullFreeze - 1);
        int raised = Math.max(Math.max(0, current), MOB_CHILL_TICKS);
        return Math.min(raised, Math.max(ceiling, Math.max(0, current)));
    }

    /** Wool per shearing: {@code base x rolls} (Bountiful), clamped to {@code 0..cap}. */
    public static int woolYield(int base, int rolls, int cap) {
        if (cap <= 0 || base <= 0) {
            return 0;
        }
        long total = (long) base * Math.max(1, rolls);
        return (int) Math.min(cap, total);
    }

    /** Clamps a configured wool regrow time; anything below the floor uses the floor. */
    public static int woolRegrowTicks(int configured) {
        return Math.max(MIN_WOOL_REGROW_TICKS, configured);
    }

    /** Clamps a configured milk cooldown. */
    public static int milkCooldownTicks(int configured) {
        return Math.max(MIN_MILK_COOLDOWN_TICKS, configured);
    }

    /** An adult with its cooldown spent can be milked. */
    public static boolean mayMilk(boolean baby, int cooldownLeft) {
        return !baby && cooldownLeft <= 0;
    }

    /** The cooldown after one growth step of {@code step} ticks. */
    public static int coolDown(int cooldownLeft, int step) {
        return Math.max(0, cooldownLeft - Math.max(0, step));
    }
}
