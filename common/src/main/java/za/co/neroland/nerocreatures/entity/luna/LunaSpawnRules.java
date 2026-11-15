package za.co.neroland.nerocreatures.entity.luna;

/**
 * The pure halves of the Luna creatures' overworld spawn gates (decision 005). The world-reading
 * halves are in {@link LunaSpawnGates}. Unit-tested.
 */
public final class LunaSpawnRules {

    /** Chance per spawn attempt that the overworld moth rule fires. */
    public static final double MOTH_OVERWORLD_CHANCE = 0.15D;
    /** Chance per spawn attempt that the deep-cave tortoise rule fires. */
    public static final double TORTOISE_CAVE_CHANCE = 0.05D;
    /** The moth rule needs block light at or below this (dark forests, not lit bases). */
    public static final int MOTH_MAX_BLOCK_LIGHT = 7;
    /** Highest Y a cave tortoise spawns at. */
    public static final int TORTOISE_MAX_Y = 0;
    /** Cave candidates are drawn at least this far above the world floor. */
    public static final int CAVE_FLOOR_MARGIN = 8;
    /** Blocks scanned (down) from a random Y for a cave floor. */
    public static final int CAVE_SCAN_BLOCKS = 12;

    private LunaSpawnRules() {
    }

    /** A uniform roll in {@code [0, 1)} passes a chance gate. */
    public static boolean roll(double uniform, double chance) {
        return uniform < chance;
    }

    /** Decision 005 moth gate, after the dimension and biome checks. */
    public static boolean mothSpot(boolean dark, boolean air, int blockLight) {
        return dark && air && blockLight <= MOTH_MAX_BLOCK_LIGHT;
    }

    /** Decision 005 tortoise gate: deep, sealed from the sky, standing on base stone. */
    public static boolean tortoiseSpot(int y, boolean canSeeSky, int skyLight, boolean stoneBelow) {
        return y <= TORTOISE_MAX_Y && !canSeeSky && skyLight == 0 && stoneBelow;
    }

    /** A floor: solid below, and two air blocks for the tortoise to stand in. */
    public static boolean caveFloor(boolean solidBelow, boolean airAt, boolean airAbove) {
        return solidBelow && airAt && airAbove;
    }

    /** The Y band a cave candidate is drawn from, as {@code {low, high}}; empty band gives low > high. */
    public static int[] caveBand(int minY) {
        return new int[] {minY + CAVE_FLOOR_MARGIN, TORTOISE_MAX_Y};
    }
}
