package za.co.neroland.nerocreatures.entity.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;

/**
 * The "kill it and it becomes two smaller ones" mechanic (the Plasma Slime pattern), as a static
 * helper rather than a goal — splitting happens on death, not on a schedule, so there is nothing
 * for a goal to tick.
 *
 * <h2>The budget is the whole point</h2>
 *
 * <p>An unbounded split mechanic is a server-killer: three tiers of a two-way split is eight mobs
 * from one, and a pack of five parents is forty. Every split here is bounded three times over:
 *
 * <ol>
 *   <li><b>Size tier.</b> A child is only produced while {@code parentSize > 1}; size 1 is terminal.
 *       That caps the depth of the tree regardless of how the caller is written.</li>
 *   <li><b>Per-call cap.</b> At most {@value #MAX_CHILDREN_PER_SPLIT} children per split.</li>
 *   <li><b>Population caps.</b> The remaining room in {@code maxCreaturesPerChunk} and
 *       {@code maxCreaturesPerDimension} is computed before spawning, and the child count is
 *       clipped to it. A full chunk yields zero children rather than blowing past the cap the
 *       spawn engine is carefully respecting.</li>
 * </ol>
 *
 * <p>Server-only. Calling this on a client level is a no-op.
 */
public final class SplitOnDamageBehaviour {

    /** Nothing may ever produce more than this many children in one split. */
    public static final int MAX_CHILDREN_PER_SPLIT = 4;

    /** How far around the parent children are scattered, in blocks. */
    private static final double SCATTER_RADIUS = 0.5D;

    private SplitOnDamageBehaviour() {
    }

    /**
     * Spawns the children of a splitting creature.
     *
     * @param level        the server level
     * @param parent       the creature that just died/split (used for position and spawn reason)
     * @param childType    the entity type to spawn — usually the same type as the parent
     * @param parentSize   the parent's size tier; a value of 1 or less produces no children
     * @param requested    how many children to attempt, before caps
     * @param initialiser  called for each child with its size tier ({@code parentSize - 1}) so the
     *                     caller can apply size-dependent attributes; may be {@code null}
     * @param <T>          the child entity type
     * @return the children actually spawned (possibly empty)
     */
    public static <T extends Mob> List<T> split(ServerLevel level, Mob parent, EntityType<T> childType,
            int parentSize, int requested, ObjIntConsumer<T> initialiser) {
        List<T> children = new ArrayList<>();
        if (parentSize <= 1 || requested <= 0) {
            return children;
        }
        int budget = childBudget(level, parent, requested);
        if (budget <= 0) {
            return children;
        }
        int childSize = parentSize - 1;
        for (int i = 0; i < budget; i++) {
            T child = childType.create(level, EntitySpawnReason.TRIGGERED);
            if (child == null) {
                break;
            }
            double offsetX = (level.getRandom().nextDouble() - 0.5D) * 2.0D * SCATTER_RADIUS;
            double offsetZ = (level.getRandom().nextDouble() - 0.5D) * 2.0D * SCATTER_RADIUS;
            child.snapTo(parent.getX() + offsetX, parent.getY() + 0.5D, parent.getZ() + offsetZ,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            if (initialiser != null) {
                initialiser.accept(child, childSize);
            }
            // Children are a consequence of a fight, not free population: they despawn like any
            // naturally spawned mob so a forgotten split does not linger in the caps forever.
            if (level.addFreshEntity(child)) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * How many children may actually be spawned here: the requested count, clipped by the per-split
     * cap and by the room left in the per-chunk and per-dimension population caps.
     */
    private static int childBudget(ServerLevel level, Mob parent, int requested) {
        int budget = Math.min(requested, MAX_CHILDREN_PER_SPLIT);

        int chunkCap = NeroCreaturesConfig.MAX_CREATURES_PER_CHUNK.get();
        int inChunk = CreatureCensus.countInChunk(level, parent.blockPosition());
        budget = Math.min(budget, chunkCap - inChunk);

        int dimensionCap = NeroCreaturesConfig.MAX_CREATURES_PER_DIMENSION.get();
        budget = Math.min(budget, dimensionCap - CreatureCensus.inDimension(level));

        return Math.max(0, budget);
    }
}
