package za.co.neroland.nerocreatures.item.gear;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The pure rules of every gear item: cooldown defaults, bounds and the small decisions, tested
 * without a world. All numbers are modest on purpose; config keys tune the cooldowns and ranges
 * within the clamps here.
 */
public final class GearRules {

    // --- defaults (ticks unless noted) ---------------------------------------------------------

    public static final int MIRROR_SHIELD_COOLDOWN = 200;
    /** Projectiles closer than this to the holder are checked, in blocks. */
    public static final double REFLECT_RADIUS = 3.0D;
    /** A reflected projectile keeps this share of its speed. */
    public static final double REFLECT_SPEED = 0.8D;
    /** Slower projectiles (stuck arrows, drifting orbs) are not reflected. */
    public static final double MIN_PROJECTILE_SPEED = 0.05D;

    public static final int BLINK_COOLDOWN = 100;
    public static final int BLINK_RANGE = 8;
    public static final int BLINK_RANGE_MIN = 2;
    public static final int BLINK_RANGE_MAX = 16;
    /** A blink shorter than this is pointless and refused (no cooldown spent). */
    public static final double BLINK_MIN_DISTANCE = 1.5D;
    /** Distance kept from the surface the ray hit, in blocks. */
    public static final double BLINK_WALL_MARGIN = 0.6D;
    public static final double BLINK_STEP = 0.5D;

    public static final int RIME_FLASK_COOLDOWN = 1200;
    public static final int RIME_FLASK_FIRE_RESIST_TICKS = 200;

    public static final int GREAVES_LAVA_TICKS = 100;
    public static final int GREAVES_LAVA_TICKS_MAX = 400;
    public static final int GREAVES_COOLDOWN = 600;
    /** Upward speed that holds the wearer at the lava surface. */
    public static final double GREAVES_LIFT = 0.12D;

    public static final int BOOTS_GRAVITY_PERCENT = 50;
    public static final int BOOTS_GRAVITY_PERCENT_MIN = 20;
    public static final int BOOTS_GRAVITY_PERCENT_MAX = 90;
    /** Extra blocks of safe fall while the boots are active. */
    public static final double BOOTS_SAFE_FALL = 4.0D;

    public static final int WHISTLE_COOLDOWN = 600;
    public static final int WHISTLE_RANGE = 48;
    public static final int WHISTLE_RANGE_MIN = 8;
    public static final int WHISTLE_RANGE_MAX = 128;
    /** Most creatures one whistle brings. */
    public static final int WHISTLE_MAX_CALLED = 8;

    public static final int BULWARK_COOLDOWN = 1800;
    public static final double BULWARK_KNOCKBACK_RESISTANCE = 0.4D;
    /** Health share at or below which the bulwark braces. */
    public static final float BULWARK_THRESHOLD = 0.4F;
    public static final int BULWARK_ABSORPTION_TICKS = 200;

    public static final int AMULET_COOLDOWN = 900;
    /** Health share at or below which the amulet blooms. */
    public static final float AMULET_THRESHOLD = 0.6F;
    public static final int AMULET_REGEN_TICKS = 100;

    /** Every cooldown is at least one second, so no key can turn gear into a per-tick effect. */
    public static final int MIN_COOLDOWN = 20;

    private GearRules() {
    }

    /** A configured cooldown, never below {@link #MIN_COOLDOWN}. */
    public static int cooldown(int configured) {
        return Math.max(MIN_COOLDOWN, configured);
    }

    /** {@code value} clamped to {@code [min, max]}. */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // --- Mirror Shield ------------------------------------------------------------------------

    /**
     * Whether a projectile with velocity {@code v} is coming at the holder, {@code to} being the
     * vector from the projectile to the holder: fast enough and moving towards them.
     */
    public static boolean isIncoming(double vx, double vy, double vz, double tx, double ty, double tz) {
        double speedSqr = vx * vx + vy * vy + vz * vz;
        if (speedSqr < MIN_PROJECTILE_SPEED * MIN_PROJECTILE_SPEED) {
            return false;
        }
        return vx * tx + vy * ty + vz * tz > 0.0D;
    }

    /** Whether the shield may reflect now. Never a projectile its holder fired. */
    public static boolean mayReflect(boolean held, boolean onCooldown, boolean ownProjectile, boolean incoming) {
        return held && !onCooldown && !ownProjectile && incoming;
    }

    /**
     * Whether the shield turns this kind of projectile at all: never a trident (a loyalty trident
     * would fly home to the shield holder) or a thrown ender pearl (it would move its thrower).
     */
    public static boolean reflectsType(String entityTypeId) {
        return !"minecraft:trident".equals(entityTypeId) && !"minecraft:ender_pearl".equals(entityTypeId);
    }

    /**
     * Whether a reflected projectile becomes the shield holder's. Only a projectile no player owns
     * (a creature's or a dispenser's); another player's shot is turned back but stays theirs.
     */
    public static boolean reflectTakesOwnership(boolean ownedByPlayer) {
        return !ownedByPlayer;
    }

    // --- Lurker Cloak -------------------------------------------------------------------------

    /** Sound hunters ignore a cloaked wearer unless they sprint (sneaking is always silent). */
    public static boolean cloakSilences(boolean enabled, boolean worn, boolean sprinting) {
        return enabled && worn && !sprinting;
    }

    // --- Blink Charm --------------------------------------------------------------------------

    /**
     * The blink distances to try, longest first: from just short of what the ray hit (or the range)
     * down to {@link #BLINK_MIN_DISTANCE}. Empty when there is no room for a blink.
     */
    public static List<Double> blinkSteps(double hitDistance, int configuredRange) {
        int range = clamp(configuredRange, BLINK_RANGE_MIN, BLINK_RANGE_MAX);
        double start = Math.min(range, hitDistance - BLINK_WALL_MARGIN);
        List<Double> steps = new ArrayList<>();
        for (double d = start; d >= BLINK_MIN_DISTANCE - 1.0E-9; d -= BLINK_STEP) {
            steps.add(d);
        }
        return steps;
    }

    /** A candidate spot is allowed only when loaded, inside the border and free of blocks. */
    public static boolean blinkSpotAllowed(boolean loaded, boolean insideBorder, boolean noCollision) {
        return loaded && insideBorder && noCollision;
    }

    // --- Heat-gland Greaves -------------------------------------------------------------------

    /** Charge after one tick: drains in lava, refills fully out of lava once off cooldown. */
    public static int nextCharge(int charge, boolean inLava, boolean onCooldown, int configuredMax) {
        int max = clamp(configuredMax, 1, GREAVES_LAVA_TICKS_MAX);
        if (inLava) {
            return Math.max(0, Math.min(charge, max) - 1);
        }
        return onCooldown ? Math.min(charge, max) : max;
    }

    /** Whether the greaves hold the wearer up this tick. */
    public static boolean greavesSupport(boolean worn, boolean inLava, int charge) {
        return worn && inLava && charge > 0;
    }

    /** Whether this tick spent the last charge (starts the recharge cooldown). */
    public static boolean greavesExhausted(int before, int after) {
        return before > 0 && after == 0;
    }

    // --- Graviton Boots -----------------------------------------------------------------------

    /** The boots act while worn and not sneaking (sneak = fall normally). */
    public static boolean bootsActive(boolean worn, boolean sneaking, boolean flying) {
        return worn && !sneaking && !flying;
    }

    /** The multiply-total gravity modifier: gravity becomes {@code percent}% of normal. */
    public static double gravityModifier(int configuredPercent) {
        int percent = clamp(configuredPercent, BOOTS_GRAVITY_PERCENT_MIN, BOOTS_GRAVITY_PERCENT_MAX);
        return percent / 100.0D - 1.0D;
    }

    // --- Resonant Whistle ---------------------------------------------------------------------

    /**
     * One creature the whistle might call.
     *
     * @param index        caller's index for the result
     * @param sameLevel    in the whistler's own dimension (never cross-dimension)
     * @param ownedByUser  the creature's owner is the whistler
     * @param distanceSqr  squared distance to the whistler
     * @param busy         sitting, ridden or riding: left where it is
     */
    public record WhistleCandidate(int index, boolean sameLevel, boolean ownedByUser, double distanceSqr,
            boolean busy) {
    }

    /** The creatures to call, nearest first, at most {@link #WHISTLE_MAX_CALLED}, within range. */
    public static List<Integer> whistlePicks(List<WhistleCandidate> candidates, int configuredRange) {
        int range = clamp(configuredRange, WHISTLE_RANGE_MIN, WHISTLE_RANGE_MAX);
        double rangeSqr = (double) range * range;
        List<WhistleCandidate> eligible = new ArrayList<>();
        for (WhistleCandidate c : candidates) {
            if (c.sameLevel() && c.ownedByUser() && !c.busy() && c.distanceSqr() <= rangeSqr) {
                eligible.add(c);
            }
        }
        eligible.sort(Comparator.comparingDouble(WhistleCandidate::distanceSqr));
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < eligible.size() && i < WHISTLE_MAX_CALLED; i++) {
            picks.add(eligible.get(i).index());
        }
        return picks;
    }

    // --- Chitin Bulwark and Bloom Amulet ------------------------------------------------------

    /** The bulwark braces at or below {@link #BULWARK_THRESHOLD} health, once per cooldown. */
    public static boolean bulwarkBraces(boolean held, float health, float maxHealth, boolean onCooldown) {
        return held && !onCooldown && maxHealth > 0.0F && health > 0.0F && health / maxHealth <= BULWARK_THRESHOLD;
    }

    /** The amulet blooms at or below {@link #AMULET_THRESHOLD} health, once per cooldown, never stacking. */
    public static boolean amuletBlooms(boolean held, float health, float maxHealth, boolean onCooldown,
            boolean alreadyRegenerating) {
        return held && !onCooldown && !alreadyRegenerating && maxHealth > 0.0F && health > 0.0F
                && health / maxHealth <= AMULET_THRESHOLD;
    }
}
