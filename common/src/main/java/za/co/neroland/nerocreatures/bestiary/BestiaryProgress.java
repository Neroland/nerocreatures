package za.co.neroland.nerocreatures.bestiary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Entry;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.StudyKind;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.breeding.SpeciesProfile;
import za.co.neroland.nerocreatures.breeding.SpeciesProfiles;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.network.BestiaryCountsPayload;
import za.co.neroland.nerocreatures.network.BestiaryEventPayload;
import za.co.neroland.nerocreatures.network.BestiaryRequestPayload;
import za.co.neroland.nerocreatures.network.ServerPayloadGuard;
import za.co.neroland.nerocreatures.platform.Services;
import za.co.neroland.nerocreatures.progression.CreatureMilestones;

/**
 * The one owner of the Bestiary gate ids: {@code nerocreatures:bestiary/scanned/<id>}
 * and {@code nerocreatures:bestiary/studied/<id>}, both player-scope Neroland Core record gates.
 * Nothing else opens them.
 *
 * <ul>
 *   <li><b>Scanned</b> opens when a Field Scanner scan completes on the server.</li>
 *   <li><b>Studied</b> opens once the species is Scanned and its rule is met: kills (the vanilla
 *       {@code ENTITY_KILLED} statistic, the only counter), or one tame, hatch or breed, or one
 *       harvest or breed, or a share in a summoned boss fight. A scan also checks the kill statistic
 *       and the player's current ownership rows, so earlier kills and pets still count.</li>
 *   <li>The Studied reward (experience only: 25, 60 for an elite, 150 for a boss) is granted only
 *       where {@link ProgressionGates#open} returns {@code true}, so exactly once per player per
 *       species. It needs the player online; a gate opened for an offline player (a hatch, a boss
 *       share) opens without the reward.</li>
 * </ul>
 *
 * <p><b>Privacy (POPIA/GDPR):</b> no new store. Gates live in Core's progression store, which Core
 * erases, retains and backs up. Nothing here logs a player. The request rate table is weakly keyed
 * by the player object and never saved.
 */
public final class BestiaryProgress {

    /** Scoreboard tag on every gallery display creature: never scanned, never counted. */
    public static final String GALLERY_TAG = "nerocreatures_gallery";

    /** Ticks between two answers to one player's counts request. */
    public static final int REQUEST_INTERVAL_TICKS = 20;

    private static final Map<ServerPlayer, Long> LAST_ANSWER = new WeakHashMap<>();

    private BestiaryProgress() {
    }

    // --- ids ----------------------------------------------------------------

    public static Identifier scannedGate(String species) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "bestiary/scanned/" + species);
    }

    public static Identifier studiedGate(String species) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "bestiary/studied/" + species);
    }

    /** The catalog entry for an entity type, if it is one of ours. */
    public static Optional<Entry> entryOf(EntityType<?> type) {
        Identifier key = EntityType.getKey(type);
        if (key == null || !NeroCreaturesCommon.MOD_ID.equals(key.getNamespace())) {
            return Optional.empty();
        }
        return BestiaryCatalog.get(key.getPath());
    }

    /** Delegates to the one gallery predicate, {@code gallery/GalleryDisplays#isDisplay}. */
    public static boolean isGallery(Entity entity) {
        return za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(entity);
    }

    /** The kill goal or 1, from the species row. */
    public static int goal(Entry entry) {
        SpeciesProfile profile = SpeciesProfiles.of(entry.id());
        int explicit = profile == SpeciesProfile.DEFAULT ? 0 : profile.studiedThreshold();
        return BestiaryRules.goal(entry.kind(), explicit);
    }

    // --- tiers ----------------------------------------------------------------

    public static Tier tier(MinecraftServer server, UUID player, String species) {
        if (ProgressionGates.isOpen(server, player, studiedGate(species))) {
            return Tier.STUDIED;
        }
        return ProgressionGates.isOpen(server, player, scannedGate(species)) ? Tier.SCANNED : Tier.NONE;
    }

    // --- scan -------------------------------------------------------------------

    /**
     * A completed scan. Opens Scanned, then checks the Studied rule against what is already known
     * (kill statistic, current ownership), sending {@code SCANNED}/{@code STUDIED} or
     * {@code NOTHING_NEW}.
     */
    public static void scan(ServerPlayer player, Entry entry) {
        boolean scannedNow = ProgressionGates.open(player, scannedGate(entry.id()));
        if (scannedNow) {
            send(player, BestiaryEventPayload.Kind.SCANNED, entry.id());
        }
        boolean studiedNow = false;
        StudyKind study = BestiaryRules.studyKind(entry.kind());
        if (study == StudyKind.KILLS) {
            studiedNow = BestiaryRules.killsMeetGoal(kills(player, entry), goal(entry)) && study(player, entry);
        } else if (study == StudyKind.TAME_OR_BREED && owned(player.level().getServer(), player.getUUID(), entry) > 0) {
            studiedNow = study(player, entry);
        }
        if (!scannedNow && !studiedNow) {
            send(player, BestiaryEventPayload.Kind.NOTHING_NEW, entry.id());
        }
    }

    // --- study paths ------------------------------------------------------------

    /** After a player kill. Cheap for anything that is not a scanned kill-studied species. */
    public static void noteKill(Entity victim, @Nullable Entity killer) {
        if (!(killer instanceof ServerPlayer player) || isGallery(victim)) {
            return;
        }
        entryOf(victim.getType()).ifPresent(entry -> {
            if (BestiaryRules.studyKind(entry.kind()) == StudyKind.KILLS
                    && BestiaryRules.killsMeetGoal(kills(player, entry), goal(entry))) {
                study(player, entry);
            }
        });
    }

    /** A player tamed, deployed or hatch-tamed one. */
    public static void noteTame(Player player, Entity creature) {
        if (player instanceof ServerPlayer serverPlayer && !isGallery(creature)) {
            entryOf(creature.getType()).ifPresent(entry -> {
                if (BestiaryRules.studyKind(entry.kind()) == StudyKind.TAME_OR_BREED) {
                    study(serverPlayer, entry);
                }
            });
        }
    }

    /**
     * Two animals bred. Credit goes to each parent's vanilla love cause (the player who fed it), never
     * to whoever stands nearest. Also records the {@code first_breed} milestone for the quest pack.
     */
    public static void noteBreed(ServerLevel level, Animal parent, Animal partner) {
        List<ServerPlayer> breeders = new ArrayList<>(2);
        addBreeder(breeders, parent.getLoveCause());
        addBreeder(breeders, partner.getLoveCause());
        for (ServerPlayer player : breeders) {
            CreatureMilestones.record(player, CreatureMilestones.FIRST_BREED);
            entryOf(parent.getType()).ifPresent(entry -> {
                StudyKind study = BestiaryRules.studyKind(entry.kind());
                if (study == StudyKind.TAME_OR_BREED || study == StudyKind.HARVEST_OR_BREED) {
                    study(player, entry);
                }
            });
        }
    }

    private static void addBreeder(List<ServerPlayer> out, @Nullable ServerPlayer player) {
        if (player != null && !out.contains(player)) {
            out.add(player);
        }
    }

    /** An egg hatched in an Incubator; credit goes to the player whose ownership row held the egg. */
    public static void noteHatch(MinecraftServer server, @Nullable UUID owner, EntityType<?> type) {
        if (owner == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            CreatureMilestones.record(online, CreatureMilestones.FIRST_BREED);
        } else {
            ProgressionGates.open(server, owner, CreatureMilestones.FIRST_BREED);
        }
        entryOf(type).ifPresent(entry -> {
            StudyKind study = BestiaryRules.studyKind(entry.kind());
            if (study == StudyKind.TAME_OR_BREED || study == StudyKind.HARVEST_OR_BREED) {
                study(server, owner, entry);
            }
        });
    }

    /**
     * A harvest interaction finished ({@code result} as returned by the species). Counts only on the
     * server and only when the interaction consumed the action (something was actually harvested).
     */
    public static InteractionResult noteHarvest(Player player, Entity creature, InteractionResult result) {
        if (player instanceof ServerPlayer serverPlayer && result.consumesAction() && !isGallery(creature)) {
            entryOf(creature.getType()).ifPresent(entry -> {
                if (BestiaryRules.studyKind(entry.kind()) == StudyKind.HARVEST_OR_BREED) {
                    study(serverPlayer, entry);
                }
            });
        }
        return result;
    }

    /** A summoned fight resolved; {@code participant} had a share above zero. Works offline. */
    public static void noteBossShare(MinecraftServer server, UUID participant, EntityType<?> boss) {
        entryOf(boss).ifPresent(entry -> {
            if (BestiaryRules.studyKind(entry.kind()) == StudyKind.BOSS_FIGHT) {
                study(server, participant, entry);
            }
        });
    }

    private static boolean study(ServerPlayer player, Entry entry) {
        return study(player.level().getServer(), player.getUUID(), entry);
    }

    /**
     * Opens Studied if Scanned is open. The reward and the {@code STUDIED} event go to the player only
     * when the gate actually changed and they are online.
     */
    private static boolean study(MinecraftServer server, UUID player, Entry entry) {
        if (server == null || !ProgressionGates.isOpen(server, player, scannedGate(entry.id()))) {
            return false;
        }
        boolean opened = ProgressionGates.open(server, player, studiedGate(entry.id()));
        if (opened) {
            checkComplete(server, player);
        }
        int reward = BestiaryRules.rewardFor(opened, entry.kind());
        if (reward == 0) {
            return false;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.giveExperiencePoints(reward);
            send(online, BestiaryEventPayload.Kind.STUDIED, entry.id());
        }
        return true;
    }

    // --- counts -------------------------------------------------------------------

    /** The vanilla kill statistic for one species, read only. */
    public static long kills(ServerPlayer player, Entry entry) {
        Optional<EntityType<?>> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(
                Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, entry.id()));
        if (type.isEmpty()) {
            return 0L;
        }
        return player.getStats().getValue(Stats.ENTITY_KILLED.get(type.get()));
    }

    /** Rows of this species the player owns right now (eggs excluded). */
    public static int owned(MinecraftServer server, UUID player, Entry entry) {
        if (server == null) {
            return 0;
        }
        Identifier type = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, entry.id());
        int count = 0;
        for (OwnedCreature row : CreatureOwnershipState.get(server).owned(player)) {
            if (row.kind() != OwnedCreature.Kind.EGG && type.equals(row.type())) {
                count++;
            }
        }
        return count;
    }

    /** The counts payload for one player: only that player's own data, only scanned species. */
    public static BestiaryCountsPayload counts(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID id = player.getUUID();
        return buildCounts(new PlayerData() {
            @Override
            public Tier tier(Entry entry) {
                return BestiaryProgress.tier(server, id, entry.id());
            }

            @Override
            public long kills(Entry entry) {
                return BestiaryProgress.kills(player, entry);
            }

            @Override
            public int owned(Entry entry) {
                return BestiaryProgress.owned(server, id, entry);
            }
        }, BestiaryProgress::goal);
    }

    /** One player's own data, as the counts payload reads it. Every lookup is for that player only. */
    interface PlayerData {
        Tier tier(Entry entry);

        long kills(Entry entry);

        int owned(Entry entry);
    }

    /**
     * Builds the payload from one player's lookups. Pure, so the "only the asking player's data"
     * rule is tested without a server: nothing here can reach any other player.
     */
    static BestiaryCountsPayload buildCounts(PlayerData data, java.util.function.ToIntFunction<Entry> goals) {
        List<BestiaryCountsPayload.Entry> out = new ArrayList<>();
        for (Entry entry : BestiaryCatalog.entries()) {
            Tier tier = data.tier(entry);
            if (tier == Tier.NONE) {
                continue;
            }
            StudyKind study = BestiaryRules.studyKind(entry.kind());
            int goal = goals.applyAsInt(entry);
            long kills = data.kills(entry);
            int owned = entry.ownable() ? data.owned(entry) : 0;
            int tracked = BestiaryCountsPayload.TRACK_KILLS | (entry.ownable() ? BestiaryCountsPayload.TRACK_OWNED : 0);
            out.add(new BestiaryCountsPayload.Entry(entry.id(), tier.wire(), study.wire(),
                    BestiaryRules.progress(study, goal, kills, tier == Tier.STUDIED), goal, tracked,
                    (int) Math.min(9_999_999L, Math.max(0L, kills)), Math.min(9999, owned), 0, 0));
        }
        return new BestiaryCountsPayload(BestiaryCountsPayload.SCHEMA, out);
    }

    /**
     * {@code bestiary_request} handler: answers the sender only, with only the sender's data, at most
     * once per {@value #REQUEST_INTERVAL_TICKS} ticks. Extra or malformed requests are dropped silently.
     */
    public static void handleRequest(BestiaryRequestPayload payload, ServerPlayer player) {
        if (payload.schema() != BestiaryRequestPayload.SCHEMA || !ServerPayloadGuard.senderMayAct(player)) {
            return;
        }
        long now = player.level().getGameTime();
        if (!mayAnswer(LAST_ANSWER.get(player), now)) {
            return;
        }
        LAST_ANSWER.put(player, now);
        // Catches players who studied every species before the completion gate existed.
        checkComplete(player.level().getServer(), player.getUUID());
        Services.NETWORK.sendToPlayer(player, counts(player));
    }

    /**
     * Opens {@code nerocreatures:bestiary/complete} once every catalogue species is Studied for
     * {@code player}. A record gate in Core's store, nothing else; a no-op when already open.
     */
    static void checkComplete(@Nullable MinecraftServer server, UUID player) {
        if (server == null || ProgressionGates.isOpen(server, player, CreatureMilestones.BESTIARY_COMPLETE)) {
            return;
        }
        if (allStudied(BestiaryCatalog.entries(), id -> ProgressionGates.isOpen(server, player, studiedGate(id)))) {
            ProgressionGates.open(server, player, CreatureMilestones.BESTIARY_COMPLETE);
        }
    }

    /** Whether every entry is Studied according to {@code studied}. Pure, for tests; empty is false. */
    static boolean allStudied(List<Entry> entries, java.util.function.Predicate<String> studied) {
        if (entries.isEmpty()) {
            return false;
        }
        for (Entry entry : entries) {
            if (!studied.test(entry.id())) {
                return false;
            }
        }
        return true;
    }

    /** The per-player answer limit. Package-private for tests. */
    static boolean mayAnswer(@Nullable Long last, long now) {
        return last == null || now < last || now - last >= REQUEST_INTERVAL_TICKS;
    }

    static void send(ServerPlayer player, BestiaryEventPayload.Kind kind, String species) {
        Services.NETWORK.sendToPlayer(player, BestiaryEventPayload.of(kind, species));
    }
}
