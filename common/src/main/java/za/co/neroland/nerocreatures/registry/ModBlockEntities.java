package za.co.neroland.nerocreatures.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.ApexAltarBlockEntity;

/**
 * NeroCreatures' block-entity types. Same Core {@link RegistrationProvider} seam as the blocks;
 * registered after {@link ModBlocks} because a type names the blocks it is valid on.
 *
 * <p>The altar's id must stay {@code nerocreatures:apex_altar}: {@link ApexAltarBlockEntity#type()}
 * looks the type up by {@link ApexAltarBlockEntity#ID}.
 */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroCreaturesCommon.MOD_ID);

    /** The Apex Altar's cooldown, fight id and sigil kind (no inventory, no menu). */
    public static final RegistryEntry<BlockEntityType<ApexAltarBlockEntity>> APEX_ALTAR = BLOCK_ENTITIES.register(
            "apex_altar", key -> BlockEntityTypeCompat.create(ApexAltarBlockEntity::new, ModBlocks.APEX_ALTAR.get()));

    private ModBlockEntities() {
    }

    /** Forces this class to initialise (and therefore its types to register on Fabric). */
    public static void init() {
    }
}
