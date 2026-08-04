package za.co.neroland.nerocreatures.neoforge;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import za.co.neroland.nerocreatures.client.ClientEntityRenderers;
import za.co.neroland.nerocreatures.client.GalleryCaptureHarness;

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
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientEntityRenderers.registerAll(new ClientEntityRenderers.Sink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type,
                    EntityRendererProvider<E> provider) {
                event.registerEntityRenderer(type, provider);
            }
        });
    }
}
