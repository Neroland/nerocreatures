package za.co.neroland.nerocreatures.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.EphemeralCrustBlock;

/**
 * NeroCreatures' blocks (decision 001: at most ten). Same Core {@link RegistrationProvider} seam as
 * the items, so Fabric registers eagerly at {@link #init()} and NeoForge/Forge attach the deferred
 * register through the existing {@code RegistrationProvider.attach(..)} call.
 *
 * <p>{@link #EPHEMERAL_CRUST} has no item, no loot and no block entity: creature and boss code finds
 * it by id through {@link EphemeralCrustBlock#registered()}, so nothing outside this class names the
 * registry entry. The eight habitat blocks (Stage 4) land here too.
 */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroCreaturesCommon.MOD_ID);

    /** The self-removing temporary block: Magma Wader slag, Rime Matriarch ice, Facet Colossus prisms. */
    public static final RegistryEntry<EphemeralCrustBlock> EPHEMERAL_CRUST = BLOCKS.register(
            "ephemeral_crust",
            key -> new EphemeralCrustBlock(EphemeralCrustBlock.crustProperties().setId(key)));

    private ModBlocks() {
    }

    /** Forces this class to initialise (and therefore its blocks to register on Fabric). */
    public static void init() {
    }
}
