package za.co.neroland.nerocreatures.entity.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.boss.FacetRules.Hit;

class FacetRulesTest {

    /** Every decoy pair the server can roll survives the form/stage sync, in range. */
    @Test
    void decoyMaskRoundTripsThroughFormAndStage() {
        for (int vulnerable = 0; vulnerable < FacetRules.FACETS; vulnerable++) {
            for (int roll = 0; roll < 20; roll++) {
                int mask = FacetRules.decoyMask(vulnerable, roll);
                for (boolean beaming : new boolean[] {false, true}) {
                    int shown = FacetRules.visualDecoys(beaming, vulnerable, mask);
                    assertEquals(2, Integer.bitCount(shown));
                    int form = FacetRules.decoyForm(shown);
                    int stage = FacetRules.decoyStage(shown);
                    assertTrue(form >= 1 && form <= FacetRules.FACETS, "form " + form);
                    assertTrue(stage >= 0 && stage <= 2, "stage " + stage);
                    assertEquals(shown, FacetRules.decoyMaskOf(form, stage));
                    // The lit plate (variant) is never shown as a decoy.
                    int lit = FacetRules.visualVariant(beaming, vulnerable);
                    assertEquals(0, shown & (1 << lit));
                }
            }
        }
        assertEquals(0, FacetRules.decoyForm(0));
        assertEquals(0, FacetRules.decoyStage(0));
        assertEquals(0, FacetRules.decoyMaskOf(0, 0));
        // Gap of 3 picks the lower facet; gap 5 wraps (facets 0 and 5 are neighbours).
        assertEquals(1, FacetRules.decoyForm((1 << 0) | (1 << 3)));
        assertEquals(2, FacetRules.decoyStage((1 << 0) | (1 << 3)));
        assertEquals(6, FacetRules.decoyForm((1 << 0) | (1 << 5)));
        assertEquals(0, FacetRules.decoyStage((1 << 0) | (1 << 5)));
    }

    @Test
    void rotationAlwaysMovesToAnotherFacetInRange() {
        for (int current = 0; current < FacetRules.FACETS; current++) {
            boolean[] reached = new boolean[FacetRules.FACETS];
            for (int roll = -50; roll < 50; roll++) {
                int next = FacetRules.nextFacet(current, roll);
                assertTrue(next >= 0 && next < FacetRules.FACETS);
                assertNotEquals(current, next, "never the same facet twice in a row");
                reached[next] = true;
            }
            for (int i = 0; i < FacetRules.FACETS; i++) {
                assertEquals(i != current, reached[i], "every other facet is reachable");
            }
        }
    }

    @Test
    void rotationGetsFasterByPhase() {
        assertTrue(FacetRules.rotationTicks(1) > FacetRules.rotationTicks(2));
        assertTrue(FacetRules.rotationTicks(2) > FacetRules.rotationTicks(3));
    }

    @Test
    void exactlyOneFacetIsVulnerableAtAnyAngle() {
        double ring = 17.0D;
        double body = 33.0D;
        for (int vulnerable = 0; vulnerable < FacetRules.FACETS; vulnerable++) {
            int hits = 0;
            for (int deg = 0; deg < 360; deg += 2) {
                Hit hit = FacetRules.classify(true, deg, body, ring, vulnerable, 0, false);
                if (hit == Hit.VULNERABLE) {
                    hits++;
                    assertTrue(FacetRules.angleBetween(deg, FacetRules.facetYaw(body, ring, vulnerable))
                            <= FacetRules.HIT_HALF_ANGLE);
                }
            }
            // A 80-degree arc sampled every 2 degrees.
            assertEquals(41, hits);
        }
    }

    @Test
    void armourBlocksEverythingButTheLitFacet() {
        double yaw = FacetRules.facetYaw(0.0D, 0.0D, 3);
        assertEquals(Hit.VULNERABLE, FacetRules.classify(true, yaw, 0.0D, 0.0D, 3, 0, false));
        assertEquals(Hit.ARMOURED, FacetRules.classify(true, yaw, 0.0D, 0.0D, 0, 0, false));
        assertEquals(0.0F, FacetRules.damageFactor(Hit.ARMOURED));
        assertEquals(1.0F, FacetRules.damageFactor(Hit.VULNERABLE));
        assertEquals(Hit.UNSOURCED, FacetRules.classify(false, 0.0D, 0.0D, 0.0D, 0, 0, false));
        assertEquals(0.0F, FacetRules.damageFactor(Hit.UNSOURCED), "lava and fire skip the facets, so they do nothing");
    }

    @Test
    void attackYawMatchesMinecraftYawConvention() {
        assertEquals(0.0D, FacetRules.attackYaw(0.0D, 5.0D), 1.0E-9, "+z is yaw 0");
        assertEquals(90.0D, FacetRules.attackYaw(-5.0D, 0.0D), 1.0E-9, "-x is yaw 90");
        assertEquals(180.0D, FacetRules.attackYaw(0.0D, -5.0D), 1.0E-9);
        assertEquals(270.0D, FacetRules.attackYaw(5.0D, 0.0D), 1.0E-9);
    }

    @Test
    void decoysAreTwoOtherFacetsAndOnlyFromPhaseTwo() {
        for (int v = 0; v < FacetRules.FACETS; v++) {
            for (int roll = 0; roll < 40; roll++) {
                int mask = FacetRules.decoyMask(v, roll);
                assertEquals(2, Integer.bitCount(mask));
                assertEquals(0, mask & (1 << v), "the vulnerable facet is never a decoy");
                assertTrue(mask < (1 << FacetRules.FACETS));
            }
        }
        assertTrue(!FacetRules.decoysActive(1) && FacetRules.decoysActive(2) && FacetRules.decoysActive(3));
        int mask = FacetRules.decoyMask(0, 0);
        int decoy = Integer.numberOfTrailingZeros(mask);
        double yaw = FacetRules.facetYaw(0.0D, 0.0D, decoy);
        assertEquals(Hit.DECOY, FacetRules.classify(true, yaw, 0.0D, 0.0D, 0, mask, false));
        assertEquals(0.0F, FacetRules.damageFactor(Hit.DECOY));
    }

    @Test
    void coreIsExposedOnlyInPhaseThreeAtHalfDamage() {
        assertEquals(0, FacetRules.exposedTicks(1));
        assertEquals(0, FacetRules.exposedTicks(2));
        assertTrue(FacetRules.exposedTicks(3) > 0);
        double armour = FacetRules.facetYaw(0.0D, 0.0D, 2);
        assertEquals(Hit.CORE, FacetRules.classify(true, armour, 0.0D, 0.0D, 0, 0, true));
        assertEquals(0.5F, FacetRules.damageFactor(Hit.CORE));
    }

    @Test
    void beamTurnsTheVulnerableFacetToTheFront() {
        for (int v = 0; v < FacetRules.FACETS; v++) {
            double ring = FacetRules.beamRingAngle(v);
            assertEquals(0.0D, FacetRules.angleBetween(FacetRules.facetYaw(40.0D, ring, v), 40.0D), 1.0E-9);
            assertEquals(Hit.VULNERABLE, FacetRules.classify(true, 40.0D, 40.0D, ring, v, 0, false));
        }
        assertEquals(0, FacetRules.visualVariant(true, 4), "beam draws variant 0");
        assertEquals(4, FacetRules.visualVariant(false, 4), "lit facet 0-5 otherwise");
    }

    @Test
    void ringAngleIsDeterministicAndPeriodic() {
        for (int phase = 1; phase <= 3; phase++) {
            double a = FacetRules.ringAngle(1234L, phase);
            assertEquals(a, FacetRules.ringAngle(1234L + FacetRules.RING_PERIOD_TICKS * 7L, phase), 1.0E-9);
            assertTrue(a >= 0.0D && a < 360.0D);
        }
        assertEquals(0.0D, FacetRules.ringAngle(Long.MAX_VALUE - (Long.MAX_VALUE % 4800L), 2), 1.0E-9);
    }
}
