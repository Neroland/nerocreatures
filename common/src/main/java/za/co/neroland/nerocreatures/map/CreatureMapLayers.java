package za.co.neroland.nerocreatures.map;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;

/**
 * NeroCreatures' map layers for any Neroland map consumer (Neroland Core's map API). One layer,
 * {@code nerocreatures:my_creatures}: the viewer's own pets, mounts and drones that are loaded right now, located
 * from the live entity. Nothing is stored for it; NeroCreatures still keeps no coordinates.
 *
 * <p>Lairs and habitats are not a layer: the bestiary records no locations. Apex altars are not a layer either.
 */
public final class CreatureMapLayers {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeroCreatures/Map");

    public static final Identifier MY_CREATURES = id("my_creatures");
    static final Identifier ICON = id("my_creature");

    private CreatureMapLayers() {
    }

    /** Registers the provider with Core. Called once from {@code NeroCreaturesCommon.init()}. */
    public static void register() {
        try {
            MapLayerRegistry.register(new CreatureMapLayerProvider(CreatureMapSource.SERVER));
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("NeroCreatures map layers not registered ({})", e.getClass().getSimpleName());
        }
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, path);
    }
}
