package za.co.neroland.nerocreatures.entity.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.shared.TetherState;

/** Gravity Leech: modes, the flip cap, lease expiry, wind-up, the tether's break rules and caps. */
class GravityTetherRulesTest {

    @Test
    void modesSplitOnTheFlipChance() {
        assertEquals(GravityTetherRules.Mode.FLIP, GravityTetherRules.chooseMode(0.0D));
        assertEquals(GravityTetherRules.Mode.LIGHTEN, GravityTetherRules.chooseMode(GravityTetherRules.FLIP_CHANCE));
        assertEquals(GravityTetherRules.Mode.LIGHTEN, GravityTetherRules.chooseMode(0.99D));
    }

    @Test
    void flipIsBriefThenLightens() {
        assertEquals(GravityTetherRules.FLIP_AMOUNT, GravityTetherRules.amount(GravityTetherRules.Mode.FLIP, 0));
        assertEquals(GravityTetherRules.LIGHTEN_AMOUNT,
                GravityTetherRules.amount(GravityTetherRules.Mode.FLIP, GravityTetherRules.FLIP_TICKS), "flip is capped");
        assertEquals(GravityTetherRules.LIGHTEN_AMOUNT, GravityTetherRules.amount(GravityTetherRules.Mode.LIGHTEN, 0));
        assertTrue(GravityTetherRules.effectiveGravity(0.08D, GravityTetherRules.FLIP_AMOUNT) < 0.0D, "flipped");
        double light = GravityTetherRules.effectiveGravity(0.08D, GravityTetherRules.LIGHTEN_AMOUNT);
        assertTrue(light > 0.0D && light < 0.08D, "lightened, never weightless");
        assertTrue(GravityTetherRules.effectiveGravity(0.08D, GravityTetherRules.FLIP_AMOUNT) >= -1.0D,
                "inside the attribute range");
    }

    @Test
    void leasesExpireWhenNotRenewed() {
        long until = GravityTetherRules.leaseUntil(100);
        assertFalse(GravityTetherRules.leaseExpired(100 + GravityTetherRules.PULSE_TICKS * 2, until),
                "two missed pulses still hold");
        assertTrue(GravityTetherRules.leaseExpired(until, until), "then it ends on its own");
        assertTrue(GravityTetherRules.hasLeaseRoom(GravityTetherRules.MAX_LEASES - 1));
        assertFalse(GravityTetherRules.hasLeaseRoom(GravityTetherRules.MAX_LEASES), "lease table is capped");
    }

    @Test
    void windupTelegraphsTheLatch() {
        assertFalse(GravityTetherRules.windupDone(GravityTetherRules.LATCH_TICK - 1));
        assertTrue(GravityTetherRules.windupDone(GravityTetherRules.LATCH_TICK));
        assertTrue(GravityTetherRules.LATCH_TICK >= 10, "at least 10 ticks of tell");
        assertTrue(GravityTetherRules.LATCH_TICK < GravityTetherRules.ABILITY_CLIP_TICKS);
    }

    @Test
    void tetherBreaksOnDamageDistanceAndTime() {
        TetherState state = new TetherState(GravityTetherRules.BREAK_DISTANCE, GravityTetherRules.BREAK_DAMAGE,
                GravityTetherRules.MAX_HOLD_TICKS, GravityTetherRules.COOLDOWN_TICKS);
        assertTrue(state.latch(7, 0));
        assertEquals(TetherState.Status.HOLD, state.check(4.0D, 10));
        double far = GravityTetherRules.BREAK_DISTANCE + 1.0D;
        assertEquals(TetherState.Status.BROKEN_DISTANCE, state.check(far * far, 10));
        assertEquals(TetherState.Status.EXPIRED, state.check(4.0D, GravityTetherRules.MAX_HOLD_TICKS));
        assertTrue(state.recordDamage(GravityTetherRules.BREAK_DAMAGE), "enough damage snaps it");
        state.release(20);
        assertFalse(state.canLatch(20 + GravityTetherRules.COOLDOWN_TICKS - 1), "cooldown");
        assertTrue(state.canLatch(20 + GravityTetherRules.COOLDOWN_TICKS));
    }

    @Test
    void trailIsBounded() {
        assertEquals(2, GravityTetherRules.trailPoints(0.0D));
        assertEquals(GravityTetherRules.MAX_TRAIL_POINTS, GravityTetherRules.trailPoints(100.0D));
    }
}
