package za.co.neroland.nerocreatures.spawn;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * NeroCreatures' own habitat biome tags, {@code nerocreatures:habitat/<world>} at
 * {@code data/nerocreatures/tags/worldgen/biome/habitat/}. Each one lists a single world's biomes, with
 * every planet-mod entry {@code required:false}, because Core's umbrella space tags mix worlds.
 *
 * <p>An empty tag is the normal state on a server without the planet mod: the rules naming it simply
 * never match. No planet-mod class is referenced and nothing is resolved eagerly.
 */
public final class HabitatTags {

    public static final TagKey<Biome> LUNA = habitat("luna");
    public static final TagKey<Biome> MARS = habitat("mars");
    public static final TagKey<Biome> CINDARA = habitat("cindara");
    public static final TagKey<Biome> GLACIRA = habitat("glacira");
    public static final TagKey<Biome> GREENXERTZ = habitat("greenxertz");
    public static final TagKey<Biome> NYXARA = habitat("nyxara");
    public static final TagKey<Biome> ASTEROID = habitat("asteroid");
    public static final TagKey<Biome> WRECKS = habitat("wrecks");
    public static final TagKey<Biome> DEEP_SPACE = habitat("deep_space");
    public static final TagKey<Biome> GAS_GIANT = habitat("gas_giant");
    /** Overworld dark forests (and the pale garden where it exists): the overworld moth habitat. */
    public static final TagKey<Biome> OVERWORLD_DARK_FOREST = habitat("overworld_dark_forest");
    /** Every overworld biome ({@code #minecraft:is_overworld}): the overworld cave and storm rules. */
    public static final TagKey<Biome> OVERWORLD = habitat("overworld");

    private HabitatTags() {
    }

    private static TagKey<Biome> habitat(String world) {
        return TagKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "habitat/" + world));
    }
}
