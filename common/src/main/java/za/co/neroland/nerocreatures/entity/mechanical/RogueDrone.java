package za.co.neroland.nerocreatures.entity.mechanical;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Rogue Drone</b> — the small, cheap half of the android family.
 *
 * <p>A drone does not fly in the pathfinding sense (a flying navigator is expensive and, on rough
 * planet terrain, unpredictable). It <b>hops and glides</b>: a telegraphed pounce (a 10-tick crouch
 * in its {@code ability} clip, then vanilla's leap impulse) closes the gap, and a permanently reduced {@code minecraft:gravity} attribute turns the
 * descent into a long float. The result reads as a hovering machine while costing exactly one
 * ordinary ground navigator — and because gravity and safe-fall are plain attributes, the whole
 * behaviour survives on all six build cells with no custom movement code at all.
 *
 * <p>When it dies it shorts out: a spark burst that nudges whatever stands right next to it (a small
 * push, never damage, never a block change).
 *
 * <p>{@link CreatureTier#LESSER} chaff: it dies fast and arrives in pairs. Drops
 * {@code salvaged_circuitry}; the {@code android_core} belongs to its bigger sibling
 * ({@link RogueAndroid}) alone.
 *
 * <p>Natural spawns are rare, in Core's {@code neroland:space/dark_biomes}. The intended source is
 * deliberate placement — see {@link AndroidSpawner}.
 */
public class RogueDrone extends AbstractAndroid {

    /** Vertical leap impulse of the pounce (vanilla {@code LeapAtTargetGoal} units). */
    private static final float LEAP_STRENGTH = 0.4F;

    /** The leap clip ({@code art/models/rogue_drone.json}): 16 ticks, launch at tick 10. */
    private static final int LEAP_CLIP_TICKS = 16;

    /** Crouch before the launch: the readable tell. */
    private static final int LEAP_TELEGRAPH_TICKS = 10;

    /** Reach of the death spark burst, in blocks. */
    private static final double BURST_RADIUS = 2.5D;

    /** Horizontal push of the death burst (a nudge, not a launch). */
    private static final double BURST_PUSH = 0.4D;

    /** Most entities one burst nudges. */
    private static final int BURST_MAX_PUSHED = 6;

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
        this.goalSelector.addGoal(2, new TelegraphedLeapGoal(this));
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
        return ModSounds.ROGUE_DRONE_AMBIENT.get();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            this.shortOut(level);
        }
    }

    /** The death spark burst: particles, a crackle, and a capped nudge away from the wreck. */
    private void shortOut(ServerLevel level) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + this.getBbHeight() * 0.5D,
                this.getZ(), 20, 0.3D, 0.3D, 0.3D, 0.2D);
        this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.6F);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(BURST_RADIUS), other -> other != this && other.isAlive());
        int pushed = 0;
        for (LivingEntity other : near) {
            if (pushed++ >= BURST_MAX_PUSHED) {
                break;
            }
            Vec3 away = new Vec3(other.getX() - this.getX(), 0.0D, other.getZ() - this.getZ());
            if (away.lengthSqr() < 1.0E-4D) {
                continue;
            }
            away = away.normalize().scale(BURST_PUSH);
            other.push(away.x, 0.15D, away.z);
            // Players move client-side: mark the motion dirty so the push reaches them.
            //? if >=26.3 {
            other.syncVelocity = true;
            //?} else {
            /*other.hurtMarked = true;
            *///?}
        }
    }

    /**
     * Vanilla's leap with a tell: the same "on the ground, 2 to 4 blocks from the target, now and
     * then" trigger, then a {@value #LEAP_TELEGRAPH_TICKS}-tick crouch in the {@code ability} clip
     * before the launch. The goal ends when the drone lands.
     */
    private static final class TelegraphedLeapGoal extends Goal {

        private final RogueDrone drone;
        private int ticks;

        TelegraphedLeapGoal(RogueDrone drone) {
            this.drone = drone;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.drone.isVehicle() || !this.drone.onGround()) {
                return false;
            }
            LivingEntity target = this.drone.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distanceSqr = this.drone.distanceToSqr(target);
            return distanceSqr >= 4.0D && distanceSqr <= 16.0D
                    && this.drone.getRandom().nextInt(reducedTickDelay(5)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.ticks <= LEAP_TELEGRAPH_TICKS || !this.drone.onGround();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.drone.getNavigation().stop();
            this.drone.visuals().playPose(VisualPose.ABILITY, LEAP_CLIP_TICKS);
            ModSounds.ROGUE_DRONE_LEAP.play(this.drone);
        }

        @Override
        public void tick() {
            LivingEntity target = this.drone.getTarget();
            if (++this.ticks < LEAP_TELEGRAPH_TICKS) {
                if (target != null) {
                    this.drone.getLookControl().setLookAt(target, 30.0F, 30.0F);
                }
                return;
            }
            if (this.ticks != LEAP_TELEGRAPH_TICKS || target == null || !this.drone.onGround()) {
                return;
            }
            Vec3 motion = this.drone.getDeltaMovement();
            Vec3 toward = new Vec3(target.getX() - this.drone.getX(), 0.0D, target.getZ() - this.drone.getZ());
            if (toward.lengthSqr() > 1.0E-7D) {
                toward = toward.normalize().scale(0.4D).add(motion.scale(0.2D));
            }
            this.drone.setDeltaMovement(toward.x, LEAP_STRENGTH, toward.z);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.ROGUE_DRONE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ROGUE_DRONE_DEATH.get();
    }
}
