package za.co.neroland.nerocreatures.entity.wrecks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.wrecks.ScrapHulkRules.Death;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

class ScrapHulkRulesTest {

    @Test
    void threeCloseRecentDeathsMakeACluster() {
        long now = 1000L;
        Death a = new Death(now - 150, 0, 64, 0);
        Death b = new Death(now - 50, 5, 64, 3);
        Death c = new Death(now, 2, 64, -2);
        List<Death> deaths = List.of(a, b, c);
        assertEquals(3, ScrapHulkRules.cluster(deaths, c, now).size());
        assertTrue(ScrapHulkRules.shouldAssemble(3, 0, 0));
        assertArrayEquals(new int[] {2, 64, 0}, ScrapHulkRules.centroid(ScrapHulkRules.cluster(deaths, c, now)));
        assertNull(ScrapHulkRules.centroid(List.of()));
    }

    @Test
    void staleOrDistantDeathsDoNotCount() {
        long now = 1000L;
        Death old = new Death(now - ScrapHulkRules.WINDOW_TICKS - 1, 0, 64, 0);
        Death far = new Death(now, 20, 64, 0);
        Death near = new Death(now - 10, 1, 64, 1);
        Death latest = new Death(now, 0, 64, 0);
        List<Death> cluster = ScrapHulkRules.cluster(List.of(old, far, near, latest), latest, now);
        assertEquals(2, cluster.size());
        assertFalse(ScrapHulkRules.shouldAssemble(cluster.size(), 0, 0), "fewer than three");
    }

    @Test
    void capsHold() {
        assertFalse(ScrapHulkRules.shouldAssemble(5, 1, 0), "one per 64 blocks");
        assertFalse(ScrapHulkRules.shouldAssemble(5, 0, ScrapHulkRules.MAX_PER_DIMENSION), "four per dimension");
        assertTrue(ScrapHulkRules.shouldAssemble(5, 0, ScrapHulkRules.MAX_PER_DIMENSION - 1));
        List<Death> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(new Death(500L + i, i, 64, 0));
        }
        List<Death> pruned = ScrapHulkRules.prune(many, 599L);
        assertEquals(ScrapHulkRules.BUFFER_CAP, pruned.size(), "buffer is bounded");
        assertEquals(599L, pruned.get(pruned.size() - 1).time(), "newest kept");
    }

    @Test
    void deniedGriefingMeansNoHulk() {
        FakeChecks denied = new FakeChecks();
        denied.mobGrief = false;
        boolean allowed = CreatureGriefing.decide(CreatureGriefing.Change.ENTITY, denied);
        assertFalse(allowed);
        assertFalse(ScrapHulkRules.assembles(true, allowed), "fallback: no hulk");
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false;
        assertFalse(ScrapHulkRules.assembles(true, CreatureGriefing.decide(CreatureGriefing.Change.ENTITY, keyOff)));
        assertTrue(ScrapHulkRules.assembles(true, CreatureGriefing.decide(CreatureGriefing.Change.ENTITY, new FakeChecks())));
    }

    @Test
    void shedsOnePartPerHealthQuarterAtMostThree() {
        assertEquals(0, ScrapHulkRules.stageFor(120.0F, 120.0F));
        assertEquals(1, ScrapHulkRules.stageFor(80.0F, 120.0F));
        assertEquals(2, ScrapHulkRules.stageFor(50.0F, 120.0F));
        assertEquals(3, ScrapHulkRules.stageFor(10.0F, 120.0F));
        assertEquals(2, ScrapHulkRules.partsToShed(0, 2), "a big hit sheds both");
        assertEquals(0, ScrapHulkRules.partsToShed(2, 1), "never re-grows or double-sheds");
        assertEquals(3, ScrapHulkRules.partsToShed(-4, 9), "clamped to three");
        assertEquals(4.0D, ScrapHulkRules.armourAt(10.0D, 3), 1.0E-9);
        assertEquals(0.0D, ScrapHulkRules.armourAt(2.0D, 3), 1.0E-9);
        assertTrue(ScrapHulkRules.ASSEMBLE_TICKS < 60 && ScrapHulkRules.SWEEP_POSE_TICKS < 60);
        assertTrue(ScrapHulkRules.SWEEP_TELEGRAPH_TICKS >= 10, "sweep telegraph");
    }

    @Test
    void sweepHitsTheFrontArcOnly() {
        assertTrue(ScrapHulkRules.inSweep(0.0D, 1.0D, 0.0D, 2.0D));
        assertFalse(ScrapHulkRules.inSweep(0.0D, 1.0D, 0.0D, -2.0D), "behind");
        assertFalse(ScrapHulkRules.inSweep(0.0D, 1.0D, 0.0D, ScrapHulkRules.SWEEP_RANGE + 0.5D), "out of reach");
    }
}
