package za.co.neroland.nerocreatures.entity.tame;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.glacira.RimeYak;
import za.co.neroland.nerocreatures.entity.greenxertz.Mossback;
import za.co.neroland.nerocreatures.entity.luna.LodeTortoise;
import za.co.neroland.nerocreatures.entity.nyxara.PrismStag;

/** Stage 2 review M1: livestock a player keeps or bred never despawns and is never wild prey. */
class HusbandryRulesTest {

    /** Every livestock species, so each gets the husbandry flag from the shared root. */
    private static final List<Class<?>> SPECIES = List.of(LodeTortoise.class, RimeYak.class, Mossback.class,
            PrismStag.class);

    @Test
    void everyLivestockSpeciesInheritsTheHusbandryFlag() {
        for (Class<?> species : SPECIES) {
            assertTrue(LivestockCreature.class.isAssignableFrom(species), species.getSimpleName());
        }
    }

    @Test
    void husbandryInteractionsMarkTheAnimal() {
        assertTrue(HusbandryRules.marksOnInteraction(true, true), "feed, shear, milk, harvest");
        assertFalse(HusbandryRules.marksOnInteraction(true, false), "a refused click marks nothing");
        assertFalse(HusbandryRules.marksOnInteraction(false, true), "client prediction marks nothing");
        assertTrue(HusbandryRules.marksOnCheck(true, false), "leashed");
        assertTrue(HusbandryRules.marksOnCheck(false, true), "named");
        assertFalse(HusbandryRules.marksOnCheck(false, false));
    }

    @Test
    void babiesInheritTheMark() {
        assertTrue(HusbandryRules.offspringHusbanded(true, false));
        assertTrue(HusbandryRules.offspringHusbanded(false, true));
        assertTrue(HusbandryRules.offspringHusbanded(true, true));
        assertFalse(HusbandryRules.offspringHusbanded(false, false), "two wild parents: a wild baby");
    }

    @Test
    void husbandedAnimalsNeverDespawn() {
        assertTrue(HusbandryRules.mayDespawn(false, false, false), "a wild spawn may despawn");
        assertFalse(HusbandryRules.mayDespawn(false, false, true), "husbanded");
        assertFalse(HusbandryRules.mayDespawn(true, false, false), "persistent");
        assertFalse(HusbandryRules.mayDespawn(false, true, false), "tame");
    }

    @Test
    void husbandedAnimalsAreNeverWildPrey() {
        assertTrue(HusbandryRules.wildPrey(false, false, false, false));
        assertFalse(HusbandryRules.wildPrey(false, false, false, true), "husbanded");
        assertFalse(HusbandryRules.wildPrey(true, false, false, false), "named");
        assertFalse(HusbandryRules.wildPrey(false, true, false, false), "tame");
        assertFalse(HusbandryRules.wildPrey(false, false, true, false), "owned");
    }

    @Test
    void oldSavesWithAnOwnerOrANameAreHusbandedOnLoad() {
        assertTrue(HusbandryRules.husbandedOnLoad(true, false, false));
        assertTrue(HusbandryRules.husbandedOnLoad(false, true, false), "named, pre-flag save");
        assertTrue(HusbandryRules.husbandedOnLoad(false, false, true), "owned, pre-flag save");
        assertFalse(HusbandryRules.husbandedOnLoad(false, false, false), "a wild animal stays wild");
    }
}
