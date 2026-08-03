package za.co.neroland.nerocreatures.spawn;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

import za.co.neroland.nerolandcore.worldgen.SpaceTags;

/**
 * One line of the spawn table: "this creature, in biomes carrying this tag, at this weight, in
 * groups of this size".
 *
 * <p>Rules are declared in <b>code</b> (see {@link CreatureSpawns}) rather than in biome-modifier
 * JSON because {@code MobSpawnSettings} injection is loader-divergent — NeoForge biome modifiers,
 * Forge's own event, and Fabric's {@code BiomeModifications} are three different systems with three
 * different data formats. A shared list of rules plus a shared placement pass is the only version
 * of this that is genuinely identical on all six cells.
 *
 * <p><b>Empty tags mean no spawns.</b> Every biome tag here comes from Core's
 * {@link SpaceTags} vocabulary, and on a server with no planet mod installed those tags are empty.
 * {@link #matches} then simply returns {@code false} everywhere and this rule contributes nothing —
 * which is exactly why vanilla Earth stays quiet by design rather than by a special case.
 *
 * @param creatureId    a stable id for logs, commands and the wiki ("void_crawler")
 * @param type          the entity type to spawn, as a supplier because on the deferred-register
 *                      loaders the type does not exist yet while rules are being declared
 * @param biomeTag      the Core space-biome tag this creature belongs to
 * @param dimensionTag  optional coarse dimension-type guard, usually
 *                      {@link SpaceTags#SPACE_DIMENSIONS}; empty means "any dimension whose biome
 *                      matches"
 * @param weight        relative selection weight before {@code globalSpawnWeightMultiplier}
 * @param minGroupSize  smallest group placed by one successful attempt
 * @param maxGroupSize  largest group placed by one successful attempt
 */
public record SpawnRule(
        String creatureId,
        Supplier<? extends EntityType<? extends Mob>> type,
        TagKey<Biome> biomeTag,
        Optional<TagKey<DimensionType>> dimensionTag,
        int weight,
        int minGroupSize,
        int maxGroupSize) {

    public SpawnRule {
        if (weight < 0) {
            throw new IllegalArgumentException("Spawn weight must not be negative: " + creatureId);
        }
        if (minGroupSize < 1 || maxGroupSize < minGroupSize) {
            throw new IllegalArgumentException("Invalid group size for " + creatureId);
        }
    }

    /**
     * A rule guarded to off-Earth dimension types — the normal case for this mod.
     */
    public static SpawnRule inSpace(String creatureId, Supplier<? extends EntityType<? extends Mob>> type,
            TagKey<Biome> biomeTag, int weight, int minGroupSize, int maxGroupSize) {
        return new SpawnRule(creatureId, type, biomeTag, Optional.of(SpaceTags.SPACE_DIMENSIONS),
                weight, minGroupSize, maxGroupSize);
    }

    /**
     * A rule with no dimension guard — for biomes that live on a dimension type shared with vanilla
     * (Nerospace's Greenxertz reuses {@code minecraft:overworld}, which cannot be tagged without
     * dragging the real Overworld in with it), where the biome tag is the only precise lever.
     */
    public static SpawnRule anyDimension(String creatureId, Supplier<? extends EntityType<? extends Mob>> type,
            TagKey<Biome> biomeTag, int weight, int minGroupSize, int maxGroupSize) {
        return new SpawnRule(creatureId, type, biomeTag, Optional.empty(),
                weight, minGroupSize, maxGroupSize);
    }

    /** Whether this rule's dimension guard admits {@code level}. */
    public boolean matchesDimension(ServerLevel level) {
        return this.dimensionTag
                .map(tag -> level.dimensionTypeRegistration().is(tag))
                .orElse(Boolean.TRUE);
    }

    /**
     * Whether this rule applies at {@code pos}. Both halves are empty-tag safe: an unmatched or
     * empty tag simply means "not here".
     */
    public boolean matches(ServerLevel level, BlockPos pos) {
        return this.matchesDimension(level) && SpaceTags.biomeIn(level, pos, this.biomeTag);
    }
}
