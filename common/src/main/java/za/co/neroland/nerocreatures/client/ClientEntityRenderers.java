package za.co.neroland.nerocreatures.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
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
 * programmer art defined in Java.
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
                texture("void_crawler"), 1.0F, 0.6F));
        sink.register(ModEntities.LUNAR_STALKER.get(), context -> new CreatureRenderer(context,
                new LunarStalkerModel(LunarStalkerModel.createBodyLayer().bakeRoot()),
                texture("lunar_stalker"), 1.0F, 0.6F));
        sink.register(ModEntities.ASTEROID_WORM.get(), context -> new CreatureRenderer(context,
                new AsteroidWormModel(AsteroidWormModel.createBodyLayer().bakeRoot()),
                texture("asteroid_worm"), 1.0F, 1.0F));
        sink.register(ModEntities.PLASMA_SLIME.get(), context -> new CreatureRenderer(context,
                new PlasmaSlimeModel(PlasmaSlimeModel.createBodyLayer().bakeRoot()),
                texture("plasma_slime"), 1.0F, 0.5F));
        sink.register(ModEntities.CRYSTAL_GOLEM.get(), context -> new CreatureRenderer(context,
                new CrystalGolemModel(CrystalGolemModel.createBodyLayer().bakeRoot()),
                texture("crystal_golem"), 1.0F, 0.8F));
        sink.register(ModEntities.SPACE_PIRATE.get(), context -> new CreatureRenderer(context,
                new SpacePirateModel(SpacePirateModel.createBodyLayer().bakeRoot()),
                texture("space_pirate"), 1.0F, 0.5F));
        sink.register(ModEntities.ROGUE_DRONE.get(), context -> new CreatureRenderer(context,
                new RogueDroneModel(RogueDroneModel.createBodyLayer().bakeRoot()),
                texture("rogue_drone"), 1.0F, 0.4F));
        sink.register(ModEntities.ROGUE_ANDROID.get(), context -> new CreatureRenderer(context,
                new RogueAndroidModel(RogueAndroidModel.createBodyLayer().bakeRoot()),
                texture("rogue_android"), 1.0F, 0.8F));
        sink.register(ModEntities.GLACITE_WISP.get(), context -> new CreatureRenderer(context,
                new GlaciteWispModel(GlaciteWispModel.createBodyLayer().bakeRoot()),
                texture("glacite_wisp"), 1.0F, 0.3F));
        sink.register(ModEntities.XERTZ_FORAGER.get(), context -> new CreatureRenderer(context,
                new XertzForagerModel(XertzForagerModel.createBodyLayer().bakeRoot()),
                texture("xertz_forager"), 1.0F, 0.4F));
        sink.register(ModEntities.TERRAFORMING_DRONE.get(), context -> new CreatureRenderer(context,
                new TerraformingDroneModel(TerraformingDroneModel.createBodyLayer().bakeRoot()),
                texture("terraforming_drone"), 1.0F, 0.5F));
        // The boss. Same renderer, same shared render state, bigger everything — the fight's state
        // reaches the player through the boss bar, sound and particles, never through the model.
        sink.register(ModEntities.CINDER_TYRANT.get(), context -> new CreatureRenderer(context,
                new CinderTyrantModel(CinderTyrantModel.createBodyLayer().bakeRoot()),
                texture("cinder_tyrant"), 1.15F, 1.6F));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID,
                "textures/entity/" + name + ".png");
    }
}
