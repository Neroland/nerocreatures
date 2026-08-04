package za.co.neroland.nerocreatures.spawn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * The invasion seam: how a future <b>NeroEvents</b> raid puts a wave of creatures into a world
 * without either breaking this mod's population caps or leaving its mobs behind afterwards.
 *
 * <p><b>Nothing in NeroCreatures 0.1.0 calls this.</b> It is a unit of design, shipped now and
 * documented in {@code wiki/Spawning.md}, so that the mod that needs it does not have to reach past
 * the caps to get it. The three-call shape is the whole API:
 *
 * <pre>
 * InvasionBudget.Handle wave = InvasionBudget.reserve(level, 12);
 * InvasionBudget.spawnWithin(wave, ModEntities.SPACE_PIRATE.get(), pos);   // × up to 12
 * …the event runs…
 * InvasionBudget.close(wave);                                             // the world is tidy again
 * </pre>
 *
 * <h2>The budget is an allowance, not a bypass</h2>
 *
 * <p>A wave is allowed to push a dimension <em>temporarily</em> above
 * {@code maxCreaturesPerDimension}, by at most the reserved budget — because an event that has to
 * wait for wandering monsters to despawn before it can start is not an event. Everything else still
 * applies exactly as it does to a natural spawn:
 *
 * <ul>
 *   <li><b>{@code maxCreaturesPerChunk} is never exceeded.</b> The dimension cap protects the
 *       server; the chunk cap protects the player standing in that chunk, and no event has any
 *       business overriding that.</li>
 *   <li><b>A cap of {@code 0} means zero.</b> A server that has set either cap to 0 has said "no
 *       NeroCreatures here", and a wave is refused outright rather than treated as an exception.</li>
 *   <li><b>The budget is spent, not rented.</b> Each successful spawn costs one, whether or not the
 *       creature is still alive later.</li>
 * </ul>
 *
 * <h2>How a wave is cleaned up</h2>
 *
 * <p>Two mechanisms, because one of them cannot survive a restart and the other cannot be exact:
 *
 * <ol>
 *   <li><b>The handle</b> remembers the entity UUID of everything it spawned, so
 *       {@link #close(Handle)} can go straight to each one. This is exact and covers <em>any</em>
 *       mob, including one from another mod — but it lives in memory and dies with the process.</li>
 *   <li><b>A persistent wave marker.</b> Every spawned {@link NeroCreatureEntity} additionally
 *       carries the wave's id in its own saved data ({@code WaveId}), so {@link #sweep} can find and
 *       remove a wave whose handle is long gone — after a crash, or in a later session. This is why
 *       the marker is persisted rather than kept in a static map: an abandoned invasion is exactly
 *       the case the marker exists for.</li>
 * </ol>
 *
 * <p>{@link #close(Handle)} runs both. Creatures from other mods therefore survive a restart mid-
 * event; that is a documented limit of what this mod can promise about somebody else's entity.
 *
 * <p><b>Removal is {@code discard()}, not a kill.</b> Wave mobs vanish rather than dying, so an
 * event ending does not shower the arena in loot or experience that nobody earned.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>A wave id is a random UUID identifying a <em>batch of mobs</em>. No player is recorded here, on
 * a handle, or in a creature's wave marker, and nothing on these paths logs player identity.
 *
 * <p>Server-side only, server thread only. Nothing here is synchronised.
 */
public final class InvasionBudget {

    /** The most creatures one wave may reserve. A raid, not a siege engine. */
    public static final int MAX_BUDGET = 128;

    /** Upper bound on the entities one {@link #sweep} pass will look at. */
    private static final int SWEEP_LIMIT = 512;

    private InvasionBudget() {
    }

    /**
     * One reserved wave. Short-lived by contract: reserve it, fill it, close it. A handle holds a
     * reference to its level, so leaking one without closing it keeps that level referenced —
     * which is the same "you must close this" contract every other resource handle has.
     */
    public static final class Handle {

        private final UUID waveId = UUID.randomUUID();
        private final ServerLevel level;
        private final int budget;
        private final Set<UUID> spawned = new LinkedHashSet<>();
        private int remaining;
        private boolean closed;

        private Handle(ServerLevel level, int budget) {
            this.level = level;
            this.budget = budget;
            this.remaining = budget;
        }

        /** This wave's id — the value written into each spawned creature's {@code WaveId}. */
        public UUID waveId() {
            return this.waveId;
        }

        /** The level this wave was reserved in. */
        public ServerLevel level() {
            return this.level;
        }

        /** How many creatures were reserved. */
        public int budget() {
            return this.budget;
        }

        /** How many of the reserved creatures are still unspent. */
        public int remaining() {
            return this.remaining;
        }

        /** How many creatures this wave has actually placed. */
        public int placed() {
            return this.spawned.size();
        }

        /** Whether {@link InvasionBudget#close(Handle)} has already run for this wave. */
        public boolean isClosed() {
            return this.closed;
        }
    }

    /**
     * Reserves room for up to {@code budget} extra creatures in {@code level}.
     *
     * <p>Reserving costs nothing and refuses nothing — the caps are consulted per spawn, not here,
     * because a wave placed over half a minute cannot sensibly be approved all at once. A budget
     * above {@link #MAX_BUDGET} is clamped; a budget of zero or less yields a handle that will never
     * place anything, which is a legitimate "this event is disabled" answer rather than an error.
     *
     * @param level  the level the wave will be placed in
     * @param budget how many creatures the wave may add beyond the standing dimension cap
     */
    public static Handle reserve(ServerLevel level, int budget) {
        return new Handle(level, Math.clamp(budget, 0, MAX_BUDGET));
    }

    /**
     * Places one creature of {@code type} at {@code pos}, against the wave's budget.
     *
     * <p>Refuses — returning {@code null}, having changed nothing — when the wave is closed or
     * spent, when either population cap says no, or when the entity could not be created or added.
     * A caller placing a wave should treat {@code null} as "try somewhere else", exactly as the
     * natural-spawn sweep does.
     *
     * @return the placed creature, or {@code null} if nothing was placed
     */
    @Nullable
    public static Mob spawnWithin(Handle handle, EntityType<? extends Mob> type, BlockPos pos) {
        if (handle == null || handle.closed || handle.remaining <= 0 || type == null) {
            return null;
        }
        ServerLevel level = handle.level;
        if (!hasWaveRoom(level, handle) || !CreatureCensus.chunkHasRoom(level, pos)) {
            return null;
        }
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return null;
        }
        mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        // Mark BEFORE the entity enters the level, so a wave mob is never briefly unmarked.
        if (mob instanceof NeroCreatureEntity creature) {
            creature.setWaveId(handle.waveId);
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        if (!level.addFreshEntity(mob)) {
            return null;
        }
        handle.spawned.add(mob.getUUID());
        handle.remaining--;
        // A wave is a large, deliberate addition; the cached population count is now stale.
        CreatureCensus.invalidate(level);
        return mob;
    }

    /**
     * The dimension cap, with this wave's allowance added on top. The chunk cap is checked
     * separately and is never relaxed.
     */
    private static boolean hasWaveRoom(ServerLevel level, Handle handle) {
        int cap = NeroCreaturesConfig.MAX_CREATURES_PER_DIMENSION.get();
        if (cap <= 0) {
            return false; // "no NeroCreatures in this dimension" means exactly that
        }
        return CreatureCensus.inDimension(level) < cap + handle.budget;
    }

    /**
     * Ends a wave and removes whatever is left of it: first everything the handle placed, then a
     * {@link #sweep} for anything carrying this wave's marker that the handle lost track of.
     *
     * <p>Idempotent — closing twice removes nothing the second time. Safe to call from an event's
     * cleanup path without first checking whether the wave ever placed anything.
     *
     * @return how many creatures were actually removed
     */
    public static int close(Handle handle) {
        if (handle == null || handle.closed) {
            return 0;
        }
        handle.closed = true;
        ServerLevel level = handle.level;
        int removed = 0;
        for (UUID id : handle.spawned) {
            Entity entity = level.getEntity(id);
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
                removed++;
            }
        }
        handle.spawned.clear();
        removed += sweep(level, handle.waveId);
        if (removed > 0) {
            CreatureCensus.invalidate(level);
            // Counts only — a wave id is not a player id, but nothing here needs to be in the log
            // beyond "an event tidied up after itself".
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Invasion wave closed: removed {} surviving creature(s).", removed);
        }
        return removed;
    }

    /**
     * Removes every loaded {@link NeroCreatureEntity} carrying {@code waveId}, whether or not a
     * handle for it still exists. This is the restart-safe half of {@link #close(Handle)}, and the
     * only way to clean up a wave whose event mod went away mid-invasion.
     *
     * <p>Bounded to {@value #SWEEP_LIMIT} entities per level per call, which is far above
     * {@link #MAX_BUDGET}; a wave larger than that could not have been placed by this API.
     *
     * @return how many creatures were removed
     */
    public static int sweep(ServerLevel level, UUID waveId) {
        if (level == null || waveId == null) {
            return 0;
        }
        List<NeroCreatureEntity> found = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(NeroCreatureEntity.class),
                creature -> waveId.equals(creature.waveId()), found, SWEEP_LIMIT);
        for (NeroCreatureEntity creature : found) {
            creature.discard();
        }
        return found.size();
    }
}
