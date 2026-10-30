package za.co.neroland.nerocreatures.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.eventbus.api.IEventBus;
*///?}

import za.co.neroland.nerolandcore.entity.EntityRegistrationSupport;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** MinecraftForge entry point for NeroCreatures. */
@Mod(NeroCreaturesCommon.MOD_ID)
public final class NeroCreaturesForge {

    public NeroCreaturesForge(FMLJavaModLoadingContext context) {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Forge bootstrap");
        // Common init declares the payloads; the channel below is sealed the moment it is built,
        // so that ordering is mandatory on Forge.
        NeroCreaturesCommon.init();
        //? if >=1.21.6 {
        BusGroup modBus = context.getModBusGroup();
        //?} else {
        /*IEventBus modBus = context.getModEventBus();
        *///?}
        // Common init created NeroCreatures' DeferredRegisters through Core's registration seam;
        // this attaches them to OUR mod bus group.
        RegistrationProvider.attach(modBus);
        // Core installs its own flush listeners for entity attributes and spawn placements, so this
        // is belt-and-braces parity with the line above rather than a requirement. It is idempotent.
        EntityRegistrationSupport.attach(modBus);
        ForgeCreatureNetwork.register();
        ForgeCreatureEvents.register();
        // Client-only: renderers. Gated so the client classes never load on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            //? if >=1.21.9 {
            ForgeClientSetup.init();
            //?} else {
            /*ForgeClientSetup.init(modBus);
            *///?}
        }
    }
}
