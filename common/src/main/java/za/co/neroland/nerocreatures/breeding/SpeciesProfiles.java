package za.co.neroland.nerocreatures.breeding;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * The jar-bundled {@value #RESOURCE}: per-species breeding, Studied threshold and
 * product timer rows, keyed by the entity id's path ({@code dune_loper}). Loaded once, lazily, like
 * the trait pool; a malformed file fails loudly in development and degrades to an empty table (every
 * species on {@link SpeciesProfile#DEFAULT}) in production. Each species adds its own row.
 *
 * @param schema  file schema (currently 1)
 * @param species rows by entity-id path
 */
public record SpeciesProfiles(int schema, Map<String, SpeciesProfile> species) {

    public static final String RESOURCE = "/data/nerocreatures/creature_data/species.json";

    public static final Codec<SpeciesProfiles> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema", 1).forGetter(SpeciesProfiles::schema),
            Codec.unboundedMap(Codec.STRING, SpeciesProfile.CODEC).optionalFieldOf("species", Map.of())
                    .forGetter(SpeciesProfiles::species)
    ).apply(instance, SpeciesProfiles::new));

    private static volatile SpeciesProfiles loaded;

    public SpeciesProfiles {
        species = Map.copyOf(species);
    }

    /** The profile for an entity-id path; {@link SpeciesProfile#DEFAULT} when the species has no row. */
    public static SpeciesProfile of(String speciesPath) {
        return get().species().getOrDefault(speciesPath, SpeciesProfile.DEFAULT);
    }

    public static SpeciesProfiles get() {
        SpeciesProfiles table = loaded;
        if (table == null) {
            table = loadBundled();
            loaded = table;
        }
        return table;
    }

    /** Test seam: replaces the table. {@code null} reloads from the jar. */
    public static void override(@Nullable SpeciesProfiles table) {
        loaded = table;
    }

    public static DataResult<SpeciesProfiles> parse(JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json);
    }

    private static SpeciesProfiles loadBundled() {
        DataResult<SpeciesProfiles> result;
        try (InputStream in = SpeciesProfiles.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new SpeciesProfiles(1, Map.of());
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                result = parse(JsonParser.parseReader(reader));
            }
        } catch (Exception e) {
            result = DataResult.error(() -> "Species table could not be read: " + e.getClass().getSimpleName());
        }
        if (result.result().isPresent()) {
            return result.result().get();
        }
        String message = result.error().map(err -> err.message()).orElse("unknown error");
        boolean dev;
        try {
            dev = za.co.neroland.nerocreatures.platform.Services.PLATFORM.isDevelopmentEnvironment();
        } catch (Throwable ignored) {
            dev = false;
        }
        if (dev) {
            throw new IllegalStateException("[NeroCreatures] Malformed species table " + RESOURCE + ": " + message);
        }
        NeroCreaturesCommon.LOGGER.error("[NeroCreatures] Malformed species table ({}); using defaults.", message);
        return new SpeciesProfiles(1, Map.of());
    }
}
