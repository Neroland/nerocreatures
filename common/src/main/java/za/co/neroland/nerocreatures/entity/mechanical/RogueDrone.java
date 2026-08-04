package za.co.neroland.nerocreatures.entity.mechanical;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * <b>Rogue Drone</b> — the small, cheap half of the android family.
 *
 * <p>A drone does not fly in the pathfinding sense (a flying navigator is expensive and, on rough
 * planet terrain, unpredictable). It <b>hops and glides</b>: vanilla's {@code LeapAtTargetGoal}
 * provides the pounce, and a permanently reduced {@code minecraft:gravity} attribute turns the
 * descent into a long float. The result reads as a hovering machine while costing exactly one
 * ordinary ground navigator — and because gravity and safe-fall are plain attributes, the whole
 * behaviour survives on all six build cells with no custom movement code at all.
 *
 * <p>{@link CreatureTier#LESSER} chaff: it dies fast and arrives in pairs. Drops
 * {@code salvaged_circuitry}; the {@code android_core} belongs to its bigger sibling
 * ({@link RogueAndroid}) alone.
 *
 * <p>Natural spawns are rare, in Core's {@code neroland:space/dark_biomes}. The intended source is
 * deliberate placement — see {@link AndroidSpawner}.
 */
public class RogueDrone extends AbstractAndroid {

    /** Leap strength for the pounce, in the units {@code LeapAtTargetGoal} expects. */
    private static final float LEAP_STRENGTH = 0.4F;

    /** Fraction of normal gravity. Low enough to glide, high enough to still land. */
    private static final double GLIDE_GRAVITY = 0.035D;

    /** Blocks a drone may fall before taking damage — it is built to drop off things. */
    private static final double GLIDE_SAFE_FALL = 12.0D;

    public RogueDrone(EntityType<? extends RogueDrone> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    /**
     * {@link CreatureTier#LESSER}, tuned quick and fragile, with the two movement attributes that
     * make the glide: about 45% of normal gravity and a generous safe-fall distance.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.GRAVITY, GLIDE_GRAVITY)
                .add(Attributes.SAFE_FALL_DISTANCE, GLIDE_SAFE_FALL);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // The pounce outranks the walk-up: a drone closes the last few blocks through the air.
        this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, LEAP_STRENGTH));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** A rotor hum rather than a voice. */
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEE_LOOP;
    }
}
