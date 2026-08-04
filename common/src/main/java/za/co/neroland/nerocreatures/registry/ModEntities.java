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
import za.co.neroland.nerocreatures.entity.boss.CinderTyrant;
import za.co.neroland.nerocreatures.entity.hostile.AsteroidWorm;
import za.co.neroland.nerocreatures.entity.hostile.LunarStalker;
import za.co.neroland.nerocreatures.entity.hostile.PlasmaSlime;
import za.co.neroland.nerocreatures.entity.hostile.VoidCrawler;
import za.co.neroland.nerocreatures.entity.humanoid.SpacePirate;
import za.co.neroland.nerocreatures.entity.mechanical.RogueAndroid;
import za.co.neroland.nerocreatures.entity.mechanical.RogueDrone;
import za.co.neroland.nerocreatures.entity.mechanical.TerraformingDrone;
import za.co.neroland.nerocreatures.entity.neutral.CrystalGolem;
import za.co.neroland.nerocreatures.entity.tame.GlaciteWisp;
import za.co.neroland.nerocreatures.entity.tame.XertzForager;

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
                    GLACITE_WISP, XERTZ_FORAGER, TERRAFORMING_DRONE, CINDER_TYRANT);

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
     * Declares default attributes and natural-spawn placements through Core's entity seam. Must run
     * <b>after</b> {@link #init()} — on Fabric both are applied the moment they are declared.
     */
    public static void registerEntitySupport() {
        EntityRegistrationSupport entities =
                EntityRegistrationSupport.get(NeroCreaturesCommon.MOD_ID);

        entities.registerAttributes(VOID_CRAWLER, VoidCrawler::createAttributes);
        entities.registerAttributes(LUNAR_STALKER, LunarStalker::createAttributes);
        entities.registerAttributes(ASTEROID_WORM, AsteroidWorm::createAttributes);
        entities.registerAttributes(PLASMA_SLIME, PlasmaSlime::createAttributes);
        entities.registerAttributes(CRYSTAL_GOLEM, CrystalGolem::createAttributes);
        entities.registerAttributes(SPACE_PIRATE, SpacePirate::createAttributes);
        entities.registerAttributes(ROGUE_DRONE, RogueDrone::createAttributes);
        entities.registerAttributes(ROGUE_ANDROID, RogueAndroid::createAttributes);
        entities.registerAttributes(GLACITE_WISP, GlaciteWisp::createAttributes);
        entities.registerAttributes(XERTZ_FORAGER, XertzForager::createAttributes);
        entities.registerAttributes(TERRAFORMING_DRONE, TerraformingDrone::createAttributes);
        entities.registerAttributes(CINDER_TYRANT, CinderTyrant::createAttributes);

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
        // Stage 4's roster is light-independent to a creature, and each for its own reason: a golem
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
