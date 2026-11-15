package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PredatorPreyRulesTest {

    @Test
    void satiationCapsHunting() {
        assertTrue(PredatorPreyRules.hungry(0, Long.MIN_VALUE, 2400));
        assertFalse(PredatorPreyRules.hungry(2399, 0, 2400));
        assertTrue(PredatorPreyRules.hungry(2400, 0, 2400));
    }

    @Test
    void chaseTimesOut() {
        assertFalse(PredatorPreyRules.chaseTimedOut(399, 0, 400));
        assertTrue(PredatorPreyRules.chaseTimedOut(400, 0, 400));
        assertFalse(PredatorPreyRules.chaseTimedOut(10_000, 0, 0), "0 = no timeout");
    }

    @Test
    void pressureCapLimitsHuntersPerPrey() {
        assertTrue(PredatorPreyRules.pressureAllows(0));
        assertTrue(PredatorPreyRules.pressureAllows(PredatorPreyRules.MAX_HUNTERS_PER_PREY - 1));
        assertFalse(PredatorPreyRules.pressureAllows(PredatorPreyRules.MAX_HUNTERS_PER_PREY));
    }

    @Test
    void nearestWithinRadius() {
        assertEquals(1, PredatorPreyRules.nearestWithin(new double[] {100, 25, 49}, 16));
        assertEquals(-1, PredatorPreyRules.nearestWithin(new double[] {300, 400}, 16));
        assertEquals(-1, PredatorPreyRules.nearestWithin(new double[0], 16));
        assertEquals(0, PredatorPreyRules.nearestWithin(new double[] {4, 4}, 16), "ties keep the first");
    }

    @Test
    void alarmWindow() {
        assertTrue(PredatorPreyRules.alarmActive(99, 100));
        assertFalse(PredatorPreyRules.alarmActive(100, 100));
        assertFalse(PredatorPreyRules.alarmActive(0, Long.MIN_VALUE));
    }
}
