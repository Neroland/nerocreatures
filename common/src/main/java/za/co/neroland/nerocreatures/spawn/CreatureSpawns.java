package za.co.neroland.nerocreatures.spawn;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.worldgen.SpaceTags;

import za.co.neroland.nerocreatures.boss.BossFights;
import za.co.neroland.nerocreatures.boss.BossSpawns;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * NeroCreatures' natural-spawn engine.
 *
 * <h2>Why a server-tick spawner rather than biome-modifier injection</h2>
 *
 * <p>The obvious implementation — add {@code MobSpawnSettings.SpawnerData} entries to space biomes
 * and let vanilla's {@code NaturalSpawner} do the work — has no portable form. NeoForge does it with
 * JSON biome modifiers, Forge with a different event, and Fabric with the Fabric API's
 * {@code BiomeModifications}; there are three data formats and three registration points, and the
 * biomes being modified belong to a mod that may not be installed. Splitting the spawn table across
 * three loader-specific data systems would put the single most gameplay-visible behaviour in this
 * mod in the place hardest to keep identical across six build cells.
 *
 * <p>So NeroCreatures declares its spawn table once, in code ({@link SpawnRule}), and runs its own
 * placement pass on the server tick. The pass deliberately mirrors what vanilla's spawner does, so
 * the result feels the same:
 *
 * <ul>
 *   <li><b>Player-anchored.</b> Candidate positions are drawn in a ring
 *       {@value #MIN_SPAWN_DISTANCE}–{@value #MAX_SPAWN_DISTANCE} blocks around a player: far enough
 *       not to pop into view, close enough to matter. Vanilla's 24-block "too close" floor is the
 *       same number.</li>
 *   <li><b>Budgeted.</b> One sweep every {@value #SWEEP_INTERVAL_TICKS} ticks, at most
 *       {@value #MAX_PLAYERS_PER_SWEEP} players considered, at most
 *       {@value #MAX_ATTEMPTS_PER_PLAYER} candidate positions each, and at most
 *       {@value #MAX_GROUPS_PER_SWEEP} groups actually placed per level per sweep. The worst case is
 *       a fixed, small number of heightmap reads and tag tests — it does not grow with world size or
 *       player count beyond that cap.</li>
 *   <li><b>Vanilla placement rules.</b> Each candidate goes through the same
 *       {@link SpawnPlacements#isSpawnPositionOk} / {@link SpawnPlacements#checkSpawnRules} pair
 *       vanilla uses (registered through Core's entity seam), plus a collision check. A creature
 *       therefore respects light levels, block validity and difficulty exactly like a vanilla
 *       mob.</li>
 *   <li><b>Capped.</b> {@code maxCreaturesPerChunk} and {@code maxCreaturesPerDimension} are checked
 *       at placement time via {@link CreatureCensus}, not after the fact.</li>
 * </ul>
 *
 * <p><b>Empty tags mean no spawns.</b> With no planet mod installed, every Core space tag is empty,
 * every rule fails {@link SpawnRule#matches}, and the sweep places nothing. Earth stays quiet
 * because the data says so, not because of a special case.
 *
 * <p>Everything here is server-side. No player data is read beyond a player's position, and nothing
 * is stored or logged about who was near a spawn (POPIA/GDPR).
 */
public final class CreatureSpawns {

    /** Ticks between placement sweeps. 40 = twice a second. */
    private static final int SWEEP_INTERVAL_TICKS = 40;

    /** Players considered per sweep, per level. */
    private static final int MAX_PLAYERS_PER_SWEEP = 8;

    /** Candidate positions tried per player. */
    private static final int MAX_ATTEMPTS_PER_PLAYER = 3;

    /** Groups actually placed per level per sweep. */
    private static final int MAX_GROUPS_PER_SWEEP = 2;

    /** Closest a spawn may be to the anchoring player, in blocks (vanilla uses 24 as well). */
    private static final int MIN_SPAWN_DISTANCE = 24;

    /** Furthest a spawn may be from the anchoring player, in blocks. */
    private static final int MAX_SPAWN_DISTANCE = 48;

    private static final List<SpawnRule> RULES = new ArrayList<>();

    private static int tickCounter;

    /**
     * The server the sweep clock and population counters belong to. Weak so a stopped server is
     * still collectable; comparing identity here is how the engine notices a new world without
     * needing a loader-specific "server stopped" event on all three loaders.
     */
    private static WeakReference<MinecraftServer> currentServer = new WeakReference<>(null);

    private CreatureSpawns() {
    }

    /**
     * Declares the spawn table. Called once from common init, after the entity types exist.
     *
     * <p>Weights are relative to each other <em>within the biomes a rule matches</em>, not global:
     * a position only ever picks between the rules that apply there, so the Asteroid Worm's weight
     * of 6 competes with nothing at all in an asteroid field. Each creature adds its line here as it
     * is built; a creature with no line never spawns naturally, which is the intended state for
     * structure- and event-placed mobs.
     */
    public static void init() {
        // Every rule below uses anyDimension() rather than inSpace(): the biome tag IS the precise
        // lever, and a dimension guard would silently exclude any planet that reuses a vanilla
        // dimension type (Nerospace's Greenxertz reuses minecraft:overworld, so it cannot be tagged
        // without dragging the real Overworld in with it). No vanilla Earth biome is a member of any
        // neroland:space/* tag, so dropping the guard costs nothing and losing a planet would.

        // Void Crawler — the common hostile of the dark planets. Solitary or in pairs; the threat is
        // the blink, so a crowd of them would be unfair rather than harder.
        register(SpawnRule.anyDimension("void_crawler", ModEntities.VOID_CRAWLER,
                SpaceTags.DARK_BIOMES, 30, 1, 2));

        // Lunar Stalker — always arrives as a pack, because one stalker is not a fight and the pack
        // AI (broadcast + flank) is the whole point of the creature.
        register(SpawnRule.anyDimension("lunar_stalker", ModEntities.LUNAR_STALKER,
                SpaceTags.MOON_BIOMES, 24, 2, 4));

        // Asteroid Worm — an elite. Deliberately the rarest thing in the table and never in groups:
        // meeting one should be an event, and two at once would be a wall.
        register(SpawnRule.anyDimension("asteroid_worm", ModEntities.ASTEROID_WORM,
                SpaceTags.ASTEROID_BIOMES, 6, 1, 1));

        // Plasma Slime — at home in the crystal terrain, an occasional visitor to the dark. Groups
        // stay small because every one of them can still split.
        register(SpawnRule.anyDimension("plasma_slime", ModEntities.PLASMA_SLIME,
                SpaceTags.CRYSTALLINE_BIOMES, 20, 1, 3));
        register(SpawnRule.anyDimension("plasma_slime", ModEntities.PLASMA_SLIME,
                SpaceTags.DARK_BIOMES, 8, 1, 2));

        // Crystal Golem — a neutral elite and a resource node, so it is rare and always alone:
        // stumbling onto one should feel like finding an ore vein that can hit back, not like being
        // ambushed by a herd.
        register(SpawnRule.anyDimension("crystal_golem", ModEntities.CRYSTAL_GOLEM,
                SpaceTags.CRYSTALLINE_BIOMES, 8, 1, 1));

        // Space Pirate — the only rule against the PLANET_BIOMES umbrella, so a band can turn up on
        // any off-Earth surface. Low weight, because the interesting pirates are the ones a future
        // NeroEvents raid places on purpose (entity/humanoid/PirateSpawner).
        register(SpawnRule.anyDimension("space_pirate", ModEntities.SPACE_PIRATE,
                SpaceTags.PLANET_BIOMES, 10, 2, 3));

        // Rogue Androids — deliberately the rarest natural spawns in the table after the worm. They
        // are a ruins creature; NeroRuins will place them properly (entity/mechanical/AndroidSpawner)
        // and these two lines only ensure the pair is obtainable in survival before that lands.
        register(SpawnRule.anyDimension("rogue_drone", ModEntities.ROGUE_DRONE,
                SpaceTags.DARK_BIOMES, 6, 1, 2));
        register(SpawnRule.anyDimension("rogue_android", ModEntities.ROGUE_ANDROID,
                SpaceTags.DARK_BIOMES, 3, 1, 1));

        // Glacite Wisp — the frozen-world pet. Two rules, because a glacial crystal world sits in
        // both tags: it is a moon first and a crystal field second, hence the weight split.
        register(SpawnRule.anyDimension("glacite_wisp", ModEntities.GLACITE_WISP,
                SpaceTags.MOON_BIOMES, 12, 1, 2));
        register(SpawnRule.anyDimension("glacite_wisp", ModEntities.GLACITE_WISP,
                SpaceTags.CRYSTALLINE_BIOMES, 6, 1, 2));

        // Xertz Forager — the lush-world pet, and the only creature that shares the crystalline tag
        // with the golem and the slime without being a threat. Small herds, so a player looking for
        // one has a reasonable chance of finding one before they run out of plasma cells.
        register(SpawnRule.anyDimension("xertz_forager", ModEntities.XERTZ_FORAGER,
                SpaceTags.CRYSTALLINE_BIOMES, 14, 1, 3));

        // Cinder Tyrant — the boss, and by far the rarest line in the table. Its weight is a tenth
        // of the next-rarest creature's, it is always alone, and unlike everything above it the
        // weighted roll is not what actually decides whether it appears: the gate is, and the gate
        // says "only if the server allows natural bosses, only once a Minecraft day per dimension,
        // and only if there is not already one out there" (boss/BossSpawns). The weight is what
        // makes it feel like a rumour rather than a schedule. DARK_BIOMES is the tag Cindara — the
        // ember world it belongs to — is a member of.
        register(SpawnRule.gated("cinder_tyrant", ModEntities.CINDER_TYRANT,
                SpaceTags.DARK_BIOMES, 1, 1, 1, BossSpawns::maySpawnNaturally));

        // The Terraforming Drone has no rule at all, and never will: it is deployed, not spawned.
    }

    /** Adds a rule to the table. Call from {@link #init()} only. */
    public static void register(SpawnRule rule) {
        RULES.add(rule);
    }

    /** The declared spawn table, for commands and the wiki. */
    public static List<SpawnRule> rules() {
        return List.copyOf(RULES);
    }

    /**
     * The per-server tick hook each loader module wires to its own tick event. Self-throttling: the
     * expensive work happens once every {@value #SWEEP_INTERVAL_TICKS} ticks.
     */
    public static void serverTick(MinecraftServer server) {
        if (currentServer.get() != server) {
            // A different server instance than last tick: a new world (or the first one). Drop the
            // sweep clock and the population counters so nothing carries over from the last save.
            reset();
            currentServer = new WeakReference<>(server);
        }
        if (RULES.isEmpty() || !NeroCreaturesConfig.SPAWNS_ENABLED.get()) {
            return;
        }
        if (++tickCounter < SWEEP_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        for (ServerLevel level : server.getAllLevels()) {
            sweep(level);
        }
    }

    /**
     * The server this engine last ticked, or {@code null} before the first server tick and after a
     * shutdown. It is tracked here anyway, for the sweep clock — so the link module, which Core's
     * snapshot API hands nothing but a player UUID, reads it from here rather than keeping a second
     * copy of the same reference.
     */
    @Nullable
    public static MinecraftServer currentServer() {
        return currentServer.get();
    }

    /** Resets the sweep clock and population counters. Called when the engine sees a new server. */
    public static void reset() {
        tickCounter = 0;
        CreatureCensus.reset();
        BossSpawns.reset();
        BossFights.reset();
    }

    private static void sweep(ServerLevel level) {
        if (!CreatureCensus.dimensionHasRoom(level)) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        int placed = 0;
        int playersConsidered = Math.min(players.size(), MAX_PLAYERS_PER_SWEEP);
        for (int i = 0; i < playersConsidered && placed < MAX_GROUPS_PER_SWEEP; i++) {
            ServerPlayer player = players.get(i);
            if (player.isSpectator()) {
                continue;
            }
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_PLAYER && placed < MAX_GROUPS_PER_SWEEP; attempt++) {
                if (trySpawnNear(level, player, random)) {
                    placed++;
                }
            }
        }
    }

    private static boolean trySpawnNear(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos pos = candidatePosition(level, player, random);
        if (pos == null) {
            return false;
        }
        SpawnRule rule = pickRule(level, pos, random);
        if (rule == null) {
            return false;
        }
        if (!CreatureCensus.chunkHasRoom(level, pos) || !CreatureCensus.dimensionHasRoom(level)) {
            return false;
        }
        return placeGroup(level, rule, pos, random) > 0;
    }

    /**
     * A random position in the ring around the player, snapped to the surface. Returns {@code null}
     * if the position is not in a loaded, spawn-eligible chunk — cheaper to bail here than to run
     * the tag tests on a position we cannot use.
     */
    private static BlockPos candidatePosition(ServerLevel level, ServerPlayer player, RandomSource random) {
        int span = MAX_SPAWN_DISTANCE - MIN_SPAWN_DISTANCE;
        int offsetX = (MIN_SPAWN_DISTANCE + random.nextInt(span)) * (random.nextBoolean() ? 1 : -1);
        int offsetZ = (MIN_SPAWN_DISTANCE + random.nextInt(span)) * (random.nextBoolean() ? 1 : -1);
        int x = player.blockPosition().getX() + offsetX;
        int z = player.blockPosition().getZ() + offsetZ;
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column) || !level.canSpawnEntitiesInChunk(ChunkPos.containing(column))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    /**
     * Weighted pick among the rules that apply at {@code pos}. Weights are multiplied by
     * {@code globalSpawnWeightMultiplier}; a total of zero (multiplier 0, or no matching rule)
     * yields {@code null} and the attempt is abandoned.
     */
    private static SpawnRule pickRule(ServerLevel level, BlockPos pos, RandomSource random) {
        double multiplier = NeroCreaturesConfig.GLOBAL_SPAWN_WEIGHT_MULTIPLIER.get();
        if (multiplier <= 0.0D) {
            return null;
        }
        double total = 0.0D;
        List<SpawnRule> eligible = new ArrayList<>(RULES.size());
        for (SpawnRule rule : RULES) {
            if (rule.weight() > 0 && rule.matches(level, pos)) {
                eligible.add(rule);
                total += rule.weight() * multiplier;
            }
        }
        if (eligible.isEmpty() || total <= 0.0D) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (SpawnRule rule : eligible) {
            roll -= rule.weight() * multiplier;
            if (roll <= 0.0D) {
                return rule;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    /**
     * Places one group for {@code rule}, running vanilla's placement checks per member and stopping
     * the moment a cap is reached.
     *
     * @return how many creatures were actually spawned
     */
    private static int placeGroup(ServerLevel level, SpawnRule rule, BlockPos origin, RandomSource random) {
        EntityType<? extends Mob> type = rule.type().get();
        if (type == null) {
            return 0;
        }
        int groupSize = rule.minGroupSize()
                + random.nextInt(1 + rule.maxGroupSize() - rule.minGroupSize());
        SpawnGroupData groupData = null;
        int spawned = 0;
        for (int i = 0; i < groupSize; i++) {
            if (!CreatureCensus.chunkHasRoom(level, origin) || !CreatureCensus.dimensionHasRoom(level)) {
                break;
            }
            BlockPos pos = i == 0 ? origin : scatter(origin, random);
            if (!isPlaceable(level, type, pos, random)) {
                continue;
            }
            Mob mob = type.create(level, EntitySpawnReason.NATURAL);
            if (mob == null) {
                break;
            }
            mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            groupData = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                    EntitySpawnReason.NATURAL, groupData);
            if (level.addFreshEntity(mob)) {
                spawned++;
            }
        }
        return spawned;
    }

    /** Vanilla's placement gate: valid block, spawn rules (light/difficulty), and room to stand. */
    private static boolean isPlaceable(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos,
            RandomSource random) {
        return SpawnPlacements.isSpawnPositionOk(type, level, pos)
                && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)
                && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
    }

    private static BlockPos scatter(BlockPos origin, RandomSource random) {
        return origin.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
    }
}
