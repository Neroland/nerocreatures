package za.co.neroland.nerocreatures.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;

/**
 * The gate on <b>natural</b> boss spawns — the one thing standing between "a boss can turn up on its
 * own" and "a world slowly fills up with bosses".
 *
 * <p>A boss never despawns (see {@link NeroBossEntity}), so unlike every other creature in this mod
 * it cannot be allowed to spawn on the ordinary weighted-roll terms. Three conditions must all hold
 * before the spawn engine will even consider a boss rule, and they are ordered cheapest first
 * because {@code spawn/CreatureSpawns} asks this question on every candidate position:
 *
 * <ol>
 *   <li><b>{@code naturalBossSpawnsEnabled}.</b> One config read. Turning it off leaves bosses
 *       reachable only through {@link BossSummons} and the spawn egg.</li>
 *   <li><b>Cooldown.</b> After a boss appears in a dimension, no further natural boss may appear
 *       there for {@value #COOLDOWN_TICKS} ticks (a full Minecraft day). One map lookup and a
 *       comparison. Killing a boss starts the same cooldown, so the reward for clearing one is a
 *       day of quiet rather than an immediate replacement.</li>
 *   <li><b>Uniqueness.</b> At most one boss alive per dimension. This is the only expensive check —
 *       a bounded entity query that stops at the first match — so it is cached for
 *       {@value #PRESENCE_TTL_TICKS} ticks and is only reached at all when the two cheap conditions
 *       have already passed. It exists to close the one hole the cooldown cannot: a boss that was
 *       alive when the server stopped, since the cooldowns live in memory.</li>
 * </ol>
 *
 * <p>This is deliberately a <b>cooldown</b> rather than the "minimum distance from the last spawn"
 * the design sketch suggested. A distance guard needs a stored position per dimension, and this mod
 * stores no coordinates anywhere; a per-dimension cooldown plus a one-alive rule achieves the same
 * "you will not trip over two of these" outcome for one integer and no persistence at all.
 *
 * <p>All state here is in-memory and per server session. Losing it on restart is harmless: the
 * uniqueness check is what makes a cold start safe.
 *
 * <p>No player data of any kind passes through here (POPIA/GDPR).
 */
public final class BossSpawns {

    /** Ticks after a boss appears or dies before another may spawn naturally in that dimension. */
    private static final int COOLDOWN_TICKS = 24_000;

    /** How long a "is a boss already alive here" answer stays fresh, in ticks. */
    private static final int PRESENCE_TTL_TICKS = 200;

    private record Presence(long gameTime, boolean bossAlive) {
    }

    /** Game time at which each dimension is next eligible for a natural boss. */
    private static final Map<ResourceKey<Level>, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private static final Map<ResourceKey<Level>, Presence> PRESENCE = new ConcurrentHashMap<>();

    private BossSpawns() {
    }

    /**
     * Whether the spawn engine may place a boss in this level right now. Used as a
     * {@code spawn/SpawnRule} gate, so it is asked often and must stay cheap — see the class docs
     * for the ordering.
     */
    public static boolean maySpawnNaturally(ServerLevel level) {
        if (!NeroCreaturesConfig.NATURAL_BOSS_SPAWNS_ENABLED.get()) {
            return false;
        }
        Long eligibleAt = COOLDOWNS.get(level.dimension());
        if (eligibleAt != null && level.getGameTime() < eligibleAt) {
            return false;
        }
        return !bossAlive(level);
    }

    /**
     * Starts the cooldown for a dimension, because a boss just arrived in it. Called from every
     * boss's {@code finalizeSpawn}, so a summoned or egg-placed boss also buys the player a day
     * without a wild one turning up on top of it.
     */
    public static void noteBossPresent(ServerLevel level) {
        COOLDOWNS.put(level.dimension(), level.getGameTime() + COOLDOWN_TICKS);
        PRESENCE.put(level.dimension(), new Presence(level.getGameTime(), true));
    }

    /** Restarts the cooldown, because a boss just died. Also invalidates the presence cache. */
    public static void noteBossGone(ServerLevel level) {
        COOLDOWNS.put(level.dimension(), level.getGameTime() + COOLDOWN_TICKS);
        PRESENCE.remove(level.dimension());
    }

    /** Drops all cached state — called when the spawn engine sees a new server. */
    public static void reset() {
        COOLDOWNS.clear();
        PRESENCE.clear();
    }

    /**
     * Whether a boss is loaded in this level. Bounded (stops at the first match) and cached for
     * {@value #PRESENCE_TTL_TICKS} ticks, in the same shape as {@code spawn/CreatureCensus}.
     */
    private static boolean bossAlive(ServerLevel level) {
        long now = level.getGameTime();
        Presence cached = PRESENCE.get(level.dimension());
        if (cached != null && now >= cached.gameTime() && now - cached.gameTime() < PRESENCE_TTL_TICKS) {
            return cached.bossAlive();
        }
        List<NeroBossEntity> found = new ArrayList<>(1);
        level.getEntities(EntityTypeTest.forClass(NeroBossEntity.class), boss -> true, found, 1);
        boolean alive = !found.isEmpty();
        PRESENCE.put(level.dimension(), new Presence(now, alive));
        return alive;
    }
}
