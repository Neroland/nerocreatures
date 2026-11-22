package za.co.neroland.nerocreatures.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * The one server-wide index of who owns which creature: {@code player UUID → list of}
 * {@link OwnedCreature} {@code rows}, plus one last-updated epoch-millis stamp per player that
 * drives retention.
 *
 * <p>It exists for three jobs and no others:
 *
 * <ol>
 *   <li><b>Caps.</b> {@code maxPetsPerPlayer} and {@code maxDronesPerPlayer} are enforced at tame
 *       time and deploy time, and the only way to answer "how many does this player already have"
 *       without loading every chunk in the save is to keep an index.</li>
 *   <li><b>Erasure.</b> A POPIA/GDPR erase request has to find and neutralise every creature bound
 *       to a player, including ones in unloaded dimensions.</li>
 *   <li><b>Access.</b> {@link #exportPlayer} answers a data-access request with exactly one
 *       player's own rows.</li>
 * </ol>
 *
 * <p>Persisted as vanilla {@link SavedData} on the overworld (so it is always loaded) through the
 * same {@link SavedDataType} + Codec pattern Neroland Core uses for its own state. Every accessor
 * goes through Neroland Core's {@link SavedDataRecovery}, so a corrupt file degrades to Core's
 * last-known-good backup (or an empty index) instead of crashing the server.
 *
 * <p><b>Threading:</b> server thread only — interactions, the deploy item, commands and the erasure
 * hook all run there. Nothing here is synchronised.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Rows are keyed by the player's existing Minecraft game UUID and
 * hold only entity UUIDs, entity-type ids, dimension ids and a timestamp — no names, IPs, chat or
 * <b>coordinates of any kind</b>. Three controls apply:
 * <ul>
 *   <li><b>Erasure</b> — {@link #forgetPlayer(UUID)} purges a UUID completely and hands back what it
 *       removed so {@link CreatureData} can free the creatures themselves. It is wired into Core's
 *       shared {@code PlayerDataErasure} hook from {@link CreatureData#init()}, which then calls
 *       {@link #backupNow} so the purge also reaches Core's backup copy. Erasure never logs player
 *       identity. Erasure also leaves two markers that name nobody, so a creature in an unloaded
 *       chunk is released when it next loads: a salted one-way hash of the erased owner and the
 *       entity UUIDs of the rows it removed (see {@link #loadVerdict}).</li>
 *   <li><b>Retention</b> — inactivity has one definition across the Neroland mods: Core's
 *       {@code PlayerActivity} last-seen time. Core's purge-inactive sweep erases an inactive player
 *       through the same registered eraser, so this store keeps no inactivity clock of its own (a
 *       player who logs in daily but has not tamed anything for a month is <em>active</em>). The
 *       per-player stamp here records only when the rows last changed, for that player's export.
 *       Owner erasure markers are the one thing this store expires itself.</li>
 *   <li><b>Access</b> — {@link #exportPlayer(MinecraftServer, UUID)} returns exactly one player's own
 *       rows, and nobody else's.</li>
 * </ul>
 *
 * <p>One thing this store deliberately does <em>not</em> try to be is the source of truth for
 * ownership. That lives on the entity, where vanilla puts it (a tamed animal's owner is part of
 * {@code TamableAnimal}'s own saved data). This is an index over it: if the two ever disagree, the
 * entity wins and the index is repaired the next time the creature is interacted with.
 */
public final class CreatureOwnershipState extends SavedData {

    /** Stable, non-identifying label used for the storage file and recovery logs. */
    public static final String NAME = NeroCreaturesCommon.MOD_ID + ":ownership";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "ownership");

    public static final SavedDataType<CreatureOwnershipState> TYPE =
            SavedDataCompat.type(ID, CreatureOwnershipState::new, codec());

    /**
     * Upper bound on rows kept for one player. Far above any sane cap
     * ({@code maxPetsPerPlayer} + {@code maxDronesPerPlayer} both max out at 64); it exists so a
     * buggy caller cannot grow a row list without limit.
     */
    private static final int MAX_ROWS_PER_PLAYER = 256;

    private static final long MILLIS_PER_DAY = 86_400_000L;

    /**
     * Upper bound on each erasure-marker set, so a runaway caller cannot grow the file without limit.
     * Reaching it is never silent: every marker dropped at the cap is counted and logged at WARN,
     * because a creature whose marker was dropped is re-indexed on load instead of released. 65,536
     * creature UUIDs is about 2.4 MB of save data.
     */
    static final int MAX_ERASURE_MARKERS = 65_536;

    /**
     * The shortest life an <em>owner</em> erasure marker (the salted hash) gets: one year. The real
     * lifetime is {@code max(dataRetentionDays, 365)} ({@link #markerLifetimeDays}): a marker that
     * expired with the retention period would defeat itself on a short-retention server. Creature
     * markers do not expire at all; see {@link #pruneErasureMarkers}. See {@code PRIVACY.md},
     * "Erasure markers".
     */
    public static final int ERASURE_MARKER_DAYS = 365;

    /** Markers dropped at {@link #MAX_ERASURE_MARKERS} since the last WARN; server thread only. */
    private int markersDroppedAtCap;

    /** Wall clock for retention stamps; tests swap it. Server thread only. */
    static LongSupplier clock = System::currentTimeMillis;

    /** What the load-time ownership check does with one tamed creature. */
    public enum LoadVerdict {
        /** The row is there; nothing to do. */
        INDEXED,
        /** No row and no erasure record: the entity wins, the row is re-added (an index gap). */
        REINDEX,
        /** Erasure (or retention) positively recorded this creature or its owner: release it. */
        RELEASE
    }

    /**
     * Root schema written by this version. 0.4.x files have no {@code schema} key and read as 1; the
     * row shape is unchanged between 1 and 2 (2 adds the {@code mount} and {@code egg} kinds and the
     * optional {@code hatch_at} field, all read leniently), so nothing is migrated on load and nothing
     * is rewritten until the next ordinary save.
     */
    public static final int SCHEMA = 2;

    /**
     * The server whose marker expiry has already run, so the lazy check in {@link #get} fires at
     * most once per server instance. Written on the server thread; {@code volatile} only so an
     * integrated-server restart in the same JVM is seen promptly.
     */
    private static volatile MinecraftServer prunedFor;

    /** When marker expiry last ran for {@link #prunedFor}, so a long-running server re-runs it daily. */
    private static volatile long prunedAt;

    private final Map<UUID, List<OwnedCreature>> byPlayer = new LinkedHashMap<>();
    private final Map<UUID, Long> lastUpdated = new LinkedHashMap<>();

    /**
     * Lifetime hand-tame count per player, for the {@code tamed/10} and {@code tamed/25} record gates.
     * Stored only up to {@link #TAME_COUNT_CAP} (the highest threshold), so it never says more than
     * those gates need. Erased with the player, shown in their export.
     */
    private final Map<UUID, Integer> tameCounts = new LinkedHashMap<>();

    /** The highest tame-count threshold any gate needs; counts stop here (data minimisation). */
    public static final int TAME_COUNT_CAP = 25;

    /**
     * Erasure markers (load-time half of erasure). {@code erasedOwners} holds a salted one-way hash
     * of each erased owner, never the UUID itself; {@code erasedCreatures} holds the entity UUIDs of
     * the rows erasure removed and could not free because they were not loaded. Neither names a
     * player. The salt is random per world, lives only in this file and is deleted again as soon as
     * no owner marker needs it. Each marker carries the epoch-millis it was written, for retention.
     */
    private String erasureSalt = "";
    private final Map<String, Long> erasedOwners = new LinkedHashMap<>();
    private final Map<UUID, Long> erasedCreatures = new LinkedHashMap<>();

    public CreatureOwnershipState() {
    }

    /**
     * The one store, on the overworld so it is always loaded. Expires old owner erasure markers on
     * the first call for a given server instance and then daily.
     */
    public static CreatureOwnershipState get(MinecraftServer server) {
        CreatureOwnershipState state = SavedDataRecovery.get(
                server.overworld(), TYPE, CreatureOwnershipState::new, NAME);
        long now = clock.getAsLong();
        if (prunedFor != server || now - prunedAt >= MILLIS_PER_DAY) {
            // Set first: pruning must never re-enter this check. Re-run daily on a long-running server.
            prunedFor = server;
            prunedAt = now;
            if (CreatureData.applyRetention(state) > 0) {
                // Markers went after Core's get() refreshed the backup; push the expiry into it.
                state.backupNow(server);
            }
            CreatureData.applyExportRetention(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT), now);
        }
        state.reportMarkersDroppedAtCap();
        return state;
    }

    /** Logs (count only, at WARN) any erasure markers dropped at the cap since the last report. */
    void reportMarkersDroppedAtCap() {
        if (this.markersDroppedAtCap > 0) {
            int dropped = this.markersDroppedAtCap;
            this.markersDroppedAtCap = 0;
            NeroCreaturesCommon.LOGGER.warn(
                    "[NeroCreatures] {} erasure marker(s) dropped at the {} cap; the creatures they covered "
                            + "will be re-indexed instead of released when they next load.",
                    dropped, MAX_ERASURE_MARKERS);
        }
    }

    /** How many markers have been dropped at the cap and not yet reported (tests). */
    int markersDroppedAtCap() {
        return this.markersDroppedAtCap;
    }

    /**
     * Refreshes Core's last-known-good backup of this store now (hash-compared, so unchanged state
     * writes nothing). The backup is a second copy of the same player-keyed rows, so every erasure
     * and retention purge calls this straight after (POPIA/GDPR; Core's {@code SavedDataRecovery}
     * contract).
     */
    public void backupNow(MinecraftServer server) {
        SavedDataRecovery.backupNow(server.overworld(), TYPE, this, NAME, server.registryAccess());
    }

    // --- queries ------------------------------------------------------------

    /** How many creatures of one kind a player currently owns. */
    public int count(UUID player, OwnedCreature.Kind kind) {
        List<OwnedCreature> rows = this.byPlayer.get(player);
        if (rows == null) {
            return 0;
        }
        int total = 0;
        for (OwnedCreature row : rows) {
            if (row.kind() == kind) {
                total++;
            }
        }
        return total;
    }

    /** Every row stored for one player, as an immutable copy (never null). */
    public List<OwnedCreature> owned(UUID player) {
        List<OwnedCreature> rows = this.byPlayer.get(player);
        return rows == null || rows.isEmpty() ? List.of() : List.copyOf(rows);
    }

    /**
     * Whether the store holds anything at all for this player (rows or a retention stamp). Not used by
     * the load-time check: "unknown" is not "erased" (see {@link #loadVerdict}).
     */
    public boolean knowsPlayer(UUID player) {
        return this.byPlayer.containsKey(player) || this.lastUpdated.containsKey(player)
                || this.tameCounts.containsKey(player);
    }

    /**
     * Counts one successful hand-tame for {@code player}, saturating at {@link #TAME_COUNT_CAP}.
     *
     * @return the player's count after this tame
     */
    public int noteTame(UUID player) {
        int before = this.tameCounts.getOrDefault(player, 0);
        if (before >= TAME_COUNT_CAP) {
            return before;
        }
        int after = before + 1;
        this.tameCounts.put(player, after);
        this.setDirty();
        return after;
    }

    /** The player's lifetime hand-tame count (saturated at {@link #TAME_COUNT_CAP}). */
    public int tameCount(UUID player) {
        return this.tameCounts.getOrDefault(player, 0);
    }

    /**
     * The load-time ownership check for one tamed creature. Release happens only when
     * erasure or retention <b>positively recorded</b> this creature or its owner; an owner the index
     * merely does not know (a 0.4.x pet tamed before its row existed, a store that recovered empty,
     * an owner whose other rows were all removed) is re-indexed instead, so no world loses a pet to
     * an index gap.
     */
    public LoadVerdict loadVerdict(UUID owner, UUID entity) {
        if (this.contains(owner, entity)) {
            return LoadVerdict.INDEXED;
        }
        if (this.erasedCreatures.containsKey(entity)) {
            return LoadVerdict.RELEASE;
        }
        if (this.hasErasureMarker(owner)) {
            return LoadVerdict.RELEASE;
        }
        return LoadVerdict.REINDEX;
    }

    /**
     * Whether erasure left an owner marker for this player (a salted one-way hash; the UUID itself is
     * never stored). Used by the load-time check and reported, as a yes/no, in that player's own export.
     */
    public boolean hasErasureMarker(UUID owner) {
        return !this.erasureSalt.isEmpty() && this.erasedOwners.containsKey(this.ownerMarker(owner));
    }

    /** How many owner and creature markers the store holds (for tests and the retention log count). */
    public int erasureMarkerCount() {
        return this.erasedOwners.size() + this.erasedCreatures.size();
    }

    /** A creature erasure pointed at has now been freed; its marker is no longer needed. */
    public void creatureFreed(UUID entity) {
        if (this.erasedCreatures.remove(entity) != null) {
            this.setDirty();
        }
    }

    /** Whether this exact creature is already indexed for this player. */
    public boolean contains(UUID player, UUID entity) {
        List<OwnedCreature> rows = this.byPlayer.get(player);
        if (rows == null) {
            return false;
        }
        for (OwnedCreature row : rows) {
            if (row.entity().equals(entity)) {
                return true;
            }
        }
        return false;
    }

    // --- edits --------------------------------------------------------------

    /**
     * Indexes one creature against its owner. Idempotent: re-registering the same entity replaces
     * the existing row (which is how a drone that changed dimension gets its dimension id fixed).
     */
    public void add(UUID player, OwnedCreature owned) {
        // A new row supersedes any erasure marker: the player is back, or the creature was re-tamed.
        this.erasedCreatures.remove(owned.entity());
        if (!this.erasureSalt.isEmpty()) {
            this.erasedOwners.remove(this.ownerMarker(player));
            this.dropSaltIfUnused();
        }
        List<OwnedCreature> rows = this.byPlayer.computeIfAbsent(player, key -> new ArrayList<>());
        rows.removeIf(row -> row.entity().equals(owned.entity()));
        if (rows.size() >= MAX_ROWS_PER_PLAYER) {
            NeroCreaturesCommon.LOGGER.warn(
                    "[NeroCreatures] Ownership index row limit reached for one player record; "
                            + "refusing to add another creature.");
            return;
        }
        rows.add(owned);
        this.touch(player);
    }

    /**
     * Cap-checked insert: adds the row only if the player owns fewer than {@code cap} rows of its
     * kind (a cap of 0 or less refuses everything). Re-registering an entity that is already indexed
     * always succeeds, because it does not raise the count.
     *
     * @return {@code true} if the row is now indexed
     */
    public boolean tryAdd(UUID player, OwnedCreature owned, int cap) {
        if (this.contains(player, owned.entity())) {
            this.add(player, owned);
            return true;
        }
        if (cap <= 0 || this.count(player, owned.kind()) >= cap) {
            return false;
        }
        int before = this.owned(player).size();
        this.add(player, owned);
        return this.owned(player).size() > before;
    }

    /**
     * Drops one creature from a player's rows — a recalled drone, a pet that died, an untamed pet.
     *
     * @return {@code true} if a row was actually removed
     */
    public boolean remove(UUID player, UUID entity) {
        List<OwnedCreature> rows = this.byPlayer.get(player);
        if (rows == null || !rows.removeIf(row -> row.entity().equals(entity))) {
            return false;
        }
        if (rows.isEmpty()) {
            this.byPlayer.remove(player);
        }
        this.touch(player);
        return true;
    }

    /**
     * The player whose row holds {@code entity} (an egg's incubation id, for example), or
     * {@code null}. One pass over the index; used only on rare events such as a hatch.
     */
    @Nullable
    public UUID ownerOfRow(UUID entity) {
        for (Map.Entry<UUID, List<OwnedCreature>> owner : this.byPlayer.entrySet()) {
            for (OwnedCreature row : owner.getValue()) {
                if (row.entity().equals(entity)) {
                    return owner.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Drops one row by its id alone, whoever it belongs to: an Incubator's egg row when the egg hatches,
     * is taken back out or its incubator breaks. The block entity only knows the random incubation id,
     * never the player, so it cannot call {@link #remove(UUID, UUID)}. One pass over the
     * index; eggs are rare events.
     *
     * @return {@code true} if a row was actually removed
     */
    public boolean removeRow(UUID entity) {
        for (UUID player : List.copyOf(this.byPlayer.keySet())) {
            if (this.remove(player, entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks one indexed creature parked (a companion mod took it out of the world into storage) or
     * unparked (it was re-created; {@code dimension} is where). Only an existing row changes: a row
     * that erasure or retention removed is never re-added here, so the load-time check still releases
     * a pet that was erased while parked. Does not touch the erasure markers.
     *
     * @return {@code true} if the row exists (whether or not it changed)
     */
    public boolean setParked(UUID player, UUID entity, boolean parked, @Nullable Identifier dimension) {
        List<OwnedCreature> rows = this.byPlayer.get(player);
        if (rows == null) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            OwnedCreature row = rows.get(i);
            if (row.entity().equals(entity)) {
                OwnedCreature updated = row.withParked(parked, dimension);
                if (!updated.equals(row)) {
                    rows.set(i, updated);
                    this.touch(player);
                }
                return true;
            }
        }
        return false;
    }

    /** Whether this player's row for this creature is marked parked. */
    public boolean isParked(UUID player, UUID entity) {
        for (OwnedCreature row : this.byPlayer.getOrDefault(player, List.of())) {
            if (row.entity().equals(entity)) {
                return row.parked();
            }
        }
        return false;
    }

    // --- privacy: erasure, retention, export --------------------------------

    /**
     * POPIA/GDPR erasure: drop everything stored for a player and hand back the rows that were
     * removed so the caller can free the creatures they point at. Never logs player identity.
     *
     * @return the removed rows, in insertion order (empty if the player had none)
     */
    public List<OwnedCreature> forgetPlayer(UUID player) {
        List<OwnedCreature> removed = this.byPlayer.remove(player);
        this.lastUpdated.remove(player);
        this.tameCounts.remove(player);
        // Positive erasure record for the load-time check: a salted hash of the owner (covers
        // creatures the index never knew about) and the entity UUIDs of the removed rows. The caller
        // clears an entity marker again once it frees that creature.
        if (this.erasureSalt.isEmpty()) {
            this.erasureSalt = newSalt();
        }
        long now = clock.getAsLong();
        this.markersDroppedAtCap += addBounded(this.erasedOwners, this.ownerMarker(player), now);
        if (removed != null) {
            for (OwnedCreature row : removed) {
                if (row.kind() != OwnedCreature.Kind.EGG) {
                    this.markersDroppedAtCap += addBounded(this.erasedCreatures, row.entity(), now);
                }
            }
        }
        this.setDirty();
        return removed == null ? List.of() : List.copyOf(removed);
    }

    /**
     * The owner-marker lifetime for a given {@code dataRetentionDays}: {@code max(retention, 365)}.
     * Never shorter than a year, because a tombstone that expires with the retention period defeats
     * itself on a short-retention server (an erased pet in a chunk nobody visits for a month).
     */
    public static int markerLifetimeDays(int retentionDays) {
        return Math.max(retentionDays, ERASURE_MARKER_DAYS);
    }

    /**
     * Expiry for the <em>owner</em> erasure markers: drops every salted owner hash older than
     * {@code days} days, and the per-world salt with the last one.
     *
     * <p>Creature markers never expire. Each is the random entity UUID of a game object that erasure
     * could not reach because it was unloaded; it names no player (tying it to a person needs the
     * region file where the creature itself sits, which already holds the owner), so keeping it until
     * that creature loads and is freed ({@link #creatureFreed}) adds no identifying information and is
     * what finishes the erasure. Without it a still-tamed creature would load after expiry and write
     * the erased player's UUID back into this store.
     *
     * <p>The owner hash is the backstop for creatures the index never held (tamed before indexing
     * existed, or during an index gap); after it expires, such a creature is re-indexed on load. That
     * narrow case is the documented residual in {@code PRIVACY.md}.
     *
     * @return how many markers were dropped
     */
    public int pruneErasureMarkers(int days) {
        if (days <= 0) {
            return 0;
        }
        long threshold = clock.getAsLong() - days * MILLIS_PER_DAY;
        int before = this.erasureMarkerCount();
        this.erasedOwners.values().removeIf(at -> at < threshold);
        this.dropSaltIfUnused();
        int dropped = before - this.erasureMarkerCount();
        if (dropped > 0) {
            this.setDirty();
        }
        return dropped;
    }

    /**
     * A data-access export of exactly one player's own ownership rows and nothing else. Every other
     * player's rows are intentionally absent. Mirrors Core's {@code MaterialMilestones.exportPlayer}.
     */
    public static JsonObject exportPlayer(MinecraftServer server, UUID player) {
        return get(server).export(player);
    }

    /** The instance-level body of {@link #exportPlayer(MinecraftServer, UUID)}. */
    public JsonObject export(UUID player) {
        JsonObject root = new JsonObject();
        root.addProperty("last_updated", this.lastUpdated.getOrDefault(player, 0L));
        JsonArray owned = new JsonArray();
        for (OwnedCreature row : this.byPlayer.getOrDefault(player, List.of())) {
            JsonObject entry = new JsonObject();
            entry.addProperty("entity", row.entity().toString());
            entry.addProperty("kind", row.kind().key());
            entry.addProperty("type", row.type().toString());
            entry.addProperty("dimension", row.dimension().toString());
            if (row.kind() == OwnedCreature.Kind.EGG) {
                entry.addProperty("hatch_at", row.hatchAt());
            }
            if (row.parked()) {
                entry.addProperty("parked", true);
            }
            owned.add(entry);
        }
        root.add("owned", owned);
        int tames = this.tameCounts.getOrDefault(player, 0);
        if (tames > 0) {
            root.addProperty("tames", tames);
        }
        return root;
    }

    // --- internals ----------------------------------------------------------

    /** Salted SHA-256 of the owner UUID, first 16 bytes, hex. One-way; never logged or exported. */
    String ownerMarker(UUID owner) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(this.erasureSalt.getBytes(StandardCharsets.UTF_8));
            digest.update(owner.toString().getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is a required JVM algorithm", impossible);
        }
    }

    private static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Adds one marker, dropping the oldest if the set is over {@link #MAX_ERASURE_MARKERS}.
     *
     * @return how many markers were dropped at the cap (the caller counts them for the WARN)
     */
    static <T> int addBounded(Map<T, Long> markers, T value, long at) {
        markers.remove(value);
        markers.put(value, at);
        int dropped = 0;
        Iterator<T> oldest = markers.keySet().iterator();
        while (markers.size() > MAX_ERASURE_MARKERS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
            dropped++;
        }
        return dropped;
    }

    /** The salt exists only to hash owner markers; with none left it is deleted (data minimisation). */
    private void dropSaltIfUnused() {
        if (this.erasedOwners.isEmpty() && !this.erasureSalt.isEmpty()) {
            this.erasureSalt = "";
            this.setDirty();
        }
    }

    /** Stamps a player's rows as just-changed (shown in their export) and marks the store dirty. */
    private void touch(UUID player) {
        if (this.byPlayer.containsKey(player)) {
            this.lastUpdated.put(player, clock.getAsLong());
        } else {
            this.lastUpdated.remove(player);
        }
        this.setDirty();
    }

    // --- persistence (same SavedDataType + Codec pattern as Core) -----------

    private record PlayerRow(String player, long updatedAt, List<OwnedCreature> owned, int tames) {
        static final Codec<PlayerRow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("player").forGetter(PlayerRow::player),
                Codec.LONG.optionalFieldOf("updated_at", 0L).forGetter(PlayerRow::updatedAt),
                OwnedCreature.CODEC.listOf().optionalFieldOf("owned", List.of()).forGetter(PlayerRow::owned),
                // Lifetime hand-tame count (unreleased). Absent in older files: reads as 0.
                Codec.INT.optionalFieldOf("tames", 0).forGetter(PlayerRow::tames)
        ).apply(instance, PlayerRow::new));
    }

    /** One erasure marker on disk: the marker (owner hash or creature UUID) and when it was written. */
    private record MarkerRow(String id, long at) {
        static final Codec<MarkerRow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(MarkerRow::id),
                Codec.LONG.optionalFieldOf("at", 0L).forGetter(MarkerRow::at)
        ).apply(instance, MarkerRow::new));
    }

    private static Codec<CreatureOwnershipState> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                // Absent in 0.4.x files (reads as 1). Always written as SCHEMA from 0.5.0 on.
                Codec.INT.optionalFieldOf("schema", 1).forGetter(state -> SCHEMA),
                PlayerRow.CODEC.listOf().optionalFieldOf("players", List.of())
                        .forGetter(CreatureOwnershipState::playerRows),
                // Erasure markers (0.5.0). Absent in 0.4.x files: no markers, nothing is released.
                Codec.STRING.optionalFieldOf("erasure_salt", "").forGetter(state -> state.erasureSalt),
                // Early 0.5.0 development files wrote unstamped marker lists; read them, never write them.
                Codec.STRING.listOf().optionalFieldOf("erased_owners", List.of()).forGetter(state -> List.of()),
                Codec.STRING.listOf().optionalFieldOf("erased_creatures", List.of()).forGetter(state -> List.of()),
                MarkerRow.CODEC.listOf().optionalFieldOf("erased_owner_marks", List.of())
                        .forGetter(state -> markerRows(state.erasedOwners, id -> id)),
                MarkerRow.CODEC.listOf().optionalFieldOf("erased_creature_marks", List.of())
                        .forGetter(state -> markerRows(state.erasedCreatures, UUID::toString))
        ).apply(instance, CreatureOwnershipState::fromRows));
    }

    private static <T> List<MarkerRow> markerRows(Map<T, Long> markers, java.util.function.Function<T, String> id) {
        List<MarkerRow> out = new ArrayList<>(markers.size());
        markers.forEach((key, at) -> out.add(new MarkerRow(id.apply(key), at)));
        return out;
    }

    /** The store codec, exposed to the package for the migration tests. */
    static Codec<CreatureOwnershipState> storeCodec() {
        return codec();
    }

    private List<PlayerRow> playerRows() {
        List<PlayerRow> out = new ArrayList<>(this.byPlayer.size());
        this.byPlayer.forEach((player, rows) ->
                out.add(new PlayerRow(player.toString(), this.lastUpdated.getOrDefault(player, 0L),
                        List.copyOf(rows), this.tameCounts.getOrDefault(player, 0))));
        // Players with a tame count but no current rows (every pet released) keep their count.
        this.tameCounts.forEach((player, tames) -> {
            if (!this.byPlayer.containsKey(player)) {
                out.add(new PlayerRow(player.toString(), 0L, List.of(), tames));
            }
        });
        return out;
    }

    private static CreatureOwnershipState fromRows(int schema, List<PlayerRow> players, String salt,
            List<String> legacyOwners, List<String> legacyCreatures, List<MarkerRow> ownerMarks,
            List<MarkerRow> creatureMarks) {
        // Schema 1 (0.4.x) and 2 share the row shape; a future schema is read leniently the same way
        // (unknown fields are ignored by the codecs, unknown kinds read as pet).
        CreatureOwnershipState state = new CreatureOwnershipState();
        for (PlayerRow row : players) {
            UUID player;
            try {
                player = UUID.fromString(row.player());
            } catch (IllegalArgumentException ignored) {
                continue; // skip malformed rows rather than failing the whole load
            }
            if (row.tames() > 0) {
                state.tameCounts.put(player, Math.min(row.tames(), TAME_COUNT_CAP));
            }
            if (row.tames() > 0 && row.owned().isEmpty() && row.updatedAt() == 0L) {
                continue; // a count-only row: no ownership rows to index
            }
            state.byPlayer.put(player, new ArrayList<>(row.owned()));
            state.lastUpdated.put(player, row.updatedAt());
        }
        state.erasureSalt = salt;
        // An unstamped (early development) marker counts as written now, so it still expires. The
        // stamp is new data, so the store is marked dirty: otherwise a quiet store would re-stamp the
        // marker "now" on every load and it would never expire.
        long now = clock.getAsLong();
        boolean stamped = false;
        if (!salt.isEmpty()) {
            for (String marker : legacyOwners) {
                state.markersDroppedAtCap += addBounded(state.erasedOwners, marker, now);
                stamped = true;
            }
            for (MarkerRow mark : ownerMarks) {
                stamped |= mark.at() <= 0L;
                state.markersDroppedAtCap += addBounded(state.erasedOwners, mark.id(), mark.at() > 0L ? mark.at() : now);
            }
        }
        for (String raw : legacyCreatures) {
            addCreatureMarker(state, raw, now);
            stamped = true;
        }
        for (MarkerRow mark : creatureMarks) {
            stamped |= mark.at() <= 0L;
            addCreatureMarker(state, mark.id(), mark.at() > 0L ? mark.at() : now);
        }
        state.dropSaltIfUnused();
        if (stamped) {
            state.setDirty();
        }
        return state;
    }

    private static void addCreatureMarker(CreatureOwnershipState state, String raw, long at) {
        try {
            state.markersDroppedAtCap += addBounded(state.erasedCreatures, UUID.fromString(raw), at);
        } catch (IllegalArgumentException ignored) {
                // A malformed marker is dropped; the creature is then re-indexed, never lost.
        }
    }
}
