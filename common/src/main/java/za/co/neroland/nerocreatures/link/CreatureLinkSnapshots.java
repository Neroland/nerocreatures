package za.co.neroland.nerocreatures.link;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossFights;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;
import za.co.neroland.nerocreatures.spawn.SpawnRule;

/**
 * The read half of the link module: what one player has hunted, what they own, and what boss fights
 * are running.
 *
 * <h2>Sections</h2>
 *
 * <ul>
 *   <li>{@code bestiary} — the whole creature roster with each entry's spawn rules, plus <em>this
 *       player's own</em> kill count per creature;</li>
 *   <li>{@code pets} — <em>this player's own</em> tamed pets and deployed drones, with their live
 *       status where the creature is loaded, and their two caps;</li>
 *   <li>{@code bosses} — the summoned boss fights currently in progress, with <em>this player's
 *       own</em> contribution to each.</li>
 * </ul>
 *
 * <p>Any other section name yields an empty object, as Core's contract prescribes.
 *
 * <h2>Where the kill counts come from</h2>
 *
 * <p>Deliberately from <b>vanilla's own statistics</b> ({@code minecraft:killed} per entity type),
 * not from a NeroCreatures store. A bestiary that needed its own store would be a third player-keyed
 * record to erase, retain and declare, in exchange for a number the game already keeps and the
 * player can already read on the vanilla statistics screen. Reusing it means the link module adds no
 * stored data at all.
 *
 * <p>The one consequence is that kill counts need a live {@link ServerPlayer}: vanilla's stats
 * counter is loaded with the player. While a player is offline the roster and its spawn data are
 * still returned — those are facts about the world — and {@code player_online} is {@code false} so
 * an app can say why the numbers are missing rather than showing zeroes as though nothing had ever
 * been killed.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>Everything player-shaped is scoped to {@code playerId} before it leaves this class: their kill
 * counts, their ownership rows, their contribution. No other player's rows, damage totals, names,
 * UUIDs or positions appear anywhere in a payload. The {@code bosses} section reports a fight's
 * <em>participant count</em> and the asking player's own share — a count is not an identity, and a
 * share is a fraction of an aggregate (see {@code BossContributionState.shareOf}).
 *
 * <p>The ownership rows themselves hold no coordinates, so neither does this; a loaded creature's
 * status is reported as loaded/unloaded and by health, never by where it is standing.
 *
 * <p><b>Read-only and bounded.</b> Nothing here mutates anything. The most expensive section is
 * {@code pets}, which does one map lookup per owned row (capped at three pets and two drones by
 * default). The bridge governs how often it is called and caches the result.
 *
 * <p>Server thread only.
 */
public final class CreatureLinkSnapshots implements LinkSnapshotProvider {

    private static final List<String> SECTIONS = List.of(
            CreatureLinkModule.SECTION_BESTIARY,
            CreatureLinkModule.SECTION_PETS,
            CreatureLinkModule.SECTION_BOSSES);

    /** An owned creature whose dimension is loaded and whose entity is there. */
    private static final String STATUS_LOADED = "loaded";

    /** An owned creature that exists in the index but is not currently loaded. */
    private static final String STATUS_UNLOADED = "unloaded";

    @Override
    public String moduleId() {
        return CreatureLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return CreatureLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        if (playerId == null || section == null) {
            return new JsonObject();
        }
        MinecraftServer server = CreatureLinkAccess.server();
        if (server == null) {
            return new JsonObject();
        }
        try {
            if (CreatureLinkModule.SECTION_BESTIARY.equals(section)) {
                return bestiary(server, playerId);
            }
            if (CreatureLinkModule.SECTION_PETS.equals(section)) {
                return pets(server, playerId);
            }
            if (CreatureLinkModule.SECTION_BOSSES.equals(section)) {
                return bosses(server, playerId);
            }
            return new JsonObject(); // unknown section: nothing to say
        } catch (RuntimeException e) {
            // Section name only — never who asked (POPIA/GDPR). A failed snapshot must not
            // propagate into the bridge.
            NeroCreaturesCommon.LOGGER.warn(
                    "[NeroCreatures] NeroLink snapshot section '{}' failed; returning nothing for it.",
                    section, e);
            return new JsonObject();
        }
    }

    // --- section: bestiary ----------------------------------------------------

    /**
     * {@code {"schema_version":1,"player_online":true,"total_kills":12,"species_killed":3,
     * "creatures":[…]}} — one entry per registered creature, in roster order, each with its
     * translation key, a printable name, its spawn rules and this player's kill count.
     */
    private static JsonObject bestiary(MinecraftServer server, UUID playerId) {
        ServerPlayer player = CreatureLinkAccess.online(server, playerId);
        List<SpawnRule> rules = CreatureSpawns.rules();

        JsonArray creatures = new JsonArray();
        int totalKills = 0;
        int speciesKilled = 0;

        for (RegistryEntry<? extends EntityType<? extends Mob>> holder : ModEntities.roster()) {
            EntityType<? extends Mob> type = resolve(holder);
            if (type == null) {
                continue; // a type that has not registered yet is simply not in the bestiary
            }
            Identifier id = EntityType.getKey(type);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("name", CreatureLinkAccess.readableName(type));
            entry.addProperty("translation_key", type.getDescriptionId());
            if (player != null) {
                // Vanilla's own per-type kill statistic — this player's, and only this player's.
                int killed = player.getStats().getValue(Stats.ENTITY_KILLED.get(type));
                entry.addProperty("killed", killed);
                totalKills += killed;
                if (killed > 0) {
                    speciesKilled++;
                }
            }
            entry.add("spawns", spawnsFor(rules, id.getPath()));
            creatures.add(entry);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        // Kill counts come from the player's live stats counter, which only exists while they are
        // connected — so an app can tell "you have killed nothing" from "we cannot see that yet".
        root.addProperty("player_online", player != null);
        if (player != null) {
            root.addProperty("total_kills", totalKills);
            root.addProperty("species_killed", speciesKilled);
        }
        root.addProperty("spawns_enabled", NeroCreaturesConfig.SPAWNS_ENABLED.get());
        root.add("creatures", creatures);
        return root;
    }

    /**
     * The spawn-table lines for one creature id, as {@code [{"biome_tag":…,"weight":…,
     * "group_min":…,"group_max":…}]}. A creature with no lines (the Terraforming Drone) reports an
     * empty array, which is the honest "this never spawns naturally".
     */
    private static JsonArray spawnsFor(List<SpawnRule> rules, String creatureId) {
        JsonArray out = new JsonArray();
        for (SpawnRule rule : rules) {
            if (!rule.creatureId().equals(creatureId)) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("biome_tag", rule.biomeTag().location().toString());
            rule.dimensionTag().ifPresent(tag ->
                    row.addProperty("dimension_tag", tag.location().toString()));
            row.addProperty("weight", rule.weight());
            row.addProperty("group_min", rule.minGroupSize());
            row.addProperty("group_max", rule.maxGroupSize());
            out.add(row);
        }
        return out;
    }

    // --- section: pets --------------------------------------------------------

    /**
     * {@code {"schema_version":1,"player_online":…,"caps":{…},"pets":[…]}} — exactly this player's
     * own ownership rows, in the order the index holds them, each enriched with live status when
     * the creature happens to be loaded.
     */
    private static JsonObject pets(MinecraftServer server, UUID playerId) {
        CreatureOwnershipState state = CreatureOwnershipState.get(server);
        List<OwnedCreature> rows = state.owned(playerId);

        JsonArray owned = new JsonArray();
        for (OwnedCreature row : rows) {
            JsonObject entry = new JsonObject();
            entry.addProperty("entity", row.entity().toString());
            entry.addProperty("kind", row.kind().key());
            entry.addProperty("type", row.type().toString());
            entry.addProperty("name", CreatureLinkAccess.readablePath(row.type().getPath()));
            entry.addProperty("dimension", row.dimension().toString());

            // Live status, when the creature happens to be loaded. This is one map lookup in the
            // level's entity index — no chunk is loaded to answer it, so an unloaded pet simply
            // reads as unloaded rather than being dragged into memory to be described.
            ServerLevel level = server.getLevel(row.dimensionKey());
            Entity entity = level == null ? null : level.getEntity(row.entity());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                entry.addProperty("status", STATUS_LOADED);
                entry.addProperty("health", living.getHealth());
                entry.addProperty("max_health", living.getMaxHealth());
                if (living instanceof TameableCreature pet) {
                    entry.addProperty("command", pet.command().key());
                }
            } else {
                entry.addProperty("status", STATUS_UNLOADED);
            }
            owned.add(entry);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", CreatureLinkAccess.isOnline(server, playerId));
        root.add("caps", caps(state, playerId));
        root.add("pets", owned);
        return root;
    }

    /** {@code {"pets":{"used":1,"max":3},"drones":{"used":0,"max":2}}}. */
    private static JsonObject caps(CreatureOwnershipState state, UUID playerId) {
        JsonObject caps = new JsonObject();
        caps.add("pets", cap(state.count(playerId, OwnedCreature.Kind.PET),
                NeroCreaturesConfig.MAX_PETS_PER_PLAYER.get()));
        caps.add("drones", cap(state.count(playerId, OwnedCreature.Kind.DRONE),
                NeroCreaturesConfig.MAX_DRONES_PER_PLAYER.get()));
        return caps;
    }

    private static JsonObject cap(int used, int max) {
        JsonObject entry = new JsonObject();
        entry.addProperty("used", used);
        entry.addProperty("max", max);
        return entry;
    }

    // --- section: bosses ------------------------------------------------------

    /**
     * {@code {"schema_version":1,"fights":[…]}} — the summoned fights loaded right now.
     *
     * <p>Only summoned fights appear, because only summoned fights have a fight id at all: a boss
     * found in the world is a mob, not an event, and records nothing about anybody. Each row carries
     * the boss, where it is, how far the fight has got — and the asking player's own damage and
     * share, which are {@code 0} for a fight they have not touched.
     */
    private static JsonObject bosses(MinecraftServer server, UUID playerId) {
        List<BossController> fights = BossFights.active();
        BossContributionState contribution = BossContributionState.get(server);

        JsonArray rows = new JsonArray();
        for (BossController controller : fights) {
            UUID fight = controller.fightId();
            if (fight == null) {
                continue; // belt and braces: BossFights only ever holds tracked fights
            }
            JsonObject row = new JsonObject();
            row.addProperty("fight", fight.toString());
            row.addProperty("boss", controller.bossId().toString());
            row.addProperty("name", CreatureLinkAccess.readablePath(controller.bossId().getPath()));
            row.addProperty("dimension", controller.dimensionId());
            row.addProperty("phase", controller.phaseNumber());
            row.addProperty("phase_count", controller.phaseCount());
            row.addProperty("health", controller.healthFraction());
            // Own damage and own share only. The participant count is a number, not a roster.
            row.addProperty("your_damage", contribution.damageOf(fight, playerId));
            row.addProperty("your_share", contribution.shareOf(fight, playerId));
            row.addProperty("participants", contribution.participantCount(fight));
            rows.add(row);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", CreatureLinkAccess.isOnline(server, playerId));
        root.add("fights", rows);
        return root;
    }

    // --- helpers --------------------------------------------------------------

    /**
     * A roster entry's type, or {@code null} if it is not resolvable yet. A deferred-register loader
     * will happily hand out a supplier that throws before registration has run, and a snapshot is
     * not the place to find that out the hard way.
     */
    private static EntityType<? extends Mob> resolve(
            RegistryEntry<? extends EntityType<? extends Mob>> holder) {
        try {
            return holder.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

}
