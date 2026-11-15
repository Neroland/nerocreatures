package za.co.neroland.nerocreatures.entity.boss;

/**
 * Pure rules for the Rime Matriarch fight (unit-tested; no Minecraft types).
 *
 * <h2>Blizzard beam</h2>
 * The {@code ability} clip is {@value #BEAM_CLIP_TICKS} ticks: telegraph 0–20, beam 20–40, recovery
 * 40–50 (decision 007 / art spec). The beam fires along the direction locked at the end of the
 * telegraph and is stopped by the first solid block — the breakable ice pillars are the cover. When
 * arena blocks are denied (decision 006 fallback) there are no pillars, so the telegraph is
 * {@value #DENIED_EXTRA_TELEGRAPH} ticks longer (twice as long in total) and any solid block is cover.
 *
 * <h2>Freezing floor</h2>
 * The floor "freezes" in stages: a status zone whose radius grows per phase (freeze ticks on players
 * standing in it) plus still water sources turned into self-reverting rime crust. Both are capped.
 */
public final class MatriarchRules {

    private MatriarchRules() {
    }

    // --- phases -----------------------------------------------------------------------------

    /** Health fraction at which phase 2 (the floor spreads, more pillars) begins. */
    public static final float PHASE_2 = 0.66F;

    /** Health fraction at which phase 3 (risen, fast beams, deep freeze) begins. */
    public static final float PHASE_3 = 0.33F;

    /** Arena radius around the summon point, in blocks. */
    public static final double ARENA_RADIUS = 24.0D;

    // --- beam -------------------------------------------------------------------------------

    /** Length of the {@code ability} clip. */
    public static final int BEAM_CLIP_TICKS = 50;

    /** Telegraph inside the clip. */
    public static final int TELEGRAPH_TICKS = 20;

    /** Last tick (exclusive) of the beam inside the clip. */
    public static final int BEAM_END_TICK = 40;

    /** Extra telegraph before the clip when arena blocks are denied (makes it twice as long). */
    public static final int DENIED_EXTRA_TELEGRAPH = 20;

    /** The beam deals damage every this many ticks while it runs. */
    public static final int BEAM_DAMAGE_INTERVAL = 4;

    /** How far the beam reaches, in blocks. */
    public static final double BEAM_RANGE = 24.0D;

    /** Players a single beam pulse may hit. */
    public static final int MAX_BEAM_VICTIMS = 8;

    /** Beam cooldown per phase (before {@code bossDifficultyMultiplier} scaling). */
    public static int beamCooldown(int phase) {
        return switch (clampPhase(phase)) {
            case 1 -> 160;
            case 2 -> 120;
            default -> 80;
        };
    }

    /** Damage per beam pulse per phase. */
    public static float beamDamage(int phase) {
        return switch (clampPhase(phase)) {
            case 1 -> 4.0F;
            case 2 -> 5.0F;
            default -> 6.0F;
        };
    }

    /** Ticks of telegraph before the clip starts: 0 normally, {@value #DENIED_EXTRA_TELEGRAPH} if denied. */
    public static int preTelegraph(boolean arenaBlocksAllowed) {
        return arenaBlocksAllowed ? 0 : DENIED_EXTRA_TELEGRAPH;
    }

    /** Total telegraph the players get to find cover. */
    public static int totalTelegraph(boolean arenaBlocksAllowed) {
        return TELEGRAPH_TICKS + preTelegraph(arenaBlocksAllowed);
    }

    /** Whether the beam is live at {@code clipTick} (0-based tick inside the 50-tick clip). */
    public static boolean beamLive(int clipTick) {
        return clipTick >= TELEGRAPH_TICKS && clipTick < BEAM_END_TICK;
    }

    /** Whether the beam deals damage at {@code clipTick}. */
    public static boolean beamPulse(int clipTick) {
        return beamLive(clipTick) && (clipTick - TELEGRAPH_TICKS) % BEAM_DAMAGE_INTERVAL == 0;
    }

    /**
     * Whether a victim at {@code victimDistance} along the beam is reached, given the first block the
     * beam meets at {@code blockDistance} ({@code Double.POSITIVE_INFINITY} if none).
     */
    public static boolean beamReaches(double blockDistance, double victimDistance) {
        return victimDistance <= BEAM_RANGE && victimDistance < blockDistance;
    }

    // --- pillars ----------------------------------------------------------------------------

    /** Height of one ice pillar, in blocks. */
    public static final int PILLAR_HEIGHT = 3;

    /** Hard cap on standing pillars at any time (all phases). */
    public static final int MAX_PILLARS = 6;

    /** Hard cap on pillar blocks; {@link #MAX_PILLARS} x {@link #PILLAR_HEIGHT}. */
    public static final int MAX_PILLAR_BLOCKS = MAX_PILLARS * PILLAR_HEIGHT;

    /** Pillars ring the boss at this distance from the arena centre. */
    public static final double PILLAR_RING = 7.0D;

    /** How long a pillar lasts before it melts back to air on its own, in ticks. */
    public static final int PILLAR_DECAY_TICKS = 1200;

    /** Ticks between pillar top-ups. */
    public static final int PILLAR_INTERVAL = 200;

    /** Pillars the boss keeps standing in a phase (never above {@link #MAX_PILLARS}). */
    public static int pillarTarget(int phase) {
        return Math.min(MAX_PILLARS, 2 + clampPhase(phase));
    }

    /** How many new pillars to raise now, given how many still stand. 0 when arena blocks are denied. */
    public static int pillarsToRaise(int phase, int standing, boolean arenaBlocksAllowed) {
        if (!arenaBlocksAllowed) {
            return 0;
        }
        return Math.max(0, pillarTarget(phase) - Math.max(0, standing));
    }

    // --- freezing floor ---------------------------------------------------------------------

    /** Hard cap on frozen water blocks at any time. */
    public static final int MAX_FROZEN_BLOCKS = 64;

    /** Water blocks frozen per pass at most. */
    public static final int FREEZE_PER_PASS = 8;

    /** Ticks between floor passes (status zone and freezing). */
    public static final int FLOOR_INTERVAL = 20;

    /** How long a frozen water block stays before it melts back, in ticks (refreshed while it lasts). */
    public static final int FROZEN_DECAY_TICKS = 600;

    /** Radius of the freezing floor per phase: it spreads in stages. */
    public static int floorRadius(int phase) {
        return switch (clampPhase(phase)) {
            case 1 -> 5;
            case 2 -> 9;
            default -> 13;
        };
    }

    /** Freeze ticks added per floor pass to a player standing in the zone. */
    public static final int FREEZE_PER_PASS_TICKS = 30;

    /**
     * New frozen-ticks value for a player on the freezing floor. Phases 1–2 stop one tick short of
     * the vanilla full freeze (slowed, never damaged); phase 3 may reach a little past it.
     */
    public static int freezeTicks(int current, int requiredToFreeze, int phase) {
        int ceiling = clampPhase(phase) >= 3 ? requiredToFreeze + 40 : requiredToFreeze - 1;
        return Math.max(Math.max(0, current), Math.min(ceiling, Math.max(0, current) + FREEZE_PER_PASS_TICKS));
    }

    static int clampPhase(int phase) {
        return Math.max(1, Math.min(3, phase));
    }
}
