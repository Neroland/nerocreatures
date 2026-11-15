package za.co.neroland.nerocreatures.entity.ai.shared;

/**
 * The pure rules behind sound hunting (Echo Shrike and anything else that is blind).
 *
 * <p>A noise has a <em>loudness</em> from 0 (silent) to {@link #MAX_LOUDNESS}. A listener with
 * hearing radius {@code r} hears a noise of loudness {@code l} out to {@code r * l / MAX_LOUDNESS}
 * blocks. The rules the build prompt fixes:
 *
 * <ul>
 *   <li>sprinting steps, mining (block destroy) and landing draw a sound hunter;</li>
 *   <li>walking steps are quiet (they do not carry);</li>
 *   <li>a sneaking source is silent, whatever it does;</li>
 *   <li>a silenced source (gear such as a cloak, through {@link SoundHearing#addSilencer}) is
 *       silent.</li>
 * </ul>
 *
 * <p>A louder or fresher noise replaces the remembered one; a remembered noise is forgotten after
 * the listener's memory window. No game types here, so the rule is unit-tested.
 */
public final class SoundHuntRules {

    /** Loudest possible noise; carries the full hearing radius. */
    public static final int MAX_LOUDNESS = 3;

    /** The kinds of vanilla game event a sound hunter listens to. */
    public enum Noise {
        /** {@code minecraft:step}: loud only while sprinting. */
        STEP,
        /** {@code minecraft:hit_ground}: landing from a fall or jump. */
        LAND,
        /** {@code minecraft:block_destroy}: mining. */
        MINE,
        /** Projectile impacts and other configured extras. */
        OTHER
    }

    private SoundHuntRules() {
    }

    /**
     * Loudness of one noise.
     *
     * @param noise     what happened
     * @param sprinting whether the source was sprinting
     * @param sneaking  whether the source was sneaking (stepping carefully)
     * @param silenced  whether gear or an effect silences the source
     */
    public static int loudness(Noise noise, boolean sprinting, boolean sneaking, boolean silenced) {
        if (sneaking || silenced) {
            return 0;
        }
        return switch (noise) {
            case STEP -> sprinting ? 2 : 0;
            case LAND, MINE -> MAX_LOUDNESS;
            case OTHER -> 1;
        };
    }

    /** Whether a noise of {@code loudness} at squared distance {@code distSqr} is heard. */
    public static boolean heard(int loudness, double distSqr, int hearingRadius) {
        if (loudness <= 0 || hearingRadius <= 0) {
            return false;
        }
        double reach = hearingRadius * (double) Math.min(loudness, MAX_LOUDNESS) / MAX_LOUDNESS;
        return distSqr <= reach * reach;
    }

    /** Whether a remembered noise heard at {@code heardAt} is stale at {@code now}. */
    public static boolean expired(long heardAt, long now, int memoryTicks) {
        return heardAt == Long.MIN_VALUE || now - heardAt > memoryTicks;
    }

    /**
     * Whether a new noise should replace the remembered one: always when the remembered one is
     * stale, otherwise only when the new one is at least as loud.
     */
    public static boolean replaces(int newLoudness, int currentLoudness, long currentHeardAt, long now,
            int memoryTicks) {
        if (newLoudness <= 0) {
            return false;
        }
        return expired(currentHeardAt, now, memoryTicks) || newLoudness >= currentLoudness;
    }
}
