package za.co.neroland.nerocreatures.entity.base;

/**
 * The coarse power band a NeroCreature belongs to. A tier is the single knob that sets a
 * creature's baseline attributes, its experience drop and how expensive it is allowed to be per
 * tick, so a new creature only has to pick a band instead of hand-tuning five numbers.
 *
 * <p>Values are deliberately close to vanilla anchors so a player's existing intuition transfers:
 * {@link #LESSER} sits near a zombie, {@link #GREATER} near a ravager, {@link #APEX} is the boss
 * band and is never used for a natural, non-boss spawn.
 */
public enum CreatureTier {

    /** Chaff. Cheap AI, small groups, dies to an iron sword. */
    LESSER(20.0D, 3.0D, 0.25D, 24.0D, 5),

    /** The standard hostile band: a real fight for a mid-game player, still spawns in numbers. */
    COMMON(30.0D, 5.0D, 0.28D, 32.0D, 10),

    /** Rare elites — armoured, slower, worth a detour. */
    GREATER(60.0D, 8.0D, 0.24D, 40.0D, 20),

    /** Bosses only. Never assign this to a naturally spawning roster entry. */
    APEX(300.0D, 14.0D, 0.30D, 64.0D, 100);

    private final double baseHealth;
    private final double baseAttackDamage;
    private final double baseMovementSpeed;
    private final double baseFollowRange;
    private final int experienceReward;

    CreatureTier(double baseHealth, double baseAttackDamage, double baseMovementSpeed,
            double baseFollowRange, int experienceReward) {
        this.baseHealth = baseHealth;
        this.baseAttackDamage = baseAttackDamage;
        this.baseMovementSpeed = baseMovementSpeed;
        this.baseFollowRange = baseFollowRange;
        this.experienceReward = experienceReward;
    }

    public double baseHealth() {
        return this.baseHealth;
    }

    public double baseAttackDamage() {
        return this.baseAttackDamage;
    }

    public double baseMovementSpeed() {
        return this.baseMovementSpeed;
    }

    public double baseFollowRange() {
        return this.baseFollowRange;
    }

    public int experienceReward() {
        return this.experienceReward;
    }
}
