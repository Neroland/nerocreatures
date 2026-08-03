package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** Fabric entry point for NeroCreatures. */
public final class NeroCreaturesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Fabric bootstrap");
        // Common init declares the payloads (and, on Fabric, registers content eagerly); the
        // registration below consumes that declaration. Core's RegistrationProvider and
        // EntityRegistrationSupport need no attach on Fabric — both apply immediately.
        NeroCreaturesCommon.init();
        FabricCreatureNetwork.registerCommon();
        FabricCreatureEvents.register();
    }
}
