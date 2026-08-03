package za.co.neroland.nerocreatures.entity.ai;

/**
 * Contract for creatures that hide underground between attacks (the Asteroid Worm pattern).
 *
 * <p>The two goals — {@link BurrowGoal} and {@link EmergeAttackGoal} — only flip this flag and
 * respect the timings; the <b>entity</b> owns what "burrowed" actually means. An implementor MUST,
 * while {@link #isBurrowed()} is true:
 *
 * <ul>
 *   <li>be invulnerable (override {@code isInvulnerableTo}) — the whole point of the mechanic is
 *       that you cannot trade hits with something under the rock;</li>
 *   <li>be untargetable (override {@code canBeSeenAsEnemy} / {@code isInvisible}) so other mobs and
 *       auto-targeting do not lock onto it;</li>
 *   <li>synchronise the flag to the client (an {@code EntityDataAccessor<Boolean>}) so the renderer
 *       can hide the body and the client does not draw a floating worm.</li>
 * </ul>
 *
 * <p>Keeping those decisions in the entity is deliberate: goals that guess at entity flags are the
 * usual source of "invulnerable forever" bugs when a goal is interrupted mid-state.
 */
public interface Burrower {

    /** True while the creature is submerged. */
    boolean isBurrowed();

    /**
     * Enter or leave the submerged state. Called only from the two goals, only on the server.
     * Implementations must apply the invulnerability/targetability rules described on this
     * interface, and must tolerate being called with the value it already has.
     */
    void setBurrowed(boolean burrowed);

    /** Minimum ticks the creature must stay under before it is allowed to surface. */
    default int minBurrowTicks() {
        return 60;
    }

    /** Maximum ticks the creature may stay under before it is forced up (anti-stalemate). */
    default int maxBurrowTicks() {
        return 400;
    }
}
