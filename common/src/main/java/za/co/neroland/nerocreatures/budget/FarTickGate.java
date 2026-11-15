package za.co.neroland.nerocreatures.budget;

import net.minecraft.world.entity.Mob;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.world.CreatureOwners;

/**
 * "Far creatures tick slowly": a per-entity gate that tells expensive per-tick work (goal
 * {@code canUse} scans, ability timers, aura pulses, {@code customServerAiStep} logic) to run only
 * every {@code farTickInterval}-th tick while no player is within {@code farTickDistance} blocks.
 * Vanilla movement, physics and damage keep running every tick; only our own work slows.
 *
 * <p>One instance per entity, as a field. It re-checks distance once a second (staggered by
 * entity id), so the steady-state cost is one modulo per call:
 * <pre>{@code
 * private final FarTickGate farTick = new FarTickGate();
 *
 * @Override
 * protected void customServerAiStep(ServerLevel level) {
 *     super.customServerAiStep(level);
 *     if (farTick.skip(this)) {
 *         return;
 *     }
 *     // ... the creature's own per-tick work
 * }
 * // In an expensive goal: canUse() { if (mob.farTick().skip(mob)) return false; ... }
 * }</pre>
 *
 * <p>Never slowed: owned or leashed creatures, ridden or riding creatures, creatures with a target,
 * and anything while {@code farTickInterval} is 1. Server side only (always {@code false} on the
 * client).
 */
public final class FarTickGate {

    /** How often (ticks) the player-distance check is refreshed. */
    static final int RECHECK_TICKS = 20;

    private boolean far;
    private long nextCheck = Long.MIN_VALUE;

    /** Whether this tick's expensive work should be skipped for {@code mob}. */
    public boolean skip(Mob mob) {
        if (mob.level().isClientSide()) {
            return false;
        }
        int interval = NeroCreaturesConfig.FAR_TICK_INTERVAL.get();
        if (interval <= 1) {
            return false;
        }
        long now = mob.level().getGameTime();
        if (now >= nextCheck || now < nextCheck - RECHECK_TICKS) {
            far = isFarNow(mob);
            nextCheck = now + RECHECK_TICKS + (mob.getId() & 7);
        }
        return shouldSkip(far, now, mob.getId(), interval);
    }

    /** Whether the last check found the mob far (for the budget probe). */
    public boolean isFar() {
        return far;
    }

    private static boolean isFarNow(Mob mob) {
        if (mob.getTarget() != null || mob.isLeashed() || mob.isVehicle() || mob.isPassenger()
                || CreatureOwners.ownerUuid(mob) != null) {
            return false;
        }
        double distance = NeroCreaturesConfig.FAR_TICK_DISTANCE.get();
        return !mob.level().hasNearbyAlivePlayer(mob.getX(), mob.getY(), mob.getZ(), distance);
    }

    /**
     * The pure rule: a far mob runs on one tick in {@code interval}, staggered by entity id so a
     * crowd does not all wake on the same tick. Package-private for tests.
     */
    static boolean shouldSkip(boolean far, long gameTime, int entityId, int interval) {
        if (!far || interval <= 1) {
            return false;
        }
        return Math.floorMod(gameTime + entityId, (long) interval) != 0L;
    }
}
