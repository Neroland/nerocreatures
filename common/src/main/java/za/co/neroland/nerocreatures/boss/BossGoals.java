package za.co.neroland.nerocreatures.boss;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The narrow seam through which {@link BossController} swaps a boss's per-phase goal set.
 *
 * <p>It exists because {@code Mob.goalSelector} is {@code protected}: only the entity itself can
 * reach it, and the controller is deliberately not an entity. Rather than moving the phase machine
 * into the entity class (where every boss would inherit a copy of it) the entity implements this
 * two-method interface and hands itself to its controller.
 *
 * <p>Implementations must add and remove against the boss's own {@code goalSelector} and do nothing
 * else. In particular they must not filter or re-prioritise: the controller tracks exactly the goal
 * objects it installed so it can take exactly those away again, and an implementation that quietly
 * dropped one would leave a phase's goal running for the rest of the fight.
 */
public interface BossGoals {

    /** Installs one phase goal at the given priority. */
    void addPhaseGoal(int priority, Goal goal);

    /** Removes a goal previously installed by {@link #addPhaseGoal}. */
    void removePhaseGoal(Goal goal);
}
