package za.co.neroland.nerocreatures.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

/** The pure effect queries the mount, ability and product systems read. */
class TraitEffectsTest {

    private final TraitPool pool = TraitPool.defaults();

    @Test
    void neutralWithoutTraits() {
        assertEquals(1.0D, TraitEffects.abilityRangeMultiplier(null, this.pool));
        assertEquals(1.0D, TraitEffects.cooldownMultiplier(null, this.pool));
        assertEquals(1.0D, TraitEffects.panicMultiplier(null, this.pool));
        assertFalse(TraitEffects.neverBuck(null, this.pool));
        assertEquals(0.0D, TraitEffects.extraProductChance(null, this.pool));
        assertEquals(9, TraitEffects.carrySlots(null, this.pool));
    }

    @Test
    void eachTraitDrivesItsHook() {
        assertEquals(1.25D, TraitEffects.abilityRangeMultiplier(TraitSet.of("keen", "hardy"), this.pool), 1e-9);
        assertEquals(0.8D, TraitEffects.cooldownMultiplier(TraitSet.of("thrifty", "hardy"), this.pool), 1e-9);
        assertEquals(0.5D, TraitEffects.panicMultiplier(TraitSet.of("calm", "hardy"), this.pool), 1e-9);
        assertTrue(TraitEffects.neverBuck(TraitSet.of("calm", "hardy"), this.pool));
        assertEquals(0.5D, TraitEffects.extraProductChance(TraitSet.of("bountiful", "hardy"), this.pool), 1e-9);
        assertEquals(18, TraitEffects.carrySlots(TraitSet.of("stout", "hardy"), this.pool));
        assertTrue(TraitSet.of("swift", "radiant").isRadiant());
    }

    @Test
    void bountifulGivesAnExtraRollAboutHalfTheTime() {
        RandomSource random = RandomSource.create(17L);
        TraitSet bountiful = TraitSet.of("bountiful", "calm");
        int rolls = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            rolls += TraitEffects.productRolls(bountiful, this.pool, random);
        }
        assertEquals(1.5D, rolls / (double) n, 0.02D);
        assertEquals(1, TraitEffects.productRolls(TraitSet.of("calm", "keen"), this.pool, random));
    }
}
