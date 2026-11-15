package za.co.neroland.nerocreatures.entity.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Lunar Stalker polish: the howl fires only on a real recruit, and only the last survivor flees. */
class PackRulesTest {

    @Test
    void howlNeedsARecruit() {
        assertFalse(PackRules.howls(0));
        assertTrue(PackRules.howls(1));
    }

    @Test
    void onlyAHurtLoneStalkerFlees() {
        assertTrue(PackRules.shouldFlee(5.0F, 26.0F, 0));
        assertFalse(PackRules.shouldFlee(5.0F, 26.0F, 1), "a mate nearby: keep fighting");
        assertFalse(PackRules.shouldFlee(20.0F, 26.0F, 0), "healthy: keep fighting");
        assertFalse(PackRules.shouldFlee(0.0F, 0.0F, 0), "no max health: never");
    }

    @Test
    void howlGlowIsCapped() {
        assertEquals(0, PackRules.glowCount(-1));
        assertEquals(3, PackRules.glowCount(3));
        assertEquals(PackRules.MAX_HOWL_GLOWS, PackRules.glowCount(50));
        assertTrue(PackRules.HOWL_GLOW_TICKS < 60, "a brief cue, not a debuff");
    }
}
