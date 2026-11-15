package za.co.neroland.nerocreatures.entity.mars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class StrataScanTest {

    @Test
    void ringsCoverEveryColumnExactlyOnce() {
        int radius = 7;
        Set<Long> seen = new HashSet<>();
        for (int d = 0; d <= radius; d++) {
            for (int i = 0; i < StrataScan.columnsInRing(d); i++) {
                int[] c = StrataScan.column(d, i);
                assertEquals(d, Math.max(Math.abs(c[0]), Math.abs(c[1])), "column in its ring");
                assertTrue(seen.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL)), "no repeats");
            }
        }
        assertEquals((2 * radius + 1) * (2 * radius + 1), seen.size());
    }

    @Test
    void radiusFollowsConfigAndKeenWithinCaps() {
        assertEquals(24, StrataScan.effectiveRadius(24, 1.0D));
        assertEquals(30, StrataScan.effectiveRadius(24, 1.25D), "Keen widens it");
        assertEquals(StrataScan.MAX_RADIUS, StrataScan.effectiveRadius(48, 1.25D), "hard cap");
        assertEquals(StrataScan.MAX_RADIUS, StrataScan.effectiveRadius(1000, 10.0D));
        assertEquals(StrataScan.MIN_RADIUS, StrataScan.effectiveRadius(0, 1.0D));
        assertEquals(24, StrataScan.effectiveRadius(24, Double.NaN));
        assertEquals(StrataScan.MAX_VERTICAL, StrataScan.verticalFor(60));
    }

    @Test
    void eachStepReadsAtMostTheBudget() {
        StrataScan scan = new StrataScan(StrataScan.MAX_RADIUS, StrataScan.MAX_VERTICAL);
        int steps = 0;
        long before = 0;
        while (!scan.step((dx, dz) -> true, (dx, dy, dz) -> false)) {
            long read = scan.blocksRead() - before;
            assertTrue(read <= StrataScan.BLOCKS_PER_STEP, "step read " + read);
            before = scan.blocksRead();
            steps++;
            assertTrue(steps < 100_000, "terminates");
        }
        assertFalse(scan.found());
        long columns = (2L * StrataScan.MAX_RADIUS + 1) * (2L * StrataScan.MAX_RADIUS + 1);
        assertEquals(columns * (2L * StrataScan.MAX_VERTICAL + 1), scan.blocksRead(), "whole box, once");
    }

    @Test
    void findsTheNearestMatchAndStopsAfterItsRing() {
        StrataScan scan = new StrataScan(24, 8);
        // Two ores in ring 5 and one in ring 9: the closer ring-5 one wins and ring 9 is never read.
        StrataScan.Probe probe = (dx, dy, dz) -> (dx == 5 && dy == 4 && dz == 0)
                || (dx == 5 && dy == 0 && dz == 1) || (dx == 9 && dy == 0 && dz == 0);
        while (!scan.step((dx, dz) -> true, probe)) {
            // keep stepping
        }
        assertTrue(scan.found());
        assertEquals(5, scan.bestDx());
        assertEquals(0, scan.bestDy());
        assertEquals(1, scan.bestDz());
        long throughRing5 = (11L * 11L) * 17L;
        assertEquals(throughRing5, scan.blocksRead(), "nothing beyond the ring of the first find");
    }

    @Test
    void unloadedColumnsAreNeverRead() {
        StrataScan scan = new StrataScan(6, 2);
        while (!scan.step((dx, dz) -> dx >= 0, (dx, dy, dz) -> {
            assertTrue(dx >= 0, "probed an unreadable column");
            return false;
        })) {
            // keep stepping
        }
        assertFalse(scan.found());
    }
}
