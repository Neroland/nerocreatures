package za.co.neroland.nerocreatures.entity.luna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.shared.PredatorPreyRules;
import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;
import za.co.neroland.nerocreatures.entity.ai.shared.SoundHuntRules;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefing.Change;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

/** WP-Luna: Lumen Moth, Echo Shrike and Lode Tortoise ability rules and caps. */
public class LunaCreaturesTest {

    // --- Lumen Moth: living lantern -------------------------------------------------------

    @Test
    void lanternDecisionTable() {
        assertEquals(LanternRules.Action.NONE, LanternRules.decide(false, false, true));
        assertEquals(LanternRules.Action.CLEAR, LanternRules.decide(false, true, false));
        assertEquals(LanternRules.Action.KEEP, LanternRules.decide(true, true, false));
        assertEquals(LanternRules.Action.MOVE, LanternRules.decide(true, true, true));
        assertEquals(LanternRules.Action.MOVE, LanternRules.decide(true, false, true));
    }

    @Test
    void lanternNeverHoldsMoreThanOneLight() {
        // Simulate a walk: each MOVE replaces, each CLEAR removes; the count never exceeds the cap.
        int lights = 0;
        boolean[][] steps = {{true, true}, {true, true}, {true, false}, {false, false}, {true, true}, {false, false}};
        for (boolean[] step : steps) {
            LanternRules.Action a = LanternRules.decide(step[0], lights > 0, step[1]);
            switch (a) {
                case MOVE -> lights = 1;
                case CLEAR -> lights = 0;
                default -> {
                }
            }
            assertTrue(lights <= LanternRules.MAX_LIGHTS_PER_MOTH);
        }
        assertEquals(0, lights, "inactive at the end: cleaned up");
    }

    @Test
    void lanternTargetsAirOnlyInOrder() {
        assertEquals(0, LanternRules.chooseTarget(true, true, true));
        assertEquals(1, LanternRules.chooseTarget(false, true, true));
        assertEquals(2, LanternRules.chooseTarget(false, false, true));
        assertEquals(-1, LanternRules.chooseTarget(false, false, false));
        assertTrue(LanternRules.isMothLight(true, LanternRules.LIGHT_LEVEL));
        assertFalse(LanternRules.isMothLight(true, 15), "a player's own light block is never removed");
        assertFalse(LanternRules.isMothLight(false, LanternRules.LIGHT_LEVEL));
    }

    @Test
    void lanternDeniedGriefingTakesTheFallback() {
        FakeChecks key = new FakeChecks();
        key.key = false;
        assertEquals(LanternRules.Outcome.FALLBACK, LanternRules.outcome(CreatureGriefing.decide(Change.PLACE, key)));
        FakeChecks rule = new FakeChecks();
        rule.mobGrief = false;
        assertEquals(LanternRules.Outcome.FALLBACK, LanternRules.outcome(CreatureGriefing.decide(Change.PLACE, rule)));
        FakeChecks claim = new FakeChecks();
        claim.place = false;
        assertEquals(LanternRules.Outcome.FALLBACK, LanternRules.outcome(CreatureGriefing.decide(Change.PLACE, claim)));
        FakeChecks owner = new FakeChecks();
        owner.owner = false;
        assertEquals(LanternRules.Outcome.FALLBACK, LanternRules.outcome(CreatureGriefing.decide(Change.PLACE, owner)));
        assertEquals(LanternRules.Outcome.PLACE, LanternRules.outcome(CreatureGriefing.decide(Change.PLACE, new FakeChecks())));
    }

    // --- Lumen Moth: reveal and wild behaviour -------------------------------------------------

    @Test
    void revealHiddenButNeverPlayers() {
        assertTrue(RevealRules.shouldReveal(false, true, false, false, false));
        assertTrue(RevealRules.shouldReveal(false, false, true, false, false));
        assertTrue(RevealRules.shouldReveal(false, false, false, true, false));
        assertTrue(RevealRules.shouldReveal(false, false, false, false, true));
        assertFalse(RevealRules.shouldReveal(false, false, false, false, false));
        assertFalse(RevealRules.shouldReveal(true, true, true, true, true));
    }

    @Test
    void revealIsCappedAndRangeBounded() {
        assertEquals(RevealRules.MAX_REVEALED_PER_PULSE, RevealRules.take(50));
        assertEquals(3, RevealRules.take(3));
        assertEquals(0, RevealRules.take(-1));
        assertEquals(RevealRules.BASE_RADIUS, RevealRules.radius(1.0D));
        assertEquals(12.5D, RevealRules.radius(1.25D), 1.0E-9);
        assertEquals(RevealRules.MAX_RADIUS, RevealRules.radius(100.0D));
        assertEquals(RevealRules.BASE_RADIUS, RevealRules.radius(Double.NaN));
    }

    @Test
    void wildMothsPickTheBrightestLight() {
        assertEquals(2, MothRules.bestLightIndex(new int[] {3, 11, 14, 14}, MothRules.MIN_ATTRACTING_LIGHT));
        assertEquals(-1, MothRules.bestLightIndex(new int[] {0, 9, 4}, MothRules.MIN_ATTRACTING_LIGHT));
    }

    @Test
    void wildMothsDespawnByDayOnlyWhenNobodyCares() {
        assertTrue(MothRules.despawnAtDay(false, false, false, true, true, false));
        assertFalse(MothRules.despawnAtDay(true, false, false, true, true, false), "tamed");
        assertFalse(MothRules.despawnAtDay(false, true, false, true, true, false), "leashed");
        assertFalse(MothRules.despawnAtDay(false, false, true, true, true, false), "persistent");
        assertFalse(MothRules.despawnAtDay(false, false, false, false, true, false), "not overworld");
        assertFalse(MothRules.despawnAtDay(false, false, false, true, false, false), "night");
        assertFalse(MothRules.despawnAtDay(false, false, false, true, true, true), "player near");
    }

    // --- Echo Shrike ---------------------------------------------------------------------------

    @Test
    void shrikeHearsSprintMiningAndLandingButNotSneaking() {
        int r = ShrikeRules.HEARING_RADIUS;
        int sprint = SoundHuntRules.loudness(SoundHuntRules.Noise.STEP, true, false, false);
        assertTrue(SoundHuntRules.heard(sprint, 16.0D * 16.0D, r));
        assertFalse(SoundHuntRules.heard(sprint, 17.0D * 17.0D, r));
        int mine = SoundHuntRules.loudness(SoundHuntRules.Noise.MINE, false, false, false);
        assertTrue(SoundHuntRules.heard(mine, 24.0D * 24.0D, r));
        int land = SoundHuntRules.loudness(SoundHuntRules.Noise.LAND, false, false, false);
        assertTrue(SoundHuntRules.heard(land, 20.0D * 20.0D, r));
        assertEquals(0, SoundHuntRules.loudness(SoundHuntRules.Noise.STEP, false, false, false), "walking is quiet");
        for (SoundHuntRules.Noise noise : SoundHuntRules.Noise.values()) {
            assertEquals(0, SoundHuntRules.loudness(noise, true, true, false), "sneaking is silent: " + noise);
        }
    }

    @Test
    void shrikeTargetsPlayersAndWildMothsOnly() {
        assertTrue(ShrikeRules.huntable(true, false, false, false));
        assertFalse(ShrikeRules.huntable(true, true, false, false), "creative/spectator");
        assertTrue(ShrikeRules.huntable(false, false, true, true));
        assertFalse(ShrikeRules.huntable(false, false, true, false), "a tamed moth is not prey");
        assertFalse(ShrikeRules.huntable(false, false, false, true), "not a moth");
    }

    @Test
    void shrikeLungeTimingMatchesTheClip() {
        assertEquals(26, ShrikeRules.LUNGE_POSE_TICKS);
        assertTrue(ShrikeRules.LUNGE_HIT_TICK >= 10 && ShrikeRules.LUNGE_HIT_TICK <= 14);
        assertTrue(ShrikeRules.mayStartLunge(4.0D, 0));
        assertFalse(ShrikeRules.mayStartLunge(4.0D, 3), "cooldown");
        assertFalse(ShrikeRules.mayStartLunge(16.0D, 0), "too far");
        assertTrue(ShrikeRules.inReach(9.0D));
        assertFalse(ShrikeRules.inReach(16.0D));
    }

    @Test
    void shrikeSearchesForOneMemoryWindowAndMothHuntIsCapped() {
        assertTrue(ShrikeRules.stillSearching(100L, 100L + ShrikeRules.MEMORY_TICKS, false));
        assertFalse(ShrikeRules.stillSearching(100L, 101L + ShrikeRules.MEMORY_TICKS, false));
        assertFalse(ShrikeRules.stillSearching(100L, 110L, true), "found something");
        assertFalse(ShrikeRules.stillSearching(Long.MIN_VALUE, 0L, false));
        assertFalse(PredatorPreyRules.hungry(1000L, 0L, ShrikeRules.MOTH_SATIATION_TICKS), "sated after a moth");
        assertTrue(PredatorPreyRules.chaseTimedOut(ShrikeRules.MOTH_MAX_CHASE_TICKS, 0L, ShrikeRules.MOTH_MAX_CHASE_TICKS));
    }

    // --- Lode Tortoise ---------------------------------------------------------------------------

    @Test
    void grazingPicksSwitchesAndPausesFamilies() {
        NoduleRules.GrazeResult none = NoduleRules.onGraze(null, false, null);
        assertNull(none.family());
        assertFalse(none.growing());

        NoduleRules.GrazeResult first = NoduleRules.onGraze(null, false, NoduleFamily.IRON);
        assertEquals(NoduleFamily.IRON, first.family());
        assertTrue(first.growing());
        assertFalse(first.resetProgress());

        NoduleRules.GrazeResult paused = NoduleRules.onGraze(NoduleFamily.IRON, false, null);
        assertEquals(NoduleFamily.IRON, paused.family());
        assertFalse(paused.growing(), "no ore in reach: growth pauses");

        NoduleRules.GrazeResult switched = NoduleRules.onGraze(NoduleFamily.IRON, false, NoduleFamily.DIAMOND);
        assertEquals(NoduleFamily.DIAMOND, switched.family());
        assertTrue(switched.resetProgress(), "no diamonds grown on iron progress");

        NoduleRules.GrazeResult ready = NoduleRules.onGraze(NoduleFamily.IRON, true, NoduleFamily.DIAMOND);
        assertEquals(NoduleFamily.IRON, ready.family(), "ready nodules keep their family");
        assertFalse(ready.resetProgress());
    }

    @Test
    void nodulesGrowSlowlyAndRareOnesSlower() {
        ProductTimer timer = new ProductTimer(NoduleRules.DEFAULT_REGROW_TICKS, NoduleRules.MAX_STAGE, 64);
        int grazes = 0;
        while (!timer.isReady() && grazes < 1000) {
            timer.advance(NoduleRules.growthTicks(NoduleRules.GRAZE_INTERVAL, NoduleFamily.IRON));
            grazes++;
        }
        assertEquals(NoduleRules.DEFAULT_REGROW_TICKS / NoduleRules.GRAZE_INTERVAL, grazes);
        assertEquals(NoduleRules.MAX_STAGE, timer.stage());
        assertEquals(NoduleRules.GRAZE_INTERVAL / 4, NoduleRules.growthTicks(NoduleRules.GRAZE_INTERVAL, NoduleFamily.DIAMOND));
        assertEquals(0, NoduleRules.growthTicks(NoduleRules.GRAZE_INTERVAL, null));
        assertFalse(NoduleRules.mayGrow(false, false), "balance key off");
        assertFalse(NoduleRules.mayGrow(true, true), "babies grow none");
    }

    @Test
    void harvestYieldIsCappedAndResetsGrowth() {
        assertEquals(3, NoduleRules.harvestYield(3, 1, NoduleRules.DEFAULT_YIELD_CAP));
        assertEquals(NoduleRules.DEFAULT_YIELD_CAP, NoduleRules.harvestYield(3, 2, NoduleRules.DEFAULT_YIELD_CAP), "Bountiful, capped");
        assertEquals(2, NoduleRules.harvestYield(1, 2, NoduleRules.DEFAULT_YIELD_CAP));
        assertEquals(0, NoduleRules.harvestYield(3, 1, 0), "cap 0 disables");
        ProductTimer timer = new ProductTimer(100, NoduleRules.MAX_STAGE, 64);
        assertEquals(0, timer.harvest(3, base -> NoduleRules.harvestYield(base, 1, 4)), "not ready: nothing");
        timer.advance(100);
        assertEquals(3, timer.harvest(3, base -> NoduleRules.harvestYield(base, 1, 4)));
        assertFalse(timer.isReady(), "regrows from bare");
        assertTrue(NoduleRules.shedsScute(0.1F));
        assertFalse(NoduleRules.shedsScute(0.5F));
    }

    @Test
    void familiesRoundTripTheirVariant() {
        for (NoduleFamily family : NoduleFamily.values()) {
            assertEquals(family, NoduleFamily.byVariant(family.variant()));
            assertTrue(family.variant() >= 0 && family.variant() <= 7);
            assertTrue(family.tagPath().startsWith("lode_tortoise/"));
        }
        assertNull(NoduleFamily.byVariant(-1));
        assertNull(NoduleFamily.byVariant(8));
    }

    // --- overworld spawn gates (decision 005) ------------------------------------------------------

    @Test
    void overworldGatesHoldTheirRules() {
        assertTrue(LunaSpawnRules.mothSpot(true, true, 7));
        assertFalse(LunaSpawnRules.mothSpot(false, true, 0), "day");
        assertFalse(LunaSpawnRules.mothSpot(true, true, 8), "lit");
        assertTrue(LunaSpawnRules.tortoiseSpot(-20, false, 0, true));
        assertFalse(LunaSpawnRules.tortoiseSpot(5, false, 0, true), "too high");
        assertFalse(LunaSpawnRules.tortoiseSpot(-20, true, 0, true), "sky");
        assertFalse(LunaSpawnRules.tortoiseSpot(-20, false, 3, true), "sky light");
        assertFalse(LunaSpawnRules.tortoiseSpot(-20, false, 0, false), "not on stone");
        assertTrue(LunaSpawnRules.caveFloor(true, true, true));
        assertFalse(LunaSpawnRules.caveFloor(true, true, false));
        int[] band = LunaSpawnRules.caveBand(-64);
        assertEquals(-56, band[0]);
        assertEquals(0, band[1]);
    }

    @Test
    void chanceGatesStayRareOverManyAttempts() {
        java.util.Random random = new java.util.Random(42L);
        int moth = 0;
        int tortoise = 0;
        int attempts = 100_000;
        for (int i = 0; i < attempts; i++) {
            if (LunaSpawnRules.roll(random.nextDouble(), LunaSpawnRules.MOTH_OVERWORLD_CHANCE)) {
                moth++;
            }
            if (LunaSpawnRules.roll(random.nextDouble(), LunaSpawnRules.TORTOISE_CAVE_CHANCE)) {
                tortoise++;
            }
        }
        assertEquals(0.15D, moth / (double) attempts, 0.01D);
        assertEquals(0.05D, tortoise / (double) attempts, 0.01D);
    }
}
