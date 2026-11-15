package za.co.neroland.nerocreatures.item;

import org.jetbrains.annotations.Nullable;

/**
 * The order of operations for a capture and a release, kept apart from the world so the duplication
 * tests drive the exact code the Capture Pod runs.
 *
 * <p>The invariant is <b>one creature, one place</b>: at the end of every call the creature is either
 * in the world or in the pod, never both and never neither.
 *
 * <ul>
 *   <li><b>Capture:</b> the pod must still be in the user's hand and be a single, empty pod; the
 *       creature must still be present. The pod is filled first and the creature removed second; if
 *       the removal fails the pod is emptied again (rollback).</li>
 *   <li><b>Release:</b> the pod must still be in hand and filled. The creature is spawned first and
 *       the pod emptied only once the spawn succeeded; a refused spawn (a wall, a claim, a cap) leaves
 *       the pod exactly as it was.</li>
 * </ul>
 *
 * <p>Both run on the server thread in one call, so nothing (a drop, a death, a chunk unload) can
 * interleave between the steps; the re-checks at the start are what catch a pod dropped or a
 * creature killed between the click and the server handling it.
 *
 * @param <S> the snapshot type ({@code CapturedCreature} in game)
 */
public final class CaptureFlow {

    /** The pod side of a capture or release. */
    public interface Pod<S> {
        /** Still in the user's hand, and a stack of exactly one. */
        boolean heldAndSingle();

        /** Holds a capture (any capture, even one this reader cannot parse). */
        boolean filled();

        /** The capture held, or null when empty or unreadable. */
        @Nullable
        S contents();

        void fill(S snapshot);

        void empty();
    }

    /** The creature side of a capture. */
    public interface Target<S> {
        /** Alive, loaded and not removed. */
        boolean present();

        /** The stripped snapshot, or null if it cannot be taken. */
        @Nullable
        S snapshot();

        /** Removes the creature from the world; false if it could not be. */
        boolean remove();
    }

    /** The world side of a release. */
    public interface Spawner<S> {
        /** Puts the creature into the world; false (and no change) if refused. */
        boolean spawn(S snapshot);
    }

    /** What happened. */
    public enum Outcome {
        CAPTURED,
        RELEASED,
        /** Pod not in hand, not single, or already filled (capture) / empty (release). */
        POD_REFUSED,
        /** The creature was gone or could not be taken. */
        GONE,
        /** The spawn was refused; the pod is unchanged. */
        KEPT
    }

    private CaptureFlow() {
    }

    /** Captures {@code target} into {@code pod}. */
    public static <S> Outcome capture(Pod<S> pod, Target<S> target) {
        if (!pod.heldAndSingle() || pod.filled()) {
            return Outcome.POD_REFUSED;
        }
        if (!target.present()) {
            return Outcome.GONE;
        }
        S snapshot = target.snapshot();
        if (snapshot == null) {
            return Outcome.GONE;
        }
        pod.fill(snapshot);
        if (!target.remove()) {
            pod.empty();
            return Outcome.GONE;
        }
        return Outcome.CAPTURED;
    }

    /** Releases the pod's creature through {@code spawner}. */
    public static <S> Outcome release(Pod<S> pod, Spawner<S> spawner) {
        if (!pod.heldAndSingle() || !pod.filled()) {
            return Outcome.POD_REFUSED;
        }
        S snapshot = pod.contents();
        if (snapshot == null) {
            return Outcome.KEPT;
        }
        if (!spawner.spawn(snapshot)) {
            return Outcome.KEPT;
        }
        pod.empty();
        return Outcome.RELEASED;
    }
}
