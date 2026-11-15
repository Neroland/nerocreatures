package za.co.neroland.nerocreatures.entity.mars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DuneLoperLeapTest {

    private static final double GRAVITY = 0.08D;

    @Test
    void fullerBarLeapsHigherAndFurtherWithinCaps() {
        assertTrue(DuneLoperLeap.upwardSpeed(1.0D) > DuneLoperLeap.upwardSpeed(0.4D));
        assertTrue(DuneLoperLeap.forwardSpeed(1.0D, 1.0D) > DuneLoperLeap.forwardSpeed(0.4D, 1.0D));
        assertEquals(DuneLoperLeap.MAX_UP, DuneLoperLeap.upwardSpeed(5.0D), 1.0E-9);
        assertEquals(DuneLoperLeap.MAX_FORWARD, DuneLoperLeap.forwardSpeed(5.0D, 1.0D), 1.0E-9);
        assertEquals(DuneLoperLeap.MIN_UP, DuneLoperLeap.upwardSpeed(Double.NaN), 1.0E-9);
        assertTrue(DuneLoperLeap.forwardSpeed(1.0D, -1.0D) < DuneLoperLeap.forwardSpeed(1.0D, 0.0D),
                "backing up is a short hop");
    }

    @Test
    void lowGravityOnlyInsideTheWindowAndOffTheGround() {
        double lift = DuneLoperLeap.liftPerTick(GRAVITY, 10, false);
        assertEquals(GRAVITY * (1.0D - DuneLoperLeap.LOW_GRAVITY_FACTOR), lift, 1.0E-9);
        assertTrue(lift < GRAVITY, "never cancels gravity completely: the leap always comes down");
        assertEquals(0.0D, DuneLoperLeap.liftPerTick(GRAVITY, 0, false));
        assertEquals(0.0D, DuneLoperLeap.liftPerTick(GRAVITY, 10, true));
        assertEquals(0.0D, DuneLoperLeap.liftPerTick(0.0D, 10, false), "zero gravity adds nothing");
    }

    @Test
    void windowIsCappedAndEndsOnLanding() {
        int window = DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS;
        int ticks = 0;
        while (window > 0) {
            window = DuneLoperLeap.nextWindow(window, false, ticks++);
        }
        assertEquals(DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS, ticks, "never longer than the cap airborne");
        assertEquals(DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS - 1,
                DuneLoperLeap.nextWindow(DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS, true, 0),
                "the take-off tick may still read as grounded");
        assertEquals(0, DuneLoperLeap.nextWindow(20, true, 10), "landing ends it");
        assertEquals(DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS, DuneLoperLeap.nextWindow(1000, false, 3));
    }

    @Test
    void fallIsForgivenOnlyForTheWindowPlusGrace() {
        assertTrue(DuneLoperLeap.fallForgiven(0));
        assertTrue(DuneLoperLeap.fallForgiven(DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS + DuneLoperLeap.LANDING_GRACE_TICKS));
        assertFalse(DuneLoperLeap.fallForgiven(DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS + DuneLoperLeap.LANDING_GRACE_TICKS + 1));
        assertFalse(DuneLoperLeap.fallForgiven(-1));
    }
}
