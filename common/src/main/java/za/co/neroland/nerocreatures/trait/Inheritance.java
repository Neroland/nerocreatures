package za.co.neroland.nerocreatures.trait;

import net.minecraft.util.RandomSource;

import org.jetbrains.annotations.Nullable;

/**
 * How traits are rolled and inherited.
 *
 * <ul>
 *   <li><b>Fresh roll</b> (a wild spawn, or an existing save's creature on its first tick): two
 *       independent weighted rolls from the pool.</li>
 *   <li><b>Inheritance</b>: one random slot from each parent; each inherited slot then has the
 *       pool's {@code mutation_chance} of being replaced by a fresh weighted roll. A parent with no
 *       traits yet contributes a fresh roll for its slot.</li>
 * </ul>
 *
 * <p>Pure functions of the pool and the random source, so tests drive them with a seeded source.
 */
public final class Inheritance {

    private Inheritance() {
    }

    /** Two fresh weighted rolls. */
    public static TraitSet fresh(TraitPool pool, RandomSource random) {
        return TraitSet.of(pool.roll(random).id(), pool.roll(random).id());
    }

    /** One slot from each parent, then a per-slot mutation roll. */
    public static TraitSet inherit(TraitPool pool, @Nullable TraitSet first, @Nullable TraitSet second,
            RandomSource random) {
        String a = mutate(pool, pick(pool, first, random), random);
        String b = mutate(pool, pick(pool, second, random), random);
        return TraitSet.of(a, b);
    }

    private static String pick(TraitPool pool, @Nullable TraitSet parent, RandomSource random) {
        if (parent == null) {
            return pool.roll(random).id();
        }
        return random.nextBoolean() ? parent.a() : parent.b();
    }

    private static String mutate(TraitPool pool, String inherited, RandomSource random) {
        double chance = pool.mutationChance();
        if (chance > 0.0D && random.nextDouble() < chance) {
            return pool.roll(random).id();
        }
        return inherited;
    }
}
