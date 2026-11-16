package za.co.neroland.nerocreatures.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

/** Inheritance: one slot from each parent, per-slot mutation, Radiant rarity, mutation off switch. */
class InheritanceTest {

    private static final int TRIALS = 20_000;

    @Test
    void takesOneSlotFromEachParentEvenly() {
        TraitPool noMutation = new TraitPool(1, 0.0D, TraitPool.defaults().traits());
        RandomSource random = RandomSource.create(42L);
        TraitSet mother = TraitSet.of("hardy", "swift");
        TraitSet father = TraitSet.of("keen", "calm");
        int hardy = 0;
        int keen = 0;
        for (int i = 0; i < TRIALS; i++) {
            TraitSet child = Inheritance.inherit(noMutation, mother, father, random);
            assertTrue(List.of("hardy", "swift").contains(child.a()), "slot a comes from the mother");
            assertTrue(List.of("keen", "calm").contains(child.b()), "slot b comes from the father");
            hardy += child.a().equals("hardy") ? 1 : 0;
            keen += child.b().equals("keen") ? 1 : 0;
        }
        assertEquals(0.5D, hardy / (double) TRIALS, 0.02D);
        assertEquals(0.5D, keen / (double) TRIALS, 0.02D);
    }

    @Test
    void mutatesAboutFivePercentOfSlots() {
        TraitPool pool = TraitPool.defaults();
        RandomSource random = RandomSource.create(7L);
        // Parents whose ids are not in the pool: any pool id in a slot can only come from mutation.
        TraitSet mother = TraitSet.of("m1", "m2");
        TraitSet father = TraitSet.of("f1", "f2");
        int mutated = 0;
        for (int i = 0; i < TRIALS; i++) {
            TraitSet child = Inheritance.inherit(pool, mother, father, random);
            mutated += pool.byId(child.a()) != null ? 1 : 0;
            mutated += pool.byId(child.b()) != null ? 1 : 0;
        }
        assertEquals(0.05D, mutated / (2.0D * TRIALS), 0.006D);
    }

    @Test
    void mutationOffNeverIntroducesNewTraits() {
        TraitPool off = new TraitPool(1, 0.0D, TraitPool.defaults().traits());
        RandomSource random = RandomSource.create(3L);
        for (int i = 0; i < TRIALS; i++) {
            TraitSet child = Inheritance.inherit(off, TraitSet.of("m1", "m1"), TraitSet.of("f1", "f1"), random);
            assertEquals(TraitSet.of("m1", "f1"), child);
        }
    }

    @Test
    void radiantIsAboutOnePercentPerFreshSlot() {
        TraitPool pool = TraitPool.defaults();
        RandomSource random = RandomSource.create(11L);
        int radiantSlots = 0;
        int radiantIndividuals = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            TraitSet traits = Inheritance.fresh(pool, random);
            radiantSlots += traits.a().equals(TraitPool.RADIANT) ? 1 : 0;
            radiantSlots += traits.b().equals(TraitPool.RADIANT) ? 1 : 0;
            radiantIndividuals += traits.isRadiant() ? 1 : 0;
        }
        assertEquals(7.0D / 707.0D, radiantSlots / (2.0D * n), 0.0015D);
        assertEquals(0.0197D, radiantIndividuals / (double) n, 0.003D);
    }

    @Test
    void twoRadiantParentsAlmostAlwaysGiveRadiant() {
        TraitPool pool = TraitPool.defaults();
        RandomSource random = RandomSource.create(5L);
        TraitSet radiant = TraitSet.of(TraitPool.RADIANT, TraitPool.RADIANT);
        int hits = 0;
        for (int i = 0; i < TRIALS; i++) {
            hits += Inheritance.inherit(pool, radiant, radiant, random).isRadiant() ? 1 : 0;
        }
        assertTrue(hits / (double) TRIALS > 0.95D);
    }

    @Test
    void parentWithoutTraitsContributesAFreshRoll() {
        TraitPool pool = TraitPool.defaults();
        TraitSet child = Inheritance.inherit(pool, null, TraitSet.of("stout", "stout"), RandomSource.create(1L));
        assertTrue(pool.byId(child.a()) != null);
    }

    @Test
    void seededRollsAreDeterministic() {
        TraitPool pool = TraitPool.defaults();
        assertEquals(Inheritance.fresh(pool, RandomSource.create(99L)), Inheritance.fresh(pool, RandomSource.create(99L)));
    }
}
