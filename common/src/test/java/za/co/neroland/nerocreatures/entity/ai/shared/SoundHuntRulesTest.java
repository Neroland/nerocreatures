package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.shared.SoundHuntRules.Noise;

class SoundHuntRulesTest {

    @Test
    void sprintMineAndLandDrawWalkingDoesNot() {
        assertTrue(SoundHuntRules.loudness(Noise.STEP, true, false, false) > 0);
        assertEquals(0, SoundHuntRules.loudness(Noise.STEP, false, false, false));
        assertEquals(SoundHuntRules.MAX_LOUDNESS, SoundHuntRules.loudness(Noise.MINE, false, false, false));
        assertEquals(SoundHuntRules.MAX_LOUDNESS, SoundHuntRules.loudness(Noise.LAND, false, false, false));
    }

    @Test
    void sneakingAndSilencedAreInvisible() {
        for (Noise noise : Noise.values()) {
            assertEquals(0, SoundHuntRules.loudness(noise, true, true, false));
            assertEquals(0, SoundHuntRules.loudness(noise, true, false, true));
        }
    }

    @Test
    void reachScalesWithLoudness() {
        // radius 16: loudness 3 reaches 16, loudness 2 reaches ~10.7
        assertTrue(SoundHuntRules.heard(3, 15.9 * 15.9, 16));
        assertFalse(SoundHuntRules.heard(3, 16.1 * 16.1, 16));
        assertTrue(SoundHuntRules.heard(2, 10.0 * 10.0, 16));
        assertFalse(SoundHuntRules.heard(2, 11.0 * 11.0, 16));
        assertFalse(SoundHuntRules.heard(0, 0.0, 16));
    }

    @Test
    void memoryAndReplacement() {
        assertTrue(SoundHuntRules.expired(Long.MIN_VALUE, 100, 60));
        assertFalse(SoundHuntRules.expired(100, 160, 60));
        assertTrue(SoundHuntRules.expired(100, 161, 60));
        // quieter fresh noise does not replace a louder remembered one
        assertFalse(SoundHuntRules.replaces(2, 3, 100, 110, 60));
        assertTrue(SoundHuntRules.replaces(3, 3, 100, 110, 60));
        // anything audible replaces a stale one
        assertTrue(SoundHuntRules.replaces(1, 3, 100, 200, 60));
        assertFalse(SoundHuntRules.replaces(0, 0, Long.MIN_VALUE, 200, 60));
    }
}
