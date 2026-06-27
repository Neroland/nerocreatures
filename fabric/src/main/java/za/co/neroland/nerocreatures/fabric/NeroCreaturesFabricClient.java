package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** Fabric client entry point for NeroCreatures. */
public final class NeroCreaturesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Fabric client bootstrap");
    }
}
