package za.co.neroland.nerocreatures.block;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * The Apex Altar's rules as pure logic (unit-tested without a world).
 *
 * <h2>One sigil, one fight</h2>
 *
 * <p>Using a sigil on the altar runs {@link State#attempt} on the server thread, which is atomic with
 * respect to every other interaction: it checks, summons, and only when the summon returned a live
 * fight does it record the fight, start the cooldown and report {@code consumed = true}, which is the
 * one place the caller shrinks the stack. A refused or failed attempt changes nothing and consumes
 * nothing. A second click in the same tick finds the fight it just started and is refused.
 *
 * <h2>Lock</h2>
 *
 * <p>The altar is <b>locked</b> while its fight is live or its cooldown runs. A locked altar cannot be
 * mined (so breaking and re-placing it cannot skip the cooldown) and refuses every sigil. A fight is
 * live while the boss is in the live fight index or its contribution row still exists; the lock on a
 * fight is released after {@value #MAX_FIGHT_LOCK_TICKS} ticks whatever happens, so a lost boss can
 * never brick an altar.
 *
 * <p>Everything here is about the block. Nothing names, counts or remembers a player.
 */
public final class ApexAltarRules {

    /** Default per-altar cooldown: 10 minutes. Config {@code apexAltarCooldownTicks}. */
    public static final int DEFAULT_COOLDOWN_TICKS = 12_000;

    /** Upper bound for the cooldown config (one in-game week of ticks). */
    public static final int MAX_COOLDOWN_TICKS = 1_008_000;

    /** A fight older than this no longer locks its altar (one hour of game time). */
    public static final long MAX_FIGHT_LOCK_TICKS = 72_000L;

    /** Ticks between the altar's lock refresh (block-state sync). */
    public static final int REFRESH_TICKS = 20;

    /** The answer to "may this sigil summon now?". */
    public enum Verdict {
        SUMMON, NOT_A_SIGIL, FIGHT_LIVE, COOLING_DOWN, UNKNOWN_BOSS, NO_ROOM, SUMMON_FAILED
    }

    private ApexAltarRules() {
    }

    /** The checks, in order. */
    public static Verdict decide(boolean isSigil, boolean bossKnown, long now, long readyAt, boolean fightLive,
            boolean headroom) {
        if (!isSigil) {
            return Verdict.NOT_A_SIGIL;
        }
        if (fightLive) {
            return Verdict.FIGHT_LIVE;
        }
        if (now < readyAt) {
            return Verdict.COOLING_DOWN;
        }
        if (!bossKnown) {
            return Verdict.UNKNOWN_BOSS;
        }
        if (!headroom) {
            return Verdict.NO_ROOM;
        }
        return Verdict.SUMMON;
    }

    /** When the cooldown that starts {@code now} ends. Negative config values count as 0. */
    public static long readyAt(long now, int cooldownTicks) {
        return now + Math.max(0, Math.min(MAX_COOLDOWN_TICKS, cooldownTicks));
    }

    /** Whole seconds left on a cooldown, rounded up; 0 when ready. */
    public static long secondsLeft(long now, long readyAt) {
        return now >= readyAt ? 0L : (readyAt - now + 19L) / 20L;
    }

    /** The altar's mutable state, owned by its block entity and saved with it. */
    public static final class State {

        /** Runs the summon; returns the new fight's id, or {@code null} when nothing was summoned. */
        @FunctionalInterface
        public interface Summon {
            @Nullable
            UUID run();
        }

        /** What an attempt did. {@code consumed} is true exactly when a fight was started. */
        public record Outcome(Verdict verdict, boolean consumed, @Nullable UUID fight) {
        }

        private long readyAt;
        private long fightStartedAt;
        @Nullable
        private UUID fight;
        private String sigil = "";

        public Outcome attempt(long now, boolean isSigil, boolean bossKnown, boolean headroom, boolean fightProbe,
                int cooldownTicks, String sigilName, Summon summon) {
            Verdict verdict = decide(isSigil, bossKnown, now, this.readyAt, this.fightLive(now, fightProbe), headroom);
            if (verdict != Verdict.SUMMON) {
                return new Outcome(verdict, false, null);
            }
            UUID started = summon.run();
            if (started == null) {
                return new Outcome(Verdict.SUMMON_FAILED, false, null);
            }
            this.fight = started;
            this.fightStartedAt = now;
            this.readyAt = ApexAltarRules.readyAt(now, cooldownTicks);
            this.sigil = sigilName == null ? "" : sigilName;
            return new Outcome(Verdict.SUMMON, true, started);
        }

        /**
         * Whether this altar's fight is still live.
         *
         * @param probe the world says the fight is live (live index or contribution row)
         */
        public boolean fightLive(long now, boolean probe) {
            return this.fight != null && probe && now >= this.fightStartedAt
                    && now - this.fightStartedAt < MAX_FIGHT_LOCK_TICKS;
        }

        /** Locked: a live fight or a running cooldown. */
        public boolean locked(long now, boolean probe) {
            return this.fightLive(now, probe) || now < this.readyAt;
        }

        /** Drops the fight id once its fight is over, so nothing outlives it. */
        public void clearFightIfOver(long now, boolean probe) {
            if (this.fight != null && !this.fightLive(now, probe)) {
                this.fight = null;
            }
        }

        @Nullable
        public UUID fight() {
            return this.fight;
        }

        public long readyAt() {
            return this.readyAt;
        }

        public long fightStartedAt() {
            return this.fightStartedAt;
        }

        public String sigil() {
            return this.sigil;
        }

        /** Restores saved values (load). */
        public void restore(long readyAt, long fightStartedAt, @Nullable UUID fight, String sigil) {
            this.readyAt = readyAt;
            this.fightStartedAt = fightStartedAt;
            this.fight = fight;
            this.sigil = sigil == null ? "" : sigil;
        }
    }
}
