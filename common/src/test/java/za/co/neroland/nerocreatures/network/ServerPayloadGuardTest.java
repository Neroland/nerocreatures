package za.co.neroland.nerocreatures.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.network.ServerPayloadGuard.Verdict;

class ServerPayloadGuardTest {

    @Test
    void acceptsOnlyWhenEveryCheckPasses() {
        assertEquals(Verdict.OK, ServerPayloadGuard.decide(true, true, true, true, 4.0D, 8.0D));
    }

    @Test
    void refusesInCheckOrder() {
        assertEquals(Verdict.SENDER_UNUSABLE, ServerPayloadGuard.decide(false, false, false, false, 1e9, 8.0D));
        assertEquals(Verdict.RATE_LIMITED, ServerPayloadGuard.decide(true, false, false, false, 1e9, 8.0D));
        assertEquals(Verdict.NO_ENTITY, ServerPayloadGuard.decide(true, true, false, false, 1e9, 8.0D));
        assertEquals(Verdict.NOT_OWNER, ServerPayloadGuard.decide(true, true, true, false, 0.0D, 8.0D));
        assertEquals(Verdict.OUT_OF_REACH, ServerPayloadGuard.decide(true, true, true, true, 64.01D, 8.0D));
    }

    @Test
    void reachIsInclusiveAndZeroReachRefuses() {
        assertEquals(Verdict.OK, ServerPayloadGuard.decide(true, true, true, true, 64.0D, 8.0D));
        assertEquals(Verdict.OUT_OF_REACH, ServerPayloadGuard.decide(true, true, true, true, 0.0D, 0.0D));
        assertEquals(Verdict.OUT_OF_REACH, ServerPayloadGuard.decide(true, true, true, true, 0.0D, Double.NaN));
    }

    @Test
    void rateWindowCapsThenResets() {
        ServerPayloadGuard.RateWindow window = new ServerPayloadGuard.RateWindow(20, 3);
        assertTrue(window.tryAcquire(100));
        assertTrue(window.tryAcquire(101));
        assertTrue(window.tryAcquire(119));
        assertFalse(window.tryAcquire(119));
        assertTrue(window.tryAcquire(120), "a new window starts after windowTicks");
        assertTrue(window.tryAcquire(5), "time going backwards (dimension change) resets");
    }
}
