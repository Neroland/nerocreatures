package za.co.neroland.nerocreatures.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

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
 * goes through {@link SavedDataRecovery}, so a corrupt file degrades to an empty index instead of
 * crashing the server.
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
 *       shared {@code PlayerDataErasure} hook from {@link CreatureData#init()}. Erasure never logs
 *       player identity.</li>
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
            new SavedDataType<>(ID, CreatureOwnershipState::new, codec(), null);

    /**
     * Upper bound on rows kept for one player. Far above any sane cap
     * ({@code maxPetsPerPlayer} + {@code maxDronesPerPlayer} both max out at 64); it exists so a
     * buggy caller cannot grow a row list without limit.
     */
    private static final int MAX_ROWS_PER_PLAYER = 256;

    private static final long MILLIS_PER_DAY = 86_400_000L;

    /**
     * The server whose retention sweep has already run, so the lazy check in {@link #get} fires at
     * most once per server instance. Written on the server thread; {@code volatile} only so an
     * integrated-server restart in the same JVM is seen promptly.
     */
    private static volatile MinecraftServer prunedFor;

    private final Map<UUID, List<OwnedCreature>> byPlayer = new LinkedHashMap<>();
    private final Map<UUID, Long> lastUpdated = new LinkedHashMap<>();

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
        }
        return state;
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

    // --- privacy: erasure, retention, export --------------------------------

    /**
     * POPIA/GDPR erasure: drop everything stored for a player and hand back the rows that were
     * removed so the caller can free the creatures they point at. Never logs player identity.
     *
     * @return the removed rows, in insertion order (empty if the player had none)
     */
    public List<OwnedCreature> forgetPlayer(UUID player) {
        List<OwnedCreature> removed = this.byPlayer.remove(player);
        boolean changed = removed != null;
        changed |= this.lastUpdated.remove(player) != null;
        if (changed) {
            this.setDirty();
        }
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
            owned.add(entry);
        }
        root.add("owned", owned);
        return root;
    }

    // --- internals ----------------------------------------------------------

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
                PlayerRow.CODEC.listOf().optionalFieldOf("players", List.of())
                        .forGetter(CreatureOwnershipState::playerRows)
        ).apply(instance, CreatureOwnershipState::fromRows));
    }

    private List<PlayerRow> playerRows() {
        List<PlayerRow> out = new ArrayList<>(this.byPlayer.size());
        this.byPlayer.forEach((player, rows) ->
                out.add(new PlayerRow(player.toString(), this.lastUpdated.getOrDefault(player, 0L),
                        List.copyOf(rows))));
        return out;
    }

    private static CreatureOwnershipState fromRows(List<PlayerRow> players) {
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
        return state;
    }
}
