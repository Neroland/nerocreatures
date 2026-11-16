package za.co.neroland.nerocreatures.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.block.ApexAltarBlockEntity;

/**
 * Which Apex Altars a player has lit, so the companion app can show the cooldowns of <em>their own</em>
 * altars (link schema 2, {@code bosses.altars}).
 *
 * <p><b>Memory only, never saved.</b> The altar block holds no player identity, and the boss
 * contribution row that names the summoner is deleted when the fight ends, so neither can answer "which
 * altars are mine" for a cooldown that outlives the fight. This class keeps that answer in server memory
 * and nowhere else: it is lost on a restart, an entry is dropped an hour after its altar is ready again
 * ({@link #EXPIRE_AFTER_READY_TICKS}) or when the altar is gone, at most {@link #MAX_PER_PLAYER} entries
 * are kept per player, and a Neroland erasure request forgets the player at once ({@link #forget}).
 *
 * <p><b>What leaves this class.</b> Only {@link Row}: a small stable {@code index} (the label "Altar 1"),
 * a state, the seconds left and the last boss summoned there. The altar's dimension and block position
 * are held in memory to find the altar again and are <b>never</b> put in a link payload; the player's own
 * {@code /nerocreatures export} lists them, because the export is everything held about that player.
 *
 * <p>The index is stable while an entry exists: a new altar takes the smallest free number, and the
 * same altar keeps its number on every later summon. Server thread only.
 */
public final class AltarUses {

    /** An entry is forgotten this long after its altar became ready again (one hour). */
    public static final long EXPIRE_AFTER_READY_TICKS = 72_000L;

    /** Most altars remembered per player. */
    public static final int MAX_PER_PLAYER = 8;

    /** The three states the app shows. */
    public enum State {
        READY, COOLDOWN, FIGHTING;

        public String key() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One altar as the link module sends it: no place, only a label number.
     *
     * @param index    stable label number, 1-based
     * @param state    ready, cooling down or fighting
     * @param readyInS seconds until ready (only meaningful in {@link State#COOLDOWN})
     * @param lastBoss boss id last summoned there, or {@code null}
     */
    public record Row(int index, State state, long readyInS, @Nullable String lastBoss) {
    }

    /**
     * What the world says about one remembered altar right now.
     *
     * @param present   the altar block is (or, if not loaded, may still be) there
     * @param loaded    its chunk is loaded, so {@code present} and {@code readyAt} are facts
     * @param readyAt   the altar's own cooldown end (game time)
     * @param fightLive this player's fight there is still going
     */
    public record Probe(boolean present, boolean loaded, long readyAt, boolean fightLive) {
    }

    /** Asks the world about one remembered altar; never loads a chunk. */
    public interface Prober {
        Probe probe(Identifier dimension, long pos, @Nullable UUID fight, long readyAt);
    }

    /** Memory-only entry. Mutable: refreshed on every summon and every read. */
    private static final class Use {
        final Identifier dimension;
        final long pos;
        final int index;
        long readyAt;
        @Nullable
        UUID fight;
        @Nullable
        String lastBoss;

        Use(Identifier dimension, long pos, int index) {
            this.dimension = dimension;
            this.pos = pos;
            this.index = index;
        }
    }

    /** The table for one server. Pure, so the rules are tested without a world. */
    public static final class Table {
        private final Map<UUID, List<Use>> uses = new HashMap<>();

        /** Remembers a summon at an altar; returns the altar's index for this player. */
        public int noteSummon(UUID player, Identifier dimension, long pos, long readyAt, @Nullable UUID fight,
                @Nullable String bossId) {
            List<Use> list = this.uses.computeIfAbsent(player, p -> new ArrayList<>());
            for (Use use : list) {
                if (use.dimension.equals(dimension) && use.pos == pos) {
                    use.readyAt = readyAt;
                    use.fight = fight;
                    use.lastBoss = bossId;
                    return use.index;
                }
            }
            if (list.size() >= MAX_PER_PLAYER) {
                // Full: forget the entry whose cooldown ended first.
                Use oldest = null;
                for (Use use : list) {
                    if (oldest == null || use.readyAt < oldest.readyAt) {
                        oldest = use;
                    }
                }
                list.remove(oldest);
            }
            Use use = new Use(dimension, pos, smallestFreeIndex(list));
            use.readyAt = readyAt;
            use.fight = fight;
            use.lastBoss = bossId;
            list.add(use);
            list.sort((a, b) -> Integer.compare(a.index, b.index));
            return use.index;
        }

        /**
         * The player's own altars, in index order, with stale entries dropped. {@code probe} is asked
         * about each remembered altar; it never loads a chunk.
         */
        public List<Row> rows(UUID player, long now, Prober probe) {
            List<Use> list = this.uses.get(player);
            if (list == null) {
                return List.of();
            }
            List<Row> out = new ArrayList<>(list.size());
            for (Iterator<Use> it = list.iterator(); it.hasNext();) {
                Use use = it.next();
                Probe seen = probe.probe(use.dimension, use.pos, use.fight, use.readyAt);
                if (seen.loaded() && !seen.present()) {
                    it.remove(); // the altar was broken
                    continue;
                }
                if (seen.loaded()) {
                    use.readyAt = seen.readyAt();
                }
                State state = stateOf(now, use.readyAt, seen.fightLive());
                if (state == State.READY && now - use.readyAt > EXPIRE_AFTER_READY_TICKS) {
                    it.remove();
                    continue;
                }
                long left = state == State.COOLDOWN ? Math.max(0L, (use.readyAt - now + 19L) / 20L) : 0L;
                out.add(new Row(use.index, state, left, use.lastBoss));
            }
            if (list.isEmpty()) {
                this.uses.remove(player);
            }
            return out;
        }

        /** Erasure: forgets everything about the player. */
        public boolean forget(UUID player) {
            return this.uses.remove(player) != null;
        }

        /** How many players are remembered. */
        public int players() {
            return this.uses.size();
        }

        /** The player's own entries for {@code /nerocreatures export}, with places (their own data). */
        public JsonArray export(UUID player) {
            JsonArray out = new JsonArray();
            for (Use use : this.uses.getOrDefault(player, List.of())) {
                BlockPos pos = BlockPos.of(use.pos);
                JsonObject row = new JsonObject();
                row.addProperty("index", use.index);
                row.addProperty("dimension", use.dimension.toString());
                row.addProperty("x", pos.getX());
                row.addProperty("y", pos.getY());
                row.addProperty("z", pos.getZ());
                row.addProperty("ready_at", use.readyAt);
                if (use.lastBoss != null) {
                    row.addProperty("last_boss", use.lastBoss);
                }
                out.add(row);
            }
            return out;
        }

        private static int smallestFreeIndex(List<Use> list) {
            int candidate = 1;
            boolean taken = true;
            while (taken) {
                taken = false;
                for (Use use : list) {
                    if (use.index == candidate) {
                        candidate++;
                        taken = true;
                        break;
                    }
                }
            }
            return candidate;
        }
    }

    /** The state rule: a live fight wins, then a running cooldown, else ready. */
    public static State stateOf(long now, long readyAt, boolean fightLive) {
        if (fightLive) {
            return State.FIGHTING;
        }
        return now < readyAt ? State.COOLDOWN : State.READY;
    }

    @Nullable
    private static MinecraftServer tableServer;
    private static Table table = new Table();

    private AltarUses() {
    }

    /** This server's table; a different server (a new single-player world) starts empty. */
    private static Table table(MinecraftServer server) {
        if (tableServer != server) {
            tableServer = server;
            table = new Table();
        }
        return table;
    }

    /** Called by the altar when a summon actually started. */
    public static void noteSummon(ServerLevel level, BlockPos pos, UUID player, long readyAt, @Nullable UUID fight,
            Identifier bossId) {
        table(level.getServer()).noteSummon(player, level.dimension().identifier(), pos.asLong(), readyAt, fight,
                bossId.toString());
    }

    /** The asking player's own altars for the link module. */
    public static List<Row> rows(MinecraftServer server, UUID player) {
        long now = server.overworld().getGameTime();
        return table(server).rows(player, now,
                (dimension, pos, fight, readyAt) -> probe(server, dimension, pos, fight, readyAt));
    }

    /** Erasure (Core's {@code PlayerDataErasure}, through {@code CreatureData}). */
    public static void forget(UUID player) {
        table.forget(player);
    }

    /** The player's own entries for {@code /nerocreatures export}. */
    public static JsonArray export(MinecraftServer server, UUID player) {
        return table(server).export(player);
    }

    private static Probe probe(MinecraftServer server, Identifier dimension, long packed, @Nullable UUID fight,
            long readyAt) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        BlockPos pos = BlockPos.of(packed);
        boolean live = fightLive(server, fight);
        if (level == null) {
            return new Probe(false, true, readyAt, false); // the dimension is gone
        }
        if (!level.isLoaded(pos)) {
            return new Probe(true, false, readyAt, live);
        }
        if (!(level.getBlockEntity(pos) instanceof ApexAltarBlockEntity altar)) {
            return new Probe(false, true, readyAt, false);
        }
        // A newer fight at the same altar (somebody else's summon) is not this player's fight.
        return new Probe(true, true, altar.readyAt(), live && fight != null && fight.equals(altar.fight()));
    }

    /** Whether a fight is still going: in the live index, or still holding a contribution row. */
    private static boolean fightLive(MinecraftServer server, @Nullable UUID fight) {
        if (fight == null) {
            return false;
        }
        for (BossController controller : BossFights.active()) {
            if (fight.equals(controller.fightId())) {
                return true;
            }
        }
        return BossContributionState.get(server).participantCount(fight) > 0;
    }
}
