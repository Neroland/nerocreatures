package za.co.neroland.nerocreatures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocreatures.block.ApexAltarBlockEntity;
import za.co.neroland.nerocreatures.block.EggLaying;
import za.co.neroland.nerocreatures.block.FeedingTroughBlockEntity;
import za.co.neroland.nerocreatures.block.IncubatorBlockEntity;
import za.co.neroland.nerocreatures.block.LureBeaconBlockEntity;
import za.co.neroland.nerocreatures.block.NestHome;
import za.co.neroland.nerocreatures.block.SonicWardBlockEntity;
import za.co.neroland.nerocreatures.boss.BossSummons;
import za.co.neroland.nerocreatures.compat.CompanionBridge;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureData;
import za.co.neroland.nerocreatures.entity.alpha.AlphaSystem;
import za.co.neroland.nerocreatures.entity.glacira.RimeYak;
import za.co.neroland.nerocreatures.entity.humanoid.SpacePirate;
import za.co.neroland.nerocreatures.entity.luna.LodeTortoise;
import za.co.neroland.nerocreatures.entity.mount.MountCreature;
import za.co.neroland.nerocreatures.entity.mount.MountHooks;
import za.co.neroland.nerocreatures.entity.nyxara.PrismStag;
import za.co.neroland.nerocreatures.item.gear.CreatureGear;
import za.co.neroland.nerocreatures.item.gear.GearSettings;
import za.co.neroland.nerocreatures.link.CreatureLinkModule;
import za.co.neroland.nerocreatures.network.CreatureNetwork;
import za.co.neroland.nerocreatures.registry.ModBlockEntities;
import za.co.neroland.nerocreatures.registry.ModBlocks;
import za.co.neroland.nerocreatures.registry.ModCreativeTab;
import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.registry.ModItems;
import za.co.neroland.nerocreatures.registry.ModSpawnEggs;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * Loader-agnostic entry point for NeroCreatures. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod construction.
 *
 * <p>The ordering below is not cosmetic. Fabric registers <em>eagerly</em> — the moment a registry
 * class is touched — so anything that must exist before something else has to be listed before it,
 * on every loader, whether or not that loader would have cared.
 */
public final class NeroCreaturesCommon {

    public static final String MOD_ID = "nerocreatures";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroCreatures");

    private NeroCreaturesCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroCreatures] common init");
        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroCreaturesConfig.init();
        // 2. Anonymous, NeroCreatures-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. Inert until a real
        //    Sentry DSN is configured (see NeroCreaturesTelemetry).
        NeroCreaturesTelemetry.init();
        // 3. Content registration. On Fabric this registers eagerly here; on NeoForge/Forge it only
        //    builds the DeferredRegisters, which each of those entry points then attaches to its mod
        //    event bus with RegistrationProvider.attach(...).
        //    Entity types come first: the spawn eggs and the spawn table both name them, and on
        //    Fabric "first" is literal.
        ModBlocks.init();
        ModBlockEntities.init();
        ModEntities.init();
        ModItems.init();
        ModSpawnEggs.init();
        // 4. NeroCreatures' own creative tab (drops, deployables, eggs), after the items it lists.
        ModCreativeTab.init();
        // 5. Default attributes and spawn placements, through Core's entity seam. After the types
        //    exist, because Fabric applies these the moment they are declared.
        ModEntities.registerEntitySupport();
        // 6. Player-data erasure registration. Registered before any store exists on purpose:
        //    registering late is how an erasure request silently misses a store (POPIA/GDPR).
        CreatureData.init();
        // 7. Resolve the optional companion-mod hooks now rather than on the first pet interaction.
        //    Same reasoning as platform/Services: a lazy ServiceLoader read can fail out of
        //    gameplay code, and this is the one place where failing is harmless. Finding nothing is
        //    the normal case and costs nothing.
        CompanionBridge.hooks();
        // 8. Declare the payloads before any loader registers them: every loader entry point runs
        //    this method first, then wires its own networking.
        CreatureNetwork.init();
        // 8b. Owned-creature actions on that channel (entity contract section 6) and the mount's
        //     saddle item. The predicate and stack supplier resolve the item lazily, so this is safe
        //     before the NeoForge/Forge registries fire.
        MountCreature.registerActions();
        MountHooks.bindSaddle(stack -> stack.is(ModItems.CREATURE_SADDLE.get()),
                () -> new ItemStack(ModItems.CREATURE_SADDLE.get()));
        // 8c. Alpha tuning from the config (negative cap = keep the data pack's cap).
        AlphaSystem.configure(() -> NeroCreaturesConfig.ALPHAS_ENABLED.get(),
                () -> NeroCreaturesConfig.ALPHA_CHANCE_MULTIPLIER.get(),
                () -> NeroCreaturesConfig.ALPHA_MAX_PER_DIMENSION.get());
        // 8d. Species tuning read live from the config (new tortoises pick up a changed value).
        LodeTortoise.configure(() -> NeroCreaturesConfig.LODE_TORTOISE_NODULE_TICKS.get(),
                () -> NeroCreaturesConfig.MAX_NODULES_PER_HARVEST.get());
        RimeYak.configure(() -> NeroCreaturesConfig.RIME_YAK_WOOL_REGROW_TICKS.get(),
                () -> NeroCreaturesConfig.RIME_YAK_MILK_COOLDOWN_TICKS.get());
        PrismStag.configure(() -> NeroCreaturesConfig.PRISM_STAG_HERD_AURA.get());
        // 8e. Apex Altar recharge, read live (per altar, never per player).
        ApexAltarBlockEntity.configure(() -> NeroCreaturesConfig.APEX_ALTAR_COOLDOWN_TICKS.get());
        // 8f. Stage 4 habitat blocks, read live (per block, never per player).
        FeedingTroughBlockEntity.configure(() -> NeroCreaturesConfig.FEEDING_TROUGH_INTERVAL_TICKS.get());
        IncubatorBlockEntity.configure(() -> NeroCreaturesConfig.INCUBATOR_HATCH_TICKS.get(),
                () -> NeroCreaturesConfig.INCUBATOR_POWERED_HATCH_TICKS.get(),
                () -> NeroCreaturesConfig.INCUBATOR_ENERGY_PER_TICK.get());
        LureBeaconBlockEntity.configure(() -> NeroCreaturesConfig.LURE_BEACON_BAIT_TICKS.get(),
                () -> NeroCreaturesConfig.LURE_BEACON_WEIGHT_MULTIPLIER.get());
        SonicWardBlockEntity.configure(() -> NeroCreaturesConfig.SONIC_WARD_FUEL_TICKS.get());
        NestHome.configure(() -> NeroCreaturesConfig.NEST_HEAL_INTERVAL_TICKS.get());
        // 8g. Creature gear: the Lurker Cloak silencer, and every cooldown read live from the config.
        CreatureGear.init();
        GearSettings.mirrorShieldCooldownTicks = () -> NeroCreaturesConfig.MIRROR_SHIELD_COOLDOWN_TICKS.get();
        GearSettings.lurkerCloakSilences = () -> NeroCreaturesConfig.LURKER_CLOAK_SILENCES.get();
        GearSettings.blinkCharmCooldownTicks = () -> NeroCreaturesConfig.BLINK_CHARM_COOLDOWN_TICKS.get();
        GearSettings.blinkCharmRange = () -> NeroCreaturesConfig.BLINK_CHARM_RANGE.get();
        GearSettings.rimeFlaskCooldownTicks = () -> NeroCreaturesConfig.RIME_FLASK_COOLDOWN_TICKS.get();
        GearSettings.heatGlandGreavesLavaTicks = () -> NeroCreaturesConfig.HEAT_GLAND_GREAVES_LAVA_TICKS.get();
        GearSettings.heatGlandGreavesCooldownTicks = () -> NeroCreaturesConfig.HEAT_GLAND_GREAVES_COOLDOWN_TICKS.get();
        GearSettings.gravitonBootsGravityPercent = () -> NeroCreaturesConfig.GRAVITON_BOOTS_GRAVITY_PERCENT.get();
        GearSettings.resonantWhistleCooldownTicks = () -> NeroCreaturesConfig.RESONANT_WHISTLE_COOLDOWN_TICKS.get();
        GearSettings.resonantWhistleRange = () -> NeroCreaturesConfig.RESONANT_WHISTLE_RANGE.get();
        GearSettings.chitinBulwarkCooldownTicks = () -> NeroCreaturesConfig.CHITIN_BULWARK_COOLDOWN_TICKS.get();
        GearSettings.bloomAmuletCooldownTicks = () -> NeroCreaturesConfig.BLOOM_AMULET_COOLDOWN_TICKS.get();
        SpacePirate.configureContraband(NeroCreaturesConfig.SPACE_PIRATE_TAKES_CONTRABAND);
        // 8h. Egg layer: egg-birth species drop <species>_egg instead of a baby (live birth if the item is missing).
        EggLaying.init();
        // 9. Spawn rules last of the content steps — they reference entity types, which on Fabric
        //    must already have been registered by the step above, and Core's entity seam applies
        //    Fabric registrations eagerly too.
        CreatureSpawns.init();
        // 10. The summonable-boss registry. After the entity types for the same reason as the spawn
        //     table, and after the spawn table because a boss's natural-spawn rule is part of it.
        BossSummons.init();
        // 11. The NeroLink module registers NeroCreatures' read/write/live surfaces with Core's link
        //     registry. It goes LAST so a companion client is never told about something before the
        //     mod itself has finished reacting to it, and its own init swallows any failure — a
        //     broken link module must never take the creature layer down with it.
        CreatureLinkModule.init();
    }
}
