package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CadenceTimerTest {

    @Test
    void firesAtMostOncePerInterval() {
        CadenceTimer timer = new CadenceTimer(20);
        int fired = 0;
        for (long t = 0; t < 200; t++) {
            if (timer.ready(t, 0)) {
                fired++;
            }
        }
        assertEquals(10, fired);
    }

    @Test
    void neverMissesWhenCheckedEveryOtherTick() {
        // Vanilla evaluates idle goals on alternate ticks; an odd phase must still fire.
        CadenceTimer timer = new CadenceTimer(10);
        int fired = 0;
        for (long t = 0; t < 100; t += 2) {
            if (timer.ready(t, 7)) {
                fired++;
            }
        }
        assertTrue(fired >= 9, "fired " + fired);
    }

    @Test
    void phaseSpreadsFirstFiring() {
        CadenceTimer a = new CadenceTimer(40);
        CadenceTimer b = new CadenceTimer(40);
        assertTrue(a.ready(1000, 0));
        assertFalse(b.ready(1000, 13));
        assertTrue(b.ready(1013, 13));
    }

    @Test
    void delayPushesNextFiring() {
        CadenceTimer timer = new CadenceTimer(5);
        assertTrue(timer.ready(0, 0));
        timer.delay(0, 50);
        assertFalse(timer.ready(49, 0));
        assertTrue(timer.ready(50, 0));
    }
}
