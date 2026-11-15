package za.co.neroland.nerocreatures.entity.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Facet Colossus's arena crystals and refracting beam, as pure logic (unit-tested without a world).
 *
 * <h2>Crystals</h2>
 *
 * <p>Crystals are {@code ephemeral_crust[variant=prism, restore=air]} pillars, {@value #PILLAR_HEIGHT}
 * blocks tall, placed into <b>air only</b> through {@code CreatureGriefing.mayAlter} with the
 * {@code bossArenaBlocks} key (decision 006). They drop nothing, any player can break them, and each
 * one reverts to air by itself after {@value #DECAY_TICKS} ticks. Every crystal the boss still owns
 * is reverted when the fight ends (death or discard). Caps, always on:
 *
 * <ul>
 *   <li>pillars alive at once: {@link #maxPillars} per phase (3 / 4 / 5);</li>
 *   <li>at most {@value #PILLARS_PER_WAVE} new pillars per wave, one wave every
 *       {@value #WAVE_INTERVAL_TICKS} ticks;</li>
 *   <li>{@value #HARD_CAP_BLOCKS} crystal blocks per Colossus, whatever the phase.</li>
 * </ul>
 *
 * <h2>Beam</h2>
 *
 * <p>A beam is a straight ray of at most {@value #BEAM_RANGE} blocks. A crystal it meets turns it by
 * {@value #REFRACT_DEGREES} degrees (left or right, decided by the crystal's position, so the same
 * pillar always bends the same way and players can read it), at most {@value #MAX_BOUNCES} times. A
 * solid block stops it. Without crystals (the key or {@code mobGriefing} denies them) the beam splits
 * off the boss's own facets instead: two extra rays at plus and minus {@value #REFRACT_DEGREES}
 * degrees, {@value #SPLIT_RANGE} blocks long, so the fight keeps its shape with no block changes.
 *
 * <p>Timing: the {@code ability} pose lasts {@value #BEAM_POSE_TICKS} ticks. The aim locks at tick
 * {@value #BEAM_LOCK_TICK} and is drawn as a telegraph; the beam fires from tick
 * {@value #BEAM_TELEGRAPH_TICKS} and deals damage at ticks 20, 30 and 40 (at least 10 ticks after
 * the aim locks, so stepping out of the drawn line always works).
 */
public final class ArenaCrystalRules {

    public static final int PILLAR_HEIGHT = 2;
    public static final int PILLARS_PER_WAVE = 2;
    public static final int WAVE_INTERVAL_TICKS = 200;
    public static final int HARD_CAP_BLOCKS = 12;
    public static final int DECAY_TICKS = 900;
    public static final int MIN_RING = 6;
    public static final int MAX_RING = 14;
    public static final int PLACE_ATTEMPTS_PER_WAVE = 6;

    public static final double BEAM_RANGE = 32.0D;
    public static final double SPLIT_RANGE = 16.0D;
    public static final double REFRACT_DEGREES = 60.0D;
    public static final int MAX_BOUNCES = 3;
    public static final double BEAM_STEP = 0.5D;
    public static final double HIT_RADIUS = 0.9D;
    public static final int MAX_VICTIMS_PER_PULSE = 8;

    public static final int BEAM_POSE_TICKS = 50;
    public static final int BEAM_TELEGRAPH_TICKS = 20;
    public static final int BEAM_LOCK_TICK = 10;
    public static final int BEAM_PULSE_INTERVAL = 10;

    private ArenaCrystalRules() {
    }

    /** Pillars allowed alive at once in a phase. */
    public static int maxPillars(int phase) {
        return switch (phase) {
            case 2 -> 4;
            case 3 -> 5;
            default -> 3;
        };
    }

    /**
     * How many pillars a wave may place.
     *
     * @param allowed     the griefing gate passed (key, mobGriefing); false places nothing
     * @param aliveBlocks crystal blocks this boss still owns
     */
    public static int pillarsToPlace(int phase, int aliveBlocks, boolean allowed) {
        if (!allowed) {
            return 0;
        }
        int alivePillars = (aliveBlocks + PILLAR_HEIGHT - 1) / PILLAR_HEIGHT;
        int byPhase = maxPillars(phase) - alivePillars;
        int byHardCap = (HARD_CAP_BLOCKS - aliveBlocks) / PILLAR_HEIGHT;
        return Math.max(0, Math.min(PILLARS_PER_WAVE, Math.min(byPhase, byHardCap)));
    }

    /** The yaw a beam leaves a crystal at; the same crystal always bends the same way. */
    public static double refract(double yaw, long cellKey) {
        long mixed = cellKey * 0x9E3779B97F4A7C15L;
        boolean left = ((mixed >>> 32) & 1L) == 0L;
        return yaw + (left ? -REFRACT_DEGREES : REFRACT_DEGREES);
    }

    /** Whether the beam deals damage on this tick of the pose. */
    public static boolean pulseNow(int tickIntoPose) {
        return tickIntoPose >= BEAM_TELEGRAPH_TICKS && tickIntoPose < BEAM_POSE_TICKS
                && (tickIntoPose - BEAM_TELEGRAPH_TICKS) % BEAM_PULSE_INTERVAL == 0;
    }

    /** Whether the beam is live (drawn and dangerous) on this tick of the pose. */
    public static boolean firing(int tickIntoPose) {
        return tickIntoPose >= BEAM_TELEGRAPH_TICKS && tickIntoPose < BEAM_POSE_TICKS;
    }

    /** Ticks between beams in a phase, before the difficulty scaling. */
    public static int beamCooldownTicks(int phase) {
        return switch (phase) {
            case 2 -> 160;
            case 3 -> 120;
            default -> 200;
        };
    }

    /** Beam damage per pulse in a phase. */
    public static float beamDamage(int phase) {
        return switch (phase) {
            case 2 -> 8.0F;
            case 3 -> 10.0F;
            default -> 6.0F;
        };
    }

    /**
     * The crystal blocks one Colossus owns, keyed by packed block position, with the game time each
     * one expires. The boss reverts every live entry when the fight ends; expired entries have
     * reverted on their own and are simply forgotten.
     */
    public static final class CrystalLedger {

        private record Entry(long pos, long expiresAt) {
        }

        private final ArrayDeque<Entry> entries = new ArrayDeque<>();

        /** Records a placed crystal. Ignored past {@link #HARD_CAP_BLOCKS} (the caller checks first). */
        public boolean add(long pos, long expiresAt) {
            if (this.entries.size() >= HARD_CAP_BLOCKS) {
                return false;
            }
            this.entries.addLast(new Entry(pos, expiresAt));
            return true;
        }

        /** Forgets entries whose decay has passed. */
        public void prune(long now) {
            this.entries.removeIf(e -> e.expiresAt() <= now);
        }

        /** Forgets one position (a player broke it). */
        public void forget(long pos) {
            this.entries.removeIf(e -> e.pos() == pos);
        }

        public int size() {
            return this.entries.size();
        }

        /** Every still-owned position, emptying the ledger: the caller reverts each one. */
        public List<Long> drainForRestore() {
            List<Long> out = new ArrayList<>(this.entries.size());
            for (Iterator<Entry> it = this.entries.iterator(); it.hasNext();) {
                out.add(it.next().pos());
            }
            this.entries.clear();
            return out;
        }

        /** Every owned position, without changing the ledger. */
        public List<Long> positions() {
            List<Long> out = new ArrayList<>(this.entries.size());
            for (Entry e : this.entries) {
                out.add(e.pos());
            }
            return out;
        }
    }
}
