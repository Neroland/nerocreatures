package za.co.neroland.nerocreatures.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.ApexAltarBlock;
import za.co.neroland.nerocreatures.block.EphemeralCrustBlock;
import za.co.neroland.nerocreatures.block.NestBlock;
import za.co.neroland.nerocreatures.block.FeedingTroughBlock;
import za.co.neroland.nerocreatures.block.IncubatorBlock;
import za.co.neroland.nerocreatures.block.LureBeaconBlock;
import za.co.neroland.nerocreatures.block.SonicWardBlock;
import za.co.neroland.nerocreatures.block.TerrariumBlock;
import za.co.neroland.nerocreatures.block.TrophyPlaqueBlock;

/**
 * NeroCreatures' blocks (at most ten). Same Core {@link RegistrationProvider} seam as
 * the items, so Fabric registers eagerly at {@link #init()} and NeoForge/Forge attach the deferred
 * register through the existing {@code RegistrationProvider.attach(..)} call.
 *
 * <p>{@link #EPHEMERAL_CRUST} has no item, no loot and no block entity: creature and boss code finds
 * it by id through {@link EphemeralCrustBlock#registered()}, so nothing outside this class names the
 * registry entry. The eight habitat blocks live here too.
 */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroCreaturesCommon.MOD_ID);

    /** The self-removing temporary block: Magma Wader slag, Rime Matriarch ice, Facet Colossus prisms. */
    public static final RegistryEntry<EphemeralCrustBlock> EPHEMERAL_CRUST = BLOCKS.register(
            "ephemeral_crust",
            key -> new EphemeralCrustBlock(EphemeralCrustBlock.crustProperties().setId(key)));

    /**
     * The Apex Altar: a boss sigil used on it summons that boss as a contribution-tracked fight. Its
     * block item is {@code ModItems.APEX_ALTAR}; its block entity is {@code ModBlockEntities.APEX_ALTAR}.
     */
    public static final RegistryEntry<ApexAltarBlock> APEX_ALTAR = BLOCKS.register(
            "apex_altar", key -> new ApexAltarBlock(ApexAltarBlock.altarProperties().setId(key)));

    /** Habitat blocks. */
    public static final RegistryEntry<NestBlock> NEST = BLOCKS.register("nest",
            key -> new NestBlock(NestBlock.nestProperties().setId(key)));
    public static final RegistryEntry<FeedingTroughBlock> FEEDING_TROUGH = BLOCKS.register("feeding_trough",
            key -> new FeedingTroughBlock(FeedingTroughBlock.troughProperties().setId(key)));
    public static final RegistryEntry<IncubatorBlock> INCUBATOR = BLOCKS.register("incubator",
            key -> new IncubatorBlock(IncubatorBlock.incubatorProperties().setId(key)));
    public static final RegistryEntry<LureBeaconBlock> LURE_BEACON = BLOCKS.register("lure_beacon",
            key -> new LureBeaconBlock(LureBeaconBlock.fieldProperties().setId(key)));
    public static final RegistryEntry<SonicWardBlock> SONIC_WARD = BLOCKS.register("sonic_ward",
            key -> new SonicWardBlock(SonicWardBlock.fieldProperties().setId(key)));
    public static final RegistryEntry<TerrariumBlock> TERRARIUM = BLOCKS.register("terrarium",
            key -> new TerrariumBlock(TerrariumBlock.terrariumProperties().setId(key)));
    public static final RegistryEntry<TrophyPlaqueBlock> TROPHY_PLAQUE = BLOCKS.register("trophy_plaque",
            key -> new TrophyPlaqueBlock(TrophyPlaqueBlock.plaqueProperties().setId(key)));

    private ModBlocks() {
    }

    /** Forces this class to initialise (and therefore its blocks to register on Fabric). */
    public static void init() {
    }
}
