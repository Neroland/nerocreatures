package za.co.neroland.nerocreatures.block;

/**
 * Pure rules for the single-item display blocks (Trophy Plaque, and the Terrarium's pod hand-over):
 * <b>exactly one item, never a stack</b>. Use an accepted item on an empty block to put one in (one is
 * taken from the held stack, however large); use an empty hand on a full block to take it back. A full
 * block refuses another item rather than swapping, so nothing can be lost in a swap on a busy tick.
 */
public final class DisplayRules {

    private DisplayRules() {
    }

    public enum Action {
        /** Put one of the held item in. */
        INSERT,
        /** Give the shown item back to the player. */
        TAKE,
        /** Already showing something; take it out first. */
        REFUSE_FULL,
        /** This block does not show that item. */
        REFUSE_ITEM,
        /** Nothing to do (empty hand on an empty block). */
        PASS
    }

    public static Action decide(boolean occupied, boolean handEmpty, boolean accepted) {
        if (handEmpty) {
            return occupied ? Action.TAKE : Action.PASS;
        }
        if (!accepted) {
            return Action.REFUSE_ITEM;
        }
        return occupied ? Action.REFUSE_FULL : Action.INSERT;
    }

    /** How many items an insert moves: always one. */
    public static int insertCount(int held) {
        return held > 0 ? 1 : 0;
    }
}
