package za.co.neroland.nerocreatures.entity.wrecks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwarmRulesTest {

    @Test
    void drainIsKeyedAndCapped() {
        assertFalse(SwarmRules.drainEnabled(false), "staticSwarmDrain off: no drain, no jar burst");
        assertTrue(SwarmRules.drainEnabled(true));
        assertEquals(SwarmRules.MAX_PLAYERS, SwarmRules.take(40, SwarmRules.MAX_PLAYERS));
        assertEquals(0, SwarmRules.take(-3, SwarmRules.MAX_PLAYERS));
        assertEquals(1, SwarmRules.builtInDrain(true, 16, SwarmRules.MAX_UNITS_PER_PLAYER));
        assertEquals(0, SwarmRules.builtInDrain(false, 16, 5), "no handler, not a cell: documented no-op");
        assertEquals(2, SwarmRules.builtInDrain(true, 2, 5), "never more than the stack");
        assertEquals(0, SwarmRules.builtInDrain(true, 0, 5));
        assertTrue(SwarmRules.DRAIN_POSE_TICKS < 60);
    }

    @Test
    void bottlesOnlyWhenWeakened() {
        assertFalse(SwarmRules.mayBottle(16.0F, 16.0F));
        assertFalse(SwarmRules.mayBottle(8.1F, 16.0F));
        assertTrue(SwarmRules.mayBottle(8.0F, 16.0F));
        assertFalse(SwarmRules.mayBottle(0.0F, 16.0F), "dead");
        assertFalse(SwarmRules.mayBottle(1.0F, 0.0F));
    }

    @Test
    void overworldStormRules() {
        assertTrue(SwarmRules.vanishes(true, false, false), "storm over: gone");
        assertFalse(SwarmRules.vanishes(true, true, false));
        assertFalse(SwarmRules.vanishes(true, false, true), "wreck habitat keeps it");
        assertFalse(SwarmRules.vanishes(false, false, false), "only the overworld");
        assertTrue(SwarmRules.tooBright(true, SwarmRules.OVERWORLD_LIGHT_LIMIT));
        assertFalse(SwarmRules.tooBright(true, SwarmRules.OVERWORLD_LIGHT_LIMIT - 1));
        assertFalse(SwarmRules.tooBright(false, 15), "stations may be lit");
        assertTrue(SwarmRules.stormSpot(true, true, 0));
        assertFalse(SwarmRules.stormSpot(false, true, 0));
        assertFalse(SwarmRules.stormSpot(true, false, 0));
        assertFalse(SwarmRules.stormSpot(true, true, 12));
    }

    @Test
    void stormChanceAndAreaCapHoldOverManyAttempts() {
        java.util.Random random = new java.util.Random(42L);
        int passed = 0;
        int attempts = 20000;
        for (int i = 0; i < attempts; i++) {
            if (SwarmRules.roll(random.nextDouble(), SwarmRules.STORM_CHANCE)) {
                passed++;
            }
        }
        double rate = passed / (double) attempts;
        assertTrue(Math.abs(rate - SwarmRules.STORM_CHANCE) < 0.01D, "rare stays rare: " + rate);
        assertTrue(SwarmRules.roomInArea(SwarmRules.MAX_PER_AREA - 1));
        assertFalse(SwarmRules.roomInArea(SwarmRules.MAX_PER_AREA), "hard cap");
    }

    @Test
    void jarArcFallsAndIsBounded() {
        double[] vel = SwarmRules.launch(0.0D, 0.0D, 2.0D);
        assertEquals(SwarmRules.JAR_SPEED, vel[2], 1.0E-9);
        double[] pos = {0.0D, 64.0D, 0.0D};
        double maxZ = 0.0D;
        for (int i = 0; i < SwarmRules.JAR_MAX_STEPS; i++) {
            SwarmRules.step(pos, vel);
            maxZ = pos[2];
        }
        assertTrue(pos[1] < 64.0D, "gravity pulls it down");
        assertTrue(maxZ < SwarmRules.JAR_SPEED * SwarmRules.JAR_MAX_STEPS, "drag slows it");
        double[] none = SwarmRules.launch(0.0D, 0.0D, 0.0D);
        assertEquals(0.0D, none[0]);
        assertEquals(SwarmRules.MAX_JAR_TARGETS, SwarmRules.take(100, SwarmRules.MAX_JAR_TARGETS), "burst cap");
        assertTrue(SwarmRules.JAR_DISABLE_TICKS > 0 && SwarmRules.JAR_DISABLE_TICKS <= 200, "brief");
    }
}
