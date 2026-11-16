package za.co.neroland.nerocreatures.entity.boss;

/**
 * Pure rules for the Bloom Sovereign fight (unit-tested; no Minecraft types).
 *
 * <ul>
 *   <li><b>Rooted.</b> She never moves; the fight is about the pods around her and her vine.</li>
 *   <li><b>Spore pods</b> ({@code bloom_host} in pod form) heal her while they live.
 *       They are entities, never blocks, hard-capped, and destroyable.</li>
 *   <li><b>Infect</b> ({@code ability}, {@value #INFECT_CLIP_TICKS} ticks, the hit at tick
 *       {@value #INFECT_HIT_TICK}): a vine lash that poisons the target and raises a sporeling (her
 *       add) at the target's feet, under her own cap and the shared sporeling dimension cap.</li>
 *   <li><b>Bark</b>: she only takes damage from an attacker her vine can answer, one within
 *       {@value #BARK_REACH} blocks that she can see. Shots from beyond the vine's reach, or blows
 *       struck from under an overhang where she has no line of sight, glance off her bark. So the
 *       fight is always fought in the open, inside her reach.</li>
 *   <li><b>Heal</b> ({@code ability_2}, {@value #HEAL_CLIP_TICKS} ticks): pods pulse and she heals by
 *       how many pods are alive when the clip ends. Kill the pods mid-channel and the heal is lost.</li>
 * </ul>
 */
public final class SovereignRules {

    private SovereignRules() {
    }

    public static final float PHASE_2 = 0.66F;
    public static final float PHASE_3 = 0.33F;

    /** Arena radius around the summon point, in blocks. */
    public static final double ARENA_RADIUS = 20.0D;

    // --- infect -----------------------------------------------------------------------------

    public static final int INFECT_CLIP_TICKS = 30;
    public static final int INFECT_HIT_TICK = 14;

    /** Vine reach, in blocks. */
    public static final double INFECT_RANGE = 14.0D;

    /** Poison applied by the lash, in ticks. */
    public static final int INFECT_POISON_TICKS = 100;

    public static int infectCooldown(int phase) {
        return switch (MatriarchRules.clampPhase(phase)) {
            case 1 -> 120;
            case 2 -> 90;
            default -> 60;
        };
    }

    public static float infectDamage(int phase) {
        return switch (MatriarchRules.clampPhase(phase)) {
            case 1 -> 5.0F;
            case 2 -> 6.0F;
            default -> 8.0F;
        };
    }

    /** Farthest attacker whose damage lands: the lash's own hit reach. */
    public static final double BARK_REACH = INFECT_RANGE + 2.0D;

    /** Ticks between two "glanced off the bark" cues, so a volley of arrows plays one sound. */
    public static final int BARK_CUE_TICKS = 10;

    /**
     * Whether damage from an attacker lands on her: only from an attacker she can answer with the lash,
     * one within {@link #BARK_REACH} that she can see.
     */
    public static boolean barkLetsThrough(double attackerDistance, boolean lineOfSight) {
        return lineOfSight && attackerDistance <= BARK_REACH;
    }

    // --- adds -------------------------------------------------------------------------------

    /** Hard cap on her live sporelings, every phase. */
    public static final int MAX_SPORELINGS = 6;

    /** Live sporelings she keeps at most in a phase. */
    public static int sporelingCap(int phase) {
        return Math.min(MAX_SPORELINGS, 2 * MatriarchRules.clampPhase(phase));
    }

    /** Whether an infect may raise one more sporeling. */
    public static boolean mayRaiseSporeling(int phase, int ownLive, boolean sporelingsEnabled) {
        return sporelingsEnabled && ownLive < sporelingCap(phase);
    }

    // --- pods -------------------------------------------------------------------------------

    /** Hard cap on live pods, every phase. */
    public static final int MAX_PODS = 4;

    /** Pod health (they are meant to be killed). */
    public static final double POD_HEALTH = 24.0D;

    /** Pods sprout this far from her roots. */
    public static final double POD_RING = 5.0D;

    /** Ticks between pod regrowth. */
    public static int podInterval(int phase) {
        return switch (MatriarchRules.clampPhase(phase)) {
            case 1 -> 400;
            case 2 -> 300;
            default -> 240;
        };
    }

    /** Pods she keeps in a phase. */
    public static int podCap(int phase) {
        return Math.min(MAX_PODS, 1 + MatriarchRules.clampPhase(phase));
    }

    /** How many pods to grow now. */
    public static int podsToGrow(int phase, int live) {
        return Math.max(0, podCap(phase) - Math.max(0, live));
    }

    // --- heal -------------------------------------------------------------------------------

    public static final int HEAL_CLIP_TICKS = 60;

    /** Ticks between heal channels. */
    public static final int HEAL_COOLDOWN = 200;

    /** Fraction of max health healed per live pod at the end of a channel. */
    public static final float HEAL_PER_POD = 0.025F;

    /** Health restored by one channel; 0 without pods. Never above what is missing. */
    public static float healAmount(float maxHealth, float health, int livePods) {
        int pods = Math.max(0, Math.min(MAX_PODS, livePods));
        float missing = Math.max(0.0F, maxHealth - health);
        return Math.min(missing, maxHealth * HEAL_PER_POD * pods);
    }

    /** Whether a heal channel is worth starting. */
    public static boolean shouldHeal(float maxHealth, float health, int livePods) {
        return livePods > 0 && health < maxHealth;
    }
}
