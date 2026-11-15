package za.co.neroland.nerocreatures.entity.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VisualsBitsTest {

    @Test
    void everyFieldRoundTripsIndependently() {
        int s = 0;
        s = VisualsBits.withPose(s, VisualPose.ABILITY_2.ordinal());
        s = VisualsBits.withPhase(s, 3);
        s = VisualsBits.withStage(s, 2);
        s = VisualsBits.withVariant(s, 7);
        s = VisualsBits.withForm(s, 5);
        s = VisualsBits.withFlag(s, VisualsBits.FLAG_RADIANT, true);
        s = VisualsBits.withFlag(s, VisualsBits.FLAG_PANNIERS, true);
        assertEquals(VisualPose.ABILITY_2.ordinal(), VisualsBits.pose(s));
        assertEquals(3, VisualsBits.phase(s));
        assertEquals(2, VisualsBits.stage(s));
        assertEquals(7, VisualsBits.variant(s));
        assertEquals(5, VisualsBits.form(s));
        assertTrue(VisualsBits.flag(s, VisualsBits.FLAG_RADIANT));
        assertFalse(VisualsBits.flag(s, VisualsBits.FLAG_ALPHA));
        assertFalse(VisualsBits.flag(s, VisualsBits.FLAG_SADDLED));
        assertTrue(VisualsBits.flag(s, VisualsBits.FLAG_PANNIERS));
        assertEquals(0, s >>> 16, "reserved bits stay zero");
    }

    @Test
    void outOfRangeValuesAreClampedAndNeverSpill() {
        int s = VisualsBits.withVariant(0, 99);
        assertEquals(7, VisualsBits.variant(s));
        assertEquals(0, VisualsBits.form(s));
        s = VisualsBits.withStage(s, -4);
        assertEquals(0, VisualsBits.stage(s));
        assertEquals(7, VisualsBits.variant(s));
        assertEquals(0, VisualsBits.timer(-1, -1));
        assertEquals(VisualsBits.MAX_TICKS, VisualsBits.duration(VisualsBits.timer(0, 1 << 20)));
    }

    @Test
    void persistentBitsExcludePoseAndPhase() {
        int s = VisualsBits.withPose(0, 1);
        s = VisualsBits.withPhase(s, 2);
        s = VisualsBits.withStage(s, 3);
        s = VisualsBits.withFlag(s, VisualsBits.FLAG_SADDLED, true);
        int saved = VisualsBits.persistent(s);
        assertEquals(0, VisualsBits.pose(saved));
        assertEquals(0, VisualsBits.phase(saved));
        int restored = VisualsBits.restorePersistent(VisualsBits.withPose(0, 3), saved);
        assertEquals(3, VisualsBits.pose(restored));
        assertEquals(3, VisualsBits.stage(restored));
        assertTrue(VisualsBits.flag(restored, VisualsBits.FLAG_SADDLED));
    }

    @Test
    void timedPoseCountsUpThenEnds() {
        int timer = VisualsBits.timer(0, 3);
        timer = CreatureVisuals.nextTimer(timer);
        assertEquals(1, VisualsBits.elapsed(timer));
        timer = CreatureVisuals.nextTimer(timer);
        assertEquals(2, VisualsBits.elapsed(timer));
        assertEquals(-1, CreatureVisuals.nextTimer(timer), "finished: back to idle");
    }

    @Test
    void heldPoseStopsCountingAtTheCap() {
        int timer = VisualsBits.timer(CreatureVisuals.HELD_COUNT_CAP - 1, 0);
        timer = CreatureVisuals.nextTimer(timer);
        assertEquals(CreatureVisuals.HELD_COUNT_CAP, VisualsBits.elapsed(timer));
        assertEquals(timer, CreatureVisuals.nextTimer(timer), "no further writes, so no further syncs");
    }

    @Test
    void snapshotInterpolatesAndReportsProgress() {
        int state = VisualsBits.withPose(VisualsBits.withVariant(0, 4), VisualPose.ABILITY.ordinal());
        VisualsSnapshot snap = VisualsSnapshot.of(state, VisualsBits.timer(5, 10), 0.5F);
        assertEquals(VisualPose.ABILITY, snap.pose());
        assertEquals(5.5F, snap.abilityTicks(), 1e-6F);
        assertEquals(0.55F, snap.progress(), 1e-6F);
        assertEquals(4, snap.variant());
        VisualsSnapshot idle = VisualsSnapshot.of(0, 0, 0.9F);
        assertEquals(0.0F, idle.abilityTicks(), 0.0F);
        assertEquals(VisualPose.IDLE, VisualPose.byId(99));
        assertEquals("ability_2", VisualPose.ABILITY_2.clip());
    }
}
