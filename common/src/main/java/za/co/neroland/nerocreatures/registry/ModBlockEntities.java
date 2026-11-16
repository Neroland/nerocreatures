package za.co.neroland.nerocreatures.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.ApexAltarBlockEntity;
import za.co.neroland.nerocreatures.block.FeedingTroughBlockEntity;
import za.co.neroland.nerocreatures.block.IncubatorBlockEntity;
import za.co.neroland.nerocreatures.block.LureBeaconBlockEntity;
import za.co.neroland.nerocreatures.block.SonicWardBlockEntity;
import za.co.neroland.nerocreatures.block.TerrariumBlockEntity;
import za.co.neroland.nerocreatures.block.TrophyPlaqueBlockEntity;

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

    /** Habitat block entities; ids equal the block ids (types are looked up by id). */
    public static final RegistryEntry<BlockEntityType<FeedingTroughBlockEntity>> FEEDING_TROUGH = BLOCK_ENTITIES.register(
            "feeding_trough", key -> BlockEntityTypeCompat.create(FeedingTroughBlockEntity::new, ModBlocks.FEEDING_TROUGH.get()));
    public static final RegistryEntry<BlockEntityType<IncubatorBlockEntity>> INCUBATOR = BLOCK_ENTITIES.register(
            "incubator", key -> BlockEntityTypeCompat.create(IncubatorBlockEntity::new, ModBlocks.INCUBATOR.get()));
    public static final RegistryEntry<BlockEntityType<LureBeaconBlockEntity>> LURE_BEACON = BLOCK_ENTITIES.register(
            "lure_beacon", key -> BlockEntityTypeCompat.create(LureBeaconBlockEntity::new, ModBlocks.LURE_BEACON.get()));
    public static final RegistryEntry<BlockEntityType<SonicWardBlockEntity>> SONIC_WARD = BLOCK_ENTITIES.register(
            "sonic_ward", key -> BlockEntityTypeCompat.create(SonicWardBlockEntity::new, ModBlocks.SONIC_WARD.get()));
    public static final RegistryEntry<BlockEntityType<TerrariumBlockEntity>> TERRARIUM = BLOCK_ENTITIES.register(
            "terrarium", key -> BlockEntityTypeCompat.create(TerrariumBlockEntity::new, ModBlocks.TERRARIUM.get()));
    public static final RegistryEntry<BlockEntityType<TrophyPlaqueBlockEntity>> TROPHY_PLAQUE = BLOCK_ENTITIES.register(
            "trophy_plaque", key -> BlockEntityTypeCompat.create(TrophyPlaqueBlockEntity::new, ModBlocks.TROPHY_PLAQUE.get()));

    private ModBlockEntities() {
    }

    /** Forces this class to initialise (and therefore its types to register on Fabric). */
    public static void init() {
    }
}
