package za.co.neroland.nerocreatures.spawn;

/**
 * The pure halves of overworld spawning: the alive cap and the per-attempt chance.
 * Unit-tested ({@code OverworldSpawnRulesTest}) over many simulated attempts.
 */
public final class OverworldSpawnRules {

    private OverworldSpawnRules() {
    }

    /** Room for one more under {@code cap}; a cap of 0 (or less) means none at all. */
    public static boolean hasRoom(int alive, int cap) {
        return cap > 0 && alive < cap;
    }

    /** A uniform roll in {@code [0, 1)} passes a per-attempt chance gate. */
    public static boolean roll(double uniform, double chance) {
        return uniform < chance;
    }
}
