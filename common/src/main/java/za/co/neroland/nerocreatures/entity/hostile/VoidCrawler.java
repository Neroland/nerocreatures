package za.co.neroland.nerocreatures.entity.hostile;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.entity.ai.BlinkStrikeGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * <b>Void Crawler</b> — the low-light ambusher of the dark planets.
 *
 * <p>Its whole identity is one move: it does not chase you down, it <em>arrives</em>. A
 * {@link BlinkStrikeGoal} closes the gap in a single teleport, so the fight is decided by whether
 * you spotted it before it decided to come. Between blinks it is an ordinary, slightly fast melee
 * mob — the pressure comes from the approach, not from raw stats.
 *
 * <p>Spawns in Core's {@code neroland:space/dark_biomes}; with no planet mod installed that tag is
 * empty and the crawler simply never appears. Drops {@code void_essence}.
 *
 * <p>Server-authoritative: all AI, targeting and blinking happen on the server, and nothing about a
 * player is stored on the entity (POPIA/GDPR).
 */
public class VoidCrawler extends NeroCreatureEntity implements Enemy {

    /** Blink no closer than this (blocks) — a blink onto a target you can already hit is wasted. */
    private static final double BLINK_MIN_DISTANCE = 5.0D;

    /** Blink no further than this (blocks). Beyond it the crawler walks like anything else. */
    private static final double BLINK_MAX_DISTANCE = 20.0D;

    /** Ticks between blinks. Five seconds: readable, and the cap that keeps a group affordable. */
    private static final int BLINK_COOLDOWN_TICKS = 100;

    public VoidCrawler(EntityType<? extends VoidCrawler> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    /**
     * A standard {@link CreatureTier#COMMON} block, re-pointed at "fast and fragile": less health
     * than the band default, more speed. The crawler is meant to die quickly once cornered.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // The blink sits above melee: when it is off cooldown and the target is in the window, it
        // fires first and hands straight back to MeleeAttackGoal.
        this.goalSelector.addGoal(1, new BlinkStrikeGoal(this,
                BLINK_MIN_DISTANCE, BLINK_MAX_DISTANCE, BLINK_COOLDOWN_TICKS));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }
}
