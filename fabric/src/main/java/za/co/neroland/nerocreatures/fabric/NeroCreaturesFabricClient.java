package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.client.ApexAltarRenderer;
import za.co.neroland.nerocreatures.client.HabitatDisplayRenderer;
import za.co.neroland.nerocreatures.client.ClientEntityRenderers;
import za.co.neroland.nerocreatures.client.GalleryCaptureHarness;
import za.co.neroland.nerocreatures.network.CreatureNetwork;
import za.co.neroland.nerocreatures.registry.ModBlockEntities;

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
        // The Apex Altar's floating sigil (culled; see ApexAltarRenderer). Vanilla's registry, made
        // accessible by Fabric API's transitive access widener.
        BlockEntityRenderers.register(ModBlockEntities.APEX_ALTAR.get(), ApexAltarRenderer::new);
        // Incubator egg, Terrarium capture, Trophy Plaque trophy: one shared culled display renderer.
        BlockEntityRenderers.register(ModBlockEntities.INCUBATOR.get(), HabitatDisplayRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.TERRARIUM.get(), HabitatDisplayRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.TROPHY_PLAQUE.get(), HabitatDisplayRenderer::new);
        // Incubator and Terrarium glass panes are cutout. 26.x picks the chunk layer from the sprite's
        // transparency by itself; 1.21.x Fabric needs the block registered (NeoForge/Forge read the
        // model's render_type instead).
        //? if <1.21.6 {
        /*net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(
                net.minecraft.client.renderer.RenderType.cutout(),
                za.co.neroland.nerocreatures.registry.ModBlocks.INCUBATOR.get(),
                za.co.neroland.nerocreatures.registry.ModBlocks.TERRARIUM.get());
        *///?} else if <26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlocks(
                net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT,
                za.co.neroland.nerocreatures.registry.ModBlocks.INCUBATOR.get(),
                za.co.neroland.nerocreatures.registry.ModBlocks.TERRARIUM.get());
        *///?}

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
