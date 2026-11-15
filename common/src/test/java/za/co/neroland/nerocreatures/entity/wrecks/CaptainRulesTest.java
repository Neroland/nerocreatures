package za.co.neroland.nerocreatures.entity.wrecks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaptainRulesTest {

    @Test
    void dashNeedsBandCooldownAndGround() {
        int ready = CaptainRules.DASH_COOLDOWN_TICKS;
        assertTrue(CaptainRules.mayDash(10.0D, ready, true));
        assertFalse(CaptainRules.mayDash(CaptainRules.DASH_MIN - 0.1D, ready, true), "too close");
        assertFalse(CaptainRules.mayDash(CaptainRules.DASH_MAX + 0.1D, ready, true), "too far");
        assertFalse(CaptainRules.mayDash(10.0D, ready - 1, true), "cooldown");
        assertFalse(CaptainRules.mayDash(10.0D, ready, false), "airborne");
        assertTrue(CaptainRules.DASH_TELEGRAPH_TICKS >= 10 && CaptainRules.DASH_POSE_TICKS < 60);
        double[] v = CaptainRules.dashVelocity(3.0D, 4.0D);
        assertEquals(CaptainRules.DASH_SPEED, Math.hypot(v[0], v[2]), 1.0E-9);
        assertEquals(CaptainRules.DASH_LIFT, v[1], 1.0E-9);
    }

    @Test
    void rallyIsPeriodicAndCapped() {
        assertTrue(CaptainRules.rallyDue(true, CaptainRules.RALLY_INTERVAL_TICKS));
        assertFalse(CaptainRules.rallyDue(false, CaptainRules.RALLY_INTERVAL_TICKS), "no target, no rally");
        assertFalse(CaptainRules.rallyDue(true, CaptainRules.RALLY_INTERVAL_TICKS - 1));
        assertTrue(CaptainRules.MAX_RALLIED > 0 && CaptainRules.MAX_RALLIED <= 8);
        assertTrue(CaptainRules.RALLY_POSE_TICKS < 60);
    }

    @Test
    void ledgerGoesToTheKillerOnly() {
        assertTrue(CaptainRules.ledgerGoesToKiller(true, true, true));
        assertFalse(CaptainRules.ledgerGoesToKiller(false, true, true), "no player credit: nobody, never nearest");
        assertFalse(CaptainRules.ledgerGoesToKiller(true, false, true), "other level");
        assertFalse(CaptainRules.ledgerGoesToKiller(true, true, false), "dead killer");
    }

    @Test
    void stashIsInTheBandAndReadable() {
        java.util.Random random = new java.util.Random(7L);
        for (int i = 0; i < 2000; i++) {
            int[] off = CaptainRules.stashOffset(random.nextDouble() * Math.PI * 2.0D, random.nextDouble());
            double d = Math.hypot(off[0], off[1]);
            assertTrue(d >= CaptainRules.STASH_MIN - 1 && d <= CaptainRules.STASH_MAX + 1, "distance " + d);
        }
        assertTrue(CaptainRules.atStash(3.0D, 4.0D));
        assertFalse(CaptainRules.atStash(6.0D, 6.0D));
        assertEquals(5, CaptainRules.roundedDistance(3.0D, 4.0D));
        assertEquals("south", CaptainRules.direction(0.0D, 10.0D));
        assertEquals("north", CaptainRules.direction(0.0D, -10.0D));
        assertEquals("east", CaptainRules.direction(10.0D, 0.0D));
        assertEquals("west", CaptainRules.direction(-10.0D, 0.0D));
        assertEquals("northeast", CaptainRules.direction(10.0D, -10.0D));
        assertEquals("southwest", CaptainRules.direction(-10.0D, 10.0D));
        assertEquals("here", CaptainRules.direction(0.0D, 0.0D));
    }
}
