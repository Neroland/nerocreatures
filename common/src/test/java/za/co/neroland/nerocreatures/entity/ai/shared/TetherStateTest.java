package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TetherStateTest {

    private static TetherState tether() {
        return new TetherState(10.0, 6.0F, 200, 100);
    }

    @Test
    void holdsWithinDistance() {
        TetherState t = tether();
        assertEquals(TetherState.Status.IDLE, t.check(0, 0));
        assertTrue(t.latch(5, 0));
        assertEquals(TetherState.Status.HOLD, t.check(9.9 * 9.9, 10));
    }

    @Test
    void breaksOnDistance() {
        TetherState t = tether();
        t.latch(5, 0);
        assertEquals(TetherState.Status.BROKEN_DISTANCE, t.check(10.1 * 10.1, 10));
    }

    @Test
    void breaksOnAccumulatedDamage() {
        TetherState t = tether();
        t.latch(5, 0);
        assertFalse(t.recordDamage(3.0F));
        assertTrue(t.recordDamage(3.0F));
        assertEquals(TetherState.Status.BROKEN_DAMAGE, t.check(1, 10));
    }

    @Test
    void expiresAtMaxDuration() {
        TetherState t = tether();
        t.latch(5, 0);
        assertEquals(TetherState.Status.EXPIRED, t.check(1, 200));
    }

    @Test
    void cooldownAfterReleaseAndNoDoubleLatch() {
        TetherState t = tether();
        assertTrue(t.latch(5, 0));
        assertFalse(t.latch(6, 1), "one tether at a time");
        t.release(50);
        assertFalse(t.isLatched());
        assertFalse(t.canLatch(149));
        assertTrue(t.canLatch(150));
        assertFalse(t.recordDamage(10.0F), "damage while idle is ignored");
    }
}
