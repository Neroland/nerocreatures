package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a mob inside a circle around an anchor point, and does nothing else.
 *
 * <p>Two creatures need exactly this and nothing more: a pet under the {@code GUARD} order, which
 * must hold a doorway rather than drift off after a stray stroll goal, and a terraforming drone,
 * whose whole contract is that it works the area it was deployed in. Both otherwise wander with a
 * plain vanilla stroll goal; this goal only wakes up when that wandering has taken them too far.
 *
 * <p>The anchor is supplied lazily so the owner can move it (a re-issued guard order, a redeployed
 * drone) without re-registering goals, and an {@code active} predicate lets a mob turn the leash off
 * entirely — a pet on {@code FOLLOW} or {@code SIT} is not held anywhere.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>{@link #canUse()} is one boolean call, one nullable read and one squared-distance
 *       comparison. No entity scans, no ray casts, no pathfinding.</li>
 *   <li>While active, one navigation call every {@value #REPATH_INTERVAL_TICKS} ticks — the same
 *       order as vanilla's stroll goals, and less often than {@code MeleeAttackGoal}.</li>
 *   <li>It holds only the {@code MOVE} flag, so it yields to combat movement rather than fighting
 *       it: a guarding pet that is attacked chases, and re-anchors when the fight ends.</li>
 *   <li>The goal stops as soon as the mob is back inside {@link #settleRadius}, which is
 *       deliberately smaller than the leash radius — without that hysteresis a mob parked exactly on
 *       the boundary would re-path every interval forever.</li>
 * </ul>
 */
public class HoldAreaGoal extends Goal {

    /** Ticks between navigation updates while walking back. */
    private static final int REPATH_INTERVAL_TICKS = 20;

    /** How much closer than the leash radius the mob must get before the goal lets go. */
    private static final double SETTLE_FRACTION = 0.6D;

    private final PathfinderMob mob;
    private final Supplier<BlockPos> anchor;
    private final BooleanSupplier active;
    private final double radiusSqr;
    private final double settleRadiusSqr;
    private final double speed;

    private int repathCooldown;

    /**
     * @param mob    the mob to keep in place
     * @param anchor supplies the current anchor; may return {@code null}, which disables the goal
     * @param radius how far the mob may stray before it is walked back, in blocks
     * @param speed  navigation speed modifier for the walk back
     * @param active whether the leash applies at all right now
     */
    public HoldAreaGoal(PathfinderMob mob, Supplier<BlockPos> anchor, double radius, double speed,
            BooleanSupplier active) {
        this.mob = mob;
        this.anchor = anchor;
        this.active = active;
        this.radiusSqr = radius * radius;
        double settle = radius * SETTLE_FRACTION;
        this.settleRadiusSqr = settle * settle;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.active.getAsBoolean() || this.mob.getTarget() != null) {
            return false;
        }
        BlockPos home = this.anchor.get();
        return home != null && this.distanceSqrTo(home) > this.radiusSqr;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.active.getAsBoolean() || this.mob.getTarget() != null) {
            return false;
        }
        BlockPos home = this.anchor.get();
        // Hysteresis: keep walking until well inside, not merely back on the boundary.
        return home != null && this.distanceSqrTo(home) > this.settleRadiusSqr;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = REPATH_INTERVAL_TICKS;
        BlockPos home = this.anchor.get();
        if (home != null) {
            this.mob.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D,
                    this.speed);
        }
    }

    /** The settle radius this goal will walk the mob back inside, in blocks. */
    public double settleRadius() {
        return Math.sqrt(this.settleRadiusSqr);
    }

    private double distanceSqrTo(BlockPos pos) {
        return this.mob.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }
}
