package za.co.neroland.nerocreatures.entity.wrecks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.spawn.SpawnRule;
import za.co.neroland.nerolandcore.worldgen.SpaceTags;

/**
 * World-reading spawn gates for the Wrecks creatures, for the integration agent to pass to
 * {@code SpawnRule.gated(...)} in {@code CreatureSpawns}. Server thread only; never loads a chunk; at
 * most one bounded entity query and one roll per candidate.
 */
public final class WrecksSpawnGates {

    private WrecksSpawnGates() {
    }

    /** Level gate for {@code habitat/wrecks} rules (decision 001): a space level only. */
    public static boolean wrecksLevel(ServerLevel level) {
        return SpaceTags.isSpace(level);
    }

    /** Level gate for the Cache Mimic: {@code cacheMimicSpawns} on and a space level. */
    public static boolean mimicLevel(ServerLevel level) {
        return MimicRules.maySpawn(NeroCreaturesConfig.CACHE_MIMIC_SPAWNS.get(), SpaceTags.isSpace(level));
    }

    /** Position gate for the swarm's wreck rule: the hard area cap only. */
    public static boolean swarmRoom(ServerLevel level, BlockPos pos, RandomSource random) {
        return level.isLoaded(pos) && SwarmRules.roomInArea(swarmsNear(level, pos));
    }

    /**
     * Position gate for the swarm's overworld storm rule (decision 005): overworld, a thunderstorm,
     * open sky, block light under 10, the area cap, then the 10% roll.
     */
    public static boolean swarmStorm(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.dimension() != Level.OVERWORLD || !level.isLoaded(pos)) {
            return false;
        }
        return SwarmRules.stormSpot(level.isThundering(), level.canSeeSky(pos),
                level.getBrightness(LightLayer.BLOCK, pos))
                && SwarmRules.roomInArea(swarmsNear(level, pos))
                && SwarmRules.roll(random.nextDouble(), SwarmRules.STORM_CHANCE);
    }

    /** Typed as a {@link SpawnRule.PositionGate} for the integrator's convenience. */
    public static final SpawnRule.PositionGate SWARM_STORM = WrecksSpawnGates::swarmStorm;

    private static int swarmsNear(ServerLevel level, BlockPos pos) {
        double r = SwarmRules.AREA_RADIUS;
        AABB box = new AABB(pos.getX() - r, pos.getY() - r, pos.getZ() - r, pos.getX() + r, pos.getY() + r, pos.getZ() + r);
        List<StaticSwarm> found = new ArrayList<>(SwarmRules.MAX_PER_AREA);
        level.getEntities(EntityTypeTest.forClass(StaticSwarm.class), box, Entity::isAlive, found, SwarmRules.MAX_PER_AREA);
        return found.size();
    }
}
