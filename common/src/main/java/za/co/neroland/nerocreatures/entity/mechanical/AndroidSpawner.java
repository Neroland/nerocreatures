package za.co.neroland.nerocreatures.entity.mechanical;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;

/**
 * The structure-friendly way to place an android.
 *
 * <p>Androids are a ruins creature: their natural spawn rate is deliberately tiny, and the intended
 * source is a NeroRuins guardian post or a similar deliberate placement. Those callers want the mob
 * to be <b>there when the player arrives</b>, which means spawning it with
 * {@link EntitySpawnReason#STRUCTURE} so the shared base marks it persistent — a detail easy to get
 * wrong from the outside, and the reason this helper exists rather than the caller poking
 * {@code ModEntities} directly.
 *
 * <p>Population caps still apply: a placement that would exceed the per-chunk or per-dimension cap
 * returns {@code null} instead of quietly blowing past it.
 *
 * <p>Nothing here is player-scoped — no owner, no initiator, no identity is recorded (POPIA/GDPR).
 * Server-side only.
 */
public final class AndroidSpawner {

    private AndroidSpawner() {
    }

    /**
     * Places a heavy frame — the shielded guardian.
     *
     * @return the android, or {@code null} if a cap refused it or the type failed to instantiate
     */
    @Nullable
    public static RogueAndroid placeGuardian(ServerLevel level, BlockPos pos) {
        return place(level, pos, ModEntities.ROGUE_ANDROID.get());
    }

    /**
     * Places a drone — the cheap patrol unit.
     *
     * @return the drone, or {@code null} if a cap refused it or the type failed to instantiate
     */
    @Nullable
    public static RogueDrone placeDrone(ServerLevel level, BlockPos pos) {
        return place(level, pos, ModEntities.ROGUE_DRONE.get());
    }

    @Nullable
    private static <T extends AbstractAndroid> T place(ServerLevel level, BlockPos pos,
            EntityType<T> type) {
        if (!CreatureCensus.dimensionHasRoom(level) || !CreatureCensus.chunkHasRoom(level, pos)) {
            return null;
        }
        T android = type.create(level, EntitySpawnReason.STRUCTURE);
        if (android == null) {
            return null;
        }
        android.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        android.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                EntitySpawnReason.STRUCTURE, null);
        if (!level.addFreshEntity(android)) {
            return null;
        }
        CreatureCensus.invalidate(level);
        return android;
    }
}
