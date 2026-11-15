package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.EnumSet;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Turns what a {@link SoundHearing} remembers into behaviour, on a cadence.
 *
 * <ul>
 *   <li>A fresh noise from a living entity that {@code targetFilter} accepts makes it the target
 *       (a melee goal of the creature does the rest).</li>
 *   <li>A fresh noise with no acceptable source makes the creature walk to where it was heard and
 *       listen there.</li>
 *   <li>A current target that has gone quiet for longer than the memory window is dropped. This is
 *       what makes a blind hunter lose a player who starts sneaking.</li>
 * </ul>
 *
 * <p>The creature must not also carry a sight-based target goal for the same targets, or it is not
 * blind. Cost: one field read per cadence; no entity scan.
 */
public class SoundHuntGoal extends Goal {

    private final PathfinderMob mob;
    private final SoundHearing hearing;
    private final Predicate<LivingEntity> targetFilter;
    private final double speed;
    private final CadenceTimer cadence;

    private long lastHeardTarget = Long.MIN_VALUE;
    private LivingEntity trackedTarget;
    private Vec3 investigate;

    /**
     * @param mob          the hunter
     * @param hearing      the installed listener
     * @param targetFilter which noisy entities may become the target (players, prey)
     * @param speed        walk speed modifier while investigating
     * @param interval     ticks between decisions (10 is a good default)
     */
    public SoundHuntGoal(PathfinderMob mob, SoundHearing hearing, Predicate<LivingEntity> targetFilter,
            double speed, int interval) {
        this.mob = mob;
        this.hearing = hearing;
        this.targetFilter = targetFilter;
        this.speed = speed;
        this.cadence = new CadenceTimer(interval);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        if (!this.cadence.ready(now, this.mob.getId())) {
            return false;
        }
        LivingEntity current = this.mob.getTarget();
        if (current != this.trackedTarget) {
            // A target from elsewhere (it hurt us): give it one memory window of grace.
            this.trackedTarget = current;
            this.lastHeardTarget = now;
        }
        if (!this.hearing.hasFreshNoise(now)) {
            if (current != null && SoundHuntRules.expired(this.lastHeardTarget, now, this.hearing.memoryTicks())) {
                this.mob.setTarget(null);
                this.trackedTarget = null;
            }
            return false;
        }
        Entity source = this.hearing.sourceEntity(level);
        if (source instanceof LivingEntity living && living.isAlive() && this.targetFilter.test(living)) {
            this.lastHeardTarget = this.hearing.heardAt();
            if (current != living) {
                this.mob.setTarget(living);
            }
            this.trackedTarget = living;
            this.hearing.forget();
            return false;
        }
        if (current != null) {
            return false;
        }
        this.investigate = this.hearing.position();
        this.hearing.forget();
        return true;
    }

    @Override
    public void start() {
        if (this.investigate != null) {
            this.mob.getNavigation().moveTo(this.investigate.x, this.investigate.y, this.investigate.z, this.speed);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.getTarget() == null && !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.investigate = null;
    }
}
