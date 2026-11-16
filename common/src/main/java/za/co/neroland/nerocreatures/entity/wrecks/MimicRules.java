package za.co.neroland.nerocreatures.entity.wrecks;

/**
 * Pure rules for the {@link CacheMimic}: when it wakes, when it may bite, when it goes back to sleep,
 * and how a dormant crate sits on the block grid (exactly one block, grid-aligned, yaw
 * snapped to 90 degrees). No Minecraft types, so every rule is unit-tested.
 */
public final class MimicRules {

    /** {@code form} while dormant: the crate. */
    public static final int FORM_DORMANT = 0;
    /** {@code form} while awake: split lid, crab legs, tongue. */
    public static final int FORM_AWAKE = 1;

    /** {@code variant} 0: the grey salvage crate (the default dormant skin). */
    public static final int SKIN_SALVAGE = 0;
    /** {@code variant} 1: the same crate in worn white plating. */
    public static final int SKIN_WORN = 1;
    /** How many dormant skins exist. */
    public static final int SKINS = 2;

    /** The reveal ({@code ability} pose): lid flies open, legs unfold. No bite before it ends. */
    public static final int REVEAL_TICKS = 20;
    /** Awake with no target for this long (30 s): it settles back into a crate. */
    public static final int SLEEP_AFTER_TICKS = 600;
    /** How often a dormant mimic re-asserts its grid position and yaw. */
    public static final int SETTLE_INTERVAL_TICKS = 20;

    /** Natural spawn cap: at most this many mimics within {@link #AREA_RADIUS}. */
    public static final int MAX_PER_AREA = 1;
    /** Radius (blocks) of the natural spawn cap. */
    public static final double AREA_RADIUS = 48.0D;

    private MimicRules() {
    }

    /** A dormant mimic wakes on any interaction or any damage; an awake one stays awake. */
    public static boolean wakes(boolean dormant, boolean interactedOrHurt) {
        return dormant && interactedOrHurt;
    }

    /** It may attack only when awake and the reveal telegraph has fully played. */
    public static boolean mayAttack(boolean dormant, int ticksSinceReveal) {
        return !dormant && ticksSinceReveal >= REVEAL_TICKS;
    }

    /** Back to sleep after {@link #SLEEP_AFTER_TICKS} awake without a target. */
    public static boolean sleeps(boolean dormant, int ticksWithoutTarget) {
        return !dormant && ticksWithoutTarget >= SLEEP_AFTER_TICKS;
    }

    /** Yaw snapped to the nearest multiple of 90 degrees, in {@code [0, 360)}. */
    public static float snapYaw(float yaw) {
        float wrapped = ((yaw % 360.0F) + 360.0F) % 360.0F;
        float snapped = Math.round(wrapped / 90.0F) * 90.0F;
        return snapped >= 360.0F ? 0.0F : snapped;
    }

    /** The centre of the block column a coordinate falls in. */
    public static double blockCentre(double coordinate) {
        return Math.floor(coordinate) + 0.5D;
    }

    /** A dormant skin index from a roll, clamped into range. */
    public static int skin(int roll) {
        return Math.floorMod(roll, SKINS);
    }

    /** The natural spawn rule's level gate: the config key on and a space level. */
    public static boolean maySpawn(boolean keyOn, boolean spaceLevel) {
        return keyOn && spaceLevel;
    }

    /** The natural spawn rule's position gate: no other mimic within {@link #AREA_RADIUS}. */
    public static boolean roomInArea(int mimicsNear) {
        return mimicsNear < MAX_PER_AREA;
    }

    /**
     * Whether the loot table pays out: every pool of
     * {@code entities/cache_mimic} carries {@code minecraft:killed_by_player}, so a mimic killed by
     * magma, a trap or another mob drops nothing and cannot be farmed with nobody present. This
     * mirrors the loot condition for the tests.
     */
    public static boolean dropsLoot(boolean killedByPlayer) {
        return killedByPlayer;
    }
}
