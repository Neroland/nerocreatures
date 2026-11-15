package za.co.neroland.nerocreatures.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;
import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * NeroCreatures config schema, built on Neroland Core's config framework
 * (file {@code nerocreatures.properties}, hot-reloadable via {@code /neroland config reload}).
 * Registered once from {@link NeroCreaturesCommon#init()}.
 *
 * <p>Every gameplay value is {@code serverAuthoritative}: spawning, AI aggression, drops, caps and
 * boss scaling are all decided by the server, and clients are told the values rather than choosing
 * them.
 *
 * <p><b>POPIA/GDPR:</b> {@code telemetryEnabled} is deliberately <b>not</b> server-authoritative —
 * anonymous crash reporting is a per-client opt-out that a server must never force on or off. The
 * server-sync snapshot carries only config keys/values, never player data.
 */
public final class NeroCreaturesConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroCreaturesCommon.MOD_ID, "NeroCreatures configuration.");

    // --- Crash telemetry (client-local opt-out) -----------------------------
    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "send anonymous, NeroCreatures-only crash reports (Sentry, EU servers) - stack trace, "
                    + "mod/MC/loader/OS/Java versions, your other installed mods, this mod's config, "
                    + "recent in-game actions, anonymous stability/timing; no IP, username, UUID, world "
                    + "data, pet ownership or chat; file paths scrubbed of your account name. "
                    + "On by default; set false to opt out of all of it (takes effect on restart). See PRIVACY.md");

    // --- Spawning (server-authoritative) ------------------------------------
    public static final ConfigValue<Boolean> SPAWNS_ENABLED = SCHEMA.bool(
            "spawnsEnabled", true, true,
            "Master switch for NeroCreatures' natural spawning. false = creatures only appear from "
                    + "spawn eggs, structures and explicit summons.");

    public static final ConfigValue<Double> GLOBAL_SPAWN_WEIGHT_MULTIPLIER = SCHEMA.doubleRange(
            "globalSpawnWeightMultiplier", 1.0D, 0.0D, 10.0D, true,
            "Multiplies every creature's spawn weight. 0 = no natural spawns at all, 2.0 = twice as "
                    + "common. Per-creature weights live in code (spawn/CreatureSpawns), not here.");

    public static final ConfigValue<Integer> MAX_CREATURES_PER_CHUNK = SCHEMA.intRange(
            "maxCreaturesPerChunk", 8, 0, 128, true,
            "Hard cap on NeroCreatures mobs alive in one chunk. Enforced at placement time, so a "
                    + "full chunk simply gets no new spawn.");

    public static final ConfigValue<Integer> MAX_CREATURES_PER_DIMENSION = SCHEMA.intRange(
            "maxCreaturesPerDimension", 200, 0, 10000, true,
            "Hard cap on NeroCreatures mobs alive in one dimension. The safety net behind the "
                    + "per-chunk cap; also bounds how much work the spawn engine can create.");

    // --- Combat / AI (server-authoritative) ---------------------------------
    public static final ConfigValue<Double> HOSTILE_AGGRESSION_MULTIPLIER = SCHEMA.doubleRange(
            "hostileAggressionMultiplier", 1.0D, 0.0D, 5.0D, true,
            "Scales hostile creatures' detection range and attack pressure. 0 = they never seek a "
                    + "target on their own (they still retaliate).");

    public static final ConfigValue<Boolean> PIRATE_VILLAGER_AGGRESSION = SCHEMA.bool(
            "pirateVillagerAggression", false, true,
            "Whether Space Pirates also attack villagers and colony NPCs (anything in the "
                    + "nerocreatures:pirate_raid_targets entity-type tag). OFF by default: a raider "
                    + "band wiping out a village is something a server should have to opt into. "
                    + "Pirates always attack players regardless.");

    public static final ConfigValue<Double> DROP_RATE_MULTIPLIER = SCHEMA.doubleRange(
            "dropRateMultiplier", 1.0D, 0.0D, 10.0D, true,
            "Scales the BONUS rolls NeroCreatures adds on top of a creature's loot table. The base "
                    + "table is untouched so data packs stay predictable.");

    // --- Bosses (server-authoritative) --------------------------------------
    public static final ConfigValue<Boolean> NATURAL_BOSS_SPAWNS_ENABLED = SCHEMA.bool(
            "naturalBossSpawnsEnabled", true, true,
            "Whether bosses can appear on their own in arena-like terrain. false = bosses exist only "
                    + "through the summon API / command.");

    public static final ConfigValue<Double> BOSS_DIFFICULTY_MULTIPLIER = SCHEMA.doubleRange(
            "bossDifficultyMultiplier", 1.0D, 0.1D, 10.0D, true,
            "Scales boss damage, phase pacing and add budgets.");

    public static final ConfigValue<Double> BOSS_HP_MULTIPLIER = SCHEMA.doubleRange(
            "bossHpMultiplier", 1.0D, 0.1D, 10.0D, true,
            "Scales boss maximum health. Separate from bossDifficultyMultiplier so a pack can make a "
                    + "boss long without making it lethal.");

    public static final ConfigValue<Integer> APEX_ALTAR_COOLDOWN_TICKS = SCHEMA.intRange(
            "apexAltarCooldownTicks", 12000, 0, 1008000, true,
            "Ticks an Apex Altar recharges after it summons a boss (per altar). 0 = no cooldown; a live "
                    + "fight still blocks.");

    // --- Ownership caps (server-authoritative) ------------------------------
    public static final ConfigValue<Integer> MAX_PETS_PER_PLAYER = SCHEMA.intRange(
            "maxPetsPerPlayer", 3, 0, 64, true,
            "How many alien pets one player may have tamed at once. 0 disables taming.");

    public static final ConfigValue<Integer> MAX_DRONES_PER_PLAYER = SCHEMA.intRange(
            "maxDronesPerPlayer", 2, 0, 64, true,
            "How many terraforming drones one player may have deployed at once. 0 disables drones.");

    // --- World-changing abilities (server-authoritative) ----------------------
    // Every key is still subject to the mobGriefing rule, the loader's place check and, for owned
    // creatures, the owner's own build permission (world/CreatureGriefing). Default true.
    public static final ConfigValue<Boolean> LUMEN_MOTH_LIGHT = SCHEMA.bool(
            "lumenMothLight", true, true,
            "Tamed Lumen Moths place a moving light block (air only). false = glow particles and Night Vision for the owner instead.");

    public static final ConfigValue<Boolean> LODE_TORTOISE_NODULES = SCHEMA.bool(
            "lodeTortoiseNodules", true, true,
            "Lode Tortoises grow ore nodules (entity state; never breaks a block). Balance switch.");

    public static final ConfigValue<Integer> LODE_TORTOISE_NODULE_TICKS = SCHEMA.intRange(
            "lodeTortoiseNoduleTicks", 36000, 1200, 240000, true,
            "Ticks of grazing for a Lode Tortoise to grow ready nodules of a common ore (rare ores take "
                    + "4x). New tortoises use the new value.");

    public static final ConfigValue<Integer> MAX_NODULES_PER_HARVEST = SCHEMA.intRange(
            "maxNodulesPerHarvest", 4, 0, 16, true,
            "Most items one Lode Tortoise nodule harvest yields after the Bountiful roll. 0 = nodules "
                    + "yield nothing.");

    public static final ConfigValue<Boolean> DUST_DEVIL_THROWS_ITEMS = SCHEMA.bool(
            "dustDevilThrowsItems", true, true,
            "Dust Devils fling ground items. false = they throw players only.");

    public static final ConfigValue<Boolean> MAGMA_WADER_SLAG = SCHEMA.bool(
            "magmaWaderSlag", true, true,
            "Magma Waders leave temporary slag crust. false = fire damage, no block.");

    public static final ConfigValue<Boolean> RIME_YAK_FREEZES_WATER = SCHEMA.bool(
            "rimeYakFreezesWater", true, true,
            "Rime Yaks turn still surface water into frosted ice that melts back. false = the aura only slows and cools.");

    public static final ConfigValue<Integer> RIME_YAK_WOOL_REGROW_TICKS = SCHEMA.intRange(
            "rimeYakWoolRegrowTicks", 6000, 1200, 72000, true,
            "Ticks for a shorn Rime Yak to regrow full wool.");

    public static final ConfigValue<Integer> RIME_YAK_MILK_COOLDOWN_TICKS = SCHEMA.intRange(
            "rimeYakMilkCooldownTicks", 2400, 200, 72000, true,
            "Ticks between milkings of one Rime Yak.");

    public static final ConfigValue<Boolean> PRISM_STAG_HERD_AURA = SCHEMA.bool(
            "prismStagHerdAura", true, true,
            "Prism Stag herds (3+ adults) give nearby players a short Regeneration I at night. false: no aura; "
                    + "antlers still grow and shed.");

    public static final ConfigValue<Boolean> MOSSBACK_FERTILISES = SCHEMA.bool(
            "mossbackFertilises", true, true,
            "Mossbacks bone-meal the grass they graze. false = they graze with no effect.");

    public static final ConfigValue<Boolean> SPORE_DRIFTER_GROWTH = SCHEMA.bool(
            "sporeDrifterGrowth", true, true,
            "Spore Drifters give extra growth ticks to crops below them. false = particles only.");

    public static final ConfigValue<Boolean> BLOOM_HOST_SPORELINGS = SCHEMA.bool(
            "bloomHostSporelings", true, true,
            "Bloom Hosts raise sporelings from mobs they kill (hard caps always apply). false = no sporelings.");

    public static final ConfigValue<Boolean> BLOOM_HOST_INFECTS_LIVESTOCK = SCHEMA.bool(
            "bloomHostInfectsLivestock", false, true,
            "Bloom Hosts may also hunt and raise sporelings from bred livestock (babies, animals in love or "
                    + "that bred recently). Tamed, leashed and named animals are always spared. false = spared too.");

    public static final ConfigValue<Boolean> MAGNET_CRAB_STEALS = SCHEMA.bool(
            "magnetCrabSteals", true, true,
            "Wild Magnet Crabs pick up metal items and return them on death. false = they ignore items.");

    public static final ConfigValue<Boolean> MAGNET_CRAB_COLLECTS = SCHEMA.bool(
            "magnetCrabCollects", true, true,
            "Tamed Magnet Crabs move nearby ground items (unthrown, or thrown by their owner) to the owner. false = off.");

    public static final ConfigValue<Boolean> SLAG_BEETLE_SMELTING = SCHEMA.bool(
            "slagBeetleSmelting", true, true,
            "Slag Beetles eat raw ore thrown by their owner and return it smelted. false = they ignore items.");

    public static final ConfigValue<Boolean> SCRAP_HULK_ASSEMBLY = SCHEMA.bool(
            "scrapHulkAssembly", true, true,
            "Scrap Hulks assemble from nearby mechanical deaths (hard caps always apply). false = no hulk.");

    public static final ConfigValue<Boolean> BOSS_ARENA_BLOCKS = SCHEMA.bool(
            "bossArenaBlocks", true, true,
            "Bosses raise temporary arena blocks (air only, removed at fight end). false = entity-only fallbacks.");

    public static final ConfigValue<Boolean> TERRAFORMING_DRONE_WORK = SCHEMA.bool(
            "terraformingDroneWork", true, true,
            "Terraforming Drones grow crops in their work area. false = they idle.");

    public static final ConfigValue<Boolean> CACHE_MIMIC_SPAWNS = SCHEMA.bool(
            "cacheMimicSpawns", true, true,
            "Cache Mimics spawn in wrecks. false = no natural Cache Mimics.");

    public static final ConfigValue<Boolean> STATIC_SWARM_DRAIN = SCHEMA.bool(
            "staticSwarmDrain", true, true,
            "Static Swarms drain chargeable items (plasma cells, or items a mod registers) and thrown Static Jars disable machines. false = swarms only shock; jars do nothing.");

    public static final ConfigValue<Integer> STRATA_HOUND_RANGE = SCHEMA.intRange(
            "strataHoundRange", 24, 4, 48, true,
            "How far (blocks) a Strata Hound scans for ore. Read-only scan, cached.");

    // --- Mounts and eggs (server-authoritative) -------------------------------
    public static final ConfigValue<Boolean> MOUNT_OWNER_ONLY = SCHEMA.bool(
            "mountOwnerOnly", true, true,
            "Only a mount's owner may ride it. false = anyone may ride; only the owner may open "
                    + "panniers, unsaddle or command it.");

    public static final ConfigValue<Integer> MAX_MOUNTS_PER_PLAYER = SCHEMA.intRange(
            "maxMountsPerPlayer", 2, 0, 16, true,
            "How many mounts one player may own at once. Mounts do not count against maxPetsPerPlayer.");

    public static final ConfigValue<Integer> MAX_INCUBATING_EGGS_PER_PLAYER = SCHEMA.intRange(
            "maxIncubatingEggsPerPlayer", 8, 0, 64, true,
            "How many creature eggs one player may have incubating at once.");

    public static final ConfigValue<Integer> MOUNT_STAMINA_REGEN_TICKS = SCHEMA.intRange(
            "mountStaminaRegenTicks", 10, 1, 200, true,
            "Ticks per point of mount stamina regained (stamina is 0-100).");

    public static final ConfigValue<Integer> MOUNT_ABILITY_COST = SCHEMA.intRange(
            "mountAbilityCost", 35, 0, 100, true,
            "Stamina a mount's leap or blink costs.");

    // --- Overworld spawns (server-authoritative) -------------------------------
    public static final ConfigValue<Boolean> OVERWORLD_SPAWNS = SCHEMA.bool(
            "overworldSpawns", true, true,
            "Lumen Moths, Lode Tortoises and Static Swarms also spawn in the overworld. Nerospace is "
                    + "not needed for these three.");

    public static final ConfigValue<Integer> OVERWORLD_SPAWN_CAP = SCHEMA.intRange(
            "overworldSpawnCap", 12, 0, 64, true,
            "The most Lumen Moths, Lode Tortoises and Static Swarms alive in the overworld at once.");

    // --- Tick budget (server-authoritative) ------------------------------------
    public static final ConfigValue<Integer> FAR_TICK_DISTANCE = SCHEMA.intRange(
            "farTickDistance", 48, 16, 256, true,
            "A creature with no player within this many blocks is 'far' and runs its own AI work "
                    + "on fewer ticks. Movement and physics are not slowed.");

    public static final ConfigValue<Integer> FAR_TICK_INTERVAL = SCHEMA.intRange(
            "farTickInterval", 4, 1, 20, true,
            "A far creature runs its own AI work on one tick in this many. 1 = never slow down.");

    // --- Alphas (server-authoritative) -------------------------------------------
    public static final ConfigValue<Boolean> ALPHAS_ENABLED = SCHEMA.bool(
            "alphasEnabled", true, true,
            "Rare, larger 'alpha' versions of hostile creatures can spawn. false = no new alphas "
                    + "(existing ones keep their state).");

    public static final ConfigValue<Double> ALPHA_CHANCE_MULTIPLIER = SCHEMA.doubleRange(
            "alphaChanceMultiplier", 1.0D, 0.0D, 10.0D, true,
            "Multiplies the alpha spawn chance from the data pack (alpha/settings.json). 0 = no new "
                    + "alphas, 2.0 = twice as likely.");

    public static final ConfigValue<Integer> ALPHA_MAX_PER_DIMENSION = SCHEMA.intRange(
            "alphaMaxPerDimension", -1, -1, 64, true,
            "The most alphas loaded in one dimension. -1 = use the data pack value "
                    + "(alpha/settings.json, 4 by default); 0 = no new alphas.");

    private NeroCreaturesConfig() {
    }

    /**
     * Whether anonymous NeroCreatures-only crash reporting is on (default true, opt-out).
     * Read once at bootstrap by {@code NeroCreaturesTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
