package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Latch a tether onto the current target and hold it until it breaks.
 *
 * <p>The effect is supplied by the creature ({@link TetherEffect}). Effects <b>must be
 * self-expiring</b> (a short mob effect or a transient attribute modifier refreshed every hold
 * pulse), because a goal is not told when its creature unloads. {@link TetherEffect#release} is still
 * called on every normal end: break, death, target loss, interruption, and from
 * {@link #releaseNow} which the creature calls from {@code remove(RemovalReason)} and
 * {@code die(DamageSource)}.
 *
 * <p>Damage breaks the tether only if the creature forwards it: call {@link #onTethererHurt} from
 * {@code hurtServer} after a successful hit.
 */
public class TetherGoal extends Goal {

    /** What a tether does to its target. All calls are server-side. */
    public interface TetherEffect {
        /** Once, when the tether latches. */
        void latch(ServerLevel level, PathfinderMob tetherer, LivingEntity target);

        /** Every hold pulse while held. Must refresh a self-expiring effect. */
        void hold(ServerLevel level, PathfinderMob tetherer, LivingEntity target);

        /** Once, when the tether ends for any reason; {@code target} may be null if gone. */
        void release(ServerLevel level, PathfinderMob tetherer, LivingEntity target, TetherState.Status why);
    }

    private final PathfinderMob mob;
    private final TetherState state;
    private final double latchRangeSqr;
    private final int pulseInterval;
    private final TetherEffect effect;
    private final CadenceTimer pulse;

    private TetherState.Status lastStatus = TetherState.Status.IDLE;

    /**
     * @param mob           the tetherer
     * @param state         break distance, break damage, max duration and cooldown
     * @param latchRange    blocks within which it can latch
     * @param pulseInterval ticks between hold pulses (5-10)
     * @param effect        what the tether does
     */
    public TetherGoal(PathfinderMob mob, TetherState state, double latchRange, int pulseInterval,
            TetherEffect effect) {
        this.mob = mob;
        this.state = state;
        this.latchRangeSqr = latchRange * latchRange;
        this.pulseInterval = Math.max(1, pulseInterval);
        this.pulse = new CadenceTimer(this.pulseInterval);
        this.effect = effect;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    public TetherState state() {
        return this.state;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.state.canLatch(this.mob.level().getGameTime())
                && this.mob.distanceToSqr(target) <= this.latchRangeSqr
                && this.mob.hasLineOfSight(target);
    }

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.state.latch(target.getId(), level.getGameTime())) {
            this.lastStatus = TetherState.Status.HOLD;
            this.effect.latch(level, this.mob, target);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.state.isLatched() && this.lastStatus == TetherState.Status.HOLD && this.mob.isAlive();
    }

    @Override
    public void tick() {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (!this.pulse.ready(now, this.mob.getId())) {
            return;
        }
        LivingEntity target = this.latchedTarget(level);
        if (target == null || !target.isAlive()) {
            this.lastStatus = TetherState.Status.BROKEN_DISTANCE;
            return;
        }
        this.lastStatus = this.state.check(this.mob.distanceToSqr(target), now);
        if (this.lastStatus == TetherState.Status.HOLD) {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            this.effect.hold(level, this.mob, target);
        }
    }

    @Override
    public void stop() {
        if (this.mob.level() instanceof ServerLevel level) {
            this.releaseNow(level);
        }
    }

    /** Ends the tether now and runs {@link TetherEffect#release}. Safe to call repeatedly. */
    public void releaseNow(ServerLevel level) {
        if (!this.state.isLatched()) {
            return;
        }
        LivingEntity target = this.latchedTarget(level);
        TetherState.Status why = this.lastStatus == TetherState.Status.HOLD
                ? TetherState.Status.IDLE : this.lastStatus;
        this.state.release(level.getGameTime());
        this.lastStatus = TetherState.Status.IDLE;
        this.effect.release(level, this.mob, target, why);
    }

    /** Forward from {@code hurtServer}: damage to the tetherer can snap the tether. */
    public void onTethererHurt(float amount) {
        if (this.state.recordDamage(amount)) {
            this.lastStatus = TetherState.Status.BROKEN_DAMAGE;
        }
    }

    private LivingEntity latchedTarget(ServerLevel level) {
        Entity entity = level.getEntity(this.state.targetId());
        return entity instanceof LivingEntity living ? living : null;
    }
}
