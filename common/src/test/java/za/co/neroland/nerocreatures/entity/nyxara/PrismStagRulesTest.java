package za.co.neroland.nerocreatures.entity.nyxara;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;

class PrismStagRulesTest {

    @Test
    void antlersGrowInVisualStagesAndShedOnlyWhenRipeAndAdult() {
        ProductTimer timer = new ProductTimer(PrismStagRules.shedTicks(1200), PrismStagRules.ANTLER_STAGES,
                PrismStagRules.SHED_CAP);
        assertEquals(0, PrismStagRules.visualStage(timer.stage()), "bare after a shed");
        timer.advance(400);
        assertEquals(1, PrismStagRules.visualStage(timer.stage()), "half-grown");
        timer.advance(400);
        assertEquals(2, PrismStagRules.visualStage(timer.stage()), "full");
        assertFalse(PrismStagRules.mayShed(timer.isReady(), true), "not ripe yet");
        timer.advance(400);
        assertEquals(2, PrismStagRules.visualStage(timer.stage()), "ripe draws as full, never past 2");
        assertFalse(PrismStagRules.mayShed(timer.isReady(), false), "babies never shed");
        assertTrue(PrismStagRules.mayShed(timer.isReady(), true));
        int count = timer.harvest(PrismStagRules.SHED_BASE,
                base -> PrismStagRules.shedYield(base, 1, PrismStagRules.SHED_CAP));
        assertEquals(PrismStagRules.SHED_BASE, count);
        assertEquals(0, timer.progress(), "growth restarts");
    }

    @Test
    void shedYieldIsCappedAndRegrowTimeHasAFloor() {
        assertEquals(2, PrismStagRules.shedYield(2, 1, PrismStagRules.SHED_CAP));
        assertEquals(4, PrismStagRules.shedYield(2, 2, PrismStagRules.SHED_CAP), "Bountiful doubles");
        assertEquals(PrismStagRules.SHED_CAP, PrismStagRules.shedYield(2, 50, PrismStagRules.SHED_CAP));
        assertEquals(PrismStagRules.SHED_CAP, PrismStagRules.shedYield(Integer.MAX_VALUE, Integer.MAX_VALUE,
                PrismStagRules.SHED_CAP), "no overflow");
        assertEquals(0, PrismStagRules.shedYield(-3, 1, PrismStagRules.SHED_CAP));
        assertEquals(PrismStagRules.DEFAULT_SHED_TICKS, PrismStagRules.shedTicks(0));
        assertEquals(PrismStagRules.MIN_SHED_TICKS, PrismStagRules.shedTicks(5));
        assertEquals(30_000, PrismStagRules.shedTicks(30_000));
    }

    @Test
    void auraNeedsNightAHerdAndTheSwitch() {
        assertTrue(PrismStagRules.auraLive(true, true, PrismStagRules.MIN_HERD));
        assertFalse(PrismStagRules.auraLive(true, true, PrismStagRules.MIN_HERD - 1), "too few");
        assertFalse(PrismStagRules.auraLive(true, false, 20), "day");
        assertFalse(PrismStagRules.auraLive(false, true, 20), "config off");
    }

    @Test
    void oneLeaderPerHerdAndPlayersAndRadiusAreCapped() {
        assertTrue(PrismStagRules.isLeader(5, 5));
        assertFalse(PrismStagRules.isLeader(9, 5));
        assertEquals(PrismStagRules.AURA_MAX_PLAYERS, PrismStagRules.take(100, PrismStagRules.AURA_MAX_PLAYERS));
        assertEquals(3, PrismStagRules.take(3, PrismStagRules.AURA_MAX_PLAYERS));
        assertEquals(0, PrismStagRules.take(-1, PrismStagRules.AURA_MAX_PLAYERS));
        assertEquals(PrismStagRules.AURA_RADIUS, PrismStagRules.auraRadius(1.0D), 1.0E-9);
        assertEquals(PrismStagRules.AURA_MAX_RADIUS, PrismStagRules.auraRadius(10.0D), 1.0E-9);
        assertEquals(PrismStagRules.AURA_RADIUS, PrismStagRules.auraRadius(0.1D), 1.0E-9, "never shrinks");
        assertEquals(PrismStagRules.AURA_RADIUS, PrismStagRules.auraRadius(Double.NaN), 1.0E-9);
        assertEquals(0, PrismStagRules.AURA_AMPLIFIER, "herd size never strengthens it");
        assertTrue(PrismStagRules.AURA_EFFECT_TICKS > PrismStagRules.AURA_INTERVAL, "no flicker between pulses");
        assertTrue(PrismStagRules.AURA_POSE_TICKS < 60 && PrismStagRules.SHED_POSE_TICKS < 60, "timed poses under 3 s");
    }
}
