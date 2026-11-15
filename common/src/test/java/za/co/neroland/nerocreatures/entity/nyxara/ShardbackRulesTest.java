package za.co.neroland.nerocreatures.entity.nyxara;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.nyxara.ShardbackRules.Phase;
import za.co.neroland.nerocreatures.entity.nyxara.ShardbackRules.ReflectBudget;

class ShardbackRulesTest {

    private static final double HALF = ShardbackRules.FRONT_HALF_ANGLE;

    @Test
    void frontArcFollowsTheBodyYaw() {
        // Yaw 0 faces +z; yaw 90 faces -x; yaw 180 faces -z; yaw -90 faces +x.
        assertTrue(ShardbackRules.inFrontArc(0.0D, 0.0D, 5.0D, HALF), "straight ahead");
        assertFalse(ShardbackRules.inFrontArc(0.0D, 0.0D, -5.0D, HALF), "straight behind");
        assertFalse(ShardbackRules.inFrontArc(0.0D, 5.0D, 0.0D, HALF), "flank at 90 degrees is outside 70");
        assertTrue(ShardbackRules.inFrontArc(90.0D, -5.0D, 0.0D, HALF));
        assertFalse(ShardbackRules.inFrontArc(90.0D, 5.0D, 0.0D, HALF));
        assertTrue(ShardbackRules.inFrontArc(180.0D, 0.0D, -5.0D, HALF));
        assertTrue(ShardbackRules.inFrontArc(-90.0D, 5.0D, 0.0D, HALF));
        assertTrue(ShardbackRules.inFrontArc(450.0D, -5.0D, 0.0D, HALF), "yaw wraps");
        assertTrue(ShardbackRules.inFrontArc(0.0D, 0.0D, 0.0D, HALF), "point blank hits the fan");
    }

    @Test
    void arcEdgeIsInclusiveAt70Degrees() {
        double in = Math.toRadians(69.0D);
        double out = Math.toRadians(71.0D);
        assertTrue(ShardbackRules.inFrontArc(0.0D, -Math.sin(in) * 4.0D, Math.cos(in) * 4.0D, HALF));
        assertFalse(ShardbackRules.inFrontArc(0.0D, -Math.sin(out) * 4.0D, Math.cos(out) * 4.0D, HALF));
        assertTrue(ShardbackRules.inFrontArc(0.0D, Math.sin(in) * 4.0D, Math.cos(in) * 4.0D, HALF), "both sides");
    }

    @Test
    void vulnerableOnlyFromBehindOrWhileCharging() {
        assertFalse(ShardbackRules.vulnerable(false, true, true), "front, fan up");
        assertTrue(ShardbackRules.vulnerable(false, true, false), "behind");
        assertTrue(ShardbackRules.vulnerable(true, true, true), "front while charging");
        assertTrue(ShardbackRules.vulnerable(false, false, true), "no source: falls, lava");
        assertTrue(ShardbackRules.reflects(false, true));
        assertFalse(ShardbackRules.reflects(false, false), "from behind it lands");
        assertFalse(ShardbackRules.reflects(true, true), "folded plates reflect nothing");
        assertFalse(ShardbackRules.exposed(Phase.ROAM));
        assertTrue(ShardbackRules.exposed(Phase.WINDUP));
        assertTrue(ShardbackRules.exposed(Phase.RUN));
        assertTrue(ShardbackRules.exposed(Phase.RECOVER));
    }

    @Test
    void chargeCycleRunsInOrderAndIsBounded() {
        assertEquals(Phase.ROAM, ShardbackRules.next(Phase.ROAM, 999, false, false));
        assertEquals(Phase.WINDUP, ShardbackRules.next(Phase.ROAM, 0, true, false));
        assertEquals(Phase.WINDUP, ShardbackRules.next(Phase.WINDUP, ShardbackRules.WINDUP_TICKS - 1, false, false));
        assertEquals(Phase.RUN, ShardbackRules.next(Phase.WINDUP, ShardbackRules.WINDUP_TICKS, false, false));
        assertEquals(Phase.RUN, ShardbackRules.next(Phase.RUN, 1, false, false));
        assertEquals(Phase.RECOVER, ShardbackRules.next(Phase.RUN, 1, false, true), "a hit or a wall ends it");
        assertEquals(Phase.RECOVER, ShardbackRules.next(Phase.RUN, ShardbackRules.RUN_MAX_TICKS, false, false));
        assertEquals(Phase.ROAM, ShardbackRules.next(Phase.RECOVER, ShardbackRules.RECOVER_TICKS, false, false));
        assertTrue(ShardbackRules.WINDUP_TICKS >= 10, "telegraph at least 10 ticks");
        assertTrue(ShardbackRules.CHARGE_POSE_TICKS < 60, "timed pose under 3 s");
        assertTrue(ShardbackRules.mayHit(0));
        assertFalse(ShardbackRules.mayHit(ShardbackRules.MAX_HITS_PER_CHARGE), "one hit per charge");
        assertTrue(ShardbackRules.inChargeRange(ShardbackRules.CHARGE_MIN_RANGE));
        assertFalse(ShardbackRules.inChargeRange(2.0D), "too close: melee instead");
        assertFalse(ShardbackRules.inChargeRange(40.0D));
        assertEquals(ShardbackRules.COOLDOWN_MIN_TICKS, ShardbackRules.cooldown(0.0F));
        assertEquals(ShardbackRules.COOLDOWN_MAX_TICKS, ShardbackRules.cooldown(1.0F));
        assertEquals(ShardbackRules.COOLDOWN_MIN_TICKS, ShardbackRules.cooldown(-5.0F));
    }

    @Test
    void reflectionsAreCappedPerWindow() {
        ReflectBudget budget = new ReflectBudget();
        for (int i = 0; i < ShardbackRules.MAX_REFLECTS_PER_WINDOW; i++) {
            assertTrue(budget.tryUse(100L));
        }
        assertFalse(budget.tryUse(100L), "cap reached");
        assertFalse(budget.tryUse(100L + ShardbackRules.REFLECT_WINDOW_TICKS - 1), "same window");
        assertTrue(budget.tryUse(100L + ShardbackRules.REFLECT_WINDOW_TICKS), "next window");
        assertTrue(budget.tryUse(5L), "a clock that went backwards resets the window");
    }
}
