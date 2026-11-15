package za.co.neroland.nerocreatures.entity.luna;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * World-reading halves of the decision-005 overworld rules for Lumen Moth and Lode Tortoise, for the
 * integration agent to wire into {@code CreatureSpawns} (position gate overload and the cave
 * candidate mode). Server thread only; never loads a chunk.
 */
public final class LunaSpawnGates {

    private LunaSpawnGates() {
    }

    /**
     * Lumen Moth, overworld: dark outside, the spot is air with block light at most 7, and the 15%
     * chance roll. The biome check ({@code habitat/overworld_dark_forest}) is the rule's own.
     */
    public static boolean mothOverworld(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.dimension() != Level.OVERWORLD || !level.isLoaded(pos)) {
            return false;
        }
        return LunaSpawnRules.mothSpot(level.isDarkOutside(), level.getBlockState(pos).isAir(),
                level.getBrightness(LightLayer.BLOCK, pos))
                && LunaSpawnRules.roll(random.nextDouble(), LunaSpawnRules.MOTH_OVERWORLD_CHANCE);
    }

    /**
     * Lode Tortoise, overworld deep caves: Y at most 0, no sky, sky light 0, base stone below, and
     * the 5% chance roll.
     */
    public static boolean tortoiseCave(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.dimension() != Level.OVERWORLD || !level.isLoaded(pos)) {
            return false;
        }
        return LunaSpawnRules.tortoiseSpot(pos.getY(), level.canSeeSky(pos),
                level.getBrightness(LightLayer.SKY, pos),
                level.getBlockState(pos.below()).is(BlockTags.BASE_STONE_OVERWORLD))
                && LunaSpawnRules.roll(random.nextDouble(), LunaSpawnRules.TORTOISE_CAVE_CHANCE);
    }

    /**
     * The cave candidate mode: a random Y in {@code [minY + 8, 0]} at the column of {@code column},
     * scanning down up to 12 blocks for a floor with two air blocks above. Null when the column is
     * unloaded or no floor is found.
     */
    @Nullable
    public static BlockPos caveCandidate(ServerLevel level, BlockPos column, RandomSource random) {
        int[] band = LunaSpawnRules.caveBand(level.getMinY());
        if (band[0] > band[1]) {
            return null;
        }
        int y = band[0] + random.nextInt(band[1] - band[0] + 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(column.getX(), y, column.getZ());
        if (!level.isLoaded(cursor)) {
            return null;
        }
        for (int i = 0; i < LunaSpawnRules.CAVE_SCAN_BLOCKS && cursor.getY() > band[0]; i++) {
            BlockPos below = cursor.below();
            if (LunaSpawnRules.caveFloor(level.getBlockState(below).isSolid(), level.getBlockState(cursor).isAir(),
                    level.getBlockState(cursor.above()).isAir())) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }
}
