package za.co.neroland.nerocreatures.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
        // Common init created NeroCreatures' DeferredRegisters through Core's registration seam;
        // this attaches them to OUR mod bus group.
        RegistrationProvider.attach(context.getModBusGroup());
        // Core installs its own flush listeners for entity attributes and spawn placements, so this
        // is belt-and-braces parity with the line above rather than a requirement. It is idempotent.
        EntityRegistrationSupport.attach(context.getModBusGroup());
        ForgeCreatureNetwork.register();
        ForgeCreatureEvents.register();
    }
}
