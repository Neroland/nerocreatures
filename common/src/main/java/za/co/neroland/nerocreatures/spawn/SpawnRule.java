package za.co.neroland.nerocreatures.spawn;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
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
 * @param gate          an extra per-level condition, checked <b>before</b> the biome lookup because
 *                      it is asked on every candidate position. Almost every rule passes
 *                      "always" here; it exists for the one creature whose availability is a policy
 *                      rather than a place — a boss, gated on {@code naturalBossSpawnsEnabled}, a
 *                      cooldown and "is one already out there" (see {@code boss/BossSpawns}). A
 *                      gate must stay cheap.
 * @param positionGate  a per-position condition (decision 005), asked <b>after</b> the biome test
 *                      and only for a candidate that already matched everything else. It may read
 *                      the world at the position and may roll a chance; {@link #ANYWHERE} for most
 *                      rules
 * @param candidate     how the spawner picks the position this rule is tested at
 *                      ({@link Candidate#SURFACE} for most rules)
 * @param overworldCapped whether this rule's creatures count toward, and are held back by,
 *                      {@code overworldSpawnCap} when they spawn in the overworld (decision 005)
 */
public record SpawnRule(
        String creatureId,
        Supplier<? extends EntityType<? extends Mob>> type,
        TagKey<Biome> biomeTag,
        Optional<TagKey<DimensionType>> dimensionTag,
        int weight,
        int minGroupSize,
        int maxGroupSize,
        Predicate<ServerLevel> gate,
        PositionGate positionGate,
        Candidate candidate,
        boolean overworldCapped) {

    /**
     * A position-dependent spawn condition. Runs on the server thread for one candidate position;
     * it must not load chunks and should stay a handful of block reads plus at most one roll.
     */
    @FunctionalInterface
    public interface PositionGate {
        boolean test(ServerLevel level, BlockPos pos, RandomSource random);
    }

    /** Where the spawner looks for this rule's position. */
    public enum Candidate {
        /** The surface of a random column in the ring around a player (vanilla-like). */
        SURFACE,
        /** As {@link #SURFACE}, lifted 1-4 blocks into the air: flyers. */
        AIR,
        /** A cave floor at or below Y 0 in the same column (decision 005). */
        CAVE
    }

    public SpawnRule {
        if (weight < 0) {
            throw new IllegalArgumentException("Spawn weight must not be negative: " + creatureId);
        }
        if (minGroupSize < 1 || maxGroupSize < minGroupSize) {
            throw new IllegalArgumentException("Invalid group size for " + creatureId);
        }
        if (gate == null || positionGate == null || candidate == null) {
            throw new IllegalArgumentException("Spawn gate must not be null: " + creatureId);
        }
    }

    /** The pre-0.5.0 shape: surface candidate, no position gate, no overworld cap. */
    public SpawnRule(String creatureId, Supplier<? extends EntityType<? extends Mob>> type,
            TagKey<Biome> biomeTag, Optional<TagKey<DimensionType>> dimensionTag, int weight,
            int minGroupSize, int maxGroupSize, Predicate<ServerLevel> gate) {
        this(creatureId, type, biomeTag, dimensionTag, weight, minGroupSize, maxGroupSize, gate,
                ANYWHERE, Candidate.SURFACE, false);
    }

    /** The gate almost every rule uses: nothing beyond the biome and dimension tags. */
    public static final Predicate<ServerLevel> ALWAYS = level -> true;

    /** The position gate almost every rule uses: none. */
    public static final PositionGate ANYWHERE = (level, pos, random) -> true;

    /**
     * A rule guarded to off-Earth dimension types — the normal case for this mod.
     */
    public static SpawnRule inSpace(String creatureId, Supplier<? extends EntityType<? extends Mob>> type,
            TagKey<Biome> biomeTag, int weight, int minGroupSize, int maxGroupSize) {
        return new SpawnRule(creatureId, type, biomeTag, Optional.of(SpaceTags.SPACE_DIMENSIONS),
                weight, minGroupSize, maxGroupSize, ALWAYS);
    }

    /**
     * A rule with no dimension guard — for biomes that live on a dimension type shared with vanilla
     * (Nerospace's Greenxertz reuses {@code minecraft:overworld}, which cannot be tagged without
     * dragging the real Overworld in with it), where the biome tag is the only precise lever.
     */
    public static SpawnRule anyDimension(String creatureId, Supplier<? extends EntityType<? extends Mob>> type,
            TagKey<Biome> biomeTag, int weight, int minGroupSize, int maxGroupSize) {
        return new SpawnRule(creatureId, type, biomeTag, Optional.empty(),
                weight, minGroupSize, maxGroupSize, ALWAYS);
    }

    /**
     * As {@link #anyDimension}, plus a per-level {@code gate}. Used for the rules whose availability
     * is a decision rather than a place — currently only the boss.
     */
    public static SpawnRule gated(String creatureId, Supplier<? extends EntityType<? extends Mob>> type,
            TagKey<Biome> biomeTag, int weight, int minGroupSize, int maxGroupSize,
            Predicate<ServerLevel> gate) {
        return new SpawnRule(creatureId, type, biomeTag, Optional.empty(),
                weight, minGroupSize, maxGroupSize, gate);
    }

    /**
     * As {@link #gated(String, Supplier, TagKey, int, int, int, Predicate)}, plus a per-position gate
     * (decision 005): the cheap per-level {@code gate} is asked first, the biome next, and
     * {@code positionGate} last, so its block reads and its chance roll only happen for a candidate
     * that already qualifies.
     */
    public static SpawnRule gated(String creatureId, Supplier<? extends EntityType<? extends Mob>> type,
            TagKey<Biome> biomeTag, int weight, int minGroupSize, int maxGroupSize,
            Predicate<ServerLevel> gate, PositionGate positionGate) {
        return new SpawnRule(creatureId, type, biomeTag, Optional.empty(),
                weight, minGroupSize, maxGroupSize, gate, positionGate, Candidate.SURFACE, false);
    }

    /** This rule, tested at a cave-floor candidate instead of the surface. */
    public SpawnRule caveMode() {
        return this.withCandidate(Candidate.CAVE);
    }

    /** This rule, tested 1-4 blocks above the surface (flyers). */
    public SpawnRule airMode() {
        return this.withCandidate(Candidate.AIR);
    }

    /** This rule, counted toward and held back by {@code overworldSpawnCap} (decision 005). */
    public SpawnRule overworldCapped() {
        return new SpawnRule(this.creatureId, this.type, this.biomeTag, this.dimensionTag, this.weight,
                this.minGroupSize, this.maxGroupSize, this.gate, this.positionGate, this.candidate, true);
    }

    private SpawnRule withCandidate(Candidate mode) {
        return new SpawnRule(this.creatureId, this.type, this.biomeTag, this.dimensionTag, this.weight,
                this.minGroupSize, this.maxGroupSize, this.gate, this.positionGate, mode,
                this.overworldCapped);
    }

    /** Whether this rule's dimension guard admits {@code level}. */
    public boolean matchesDimension(ServerLevel level) {
        return this.dimensionTag
                .map(tag -> level.dimensionTypeRegistration().is(tag))
                .orElse(Boolean.TRUE);
    }

    /**
     * Whether this rule applies at {@code pos}. Every part is empty-tag safe: an unmatched or empty
     * tag simply means "not here". The biome lookup is deliberately <b>last</b> — it is the most
     * expensive of the three, and both of the checks in front of it are cheap enough to be worth
     * asking first.
     */
    public boolean matches(ServerLevel level, BlockPos pos, RandomSource random) {
        return this.matchesLevel(level)
                && SpaceTags.biomeIn(level, pos, this.biomeTag)
                && this.positionGate.test(level, pos, random);
    }

    /** The position-free half of {@link #matches}: dimension guard and per-level gate. */
    public boolean matchesLevel(ServerLevel level) {
        return this.matchesDimension(level) && this.gate.test(level);
    }
}
