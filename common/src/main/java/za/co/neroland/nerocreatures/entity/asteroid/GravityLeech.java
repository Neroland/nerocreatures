package za.co.neroland.nerocreatures.entity.asteroid;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.entity.ai.shared.TetherGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.TetherState;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;

/**
 * <b>Gravity Leech</b> (Asteroid, hostile): a floating sucker disc with a ribbon tail that latches a
 * tether onto its target and bends that target's gravity until the tether breaks.
 *
 * <h2>Ability: gravity tether</h2>
 * The shared {@link TetherGoal} latches within {@value GravityTetherRules#LATCH_RANGE} blocks with line
 * of sight. While held ({@link GravityTether}) the target's {@code minecraft:gravity} is lightened to
 * 30%, or, on some latches, reversed for one second (a lift of a few blocks) and then lightened. It
 * breaks when the leech takes {@value GravityTetherRules#BREAK_DAMAGE} damage, the target gets
 * {@value GravityTetherRules#BREAK_DISTANCE} blocks away, or after
 * {@value GravityTetherRules#MAX_HOLD_TICKS} ticks; then it cools down. The modifier is transient,
 * leased and released on every end, on {@code remove} and on {@code die}. No world change, so no
 * griefing key.
 *
 * <p>The counter-play is vertical: shoot it, out-run the tether, or use the float. Drops
 * {@code graviton_node} (Graviton Boots, controlled low gravity).
 *
 * <p>Visuals: {@code ability} is <b>held</b> from the start of the wind-up: the 16-tick clip (disc
 * flares, body stretches) plays once and the model clamps on its last, stretched frame for as long as
 * the tether holds. The latch itself lands at tick {@value GravityTetherRules#LATCH_TICK} of the
 * wind-up (the telegraph). The leech then holds {@value GravityTetherRules#HOLD_DISTANCE} blocks off;
 * the tether is a particle chain drawn on the hold pulse. Back to {@code idle} on any release or an
 * aborted wind-up. Alpha tint applies.
 */
public class GravityLeech extends NeroCreatureEntity implements Enemy
        //? if <26.2 {
        /*, net.minecraft.world.entity.animal.FlyingAnimal
        *///?}
{

    private static final int BITE_COOLDOWN_TICKS = 30;
    private static final double BITE_REACH_SQR = 2.6D;

    // Created in registerGoals() (it runs from the Mob constructor, before field initialisers).
    private TetherGoal tether;

    public GravityLeech(EntityType<? extends GravityLeech> type, Level level) {
        super(type, level, CreatureTier.COMMON);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    /** Fragile and quick: the threat is the tether, not the bite. */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.SAFE_FALL_DISTANCE, 256.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        return navigation;
    }

    //? if >=26.2 {
    @Override
    protected boolean omnidirectionalAirMover() {
        return true;
    }
    //?} else {
    /*@Override
    public boolean isFlying() {
        return true;
    }
    *///?}

    @Override
    protected void registerGoals() {
        this.tether = new WindupTetherGoal(this,
                new TetherState(GravityTetherRules.BREAK_DISTANCE, GravityTetherRules.BREAK_DAMAGE,
                        GravityTetherRules.MAX_HOLD_TICKS, GravityTetherRules.COOLDOWN_TICKS),
                GravityTetherRules.LATCH_RANGE, GravityTetherRules.PULSE_TICKS, new GravityTether());
        this.goalSelector.addGoal(1, this.tether);
        this.goalSelector.addGoal(2, new HoverStrikeGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Whether the tether is latched right now. */
    public boolean isLatched() {
        return this.tether != null && this.tether.state().isLatched();
    }

    /** The latch lands at the end of the wind-up; the held {@code ability} pose is already running. */
    void onLatched() {
        this.visuals().holdPose(VisualPose.ABILITY);
    }

    void onReleased() {
        if (this.visuals().pose() == VisualPose.ABILITY) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    //? if >=1.21.2 {
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
    //?} else {
    /*@Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) this.level();
    *///?}
        if (level.getServer() != null) {
            GravityTether.sweepIfDue(level.getServer());
        }
    }

    //? if >=1.21.2 {
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    //?} else {
    /*@Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(this.level() instanceof ServerLevel level)) {
            return super.hurt(source, amount);
        }
    *///?}
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.tether != null) {
            this.tether.onTethererHurt(amount);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (this.tether != null && this.level() instanceof ServerLevel level) {
            this.tether.releaseNow(level);
        }
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.tether != null && this.level() instanceof ServerLevel level) {
            this.tether.releaseNow(level);
        }
        super.remove(reason);
    }

    // Mapped vanilla events, pitched down: a wet, hollow squelch.

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.7F;
    }

    /**
     * The shared tether with a telegraph: when it could latch, it first holds {@code ability} and only
     * latches {@value GravityTetherRules#LATCH_TICK} ticks later if it still can. Losing the target (or
     * sight, or range) during the wind-up cancels it.
     */
    private final class WindupTetherGoal extends TetherGoal {

        private long windupStart = Long.MIN_VALUE;

        WindupTetherGoal(GravityLeech leech, TetherState state, double latchRange, int pulseInterval,
                TetherEffect effect) {
            super(leech, state, latchRange, pulseInterval, effect);
        }

        @Override
        public boolean canUse() {
            GravityLeech leech = GravityLeech.this;
            if (!super.canUse()) {
                if (this.windupStart != Long.MIN_VALUE) {
                    this.windupStart = Long.MIN_VALUE;
                    leech.onReleased();
                }
                return false;
            }
            long now = leech.level().getGameTime();
            if (this.windupStart == Long.MIN_VALUE || now < this.windupStart) {
                this.windupStart = now;
                leech.visuals().holdPose(VisualPose.ABILITY);
                return false;
            }
            if (GravityTetherRules.windupDone(now - this.windupStart)) {
                this.windupStart = Long.MIN_VALUE;
                return true;
            }
            return false;
        }
    }

    /**
     * Swims through the air to its target: holds {@value GravityTetherRules#HOLD_DISTANCE} blocks off
     * while latched, closes in and bites (with a cooldown) while not.
     */
    private static final class HoverStrikeGoal extends Goal {

        private final GravityLeech leech;
        private int biteCooldown;

        HoverStrikeGoal(GravityLeech leech) {
            this.leech = leech;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.leech.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            this.leech.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.leech.getTarget();
            if (target == null || !(this.leech.level() instanceof ServerLevel level)) {
                return;
            }
            if (this.biteCooldown > 0) {
                this.biteCooldown--;
            }
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
            Vec3 offset = this.leech.position().subtract(aim);
            double keep = this.leech.isLatched() ? GravityTetherRules.HOLD_DISTANCE : 0.8D;
            Vec3 dir = offset.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : offset.normalize();
            Vec3 wanted = aim.add(dir.scale(keep));
            this.leech.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, 1.1D);
            this.leech.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (!this.leech.isLatched() && this.biteCooldown <= 0
                    && this.leech.distanceToSqr(target) <= BITE_REACH_SQR) {
                this.biteCooldown = BITE_COOLDOWN_TICKS;
                this.leech.doHurtTarget(level, target);
            }
        }
    }
}
