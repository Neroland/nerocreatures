package za.co.neroland.nerocreatures.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * NeroCreatures' cross-loader client seam — the one place a creature's renderer is named.
 *
 * <p>The renderer set is identical on all three loaders, but the registration <em>call</em> is not:
 * NeoForge and Forge each have their own {@code EntityRenderersEvent.RegisterRenderers}, Fabric has
 * {@code EntityRendererRegistry}. So the list lives here once and each loader passes in its own
 * {@link Sink}. This mirrors Nerospace's proven arrangement rather than inventing a second shape for
 * the same problem.
 *
 * <p><b>Models are baked directly</b> via {@code createBodyLayer().bakeRoot()} instead of being
 * registered with a model-layer registry. Fabric's {@code EntityModelLayerRegistry} is not on this
 * de-obfuscated classpath, and a baked-at-construction model needs no registry on any loader — the
 * cost is that a resource-pack reload does not rebuild the geometry, which is irrelevant for
 * geometry defined in Java.
 *
 * <p>A creature whose spec has landed ({@code art/models/<id>.json}) is registered with its generated
 * model's {@code ART}; the rest use {@link CreatureArt#legacy} until their art arrives.
 *
 * <p>Called only from the client entry points; a dedicated server never touches this class.
 */
public final class ClientEntityRenderers {

    /** A loader's renderer-registration entry point. */
    public interface Sink {
        <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider);
    }

    private ClientEntityRenderers() {
    }

    public static void registerAll(Sink sink) {
        sink.register(ModEntities.VOID_CRAWLER.get(), context -> new CreatureRenderer(context,
                new VoidCrawlerModel(VoidCrawlerModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("void_crawler", 1.0F, 0.6F)));
        // Generated from art/models/lunar_stalker.json: geometry, rig and ART all come from the spec.
        sink.register(ModEntities.LUNAR_STALKER.get(), context -> new CreatureRenderer(context,
                new LunarStalkerModel(LunarStalkerModel.createBodyLayer().bakeRoot()), LunarStalkerModel.ART));
        sink.register(ModEntities.ASTEROID_WORM.get(), context -> new CreatureRenderer(context,
                new AsteroidWormModel(AsteroidWormModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("asteroid_worm", 1.0F, 1.0F)));
        sink.register(ModEntities.PLASMA_SLIME.get(), context -> new CreatureRenderer(context,
                new PlasmaSlimeModel(PlasmaSlimeModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("plasma_slime", 1.0F, 0.5F)));
        sink.register(ModEntities.CRYSTAL_GOLEM.get(), context -> new CreatureRenderer(context,
                new CrystalGolemModel(CrystalGolemModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("crystal_golem", 1.0F, 0.8F)));
        sink.register(ModEntities.SPACE_PIRATE.get(), context -> new CreatureRenderer(context,
                new SpacePirateModel(SpacePirateModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("space_pirate", 1.0F, 0.5F)));
        sink.register(ModEntities.ROGUE_DRONE.get(), context -> new CreatureRenderer(context,
                new RogueDroneModel(RogueDroneModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("rogue_drone", 1.0F, 0.4F)));
        sink.register(ModEntities.ROGUE_ANDROID.get(), context -> new CreatureRenderer(context,
                new RogueAndroidModel(RogueAndroidModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("rogue_android", 1.0F, 0.8F)));
        sink.register(ModEntities.GLACITE_WISP.get(), context -> new CreatureRenderer(context,
                new GlaciteWispModel(GlaciteWispModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("glacite_wisp", 1.0F, 0.3F)));
        sink.register(ModEntities.XERTZ_FORAGER.get(), context -> new CreatureRenderer(context,
                new XertzForagerModel(XertzForagerModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("xertz_forager", 1.0F, 0.4F)));
        sink.register(ModEntities.TERRAFORMING_DRONE.get(), context -> new CreatureRenderer(context,
                new TerraformingDroneModel(TerraformingDroneModel.createBodyLayer().bakeRoot()),
                CreatureArt.legacy("terraforming_drone", 1.0F, 0.5F)));
        // The boss. Same renderer, same shared render state, bigger everything — the fight's state
        // reaches the player through the boss bar, sound and particles, never through the model.
        sink.register(ModEntities.CINDER_TYRANT.get(), context -> new CreatureRenderer(context,
                new CinderTyrantModel(CinderTyrantModel.createBodyLayer().bakeRoot()), CinderTyrantModel.ART));

        // 0.5.0 Luna, Mars and Cindara: every model generated from art/models/<id>.json.
        sink.register(ModEntities.LUMEN_MOTH.get(), context -> new CreatureRenderer(context,
                new LumenMothModel(LumenMothModel.createBodyLayer().bakeRoot()), LumenMothModel.ART));
        sink.register(ModEntities.ECHO_SHRIKE.get(), context -> new CreatureRenderer(context,
                new EchoShrikeModel(EchoShrikeModel.createBodyLayer().bakeRoot()), EchoShrikeModel.ART));
        sink.register(ModEntities.LODE_TORTOISE.get(), context -> new CreatureRenderer(context,
                new LodeTortoiseModel(LodeTortoiseModel.createBodyLayer().bakeRoot()), LodeTortoiseModel.ART));
        sink.register(ModEntities.DUNE_LOPER.get(), context -> new CreatureRenderer(context,
                new DuneLoperModel(DuneLoperModel.createBodyLayer().bakeRoot()), DuneLoperModel.ART));
        sink.register(ModEntities.STRATA_HOUND.get(), context -> new CreatureRenderer(context,
                new StrataHoundModel(StrataHoundModel.createBodyLayer().bakeRoot()), StrataHoundModel.ART));
        sink.register(ModEntities.DUST_DEVIL.get(), context -> new CreatureRenderer(context,
                new DustDevilModel(DustDevilModel.createBodyLayer().bakeRoot()), DustDevilModel.ART));
        sink.register(ModEntities.SLAG_BEETLE.get(), context -> new CreatureRenderer(context,
                new SlagBeetleModel(SlagBeetleModel.createBodyLayer().bakeRoot()), SlagBeetleModel.ART));
        sink.register(ModEntities.MAGMA_WADER.get(), context -> new CreatureRenderer(context,
                new MagmaWaderModel(MagmaWaderModel.createBodyLayer().bakeRoot()), MagmaWaderModel.ART));
    }
}
