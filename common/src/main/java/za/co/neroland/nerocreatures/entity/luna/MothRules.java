package za.co.neroland.nerocreatures.entity.luna;

/**
 * Wild Lumen Moth behaviour rules: gathering at light, and the overworld day despawn (decision
 * 005). Pure and unit-tested.
 */
public final class MothRules {

    /** Block light at or above which a sampled spot attracts wild moths. */
    public static final int MIN_ATTRACTING_LIGHT = 10;
    /** Random spots sampled per gather search (bounded work). */
    public static final int GATHER_SAMPLES = 8;
    /** Horizontal sample radius, blocks. */
    public static final int GATHER_RADIUS = 12;
    /** Ticks between gather searches. */
    public static final int GATHER_INTERVAL = 100;
    /** Ticks a moth flutters around one light before it looks again. */
    public static final int GATHER_HOVER_TICKS = 200;
    /** No player within this many blocks is required for the day despawn. */
    public static final double DAY_DESPAWN_PLAYER_RADIUS = 32.0D;

    private MothRules() {
    }

    /** Index of the brightest sample at or above {@code threshold}; ties keep the first; -1 if none. */
    public static int bestLightIndex(int[] blockLight, int threshold) {
        int best = -1;
        int bestLevel = Integer.MIN_VALUE;
        for (int i = 0; i < blockLight.length; i++) {
            if (blockLight[i] >= threshold && blockLight[i] > bestLevel) {
                best = i;
                bestLevel = blockLight[i];
            }
        }
        return best;
    }

    /**
     * Decision 005: a wild moth in the overworld that is not tamed, leashed or persistent despawns
     * in daylight when no player is within {@link #DAY_DESPAWN_PLAYER_RADIUS} blocks.
     */
    public static boolean despawnAtDay(boolean tame, boolean leashed, boolean persistent, boolean overworld,
            boolean daylight, boolean playerNear) {
        return !tame && !leashed && !persistent && overworld && daylight && !playerNear;
    }
}
