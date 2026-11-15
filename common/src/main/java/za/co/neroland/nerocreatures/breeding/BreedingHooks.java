package za.co.neroland.nerocreatures.breeding;

import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.trait.TraitSet;

/**
 * Seams the breeding path calls into, bound by later stages so this package never depends on items,
 * blocks or registries it does not own.
 *
 * <p><b>Egg layer</b> (Stage 4, decision 003): for an {@link BirthMode#EGG} species, breeding asks the
 * bound {@link EggLayer} to drop a {@code <species>_egg} carrying the rolled traits in
 * {@code minecraft:custom_data} ({@code {species, v, a, b}}). Until one is bound, or if it returns
 * {@code false}, the species falls back to live birth, so nothing is lost before Stage 4 lands.
 */
public final class BreedingHooks {

    /** Drops an egg for a breeding pair. Return {@code true} when an egg was actually produced. */
    @FunctionalInterface
    public interface EggLayer {
        boolean layEgg(ServerLevel level, TameableCreature mother, TameableCreature father, TraitSet traits);
    }

    @Nullable
    private static volatile EggLayer eggLayer;

    private BreedingHooks() {
    }

    public static void bindEggLayer(@Nullable EggLayer layer) {
        eggLayer = layer;
    }

    @Nullable
    public static EggLayer eggLayer() {
        return eggLayer;
    }
}
