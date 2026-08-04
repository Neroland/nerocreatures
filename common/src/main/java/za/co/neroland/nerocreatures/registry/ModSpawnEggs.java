package za.co.neroland.nerocreatures.registry;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

import za.co.neroland.nerolandcore.registry.CoreCreativeTab;
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
            GLACITE_WISP_SPAWN_EGG, XERTZ_FORAGER_SPAWN_EGG, CINDER_TYRANT_SPAWN_EGG);

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

    /** Contributes the eggs to Core's shared creative tab, after the drops. */
    public static void addToCreativeTab() {
        for (RegistryEntry<Item> egg : EGGS) {
            CoreCreativeTab.add(egg::get);
        }
    }
}
