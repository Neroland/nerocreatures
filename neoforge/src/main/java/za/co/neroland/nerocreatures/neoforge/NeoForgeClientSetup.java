package za.co.neroland.nerocreatures.neoforge;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

import za.co.neroland.nerocreatures.client.ApexAltarRenderer;
import za.co.neroland.nerocreatures.client.HabitatDisplayRenderer;
import za.co.neroland.nerocreatures.client.ClientEntityRenderers;
import za.co.neroland.nerocreatures.client.GalleryCaptureHarness;
import za.co.neroland.nerocreatures.client.bestiary.ClientBestiary;
import za.co.neroland.nerocreatures.client.bestiary.ScannerHud;
import za.co.neroland.nerocreatures.client.particle.CreatureParticle;
import za.co.neroland.nerocreatures.client.particle.CreatureParticles;
import za.co.neroland.nerocreatures.registry.ModBlockEntities;

/**
 * NeoForge client-only wiring. Nothing here runs on a dedicated server: the entry point calls
 * {@link #init(IEventBus)} only when the dist is {@code CLIENT}, so the client classes are never
 * even loaded server-side.
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeClientSetup::onRegisterEntityRenderers);
        // The screenshot harness: its per-tick pump on the game bus, and its own client-side command
        // tree. The root is /ncgallery, NOT /nerocreatures — a client root sharing the server root's
        // name shadows the server subtree (see GalleryCaptureHarness).
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> GalleryCaptureHarness.tick());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                GalleryCaptureHarness.registerClientCommands(event.getDispatcher()));
        // Field Scanner and Bestiary: client seams, the open-Bestiary key (its category registers in
        // ClientBestiary's static initialiser), its tick, and the scanner HUD layer.
        ClientBestiary.install();
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(ClientBestiary.OPEN_BESTIARY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientBestiary.tick());
        modEventBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAboveAll(ScannerHud.LAYER_ID, (graphics, delta) -> ScannerHud.render(graphics)));
        // The 16 custom particles: one sprite-set provider per type.
        modEventBus.addListener((RegisterParticleProvidersEvent event) -> CreatureParticles.registerAll((type, id) ->
                event.registerSpriteSet(type, sprites -> new CreatureParticle.Provider(sprites, id))));
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientEntityRenderers.registerAll(new ClientEntityRenderers.Sink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type,
                    EntityRendererProvider<E> provider) {
                event.registerEntityRenderer(type, provider);
            }
        });
        // The Apex Altar's floating sigil (culled; see ApexAltarRenderer).
        event.registerBlockEntityRenderer(ModBlockEntities.APEX_ALTAR.get(), ApexAltarRenderer::new);
        // Incubator egg, Terrarium capture, Trophy Plaque trophy: one shared culled display renderer.
        event.registerBlockEntityRenderer(ModBlockEntities.INCUBATOR.get(), HabitatDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TERRARIUM.get(), HabitatDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TROPHY_PLAQUE.get(), HabitatDisplayRenderer::new);
    }
}
