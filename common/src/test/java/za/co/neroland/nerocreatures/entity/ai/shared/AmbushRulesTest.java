package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmbushRulesTest {

    @Test
    void eruptsOnlyWhenWalkedOver() {
        assertTrue(AmbushRules.shouldErupt(1.0, 0.0, 1.5));
        assertFalse(AmbushRules.shouldErupt(4.0, 0.0, 1.5));
        assertFalse(AmbushRules.shouldErupt(0.0, 3.0, 1.5), "flying far above does not trigger");
        assertFalse(AmbushRules.shouldErupt(0.0, -1.0, 1.5), "below the ambusher does not trigger");
        assertFalse(AmbushRules.shouldErupt(0.0, 0.0, 0.0));
    }

    @Test
    void cooldownCapsEruptions() {
        assertTrue(AmbushRules.cooldownReady(0, Long.MIN_VALUE, 200));
        assertFalse(AmbushRules.cooldownReady(150, 0, 200));
        assertTrue(AmbushRules.cooldownReady(200, 0, 200));
    }

    @Test
    void lurkHasAMaximum() {
        assertFalse(AmbushRules.lurkExpired(100, 0, 0), "0 = unlimited");
        assertFalse(AmbushRules.lurkExpired(599, 0, 600));
        assertTrue(AmbushRules.lurkExpired(600, 0, 600));
    }
}
