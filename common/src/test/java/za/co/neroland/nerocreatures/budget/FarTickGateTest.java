package za.co.neroland.nerocreatures.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class FarTickGateTest {

    @Test
    void nearCreaturesNeverSkip() {
        for (long t = 0; t < 40; t++) {
            assertFalse(FarTickGate.shouldSkip(false, t, 7, 4));
        }
    }

    @Test
    void farCreaturesRunOneTickInInterval() {
        int runs = 0;
        for (long t = 0; t < 400; t++) {
            if (!FarTickGate.shouldSkip(true, t, 12345, 4)) {
                runs++;
            }
        }
        assertEquals(100, runs);
    }

    @Test
    void intervalOneDisablesSlowing() {
        for (long t = 0; t < 10; t++) {
            assertFalse(FarTickGate.shouldSkip(true, t, 3, 1));
        }
    }

    @Test
    void crowdIsStaggeredAndNegativeIdsAreSafe() {
        int awake = 0;
        for (int id = -8; id < 8; id++) {
            if (!FarTickGate.shouldSkip(true, 1000L, id, 4)) {
                awake++;
            }
        }
        assertEquals(4, awake, "a quarter of a crowd wakes on any one tick");
    }
}
