package za.co.neroland.nerocreatures.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.item.CreatureSpawnEggItem;

/**
 * The creature spawn eggs — one per roster entry, so a builder, a pack tester or an operator can
 * place a creature without waiting for the spawn engine (or without a planet mod installed at all).
 *
 * <p>They live apart from {@link ModItems} on purpose: {@code ModItems} is the <b>materials</b>
 * registry, and materials and tools are highlighted differently by Core
 * ({@code neroland:highlight/materials} vs {@code neroland:highlight/tools}).
 *
 * <p>Each egg holds a {@link Supplier} of its entity type rather than the type itself — see
 * {@link CreatureSpawnEggItem} for why that matters on the deferred-register loaders. That also
 * means this class may safely be initialised before or after {@link ModEntities}; the type is only
 * resolved when someone actually right-clicks.
 */
public final class ModSpawnEggs {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroCreaturesCommon.MOD_ID);

    public static final RegistryEntry<Item> VOID_CRAWLER_SPAWN_EGG =
            egg("void_crawler_spawn_egg", ModEntities.VOID_CRAWLER);

    public static final RegistryEntry<Item> LUNAR_STALKER_SPAWN_EGG =
            egg("lunar_stalker_spawn_egg", ModEntities.LUNAR_STALKER);

    public static final RegistryEntry<Item> ASTEROID_WORM_SPAWN_EGG =
            egg("asteroid_worm_spawn_egg", ModEntities.ASTEROID_WORM);

    public static final RegistryEntry<Item> PLASMA_SLIME_SPAWN_EGG =
            egg("plasma_slime_spawn_egg", ModEntities.PLASMA_SLIME);

    public static final RegistryEntry<Item> CRYSTAL_GOLEM_SPAWN_EGG =
            egg("crystal_golem_spawn_egg", ModEntities.CRYSTAL_GOLEM);

    public static final RegistryEntry<Item> SPACE_PIRATE_SPAWN_EGG =
            egg("space_pirate_spawn_egg", ModEntities.SPACE_PIRATE);

    public static final RegistryEntry<Item> ROGUE_DRONE_SPAWN_EGG =
            egg("rogue_drone_spawn_egg", ModEntities.ROGUE_DRONE);

    public static final RegistryEntry<Item> ROGUE_ANDROID_SPAWN_EGG =
            egg("rogue_android_spawn_egg", ModEntities.ROGUE_ANDROID);

    public static final RegistryEntry<Item> GLACITE_WISP_SPAWN_EGG =
            egg("glacite_wisp_spawn_egg", ModEntities.GLACITE_WISP);

    public static final RegistryEntry<Item> XERTZ_FORAGER_SPAWN_EGG =
            egg("xertz_forager_spawn_egg", ModEntities.XERTZ_FORAGER);

    /**
     * The boss egg. It exists for operators, builders and pack testers, and it is <b>not</b> the
     * intended way to start a fight: an egg-placed Tyrant anchors its arena where it lands and drops
     * only its plain loot table, because contribution tracking and the enhanced reward split belong
     * to a deliberate summon ({@code boss/BossSummons}), not to a right-click.
     */
    public static final RegistryEntry<Item> CINDER_TYRANT_SPAWN_EGG =
            egg("cinder_tyrant_spawn_egg", ModEntities.CINDER_TYRANT);

    // --- 0.5.0 -----------------------------------------------------------------------------------

    public static final RegistryEntry<Item> LUMEN_MOTH_SPAWN_EGG =
            egg("lumen_moth_spawn_egg", ModEntities.LUMEN_MOTH);

    public static final RegistryEntry<Item> ECHO_SHRIKE_SPAWN_EGG =
            egg("echo_shrike_spawn_egg", ModEntities.ECHO_SHRIKE);

    public static final RegistryEntry<Item> LODE_TORTOISE_SPAWN_EGG =
            egg("lode_tortoise_spawn_egg", ModEntities.LODE_TORTOISE);

    public static final RegistryEntry<Item> DUNE_LOPER_SPAWN_EGG =
            egg("dune_loper_spawn_egg", ModEntities.DUNE_LOPER);

    public static final RegistryEntry<Item> STRATA_HOUND_SPAWN_EGG =
            egg("strata_hound_spawn_egg", ModEntities.STRATA_HOUND);

    public static final RegistryEntry<Item> DUST_DEVIL_SPAWN_EGG =
            egg("dust_devil_spawn_egg", ModEntities.DUST_DEVIL);

    public static final RegistryEntry<Item> SLAG_BEETLE_SPAWN_EGG =
            egg("slag_beetle_spawn_egg", ModEntities.SLAG_BEETLE);

    public static final RegistryEntry<Item> MAGMA_WADER_SPAWN_EGG =
            egg("magma_wader_spawn_egg", ModEntities.MAGMA_WADER);

    public static final RegistryEntry<Item> RIME_YAK_SPAWN_EGG =
            egg("rime_yak_spawn_egg", ModEntities.RIME_YAK);

    public static final RegistryEntry<Item> FROST_LURKER_SPAWN_EGG =
            egg("frost_lurker_spawn_egg", ModEntities.FROST_LURKER);

    public static final RegistryEntry<Item> SPORE_DRIFTER_SPAWN_EGG =
            egg("spore_drifter_spawn_egg", ModEntities.SPORE_DRIFTER);

    public static final RegistryEntry<Item> BLOOM_HOST_SPAWN_EGG =
            egg("bloom_host_spawn_egg", ModEntities.BLOOM_HOST);

    public static final RegistryEntry<Item> MOSSBACK_SPAWN_EGG =
            egg("mossback_spawn_egg", ModEntities.MOSSBACK);

    public static final RegistryEntry<Item> DRIFT_LEVIATHAN_SPAWN_EGG =
            egg("drift_leviathan_spawn_egg", ModEntities.DRIFT_LEVIATHAN);

    public static final RegistryEntry<Item> PRISM_STAG_SPAWN_EGG =
            egg("prism_stag_spawn_egg", ModEntities.PRISM_STAG);

    public static final RegistryEntry<Item> SHARDBACK_SPAWN_EGG =
            egg("shardback_spawn_egg", ModEntities.SHARDBACK);

    public static final RegistryEntry<Item> MAGNET_CRAB_SPAWN_EGG =
            egg("magnet_crab_spawn_egg", ModEntities.MAGNET_CRAB);

    public static final RegistryEntry<Item> GRAVITY_LEECH_SPAWN_EGG =
            egg("gravity_leech_spawn_egg", ModEntities.GRAVITY_LEECH);

    public static final RegistryEntry<Item> CACHE_MIMIC_SPAWN_EGG =
            egg("cache_mimic_spawn_egg", ModEntities.CACHE_MIMIC);

    public static final RegistryEntry<Item> STATIC_SWARM_SPAWN_EGG =
            egg("static_swarm_spawn_egg", ModEntities.STATIC_SWARM);

    public static final RegistryEntry<Item> SCRAP_HULK_SPAWN_EGG =
            egg("scrap_hulk_spawn_egg", ModEntities.SCRAP_HULK);

    public static final RegistryEntry<Item> PIRATE_CAPTAIN_SPAWN_EGG =
            egg("pirate_captain_spawn_egg", ModEntities.PIRATE_CAPTAIN);

    public static final RegistryEntry<Item> VOID_MANTA_SPAWN_EGG =
            egg("void_manta_spawn_egg", ModEntities.VOID_MANTA);

    /**
     * Every egg, in roster order — used for the creative tab.
     *
     * <p>There is deliberately <b>no terraforming-drone egg</b>. An egg makes an ownerless mob, and
     * a drone with no owner would be outside {@code maxDronesPerPlayer}, un-recallable and
     * un-erasable. Its crafted {@code drone_shell} is the only way to make one, and it is the thing
     * that binds the drone to a player in the same breath.
     */
    private static final List<RegistryEntry<Item>> EGGS = List.of(
            VOID_CRAWLER_SPAWN_EGG, LUNAR_STALKER_SPAWN_EGG, ASTEROID_WORM_SPAWN_EGG,
            PLASMA_SLIME_SPAWN_EGG, CRYSTAL_GOLEM_SPAWN_EGG, SPACE_PIRATE_SPAWN_EGG,
            ROGUE_DRONE_SPAWN_EGG, ROGUE_ANDROID_SPAWN_EGG,
            GLACITE_WISP_SPAWN_EGG, XERTZ_FORAGER_SPAWN_EGG, CINDER_TYRANT_SPAWN_EGG,
            LUMEN_MOTH_SPAWN_EGG, ECHO_SHRIKE_SPAWN_EGG, LODE_TORTOISE_SPAWN_EGG,
            DUNE_LOPER_SPAWN_EGG, STRATA_HOUND_SPAWN_EGG, DUST_DEVIL_SPAWN_EGG,
            SLAG_BEETLE_SPAWN_EGG, MAGMA_WADER_SPAWN_EGG,
            RIME_YAK_SPAWN_EGG, FROST_LURKER_SPAWN_EGG,
            SPORE_DRIFTER_SPAWN_EGG, BLOOM_HOST_SPAWN_EGG, MOSSBACK_SPAWN_EGG, DRIFT_LEVIATHAN_SPAWN_EGG,
            PRISM_STAG_SPAWN_EGG, SHARDBACK_SPAWN_EGG,
            MAGNET_CRAB_SPAWN_EGG, GRAVITY_LEECH_SPAWN_EGG,
            CACHE_MIMIC_SPAWN_EGG, STATIC_SWARM_SPAWN_EGG, SCRAP_HULK_SPAWN_EGG, PIRATE_CAPTAIN_SPAWN_EGG,
            VOID_MANTA_SPAWN_EGG);

    private ModSpawnEggs() {
    }

    private static RegistryEntry<Item> egg(String name,
            Supplier<? extends EntityType<? extends Mob>> type) {
        return ITEMS.register(name,
                key -> new CreatureSpawnEggItem(new Item.Properties().setId(key), type));
    }

    /** Forces this class to initialise (and therefore its items to register on Fabric). */
    public static void init() {
    }

    /** The spawn eggs, for {@link ModCreativeTab} (shown after the drops). Called when the tab is built. */
    public static List<ItemLike> creativeContents() {
        List<ItemLike> contents = new ArrayList<>();
        for (RegistryEntry<Item> egg : EGGS) {
            contents.add(egg.get());
        }
        return contents;
    }
}
