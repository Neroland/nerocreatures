package za.co.neroland.nerocreatures.entity.humanoid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;

import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;

/**
 * The public way to put a pirate band somewhere on purpose.
 *
 * <p>Natural pirate spawns are rare by design; the interesting use of the creature is a
 * <b>deliberate</b> one — a NeroEvents raid, a NeroRuins encounter, an operator command. Those
 * callers need one call that places a themed group with a coherent kit tier, and they must not have
 * to know how this mod applies equipment or accounts for its own population caps. That call is
 * {@link #spawnBand}.
 *
 * <p>Contract for callers:
 *
 * <ul>
 *   <li><b>Caps are respected.</b> Placement stops early when the dimension or the chunk is at its
 *       NeroCreatures population cap; the returned list tells you how many actually arrived. This is
 *       a safety net, not a policy — an event that needs a bigger wave should raise the cap rather
 *       than route around it.</li>
 *   <li><b>Spawn reason is {@code EVENT}</b>, which makes every band member persistent (the shared
 *       base marks deliberately placed creatures as no-despawn), so a raid does not evaporate while
 *       the player is fetching a weapon.</li>
 *   <li><b>Nothing here is player-scoped.</b> No initiator, no owner, no identity is recorded on the
 *       entities (POPIA/GDPR). A caller that wants to track participation keeps that in its own
 *       store.</li>
 * </ul>
 *
 * <p>Server-side only.
 */
public final class PirateSpawner {

    /** How far apart band members are scattered around the origin, in blocks. */
    private static final int SCATTER_RADIUS = 3;

    /** Largest band this helper will place in one call. */
    private static final int MAX_BAND_SIZE = 12;

    private PirateSpawner() {
    }

    /**
     * Places a band of pirates around {@code origin}, all carrying kits from {@code loadoutTier}.
     *
     * @param level       the level to spawn in
     * @param origin      where the band forms; members are scattered up to
     *                    {@value #SCATTER_RADIUS} blocks around it
     * @param loadoutTier {@link PirateLoadout#RECRUIT_TIER} or {@link PirateLoadout#RAIDER_TIER};
     *                    unknown tiers clamp to the nearest known kit
     * @param count       how many to try to place, clamped to 1&ndash;{@value #MAX_BAND_SIZE}
     * @return the pirates that were actually added to the level, in placement order — possibly
     *         fewer than {@code count}, and possibly empty if the caps are full
     */
    public static List<SpacePirate> spawnBand(ServerLevel level, BlockPos origin, int loadoutTier,
            int count) {
        List<SpacePirate> band = new ArrayList<>();
        int wanted = Mth.clamp(count, 1, MAX_BAND_SIZE);
        RandomSource random = level.getRandom();
        SpawnGroupData groupData = null;
        for (int i = 0; i < wanted; i++) {
            if (!CreatureCensus.dimensionHasRoom(level)) {
                break;
            }
            BlockPos pos = i == 0 ? origin : scatter(origin, random);
            if (!CreatureCensus.chunkHasRoom(level, pos)) {
                continue;
            }
            SpacePirate pirate = ModEntities.SPACE_PIRATE.get().create(level, EntitySpawnReason.EVENT);
            if (pirate == null) {
                break;
            }
            pirate.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            groupData = pirate.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                    EntitySpawnReason.EVENT, groupData);
            // After finalizeSpawn, which applies the natural (recruit) kit: the requested tier wins.
            pirate.setLoadout(PirateLoadout.roll(random, loadoutTier));
            if (level.addFreshEntity(pirate)) {
                band.add(pirate);
            }
        }
        if (!band.isEmpty()) {
            // The cached per-dimension count is now stale by exactly this band.
            CreatureCensus.invalidate(level);
        }
        return band;
    }

    private static BlockPos scatter(BlockPos origin, RandomSource random) {
        int span = SCATTER_RADIUS * 2 + 1;
        return origin.offset(random.nextInt(span) - SCATTER_RADIUS, 0,
                random.nextInt(span) - SCATTER_RADIUS);
    }
}
