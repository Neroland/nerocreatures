package za.co.neroland.nerocreatures.link;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Entry;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.StudyKind;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.boss.AltarUses;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;

/**
 * The three snapshot sections as JSON, built from plain inputs so the privacy rules are tested without
 * a server (link schema 2; every schema-1 member is kept).
 *
 * <p>Every builder reads <b>one player's</b> data: the Bestiary through a {@link BestiaryData} that
 * answers for the asking player only, pets through {@link CreatureOwnershipState#owned(UUID)} for that
 * player, boss shares through {@link BossContributionState} for that player. No builder writes a
 * coordinate, a player name or a player id; a creature appears by its opaque entity id only in its own
 * owner's {@code pets} section.
 */
public final class CreatureLinkJson {

    /** Longest name the app shows. */
    public static final int MAX_NAME = 64;

    /** Longest ability, weakness, overworld or drop-use line. */
    public static final int MAX_TEXT = 220;

    /** Most drops listed per creature. */
    public static final int MAX_DROPS = 8;

    /** The commands the app may send for a pet or a mount. */
    public static final List<String> REMOTE_COMMANDS = List.of("follow", "stay", "sit");

    private CreatureLinkJson() {
    }

    // --- bestiary -----------------------------------------------------------------

    /** One player's Bestiary facts; every method answers for that player only. */
    public interface BestiaryData {
        /** Whether the player is online, so their vanilla kill statistic can be read. */
        boolean online();

        Tier tier(Entry entry);

        /** Kill count (only asked while online). */
        long kills(Entry entry);

        /** Owned now (only asked for ownable species). */
        int owned(Entry entry);

        /** The study goal for the species. */
        int goal(Entry entry);

        /** The schema-1 spawn rows for the species. */
        JsonArray spawns(Entry entry);
    }

    /**
     * {@code bestiary}: every species in list order with its habitat, kind and the asking player's own
     * discovery tier. An unscanned (tier 0) entry carries <b>only</b> {@code id}, {@code habitat},
     * {@code kind} and {@code tier}; a scanned one adds the schema-1 members ({@code name},
     * {@code translation_key}, {@code killed}, {@code spawns}) and the schema-2 text: ability, drops
     * with uses, study progress, owned count, sigil, overworld line, and at tier 2 the weakness.
     */
    public static JsonObject bestiary(BestiaryData data, boolean spawnsEnabled) {
        boolean online = data.online();
        JsonArray creatures = new JsonArray();
        long totalKills = 0L;
        int speciesKilled = 0;
        for (Entry entry : BestiaryCatalog.entries()) {
            long kills = online ? Math.max(0L, data.kills(entry)) : 0L;
            totalKills += kills;
            if (kills > 0L) {
                speciesKilled++;
            }
            creatures.add(bestiaryRow(data, entry, online, kills));
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", online);
        if (online) {
            root.addProperty("total_kills", Math.min(Integer.MAX_VALUE, totalKills));
            root.addProperty("species_killed", speciesKilled);
        }
        root.addProperty("spawns_enabled", spawnsEnabled);
        root.add("creatures", creatures);
        return root;
    }

    private static JsonObject bestiaryRow(BestiaryData data, Entry entry, boolean online, long kills) {
        String id = entry.id();
        Tier tier = data.tier(entry);
        JsonObject row = new JsonObject();
        row.addProperty("id", NeroCreaturesCommon.MOD_ID + ":" + id);
        row.addProperty("habitat", entry.habitat().id());
        row.addProperty("kind", entry.kind().key());
        row.addProperty("tier", tier.ordinal());
        if (tier == Tier.NONE) {
            return row; // a silhouette: nothing else, so nothing is spoiled
        }
        row.addProperty("name", LinkText.clip(LinkText.speciesName(id), MAX_NAME));
        row.addProperty("translation_key", "entity." + NeroCreaturesCommon.MOD_ID + "." + id);
        if (online) {
            row.addProperty("killed", Math.min(9_999_999L, kills));
        }
        row.add("spawns", data.spawns(entry));
        text(row, "ability", "bestiary.nerocreatures." + id + ".ability");
        if (tier == Tier.STUDIED) {
            text(row, "weakness", "bestiary.nerocreatures." + id + ".weakness");
        }
        if (entry.overworld()) {
            text(row, "also", "bestiary.nerocreatures." + id + ".also");
        }
        JsonArray drops = new JsonArray();
        for (String item : entry.drops()) {
            if (drops.size() >= MAX_DROPS) {
                break;
            }
            String path = item.substring(item.indexOf(':') + 1);
            JsonObject drop = new JsonObject();
            drop.addProperty("item", item);
            drop.addProperty("name", LinkText.clip(LinkText.itemName(path), MAX_NAME));
            LinkText.get("bestiary.nerocreatures.drop." + path + ".use")
                    .ifPresent(use -> drop.addProperty("use", LinkText.clip(use, MAX_TEXT)));
            drops.add(drop);
        }
        row.add("drops", drops);
        StudyKind study = BestiaryRules.studyKind(entry.kind());
        boolean studied = tier == Tier.STUDIED;
        // Kill progress needs the live statistic; offline, an unfinished kill study is left out rather
        // than shown as zero.
        if (studied || study != StudyKind.KILLS || online) {
            int goal = Math.max(1, Math.min(1000, data.goal(entry)));
            JsonObject progress = new JsonObject();
            progress.addProperty("kind", study.name().toLowerCase(java.util.Locale.ROOT));
            progress.addProperty("progress", BestiaryRules.progress(study, goal, kills, studied));
            progress.addProperty("goal", goal);
            row.add("study", progress);
        }
        if (entry.ownable()) {
            row.addProperty("owned", Math.max(0, Math.min(9999, data.owned(entry))));
        }
        entry.sigil().ifPresent(sigil -> row.addProperty("sigil", LinkText.clip(LinkText.itemName(sigil), MAX_NAME)));
        return row;
    }

    private static void text(JsonObject row, String member, String key) {
        LinkText.get(key).ifPresent(line -> row.addProperty(member, LinkText.clip(line, MAX_TEXT)));
    }

    // --- pets ---------------------------------------------------------------------

    /**
     * What the live world says about one owned creature. {@link #UNLOADED} when it is not loaded (or is
     * parked, or is an egg whose Incubator is not loaded).
     *
     * @param loaded         the creature (or an egg's Incubator) is loaded right now
     * @param health         current health (creatures only)
     * @param maxHealth      maximum health; below 1 the pair is left out
     * @param command        standing order key, or {@code null}
     * @param traits         effective trait ids, at most two
     * @param saddled        a mount wears a saddle
     * @param stamina        mount stamina 0..1, or a negative number for none
     * @param baby           not grown up yet
     * @param breedReadyInS  seconds until it may breed again (0 = now), or negative for no breeding line
     * @param hatchInS       an egg's seconds to hatch, or negative when unknown
     */
    public record Live(boolean loaded, float health, float maxHealth, @Nullable String command, List<String> traits,
            boolean saddled, double stamina, boolean baby, long breedReadyInS, long hatchInS) {

        public static final Live UNLOADED = new Live(false, 0.0F, 0.0F, null, List.of(), false, -1.0D, false, -1L, -1L);

        /** An egg whose Incubator is loaded. */
        public static Live egg(long hatchInS) {
            return new Live(true, 0.0F, 0.0F, null, List.of(), false, -1.0D, false, -1L, hatchInS);
        }
    }

    /**
     * {@code pets}: the asking player's own ownership rows (pets, mounts, drones, eggs) with live status
     * where known, plus their four caps.
     *
     * @param caps the configured cap per kind
     */
    public static JsonObject pets(CreatureOwnershipState state, UUID player, Function<OwnedCreature, Live> live,
            boolean online, ToIntFunction<OwnedCreature.Kind> caps) {
        JsonArray rows = new JsonArray();
        for (OwnedCreature owned : state.owned(player)) {
            rows.add(petRow(owned, live.apply(owned)));
        }
        JsonObject capsJson = new JsonObject();
        capsJson.add("pets", cap(state.count(player, OwnedCreature.Kind.PET), caps.applyAsInt(OwnedCreature.Kind.PET)));
        capsJson.add("mounts", cap(state.count(player, OwnedCreature.Kind.MOUNT), caps.applyAsInt(OwnedCreature.Kind.MOUNT)));
        capsJson.add("drones", cap(state.count(player, OwnedCreature.Kind.DRONE), caps.applyAsInt(OwnedCreature.Kind.DRONE)));
        capsJson.add("eggs", cap(state.count(player, OwnedCreature.Kind.EGG), caps.applyAsInt(OwnedCreature.Kind.EGG)));
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", online);
        root.add("caps", capsJson);
        root.add("pets", rows);
        return root;
    }

    private static JsonObject petRow(OwnedCreature owned, Live live) {
        OwnedCreature.Kind kind = owned.kind();
        boolean creature = kind == OwnedCreature.Kind.PET || kind == OwnedCreature.Kind.MOUNT;
        JsonObject row = new JsonObject();
        row.addProperty("entity", owned.entity().toString());
        row.addProperty("kind", kind.key());
        row.addProperty("type", owned.type().toString());
        row.addProperty("name", LinkText.clip(speciesName(owned.type()), MAX_NAME));
        row.addProperty("dimension", owned.dimension().toString());
        row.addProperty("status", live.loaded() ? "loaded" : "unloaded");
        JsonArray commands = new JsonArray();
        if (creature) {
            REMOTE_COMMANDS.forEach(commands::add);
        }
        row.add("commands", commands);
        row.addProperty("can_recall", creature);
        if (!live.loaded()) {
            return row;
        }
        if (kind == OwnedCreature.Kind.EGG) {
            if (live.hatchInS() >= 0L) {
                row.addProperty("hatch_in_s", live.hatchInS());
            }
            return row;
        }
        if (live.maxHealth() >= 1.0F) {
            row.addProperty("health", live.health());
            row.addProperty("max_health", live.maxHealth());
        }
        if (live.command() != null) {
            row.addProperty("command", live.command());
        }
        JsonArray traits = new JsonArray();
        for (String trait : live.traits()) {
            if (traits.size() < 2) {
                traits.add(trait);
            }
        }
        row.add("traits", traits);
        row.addProperty("baby", live.baby());
        if (kind == OwnedCreature.Kind.MOUNT) {
            row.addProperty("saddled", live.saddled());
            if (live.stamina() >= 0.0D) {
                row.addProperty("stamina", Math.min(1.0D, live.stamina()));
            }
        }
        if (live.breedReadyInS() >= 0L) {
            row.addProperty("breed_ready_in_s", live.breedReadyInS());
        }
        return row;
    }

    private static JsonObject cap(int used, int max) {
        JsonObject entry = new JsonObject();
        entry.addProperty("used", used);
        entry.addProperty("max", max);
        return entry;
    }

    /** A species' English name from its id ({@code nerocreatures:rime_yak} &rarr; "Rime Yak"). */
    public static String speciesName(Identifier type) {
        return NeroCreaturesCommon.MOD_ID.equals(type.getNamespace())
                ? LinkText.speciesName(type.getPath())
                : CreatureLinkAccess.readablePath(type.getPath());
    }

    // --- bosses -------------------------------------------------------------------

    /** One summoned fight as the live index reports it: a boss, a place id and progress; no people. */
    public record Fight(UUID fight, Identifier boss, String dimension, int phase, int phaseCount, float health) {
    }

    /**
     * {@code bosses}: the summoned fights in progress that the asking player is recorded in (they
     * summoned it or hit the boss), with their own damage and share and a participant <b>count</b>; and
     * (schema 2) the player's own altars by label number only. Fights they are not in are left out
     * entirely, so nobody learns where somebody else is fighting or how many are with them.
     */
    public static JsonObject bosses(List<Fight> fights, BossContributionState contribution, UUID player,
            List<AltarUses.Row> altars, boolean online) {
        JsonArray rows = new JsonArray();
        for (Fight fight : fights) {
            if (!contribution.isParticipant(fight.fight(), player)) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("fight", fight.fight().toString());
            row.addProperty("boss", fight.boss().toString());
            row.addProperty("name", LinkText.clip(speciesName(fight.boss()), MAX_NAME));
            row.addProperty("dimension", fight.dimension());
            row.addProperty("phase", fight.phase());
            row.addProperty("phase_count", fight.phaseCount());
            row.addProperty("health", fight.health());
            row.addProperty("your_damage", contribution.damageOf(fight.fight(), player));
            row.addProperty("your_share", contribution.shareOf(fight.fight(), player));
            row.addProperty("participants", contribution.participantCount(fight.fight()));
            rows.add(row);
        }
        JsonArray altarRows = new JsonArray();
        for (AltarUses.Row altar : altars) {
            JsonObject row = new JsonObject();
            row.addProperty("index", altar.index());
            row.addProperty("state", altar.state().key());
            if (altar.state() == AltarUses.State.COOLDOWN) {
                row.addProperty("ready_in_s", altar.readyInS());
            }
            if (altar.lastBoss() != null) {
                row.addProperty("last_boss", altar.lastBoss());
            }
            altarRows.add(row);
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", online);
        root.add("fights", rows);
        root.add("altars", altarRows);
        return root;
    }
}
