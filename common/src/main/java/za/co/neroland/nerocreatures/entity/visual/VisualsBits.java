package za.co.neroland.nerocreatures.entity.visual;

/**
 * Bit layout of the two synced ints behind {@link CreatureVisuals}. Pure int math, unit tested.
 *
 * <pre>
 * state int (changes rarely)            timer int (changes every tick during a timed pose)
 *  bits  0-1  pose     0-3               bits  0-15  elapsed ticks in the current pose
 *  bits  2-3  phase    0-3 (bosses)      bits 16-31  duration ticks (0 = held, untimed)
 *  bits  4-5  stage    0-3
 *  bits  6-8  variant  0-7
 *  bits  9-11 form     0-7
 *  bit  12    radiant
 *  bit  13    alpha
 *  bit  14    saddled
 *  bit  15    panniers
 *  bits 16-31 reserved (zero)
 * </pre>
 *
 * Every setter clamps its value into range, so a bad caller can never corrupt a neighbouring field.
 */
public final class VisualsBits {

    public static final int MAX_PHASE = 3;
    public static final int MAX_STAGE = 3;
    public static final int MAX_VARIANT = 7;
    public static final int MAX_FORM = 7;
    public static final int MAX_TICKS = 0xFFFF;

    public static final int FLAG_RADIANT = 1 << 12;
    public static final int FLAG_ALPHA = 1 << 13;
    public static final int FLAG_SADDLED = 1 << 14;
    public static final int FLAG_PANNIERS = 1 << 15;

    /** The bits saved with the entity: stage, variant, form and the four flags (not pose/phase). */
    public static final int PERSISTENT_MASK = (0x3 << 4) | (0x7 << 6) | (0x7 << 9)
            | FLAG_RADIANT | FLAG_ALPHA | FLAG_SADDLED | FLAG_PANNIERS;

    private VisualsBits() {
    }

    // --- state int ---------------------------------------------------------------

    public static int pose(int state) {
        return state & 0x3;
    }

    public static int withPose(int state, int pose) {
        return (state & ~0x3) | clamp(pose, 0, 3);
    }

    public static int phase(int state) {
        return (state >>> 2) & 0x3;
    }

    public static int withPhase(int state, int phase) {
        return (state & ~(0x3 << 2)) | (clamp(phase, 0, MAX_PHASE) << 2);
    }

    public static int stage(int state) {
        return (state >>> 4) & 0x3;
    }

    public static int withStage(int state, int stage) {
        return (state & ~(0x3 << 4)) | (clamp(stage, 0, MAX_STAGE) << 4);
    }

    public static int variant(int state) {
        return (state >>> 6) & 0x7;
    }

    public static int withVariant(int state, int variant) {
        return (state & ~(0x7 << 6)) | (clamp(variant, 0, MAX_VARIANT) << 6);
    }

    public static int form(int state) {
        return (state >>> 9) & 0x7;
    }

    public static int withForm(int state, int form) {
        return (state & ~(0x7 << 9)) | (clamp(form, 0, MAX_FORM) << 9);
    }

    public static boolean flag(int state, int flag) {
        return (state & flag) != 0;
    }

    public static int withFlag(int state, int flag, boolean on) {
        return on ? state | flag : state & ~flag;
    }

    /** Only the persistent fields of {@code state}. */
    public static int persistent(int state) {
        return state & PERSISTENT_MASK;
    }

    /** {@code current} with its persistent fields replaced by those in {@code saved}. */
    public static int restorePersistent(int current, int saved) {
        return (current & ~PERSISTENT_MASK) | (saved & PERSISTENT_MASK);
    }

    // --- timer int ---------------------------------------------------------------

    public static int elapsed(int timer) {
        return timer & MAX_TICKS;
    }

    public static int duration(int timer) {
        return (timer >>> 16) & MAX_TICKS;
    }

    public static int timer(int elapsed, int duration) {
        return (clamp(duration, 0, MAX_TICKS) << 16) | clamp(elapsed, 0, MAX_TICKS);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }
}
