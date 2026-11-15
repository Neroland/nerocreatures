package za.co.neroland.nerocreatures.entity.greenxertz;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * The Bloom Sovereign's door into {@link BloomHost}'s forms (decision 001: her spore pods and her
 * sporelings are {@code bloom_host} in pod and sporeling form). It lives in this package only to
 * reach {@code becomeSporeling()} and the shared sporeling tally, so her adds count against the same
 * per-dimension sporeling cap as every Bloom Host's.
 *
 * <p>Owned by the Bloom Sovereign's boss code. The boss owns caps, life and removal of what this
 * creates. Server thread only.
 */
public final class SovereignBrood {

    private SovereignBrood() {
    }

    /**
     * A spore pod at a position: rooted, no AI (it is a target, not a fighter), {@code health} hit
     * points, no experience. Not yet added to the level.
     */
    @Nullable
    public static BloomHost pod(ServerLevel level, double x, double y, double z, double health) {
        if (!(ModEntities.BLOOM_HOST.get().create(level, EntitySpawnReason.MOB_SUMMONED) instanceof BloomHost pod)) {
            return null;
        }
        pod.becomePod();
        AttributeInstance max = pod.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
        }
        pod.setHealth((float) health);
        pod.setNoAi(true);
        pod.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        return pod;
    }

    /**
     * Adds a sporeling (short-lived, never saved, no drops) at a position, inside the shared
     * per-dimension sporeling cap. Returns the sporeling, or {@code null} if the cap is full or the
     * level refused it.
     */
    @Nullable
    public static BloomHost sporeling(ServerLevel level, double x, double y, double z) {
        if (!(ModEntities.BLOOM_HOST.get().create(level, EntitySpawnReason.MOB_SUMMONED) instanceof BloomHost add)) {
            return null;
        }
        String dimension = level.dimension().identifier().toString();
        if (!BloomHost.SPORELINGS.tryAdd(dimension, add.getId(), BloomRules.PER_DIMENSION,
                id -> level.getEntity(id) instanceof BloomHost host && host.isAlive() && host.isSporeling())) {
            return null;
        }
        add.becomeSporeling();
        add.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.addFreshEntity(add)) {
            BloomHost.SPORELINGS.remove(dimension, add.getId());
            return null;
        }
        return add;
    }
}
