package za.co.neroland.nerocreatures.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * Population accounting for the spawn caps — "how many NeroCreatures are alive here right now".
 *
 * <h2>Cost</h2>
 *
 * <p>Both counts are plain vanilla entity queries. Nothing here hooks entity add/remove, because
 * those hooks are loader-specific in 26.x and this mod's {@code common/} module must compile
 * against raw vanilla.
 *
 * <ul>
 *   <li><b>Per dimension</b> — a whole-level query, but <em>bounded</em>: it stops as soon as
 *       {@code cap + 1} matches have been found, so a runaway population costs no more than a
 *       healthy one. The result is cached for {@value #CACHE_TTL_TICKS} ticks per dimension, and
 *       the spawn sweep only runs every 40 ticks, so in practice this is roughly one query per
 *       dimension per sweep.</li>
 *   <li><b>Per chunk</b> — an AABB query bounded to a single chunk column, so it walks that one
 *       chunk's entity sections and nothing else. Only ever run at placement time.</li>
 * </ul>
 *
 * <p>Never call either from a per-tick loop. All counts are per {@link ServerLevel}; client levels
 * are never counted, since the client has no say in spawning.
 *
 * <p>No player data of any kind passes through here (POPIA/GDPR).
 */
public final class CreatureCensus {

    /** How long a per-dimension count stays fresh, in ticks. */
    private static final int CACHE_TTL_TICKS = 20;

    private record Snapshot(long gameTime, int count) {
    }

    private static final Map<ResourceKey<Level>, Snapshot> PER_DIMENSION = new ConcurrentHashMap<>();

    private CreatureCensus() {
    }

    /**
     * How many NeroCreatures are currently loaded in this dimension, counted up to
     * {@code maxCreaturesPerDimension + 1} and cached for {@value #CACHE_TTL_TICKS} ticks. The
     * count is therefore accurate up to the cap and deliberately says nothing beyond it — the only
     * question anyone asks of it is "is there room?".
     */
    public static int inDimension(ServerLevel level) {
        int cap = NeroCreaturesConfig.MAX_CREATURES_PER_DIMENSION.get();
        long now = level.getGameTime();
        Snapshot cached = PER_DIMENSION.get(level.dimension());
        if (cached != null && now >= cached.gameTime() && now - cached.gameTime() < CACHE_TTL_TICKS) {
            return cached.count();
        }
        List<NeroCreatureEntity> found = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(NeroCreatureEntity.class), entity -> true, found, cap + 1);
        PER_DIMENSION.put(level.dimension(), new Snapshot(now, found.size()));
        return found.size();
    }

    /**
     * Whether the dimension is below {@code maxCreaturesPerDimension}. A cap of 0 means
     * "no NeroCreatures at all here".
     */
    public static boolean dimensionHasRoom(ServerLevel level) {
        int cap = NeroCreaturesConfig.MAX_CREATURES_PER_DIMENSION.get();
        return cap > 0 && inDimension(level) < cap;
    }

    /**
     * Whether the chunk containing {@code pos} is below {@code maxCreaturesPerChunk}. A cap of 0
     * means "no NeroCreatures in any chunk".
     */
    public static boolean chunkHasRoom(ServerLevel level, BlockPos pos) {
        int cap = NeroCreaturesConfig.MAX_CREATURES_PER_CHUNK.get();
        return cap > 0 && countInChunk(level, pos) < cap;
    }

    /** Counts loaded NeroCreatures in the chunk column containing {@code pos}. */
    public static int countInChunk(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(pos);
        int minY = level.getMinY();
        AABB box = new AABB(
                chunk.getMinBlockX(), minY, chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1.0D, minY + level.getHeight(), chunk.getMaxBlockZ() + 1.0D);
        return level.getEntitiesOfClass(NeroCreatureEntity.class, box, entity -> true).size();
    }

    /**
     * Invalidates the cached count for a dimension. Call after deliberately spawning a batch (an
     * event wave, a boss and its adds) so the next cap check sees them immediately.
     */
    public static void invalidate(ServerLevel level) {
        PER_DIMENSION.remove(level.dimension());
    }

    /** Drops every cached count — called when the engine sees a new server. */
    public static void reset() {
        PER_DIMENSION.clear();
    }
}
