package za.co.neroland.nerocreatures.entity.asteroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

/** Magnet Crab: shell cap and plates, the steal gate, the magnet gate and rate cap, the denied cases. */
class MagnetCrabRulesTest {

    @Test
    void shellIsCappedPerSlotAndInSlots() {
        assertEquals(16, MagnetCrabRules.takeCount(64, 0), "a stack of 64 gives 16");
        assertEquals(5, MagnetCrabRules.takeCount(5, 2));
        assertEquals(0, MagnetCrabRules.takeCount(5, MagnetCrabRules.SHELL_SLOTS), "full shell takes nothing");
        assertEquals(0, MagnetCrabRules.takeCount(0, 0));
    }

    @Test
    void platesFollowStoredItemCount() {
        assertEquals(0, MagnetCrabRules.stage(0));
        assertEquals(1, MagnetCrabRules.stage(1));
        assertEquals(1, MagnetCrabRules.stage(16));
        assertEquals(2, MagnetCrabRules.stage(17));
        assertEquals(3, MagnetCrabRules.stage(48));
        assertEquals(3, MagnetCrabRules.stage(999), "never more than three plates");
    }

    @Test
    void wildStealsOnlyPlainMetalThatIsUnclaimed() {
        assertTrue(MagnetCrabRules.maySteal(false, 0, true, true, false, true));
        assertFalse(MagnetCrabRules.maySteal(true, 0, true, true, false, true), "tamed crabs never steal");
        assertFalse(MagnetCrabRules.maySteal(false, 3, true, true, false, true), "full shell");
        assertFalse(MagnetCrabRules.maySteal(false, 0, false, true, false, true), "not metal");
        assertFalse(MagnetCrabRules.maySteal(false, 0, true, false, false, true), "stack with components");
        assertFalse(MagnetCrabRules.maySteal(false, 0, true, true, true, true), "beetle claim");
    }

    @Test
    void deniedGriefingMeansTheWildCrabIgnoresTheItem() {
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false; // magnetCrabSteals = false
        assertFalse(MagnetCrabRules.maySteal(false, 0, true, true, false,
                CreatureGriefing.decide(CreatureGriefing.Change.ITEM, keyOff)));
        FakeChecks noGrief = new FakeChecks();
        noGrief.mobGrief = false;
        assertFalse(MagnetCrabRules.maySteal(false, 0, true, true, false,
                CreatureGriefing.decide(CreatureGriefing.Change.ITEM, noGrief)));
    }

    @Test
    void magnetOnlyMovesFreeUnclaimedItemsWhenAllowed() {
        assertTrue(MagnetCrabRules.mayCollect(true, false, true, true));
        assertFalse(MagnetCrabRules.mayCollect(false, false, true, true), "wild crabs are no magnet");
        assertFalse(MagnetCrabRules.mayCollect(true, true, true, true), "Slag Beetle claim always wins");
        assertFalse(MagnetCrabRules.mayCollect(true, false, false, true), "someone else's item");
    }

    @Test
    void deniedGriefingMeansTheMagnetMovesNothing() {
        FakeChecks ownerDenied = new FakeChecks();
        ownerDenied.owner = false; // the owner may not build where the item lies (claim)
        assertFalse(MagnetCrabRules.mayCollect(true, false, true,
                CreatureGriefing.decide(CreatureGriefing.Change.ITEM, ownerDenied)));
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false; // magnetCrabCollects = false
        assertFalse(MagnetCrabRules.mayCollect(true, false, true,
                CreatureGriefing.decide(CreatureGriefing.Change.ITEM, keyOff)));
        assertTrue(MagnetCrabRules.mayCollect(true, false, true,
                CreatureGriefing.decide(CreatureGriefing.Change.ITEM, new FakeChecks())));
    }

    @Test
    void throwerMustBeTheOwnerOrAbsentAndNoTarget() {
        assertTrue(MagnetCrabRules.claimFree(false, false, false), "mined drop, no thrower");
        assertTrue(MagnetCrabRules.claimFree(true, true, false), "the owner's own drop");
        assertFalse(MagnetCrabRules.claimFree(false, true, false), "another player's drop, even offline");
        assertFalse(MagnetCrabRules.claimFree(true, true, true), "owner-only pick-up target");
        assertFalse(MagnetCrabRules.claimFree(false, false, true));
    }

    @Test
    void rateAndRangeAreCapped() {
        assertTrue(MagnetCrabRules.underRate(0));
        assertFalse(MagnetCrabRules.underRate(MagnetCrabRules.ITEMS_PER_PULSE), "one item per pulse");
        double edge = MagnetCrabRules.DELIVERY_RANGE * MagnetCrabRules.DELIVERY_RANGE;
        assertTrue(MagnetCrabRules.ownerInRange(true, edge));
        assertFalse(MagnetCrabRules.ownerInRange(true, edge + 1.0D), "owner too far");
        assertFalse(MagnetCrabRules.ownerInRange(false, 0.0D), "owner offline");
        assertTrue(MagnetCrabRules.stillPulling(100, 100 - MagnetCrabRules.PULL_LINGER_TICKS + 1));
        assertFalse(MagnetCrabRules.stillPulling(100, 100 - MagnetCrabRules.PULL_LINGER_TICKS));
    }

    @Test
    void shellRoundTripsAndIsCappedOnLoad() {
        String encoded = MagnetCrabRules.encode(List.of("minecraft:iron_ingot", "minecraft:raw_gold"), List.of(16, 3));
        assertEquals("minecraft:iron_ingot*16;minecraft:raw_gold*3", encoded);
        List<MagnetCrabRules.Slot> back = MagnetCrabRules.decode(encoded);
        assertEquals(2, back.size());
        assertEquals(new MagnetCrabRules.Slot("minecraft:raw_gold", 3), back.get(1));
        assertEquals(3, MagnetCrabRules.decode("a:b*1;a:c*1;a:d*1;a:e*1").size(), "at most three slots");
        assertEquals(1, MagnetCrabRules.decode("bad;a:b*x;*3;a:b*2").size(), "malformed slots skipped");
        assertTrue(MagnetCrabRules.decode("").isEmpty());
    }
}
