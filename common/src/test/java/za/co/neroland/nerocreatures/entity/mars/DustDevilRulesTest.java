package za.co.neroland.nerocreatures.entity.mars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.mars.DustDevilRules.Phase;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

class DustDevilRulesTest {

    @Test
    void cycleRunsInOrderAndWaitsForATarget() {
        assertEquals(Phase.CALM, DustDevilRules.next(Phase.CALM, DustDevilRules.COOLDOWN_TICKS, false));
        assertEquals(Phase.CALM, DustDevilRules.next(Phase.CALM, DustDevilRules.COOLDOWN_TICKS - 1, true),
                "cooldown first");
        assertEquals(Phase.SPIN_UP, DustDevilRules.next(Phase.CALM, DustDevilRules.COOLDOWN_TICKS, true));
        assertEquals(Phase.SPIN_UP, DustDevilRules.next(Phase.SPIN_UP, DustDevilRules.SPIN_UP_TICKS - 1, true));
        assertEquals(Phase.WHIRL, DustDevilRules.next(Phase.SPIN_UP, DustDevilRules.SPIN_UP_TICKS, false));
        assertEquals(Phase.THROW, DustDevilRules.next(Phase.WHIRL, DustDevilRules.WHIRL_TICKS, true));
        assertEquals(Phase.CALM, DustDevilRules.next(Phase.THROW, DustDevilRules.THROW_TICKS, true));
    }

    @Test
    void invulnerableOnlyWhileSpinningUpAndTheTelegraphIsLongEnough() {
        assertTrue(DustDevilRules.invulnerable(Phase.SPIN_UP));
        assertFalse(DustDevilRules.invulnerable(Phase.WHIRL));
        assertFalse(DustDevilRules.invulnerable(Phase.THROW));
        assertFalse(DustDevilRules.invulnerable(Phase.CALM));
        assertTrue(DustDevilRules.SPIN_UP_TICKS >= 10, "telegraph at least 10 ticks");
        assertTrue(DustDevilRules.SPIN_UP_TICKS < 60 && DustDevilRules.THROW_TICKS < 60, "timed poses under 3 s");
    }

    @Test
    void liftIsCappedInCountAndHeight() {
        assertEquals(DustDevilRules.MAX_PLAYERS, DustDevilRules.take(50, DustDevilRules.MAX_PLAYERS));
        assertEquals(2, DustDevilRules.take(2, DustDevilRules.MAX_PLAYERS));
        assertEquals(0, DustDevilRules.take(-1, DustDevilRules.MAX_PLAYERS));
        assertEquals(DustDevilRules.LIFT_SPEED, DustDevilRules.liftVelocity(0.0D, 0.5D), 1.0E-9);
        assertEquals(0.0D, DustDevilRules.liftVelocity(0.3D, DustDevilRules.MAX_LIFT_HEIGHT), 1.0E-9,
                "hovers at the cap, never higher");
        double[] s = DustDevilRules.swirl(DustDevilRules.LIFT_RADIUS, 0.0D);
        double speed = Math.hypot(s[0], s[1]);
        assertTrue(speed <= DustDevilRules.SWIRL_SPEED + DustDevilRules.PULL * DustDevilRules.LIFT_RADIUS + 1.0E-9);
        assertTrue(s[0] < 0.0D, "pulled toward the axis");
        double[] centred = DustDevilRules.swirl(0.0D, 0.0D);
        assertEquals(0.0D, centred[0]);
        assertEquals(0.0D, centred[1]);
    }

    @Test
    void throwIsOutwardAndBounded() {
        double[] v = DustDevilRules.throwVelocity(0.0D, -2.0D);
        assertEquals(0.0D, v[0], 1.0E-9);
        assertEquals(-DustDevilRules.THROW_SPEED, v[2], 1.0E-9);
        assertEquals(DustDevilRules.THROW_UP, v[1], 1.0E-9);
        double[] centred = DustDevilRules.throwVelocity(0.0D, 0.0D);
        assertEquals(DustDevilRules.THROW_SPEED, Math.hypot(centred[0], centred[2]), 1.0E-9);
    }

    @Test
    void deniedGriefingThrowsPlayersOnly() {
        FakeChecks denied = new FakeChecks();
        denied.mobGrief = false;
        boolean allowed = CreatureGriefing.decide(CreatureGriefing.Change.ITEM, denied);
        assertFalse(allowed);
        assertEquals(0, DustDevilRules.itemCap(allowed), "no item entity is moved");

        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false;
        assertEquals(0, DustDevilRules.itemCap(CreatureGriefing.decide(CreatureGriefing.Change.ITEM, keyOff)));

        assertEquals(DustDevilRules.MAX_ITEMS,
                DustDevilRules.itemCap(CreatureGriefing.decide(CreatureGriefing.Change.ITEM, new FakeChecks())));
    }
}
