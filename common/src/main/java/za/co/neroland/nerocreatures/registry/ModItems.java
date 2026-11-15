package za.co.neroland.nerocreatures.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.item.DroneShellItem;

/**
 * NeroCreatures' item registrations — the creature drops.
 *
 * <p>Registration rides on <b>Neroland Core's</b> {@code RegistrationProvider} seam rather than a
 * NeroCreatures-specific one: the seam takes the mod id as a parameter, is published API on Core
 * (which every Nero mod already hard-depends on and which loads first), and ships one ServiceLoader
 * impl per loader inside Core's own jars — so there is nothing to duplicate here.
 *
 * <p>On Fabric that impl registers <em>eagerly</em>, at the moment this class is initialised, which
 * is why {@link #init()} exists. On NeoForge and Forge it creates a {@code DeferredRegister} that is
 * attached to the mod event bus by the {@code RegistrationProvider.attach(...)} call in each of
 * those loaders' entry points.
 *
 * <p>Every drop here is a <b>material</b>, not a tool: it exists to be consumed by another mod's
 * recipes. The canonical mapping of creature &rarr; drop &rarr; tag &rarr; consuming mod lives in
 * {@code wiki/Drop-Map.md}; keep the two in sync.
 */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroCreaturesCommon.MOD_ID);

    /** Void Crawler drop — condensed darkness; exotic/quantum recipe input. */
    public static final RegistryEntry<Item> VOID_ESSENCE = ITEMS.register("void_essence",
            key -> new Item(new Item.Properties().setId(key)));

    /** Lunar Stalker drop — tough hide; armour and insulation input. */
    public static final RegistryEntry<Item> STALKER_HIDE = ITEMS.register("stalker_hide",
            key -> new Item(new Item.Properties().setId(key)));

    /** Lunar Stalker drop — elastic sinew; taming reagent and cabling input. */
    public static final RegistryEntry<Item> STALKER_SINEW = ITEMS.register("stalker_sinew",
            key -> new Item(new Item.Properties().setId(key)));

    /** Crystal Golem drop — a lattice-perfect gem; optics and lens input ({@code c:gems}). */
    public static final RegistryEntry<Item> REFINED_CRYSTAL = ITEMS.register("refined_crystal",
            key -> new Item(new Item.Properties().setId(key)));

    /** Asteroid Worm drop — armour plating; heavy machine casing input. */
    public static final RegistryEntry<Item> WORM_CHITIN = ITEMS.register("worm_chitin",
            key -> new Item(new Item.Properties().setId(key)));

    /** Asteroid Worm drop — swallowed rock, half-digested; ore-processing input ({@code c:dusts}). */
    public static final RegistryEntry<Item> ORE_SLURRY = ITEMS.register("ore_slurry",
            key -> new Item(new Item.Properties().setId(key)));

    /** Plasma Slime drop — a still-charged cell; energy and taming input. */
    public static final RegistryEntry<Item> PLASMA_CELL = ITEMS.register("plasma_cell",
            key -> new Item(new Item.Properties().setId(key)));

    /** Space Pirate drop — stolen goods; trade/economy value rather than a recipe input. */
    public static final RegistryEntry<Item> CONTRABAND = ITEMS.register("contraband",
            key -> new Item(new Item.Properties().setId(key)));

    /** Rogue Android drop — recovered boards; electronics input. */
    public static final RegistryEntry<Item> SALVAGED_CIRCUITRY = ITEMS.register("salvaged_circuitry",
            key -> new Item(new Item.Properties().setId(key)));

    /** Heavy Rogue Android drop — an intact processing core; rare automation input. */
    public static final RegistryEntry<Item> ANDROID_CORE = ITEMS.register("android_core",
            key -> new Item(new Item.Properties().stacksTo(16).setId(key)));

    // --- 0.5.0 habitat drops (decision 001) ----------------------------------------------------

    /** Lumen Moth drop — luminous wing-scale dust; light source input ({@code c:dusts/lumen}). */
    public static final RegistryEntry<Item> LUMEN_DUST = ITEMS.register("lumen_dust",
            key -> new Item(new Item.Properties().setId(key)));

    /** Echo Shrike drop — the ear membrane; resonates with sound. Acoustic and instrument input. */
    public static final RegistryEntry<Item> RESONANT_MEMBRANE = ITEMS.register("resonant_membrane",
            key -> new Item(new Item.Properties().setId(key)));

    /** Lode Tortoise drop — a mineral-laced shell plate; machine and suit plating input. */
    public static final RegistryEntry<Item> LODE_SCUTE = ITEMS.register("lode_scute",
            key -> new Item(new Item.Properties().setId(key)));

    /** Dust Devil drop — static-charged red sand; sensor and energy reagent ({@code c:dusts/charged_sand}). */
    public static final RegistryEntry<Item> CHARGED_SAND = ITEMS.register("charged_sand",
            key -> new Item(new Item.Properties().setId(key)));

    /** Slag Beetle drop — a heat-proof carapace plate; shield facing and furnace lining input. */
    public static final RegistryEntry<Item> SLAG_CARAPACE = ITEMS.register("slag_carapace",
            key -> new Item(new Item.Properties().setId(key)));

    /** Magma Wader drop — a thermal organ; heat-exchanger and lava-walking gear reagent. */
    public static final RegistryEntry<Item> HEAT_GLAND = ITEMS.register("heat_gland",
            key -> new Item(new Item.Properties().setId(key)));

    /**
     * Planet-boss drop — the proof you killed one, and the ecosystem's top-tier reagent.
     *
     * <p>It stacks to 8 rather than 64 because it is meant to be counted rather than accumulated: a
     * trophy is one boss. Every planet boss drops this same item — the trophy is the <b>tier</b>, not
     * the creature — so a downstream recipe can ask for "a boss kill" without caring which world it
     * happened on.
     */
    public static final RegistryEntry<Item> APEX_TROPHY = ITEMS.register("apex_trophy",
            key -> new Item(new Item.Properties().stacksTo(8).setId(key)));

    /**
     * Terraforming Drone deployment shell — the one item here that is <b>not</b> a drop.
     *
     * <p>It is a crafted deployable: right-click it to unfold a drone bound to you (see
     * {@code item/DroneShellItem}), and shift-interact that drone with an empty hand to get the shell
     * back. It lives in this class because there is no third item registry worth having, but it is
     * kept out of {@link #DROPS} and tagged as a <b>tool</b> rather than a material, so Core's item
     * highlighting gives it the tool border alongside the spawn eggs.
     */
    public static final RegistryEntry<Item> DRONE_SHELL = ITEMS.register("drone_shell",
            key -> new DroneShellItem(new Item.Properties().stacksTo(4).setId(key)));

    /**
     * Mount saddle (decision 004): the owner uses it on a tamed mount. Its own item, never the
     * vanilla saddle, so there is one code path on every Minecraft line. Crafted, not a drop.
     */
    public static final RegistryEntry<Item> CREATURE_SADDLE = ITEMS.register("creature_saddle",
            key -> new Item(new Item.Properties().stacksTo(1).setId(key)));

    /** Every drop, in Drop-Map order — used for the creative tab and by the wiki check. */
    private static final List<RegistryEntry<Item>> DROPS = List.of(
            VOID_ESSENCE, STALKER_HIDE, STALKER_SINEW, REFINED_CRYSTAL, WORM_CHITIN,
            ORE_SLURRY, PLASMA_CELL, CONTRABAND, SALVAGED_CIRCUITRY, ANDROID_CORE,
            LUMEN_DUST, RESONANT_MEMBRANE, LODE_SCUTE, CHARGED_SAND, SLAG_CARAPACE, HEAT_GLAND,
            APEX_TROPHY);

    /** Crafted deployables — shown in the creative tab after the drops. */
    private static final List<RegistryEntry<Item>> DEPLOYABLES = List.of(DRONE_SHELL, CREATURE_SADDLE);

    private ModItems() {
    }

    /** Forces this class to initialise (and therefore its items to register on Fabric). */
    public static void init() {
    }

    /**
     * The drops, then the deployables, for {@link ModCreativeTab}. Called only when the tab is built,
     * by which point the items exist on every loader.
     */
    public static List<ItemLike> creativeContents() {
        List<ItemLike> contents = new ArrayList<>();
        for (RegistryEntry<Item> drop : DROPS) {
            contents.add(drop.get());
        }
        for (RegistryEntry<Item> deployable : DEPLOYABLES) {
            contents.add(deployable.get());
        }
        return contents;
    }
}
