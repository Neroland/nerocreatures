package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Short-range teleport that closes the gap to the current target — the ambusher's signature move.
 *
 * <p>The mob blinks to a point just behind/beside its target, then immediately resumes pathing, so
 * the player experiences "it was over there, now it is on me" rather than a slow approach.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>{@link #canUse()} is three field reads and one squared-distance compare in the common case,
 *       because the cooldown is checked first. The goal selector already only re-evaluates every
 *       other tick.</li>
 *   <li>There is <b>no pathfinding</b> in this goal. {@link #start()} does at most
 *       {@value #MAX_ATTEMPTS} {@code randomTeleport} attempts (each a handful of collision checks)
 *       and then hands the mob straight back to its melee goal.</li>
 *   <li>The line-of-sight test reuses the mob's existing {@code Sensing} cache — it is not a fresh
 *       ray trace.</li>
 *   <li>Hard cap: one blink per {@code cooldownTicks}. With the default 100 ticks that is at most
 *       5 blinks a minute per mob, which is what keeps a pack of ambushers affordable.</li>
 * </ul>
 */
public class BlinkStrikeGoal extends Goal {

    /** Teleport attempts before the goal gives up for this cooldown window. */
    private static final int MAX_ATTEMPTS = 4;

    protected final PathfinderMob mob;

    private final double minDistanceSqr;
    private final double maxDistanceSqr;
    private final int cooldownTicks;

    /** Ticks remaining before another blink is allowed. */
    private int cooldown;

    /**
     * @param mob            the blinker
     * @param minDistance    do not blink when already closer than this (blocks)
     * @param maxDistance    do not blink when further away than this (blocks)
     * @param cooldownTicks  minimum ticks between blinks
     */
    public BlinkStrikeGoal(PathfinderMob mob, double minDistance, double maxDistance, int cooldownTicks) {
        this.mob = mob;
        this.minDistanceSqr = minDistance * minDistance;
        this.maxDistanceSqr = maxDistance * maxDistance;
        this.cooldownTicks = Math.max(1, cooldownTicks);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double distanceSqr = this.mob.distanceToSqr(target);
        if (distanceSqr < this.minDistanceSqr || distanceSqr > this.maxDistanceSqr) {
            return false;
        }
        return this.mob.getSensing().hasLineOfSight(target);
    }

    /** A blink is instantaneous: everything happens in {@link #start()} and the goal ends. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        this.cooldown = this.cooldownTicks;
        LivingEntity target = this.mob.getTarget();
        if (target == null || !(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        double fromX = this.mob.getX();
        double fromY = this.mob.getY();
        double fromZ = this.mob.getZ();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double offsetX = (this.mob.getRandom().nextDouble() - 0.5D) * 4.0D;
            double offsetZ = (this.mob.getRandom().nextDouble() - 0.5D) * 4.0D;
            double toX = target.getX() + offsetX;
            double toY = target.getY();
            double toZ = target.getZ() + offsetZ;
            if (this.mob.randomTeleport(toX, toY, toZ, false)) {
                this.onBlink(level, fromX, fromY, fromZ);
                this.mob.getNavigation().moveTo(target, 1.0D);
                return;
            }
        }
    }

    /**
     * Feedback hook, fired only on the server after a successful blink. The default plays the
     * vanilla enderman teleport sound at both ends and puffs portal particles at the origin;
     * concrete creatures override it for their own sound/particle identity.
     *
     * @param level the server level the blink happened in
     * @param fromX origin X (the destination is the mob's current position)
     * @param fromY origin Y
     * @param fromZ origin Z
     */
    protected void onBlink(ServerLevel level, double fromX, double fromY, double fromZ) {
        level.sendParticles(ParticleTypes.PORTAL, fromX, fromY + 1.0D, fromZ, 12,
                0.3D, 0.5D, 0.3D, 0.05D);
        this.mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.4F);
    }
}
