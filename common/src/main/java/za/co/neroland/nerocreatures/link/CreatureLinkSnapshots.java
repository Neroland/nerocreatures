package za.co.neroland.nerocreatures.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Entry;
import za.co.neroland.nerocreatures.bestiary.BestiaryProgress;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.block.IncubatorBlockEntity;
import za.co.neroland.nerocreatures.boss.AltarUses;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossFights;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.data.OwnershipCaps;
import za.co.neroland.nerocreatures.entity.mount.MountCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;
import za.co.neroland.nerocreatures.spawn.SpawnRule;
import za.co.neroland.nerocreatures.trait.TraitSet;
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * The read half of the link module (schema 2): what one player has discovered and hunted, what they
 * own, and the boss fights and altars that concern them. This class only gathers facts from the live
 * world for <b>the asking player</b>; {@link CreatureLinkJson} turns them into JSON.
 *
 * <h2>Sections</h2>
 *
 * <ul>
 *   <li>{@code bestiary} — every species with its habitat, kind and the player's own discovery tier
 *       (Neroland Core record gates); scanned species add the player's kill count (vanilla's own
 *       statistic, read while they are online), ability, drops and uses, study progress and owned
 *       count, and at Studied the weakness;</li>
 *   <li>{@code pets} — the player's own pets, mounts, drones and incubating eggs, with health, order,
 *       traits, saddle and stamina, breeding and hatch timers where the creature (or the egg's
 *       Incubator) is loaded;</li>
 *   <li>{@code bosses} — summoned fights in progress with the player's own share and a participant
 *       count, and the player's own altars by label number with their cooldowns.</li>
 * </ul>
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>No section carries a coordinate, another player's name or id, or a list of players: at most a
 * dimension id and a participant count. Lookups never load a chunk. Nothing here writes anything.
 *
 * <p>Server thread only.
 */
public final class CreatureLinkSnapshots implements LinkSnapshotProvider {

    private static final List<String> SECTIONS = List.of(
            CreatureLinkModule.SECTION_BESTIARY,
            CreatureLinkModule.SECTION_PETS,
            CreatureLinkModule.SECTION_BOSSES);

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
                    "[NeroCreatures] NeroLink snapshot section '{}' failed ({}); returning nothing for it.",
                    section, e.getClass().getSimpleName());
            NeroCreaturesTelemetry.captureHandledException(e, "link-snapshot", section);
            return new JsonObject();
        }
    }

    // --- bestiary -------------------------------------------------------------------

    private static JsonObject bestiary(MinecraftServer server, UUID playerId) {
        ServerPlayer player = CreatureLinkAccess.online(server, playerId);
        List<SpawnRule> rules = CreatureSpawns.rules();
        return CreatureLinkJson.bestiary(new CreatureLinkJson.BestiaryData() {
            @Override
            public boolean online() {
                return player != null;
            }

            @Override
            public Tier tier(Entry entry) {
                return BestiaryProgress.tier(server, playerId, entry.id());
            }

            @Override
            public long kills(Entry entry) {
                return player == null ? 0L : BestiaryProgress.kills(player, entry);
            }

            @Override
            public int owned(Entry entry) {
                return BestiaryProgress.owned(server, playerId, entry);
            }

            @Override
            public int goal(Entry entry) {
                return BestiaryProgress.goal(entry);
            }

            @Override
            public JsonArray spawns(Entry entry) {
                return spawnsFor(rules, entry.id());
            }
        }, NeroCreaturesConfig.SPAWNS_ENABLED.get());
    }

    /**
     * The schema-1 spawn-table lines for one creature id: biome tag, optional dimension tag, weight and
     * group size. Tags, never places.
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

    // --- pets -----------------------------------------------------------------------

    private static JsonObject pets(MinecraftServer server, UUID playerId) {
        return CreatureLinkJson.pets(CreatureOwnershipState.get(server), playerId, row -> live(server, row),
                CreatureLinkAccess.isOnline(server, playerId), OwnershipCaps::capFor);
    }

    /**
     * Live facts about one of the player's own rows. One entity-index lookup in the row's level (no
     * chunk is loaded), or one in-memory incubator lookup for an egg.
     */
    private static CreatureLinkJson.Live live(MinecraftServer server, OwnedCreature row) {
        if (row.kind() == OwnedCreature.Kind.EGG) {
            OptionalLong left = IncubatorBlockEntity.hatchSecondsLeft(row.entity());
            return left.isPresent() ? CreatureLinkJson.Live.egg(left.getAsLong()) : CreatureLinkJson.Live.UNLOADED;
        }
        if (row.parked()) {
            return CreatureLinkJson.Live.UNLOADED;
        }
        ServerLevel level = server.getLevel(row.dimensionKey());
        Entity entity = level == null ? null : level.getEntity(row.entity());
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return CreatureLinkJson.Live.UNLOADED;
        }
        String command = null;
        List<String> traits = List.of();
        boolean baby = living.isBaby();
        long breedReady = -1L;
        boolean saddled = false;
        double stamina = -1.0D;
        if (living instanceof TameableCreature pet) {
            command = pet.command().key();
            TraitSet set = pet.traits();
            traits = set == null ? List.of() : new ArrayList<>(set.effectiveIds());
            if (pet.breedable() && !baby) {
                int age = pet.getAge();
                breedReady = age > 0 ? (age + 19L) / 20L : 0L;
            }
        }
        if (living instanceof MountCreature mount) {
            saddled = mount.isCreatureSaddled();
            stamina = mount.stamina() / (double) MountCreature.MAX_STAMINA;
        }
        return new CreatureLinkJson.Live(true, living.getHealth(), living.getMaxHealth(), command, traits, saddled,
                stamina, baby, breedReady, -1L);
    }

    // --- bosses ---------------------------------------------------------------------

    private static JsonObject bosses(MinecraftServer server, UUID playerId) {
        List<CreatureLinkJson.Fight> fights = new ArrayList<>();
        for (BossController controller : BossFights.active()) {
            UUID fight = controller.fightId();
            if (fight == null) {
                continue; // only summoned fights have an id; a natural boss records nobody
            }
            fights.add(new CreatureLinkJson.Fight(fight, controller.bossId(), controller.dimensionId(),
                    controller.phaseNumber(), controller.phaseCount(), controller.healthFraction()));
        }
        return CreatureLinkJson.bosses(fights, BossContributionState.get(server), playerId,
                AltarUses.rows(server, playerId), CreatureLinkAccess.isOnline(server, playerId));
    }
}
