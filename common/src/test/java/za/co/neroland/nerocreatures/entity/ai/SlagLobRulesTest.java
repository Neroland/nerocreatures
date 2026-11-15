package za.co.neroland.nerocreatures.entity.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.SlagLobRules.Target;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

/** Magma Wader slag lob: range, telegraph, caps, placement rule and the denied fallback. */
class SlagLobRulesTest {

    @Test
    void lobsOnlyInRangeAfterATelegraph() {
        assertFalse(SlagLobRules.inRange(2.0D), "too close: melee instead");
        assertTrue(SlagLobRules.inRange(8.0D));
        assertFalse(SlagLobRules.inRange(20.0D));
        assertTrue(SlagLobRules.WINDUP_TICKS >= 10, "contract: telegraph of at least 10 ticks");
        assertTrue(SlagLobRules.POSE_TICKS < 60, "contract: timed poses under 3 s");
    }

    @Test
    void flightAndCooldownAreBounded() {
        assertEquals(SlagLobRules.MIN_FLIGHT_TICKS, SlagLobRules.flightTicks(0.0D));
        assertEquals(SlagLobRules.MAX_FLIGHT_TICKS, SlagLobRules.flightTicks(100.0D));
        assertEquals(SlagLobRules.COOLDOWN_MIN_TICKS, SlagLobRules.cooldown(0.0D));
        assertEquals(SlagLobRules.COOLDOWN_MAX_TICKS, SlagLobRules.cooldown(5.0D));
        assertEquals(0.0D, SlagLobRules.arcHeight(10.0D, 0.0D), 1e-9);
        assertEquals(0.0D, SlagLobRules.arcHeight(10.0D, 1.0D), 1e-9);
        assertTrue(SlagLobRules.arcHeight(10.0D, 0.5D) > 1.0D);
    }

    @Test
    void decayIsThirtyToSixtySeconds() {
        assertEquals(600, SlagLobRules.decayTicks(0.0D));
        assertEquals(1200, SlagLobRules.decayTicks(1.0D));
        assertEquals(1200, SlagLobRules.decayTicks(7.0D));
    }

    @Test
    void lobsAndCrustAreCapped() {
        assertTrue(SlagLobRules.mayLaunch(0));
        assertFalse(SlagLobRules.mayLaunch(SlagLobRules.MAX_IN_FLIGHT));
        assertEquals(SlagLobRules.MAX_CRUST_PER_LOB, SlagLobRules.crustBudget(true, 0));
        assertEquals(1, SlagLobRules.crustBudget(true, SlagLobRules.MAX_LIVE_CRUST - 1));
        assertEquals(0, SlagLobRules.crustBudget(true, SlagLobRules.MAX_LIVE_CRUST));
        assertEquals(0, SlagLobRules.crustBudget(true, 1000));
    }

    @Test
    void deniedGriefingPlacesNoCrust() {
        FakeChecks noGrief = new FakeChecks();
        noGrief.mobGrief = false;
        assertEquals(0, SlagLobRules.crustBudget(CreatureGriefing.decide(CreatureGriefing.Change.PLACE, noGrief), 0));
        FakeChecks claimed = new FakeChecks();
        claimed.place = false; // a claim mod cancelled the place event
        assertEquals(0, SlagLobRules.crustBudget(CreatureGriefing.decide(CreatureGriefing.Change.REPLACE, claimed), 0));
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false; // magmaWaderSlag = false
        assertEquals(0, SlagLobRules.crustBudget(CreatureGriefing.decide(CreatureGriefing.Change.PLACE, keyOff), 0));
    }

    @Test
    void crustGoesIntoAirOverGroundOrOverLavaAndNeverIntoAnEntity() {
        assertEquals(Target.AIR, SlagLobRules.classify(false, false, true, true, false));
        assertEquals(Target.LAVA, SlagLobRules.classify(true, false, true, false, false));
        assertEquals(Target.NONE, SlagLobRules.classify(true, true, true, false, false), "something in the lava");
        assertEquals(Target.NONE, SlagLobRules.classify(false, false, true, true, true), "a player stands there");
        assertEquals(Target.NONE, SlagLobRules.classify(false, false, true, false, false), "no floating crust");
        assertEquals(Target.NONE, SlagLobRules.classify(false, false, false, true, false), "never replaces a block");
    }
}
