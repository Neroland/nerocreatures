package za.co.neroland.nerocreatures.entity.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefing.Change;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

import net.minecraft.resources.Identifier;

/** Rime Matriarch and Bloom Sovereign: phases, caps, restoration, denied griefing, no double pay. */
class ApexBossesTest {

    // --- phase transitions ------------------------------------------------------------------

    @Test
    void phaseThresholdsDescend() {
        assertTrue(1.0F > MatriarchRules.PHASE_2 && MatriarchRules.PHASE_2 > MatriarchRules.PHASE_3
                && MatriarchRules.PHASE_3 > 0.0F);
        assertTrue(1.0F > SovereignRules.PHASE_2 && SovereignRules.PHASE_2 > SovereignRules.PHASE_3
                && SovereignRules.PHASE_3 > 0.0F);
    }

    @Test
    void firstPhaseIsNotATransitionButLaterOnesHoldThirtyTicks() {
        PhaseClock clock = new PhaseClock();
        assertFalse(clock.tick(1));
        assertFalse(clock.busy());
        assertTrue(clock.tick(2));
        for (int i = 0; i < PhaseClock.TRANSITION_TICKS; i++) {
            assertTrue(clock.busy(), "tick " + i);
            assertFalse(clock.tick(2));
        }
        assertFalse(clock.busy());
        assertEquals(2, clock.phase());
    }

    @Test
    void reloadedBossInLaterPhaseDoesNotReplayTransition() {
        PhaseClock clock = new PhaseClock();
        assertFalse(clock.tick(3));
        assertFalse(clock.busy());
    }

    @Test
    void phasesEscalate() {
        assertTrue(MatriarchRules.beamCooldown(3) < MatriarchRules.beamCooldown(1));
        assertTrue(MatriarchRules.floorRadius(1) < MatriarchRules.floorRadius(2)
                && MatriarchRules.floorRadius(2) < MatriarchRules.floorRadius(3));
        assertTrue(SovereignRules.infectCooldown(3) < SovereignRules.infectCooldown(1));
        assertEquals(MatriarchRules.floorRadius(1), MatriarchRules.floorRadius(0));
        assertEquals(MatriarchRules.floorRadius(3), MatriarchRules.floorRadius(9));
    }

    // --- Matriarch beam ---------------------------------------------------------------------

    @Test
    void beamTimingMatchesTheArtClip() {
        assertFalse(MatriarchRules.beamLive(19));
        assertTrue(MatriarchRules.beamLive(20));
        assertTrue(MatriarchRules.beamLive(39));
        assertFalse(MatriarchRules.beamLive(40));
        assertTrue(MatriarchRules.beamPulse(20));
        assertFalse(MatriarchRules.beamPulse(21));
        assertEquals(50, MatriarchRules.BEAM_CLIP_TICKS);
        int pulses = 0;
        for (int t = 0; t < MatriarchRules.BEAM_CLIP_TICKS; t++) {
            pulses += MatriarchRules.beamPulse(t) ? 1 : 0;
        }
        assertEquals(5, pulses);
    }

    @Test
    void pillarsAndBlocksAreCover() {
        assertTrue(MatriarchRules.beamReaches(Double.POSITIVE_INFINITY, 10.0D));
        assertFalse(MatriarchRules.beamReaches(6.0D, 10.0D), "a pillar at 6 blocks shields a player at 10");
        assertTrue(MatriarchRules.beamReaches(12.0D, 10.0D));
        assertFalse(MatriarchRules.beamReaches(Double.POSITIVE_INFINITY, MatriarchRules.BEAM_RANGE + 1.0D));
    }

    // --- caps -------------------------------------------------------------------------------

    @Test
    void pillarCountIsCapped() {
        for (int phase = 1; phase <= 3; phase++) {
            assertTrue(MatriarchRules.pillarTarget(phase) <= MatriarchRules.MAX_PILLARS);
            assertEquals(0, MatriarchRules.pillarsToRaise(phase, MatriarchRules.MAX_PILLARS, true));
        }
        assertEquals(MatriarchRules.MAX_PILLARS * MatriarchRules.PILLAR_HEIGHT, MatriarchRules.MAX_PILLAR_BLOCKS);
        ArenaLedger blocks = new ArenaLedger(MatriarchRules.MAX_PILLAR_BLOCKS);
        for (long p = 0; p < 100; p++) {
            blocks.tryAdd(p);
        }
        assertEquals(MatriarchRules.MAX_PILLAR_BLOCKS, blocks.size());
        assertFalse(blocks.tryAdd(1000L));
        assertTrue(blocks.tryAdd(3L), "an already-tracked block is accepted without growing");
    }

    @Test
    void frozenFloorIsCapped() {
        ArenaLedger frozen = new ArenaLedger(MatriarchRules.MAX_FROZEN_BLOCKS);
        int accepted = 0;
        for (long p = 0; p < 500; p++) {
            accepted += frozen.tryAdd(p) ? 1 : 0;
        }
        assertEquals(MatriarchRules.MAX_FROZEN_BLOCKS, accepted);
        assertTrue(MatriarchRules.FREEZE_PER_PASS <= MatriarchRules.MAX_FROZEN_BLOCKS);
        // Melted or broken blocks free their slot.
        assertEquals(10, frozen.prune(p -> p >= 10));
        assertTrue(frozen.hasRoom());
    }

    @Test
    void floorSlowsButOnlyPhaseThreeCanHurt() {
        int required = 140;
        int ticks = 0;
        for (int i = 0; i < 100; i++) {
            ticks = MatriarchRules.freezeTicks(ticks, required, 2);
        }
        assertEquals(required - 1, ticks);
        for (int i = 0; i < 100; i++) {
            ticks = MatriarchRules.freezeTicks(ticks, required, 3);
        }
        assertEquals(required + 40, ticks);
        assertEquals(500, MatriarchRules.freezeTicks(500, required, 1), "never lowers existing frost");
    }

    @Test
    void podsAndAddsAreCapped() {
        for (int phase = 1; phase <= 3; phase++) {
            assertTrue(SovereignRules.podCap(phase) <= SovereignRules.MAX_PODS);
            assertEquals(0, SovereignRules.podsToGrow(phase, SovereignRules.MAX_PODS));
            assertTrue(SovereignRules.sporelingCap(phase) <= SovereignRules.MAX_SPORELINGS);
            assertFalse(SovereignRules.mayRaiseSporeling(phase, SovereignRules.MAX_SPORELINGS, true));
        }
        assertTrue(SovereignRules.mayRaiseSporeling(1, 0, true));
        assertFalse(SovereignRules.mayRaiseSporeling(1, 0, false), "bloomHostSporelings=false: lash only");
    }

    @Test
    void healNeedsPodsAndNeverOverheals() {
        assertFalse(SovereignRules.shouldHeal(340.0F, 100.0F, 0));
        assertEquals(0.0F, SovereignRules.healAmount(340.0F, 100.0F, 0));
        assertTrue(SovereignRules.shouldHeal(340.0F, 100.0F, 2));
        assertEquals(340.0F * SovereignRules.HEAL_PER_POD * 2, SovereignRules.healAmount(340.0F, 100.0F, 2), 1e-3);
        assertEquals(1.0F, SovereignRules.healAmount(340.0F, 339.0F, 4), 1e-3);
        assertEquals(SovereignRules.healAmount(340.0F, 0.0F, SovereignRules.MAX_PODS),
                SovereignRules.healAmount(340.0F, 0.0F, 99), 1e-3, "pods beyond the cap add nothing");
        assertEquals(14, SovereignRules.INFECT_HIT_TICK);
        assertEquals(30, SovereignRules.INFECT_CLIP_TICKS);
        assertEquals(60, SovereignRules.HEAL_CLIP_TICKS);
    }

    // --- restoration and duplication --------------------------------------------------------

    @Test
    void arenaIsRestoredOnceWhateverEndsTheFight() {
        ArenaLedger ledger = new ArenaLedger(8);
        ledger.tryAdd(1L);
        ledger.tryAdd(2L);
        FightEndLatch latch = new FightEndLatch();
        assertTrue(latch.claim(FightEndLatch.Exit.DEATH));
        assertEquals(List.of(1L, 2L), ledger.drain());
        // The KILLED removal that follows a death must not restore (or clear adds) a second time.
        assertFalse(latch.claim(FightEndLatch.Exit.DISCARD));
        assertTrue(ledger.drain().isEmpty());
    }

    @Test
    void unloadNeverEndsTheFight() {
        FightEndLatch latch = new FightEndLatch();
        assertFalse(latch.claim(FightEndLatch.Exit.UNLOAD));
        assertFalse(latch.ended());
        assertTrue(latch.claim(FightEndLatch.Exit.DISCARD));
        assertFalse(latch.claim(FightEndLatch.Exit.DEATH));
    }

    @Test
    void noDoublePayoutOnDeathThenDespawnRace() {
        BossContributionState store = new BossContributionState();
        UUID fight = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Identifier boss = Identifier.fromNamespaceAndPath("nerocreatures", "rime_matriarch");
        store.record(fight, boss, player, 50.0F);
        assertEquals(1, store.resolve(fight).size());
        // Death resolved the row; a racing removal or a second death sees nothing to pay.
        assertTrue(store.resolve(fight).isEmpty());
        store.discard(fight);
        assertEquals(0, store.participantCount(fight));
    }

    @Test
    void contributionExistsOnlyForAFightThatWasBound() {
        BossContributionState store = new BossContributionState();
        UUID natural = UUID.randomUUID();
        // A natural (unsummoned) boss never calls record: its controller has no fight id.
        assertEquals(0, store.participantCount(natural));
        assertTrue(store.resolve(natural).isEmpty());
        UUID summoned = UUID.randomUUID();
        store.join(summoned, Identifier.fromNamespaceAndPath("nerocreatures", "bloom_sovereign"), UUID.randomUUID());
        assertEquals(1, store.participantCount(summoned));
    }

    // --- denied griefing --------------------------------------------------------------------

    @Test
    void deniedArenaBlocksKeepTheFightFair() {
        FakeChecks denied = new FakeChecks();
        denied.mobGrief = false;
        boolean allowed = CreatureGriefing.decide(Change.PLACE, denied);
        assertFalse(allowed);
        assertEquals(0, MatriarchRules.pillarsToRaise(1, 0, allowed), "no pillars");
        assertEquals(2 * MatriarchRules.TELEGRAPH_TICKS, MatriarchRules.totalTelegraph(allowed),
                "telegraph twice as long");
        assertTrue(MatriarchRules.beamReaches(5.0D, 3.0D), "the beam still fires");
        assertTrue(MatriarchRules.pillarsToRaise(1, 0, true) > 0);
        assertEquals(MatriarchRules.TELEGRAPH_TICKS, MatriarchRules.totalTelegraph(true));
    }

    @Test
    void deniedConfigKeyAlsoStopsFreezingWater() {
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false;
        assertFalse(CreatureGriefing.decide(Change.REPLACE, keyOff));
        assertEquals(List.of("key"), keyOff.calls);
    }
}
