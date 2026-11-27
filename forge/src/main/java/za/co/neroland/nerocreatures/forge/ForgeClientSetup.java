package za.co.neroland.nerocreatures.forge;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
//? if >=1.20.5 {
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
//?} else {
/*import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
*///?}
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.TickEvent;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}

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
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(ClientBestiary.OPEN_BESTIARY));
        AddGuiOverlayLayersEvent.BUS.addListener(ForgeClientSetup::onAddGuiLayers);
        RegisterParticleProvidersEvent.BUS.addListener(ForgeClientSetup::onRegisterParticles);
        ClientBestiary.install();
    }
    //?} else if >=1.21.6 {
    /*// Forge 1.21.6 - 1.21.8: renderer registration is still a mod-bus event.
    public static void init(net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        EntityRenderersEvent.RegisterRenderers.getBus(modBus).addListener(ForgeClientSetup::onRegisterEntityRenderers);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ForgeClientSetup::onClientTick);
        RegisterClientCommandsEvent.BUS.addListener(ForgeClientSetup::onRegisterClientCommands);
        RegisterKeyMappingsEvent.getBus(modBus).addListener(event -> event.register(ClientBestiary.OPEN_BESTIARY));
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(ForgeClientSetup::onAddGuiLayers);
        RegisterParticleProvidersEvent.getBus(modBus).addListener(ForgeClientSetup::onRegisterParticles);
        ClientBestiary.install();
    }
    *///?} else {
    /*// Forge before 1.21.6 (EventBus 6): renderer registration is a mod-bus event, the rest are on the
    // single game bus.
    public static void init(IEventBus modBus) {
        modBus.addListener(ForgeClientSetup::onRegisterEntityRenderers);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientSetup::onRegisterClientCommands);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(ClientBestiary.OPEN_BESTIARY));
        modBus.addListener(ForgeClientSetup::onAddGuiLayers);
        modBus.addListener(ForgeClientSetup::onRegisterParticles);
        ClientBestiary.install();
    }
    *///?}

    //? if >=1.20.5 {
    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
    //?} else {
    /*private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
    *///?}
        GalleryCaptureHarness.tick();
        ClientBestiary.tick();
    }

    /** The Field Scanner HUD indicator, on top of the vanilla HUD. */
    //? if >=1.20.5 {
    private static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(ScannerHud.LAYER_ID, (graphics, delta) -> ScannerHud.render(graphics));
    }
    //?} else {
    /*private static void onAddGuiLayers(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(ScannerHud.LAYER_ID.getPath(),
                (gui, graphics, delta, width, height) -> ScannerHud.render(graphics));
    }
    *///?}

    /** The 16 custom particles: one sprite-set provider per type. */
    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        CreatureParticles.registerAll((type, id) ->
                event.registerSpriteSet(type, sprites -> new CreatureParticle.Provider(sprites, id)));
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
        // The Apex Altar's floating sigil (culled; see ApexAltarRenderer).
        event.registerBlockEntityRenderer(ModBlockEntities.APEX_ALTAR.get(), ApexAltarRenderer::new);
        // Incubator egg, Terrarium capture, Trophy Plaque trophy: one shared culled display renderer.
        event.registerBlockEntityRenderer(ModBlockEntities.INCUBATOR.get(), HabitatDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TERRARIUM.get(), HabitatDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TROPHY_PLAQUE.get(), HabitatDisplayRenderer::new);
    }
}
