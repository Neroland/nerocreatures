package za.co.neroland.nerocreatures.entity.tame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.shared.DigestState;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

/** Slag Beetle: smelting output and its cap, the eating gate, the denied case, the crab claim. */
class SlagBeetleRulesTest {

    @Test
    void outputIsTheRecipeCountPlusBonusHits() {
        assertEquals(1, SlagBeetleRules.outputCount(1, 0));
        assertEquals(2, SlagBeetleRules.outputCount(1, 1), "one bonus hit adds one");
        assertEquals(3, SlagBeetleRules.outputCount(1, 2), "Bountiful's second roll adds one more, never doubles");
        assertEquals(0, SlagBeetleRules.outputCount(0, 2), "unsmeltable produces nothing");
        assertEquals(1, SlagBeetleRules.bonusRolls(1));
        assertEquals(2, SlagBeetleRules.bonusRolls(2), "Bountiful: one extra roll");
        assertEquals(2, SlagBeetleRules.bonusRolls(99), "rolls clamp to 2");
        assertEquals(1, SlagBeetleRules.bonusRolls(-5), "rolls clamp to at least 1");
    }

    @Test
    void outputIsCapped() {
        assertEquals(3, SlagBeetleRules.maxOutput(1));
        assertEquals(3, SlagBeetleRules.outputCount(1, 99), "hits clamp to 2");
        assertEquals(64, SlagBeetleRules.outputCount(63, 2), "never more than a stack");
        assertEquals(1, SlagBeetleRules.outputCount(1, -5), "hits clamp to at least 0");
    }

    @Test
    void expectedOutputStaysWithinAQuarterOfAFurnace() {
        // Bountiful used to multiply (about 1.6 per item); the bound is 1.25x a furnace.
        for (int base = 1; base <= 4; base++) {
            for (int rolls = 1; rolls <= 2; rolls++) {
                double ratio = SlagBeetleRules.expectedOutput(base, rolls) / base;
                assertTrue(ratio <= SlagBeetleRules.MAX_EXPECTED_RATIO, "base " + base + " rolls " + rolls + ": " + ratio);
            }
        }
        assertEquals(1.2D, SlagBeetleRules.expectedOutput(1, 2), 1e-9, "worst case: a Bountiful roll every time");
        assertEquals(0.0D, SlagBeetleRules.expectedOutput(0, 2));
    }

    @Test
    void foodTagIsRawOreItemsOnly() throws java.io.IOException {
        try (java.io.InputStream in = SlagBeetleRulesTest.class.getResourceAsStream(
                "/data/nerocreatures/tags/item/slag_beetle_ores.json")) {
            assertTrue(in != null, "tag on the classpath");
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(json.contains("\"#c:ores\""), "no ore blocks via #c:ores");
            assertFalse(json.contains("_ores\""), "no ore-block tags");
            assertTrue(json.contains("minecraft:raw_iron"));
        }
    }

    @Test
    void eatsOnlyOwnerThrownFoodWhenTamedAndAllowed() {
        assertTrue(SlagBeetleRules.mayEat(true, true, true, true));
        assertFalse(SlagBeetleRules.mayEat(false, true, true, true), "wild beetles never eat");
        assertFalse(SlagBeetleRules.mayEat(true, false, true, true), "someone else's item");
        assertFalse(SlagBeetleRules.mayEat(true, true, false, true), "not ore");
    }

    @Test
    void deniedGriefingMeansTheBeetleIgnoresTheItem() {
        FakeChecks denied = new FakeChecks();
        denied.owner = false; // e.g. the owner cannot build here
        assertFalse(SlagBeetleRules.mayEat(true, true, true, CreatureGriefing.decide(CreatureGriefing.Change.ITEM, denied)));
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false; // slagBeetleSmelting = false
        assertFalse(SlagBeetleRules.mayEat(true, true, true, CreatureGriefing.decide(CreatureGriefing.Change.ITEM, keyOff)));
        assertTrue(SlagBeetleRules.mayEat(true, true, true, CreatureGriefing.decide(CreatureGriefing.Change.ITEM, new FakeChecks())));
    }

    @Test
    void stomachCapsAndDigestsInFurnaceTime() {
        DigestState stomach = new DigestState(SlagBeetleRules.STOMACH_CAPACITY, SlagBeetleRules.DIGEST_TICKS);
        for (int i = 0; i < SlagBeetleRules.STOMACH_CAPACITY; i++) {
            assertTrue(stomach.eat("minecraft:raw_iron", 0));
        }
        assertFalse(stomach.eat("minecraft:raw_iron", 0), "full stomach eats nothing");
        assertTrue(stomach.pollReady(SlagBeetleRules.DIGEST_TICKS - 1).isEmpty());
        assertEquals(1, stomach.pollReady(SlagBeetleRules.DIGEST_TICKS).size(), "one item per digestion");
        DigestState reloaded = new DigestState(SlagBeetleRules.STOMACH_CAPACITY, SlagBeetleRules.DIGEST_TICKS);
        reloaded.decode(stomach.encode());
        assertEquals(stomach.size(), reloaded.size(), "digestion state persists");
    }

    @Test
    void crabClaimReservesFreshItemsAndFoodNearABeetle() {
        assertTrue(SlagBeetleRules.reserved(0, false, false), "any item younger than 2 s");
        assertTrue(SlagBeetleRules.reserved(SlagBeetleRules.FRESH_ITEM_TICKS - 1, false, false));
        assertFalse(SlagBeetleRules.reserved(SlagBeetleRules.FRESH_ITEM_TICKS, false, true), "not food");
        assertTrue(SlagBeetleRules.reserved(600, true, true), "ore near a tamed beetle");
        assertFalse(SlagBeetleRules.reserved(600, true, false), "ore with no beetle nearby");
    }
}
