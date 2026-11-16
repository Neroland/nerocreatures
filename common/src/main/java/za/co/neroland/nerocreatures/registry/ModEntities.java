package za.co.neroland.nerocreatures.registry;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

import za.co.neroland.nerolandcore.entity.EntityRegistrationSupport;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.asteroid.GravityLeech;
import za.co.neroland.nerocreatures.entity.asteroid.MagnetCrab;
import za.co.neroland.nerocreatures.entity.boss.BloomSovereign;
import za.co.neroland.nerocreatures.entity.boss.CinderTyrant;
import za.co.neroland.nerocreatures.entity.boss.FacetColossus;
import za.co.neroland.nerocreatures.entity.boss.RimeMatriarch;
import za.co.neroland.nerocreatures.entity.deepspace.VoidManta;
import za.co.neroland.nerocreatures.entity.glacira.FrostLurker;
import za.co.neroland.nerocreatures.entity.glacira.RimeYak;
import za.co.neroland.nerocreatures.entity.greenxertz.BloomHost;
import za.co.neroland.nerocreatures.entity.greenxertz.DriftLeviathan;
import za.co.neroland.nerocreatures.entity.greenxertz.Mossback;
import za.co.neroland.nerocreatures.entity.greenxertz.SporeDrifter;
import za.co.neroland.nerocreatures.entity.hostile.AsteroidWorm;
import za.co.neroland.nerocreatures.entity.hostile.LunarStalker;
import za.co.neroland.nerocreatures.entity.hostile.MagmaWader;
import za.co.neroland.nerocreatures.entity.hostile.PlasmaSlime;
import za.co.neroland.nerocreatures.entity.hostile.VoidCrawler;
import za.co.neroland.nerocreatures.entity.humanoid.PirateCaptain;
import za.co.neroland.nerocreatures.entity.humanoid.SpacePirate;
import za.co.neroland.nerocreatures.entity.luna.EchoShrike;
import za.co.neroland.nerocreatures.entity.luna.LodeTortoise;
import za.co.neroland.nerocreatures.entity.luna.LumenMoth;
import za.co.neroland.nerocreatures.entity.mars.DuneLoper;
import za.co.neroland.nerocreatures.entity.mars.DustDevil;
import za.co.neroland.nerocreatures.entity.mars.StrataHound;
import za.co.neroland.nerocreatures.entity.mechanical.RogueAndroid;
import za.co.neroland.nerocreatures.entity.mechanical.RogueDrone;
import za.co.neroland.nerocreatures.entity.mechanical.TerraformingDrone;
import za.co.neroland.nerocreatures.entity.neutral.CrystalGolem;
import za.co.neroland.nerocreatures.entity.nyxara.PrismStag;
import za.co.neroland.nerocreatures.entity.nyxara.Shardback;
import za.co.neroland.nerocreatures.entity.tame.GlaciteWisp;
import za.co.neroland.nerocreatures.entity.tame.SlagBeetle;
import za.co.neroland.nerocreatures.entity.tame.XertzForager;
import za.co.neroland.nerocreatures.entity.wrecks.CacheMimic;
import za.co.neroland.nerocreatures.entity.wrecks.ScrapHulk;
import za.co.neroland.nerocreatures.entity.wrecks.StaticSwarm;

/**
 * NeroCreatures' entity types, and the two pieces of mob setup that have no vanilla cross-loader
 * home — default attributes and natural-spawn placements.
 *
 * <p>The types themselves ride Core's {@link RegistrationProvider} exactly like the items do.
 * Attributes and placements ride Core's {@link EntityRegistrationSupport} seam, which buffers them
 * and lets Core's per-loader plumbing flush them into NeoForge's
 * {@code EntityAttributeCreationEvent} / {@code RegisterSpawnPlacementsEvent}, Forge's equivalents,
 * or Fabric's immediate registration — downstream code sees one API.
 *
 * <p><b>Two-phase init, and it matters.</b> {@link #init()} registers the types;
 * {@link #registerEntitySupport()} declares attributes and placements. They are separate calls
 * because Fabric applies both eagerly, so the placements must not run until the types they name
 * actually exist. {@link NeroCreaturesCommon#init()} sequences them.
 *
 * <p><b>Placement is not the spawn table.</b> What is registered here is only "may the game
 * consider this position at all" (solid ground, air above, dark enough, not Peaceful). Which biome
 * a creature belongs in, at what weight and in what group size, lives in
 * {@code spawn/CreatureSpawns} — see that class for why this mod runs its own placement sweep.
 */
public final class ModEntities {

    /** Block light at or below which a light-shy creature will consider a position. */
    private static final int MAX_SPAWN_BLOCK_LIGHT = 7;

    public static final RegistrationProvider<EntityType<?>> ENTITY_TYPES =
            RegistrationProvider.get(Registries.ENTITY_TYPE, NeroCreaturesCommon.MOD_ID);

    /** Void Crawler — fast low-light ambusher; blinks onto its target. */
    public static final RegistryEntry<EntityType<VoidCrawler>> VOID_CRAWLER = ENTITY_TYPES.register(
            "void_crawler",
            key -> EntityType.Builder.of(VoidCrawler::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.8F).eyeHeight(0.6F).clientTrackingRange(8).build(key));

    /** Lunar Stalker — pack hunter; flanks, and gets bolder after dark. */
    public static final RegistryEntry<EntityType<LunarStalker>> LUNAR_STALKER = ENTITY_TYPES.register(
            "lunar_stalker",
            key -> EntityType.Builder.of(LunarStalker::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.4F).eyeHeight(1.2F).clientTrackingRange(10).build(key));

    /**
     * Asteroid Worm — burrowing elite. The hitbox is deliberately <b>long and low</b> rather than
     * tall: on the server the worm is one entity, and this box is the whole of it (the segments are
     * a client-model illusion — see {@link AsteroidWorm}).
     */
    public static final RegistryEntry<EntityType<AsteroidWorm>> ASTEROID_WORM = ENTITY_TYPES.register(
            "asteroid_worm",
            key -> EntityType.Builder.of(AsteroidWorm::new, MobCategory.MONSTER)
                    .sized(2.0F, 1.1F).eyeHeight(0.9F).clientTrackingRange(12).build(key));

    /** Plasma Slime — splitting blob with an energy aura. */
    public static final RegistryEntry<EntityType<PlasmaSlime>> PLASMA_SLIME = ENTITY_TYPES.register(
            "plasma_slime",
            key -> EntityType.Builder.of(PlasmaSlime::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.0F).eyeHeight(0.7F).clientTrackingRange(8).build(key));

    /**
     * Crystal Golem — the neutral elite. Broad and tall, and it walks rather than charges, so the
     * hitbox is the honest size of the thing you decided to mine.
     */
    public static final RegistryEntry<EntityType<CrystalGolem>> CRYSTAL_GOLEM = ENTITY_TYPES.register(
            "crystal_golem",
            key -> EntityType.Builder.of(CrystalGolem::new, MobCategory.CREATURE)
                    .sized(1.4F, 2.2F).eyeHeight(1.9F).clientTrackingRange(10).build(key));

    /**
     * Space Pirate — a humanoid raider, player-sized. One type for both kits: the melee/ranged split
     * is equipment, not biology (see {@code entity/humanoid/PirateLoadout}).
     */
    public static final RegistryEntry<EntityType<SpacePirate>> SPACE_PIRATE = ENTITY_TYPES.register(
            "space_pirate",
            key -> EntityType.Builder.of(SpacePirate::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(12).build(key));

    /** Rogue Drone — small, quick and airborne more often than not. */
    public static final RegistryEntry<EntityType<RogueDrone>> ROGUE_DRONE = ENTITY_TYPES.register(
            "rogue_drone",
            key -> EntityType.Builder.of(RogueDrone::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.7F).eyeHeight(0.5F).clientTrackingRange(8).build(key));

    /** Rogue Android — the heavy shielded frame. */
    public static final RegistryEntry<EntityType<RogueAndroid>> ROGUE_ANDROID = ENTITY_TYPES.register(
            "rogue_android",
            key -> EntityType.Builder.of(RogueAndroid::new, MobCategory.MONSTER)
                    .sized(1.2F, 2.4F).eyeHeight(2.1F).clientTrackingRange(10).build(key));

    /** Glacite Wisp — the Glacira-themed tameable pet. Small, frail and mostly airborne. */
    public static final RegistryEntry<EntityType<GlaciteWisp>> GLACITE_WISP = ENTITY_TYPES.register(
            "glacite_wisp",
            key -> EntityType.Builder.of(GlaciteWisp::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.7F).eyeHeight(0.5F).clientTrackingRange(8).build(key));

    /** Xertz Forager — the Greenxertz-themed tameable pet. A low, quick quadruped grazer. */
    public static final RegistryEntry<EntityType<XertzForager>> XERTZ_FORAGER = ENTITY_TYPES.register(
            "xertz_forager",
            key -> EntityType.Builder.of(XertzForager::new, MobCategory.CREATURE)
                    .sized(0.8F, 0.8F).eyeHeight(0.7F).clientTrackingRange(8).build(key));

    /**
     * Cinder Tyrant — the ember-world boss, and the mod's first {@code APEX} entity.
     *
     * <p>Three things here differ from every other creature and all three are deliberate:
     * {@code fireImmune()} (it is made of slag — see {@link CinderTyrant}), a
     * {@code clientTrackingRange} large enough that the fight is visible from the edge of its
     * arena rather than popping in halfway through, and a hitbox that is honestly the size of the
     * thing you are being asked to fight.
     */
    public static final RegistryEntry<EntityType<CinderTyrant>> CINDER_TYRANT = ENTITY_TYPES.register(
            "cinder_tyrant",
            key -> EntityType.Builder.of(CinderTyrant::new, MobCategory.MONSTER)
                    .sized(2.2F, 3.4F).eyeHeight(3.0F).fireImmune().clientTrackingRange(32).build(key));

    /**
     * Terraforming Drone — the utility mob. It is registered like any other entity, but note what is
     * <b>missing</b> below in {@link #registerEntitySupport()}: it has default attributes and no
     * spawn placement, because a drone has no natural spawn to place. The only way one exists is a
     * player deploying a {@code drone_shell} ({@code item/DroneShellItem}).
     */
    public static final RegistryEntry<EntityType<TerraformingDrone>> TERRAFORMING_DRONE =
            ENTITY_TYPES.register("terraforming_drone",
                    key -> EntityType.Builder.of(TerraformingDrone::new, MobCategory.CREATURE)
                            .sized(0.8F, 0.9F).eyeHeight(0.7F).clientTrackingRange(8).build(key));

    // --- 0.5.0 Luna ---------------------------------------------------------------------------

    /** Lumen Moth — Luna's tameable living lantern; flies. Size class S. */
    public static final RegistryEntry<EntityType<LumenMoth>> LUMEN_MOTH = ENTITY_TYPES.register(
            "lumen_moth",
            key -> EntityType.Builder.of(LumenMoth::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.5F).eyeHeight(0.3F).clientTrackingRange(8).build(key));

    /** Echo Shrike — Luna hostile; blind, hunts by sound. Size class S. */
    public static final RegistryEntry<EntityType<EchoShrike>> ECHO_SHRIKE = ENTITY_TYPES.register(
            "echo_shrike",
            key -> EntityType.Builder.of(EchoShrike::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.9F).eyeHeight(0.75F).clientTrackingRange(10).build(key));

    /** Lode Tortoise — Luna livestock; grows ore nodules. Size class M. */
    public static final RegistryEntry<EntityType<LodeTortoise>> LODE_TORTOISE = ENTITY_TYPES.register(
            "lode_tortoise",
            key -> EntityType.Builder.of(LodeTortoise::new, MobCategory.CREATURE)
                    .sized(1.1F, 0.9F).eyeHeight(0.6F).clientTrackingRange(10).build(key));

    // --- 0.5.0 Mars ---------------------------------------------------------------------------

    /** Dune Loper — Mars mount. Size class L, biped. */
    public static final RegistryEntry<EntityType<DuneLoper>> DUNE_LOPER = ENTITY_TYPES.register(
            "dune_loper",
            key -> EntityType.Builder.of(DuneLoper::new, MobCategory.CREATURE)
                    .sized(1.3F, 2.0F).eyeHeight(1.8F).clientTrackingRange(10).build(key));

    /** Strata Hound — Mars prospecting pet. Size class M. */
    public static final RegistryEntry<EntityType<StrataHound>> STRATA_HOUND = ENTITY_TYPES.register(
            "strata_hound",
            key -> EntityType.Builder.of(StrataHound::new, MobCategory.CREATURE)
                    .sized(0.8F, 0.9F).eyeHeight(0.7F).clientTrackingRange(10).build(key));

    /** Dust Devil — Mars hostile whirlwind. Size class M, headless. */
    public static final RegistryEntry<EntityType<DustDevil>> DUST_DEVIL = ENTITY_TYPES.register(
            "dust_devil",
            key -> EntityType.Builder.of(DustDevil::new, MobCategory.MONSTER)
                    .sized(1.1F, 2.2F).eyeHeight(1.6F).clientTrackingRange(10).build(key));

    // --- 0.5.0 Cindara ------------------------------------------------------------------------

    /** Slag Beetle — Cindara's living furnace. Fire-immune: it lives on ember plains. */
    public static final RegistryEntry<EntityType<SlagBeetle>> SLAG_BEETLE = ENTITY_TYPES.register(
            "slag_beetle",
            key -> EntityType.Builder.of(SlagBeetle::new, MobCategory.CREATURE)
                    .sized(0.9F, 0.8F).eyeHeight(0.55F).fireImmune().clientTrackingRange(8).build(key));

    /** Magma Wader — the stilt-legged lava wader that lobs cooling slag. */
    public static final RegistryEntry<EntityType<MagmaWader>> MAGMA_WADER = ENTITY_TYPES.register(
            "magma_wader",
            key -> EntityType.Builder.of(MagmaWader::new, MobCategory.MONSTER)
                    .sized(1.1F, 2.6F).eyeHeight(2.2F).fireImmune().clientTrackingRange(10).build(key));

    // --- 0.5.0 Glacira ------------------------------------------------------------------------

    /** Rime Yak — Glacira's walking cold store. A haystack of wool under wide sweeping horns. */
    public static final RegistryEntry<EntityType<RimeYak>> RIME_YAK = ENTITY_TYPES.register(
            "rime_yak",
            key -> EntityType.Builder.of(RimeYak::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.5F).eyeHeight(1.25F).clientTrackingRange(10).build(key));

    /** Frost Lurker — the flat ambusher under Glacira's snow. */
    public static final RegistryEntry<EntityType<FrostLurker>> FROST_LURKER = ENTITY_TYPES.register(
            "frost_lurker",
            key -> EntityType.Builder.of(FrostLurker::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.6F).eyeHeight(0.45F).clientTrackingRange(8).build(key));

    // --- 0.5.0 Greenxertz ---------------------------------------------------------------------

    /** Spore Drifter — Greenxertz's umbrella-canopy crop booster. */
    public static final RegistryEntry<EntityType<SporeDrifter>> SPORE_DRIFTER = ENTITY_TYPES.register(
            "spore_drifter",
            key -> EntityType.Builder.of(SporeDrifter::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.1F).eyeHeight(0.7F).clientTrackingRange(8).build(key));

    /**
     * Bloom Host — the flower-capped walker; sporelings and Bloom Sovereign's pods are this type
     * (form). The registered size is the host's; sporelings and pods size themselves by form.
     */
    public static final RegistryEntry<EntityType<BloomHost>> BLOOM_HOST = ENTITY_TYPES.register(
            "bloom_host",
            key -> EntityType.Builder.of(BloomHost::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.2F).eyeHeight(1.8F).clientTrackingRange(8).build(key));

    /** Mossback — the moss-carpeted herd grazer. */
    public static final RegistryEntry<EntityType<Mossback>> MOSSBACK = ENTITY_TYPES.register(
            "mossback",
            key -> EntityType.Builder.of(Mossback::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.2F).eyeHeight(0.9F).clientTrackingRange(10).build(key));

    /** Drift Leviathan — the enormous sky drifter with a walkable deck. Width = deck length (spawn box). */
    public static final RegistryEntry<EntityType<DriftLeviathan>> DRIFT_LEVIATHAN = ENTITY_TYPES.register(
            "drift_leviathan",
            key -> EntityType.Builder.of(DriftLeviathan::new, MobCategory.CREATURE)
                    .sized(8.75F, 3.0F).eyeHeight(2.0F).clientTrackingRange(16).build(key));

    // --- 0.5.0 Nyxara -------------------------------------------------------------------------

    /** Prism Stag — Nyxara's crystal-antlered herd deer; sheds antlers, heals its herd's companions at night. */
    public static final RegistryEntry<EntityType<PrismStag>> PRISM_STAG = ENTITY_TYPES.register(
            "prism_stag",
            key -> EntityType.Builder.of(PrismStag::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.6F).eyeHeight(1.4F).clientTrackingRange(10).build(key));

    /** Shardback — the mirror-fanned charger; reflects from the front, vulnerable behind or while charging. */
    public static final RegistryEntry<EntityType<Shardback>> SHARDBACK = ENTITY_TYPES.register(
            "shardback",
            key -> EntityType.Builder.of(Shardback::new, MobCategory.MONSTER)
                    .sized(1.2F, 1.1F).eyeHeight(0.8F).clientTrackingRange(10).build(key));

    // --- 0.5.0 Asteroid -----------------------------------------------------------------------

    /** Magnet Crab — the asteroid pet that steals metal when wild and fetches drops when tamed. */
    public static final RegistryEntry<EntityType<MagnetCrab>> MAGNET_CRAB = ENTITY_TYPES.register(
            "magnet_crab",
            key -> EntityType.Builder.of(MagnetCrab::new, MobCategory.CREATURE)
                    .sized(0.9F, 0.55F).eyeHeight(0.4F).clientTrackingRange(8).build(key));

    /** Gravity Leech — the floating sucker disc whose tether bends a player's gravity. */
    public static final RegistryEntry<EntityType<GravityLeech>> GRAVITY_LEECH = ENTITY_TYPES.register(
            "gravity_leech",
            key -> EntityType.Builder.of(GravityLeech::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.5F).eyeHeight(0.25F).clientTrackingRange(10).build(key));

    // --- 0.5.0 Wrecks and Pirates -------------------------------------------------------------

    /** Cache Mimic — a salvage crate until someone tries to open it. Exactly one block. */
    public static final RegistryEntry<EntityType<CacheMimic>> CACHE_MIMIC = ENTITY_TYPES.register(
            "cache_mimic",
            key -> EntityType.Builder.of(CacheMimic::new, MobCategory.MONSTER)
                    .sized(0.98F, 0.98F).eyeHeight(0.6F).clientTrackingRange(8).build(key));

    /** Static Swarm — a cloud of charged motes around a dense core (the hitbox). */
    public static final RegistryEntry<EntityType<StaticSwarm>> STATIC_SWARM = ENTITY_TYPES.register(
            "static_swarm",
            key -> EntityType.Builder.of(StaticSwarm::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.8F).eyeHeight(0.4F).clientTrackingRange(8).build(key));

    /** Scrap Hulk — the lopsided knuckle-walker built from machine wreckage. Elite; assembled, never spawned by a rule. */
    public static final RegistryEntry<EntityType<ScrapHulk>> SCRAP_HULK = ENTITY_TYPES.register(
            "scrap_hulk",
            key -> EntityType.Builder.of(ScrapHulk::new, MobCategory.MONSTER)
                    .sized(1.6F, 2.2F).eyeHeight(1.7F).clientTrackingRange(10).build(key));

    /** Pirate Captain — the crew's elite: jet nacelles, long coat, a ledger worth killing for. */
    public static final RegistryEntry<EntityType<PirateCaptain>> PIRATE_CAPTAIN = ENTITY_TYPES.register(
            "pirate_captain",
            key -> EntityType.Builder.of(PirateCaptain::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.25F).eyeHeight(2.0F).clientTrackingRange(12).build(key));

    // --- 0.5.0 Deep space ---------------------------------------------------------------------

    /** Void Manta — deep-space flying mount. Size class L, flat disc. */
    public static final RegistryEntry<EntityType<VoidManta>> VOID_MANTA = ENTITY_TYPES.register(
            "void_manta",
            key -> EntityType.Builder.of(VoidManta::new, MobCategory.CREATURE)
                    .sized(2.5F, 0.9F).eyeHeight(0.6F).clientTrackingRange(10).build(key));

    // --- 0.5.0 Apex bosses (altar-summoned only: no spawn placement, no spawn rule) -----------

    /** Rime Matriarch — Glacira's boss: blizzard beam, ice pillars, a freezing floor. */
    public static final RegistryEntry<EntityType<RimeMatriarch>> RIME_MATRIARCH = ENTITY_TYPES.register(
            "rime_matriarch",
            key -> EntityType.Builder.of(RimeMatriarch::new, MobCategory.MONSTER)
                    .sized(1.8F, 4.2F).eyeHeight(3.6F).clientTrackingRange(10).build(key));

    /** Bloom Sovereign — Greenxertz's rooted boss: spore pods that heal her, infecting lashes. */
    public static final RegistryEntry<EntityType<BloomSovereign>> BLOOM_SOVEREIGN = ENTITY_TYPES.register(
            "bloom_sovereign",
            key -> EntityType.Builder.of(BloomSovereign::new, MobCategory.MONSTER)
                    .sized(3.0F, 4.5F).eyeHeight(3.8F).clientTrackingRange(10).build(key));

    /** Facet Colossus — Nyxara's boss: one lit facet takes damage; the beam refracts through arena crystals. */
    public static final RegistryEntry<EntityType<FacetColossus>> FACET_COLOSSUS = ENTITY_TYPES.register(
            "facet_colossus",
            key -> EntityType.Builder.of(FacetColossus::new, MobCategory.MONSTER)
                    .sized(2.8F, 4.4F).eyeHeight(3.6F).clientTrackingRange(10).build(key));

    /**
     * Every creature type this mod registers, in roster order. Declared once here so anything that
     * has to walk the whole roster — the {@code /nerocreatures list} command, the link module's
     * bestiary section, the wiki checks — cannot silently miss a creature that was added to the
     * fields above and nowhere else.
     */
    private static final List<RegistryEntry<? extends EntityType<? extends Mob>>> ROSTER =
            List.<RegistryEntry<? extends EntityType<? extends Mob>>>of(
                    VOID_CRAWLER, LUNAR_STALKER, ASTEROID_WORM, PLASMA_SLIME,
                    CRYSTAL_GOLEM, SPACE_PIRATE, ROGUE_DRONE, ROGUE_ANDROID,
                    GLACITE_WISP, XERTZ_FORAGER, TERRAFORMING_DRONE, CINDER_TYRANT,
                    LUMEN_MOTH, ECHO_SHRIKE, LODE_TORTOISE,
                    DUNE_LOPER, STRATA_HOUND, DUST_DEVIL,
                    SLAG_BEETLE, MAGMA_WADER,
                    RIME_YAK, FROST_LURKER,
                    SPORE_DRIFTER, BLOOM_HOST, MOSSBACK, DRIFT_LEVIATHAN,
                    PRISM_STAG, SHARDBACK,
                    MAGNET_CRAB, GRAVITY_LEECH,
                    CACHE_MIMIC, STATIC_SWARM, SCRAP_HULK, PIRATE_CAPTAIN,
                    VOID_MANTA,
                    RIME_MATRIARCH, BLOOM_SOVEREIGN, FACET_COLOSSUS);

    private ModEntities() {
    }

    /** Forces this class to initialise (and therefore its types to register on Fabric). */
    public static void init() {
    }

    /**
     * The whole creature roster, in a stable order. Callers must be able to cope with a supplier
     * whose type is not resolvable yet — on the deferred-register loaders the types only exist once
     * registration has run.
     */
    public static List<RegistryEntry<? extends EntityType<? extends Mob>>> roster() {
        return ROSTER;
    }

    /**
     * From 1.21.4 vanilla {@code TemptGoal} reads the {@code minecraft:tempt_range} attribute and throws
     * if the mob lacks it (vanilla animals get it from {@code createAnimalAttributes}). Every creature
     * gets the vanilla default so a tempt goal on any of them can never crash the server tick.
     */
    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder withTemptRange(
            net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder builder) {
        //? if >=1.21.4 {
        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.TEMPT_RANGE, 10.0D);
        //?}
        return builder;
    }

    /**
     * Declares default attributes and natural-spawn placements through Core's entity seam. Must run
     * <b>after</b> {@link #init()} — on Fabric both are applied the moment they are declared.
     */
    public static void registerEntitySupport() {
        EntityRegistrationSupport entities =
                EntityRegistrationSupport.get(NeroCreaturesCommon.MOD_ID);

        entities.registerAttributes(VOID_CRAWLER, () -> withTemptRange(VoidCrawler.createAttributes()));
        entities.registerAttributes(LUNAR_STALKER, () -> withTemptRange(LunarStalker.createAttributes()));
        entities.registerAttributes(ASTEROID_WORM, () -> withTemptRange(AsteroidWorm.createAttributes()));
        entities.registerAttributes(PLASMA_SLIME, () -> withTemptRange(PlasmaSlime.createAttributes()));
        entities.registerAttributes(CRYSTAL_GOLEM, () -> withTemptRange(CrystalGolem.createAttributes()));
        entities.registerAttributes(SPACE_PIRATE, () -> withTemptRange(SpacePirate.createAttributes()));
        entities.registerAttributes(ROGUE_DRONE, () -> withTemptRange(RogueDrone.createAttributes()));
        entities.registerAttributes(ROGUE_ANDROID, () -> withTemptRange(RogueAndroid.createAttributes()));
        entities.registerAttributes(GLACITE_WISP, () -> withTemptRange(GlaciteWisp.createAttributes()));
        entities.registerAttributes(XERTZ_FORAGER, () -> withTemptRange(XertzForager.createAttributes()));
        entities.registerAttributes(TERRAFORMING_DRONE, () -> withTemptRange(TerraformingDrone.createAttributes()));
        entities.registerAttributes(CINDER_TYRANT, () -> withTemptRange(CinderTyrant.createAttributes()));
        entities.registerAttributes(LUMEN_MOTH, () -> withTemptRange(LumenMoth.createAttributes()));
        entities.registerAttributes(ECHO_SHRIKE, () -> withTemptRange(EchoShrike.createAttributes()));
        entities.registerAttributes(LODE_TORTOISE, () -> withTemptRange(LodeTortoise.createAttributes()));
        entities.registerAttributes(DUNE_LOPER, () -> withTemptRange(DuneLoper.createAttributes()));
        entities.registerAttributes(STRATA_HOUND, () -> withTemptRange(StrataHound.createAttributes()));
        entities.registerAttributes(DUST_DEVIL, () -> withTemptRange(DustDevil.createAttributes()));
        entities.registerAttributes(SLAG_BEETLE, () -> withTemptRange(SlagBeetle.createAttributes()));
        entities.registerAttributes(MAGMA_WADER, () -> withTemptRange(MagmaWader.createAttributes()));
        entities.registerAttributes(RIME_YAK, () -> withTemptRange(RimeYak.createAttributes()));
        entities.registerAttributes(FROST_LURKER, () -> withTemptRange(FrostLurker.createAttributes()));
        entities.registerAttributes(SPORE_DRIFTER, () -> withTemptRange(SporeDrifter.createAttributes()));
        entities.registerAttributes(BLOOM_HOST, () -> withTemptRange(BloomHost.createAttributes()));
        entities.registerAttributes(MOSSBACK, () -> withTemptRange(Mossback.createAttributes()));
        entities.registerAttributes(DRIFT_LEVIATHAN, () -> withTemptRange(DriftLeviathan.createAttributes()));
        entities.registerAttributes(PRISM_STAG, () -> withTemptRange(PrismStag.createAttributes()));
        entities.registerAttributes(SHARDBACK, () -> withTemptRange(Shardback.createAttributes()));
        entities.registerAttributes(MAGNET_CRAB, () -> withTemptRange(MagnetCrab.createAttributes()));
        entities.registerAttributes(GRAVITY_LEECH, () -> withTemptRange(GravityLeech.createAttributes()));
        entities.registerAttributes(CACHE_MIMIC, () -> withTemptRange(CacheMimic.createAttributes()));
        entities.registerAttributes(STATIC_SWARM, () -> withTemptRange(StaticSwarm.createAttributes()));
        entities.registerAttributes(SCRAP_HULK, () -> withTemptRange(ScrapHulk.createAttributes()));
        entities.registerAttributes(PIRATE_CAPTAIN, () -> withTemptRange(PirateCaptain.createAttributes()));
        entities.registerAttributes(VOID_MANTA, () -> withTemptRange(VoidManta.createAttributes()));
        // 0.5.0 Apex bosses. Like the Tyrant: attributes only, no placement (altar-summoned).
        entities.registerAttributes(RIME_MATRIARCH, () -> withTemptRange(RimeMatriarch.createAttributes()));
        entities.registerAttributes(BLOOM_SOVEREIGN, () -> withTemptRange(BloomSovereign.createAttributes()));
        entities.registerAttributes(FACET_COLOSSUS, () -> withTemptRange(FacetColossus.createAttributes()));

        // Light-shy: crawlers and slimes are creatures of the dark side and the crystal caves.
        entities.registerSpawnPlacement(VOID_CRAWLER, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkGround);
        entities.registerSpawnPlacement(PLASMA_SLIME, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkGround);
        // Light-independent: a moon and a rubble field are hostile at every hour, and most space
        // dimensions have a fixed sun anyway — a light rule there would mean "never spawns".
        entities.registerSpawnPlacement(LUNAR_STALKER, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(ASTEROID_WORM, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // These are light-independent, each and each for its own reason: a golem
        // is part of the terrain, a pirate band camps where it likes, and a machine does not care
        // whether the lights are on.
        entities.registerSpawnPlacement(CRYSTAL_GOLEM, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(SPACE_PIRATE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(ROGUE_DRONE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(ROGUE_ANDROID, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // The two pets are the only creatures here that spawn in daylight by preference rather than
        // by indifference — they are fauna, not monsters — but the placement rule is the same one:
        // solid ground, open air, not Peaceful. Peaceful is worth keeping even for a harmless
        // animal, because a wild pet on Peaceful would be the only NeroCreature in the world.
        entities.registerSpawnPlacement(GLACITE_WISP, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(XERTZ_FORAGER, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // The boss needs somewhere it can actually stand: solid ground and open air, at any light
        // level (its home is a permanently dim ember world). Whether a boss may appear at all is a
        // separate and much stricter question, answered by boss/BossSpawns.
        entities.registerSpawnPlacement(CINDER_TYRANT, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // Deliberately no placement for TERRAFORMING_DRONE: it has no natural spawn to place.

        // 0.5.0 Luna. The moth is a flyer: it may hang in open air (airSpot), and the overworld rule
        // lifts its candidate 1-4 blocks off the surface. The shrike hunts the dark; the tortoise is
        // a cave grazer as much as a moon one, so light does not matter to it.
        entities.registerSpawnPlacement(LUMEN_MOTH, SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::airSpot);
        entities.registerSpawnPlacement(ECHO_SHRIKE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkGround);
        entities.registerSpawnPlacement(LODE_TORTOISE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // 0.5.0 Mars and Cindara: open dunes and glowing ember plains, any hour.
        entities.registerSpawnPlacement(DUNE_LOPER, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(STRATA_HOUND, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(DUST_DEVIL, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(SLAG_BEETLE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(MAGMA_WADER, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // 0.5.0 Glacira and Nyxara: snowfields and crystal plains, any hour.
        entities.registerSpawnPlacement(RIME_YAK, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(FROST_LURKER, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(PRISM_STAG, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(SHARDBACK, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // 0.5.0 Greenxertz. The drifter is a flyer (airSpot); the host hunts the jungle's dark; the
        // leviathan is placed at the surface (it needs a free 8.75 x 3 box) and lifts itself to cruise
        // in finalizeSpawn.
        entities.registerSpawnPlacement(SPORE_DRIFTER, SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::airSpot);
        entities.registerSpawnPlacement(BLOOM_HOST, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkGround);
        entities.registerSpawnPlacement(MOSSBACK, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(DRIFT_LEVIATHAN, SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // 0.5.0 Asteroid: rubble fields, any hour. The leech spawns on the rock and lifts off.
        entities.registerSpawnPlacement(MAGNET_CRAB, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        entities.registerSpawnPlacement(GRAVITY_LEECH, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // 0.5.0 Wrecks and Pirates. Mimics wait in dark holds; the swarm is a flyer (airSpot keeps
        // the same block-light ceiling as darkGround for natural spawns, and it is placed in the air
        // or over open ground in a thunderstorm); a captain camps where it likes. The Scrap Hulk has no
        // placement: it is assembled, never spawned by a rule.
        entities.registerSpawnPlacement(CACHE_MIMIC, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::darkGround);
        entities.registerSpawnPlacement(STATIC_SWARM, SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::airSpot);
        entities.registerSpawnPlacement(PIRATE_CAPTAIN, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
        // 0.5.0 Deep space: the manta drifts over dark ground at any hour.
        entities.registerSpawnPlacement(VOID_MANTA, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::anyLightGround);
    }

    /**
     * Flyers: the spot and the one above are air, not Peaceful, and a <b>natural</b> spawn also needs
     * block light at or below {@value #MAX_SPAWN_BLOCK_LIGHT} (a moth is drawn out by the dark, and a
     * lit base stays clear of wild ones). Shared by every flyer in the roster.
     */
    private static <T extends Mob> boolean airSpot(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && (reason != EntitySpawnReason.NATURAL
                        || level.getBrightness(LightLayer.BLOCK, pos) <= MAX_SPAWN_BLOCK_LIGHT);
    }

    /** Solid ground below, open air at the position, not Peaceful. */
    private static <T extends Mob> boolean anyLightGround(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && !level.getBlockState(pos.below()).isAir()
                && level.getBlockState(pos).isAir();
    }

    /**
     * As {@link #anyLightGround} plus a block-light ceiling, applied only to <b>natural</b> spawns —
     * a spawn egg, a structure or a summon places the creature wherever it was asked to.
     */
    private static <T extends Mob> boolean darkGround(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (!anyLightGround(type, level, reason, pos, random)) {
            return false;
        }
        return reason != EntitySpawnReason.NATURAL
                || level.getBrightness(LightLayer.BLOCK, pos) <= MAX_SPAWN_BLOCK_LIGHT;
    }
}
