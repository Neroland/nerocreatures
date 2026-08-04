package za.co.neroland.nerocreatures.boss;

import java.util.ArrayList;
import java.util.Comparator;
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
import za.co.neroland.nerocreatures.data.CreatureData;
import za.co.neroland.nerocreatures.data.SavedDataRecovery;

/**
 * Damage totals for <b>summoned</b> boss fights: {@code fight id → (player UUID → damage dealt)},
 * plus one last-updated stamp per player for retention.
 *
 * <p>It exists for exactly one reason: a summoned boss pays out enhanced rewards split by how much
 * each player actually fought it (see {@link BossRewards}), and "how much did this player fight it"
 * cannot be answered without adding the numbers up as the fight happens.
 *
 * <h2>What is and is not recorded</h2>
 *
 * <ul>
 *   <li><b>Only summoned fights.</b> A naturally spawned boss has no fight row, records nothing and
 *       drops its plain loot table. Wandering into a wild boss never puts a player in this store.
 *       {@link BossController} enforces that: it records only when {@link BossSummons} has bound a
 *       fight id.</li>
 *   <li><b>Short-lived by construction.</b> A fight row is deleted the moment the fight ends —
 *       {@link #resolve} both reads the shares and removes the row, and {@link #discard} removes it
 *       when a boss leaves the world unbeaten. A fight that somehow outlives both is pruned after
 *       {@value #FIGHT_MAX_AGE_HOURS} hours. In normal play this store is empty.</li>
 *   <li><b>No names, no positions, no timestamps per hit.</b> A row is a game UUID and a float.</li>
 * </ul>
 *
 * <p>Persisted as vanilla {@link SavedData} on the overworld through the same
 * {@link SavedDataType} + Codec pattern as {@code data/CreatureOwnershipState}, and reached only
 * through {@link SavedDataRecovery}, so a corrupt file degrades to an empty store rather than
 * crashing the server mid-fight (the cost of that degradation is one fight's rewards, which is the
 * right trade).
 *
 * <p><b>Threading:</b> server thread only. Nothing here is synchronised.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Three controls, matching the ownership store:
 * <ul>
 *   <li><b>Erasure</b> — {@link #forgetPlayer(UUID)} removes a UUID from every fight it appears in
 *       and from the retention stamps. It is reached from Core's shared {@code PlayerDataErasure}
 *       hook via {@code data/CreatureData}. Nothing on that path logs who was erased.</li>
 *   <li><b>Retention</b> — {@link #stalerThan(int)} against Core's {@code dataRetentionDays}, swept
 *       lazily on the first access per server session, plus the unconditional stale-fight prune.</li>
 *   <li><b>Access</b> — {@link #exportPlayer} returns exactly one player's own contribution and
 *       nobody else's.</li>
 * </ul>
 */
public final class BossContributionState extends SavedData {

    /** Stable, non-identifying label used for the storage file and recovery logs. */
    public static final String NAME = NeroCreaturesCommon.MOD_ID + ":boss_contribution";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "boss_contribution");

    public static final SavedDataType<BossContributionState> TYPE =
            new SavedDataType<>(ID, BossContributionState::new, codec(), null);

    /** A fight untouched for this long is abandoned and pruned, rewards unpaid. */
    private static final int FIGHT_MAX_AGE_HOURS = 6;

    private static final long MILLIS_PER_HOUR = 3_600_000L;
    private static final long MILLIS_PER_DAY = 86_400_000L;

    /** Upper bound on live fights. Far above any plausible number of simultaneous summons. */
    private static final int MAX_FIGHTS = 64;

    /** Upper bound on contributors to one fight. */
    private static final int MAX_CONTRIBUTORS_PER_FIGHT = 64;

    /**
     * The server whose retention sweep has already run, so the lazy check in {@link #get} fires at
     * most once per server instance. Same arrangement as the ownership store.
     */
    private static volatile MinecraftServer prunedFor;

    /** One player's slice of one fight. */
    public record Share(UUID player, float damage, float fraction) {
    }

    /** Live state of one fight. Mutable on purpose — it is written on every landed hit. */
    private static final class Fight {
        private final String boss;
        private final Map<UUID, Float> damage = new LinkedHashMap<>();
        private long updatedAt;

        private Fight(String boss, long updatedAt) {
            this.boss = boss;
            this.updatedAt = updatedAt;
        }
    }

    private final Map<UUID, Fight> fights = new LinkedHashMap<>();
    private final Map<UUID, Long> lastUpdated = new LinkedHashMap<>();

    public BossContributionState() {
    }

    /**
     * The one store, on the overworld so it is always loaded. Runs the stale-fight prune and the
     * retention sweep on the first call for a given server instance.
     */
    public static BossContributionState get(MinecraftServer server) {
        BossContributionState state = SavedDataRecovery.get(
                server.overworld(), TYPE, BossContributionState::new, NAME);
        if (prunedFor != server) {
            prunedFor = server; // set first: pruning must never re-enter this sweep
            state.pruneStaleFights();
            CreatureData.applyContributionRetention(state);
        }
        return state;
    }

    // --- recording ----------------------------------------------------------

    /**
     * Registers a player as a participant in a fight with no damage yet. Used for the summoner, so
     * whoever paid to start the fight is on the reward list even if they never land a hit.
     */
    public void join(UUID fight, Identifier boss, UUID player) {
        this.record(fight, boss, player, 0.0F);
    }

    /** Adds {@code damage} to a player's running total for a fight. */
    public void record(UUID fight, Identifier boss, UUID player, float damage) {
        if (damage < 0.0F) {
            return;
        }
        Fight row = this.fights.get(fight);
        if (row == null) {
            if (this.fights.size() >= MAX_FIGHTS) {
                NeroCreaturesCommon.LOGGER.warn(
                        "[NeroCreatures] Boss contribution fight limit reached; this fight's rewards "
                                + "will fall back to the plain loot table.");
                return;
            }
            row = new Fight(boss.toString(), System.currentTimeMillis());
            this.fights.put(fight, row);
        }
        Float existing = row.damage.get(player);
        if (existing == null && row.damage.size() >= MAX_CONTRIBUTORS_PER_FIGHT) {
            return;
        }
        row.damage.put(player, (existing == null ? 0.0F : existing) + damage);
        row.updatedAt = System.currentTimeMillis();
        this.lastUpdated.put(player, row.updatedAt);
        this.setDirty();
    }

    // --- resolution ---------------------------------------------------------

    /**
     * Ends a fight: returns each participant's share, highest first, and <b>removes the row</b>. The
     * store is deliberately not a history — once the rewards are paid there is nothing left to keep.
     *
     * @return the shares, or an empty list if the fight was never recorded
     */
    public List<Share> resolve(UUID fight) {
        Fight row = this.fights.remove(fight);
        if (row == null) {
            return List.of();
        }
        this.setDirty();
        float total = 0.0F;
        for (float damage : row.damage.values()) {
            total += damage;
        }
        List<Share> shares = new ArrayList<>(row.damage.size());
        for (Map.Entry<UUID, Float> entry : row.damage.entrySet()) {
            float fraction = total <= 0.0F ? 0.0F : entry.getValue() / total;
            shares.add(new Share(entry.getKey(), entry.getValue(), fraction));
        }
        shares.sort(Comparator.comparingDouble(Share::damage).reversed());
        return List.copyOf(shares);
    }

    /** Drops a fight without paying anything out — a boss that left the world unbeaten. */
    public void discard(UUID fight) {
        if (this.fights.remove(fight) != null) {
            this.setDirty();
        }
    }

    /** A player's live contribution to a fight, or {@code 0} if they are not in it. */
    public float damageOf(UUID fight, UUID player) {
        Fight row = this.fights.get(fight);
        if (row == null) {
            return 0.0F;
        }
        return row.damage.getOrDefault(player, 0.0F);
    }

    /**
     * A player's live share of a fight, {@code 0.0}–{@code 1.0}, or {@code 0} if they are not in it.
     *
     * <p>The denominator is the fight's total recorded damage. That is an <em>aggregate</em> of the
     * fight, not a fact about any identifiable other player, which is why this is safe to hand to
     * the asking player through the link module: it answers "how much of this fight is mine",
     * without naming or quantifying anybody else.
     */
    public float shareOf(UUID fight, UUID player) {
        Fight row = this.fights.get(fight);
        if (row == null) {
            return 0.0F;
        }
        Float mine = row.damage.get(player);
        if (mine == null || mine <= 0.0F) {
            return 0.0F;
        }
        float total = 0.0F;
        for (float damage : row.damage.values()) {
            total += damage;
        }
        return total <= 0.0F ? 0.0F : mine / total;
    }

    /**
     * How many players are recorded in a fight. A <b>count</b>, never a list: the link module uses
     * it to say "you and two others", which needs no identities at all.
     */
    public int participantCount(UUID fight) {
        Fight row = this.fights.get(fight);
        return row == null ? 0 : row.damage.size();
    }

    // --- privacy: erasure, retention, export --------------------------------

    /**
     * POPIA/GDPR erasure: removes a player from every fight they appear in and from the retention
     * stamps. A fight left with no contributors is removed outright. Never logs player identity.
     *
     * @return {@code true} if anything was actually removed
     */
    public boolean forgetPlayer(UUID player) {
        boolean changed = this.lastUpdated.remove(player) != null;
        for (Fight row : this.fights.values()) {
            if (row.damage.remove(player) != null) {
                changed = true;
            }
        }
        // A fight nobody is left in has nothing to pay out and no reason to exist.
        changed |= this.fights.values().removeIf(row -> row.damage.isEmpty());
        if (changed) {
            this.setDirty();
        }
        return changed;
    }

    /**
     * Players whose contribution has not changed in {@code days} days. {@code 0} or less disables
     * the sweep, leaving retention to Core's own purge-inactive flow (which reaches this store
     * through the registered eraser).
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
     * Drops fights nobody has touched for {@value #FIGHT_MAX_AGE_HOURS} hours. This is the safety
     * net under the normal path (a fight is removed when it resolves or when the boss leaves the
     * world) and is what guarantees the store cannot accumulate rows across a crash.
     *
     * @return how many fights were pruned
     */
    public int pruneStaleFights() {
        long threshold = System.currentTimeMillis() - FIGHT_MAX_AGE_HOURS * MILLIS_PER_HOUR;
        int before = this.fights.size();
        this.fights.values().removeIf(row -> row.updatedAt < threshold);
        int pruned = before - this.fights.size();
        if (pruned > 0) {
            this.setDirty();
            // Count only — never which players (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Dropped {} abandoned boss fight record(s).", pruned);
        }
        return pruned;
    }

    /**
     * A data-access export of exactly one player's own boss contribution and nothing else. Other
     * players' totals in the same fight are intentionally absent.
     */
    public static JsonObject exportPlayer(MinecraftServer server, UUID player) {
        return get(server).export(player);
    }

    /** The instance-level body of {@link #exportPlayer(MinecraftServer, UUID)}. */
    public JsonObject export(UUID player) {
        JsonObject root = new JsonObject();
        root.addProperty("last_updated", this.lastUpdated.getOrDefault(player, 0L));
        JsonArray contributions = new JsonArray();
        this.fights.forEach((fight, row) -> {
            Float damage = row.damage.get(player);
            if (damage == null) {
                return;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("fight", fight.toString());
            entry.addProperty("boss", row.boss);
            entry.addProperty("damage", damage);
            contributions.add(entry);
        });
        root.add("boss_contribution", contributions);
        return root;
    }

    // --- persistence (same SavedDataType + Codec pattern as the ownership store) ---

    private record Contributor(String player, float damage) {
        static final Codec<Contributor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("player").forGetter(Contributor::player),
                Codec.FLOAT.optionalFieldOf("damage", 0.0F).forGetter(Contributor::damage)
        ).apply(instance, Contributor::new));
    }

    private record FightRow(String fight, String boss, long updatedAt, List<Contributor> contributors) {
        static final Codec<FightRow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("fight").forGetter(FightRow::fight),
                Codec.STRING.optionalFieldOf("boss", "").forGetter(FightRow::boss),
                Codec.LONG.optionalFieldOf("updated_at", 0L).forGetter(FightRow::updatedAt),
                Contributor.CODEC.listOf().optionalFieldOf("contributors", List.of())
                        .forGetter(FightRow::contributors)
        ).apply(instance, FightRow::new));
    }

    private static Codec<BossContributionState> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                FightRow.CODEC.listOf().optionalFieldOf("fights", List.of())
                        .forGetter(BossContributionState::fightRows)
        ).apply(instance, BossContributionState::fromRows));
    }

    private List<FightRow> fightRows() {
        List<FightRow> out = new ArrayList<>(this.fights.size());
        this.fights.forEach((fight, row) -> {
            List<Contributor> contributors = new ArrayList<>(row.damage.size());
            row.damage.forEach((player, damage) ->
                    contributors.add(new Contributor(player.toString(), damage)));
            out.add(new FightRow(fight.toString(), row.boss, row.updatedAt, contributors));
        });
        return out;
    }

    private static BossContributionState fromRows(List<FightRow> rows) {
        BossContributionState state = new BossContributionState();
        for (FightRow row : rows) {
            UUID fight = parseUuid(row.fight());
            if (fight == null) {
                continue; // skip malformed rows rather than failing the whole load
            }
            Fight live = new Fight(row.boss(), row.updatedAt());
            for (Contributor contributor : row.contributors()) {
                UUID player = parseUuid(contributor.player());
                if (player == null) {
                    continue;
                }
                live.damage.put(player, contributor.damage());
                state.lastUpdated.merge(player, row.updatedAt(), Math::max);
            }
            state.fights.put(fight, live);
        }
        return state;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
