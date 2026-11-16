package za.co.neroland.nerocreatures.item;

/**
 * The pure rules of the Capture Pod: who may be captured, and when a release may happen. No world
 * access here, so every rule (and every refusal) is unit-tested.
 *
 * <p>A capture is refused, in this order, for: a dead or removed creature; a display creature (NoAI
 * or invulnerable: the gallery, a command-placed exhibit); anything hostile, a boss or an alpha; a
 * mount; a species outside {@code #nerocreatures:capturable} or bigger than {@link #MAX_WIDTH} by
 * {@link #MAX_HEIGHT}; a creature that is riding, ridden or leashed; and a tamed creature that is
 * not the user's own (the entity's owner <b>and</b> the ownership store must both say so).
 */
public final class CaptureRules {

    /** Largest hitbox width a pod takes, in blocks. */
    public static final float MAX_WIDTH = 1.2F;

    /** Largest hitbox height a pod takes, in blocks. */
    public static final float MAX_HEIGHT = 1.6F;

    /** Key added to the stripped entity data when the creature was tamed. A flag, never an identity. */
    public static final String WAS_TAMED_KEY = "nerocreatures:was_tamed";

    /** Why a capture was refused, or {@link #OK}. Each maps to {@code message.nerocreatures.capture_pod.<key>}. */
    public enum Verdict {
        OK("ok"),
        DEAD("gone"),
        DISPLAY("display"),
        HOSTILE("hostile"),
        BOSS("hostile"),
        ALPHA("hostile"),
        MOUNT("mount"),
        NOT_CAPTURABLE("not_capturable"),
        TOO_BIG("too_big"),
        ATTACHED("attached"),
        NOT_YOURS("not_yours");

        private final String key;

        Verdict(String key) {
            this.key = key;
        }

        /** The lang suffix for the refusal message. */
        public String key() {
            return this.key;
        }
    }

    /**
     * What the pod knows about a candidate, read on the server.
     *
     * @param alive         alive and not removed
     * @param display       NoAI or invulnerable (gallery and exhibits)
     * @param hostile       an {@code Enemy}
     * @param boss          a boss entity
     * @param alpha         an alpha
     * @param mount         a mount (never captured)
     * @param inTag         in {@code #nerocreatures:capturable}
     * @param width         hitbox width
     * @param height        hitbox height
     * @param attached      riding, ridden or leashed
     * @param tamed         tamed by somebody
     * @param ownerIsUser   the entity's own owner is the user
     * @param storeAgrees   the ownership store does not contradict that (no erasure record)
     */
    public record Candidate(boolean alive, boolean display, boolean hostile, boolean boss, boolean alpha,
            boolean mount, boolean inTag, float width, float height, boolean attached, boolean tamed,
            boolean ownerIsUser, boolean storeAgrees) {
    }

    private CaptureRules() {
    }

    /**
     * Whether a capture must be refused (with a message) because the game handed the pod a creative-mode
     * copy of the held stack: filling the copy would leave the real pod empty and the creature gone.
     */
    public static boolean creativeCopy(boolean creative, boolean sameStackInHand) {
        return creative && !sameStackInHand;
    }

    /** The capture verdict for one candidate. */
    public static Verdict mayCapture(Candidate c) {
        if (!c.alive()) {
            return Verdict.DEAD;
        }
        if (c.display()) {
            return Verdict.DISPLAY;
        }
        if (c.boss()) {
            return Verdict.BOSS;
        }
        if (c.hostile()) {
            return Verdict.HOSTILE;
        }
        if (c.alpha()) {
            return Verdict.ALPHA;
        }
        if (c.mount()) {
            return Verdict.MOUNT;
        }
        if (!c.inTag()) {
            return Verdict.NOT_CAPTURABLE;
        }
        if (c.width() > MAX_WIDTH || c.height() > MAX_HEIGHT) {
            return Verdict.TOO_BIG;
        }
        if (c.attached()) {
            return Verdict.ATTACHED;
        }
        if (c.tamed() && !(c.ownerIsUser() && c.storeAgrees())) {
            return Verdict.NOT_YOURS;
        }
        return Verdict.OK;
    }

    /** Why a release was refused, or {@link #OK}. */
    public enum Release {
        OK("ok"),
        EMPTY("empty"),
        UNKNOWN("unknown"),
        NOT_ALLOWED("not_allowed"),
        UNLOADED("not_allowed"),
        BLOCKED("blocked"),
        PET_CAP("pet_cap");

        private final String key;

        Release(String key) {
            this.key = key;
        }

        /** The lang suffix for the refusal message. */
        public String key() {
            return this.key;
        }
    }

    /**
     * The release verdict.
     *
     * @param filled      the pod holds a capture
     * @param understood  this reader knows the capture's schema and species
     * @param mayUse      the player may use items at the spot (claims, spawn protection)
     * @param loaded      the spot is loaded and inside the world border
     * @param roomFree    the creature's hitbox fits there (never into a wall)
     * @param wasTamed    the creature was tamed when captured
     * @param ownedCount  the releaser's current count of that ownership kind
     * @param cap         the releaser's cap for that kind
     */
    public static Release mayRelease(boolean filled, boolean understood, boolean mayUse, boolean loaded,
            boolean roomFree, boolean wasTamed, int ownedCount, int cap) {
        if (!filled) {
            return Release.EMPTY;
        }
        if (!understood) {
            return Release.UNKNOWN;
        }
        if (!mayUse) {
            return Release.NOT_ALLOWED;
        }
        if (!loaded) {
            return Release.UNLOADED;
        }
        if (!roomFree) {
            return Release.BLOCKED;
        }
        if (wasTamed && (cap <= 0 || ownedCount >= cap)) {
            return Release.PET_CAP;
        }
        return Release.OK;
    }
}
