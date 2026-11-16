package za.co.neroland.nerocreatures.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.luna.LunaSpawnRules;

/** Overworld spawns: the per-attempt chance keeps overworld spawns rare and the alive cap holds. */
class OverworldSpawnRulesTest {

    private static final int ATTEMPTS = 20_000;

    @Test
    void capHoldsAndZeroMeansNone() {
        assertTrue(OverworldSpawnRules.hasRoom(0, 12));
        assertTrue(OverworldSpawnRules.hasRoom(11, 12));
        assertFalse(OverworldSpawnRules.hasRoom(12, 12));
        assertFalse(OverworldSpawnRules.hasRoom(0, 0));
    }

    @Test
    void chanceGateAndCapHoldOverManyAttempts() {
        Random random = new Random(5L);
        int cap = 12;
        int alive = 0;
        int passed = 0;
        for (int i = 0; i < ATTEMPTS; i++) {
            if (!OverworldSpawnRules.roll(random.nextDouble(), LunaSpawnRules.TORTOISE_CAVE_CHANCE)) {
                continue;
            }
            passed++;
            if (OverworldSpawnRules.hasRoom(alive, cap)) {
                alive++;
            }
        }
        assertEquals(cap, alive, "never more than the cap alive");
        double rate = passed / (double) ATTEMPTS;
        assertTrue(rate > 0.04D && rate < 0.06D, "about 5% of attempts pass: " + rate);
    }

    @Test
    void mothChanceIsAboutFifteenPercent() {
        Random random = new Random(9L);
        int passed = 0;
        for (int i = 0; i < ATTEMPTS; i++) {
            if (OverworldSpawnRules.roll(random.nextDouble(), LunaSpawnRules.MOTH_OVERWORLD_CHANCE)) {
                passed++;
            }
        }
        double rate = passed / (double) ATTEMPTS;
        assertTrue(rate > 0.13D && rate < 0.17D, "about 15%: " + rate);
    }
}
