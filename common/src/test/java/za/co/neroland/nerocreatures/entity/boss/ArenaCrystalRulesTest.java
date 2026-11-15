package za.co.neroland.nerocreatures.entity.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest;

class ArenaCrystalRulesTest {

    @Test
    void pillarsAreCappedPerPhasePerWaveAndHard() {
        assertEquals(2, ArenaCrystalRules.pillarsToPlace(1, 0, true), "two per wave");
        assertEquals(1, ArenaCrystalRules.pillarsToPlace(1, 4, true), "phase 1: 3 pillars");
        assertEquals(0, ArenaCrystalRules.pillarsToPlace(1, 6, true));
        assertEquals(0, ArenaCrystalRules.pillarsToPlace(2, 8, true), "phase 2: 4 pillars");
        assertEquals(1, ArenaCrystalRules.pillarsToPlace(3, 8, true), "phase 3: 5 pillars");
        assertEquals(0, ArenaCrystalRules.pillarsToPlace(3, 10, true));
        for (int phase = 1; phase <= 3; phase++) {
            for (int alive = 0; alive <= 20; alive++) {
                int place = ArenaCrystalRules.pillarsToPlace(phase, alive, true);
                assertTrue(alive + place * ArenaCrystalRules.PILLAR_HEIGHT <= ArenaCrystalRules.HARD_CAP_BLOCKS
                        || place == 0, "hard cap");
                assertTrue(place >= 0 && place <= ArenaCrystalRules.PILLARS_PER_WAVE);
            }
        }
    }

    @Test
    void deniedGriefingPlacesNoCrystals() {
        CreatureGriefingTest.FakeChecks denied = new CreatureGriefingTest.FakeChecks();
        denied.mobGrief = false;
        boolean allowed = CreatureGriefing.decide(CreatureGriefing.Change.PLACE, denied);
        assertFalse(allowed);
        assertEquals(0, ArenaCrystalRules.pillarsToPlace(3, 0, allowed));

        CreatureGriefingTest.FakeChecks keyOff = new CreatureGriefingTest.FakeChecks();
        keyOff.key = false;
        assertEquals(0, ArenaCrystalRules.pillarsToPlace(1, 0, CreatureGriefing.decide(CreatureGriefing.Change.PLACE, keyOff)));
    }

    @Test
    void ledgerRestoresEverythingStillOwnedAndForgetsDecayed() {
        ArenaCrystalRules.CrystalLedger ledger = new ArenaCrystalRules.CrystalLedger();
        assertTrue(ledger.add(1L, 100L));
        assertTrue(ledger.add(2L, 100L));
        assertTrue(ledger.add(3L, 500L));
        ledger.prune(100L);
        assertEquals(List.of(3L), ledger.positions(), "decayed crystals reverted themselves");
        ledger.add(4L, 600L);
        ledger.forget(4L);
        assertEquals(List.of(3L), ledger.drainForRestore());
        assertEquals(0, ledger.size(), "restore empties the ledger");
        assertEquals(List.of(), ledger.drainForRestore(), "a second restore (death then remove) does nothing");
    }

    @Test
    void ledgerNeverExceedsTheHardCap() {
        ArenaCrystalRules.CrystalLedger ledger = new ArenaCrystalRules.CrystalLedger();
        for (int i = 0; i < ArenaCrystalRules.HARD_CAP_BLOCKS; i++) {
            assertTrue(ledger.add(i, 1000L));
        }
        assertFalse(ledger.add(99L, 1000L));
        assertEquals(ArenaCrystalRules.HARD_CAP_BLOCKS, ledger.size());
    }

    @Test
    void refractionIsSixtyDegreesAndStablePerCrystal() {
        for (long key = -500L; key < 500L; key += 7L) {
            double out = ArenaCrystalRules.refract(10.0D, key);
            assertEquals(ArenaCrystalRules.REFRACT_DEGREES, Math.abs(out - 10.0D), 1.0E-9);
            assertEquals(out, ArenaCrystalRules.refract(10.0D, key), 1.0E-9);
        }
        assertTrue(ArenaCrystalRules.MAX_BOUNCES >= 1 && ArenaCrystalRules.MAX_BOUNCES <= 3);
    }

    @Test
    void beamTelegraphsBeforeItHurts() {
        int first = -1;
        int pulses = 0;
        for (int t = 0; t < ArenaCrystalRules.BEAM_POSE_TICKS + 5; t++) {
            if (ArenaCrystalRules.pulseNow(t)) {
                pulses++;
                if (first < 0) {
                    first = t;
                }
                assertTrue(ArenaCrystalRules.firing(t));
            }
        }
        assertEquals(ArenaCrystalRules.BEAM_TELEGRAPH_TICKS, first, "20-tick telegraph");
        assertTrue(first - ArenaCrystalRules.BEAM_LOCK_TICK >= 10, "aim locks at least 10 ticks before a hit");
        assertEquals(3, pulses);
        assertEquals(50, ArenaCrystalRules.BEAM_POSE_TICKS);
        assertFalse(ArenaCrystalRules.firing(ArenaCrystalRules.BEAM_POSE_TICKS));
    }

    @Test
    void cinderTyrantSlamPoseLeadsTheImpactByTwelveTicks() {
        for (int telegraph : new int[] {12, 20, 30}) {
            int ticksToSlam = telegraph;
            int poseAt = BossArtTiming.slamPoseStarts(ticksToSlam, true) ? ticksToSlam : -1;
            while (ticksToSlam > 0) {
                ticksToSlam--;
                if (BossArtTiming.slamPoseStarts(ticksToSlam, false)) {
                    assertEquals(-1, poseAt, "pose starts once");
                    poseAt = ticksToSlam;
                }
            }
            assertEquals(BossArtTiming.SLAM_POSE_LEAD, poseAt, "telegraph " + telegraph);
        }
        assertEquals(32, BossArtTiming.SLAM_POSE_TICKS);
    }
}
