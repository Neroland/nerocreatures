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
                    + "false = opt out of all of it. See PRIVACY.md");

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

    // --- Ownership caps (server-authoritative) ------------------------------
    public static final ConfigValue<Integer> MAX_PETS_PER_PLAYER = SCHEMA.intRange(
            "maxPetsPerPlayer", 3, 0, 64, true,
            "How many alien pets one player may have tamed at once. 0 disables taming.");

    public static final ConfigValue<Integer> MAX_DRONES_PER_PLAYER = SCHEMA.intRange(
            "maxDronesPerPlayer", 2, 0, 64, true,
            "How many terraforming drones one player may have deployed at once. 0 disables drones.");

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
