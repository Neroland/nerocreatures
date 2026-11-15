package za.co.neroland.nerocreatures.item.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Stage 4 creature gear: each item's rule, cooldown floor and bounds. */
class GearRulesTest {

    @Test
    void cooldownsNeverDropBelowOneSecond() {
        assertEquals(GearRules.MIN_COOLDOWN, GearRules.cooldown(0));
        assertEquals(GearRules.MIN_COOLDOWN, GearRules.cooldown(-5));
        assertEquals(600, GearRules.cooldown(600));
    }

    // --- Mirror Shield ---

    @Test
    void mirrorShieldReflectsOnlyIncomingForeignProjectilesOffCooldown() {
        boolean incoming = GearRules.isIncoming(1.0D, 0.0D, 0.0D, 2.0D, 0.0D, 0.0D);
        assertTrue(incoming);
        assertFalse(GearRules.isIncoming(-1.0D, 0.0D, 0.0D, 2.0D, 0.0D, 0.0D), "moving away");
        assertFalse(GearRules.isIncoming(0.01D, 0.0D, 0.0D, 2.0D, 0.0D, 0.0D), "stuck or drifting");
        assertTrue(GearRules.mayReflect(true, false, false, true));
        assertFalse(GearRules.mayReflect(true, true, false, true), "one projectile per cooldown");
        assertFalse(GearRules.mayReflect(true, false, true, true), "never its holder's own shot");
        assertFalse(GearRules.mayReflect(false, false, false, true), "only while held");
    }

    // --- Lurker Cloak ---

    @Test
    void lurkerCloakSilencesUnlessSprintingOrDisabled() {
        assertTrue(GearRules.cloakSilences(true, true, false));
        assertFalse(GearRules.cloakSilences(true, true, true));
        assertFalse(GearRules.cloakSilences(true, false, false));
        assertFalse(GearRules.cloakSilences(false, true, false));
    }

    // --- Blink Charm ---

    @Test
    void blinkStopsShortOfWallsAndIsBounded() {
        List<Double> open = GearRules.blinkSteps(100.0D, 8);
        assertEquals(8.0D, open.get(0), 1.0E-9);
        assertEquals(GearRules.BLINK_MIN_DISTANCE, open.get(open.size() - 1), 1.0E-9);
        List<Double> wall = GearRules.blinkSteps(4.0D, 8);
        assertEquals(4.0D - GearRules.BLINK_WALL_MARGIN, wall.get(0), 1.0E-9);
        for (double d : wall) {
            assertTrue(d < 4.0D, "never at or past the wall");
        }
        assertTrue(GearRules.blinkSteps(1.5D, 8).isEmpty(), "no room = no blink, no cooldown spent");
        assertEquals(GearRules.BLINK_RANGE_MAX, GearRules.blinkSteps(1000.0D, 999).get(0), 1.0E-9);
        assertEquals(GearRules.BLINK_RANGE_MIN, GearRules.blinkSteps(1000.0D, 0).get(0), 1.0E-9);
    }

    @Test
    void blinkNeverIntoBlocksUnloadedChunksOrPastTheBorder() {
        assertTrue(GearRules.blinkSpotAllowed(true, true, true));
        assertFalse(GearRules.blinkSpotAllowed(false, true, true));
        assertFalse(GearRules.blinkSpotAllowed(true, false, true));
        assertFalse(GearRules.blinkSpotAllowed(true, true, false));
    }

    // --- Heat-gland Greaves ---

    @Test
    void greavesHoldForTheChargeThenRechargeAfterTheCooldown() {
        int max = GearRules.GREAVES_LAVA_TICKS;
        int charge = max;
        int supported = 0;
        boolean exhausted = false;
        for (int t = 0; t < max * 2; t++) {
            if (GearRules.greavesSupport(true, true, charge)) {
                supported++;
            }
            int next = GearRules.nextCharge(charge, true, exhausted, max);
            exhausted |= GearRules.greavesExhausted(charge, next);
            charge = next;
        }
        assertEquals(max, supported, "a few seconds of lava walking, never more");
        assertTrue(exhausted);
        assertEquals(0, GearRules.nextCharge(0, false, true, max), "no refill while cooling down");
        assertEquals(max, GearRules.nextCharge(0, false, false, max), "full again once off cooldown");
        assertEquals(GearRules.GREAVES_LAVA_TICKS_MAX, GearRules.nextCharge(0, false, false, 100_000));
        assertFalse(GearRules.greavesSupport(false, true, max));
    }

    // --- Graviton Boots ---

    @Test
    void gravitonBootsAreControlledAndBounded() {
        assertTrue(GearRules.bootsActive(true, false, false));
        assertFalse(GearRules.bootsActive(true, true, false), "sneak to fall normally");
        assertFalse(GearRules.bootsActive(true, false, true));
        assertEquals(-0.5D, GearRules.gravityModifier(50), 1.0E-9);
        assertEquals(GearRules.BOOTS_GRAVITY_PERCENT_MIN / 100.0D - 1.0D, GearRules.gravityModifier(0), 1.0E-9);
        assertEquals(GearRules.BOOTS_GRAVITY_PERCENT_MAX / 100.0D - 1.0D, GearRules.gravityModifier(500), 1.0E-9);
    }

    // --- Resonant Whistle ---

    @Test
    void whistleCallsOnlyOwnSameLevelIdleCreaturesInRangeNearestFirstAndCapped() {
        List<GearRules.WhistleCandidate> candidates = new java.util.ArrayList<>();
        candidates.add(new GearRules.WhistleCandidate(0, true, true, 400.0D, false));
        candidates.add(new GearRules.WhistleCandidate(1, false, true, 1.0D, false)); // other dimension
        candidates.add(new GearRules.WhistleCandidate(2, true, false, 1.0D, false)); // someone else's
        candidates.add(new GearRules.WhistleCandidate(3, true, true, 1.0D, true)); // sitting
        candidates.add(new GearRules.WhistleCandidate(4, true, true, 49.0D * 49.0D, false)); // out of range
        candidates.add(new GearRules.WhistleCandidate(5, true, true, 4.0D, false));
        assertEquals(List.of(5, 0), GearRules.whistlePicks(candidates, 48));

        List<GearRules.WhistleCandidate> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(new GearRules.WhistleCandidate(i, true, true, i, false));
        }
        assertEquals(GearRules.WHISTLE_MAX_CALLED, GearRules.whistlePicks(many, 48).size());
        assertTrue(GearRules.whistlePicks(List.of(new GearRules.WhistleCandidate(0, true, true,
                200.0D * 200.0D, false)), 100_000).isEmpty(), "range clamps to the max");
    }

    // --- Chitin Bulwark and Bloom Amulet ---

    @Test
    void bulwarkBracesOnlyAtLowHealthOncePerCooldown() {
        assertTrue(GearRules.bulwarkBraces(true, 8.0F, 20.0F, false));
        assertFalse(GearRules.bulwarkBraces(true, 9.0F, 20.0F, false));
        assertFalse(GearRules.bulwarkBraces(true, 8.0F, 20.0F, true));
        assertFalse(GearRules.bulwarkBraces(false, 8.0F, 20.0F, false));
        assertFalse(GearRules.bulwarkBraces(true, 0.0F, 20.0F, false), "never on a dead player");
    }

    @Test
    void amuletBloomsWhenHurtOncePerCooldownAndNeverStacks() {
        assertTrue(GearRules.amuletBlooms(true, 12.0F, 20.0F, false, false));
        assertFalse(GearRules.amuletBlooms(true, 13.0F, 20.0F, false, false));
        assertFalse(GearRules.amuletBlooms(true, 12.0F, 20.0F, true, false));
        assertFalse(GearRules.amuletBlooms(true, 12.0F, 20.0F, false, true));
    }

    @Test
    void everyGearKindHasItsDecisionId() {
        assertEquals(9, GearKind.values().length, "static_jar is the tenth, its own item");
        assertEquals("heat_gland_greaves", GearKind.HEAT_GLAND_GREAVES.id());
        assertTrue(GearKind.BLINK_CHARM.active());
        assertFalse(GearKind.MIRROR_SHIELD.active());
    }
}
