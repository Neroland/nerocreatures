package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** Fabric entry point for NeroCreatures. */
public final class NeroCreaturesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Fabric bootstrap");
        NeroCreaturesCommon.init();
    }
}
