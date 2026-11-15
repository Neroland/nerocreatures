package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HerdRulesTest {

    @Test
    void centroidOfMates() {
        assertArrayEquals(new double[] {2.0, 3.0}, HerdRules.centroid(new double[] {0, 4}, new double[] {2, 4}), 1e-9);
        assertNull(HerdRules.centroid(new double[0], new double[0]));
    }

    @Test
    void regroupsOnlyWhenStrayedAndNotAlone() {
        assertTrue(HerdRules.shouldRegroup(3, 9.0 * 9.0, 8.0));
        assertFalse(HerdRules.shouldRegroup(3, 7.0 * 7.0, 8.0));
        assertFalse(HerdRules.shouldRegroup(0, 100.0 * 100.0, 8.0), "a herd of one never regroups");
    }

    @Test
    void stopsAtHalfGap() {
        assertTrue(HerdRules.regrouped(3.9 * 3.9, 8.0));
        assertFalse(HerdRules.regrouped(4.1 * 4.1, 8.0));
    }
}
