package za.co.neroland.nerocreatures.entity.luna;

/**
 * The pure rules behind the Lumen Moth's living lantern (decision 006). No game types, so the rule
 * and its cap are unit-tested.
 *
 * <ul>
 *   <li><b>One light per moth, always.</b> A moth remembers at most one position. A move places the
 *       new light and removes the old one in the same step, so there is never a second block.</li>
 *   <li><b>Air only.</b> The target is the owner's head block, then their feet block, then the
 *       moth's own block, whichever is air (or already this moth's light). Water and replaceables
 *       are never used.</li>
 *   <li><b>Always cleaned up.</b> When the lantern is not active (wild, sitting, owner gone or
 *       elsewhere, daylight in the open) a remembered light is cleared.</li>
 *   <li><b>Denied means fallback.</b> When the griefing gate says no, nothing is placed and the moth
 *       lends Night Vision with glow particles instead.</li>
 * </ul>
 */
public final class LanternRules {

    /** Light level of the placed {@code minecraft:light} block. */
    public static final int LIGHT_LEVEL = 12;

    /** Ticks between lantern updates. */
    public static final int UPDATE_INTERVAL = 4;

    /** The owner must be this close (blocks) for the lantern to be lit. */
    public static final double OWNER_RANGE = 16.0D;

    /** The most light blocks one moth ever holds. */
    public static final int MAX_LIGHTS_PER_MOTH = 1;

    /** What the lantern does this update. */
    public enum Action {
        /** Nothing lit and nothing to light. */
        NONE,
        /** The light is where it should be. */
        KEEP,
        /** Place at the new target (after asking the gate) and remove the old one. */
        MOVE,
        /** Remove the remembered light. */
        CLEAR
    }

    /** What a {@link Action#MOVE} turns into after the griefing gate answered. */
    public enum Outcome {
        PLACE,
        FALLBACK
    }

    private LanternRules() {
    }

    /**
     * @param active        tamed, following, owner online here and in range, and it is dark
     * @param hasLight      a light is remembered (and is still there)
     * @param targetChanged the chosen target differs from the remembered light
     */
    public static Action decide(boolean active, boolean hasLight, boolean targetChanged) {
        if (!active) {
            return hasLight ? Action.CLEAR : Action.NONE;
        }
        if (hasLight && !targetChanged) {
            return Action.KEEP;
        }
        return Action.MOVE;
    }

    /** The world-change branch: place only when the gate allowed it, otherwise the fallback. */
    public static Outcome outcome(boolean allowed) {
        return allowed ? Outcome.PLACE : Outcome.FALLBACK;
    }

    /**
     * Which candidate is the target: 0 = owner's head block, 1 = owner's feet block, 2 = the moth's
     * own block, -1 = none (no air anywhere). Each flag means "air, or already this moth's light".
     */
    public static int chooseTarget(boolean headFree, boolean feetFree, boolean mothFree) {
        if (headFree) {
            return 0;
        }
        if (feetFree) {
            return 1;
        }
        return mothFree ? 2 : -1;
    }

    /** Whether a block is one this mod's moths placed: a light block at exactly {@link #LIGHT_LEVEL}. */
    public static boolean isMothLight(boolean isLightBlock, int level) {
        return isLightBlock && level == LIGHT_LEVEL;
    }
}
