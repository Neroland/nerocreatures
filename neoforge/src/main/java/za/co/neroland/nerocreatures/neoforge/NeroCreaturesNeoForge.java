package za.co.neroland.nerocreatures.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** NeoForge entry point for NeroCreatures. */
@Mod(NeroCreaturesCommon.MOD_ID)
public final class NeroCreaturesNeoForge {

    public NeroCreaturesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] NeoForge bootstrap");
        NeroCreaturesCommon.init();
    }
}
