package za.co.neroland.nerocreatures.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** ephemeral_crust: decay window clamp, lost-tick fallback, fluid refill. */
class EphemeralCrustRulesTest {

    @Test
    void decayIsClamped() {
        assertEquals(EphemeralCrustRules.MIN_DECAY_TICKS, EphemeralCrustRules.clampDecay(0));
        assertEquals(EphemeralCrustRules.MIN_DECAY_TICKS, EphemeralCrustRules.clampDecay(-50));
        assertEquals(900, EphemeralCrustRules.clampDecay(900));
        assertEquals(EphemeralCrustRules.MAX_DECAY_TICKS, EphemeralCrustRules.clampDecay(Integer.MAX_VALUE));
    }

    @Test
    void randomTickRevertsOnlyWhenTheScheduledTickIsLost() {
        assertFalse(EphemeralCrustRules.revertOnRandomTick(true));
        assertTrue(EphemeralCrustRules.revertOnRandomTick(false));
    }

    @Test
    void aBrokenCrustPutsItsFluidSourceBack() {
        assertTrue(EphemeralCrustRules.refillAfterBreak(true, true));
        assertFalse(EphemeralCrustRules.refillAfterBreak(false, true), "restore=air stays air");
        assertFalse(EphemeralCrustRules.refillAfterBreak(true, false), "never overwrites a new block");
    }
}
