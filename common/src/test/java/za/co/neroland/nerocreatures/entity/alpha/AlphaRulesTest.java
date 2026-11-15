package za.co.neroland.nerocreatures.entity.alpha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AlphaRulesTest {

    @Test
    void rollRespectsChance() {
        assertTrue(AlphaRules.rolls(0.02, 0.019));
        assertFalse(AlphaRules.rolls(0.02, 0.02));
        assertFalse(AlphaRules.rolls(0.0, 0.0));
        assertFalse(AlphaRules.rolls(-1.0, 0.0), "negative chance clamps to 0");
        assertTrue(AlphaRules.rolls(5.0, 0.999), "chance clamps to 1");
    }

    @Test
    void capPerDimension() {
        assertTrue(AlphaRules.capAllows(0, 4));
        assertTrue(AlphaRules.capAllows(3, 4));
        assertFalse(AlphaRules.capAllows(4, 4));
        assertFalse(AlphaRules.capAllows(0, 0), "cap 0 disables alphas");
    }

    @Test
    void effectiveCapUsesOverrideAndHardCeiling() {
        assertEquals(4, AlphaRules.effectiveCap(4, -1));
        assertEquals(1, AlphaRules.effectiveCap(4, 1));
        assertEquals(0, AlphaRules.effectiveCap(4, 0));
        assertEquals(AlphaSettings.ABSOLUTE_MAX_PER_DIMENSION, AlphaRules.effectiveCap(10_000, -1));
        assertEquals(0, AlphaRules.effectiveCap(-3, -1));
    }

    @Test
    void weightedPickFollowsWeights() {
        List<String> options = List.of("a", "b");
        Map<String, Integer> weights = Map.of("a", 1, "b", 3);
        Map<String, Integer> counts = new HashMap<>();
        for (int roll = 0; roll < 400; roll++) {
            counts.merge(AlphaRules.pickWeighted(options, weights::get, roll), 1, Integer::sum);
        }
        assertEquals(100, counts.get("a"));
        assertEquals(300, counts.get("b"));
        assertNull(AlphaRules.pickWeighted(List.<String>of(), s -> 1, 5));
        assertEquals("a", AlphaRules.pickWeighted(List.of("a"), s -> 0, Integer.MAX_VALUE), "weight <1 counts as 1");
    }

    @Test
    void dropFileSplitsId() {
        assertEquals("nerocreatures", AlphaRules.dropFileFor("nerocreatures:void_crawler")[0]);
        assertEquals("void_crawler", AlphaRules.dropFileFor("nerocreatures:void_crawler")[1]);
    }
}
