package za.co.neroland.nerocreatures.breeding;

import net.minecraft.server.level.ServerLevel;

/**
 * Hook for a creature with a timed product (milk, nodule, antler, barnacle, ...). The shared
 * product-timer goal ticks the countdown; this interface only says how often and what happens.
 * {@code TameableCreature} implements it with the species' {@code product_interval_ticks} from
 * {@code species.json} and a no-op {@link #produce}; species override {@link #produce}.
 */
public interface ProductSource {

    /** Ticks between products; 0 means this creature has no product timer. */
    int productIntervalTicks();

    /** Whether a product may be produced now (adult, alive, not a gallery display). */
    boolean canProduce();

    /**
     * Produces the product, server side. {@code rolls} is 1, or 2 on a Bountiful extra roll (see
     * {@code TraitEffects.productRolls}).
     */
    void produce(ServerLevel level, int rolls);
}
