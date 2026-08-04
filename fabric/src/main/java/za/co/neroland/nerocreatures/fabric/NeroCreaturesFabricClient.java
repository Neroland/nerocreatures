package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.client.ClientEntityRenderers;
import za.co.neroland.nerocreatures.client.GalleryCaptureHarness;
import za.co.neroland.nerocreatures.network.CreatureNetwork;

/** Fabric client entry point for NeroCreatures. */
public final class NeroCreaturesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Fabric client bootstrap");
        // Clientbound receivers (client-only API) — registered here, off the dedicated server.
        FabricCreatureNetwork.registerClient();

        // Creature renderers, through the shared client seam (Fabric's registry is the sink).
        ClientEntityRenderers.registerAll(new ClientEntityRenderers.Sink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type,
                    EntityRendererProvider<E> provider) {
                EntityRendererRegistry.register(type, provider);
            }
        });

        // Drop any synced mirror caches on leaving a world/server, so one session's state can never
        // be shown in the next (or on a server that does not run NeroCreatures).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CreatureNetwork.clearClientCaches());

        // The screenshot harness's pump — counterpart to NeoForge's ClientTickEvent.Post.
        ClientTickEvents.END_CLIENT_TICK.register(client -> GalleryCaptureHarness.tick());
        // Client-side /ncgallery capture tree. Its own dispatcher, and deliberately NOT rooted at
        // "nerocreatures": a shared root would shadow the server-side /nerocreatures tree.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                GalleryCaptureHarness.registerClientCommands(dispatcher));
    }
}
