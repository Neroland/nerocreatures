package za.co.neroland.nerocreatures.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
 *   <li><b>Retention</b> — when Core's {@code dataRetentionDays} is above zero, rows whose stamp is
 *       older than that are pruned on the first access per server session; only the number pruned is
 *       logged.</li>
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
     * Upper bound on each erasure-marker set. Oldest markers go first; a creature whose marker was
     * evicted is re-indexed on load rather than released, which never costs a world anything.
     */
    static final int MAX_ERASURE_MARKERS = 16_384;

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
     * is rewritten until the next ordinary save (decision 004).
     */
    public static final int SCHEMA = 2;

    /**
     * The server whose retention sweep has already run, so the lazy check in {@link #get} fires at
     * most once per server instance. Written on the server thread; {@code volatile} only so an
     * integrated-server restart in the same JVM is seen promptly.
     */
    private static volatile MinecraftServer prunedFor;

    private final Map<UUID, List<OwnedCreature>> byPlayer = new LinkedHashMap<>();
    private final Map<UUID, Long> lastUpdated = new LinkedHashMap<>();

    /**
     * Erasure markers (load-time half of erasure). {@code erasedOwners} holds a salted one-way hash
     * of each erased owner, never the UUID itself; {@code erasedCreatures} holds the entity UUIDs of
     * the rows erasure removed and could not free because they were not loaded. Neither names a
     * player. The salt is random per world and lives only in this file.
     */
    private String erasureSalt = "";
    private final Set<String> erasedOwners = new LinkedHashSet<>();
    private final Set<UUID> erasedCreatures = new LinkedHashSet<>();

    public CreatureOwnershipState() {
    }

    /**
     * The one store, on the overworld so it is always loaded. Runs the retention sweep on the first
     * call for a given server instance.
     */
    public static CreatureOwnershipState get(MinecraftServer server) {
        CreatureOwnershipState state = SavedDataRecovery.get(
                server.overworld(), TYPE, CreatureOwnershipState::new, NAME);
        if (prunedFor != server) {
            prunedFor = server; // set first: pruning must never re-enter this sweep
            CreatureData.applyRetention(server, state);
            // The sweep purged rows after Core's get() refreshed the backup; push the purge into it.
            state.backupNow(server);
        }
        return state;
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
        return this.byPlayer.containsKey(player) || this.lastUpdated.containsKey(player);
    }

    /**
     * The load-time ownership check for one tamed creature (decision 004). Release happens only when
     * erasure or retention <b>positively recorded</b> this creature or its owner; an owner the index
     * merely does not know (a 0.4.x pet tamed before its row existed, a store that recovered empty,
     * an owner whose other rows were all removed) is re-indexed instead, so no world loses a pet to
     * an index gap.
     */
    public LoadVerdict loadVerdict(UUID owner, UUID entity) {
        if (this.contains(owner, entity)) {
            return LoadVerdict.INDEXED;
        }
        if (this.erasedCreatures.contains(entity)) {
            return LoadVerdict.RELEASE;
        }
        if (!this.erasureSalt.isEmpty() && this.erasedOwners.contains(this.ownerMarker(owner))) {
            return LoadVerdict.RELEASE;
        }
        return LoadVerdict.REINDEX;
    }

    /** A creature erasure pointed at has now been freed; its marker is no longer needed. */
    public void creatureFreed(UUID entity) {
        if (this.erasedCreatures.remove(entity)) {
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
     * Drops one row by its id alone, whoever it belongs to: an Incubator's egg row when the egg hatches,
     * is taken back out or its incubator breaks. The block entity only knows the random incubation id,
     * never the player (decision 004), so it cannot call {@link #remove(UUID, UUID)}. One pass over the
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
        // Positive erasure record for the load-time check: a salted hash of the owner (covers
        // creatures the index never knew about) and the entity UUIDs of the removed rows. The caller
        // clears an entity marker again once it frees that creature.
        if (this.erasureSalt.isEmpty()) {
            this.erasureSalt = newSalt();
        }
        addBounded(this.erasedOwners, this.ownerMarker(player));
        if (removed != null) {
            for (OwnedCreature row : removed) {
                if (row.kind() != OwnedCreature.Kind.EGG) {
                    addBounded(this.erasedCreatures, row.entity());
                }
            }
        }
        this.setDirty();
        return removed == null ? List.of() : List.copyOf(removed);
    }

    /**
     * The players whose ownership rows have not changed in {@code days} days. A value of {@code 0}
     * or less disables the sweep, leaving retention entirely to Core's own purge-inactive flow
     * (which reaches this store through the registered eraser).
     *
     * <p>Rows with no usable stamp are left alone rather than deleted on a guess.
     */
    public List<UUID> stalerThan(int days) {
        if (days <= 0) {
            return List.of();
        }
        long threshold = System.currentTimeMillis() - days * MILLIS_PER_DAY;
        List<UUID> stale = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : this.lastUpdated.entrySet()) {
            Long stamp = entry.getValue();
            if (stamp != null && stamp > 0L && stamp < threshold) {
                stale.add(entry.getKey());
            }
        }
        return stale;
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

    private static <T> void addBounded(Set<T> set, T value) {
        set.remove(value);
        set.add(value);
        Iterator<T> oldest = set.iterator();
        while (set.size() > MAX_ERASURE_MARKERS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Stamps a player's row as just-changed (retention input) and marks the store dirty. */
    private void touch(UUID player) {
        if (this.byPlayer.containsKey(player)) {
            this.lastUpdated.put(player, System.currentTimeMillis());
        } else {
            this.lastUpdated.remove(player);
        }
        this.setDirty();
    }

    // --- persistence (same SavedDataType + Codec pattern as Core) -----------

    private record PlayerRow(String player, long updatedAt, List<OwnedCreature> owned) {
        static final Codec<PlayerRow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("player").forGetter(PlayerRow::player),
                Codec.LONG.optionalFieldOf("updated_at", 0L).forGetter(PlayerRow::updatedAt),
                OwnedCreature.CODEC.listOf().optionalFieldOf("owned", List.of()).forGetter(PlayerRow::owned)
        ).apply(instance, PlayerRow::new));
    }

    private static Codec<CreatureOwnershipState> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                // Absent in 0.4.x files (reads as 1). Always written as SCHEMA from 0.5.0 on.
                Codec.INT.optionalFieldOf("schema", 1).forGetter(state -> SCHEMA),
                PlayerRow.CODEC.listOf().optionalFieldOf("players", List.of())
                        .forGetter(CreatureOwnershipState::playerRows),
                // Erasure markers (0.5.0). Absent in 0.4.x files: no markers, nothing is released.
                Codec.STRING.optionalFieldOf("erasure_salt", "").forGetter(state -> state.erasureSalt),
                Codec.STRING.listOf().optionalFieldOf("erased_owners", List.of())
                        .forGetter(state -> List.copyOf(state.erasedOwners)),
                Codec.STRING.listOf().optionalFieldOf("erased_creatures", List.of())
                        .forGetter(state -> state.erasedCreatures.stream().map(UUID::toString).toList())
        ).apply(instance, CreatureOwnershipState::fromRows));
    }

    /** The store codec, exposed to the package for the migration tests. */
    static Codec<CreatureOwnershipState> storeCodec() {
        return codec();
    }

    private List<PlayerRow> playerRows() {
        List<PlayerRow> out = new ArrayList<>(this.byPlayer.size());
        this.byPlayer.forEach((player, rows) ->
                out.add(new PlayerRow(player.toString(), this.lastUpdated.getOrDefault(player, 0L),
                        List.copyOf(rows))));
        return out;
    }

    private static CreatureOwnershipState fromRows(int schema, List<PlayerRow> players, String salt,
            List<String> erasedOwners, List<String> erasedCreatures) {
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
            state.byPlayer.put(player, new ArrayList<>(row.owned()));
            state.lastUpdated.put(player, row.updatedAt());
        }
        state.erasureSalt = salt;
        if (!salt.isEmpty()) {
            erasedOwners.forEach(marker -> addBounded(state.erasedOwners, marker));
        }
        for (String raw : erasedCreatures) {
            try {
                addBounded(state.erasedCreatures, UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // A malformed marker is dropped; the creature is then re-indexed, never lost.
            }
        }
        return state;
    }
}
