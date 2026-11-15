package za.co.neroland.nerocreatures.bestiary;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Kind;

/**
 * The pure Bestiary rules (decisions 002 and 010): which action studies a species, its goal, the
 * progress shown for it, the one-time Studied reward and the scan timing. No Minecraft state, so
 * every rule is unit-tested on a plain JVM.
 */
public final class BestiaryRules {

    /** Scan reach and the hit-box inflation of the ray (decision 010 section 1). */
    public static final double SCAN_RANGE = 8.0D;
    public static final double SCAN_INFLATE = 0.3D;
    /** On-target server ticks a scan needs. */
    public static final int SCAN_TICKS = 40;
    /** Consecutive off-target ticks that are forgiven; one more resets the scan. */
    public static final int SCAN_GRACE_TICKS = 5;
    /** Item cooldown after a completed scan. */
    public static final int SCAN_COOLDOWN_TICKS = 20;

    /** Kill goals when a species has no explicit {@code studied_threshold} row (decision 002). */
    public static final int KILLS_COMMON = 5;
    public static final int KILLS_ELITE = 3;

    /** The one-time Studied reward in experience points (orchestrator ruling on 010 §11.3). */
    public static final int REWARD_XP = 25;
    public static final int REWARD_XP_ELITE = 60;
    public static final int REWARD_XP_BOSS = 150;

    /** How a species is studied; the byte is the {@code bestiary_counts} wire value. */
    public enum StudyKind {
        KILLS, TAME_OR_BREED, HARVEST_OR_BREED, BOSS_FIGHT;

        public byte wire() {
            return (byte) this.ordinal();
        }
    }

    /** Discovery tier; the byte is the wire value (0 is never sent). */
    public enum Tier {
        NONE, SCANNED, STUDIED;

        public byte wire() {
            return (byte) this.ordinal();
        }

        public String key() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private BestiaryRules() {
    }

    /** Decision 002's table, by kind chip. A neutral (the Crystal Golem) is studied by defeating it. */
    public static StudyKind studyKind(Kind kind) {
        return switch (kind) {
            case TAMEABLE, DRONE, MOUNT -> StudyKind.TAME_OR_BREED;
            case LIVESTOCK, AMBIENT -> StudyKind.HARVEST_OR_BREED;
            case BOSS -> StudyKind.BOSS_FIGHT;
            case NEUTRAL, HOSTILE, ELITE -> StudyKind.KILLS;
        };
    }

    /**
     * The goal. Kill goals come from the species row when it names one ({@code explicitThreshold}
     * > 0), else 5 (3 for an elite). Every other kind is 1: decision 002 keeps no counter for tames,
     * breeds, harvests or fights, so the gate itself is the record.
     */
    public static int goal(Kind kind, int explicitThreshold) {
        if (studyKind(kind) != StudyKind.KILLS) {
            return 1;
        }
        if (explicitThreshold > 0) {
            return Math.min(1000, explicitThreshold);
        }
        return kind == Kind.ELITE ? KILLS_ELITE : KILLS_COMMON;
    }

    /** Progress for the payload: kills clamp to the goal; other kinds are 0 or the goal. */
    public static int progress(StudyKind study, int goal, long kills, boolean studied) {
        if (studied) {
            return goal;
        }
        if (study == StudyKind.KILLS) {
            return (int) Math.max(0L, Math.min(goal, kills));
        }
        return 0;
    }

    /** Whether a kill count meets a kill goal. */
    public static boolean killsMeetGoal(long kills, int goal) {
        return kills >= goal;
    }

    /** The Studied reward in experience points. */
    public static int rewardXp(Kind kind) {
        return switch (kind) {
            case BOSS -> REWARD_XP_BOSS;
            case ELITE -> REWARD_XP_ELITE;
            default -> REWARD_XP;
        };
    }

    /**
     * One player's scan in progress (target network id, on-target ticks, off-target ticks). Not
     * saved, not keyed by identity on disk: it lives in a transient server map for the session.
     */
    public static final class ScanProgress {
        private int target = -1;
        private int ticks;
        private int offTicks;
        private long lastTick = Long.MIN_VALUE;

        /** What one tick did. */
        public enum Step {
            PROGRESS, COMPLETE, NO_TARGET, LOST
        }

        /**
         * Advances by one server tick. {@code targetId} is the valid target under the ray this tick,
         * or -1. A different target restarts at 1; six off-target ticks in a row lose the scan; a gap
         * in ticks (the use was interrupted) restarts too.
         */
        public Step tick(long now, int targetId) {
            if (this.lastTick != Long.MIN_VALUE && now - this.lastTick > 1) {
                this.reset();
            }
            this.lastTick = now;
            if (targetId < 0) {
                if (this.target < 0) {
                    return Step.NO_TARGET;
                }
                this.offTicks++;
                if (this.offTicks > SCAN_GRACE_TICKS) {
                    this.reset();
                    this.lastTick = now;
                    return Step.LOST;
                }
                return Step.PROGRESS;
            }
            if (targetId != this.target) {
                this.target = targetId;
                this.ticks = 0;
            }
            this.offTicks = 0;
            this.ticks++;
            if (this.ticks >= SCAN_TICKS) {
                this.reset();
                this.lastTick = now;
                return Step.COMPLETE;
            }
            return Step.PROGRESS;
        }

        public void reset() {
            this.target = -1;
            this.ticks = 0;
            this.offTicks = 0;
            this.lastTick = Long.MIN_VALUE;
        }

        public int target() {
            return this.target;
        }

        public int ticks() {
            return this.ticks;
        }
    }
}
