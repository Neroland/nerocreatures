package za.co.neroland.nerocreatures.entity.greenxertz;

/**
 * The Mossback's grazing rules as pure logic (decision 006, key {@code mossbackFertilises},
 * {@code Change.PLACE}).
 *
 * <p>Every {@link #GRAZE_INTERVAL} ticks an adult standing on a grass block grazes (the
 * {@code ability} pose, head down sweeping). At the pose's tick {@link #FERTILISE_AT_TICK} the
 * grass it grazes is bone-mealed (vanilla {@code BoneMealItem.growCrop}: grass and flowers sprout
 * around it; grass is never turned to dirt), at most once per {@link #FERTILISE_COOLDOWN} ticks.
 * Denied, or on cooldown: it grazes with no effect.
 */
public final class MossbackRules {

    /** 15 s between grazes. */
    public static final int GRAZE_INTERVAL = 300;
    public static final int GRAZE_POSE_TICKS = 60;
    public static final int FERTILISE_AT_TICK = 30;
    /** 60 s between fertilising grazes, per Mossback. */
    public static final int FERTILISE_COOLDOWN = 1200;

    public enum Graze {
        /** Not on grass, or a baby: nothing. */
        NONE,
        /** The pose plays; nothing changes in the world. */
        GRAZE_ONLY,
        /** The pose plays and the grass is bone-mealed. */
        FERTILISE
    }

    private MossbackRules() {
    }

    /** Whether a graze starts at all. */
    public static boolean mayGraze(boolean adult, boolean onGrass) {
        return adult && onGrass;
    }

    /** What the graze's fertilise tick does; {@code allowed} is the {@code mayAlter} answer. */
    public static Graze fertilise(boolean adult, boolean onGrass, long now, long cooldownUntil, boolean allowed) {
        if (!mayGraze(adult, onGrass)) {
            return Graze.NONE;
        }
        return now >= cooldownUntil && allowed ? Graze.FERTILISE : Graze.GRAZE_ONLY;
    }
}
