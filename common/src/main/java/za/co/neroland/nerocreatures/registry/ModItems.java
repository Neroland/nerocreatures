package za.co.neroland.nerocreatures.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.item.CaptainsLedgerItem;
import za.co.neroland.nerocreatures.item.DrifterSporesItem;
import za.co.neroland.nerocreatures.item.DroneShellItem;
import za.co.neroland.nerocreatures.item.StaticJarItem;

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

    /** Rime Yak product (shearing) — insulating wool; cold-world suit and cryo-line lagging input. */
    public static final RegistryEntry<Item> RIME_WOOL = ITEMS.register("rime_wool",
            key -> new Item(new Item.Properties().setId(key)));

    /**
     * Rime Yak product (a bucket on an adult) — a cooling reagent; machine coolant input. The bucket
     * comes back when it is used in a crafting recipe.
     */
    public static final RegistryEntry<Item> COOLANT_MILK = ITEMS.register("coolant_milk",
            key -> new Item(new Item.Properties().stacksTo(16).craftRemainder(Items.BUCKET).setId(key)));

    /** Frost Lurker drop — a cryogenic fang; freezer and cryo-storage reagent. */
    public static final RegistryEntry<Item> CRYO_FANG = ITEMS.register("cryo_fang",
            key -> new Item(new Item.Properties().setId(key)));

    /** Spore Drifter product — a bottle of spores: two bone-meal growths, the bottle comes back. */
    public static final RegistryEntry<Item> DRIFTER_SPORES = ITEMS.register("drifter_spores",
            key -> new DrifterSporesItem(new Item.Properties().stacksTo(16).setId(key)));

    /** Bloom Host drop — the flower's living core; organic regen catalyst. */
    public static final RegistryEntry<Item> BLOOM_HEART = ITEMS.register("bloom_heart",
            key -> new Item(new Item.Properties().setId(key)));

    /** Mossback drop — a leather-class hide ({@code c:leathers}). */
    public static final RegistryEntry<Item> MOSSBACK_HIDE = ITEMS.register("mossback_hide",
            key -> new Item(new Item.Properties().setId(key)));

    /** Mossback drop — livestock meat, raw ({@code c:foods/raw_meat}). */
    public static final RegistryEntry<Item> RAW_MOSSBACK = ITEMS.register("raw_mossback",
            key -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()).setId(key)));

    /** Cooked Mossback ({@code c:foods/cooked_meat}). */
    public static final RegistryEntry<Item> COOKED_MOSSBACK = ITEMS.register("cooked_mossback",
            key -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()).setId(key)));

    /** Drift Leviathan product (shearing) — a rare renewable barnacle; hull sealant / organic polymer. */
    public static final RegistryEntry<Item> LEVIATHAN_BARNACLE = ITEMS.register("leviathan_barnacle",
            key -> new Item(new Item.Properties().setId(key)));

    /** Prism Stag product (shed antlers) — renewable optical crystal; cuts into Refined Crystal. */
    public static final RegistryEntry<Item> PRISM_ANTLER = ITEMS.register("prism_antler",
            key -> new Item(new Item.Properties().setId(key)));

    /** Shardback drop — a reflective scale; reflector plating input. */
    public static final RegistryEntry<Item> MIRROR_SCALE = ITEMS.register("mirror_scale",
            key -> new Item(new Item.Properties().setId(key)));

    /** Gravity Leech drop — a gravity reagent; Graviton Boots (Stage 4) and anti-grav parts elsewhere. */
    public static final RegistryEntry<Item> GRAVITON_NODE = ITEMS.register("graviton_node",
            key -> new Item(new Item.Properties().setId(key)));

    /**
     * Pirate Captain's log: marks one pirate stash for the captain's killer ({@code item/CaptainsLedgerItem}).
     * Given straight to the credited killer, never dropped on the ground.
     */
    public static final RegistryEntry<Item> CAPTAINS_LEDGER = ITEMS.register("captains_ledger",
            key -> new CaptainsLedgerItem(new Item.Properties().stacksTo(1).setId(key)));

    /**
     * Static Swarm, bottled (a glass bottle on a weakened swarm): thrown, it briefly disables
     * mechanical creatures ({@code entity/wrecks/StaticJarBurst}). Tagged as a tool.
     */
    public static final RegistryEntry<Item> STATIC_JAR = ITEMS.register("static_jar",
            key -> new StaticJarItem(new Item.Properties().stacksTo(16).setId(key)));

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
            RIME_WOOL, COOLANT_MILK, CRYO_FANG,
            DRIFTER_SPORES, BLOOM_HEART, MOSSBACK_HIDE, RAW_MOSSBACK, COOKED_MOSSBACK, LEVIATHAN_BARNACLE,
            PRISM_ANTLER, MIRROR_SCALE,
            GRAVITON_NODE, CAPTAINS_LEDGER,
            APEX_TROPHY);

    /** Crafted deployables — shown in the creative tab after the drops. */
    private static final List<RegistryEntry<Item>> DEPLOYABLES = List.of(DRONE_SHELL, CREATURE_SADDLE, STATIC_JAR);

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
