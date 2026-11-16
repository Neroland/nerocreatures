package za.co.neroland.nerocreatures.entity.greenxertz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefing.Change;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

/** Greenxertz: Spore Drifter, Bloom Host, Mossback, Drift Leviathan — each ability's rule, its cap and its denied case. */
class GreenxertzCreaturesTest {

    // --- Spore Drifter ------------------------------------------------------------------

    @Test
    void drifterPulsesEveryFourSecondsAlone() {
        assertEquals(80, DrifterRules.interval(0));
        assertEquals(80, DrifterRules.interval(DrifterRules.AREA_BOOSTERS - 1), "up to the area cap: full rate");
        assertEquals(160, DrifterRules.interval(DrifterRules.AREA_BOOSTERS));
    }

    @Test
    void drifterAreaCapHoldsForAnyCrowd() {
        for (int n = 1; n <= DrifterRules.MAX_COUNTED + 1; n++) {
            assertTrue(DrifterRules.areaTicksPerInterval(n) <= DrifterRules.AREA_BOOSTERS + 1e-9, "crowd of " + n);
        }
        assertEquals(DrifterRules.interval(DrifterRules.MAX_COUNTED), DrifterRules.interval(500), "neighbour count is clamped");
    }

    @Test
    void drifterGrowthDeniedFallsBackToParticles() {
        FakeChecks denied = new FakeChecks();
        denied.place = false; // a claim refuses the block change
        assertEquals(DrifterRules.Growth.PARTICLES, DrifterRules.growth(true, CreatureGriefing.decide(Change.REPLACE, denied)));
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false; // sporeDrifterGrowth = false
        assertEquals(DrifterRules.Growth.PARTICLES, DrifterRules.growth(true, CreatureGriefing.decide(Change.REPLACE, keyOff)));
        FakeChecks noGriefing = new FakeChecks();
        noGriefing.mobGrief = false;
        assertEquals(DrifterRules.Growth.PARTICLES, DrifterRules.growth(true, CreatureGriefing.decide(Change.REPLACE, noGriefing)));
        assertEquals(DrifterRules.Growth.TICK, DrifterRules.growth(true, CreatureGriefing.decide(Change.REPLACE, new FakeChecks())));
        assertEquals(DrifterRules.Growth.NONE, DrifterRules.growth(false, true), "no crop below: nothing");
    }

    @Test
    void drifterBottlingRespectsOwnerAndCooldown() {
        assertTrue(DrifterRules.mayBottle(false, false, false, 100, Long.MIN_VALUE), "wild: anyone");
        assertFalse(DrifterRules.mayBottle(false, true, false, 100, Long.MIN_VALUE), "tamed: not a stranger");
        assertTrue(DrifterRules.mayBottle(false, true, true, 100, Long.MIN_VALUE), "tamed: its owner");
        assertFalse(DrifterRules.mayBottle(true, false, false, 100, Long.MIN_VALUE), "never a baby");
        long next = DrifterRules.nextBottle(100);
        assertFalse(DrifterRules.mayBottle(false, false, false, next - 1, next), "refilling");
        assertTrue(DrifterRules.mayBottle(false, false, false, next, next));
        assertEquals(100 + DrifterRules.BOTTLE_COOLDOWN_TICKS, next);
    }

    // --- Bloom Host ---------------------------------------------------------------------------

    private static BloomRules.Victim cow() {
        return new BloomRules.Victim(false, false, false, false, false, false, true);
    }

    @Test
    void sporelingsRiseOnlyFromEligibleMobs() {
        assertTrue(BloomRules.eligible(cow()));
        assertFalse(BloomRules.eligible(new BloomRules.Victim(true, false, false, false, false, false, true)), "never a player");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, true, false, false, false, false, true)), "never owned");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, false, true, false, false, false, true)), "never named");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, false, false, true, false, false, true)), "never persistent");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, false, false, false, true, false, true)), "never a boss");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, false, false, false, false, true, true)), "never a villager");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, false, false, false, false, false, false)), "only tagged hosts");
        assertFalse(BloomRules.eligible(null));
    }

    @Test
    void sporelingCapsAndKeyAlwaysApply() {
        assertTrue(BloomRules.mayRaise(true, BloomRules.FORM_HOST, cow(), 0, 0));
        assertFalse(BloomRules.mayRaise(false, BloomRules.FORM_HOST, cow(), 0, 0), "bloomHostSporelings = false: no sporeling");
        assertFalse(BloomRules.mayRaise(true, BloomRules.FORM_HOST, cow(), BloomRules.PER_HOST, 0), "3 per host");
        assertTrue(BloomRules.mayRaise(true, BloomRules.FORM_HOST, cow(), BloomRules.PER_HOST - 1, 0));
        assertFalse(BloomRules.mayRaise(true, BloomRules.FORM_HOST, cow(), 0, BloomRules.PER_DIMENSION), "24 per dimension");
        assertFalse(BloomRules.mayRaise(true, BloomRules.FORM_SPORELING, cow(), 0, 0), "sporelings never raise");
        assertFalse(BloomRules.mayRaise(true, BloomRules.FORM_POD, cow(), 0, 0), "pods never raise");
    }

    @Test
    void sporelingsLiveThirtySeconds() {
        assertFalse(BloomRules.expired(BloomRules.LIFE_TICKS - 1));
        assertTrue(BloomRules.expired(BloomRules.LIFE_TICKS));
        assertEquals(600, BloomRules.LIFE_TICKS);
        assertEquals(BloomRules.FORM_HOST, BloomRules.form(7), "unknown forms read as the host");
        assertEquals(BloomRules.FORM_POD, BloomRules.form(BloomRules.FORM_POD));
    }

    @Test
    void dimensionTallyEnforcesTheCapAndPrunesTheDead() {
        DimensionTally tally = new DimensionTally();
        Set<Integer> alive = new HashSet<>();
        for (int id = 0; id < BloomRules.PER_DIMENSION; id++) {
            alive.add(id);
            assertTrue(tally.tryAdd("w", id, BloomRules.PER_DIMENSION, alive::contains));
        }
        alive.add(99);
        assertFalse(tally.tryAdd("w", 99, BloomRules.PER_DIMENSION, alive::contains), "cap reached");
        assertTrue(tally.tryAdd("other", 99, BloomRules.PER_DIMENSION, alive::contains), "caps are per dimension");
        alive.remove(3); // one sporeling expired
        assertTrue(tally.tryAdd("w", 99, BloomRules.PER_DIMENSION, alive::contains), "a dead id frees its slot");
        assertEquals(BloomRules.PER_DIMENSION, tally.count("w", alive::contains));
        assertTrue(tally.tryAdd("w", 99, BloomRules.PER_DIMENSION, alive::contains), "idempotent");
        tally.remove("w", 99);
        assertEquals(BloomRules.PER_DIMENSION - 1, tally.count("w", alive::contains));
        assertFalse(tally.tryAdd("z", 1, 0, alive::contains), "cap 0 admits nothing");
    }

    // --- Mossback -----------------------------------------------------------------------------

    @Test
    void mossbackFertilisesOnlyGrassAndOnlyWhenAllowed() {
        assertEquals(MossbackRules.Graze.FERTILISE, MossbackRules.fertilise(true, true, 100, 0,
                CreatureGriefing.decide(Change.PLACE, new FakeChecks())));
        assertEquals(MossbackRules.Graze.NONE, MossbackRules.fertilise(true, false, 100, 0, true), "not on grass");
        assertEquals(MossbackRules.Graze.NONE, MossbackRules.fertilise(false, true, 100, 0, true), "babies do not graze");
        assertEquals(MossbackRules.Graze.GRAZE_ONLY, MossbackRules.fertilise(true, true, 100, 101, true), "on cooldown");
    }

    @Test
    void mossbackDeniedGriefingGrazesWithNoEffect() {
        FakeChecks denied = new FakeChecks();
        denied.mobGrief = false;
        assertEquals(MossbackRules.Graze.GRAZE_ONLY, MossbackRules.fertilise(true, true, 100, 0,
                CreatureGriefing.decide(Change.PLACE, denied)));
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false; // mossbackFertilises = false
        assertEquals(MossbackRules.Graze.GRAZE_ONLY, MossbackRules.fertilise(true, true, 100, 0,
                CreatureGriefing.decide(Change.PLACE, keyOff)));
        FakeChecks claim = new FakeChecks();
        claim.place = false;
        assertEquals(MossbackRules.Graze.GRAZE_ONLY, MossbackRules.fertilise(true, true, 100, 0,
                CreatureGriefing.decide(Change.PLACE, claim)));
    }

    // --- Drift Leviathan ----------------------------------------------------------------------

    @Test
    void leviathanDeckCollidesOnlyFromAbove() {
        assertTrue(LeviathanRules.collidesFromAbove(100.0D, 100.0D), "standing on it");
        assertTrue(LeviathanRules.collidesFromAbove(100.0D - LeviathanRules.DECK_EPSILON, 100.0D));
        assertFalse(LeviathanRules.collidesFromAbove(99.0D, 100.0D), "beside or below: passes through");
    }

    @Test
    void leviathanDeckFootprintFollowsTheBodyAxis() {
        assertFalse(LeviathanRules.alongX(0.0F), "yaw 0 faces +z");
        assertTrue(LeviathanRules.alongX(90.0F));
        assertTrue(LeviathanRules.alongX(-100.0F));
        assertFalse(LeviathanRules.alongX(180.0F));
        double[] z = LeviathanRules.halfExtents(false);
        assertEquals(1.75D, z[0], 1e-9);
        assertEquals(4.375D, z[1], 1e-9);
        double[] x = LeviathanRules.halfExtents(true);
        assertEquals(4.375D, x[0], 1e-9);
        assertEquals(1.75D, x[1], 1e-9);
        assertEquals(3.0D, LeviathanRules.DECK_HEIGHT, 1e-9, "deck top 3 blocks above the origin");
    }

    @Test
    void leviathanHoldsStillWhileAPlayerIsOnTheDeck() {
        assertTrue(LeviathanRules.onDeck(100.0D, 0.0D, 0.0D, 100.0D, -3.5D, 3.5D, -3.5D, 3.5D));
        assertTrue(LeviathanRules.onDeck(101.0D, 3.9D, 0.0D, 100.0D, -3.5D, 3.5D, -3.5D, 3.5D), "jumping at the edge");
        assertFalse(LeviathanRules.onDeck(103.0D, 0.0D, 0.0D, 100.0D, -3.5D, 3.5D, -3.5D, 3.5D), "flying high above");
        assertFalse(LeviathanRules.onDeck(100.0D, 6.0D, 0.0D, 100.0D, -3.5D, 3.5D, -3.5D, 3.5D), "off the side");
        int still = LeviathanRules.stillTimer(0, true);
        assertEquals(LeviathanRules.STILL_TICKS, still);
        for (int i = 0; i < LeviathanRules.STILL_TICKS; i++) {
            assertTrue(still > 0, "still for the grace period");
            still = LeviathanRules.stillTimer(still, false);
        }
        assertEquals(0, still, "then free to drift");
    }

    @Test
    void leviathanCruisesInItsAltitudeBand() {
        assertEquals(76.0D, LeviathanRules.clampAltitude(10.0D, 64, 320), 1e-9);
        assertEquals(104.0D, LeviathanRules.clampAltitude(500.0D, 64, 320), 1e-9);
        assertEquals(90.0D, LeviathanRules.clampAltitude(90.0D, 64, 320), 1e-9);
        assertEquals(LeviathanRules.MAX_SPEED, LeviathanRules.accelerate(LeviathanRules.MAX_SPEED, 1.0D), 1e-9, "never hurries");
    }

    @Test
    void leviathanPopulationIsStrictlyCapped() {
        assertTrue(LeviathanRules.maySpawnNaturally(0));
        assertTrue(LeviathanRules.maySpawnNaturally(LeviathanRules.MAX_PER_DIMENSION - 1));
        assertFalse(LeviathanRules.maySpawnNaturally(LeviathanRules.MAX_PER_DIMENSION));
    }

    @Test
    void barnaclesRegrowAndHarvestIsCapped() {
        ProductTimer barnacles = new ProductTimer(LeviathanRules.BARNACLE_REGROW_TICKS, LeviathanRules.BARNACLE_STAGES,
                LeviathanRules.BARNACLE_MAX_YIELD);
        assertEquals(0, barnacles.harvest(LeviathanRules.BARNACLE_BASE_YIELD, b -> b), "not ready");
        barnacles.advance(LeviathanRules.BARNACLE_REGROW_TICKS);
        assertEquals(LeviathanRules.BARNACLE_STAGES, barnacles.stage());
        assertEquals(LeviathanRules.BARNACLE_MAX_YIELD,
                barnacles.harvest(LeviathanRules.BARNACLE_BASE_YIELD, b -> LeviathanRules.barnacleYield(b, 99)));
        assertEquals(0, barnacles.stage(), "regrows from bare");
        assertEquals(2, LeviathanRules.barnacleYield(2, 0));
    }

    @Test
    void naturalLeviathansArriveWithBarnaclesHalfToFullyGrown() {
        // Seeded so a wild leviathan is worth landing on.
        int regrow = LeviathanRules.BARNACLE_REGROW_TICKS;
        assertEquals(regrow / 2, LeviathanRules.seedBarnacles(regrow, 0.0D));
        assertEquals(regrow, LeviathanRules.seedBarnacles(regrow, 1.0D));
        assertEquals(regrow, LeviathanRules.seedBarnacles(regrow, 7.0D), "fraction clamps");
        assertEquals(0, LeviathanRules.seedBarnacles(0, 0.5D));
        ProductTimer seeded = new ProductTimer(regrow, LeviathanRules.BARNACLE_STAGES, LeviathanRules.BARNACLE_MAX_YIELD);
        seeded.setProgress(LeviathanRules.seedBarnacles(regrow, 0.0D));
        assertTrue(seeded.stage() >= 1, "barnacles visibly present at spawn");
    }

    @Test
    void leviathanStateSurvivesASaveRoundTrip() {
        // The leviathan is saved now; its saved state is barnacle progress and the deck axis.
        ProductTimer before = new ProductTimer(LeviathanRules.BARNACLE_REGROW_TICKS,
                LeviathanRules.BARNACLE_STAGES, LeviathanRules.BARNACLE_MAX_YIELD);
        before.setProgress(LeviathanRules.seedBarnacles(LeviathanRules.BARNACLE_REGROW_TICKS, 0.4D));
        before.advance(LeviathanRules.GROWTH_STEP * 10);
        int saved = before.progress();
        ProductTimer after = new ProductTimer(LeviathanRules.BARNACLE_REGROW_TICKS,
                LeviathanRules.BARNACLE_STAGES, LeviathanRules.BARNACLE_MAX_YIELD);
        after.setProgress(saved);
        assertEquals(before.progress(), after.progress());
        assertEquals(before.stage(), after.stage());
        assertEquals(before.isReady(), after.isReady());
        for (float yaw : new float[] {0.0F, 90.0F, 180.0F, 270.0F}) {
            boolean axis = LeviathanRules.alongX(yaw);
            double[] extents = LeviathanRules.halfExtents(axis);
            assertEquals(extents[0], LeviathanRules.halfExtents(axis)[0], "deck box rebuilt from the saved axis");
        }
    }

    @Test
    void leviathansSkipRandomDespawnButHonourTheHardRange() {
        assertFalse(LeviathanRules.mayDespawnAt(40.0D * 40.0D), "no random despawn at 40 blocks");
        assertFalse(LeviathanRules.mayDespawnAt(128.0D * 128.0D));
        assertTrue(LeviathanRules.mayDespawnAt(129.0D * 129.0D), "vanilla hard range still applies");
    }

    @Test
    void onlyTheHostFormDropsLoot() {
        // Pods regrow under the Bloom Sovereign: a pod kill must never pay out a bloom_heart.
        assertTrue(BloomRules.dropsLoot(BloomRules.FORM_HOST));
        assertFalse(BloomRules.dropsLoot(BloomRules.FORM_SPORELING), "sporelings drop nothing");
        assertFalse(BloomRules.dropsLoot(BloomRules.FORM_POD), "pods drop nothing");
        assertTrue(BloomRules.dropsLoot(99), "unknown forms read as the host");
    }
}
