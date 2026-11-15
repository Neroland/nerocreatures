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
 * model's {@code ART}. Since 0.5.0 that is every registered creature; {@link CreatureArt#legacy}
 * stays for a future creature whose art has not landed yet.
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
        // Every creature with a spec (art/models/<id>.json) renders with its generated model's ART.
        sink.register(ModEntities.VOID_CRAWLER.get(), context -> new CreatureRenderer(context,
                new VoidCrawlerModel(VoidCrawlerModel.createBodyLayer().bakeRoot()), VoidCrawlerModel.ART));
        // Generated from art/models/lunar_stalker.json: geometry, rig and ART all come from the spec.
        sink.register(ModEntities.LUNAR_STALKER.get(), context -> new CreatureRenderer(context,
                new LunarStalkerModel(LunarStalkerModel.createBodyLayer().bakeRoot()), LunarStalkerModel.ART));
        sink.register(ModEntities.ASTEROID_WORM.get(), context -> new CreatureRenderer(context,
                new AsteroidWormModel(AsteroidWormModel.createBodyLayer().bakeRoot()), AsteroidWormModel.ART));
        sink.register(ModEntities.PLASMA_SLIME.get(), context -> new CreatureRenderer(context,
                new PlasmaSlimeModel(PlasmaSlimeModel.createBodyLayer().bakeRoot()), PlasmaSlimeModel.ART));
        sink.register(ModEntities.CRYSTAL_GOLEM.get(), context -> new CreatureRenderer(context,
                new CrystalGolemModel(CrystalGolemModel.createBodyLayer().bakeRoot()), CrystalGolemModel.ART));
        sink.register(ModEntities.SPACE_PIRATE.get(), context -> new CreatureRenderer(context,
                new SpacePirateModel(SpacePirateModel.createBodyLayer().bakeRoot()), SpacePirateModel.ART));
        sink.register(ModEntities.ROGUE_DRONE.get(), context -> new CreatureRenderer(context,
                new RogueDroneModel(RogueDroneModel.createBodyLayer().bakeRoot()), RogueDroneModel.ART));
        sink.register(ModEntities.ROGUE_ANDROID.get(), context -> new CreatureRenderer(context,
                new RogueAndroidModel(RogueAndroidModel.createBodyLayer().bakeRoot()), RogueAndroidModel.ART));
        sink.register(ModEntities.GLACITE_WISP.get(), context -> new CreatureRenderer(context,
                new GlaciteWispModel(GlaciteWispModel.createBodyLayer().bakeRoot()), GlaciteWispModel.ART));
        sink.register(ModEntities.XERTZ_FORAGER.get(), context -> new CreatureRenderer(context,
                new XertzForagerModel(XertzForagerModel.createBodyLayer().bakeRoot()), XertzForagerModel.ART));
        sink.register(ModEntities.TERRAFORMING_DRONE.get(), context -> new CreatureRenderer(context,
                new TerraformingDroneModel(TerraformingDroneModel.createBodyLayer().bakeRoot()), TerraformingDroneModel.ART));
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

        // 0.5.0 Glacira, Greenxertz and Nyxara.
        sink.register(ModEntities.RIME_YAK.get(), context -> new CreatureRenderer(context,
                new RimeYakModel(RimeYakModel.createBodyLayer().bakeRoot()), RimeYakModel.ART));
        sink.register(ModEntities.FROST_LURKER.get(), context -> new CreatureRenderer(context,
                new FrostLurkerModel(FrostLurkerModel.createBodyLayer().bakeRoot()), FrostLurkerModel.ART));
        sink.register(ModEntities.SPORE_DRIFTER.get(), context -> new CreatureRenderer(context,
                new SporeDrifterModel(SporeDrifterModel.createBodyLayer().bakeRoot()), SporeDrifterModel.ART));
        sink.register(ModEntities.BLOOM_HOST.get(), context -> new CreatureRenderer(context,
                new BloomHostModel(BloomHostModel.createBodyLayer().bakeRoot()), BloomHostModel.ART));
        sink.register(ModEntities.MOSSBACK.get(), context -> new CreatureRenderer(context,
                new MossbackModel(MossbackModel.createBodyLayer().bakeRoot()), MossbackModel.ART));
        sink.register(ModEntities.DRIFT_LEVIATHAN.get(), context -> new CreatureRenderer(context,
                new DriftLeviathanModel(DriftLeviathanModel.createBodyLayer().bakeRoot()), DriftLeviathanModel.ART));
        sink.register(ModEntities.PRISM_STAG.get(), context -> new CreatureRenderer(context,
                new PrismStagModel(PrismStagModel.createBodyLayer().bakeRoot()), PrismStagModel.ART));
        sink.register(ModEntities.SHARDBACK.get(), context -> new CreatureRenderer(context,
                new ShardbackModel(ShardbackModel.createBodyLayer().bakeRoot()), ShardbackModel.ART));

        // 0.5.0 Asteroid, Wrecks and Pirates, Deep space.
        sink.register(ModEntities.MAGNET_CRAB.get(), context -> new CreatureRenderer(context,
                new MagnetCrabModel(MagnetCrabModel.createBodyLayer().bakeRoot()), MagnetCrabModel.ART));
        sink.register(ModEntities.GRAVITY_LEECH.get(), context -> new CreatureRenderer(context,
                new GravityLeechModel(GravityLeechModel.createBodyLayer().bakeRoot()), GravityLeechModel.ART));
        sink.register(ModEntities.CACHE_MIMIC.get(), context -> new CreatureRenderer(context,
                new CacheMimicModel(CacheMimicModel.createBodyLayer().bakeRoot()), CacheMimicModel.ART));
        sink.register(ModEntities.STATIC_SWARM.get(), context -> new CreatureRenderer(context,
                new StaticSwarmModel(StaticSwarmModel.createBodyLayer().bakeRoot()), StaticSwarmModel.ART));
        sink.register(ModEntities.SCRAP_HULK.get(), context -> new CreatureRenderer(context,
                new ScrapHulkModel(ScrapHulkModel.createBodyLayer().bakeRoot()), ScrapHulkModel.ART));
        sink.register(ModEntities.PIRATE_CAPTAIN.get(), context -> new CreatureRenderer(context,
                new PirateCaptainModel(PirateCaptainModel.createBodyLayer().bakeRoot()), PirateCaptainModel.ART));
        sink.register(ModEntities.VOID_MANTA.get(), context -> new CreatureRenderer(context,
                new VoidMantaModel(VoidMantaModel.createBodyLayer().bakeRoot()), VoidMantaModel.ART));
    }
}
