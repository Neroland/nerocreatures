package za.co.neroland.nerocreatures.entity.nyxara;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.entity.nyxara.ShardbackRules.Phase;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * The Shardback's charge: wind-up (telegraph, plates fold), a locked straight run that hits at most
 * {@value ShardbackRules#MAX_HITS_PER_CHARGE} entity (its target or a player in the way), then a
 * recovery while still exposed.
 *
 * <p>Cost: {@code canUse} is a counter, a cached target read, one distance and the cached
 * line-of-sight check. While running, one small box query per tick around the Shardback only.
 */
public class ShardbackChargeGoal extends Goal {

    private final Shardback mob;
    private int cooldown = ShardbackRules.COOLDOWN_MIN_TICKS / 2;
    private int ticks;
    private int hits;
    private Vec3 direction = Vec3.ZERO;

    public ShardbackChargeGoal(Shardback mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || !this.mob.onGround()) {
            return false;
        }
        return ShardbackRules.inChargeRange(this.mob.distanceTo(target))
                && this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isAlive() && this.mob.phase() != Phase.ROAM;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.hits = 0;
        this.mob.getNavigation().stop();
        this.mob.enterPhase(ShardbackRules.next(Phase.ROAM, 0, true, false));
        this.mob.visuals().playPose(VisualPose.ABILITY, ShardbackRules.CHARGE_POSE_TICKS);
        ModSounds.SHARDBACK_CHARGE.play(this.mob);
    }

    @Override
    public void stop() {
        this.mob.enterPhase(Phase.ROAM);
        this.cooldown = ShardbackRules.cooldown(this.mob.getRandom().nextFloat());
        this.direction = Vec3.ZERO;
    }

    @Override
    public void tick() {
        this.ticks++;
        Phase phase = this.mob.phase();
        boolean stopped = false;
        switch (phase) {
            case WINDUP -> {
                LivingEntity target = this.mob.getTarget();
                if (target != null) {
                    this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    Vec3 d = new Vec3(target.getX() - this.mob.getX(), 0.0D, target.getZ() - this.mob.getZ());
                    this.direction = d.lengthSqr() < 1.0E-6 ? this.mob.getLookAngle().multiply(1.0D, 0.0D, 1.0D) : d;
                }
            }
            case RUN -> stopped = this.run();
            case RECOVER -> this.mob.getNavigation().stop();
            default -> {
            }
        }
        Phase next = ShardbackRules.next(phase, this.ticks, false, stopped);
        if (next != phase) {
            this.ticks = 0;
            this.mob.enterPhase(next);
            if (next == Phase.RUN) {
                this.direction = this.direction.lengthSqr() < 1.0E-6 ? Vec3.ZERO : this.direction.normalize();
            }
        }
    }

    /** One run tick; true when the run ends (hit, wall or nowhere to go). */
    private boolean run() {
        if (this.direction.lengthSqr() < 1.0E-6) {
            return true;
        }
        float yaw = (float) (Math.atan2(this.direction.z, this.direction.x) * (180.0D / Math.PI)) - 90.0F;
        this.mob.setYRot(yaw);
        this.mob.yBodyRot = yaw;
        this.mob.yHeadRot = yaw;
        Vec3 motion = this.mob.getDeltaMovement();
        this.mob.setDeltaMovement(this.direction.x * ShardbackRules.RUN_SPEED, motion.y,
                this.direction.z * ShardbackRules.RUN_SPEED);
        if (this.ticks > 2 && this.mob.horizontalCollision) {
            return true;
        }
        if (!(this.mob.level() instanceof ServerLevel level) || !ShardbackRules.mayHit(this.hits)) {
            return !ShardbackRules.mayHit(this.hits);
        }
        LivingEntity target = this.mob.getTarget();
        List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(0.4D),
                e -> e != this.mob && e.isAlive() && (e == target || e instanceof Player p && !p.isSpectator()));
        if (struck.isEmpty()) {
            return false;
        }
        LivingEntity hit = struck.get(0);
        this.hits++;
        if (hit.hurtServer(level, this.mob.damageSources().mobAttack(this.mob), ShardbackRules.CHARGE_DAMAGE)) {
            double kb = ShardbackRules.CHARGE_KNOCKBACK * (1.0D - hit.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            hit.push(this.direction.x * kb, 0.35D, this.direction.z * kb);
            //? if >=26.3 {
            hit.syncVelocity = true;
            //?} else {
            /*hit.hurtMarked = true;
            *///?}
        }
        this.mob.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.8F);
        return true;
    }
}
