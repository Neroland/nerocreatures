package za.co.neroland.nerocreatures.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

/** Decision 003: NBT schema {@code {v,a,b}}, version handling, unknown ids and egg custom data. */
class TraitSetTest {

    @Test
    void roundTripsThroughNbtWithVersion() {
        TraitSet traits = TraitSet.of("hardy", "radiant");
        CompoundTag tag = traits.toTag();
        CompoundTag expected = new CompoundTag();
        expected.putInt("v", 1);
        expected.putString("a", "hardy");
        expected.putString("b", "radiant");
        assertEquals(expected, tag);
        assertEquals(traits, TraitSet.fromTag(tag).orElseThrow());
    }

    @Test
    void missingVersionReadsAsOne() {
        CompoundTag tag = new CompoundTag();
        tag.putString("a", "swift");
        tag.putString("b", "calm");
        assertEquals(TraitSet.of("swift", "calm"), TraitSet.fromTag(tag).orElseThrow());
    }

    @Test
    void unknownIdsSurviveAndHaveNoEffect() {
        TraitSet traits = TraitSet.of("from_the_future", "keen");
        TraitSet back = TraitSet.fromTag(traits.toTag()).orElseThrow();
        assertEquals("from_the_future", back.a());
        assertEquals(1.25D, TraitEffects.abilityRangeMultiplier(back, TraitPool.defaults()), 1e-9);
        assertEquals(1.0D, TraitEffects.cooldownMultiplier(back, TraitPool.defaults()), 1e-9);
    }

    @Test
    void newerSchemaKeepsStringsButAppliesNothing() {
        TraitSet future = new TraitSet(TraitSet.CURRENT_VERSION + 1, "stout", "radiant");
        TraitSet back = TraitSet.fromTag(future.toTag()).orElseThrow();
        assertEquals(future, back);
        assertFalse(back.understood());
        assertFalse(back.isRadiant());
        assertTrue(back.effectiveIds().isEmpty());
        assertEquals(TraitEffects.BASE_CARRY_SLOTS, TraitEffects.carrySlots(back, TraitPool.defaults()));
    }

    @Test
    void duplicateAppliesOnce() {
        TraitSet twice = TraitSet.of("thrifty", "thrifty");
        assertEquals(Set.of("thrifty"), twice.effectiveIds());
        assertEquals(0.8D, TraitEffects.cooldownMultiplier(twice, TraitPool.defaults()), 1e-9);
    }

    @Test
    void eggCustomDataKeepsSpeciesKey() {
        CompoundTag custom = new CompoundTag();
        custom.putString("species", "nerocreatures:lumen_moth");
        TraitSet traits = TraitSet.of("bountiful", "radiant");
        traits.writeInto(custom);
        assertTrue(custom.contains("species"));
        assertEquals(4, custom.size());
        assertEquals(traits, TraitSet.readFrom(custom).orElseThrow());
    }

    @Test
    void malformedTagReadsEmpty() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", 1);
        assertTrue(TraitSet.fromTag(tag).isEmpty());
    }
}
