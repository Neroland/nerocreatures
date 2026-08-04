package za.co.neroland.nerocreatures.forge;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;

import za.co.neroland.nerocreatures.client.ClientEntityRenderers;
import za.co.neroland.nerocreatures.client.GalleryCaptureHarness;

/**
 * Forge client-only wiring. Nothing here runs on a dedicated server: the entry point calls
 * {@link #init()} only when the dist is {@code CLIENT}, so the client classes are never even loaded
 * server-side.
 *
 * <p>Forge's renderer event lives on its own static {@code BUS} rather than the mod bus group, which
 * is why this takes no bus argument (unlike the NeoForge twin).
 */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    public static void init() {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterEntityRenderers);
        // The screenshot harness: its per-tick pump, and its own client-side command tree. The root
        // is /ncgallery, NOT /nerocreatures — a client root sharing the server root's name shadows
        // the server subtree (see GalleryCaptureHarness).
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> GalleryCaptureHarness.tick());
        RegisterClientCommandsEvent.BUS.addListener(event ->
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
