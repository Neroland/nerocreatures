package za.co.neroland.nerocreatures.entity.wrecks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MimicRulesTest {

    @Test
    void wakesOnlyFromDormantOnInteractionOrDamage() {
        assertTrue(MimicRules.wakes(true, true));
        assertFalse(MimicRules.wakes(true, false), "a dormant crate never wakes on its own");
        assertFalse(MimicRules.wakes(false, true), "already awake");
    }

    @Test
    void revealTelegraphGatesTheBite() {
        assertTrue(MimicRules.REVEAL_TICKS >= 10, "telegraph at least 10 ticks");
        assertTrue(MimicRules.REVEAL_TICKS < 60, "timed pose under 3 s");
        assertFalse(MimicRules.mayAttack(true, 1000), "never bites while dormant");
        assertFalse(MimicRules.mayAttack(false, MimicRules.REVEAL_TICKS - 1));
        assertTrue(MimicRules.mayAttack(false, MimicRules.REVEAL_TICKS));
    }

    @Test
    void sleepsAfterTheIdleCap() {
        assertFalse(MimicRules.sleeps(false, MimicRules.SLEEP_AFTER_TICKS - 1));
        assertTrue(MimicRules.sleeps(false, MimicRules.SLEEP_AFTER_TICKS));
        assertFalse(MimicRules.sleeps(true, MimicRules.SLEEP_AFTER_TICKS * 2), "dormant stays dormant");
    }

    @Test
    void dormantCrateSitsOnTheGrid() {
        assertEquals(0.0F, MimicRules.snapYaw(10.0F));
        assertEquals(90.0F, MimicRules.snapYaw(80.0F));
        assertEquals(180.0F, MimicRules.snapYaw(-170.0F));
        assertEquals(270.0F, MimicRules.snapYaw(-90.0F));
        assertEquals(0.0F, MimicRules.snapYaw(359.0F));
        assertEquals(0.0F, MimicRules.snapYaw(720.0F));
        assertEquals(3.5D, MimicRules.blockCentre(3.99D));
        assertEquals(-2.5D, MimicRules.blockCentre(-2.01D));
    }

    @Test
    void skinsAndSpawnGate() {
        for (int roll = -5; roll < 10; roll++) {
            int skin = MimicRules.skin(roll);
            assertTrue(skin >= 0 && skin < MimicRules.SKINS && skin <= 7, "variant stays in the 0-7 field");
        }
        assertTrue(MimicRules.maySpawn(true, true));
        assertFalse(MimicRules.maySpawn(false, true), "cacheMimicSpawns off");
        assertFalse(MimicRules.maySpawn(true, false), "space levels only");
    }
}
