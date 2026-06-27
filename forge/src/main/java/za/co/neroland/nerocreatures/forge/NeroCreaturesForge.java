package za.co.neroland.nerocreatures.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** MinecraftForge entry point for NeroCreatures. */
@Mod(NeroCreaturesCommon.MOD_ID)
public final class NeroCreaturesForge {

    public NeroCreaturesForge(FMLJavaModLoadingContext context) {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Forge bootstrap");
        NeroCreaturesCommon.init();
    }
}
