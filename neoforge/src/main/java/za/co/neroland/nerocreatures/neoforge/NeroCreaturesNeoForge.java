package za.co.neroland.nerocreatures.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerolandcore.entity.EntityRegistrationSupport;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** NeoForge entry point for NeroCreatures. */
@Mod(NeroCreaturesCommon.MOD_ID)
public final class NeroCreaturesNeoForge {

    public NeroCreaturesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] NeoForge bootstrap");
        // Common init declares the payloads and creates the deferred registrations; the calls below
        // consume those declarations.
        NeroCreaturesCommon.init();
        // Common init created NeroCreatures' DeferredRegisters through Core's registration seam;
        // this attaches them to OUR mod event bus.
        RegistrationProvider.attach(modEventBus);
        // Core installs its own flush listeners for entity attributes and spawn placements, so this
        // is belt-and-braces parity with the line above rather than a requirement. It is idempotent.
        EntityRegistrationSupport.attach(modEventBus);
        NeoForgeCreatureNetwork.register(modEventBus);
        NeoForgeCreatureEvents.register();
    }
}
