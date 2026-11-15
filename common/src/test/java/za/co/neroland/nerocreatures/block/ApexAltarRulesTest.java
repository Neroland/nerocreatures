package za.co.neroland.nerocreatures.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.block.ApexAltarRules.State;
import za.co.neroland.nerocreatures.block.ApexAltarRules.State.Outcome;
import za.co.neroland.nerocreatures.block.ApexAltarRules.Verdict;

class ApexAltarRulesTest {

    private static final int COOLDOWN = 1200;

    /** Summons count how often the world was asked for a boss. */
    private static final class World {
        final AtomicInteger summons = new AtomicInteger();
        boolean succeeds = true;
        boolean live;

        State.Summon summon() {
            return () -> {
                this.summons.incrementAndGet();
                if (!this.succeeds) {
                    return null;
                }
                this.live = true;
                return UUID.randomUUID();
            };
        }
    }

    private static Outcome use(State state, World world, long now) {
        return state.attempt(now, true, true, true, world.live, COOLDOWN, "FACET_COLOSSUS", world.summon());
    }

    @Test
    void oneSigilOneFightEvenOnADoubleClick() {
        State state = new State();
        World world = new World();
        Outcome first = use(state, world, 100L);
        Outcome second = use(state, world, 100L);
        assertEquals(Verdict.SUMMON, first.verdict());
        assertTrue(first.consumed());
        assertEquals(Verdict.FIGHT_LIVE, second.verdict());
        assertFalse(second.consumed(), "the second click consumes nothing");
        assertEquals(1, world.summons.get(), "and summons nothing");
    }

    @Test
    void failedSummonConsumesNothingAndStartsNoCooldown() {
        State state = new State();
        World world = new World();
        world.succeeds = false;
        Outcome out = use(state, world, 100L);
        assertEquals(Verdict.SUMMON_FAILED, out.verdict());
        assertFalse(out.consumed());
        assertNull(state.fight());
        assertFalse(state.locked(100L, false));
        world.succeeds = true;
        assertTrue(use(state, world, 101L).consumed(), "the sigil still works afterwards");
    }

    @Test
    void cooldownHoldsAfterTheFightEndsAndThenReleases() {
        State state = new State();
        World world = new World();
        assertTrue(use(state, world, 1000L).consumed());
        world.live = false; // the boss died at once
        Outcome during = use(state, world, 1000L + COOLDOWN - 1);
        assertEquals(Verdict.COOLING_DOWN, during.verdict());
        assertFalse(during.consumed());
        assertTrue(state.locked(1000L + COOLDOWN - 1, false));
        assertFalse(state.locked(1000L + COOLDOWN, false));
        assertTrue(use(state, world, 1000L + COOLDOWN).consumed());
        assertEquals(2, world.summons.get());
    }

    @Test
    void aLiveFightBlocksEvenAfterTheCooldown() {
        State state = new State();
        World world = new World();
        use(state, world, 0L);
        assertEquals(Verdict.FIGHT_LIVE, use(state, world, COOLDOWN * 10L).verdict());
        assertEquals(1, world.summons.get());
    }

    @Test
    void aLostFightNeverLocksTheAltarForever() {
        State state = new State();
        World world = new World();
        use(state, world, 0L);
        long late = ApexAltarRules.MAX_FIGHT_LOCK_TICKS;
        assertFalse(state.fightLive(late, true), "lock bound reached");
        state.clearFightIfOver(late, true);
        assertNull(state.fight(), "nothing outlives the fight");
        assertTrue(use(state, world, late).consumed());
    }

    @Test
    void checksRunInOrder() {
        assertEquals(Verdict.NOT_A_SIGIL, ApexAltarRules.decide(false, false, 0L, 99L, true, false));
        assertEquals(Verdict.FIGHT_LIVE, ApexAltarRules.decide(true, false, 0L, 99L, true, false));
        assertEquals(Verdict.COOLING_DOWN, ApexAltarRules.decide(true, false, 0L, 99L, false, false));
        assertEquals(Verdict.UNKNOWN_BOSS, ApexAltarRules.decide(true, false, 99L, 99L, false, false));
        assertEquals(Verdict.NO_ROOM, ApexAltarRules.decide(true, true, 99L, 99L, false, false));
        assertEquals(Verdict.SUMMON, ApexAltarRules.decide(true, true, 99L, 99L, false, true));
    }

    @Test
    void refusedAttemptsNeverSummon() {
        State state = new State();
        World world = new World();
        Outcome unknown = state.attempt(0L, true, false, true, false, COOLDOWN, "RIME_MATRIARCH", world.summon());
        Outcome noRoom = state.attempt(0L, true, true, false, false, COOLDOWN, "RIME_MATRIARCH", world.summon());
        assertFalse(unknown.consumed() || noRoom.consumed());
        assertEquals(0, world.summons.get());
    }

    @Test
    void cooldownMathIsClampedAndRoundsUp() {
        assertEquals(500L, ApexAltarRules.readyAt(500L, -10));
        assertEquals(500L + ApexAltarRules.MAX_COOLDOWN_TICKS, ApexAltarRules.readyAt(500L, Integer.MAX_VALUE));
        assertEquals(1L, ApexAltarRules.secondsLeft(0L, 1L));
        assertEquals(0L, ApexAltarRules.secondsLeft(5L, 5L));
        assertEquals(600L, ApexAltarRules.secondsLeft(0L, ApexAltarRules.DEFAULT_COOLDOWN_TICKS));
    }

    @Test
    void stateRoundTripsThroughRestore() {
        State state = new State();
        UUID fight = UUID.randomUUID();
        state.restore(42L, 7L, fight, "CINDER_TYRANT");
        assertEquals(42L, state.readyAt());
        assertEquals(7L, state.fightStartedAt());
        assertEquals(fight, state.fight());
        assertEquals("CINDER_TYRANT", state.sigil());
        assertTrue(state.locked(10L, true));
    }
}
