package za.co.neroland.nerocreatures.trait;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * The trait pool: every trait an individual can carry, with its roll weight and effect.
 *
 * <p><b>Format.</b> A jar-bundled JSON, {@value #RESOURCE}, read once through the classloader the
 * first time anything asks for the pool. It is not datapack-reloadable on purpose: that would need a
 * reload listener per loader and per line, and the pool only tunes numbers. A malformed file fails
 * loudly in a development environment and falls back to {@link #defaults()} in production, so a bad
 * edit can never stop a server from starting.
 *
 * <p><b>Mutation off switch.</b> Trait mutation is on the build prompt's cut list; cutting it is
 * {@code "mutation_chance": 0} in the JSON, no code change.
 *
 * <p>Traits live on the entity only. Nothing here touches player data.
 *
 * @param schema         pool file schema (currently 1)
 * @param mutationChance chance, per inherited slot, of being replaced by a fresh weighted roll
 * @param traits         the pool, in file order
 */
public record TraitPool(int schema, double mutationChance, List<TraitDefinition> traits) {

    /** Classpath location of the bundled pool. */
    public static final String RESOURCE = "/data/nerocreatures/creature_data/traits.json";

    public static final String HARDY = "hardy";
    public static final String SWIFT = "swift";
    public static final String KEEN = "keen";
    public static final String BOUNTIFUL = "bountiful";
    public static final String CALM = "calm";
    public static final String THRIFTY = "thrifty";
    public static final String STOUT = "stout";
    public static final String RADIANT = "radiant";

    public static final Codec<TraitPool> CODEC = RecordCodecBuilder.<TraitPool>create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema", 1).forGetter(TraitPool::schema),
            Codec.DOUBLE.optionalFieldOf("mutation_chance", 0.05D).forGetter(TraitPool::mutationChance),
            TraitDefinition.CODEC.listOf().fieldOf("traits").forGetter(TraitPool::traits)
    ).apply(instance, TraitPool::new)).validate(TraitPool::validate);

    private static volatile TraitPool loaded;

    public TraitPool {
        traits = List.copyOf(traits);
    }

    // --- access ------------------------------------------------------------

    /** The pool in use, loaded lazily from the jar on first call. Never null. */
    public static TraitPool get() {
        TraitPool pool = loaded;
        if (pool == null) {
            pool = loadBundled();
            loaded = pool;
        }
        return pool;
    }

    /** The definition for an id, or {@code null} for an unknown id (which then has no effect). */
    @Nullable
    public TraitDefinition byId(String id) {
        for (TraitDefinition def : this.traits) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /** Sum of all weights. Always positive for a validated pool. */
    public int totalWeight() {
        int total = 0;
        for (TraitDefinition def : this.traits) {
            total += def.weight();
        }
        return total;
    }

    /** One fresh weighted roll. */
    public TraitDefinition roll(RandomSource random) {
        int pick = random.nextInt(this.totalWeight());
        for (TraitDefinition def : this.traits) {
            pick -= def.weight();
            if (pick < 0) {
                return def;
            }
        }
        return this.traits.get(this.traits.size() - 1); // unreachable for a validated pool
    }

    // --- loading -----------------------------------------------------------

    /** Parses a pool document. Pure: no logging, no fallback. */
    public static DataResult<TraitPool> parse(JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json);
    }

    /** Parses a pool from a stream (UTF-8 JSON). */
    public static DataResult<TraitPool> parse(InputStream in) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader));
        } catch (Exception e) { // IOException or a Gson syntax error
            return DataResult.error(() -> "Trait pool is not valid JSON: " + e.getClass().getSimpleName());
        }
    }

    private static TraitPool loadBundled() {
        DataResult<TraitPool> result;
        try (InputStream in = TraitPool.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] Trait pool resource missing; using built-in defaults.");
                return defaults();
            }
            result = parse(in);
        } catch (Exception e) {
            result = DataResult.error(() -> "Trait pool could not be read: " + e.getClass().getSimpleName());
        }
        if (result.result().isPresent()) {
            return result.result().get();
        }
        String message = result.error().map(err -> err.message()).orElse("unknown error");
        if (isDevelopment()) {
            throw new IllegalStateException("[NeroCreatures] Malformed trait pool " + RESOURCE + ": " + message);
        }
        NeroCreaturesCommon.LOGGER.error("[NeroCreatures] Malformed trait pool ({}); using built-in defaults.", message);
        return defaults();
    }

    private static boolean isDevelopment() {
        try {
            return za.co.neroland.nerocreatures.platform.Services.PLATFORM.isDevelopmentEnvironment();
        } catch (Throwable ignored) { // no platform service (unit tests): treat as production
            return false;
        }
    }

    /** Test seam and reload hook: replaces the pool in use. Pass {@code null} to reload from the jar. */
    public static void override(@Nullable TraitPool pool) {
        loaded = pool;
    }

    /** The built-in pool, identical to the bundled JSON. */
    public static TraitPool defaults() {
        return new TraitPool(1, 0.05D, List.of(
                new TraitDefinition(HARDY, 100, TraitEffect.attribute("minecraft:max_health", "add_multiplied_base", 0.20D)),
                new TraitDefinition(SWIFT, 100, TraitEffect.attribute("minecraft:movement_speed", "add_multiplied_base", 0.15D)),
                new TraitDefinition(KEEN, 100, TraitEffect.multiplier(TraitEffect.Type.ABILITY_RANGE, 1.25D)),
                new TraitDefinition(BOUNTIFUL, 100, TraitEffect.productYield(0.5D)),
                new TraitDefinition(CALM, 100, TraitEffect.temperament(0.5D, true)),
                new TraitDefinition(THRIFTY, 100, TraitEffect.multiplier(TraitEffect.Type.COOLDOWN, 0.8D)),
                new TraitDefinition(STOUT, 100, TraitEffect.carrySlots(18)),
                new TraitDefinition(RADIANT, 7, TraitEffect.cosmetic())));
    }

    private static DataResult<TraitPool> validate(TraitPool pool) {
        if (pool.traits().isEmpty()) {
            return DataResult.error(() -> "Trait pool is empty");
        }
        if (pool.mutationChance() < 0.0D || pool.mutationChance() > 1.0D) {
            return DataResult.error(() -> "mutation_chance must be within 0..1");
        }
        Set<String> seen = new HashSet<>();
        for (TraitDefinition def : pool.traits()) {
            if (!def.id().matches("[a-z0-9_]{1,32}")) {
                return DataResult.error(() -> "Trait id must be 1-32 chars of [a-z0-9_]: " + def.id());
            }
            if (!seen.add(def.id())) {
                return DataResult.error(() -> "Duplicate trait id: " + def.id());
            }
            if (def.weight() <= 0 || def.weight() > 1_000_000) {
                return DataResult.error(() -> "Trait weight out of range: " + def.id());
            }
            if (def.effect().type() == TraitEffect.Type.ATTRIBUTE
                    && !TraitAttributes.isSupported(def.effect().attribute(), def.effect().operation())) {
                return DataResult.error(() -> "Unsupported attribute or operation on trait: " + def.id());
            }
        }
        return DataResult.success(pool);
    }

    /** Ids in pool order; handy for glossaries and tests. */
    public Map<String, TraitDefinition> asMap() {
        Map<String, TraitDefinition> map = new LinkedHashMap<>();
        for (TraitDefinition def : this.traits) {
            map.put(def.id(), def);
        }
        return map;
    }
}
