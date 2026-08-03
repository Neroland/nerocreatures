package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Surfaces from a burrow with a telegraphed strike — the payoff half of {@link BurrowGoal}.
 *
 * <p>The strike is deliberately readable: for {@code telegraphTicks} the creature stays under while
 * dust erupts at its position (so the player can see where it is about to come up and step away),
 * and only then does it surface and swing. A hidden instant hit would be unfair; a telegraph makes
 * it a mechanic.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>The telegraph is a particle packet every {@value #TELEGRAPH_PARTICLE_INTERVAL} ticks, not
 *       every tick.</li>
 *   <li>No pathfinding at all: the goal holds position and then does a single
 *       {@code doHurtTarget} on the surfacing tick.</li>
 *   <li>{@link #stop()} always clears the burrowed flag, so an interrupted emerge can never leave
 *       the creature invulnerable.</li>
 * </ul>
 *
 * @param <T> the creature type — a pathfinding mob that implements {@link Burrower}
 */
public class EmergeAttackGoal<T extends PathfinderMob & Burrower> extends Goal {

    /** Ticks between telegraph particle bursts. */
    private static final int TELEGRAPH_PARTICLE_INTERVAL = 4;

    protected final T mob;

    private final double strikeRangeSqr;
    private final int telegraphTicks;

    private int ticksRunning;
    private boolean struck;

    /**
     * @param mob            the burrower
     * @param strikeRange    distance (blocks) the target must be within for the strike to land
     * @param telegraphTicks how long the "it is about to come up" warning lasts
     */
    public EmergeAttackGoal(T mob, double strikeRange, int telegraphTicks) {
        this.mob = mob;
        this.strikeRangeSqr = strikeRange * strikeRange;
        this.telegraphTicks = Math.max(1, telegraphTicks);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!this.mob.isBurrowed()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        return this.mob.distanceToSqr(target) <= this.strikeRangeSqr;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.struck && this.mob.getTarget() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.ticksRunning = 0;
        this.struck = false;
        this.mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        // Whatever happened — struck, target lost, or interrupted — the creature ends up above
        // ground. This is the single place the invulnerable state is guaranteed to be released.
        this.mob.setBurrowed(false);
        this.struck = false;
    }

    @Override
    public void tick() {
        this.ticksRunning++;
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.ticksRunning < this.telegraphTicks) {
            if (this.ticksRunning % TELEGRAPH_PARTICLE_INTERVAL == 0) {
                this.onTelegraph(level);
            }
            return;
        }
        if (!this.struck) {
            this.struck = true;
            this.mob.setBurrowed(false);
            this.onEmerge(level);
            if (this.mob.distanceToSqr(target) <= this.strikeRangeSqr) {
                this.mob.doHurtTarget(level, target);
            }
        }
    }

    /** Warning feedback while still under. Default: dust puffing out of the ground. */
    protected void onTelegraph(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                8, 0.4D, 0.0D, 0.4D, 0.05D);
    }

    /** Feedback for the surfacing itself. Default: the vanilla warden emerge sound plus dust. */
    protected void onEmerge(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                30, 0.8D, 0.3D, 0.8D, 0.1D);
        this.mob.playSound(SoundEvents.WARDEN_EMERGE, 1.0F, 1.0F);
    }
}
