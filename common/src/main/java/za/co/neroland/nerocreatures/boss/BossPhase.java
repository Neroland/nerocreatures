package za.co.neroland.nerocreatures.boss;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * One stage of a boss fight: a health threshold, the goal set that is active while the fight is in
 * this stage, and a one-off action to run on the way in.
 *
 * <p>A phase is a <b>description</b>, not a state — it holds no per-fight data, so the same list can
 * be handed to every instance of a boss. The live state (which phase is current, which goal objects
 * are installed) belongs to {@link BossController}.
 *
 * <h2>How the threshold is read</h2>
 *
 * <p>{@link #healthFraction()} is the health fraction <em>at or below which</em> the phase becomes
 * current, and phases are declared in descending order — the first phase is always {@code 1.0F}
 * (the fight starts there), the second might be {@code 0.66F}, the third {@code 0.33F}. The
 * controller advances to the last phase whose fraction the boss has dropped to, and
 * <b>never goes backwards</b>: healing a boss does not rewind its fight. That is deliberate — a
 * phase entry action (an enrage, a summon) is not something that can be sensibly undone, and a
 * regenerating boss that oscillated between phases would swap its whole goal set every few ticks.
 *
 * <h2>Goals</h2>
 *
 * <p>{@link #goals()} is a <b>factory</b>, called once each time the phase is entered. Goals hold
 * per-use state (cooldowns, telegraph counters), so a phase must never hand out shared instances.
 * The goals it returns are added to the boss's {@code goalSelector} on entry and removed again on
 * exit; anything the boss should always be able to do (floating, ordinary melee, looking around)
 * belongs in the boss's own {@code registerGoals} instead, where the controller will not touch it.
 *
 * @param id             stable, non-translated id used in logs and as the phase's identity
 *                       ({@code "stomp"}); never shown to a player
 * @param title          the phase's display name, shown on the boss bar next to the boss's name
 * @param healthFraction health fraction at or below which this phase becomes current, {@code 0..1}
 * @param goals          builds this phase's goal set; called on every entry, never cached
 * @param onEnter        one-off entry action, run on the server when the phase is first entered.
 *                       <b>Not</b> re-run when a boss is loaded from disk already in this phase —
 *                       see {@link BossController}.
 */
public record BossPhase(
        String id,
        Component title,
        float healthFraction,
        Supplier<List<PhaseGoal>> goals,
        Consumer<ServerLevel> onEnter) {

    /** A goal and the priority it is installed at while its phase is current. */
    public record PhaseGoal(int priority, Goal goal) {
    }

    public BossPhase {
        if (healthFraction <= 0.0F || healthFraction > 1.0F) {
            throw new IllegalArgumentException("Phase health fraction must be in (0, 1]: " + id);
        }
        if (goals == null) {
            throw new IllegalArgumentException("Phase goal factory must not be null: " + id);
        }
    }

    /** A phase with a goal set and no entry action. */
    public static BossPhase of(String id, Component title, float healthFraction,
            Supplier<List<PhaseGoal>> goals) {
        return new BossPhase(id, title, healthFraction, goals, level -> { });
    }

    /** Convenience for the common {@code new PhaseGoal(priority, goal)}. */
    public static PhaseGoal goal(int priority, Goal goal) {
        return new PhaseGoal(priority, goal);
    }
}
