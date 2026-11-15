package za.co.neroland.nerocreatures.entity.humanoid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

/** Existing-12 polish: Space Pirate contraband pick-up, its cap and its griefing gate. */
class ContrabandRulesTest {

    @Test
    void carriesAtMostOneCappedSlot() {
        assertEquals(16, ContrabandRules.takeAmount(0, 64, ContrabandRules.CARRY_LIMIT));
        assertEquals(6, ContrabandRules.takeAmount(10, 64, ContrabandRules.CARRY_LIMIT));
        assertEquals(0, ContrabandRules.takeAmount(16, 3, ContrabandRules.CARRY_LIMIT));
        assertFalse(ContrabandRules.mayLook(true, false, ContrabandRules.CARRY_LIMIT), "full slot stops looking");
    }

    @Test
    void neverLooksWhileFightingOrUnconfigured() {
        assertTrue(ContrabandRules.mayLook(true, false, 0));
        assertFalse(ContrabandRules.mayLook(true, true, 0));
        assertFalse(ContrabandRules.mayLook(false, false, 0));
    }

    @Test
    void deniedGriefingTakesNothing() {
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false;
        boolean allowed = CreatureGriefing.decide(CreatureGriefing.Change.ITEM, keyOff);
        assertFalse(allowed);
        assertFalse(ContrabandRules.mayGrab(allowed, false, 0, 5));

        FakeChecks noMobGriefing = new FakeChecks();
        noMobGriefing.mobGrief = false;
        assertFalse(ContrabandRules.mayGrab(CreatureGriefing.decide(CreatureGriefing.Change.ITEM, noMobGriefing),
                false, 0, 5));

        assertTrue(ContrabandRules.mayGrab(CreatureGriefing.decide(CreatureGriefing.Change.ITEM, new FakeChecks()),
                false, 0, 5));
        assertFalse(ContrabandRules.mayGrab(true, true, 0, 5), "respects the pick-up delay");
    }
}
