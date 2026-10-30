package za.co.neroland.nerocreatures.forge;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}

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

    // The screenshot harness: its per-tick pump, and its own client-side command tree. The root
    // is /ncgallery, NOT /nerocreatures — a client root sharing the server root's name shadows
    // the server subtree (see GalleryCaptureHarness).
    //? if >=1.21.9 {
    public static void init() {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterEntityRenderers);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ForgeClientSetup::onClientTick);
        RegisterClientCommandsEvent.BUS.addListener(ForgeClientSetup::onRegisterClientCommands);
    }
    //?} else if >=1.21.6 {
    /*// Forge 1.21.6 - 1.21.8: renderer registration is still a mod-bus event.
    public static void init(net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        EntityRenderersEvent.RegisterRenderers.getBus(modBus).addListener(ForgeClientSetup::onRegisterEntityRenderers);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ForgeClientSetup::onClientTick);
        RegisterClientCommandsEvent.BUS.addListener(ForgeClientSetup::onRegisterClientCommands);
    }
    *///?} else {
    /*// Forge before 1.21.6 (EventBus 6): renderer registration is a mod-bus event, the rest are on the
    // single game bus.
    public static void init(IEventBus modBus) {
        modBus.addListener(ForgeClientSetup::onRegisterEntityRenderers);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientSetup::onRegisterClientCommands);
    }
    *///?}

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        GalleryCaptureHarness.tick();
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        GalleryCaptureHarness.registerClientCommands(event.getDispatcher());
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
