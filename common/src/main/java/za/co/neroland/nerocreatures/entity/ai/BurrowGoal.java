package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Dives underground and stalks the target from below. The other half of the pattern is
 * {@link EmergeAttackGoal}, which brings the creature back up with a telegraphed strike.
 *
 * <p>Behaviour: with a live target further away than {@code emergeRange}, the creature submerges
 * (via {@link Burrower#setBurrowed}) and paths toward the target while under. It stays down for at
 * least {@link Burrower#minBurrowTicks()} and at most {@link Burrower#maxBurrowTicks()} — the upper
 * bound exists so an unreachable target (a player on a pillar) can never leave the creature
 * invulnerable forever.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>One navigation re-path every {@value #REPATH_INTERVAL_TICKS} ticks, not every tick. That is
 *       the only expensive call in the goal and it is the same order as vanilla
 *       {@code MeleeAttackGoal}.</li>
 *   <li>{@link #canUse()} is a null check plus one squared distance — no ray casts, no entity
 *       scans.</li>
 *   <li>{@link #stop()} always clears the burrowed flag, including on interruption, so the
 *       invulnerable state cannot leak.</li>
 * </ul>
 *
 * @param <T> the creature type — a pathfinding mob that implements {@link Burrower}
 */
public class BurrowGoal<T extends PathfinderMob & Burrower> extends Goal {

    /** Ticks between navigation updates while submerged. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    protected final T mob;

    private final double emergeRangeSqr;
    private final double speedModifier;

    private int burrowedTicks;
    private int repathCooldown;

    /**
     * @param mob           the burrower
     * @param emergeRange   distance (blocks) at which it stops burrowing and hands over to
     *                      {@link EmergeAttackGoal}
     * @param speedModifier navigation speed multiplier while submerged
     */
    public BurrowGoal(T mob, double emergeRange, double speedModifier) {
        this.mob = mob;
        this.emergeRangeSqr = emergeRange * emergeRange;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isBurrowed()) {
            return false; // already under — EmergeAttackGoal owns that state
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        return this.mob.distanceToSqr(target) > this.emergeRangeSqr;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mob.isBurrowed()) {
            return false;
        }
        if (this.burrowedTicks >= this.mob.maxBurrowTicks()) {
            return false; // forced surfacing: never stay invulnerable indefinitely
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        // Close enough to strike, and we have been under long enough: hand over to the emerge goal.
        return !(this.burrowedTicks >= this.mob.minBurrowTicks()
                && this.mob.distanceToSqr(target) <= this.emergeRangeSqr);
    }

    @Override
    public void start() {
        this.burrowedTicks = 0;
        this.repathCooldown = 0;
        this.mob.setBurrowed(true);
        if (this.mob.level() instanceof ServerLevel level) {
            this.onSubmerge(level);
        }
    }

    @Override
    public void stop() {
        // Only clear the flag when we are not handing over to EmergeAttackGoal. The emerge goal
        // takes over an already-burrowed mob; anything else (target lost, timeout, interruption)
        // must surface here so the mob can never be left invulnerable.
        if (!this.handingOverToEmerge()) {
            this.mob.setBurrowed(false);
        }
        this.mob.getNavigation().stop();
    }

    private boolean handingOverToEmerge() {
        LivingEntity target = this.mob.getTarget();
        return target != null
                && target.isAlive()
                && this.burrowedTicks >= this.mob.minBurrowTicks()
                && this.mob.distanceToSqr(target) <= this.emergeRangeSqr;
    }

    @Override
    public void tick() {
        this.burrowedTicks++;
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        if (--this.repathCooldown <= 0) {
            this.repathCooldown = REPATH_INTERVAL_TICKS;
            this.mob.getNavigation().moveTo(target, this.speedModifier);
        }
    }

    /** How long the creature has been under on this dive. */
    protected final int burrowedTicks() {
        return this.burrowedTicks;
    }

    /**
     * Feedback hook for the dive, fired once on the server. Default: a puff of dust and the vanilla
     * warden dig sound. Concrete creatures override for their own identity.
     */
    protected void onSubmerge(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                20, 0.6D, 0.1D, 0.6D, 0.02D);
        this.mob.playSound(SoundEvents.WARDEN_DIG, 1.0F, 0.8F);
    }
}
