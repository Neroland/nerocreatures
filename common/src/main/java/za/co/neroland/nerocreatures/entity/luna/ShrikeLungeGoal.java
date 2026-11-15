package za.co.neroland.nerocreatures.entity.luna;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.entity.visual.VisualPose;

/**
 * The Echo Shrike's attack: it closes on the target it heard, then a telegraphed lunge. The
 * {@code ability} pose runs {@value ShrikeRules#LUNGE_POSE_TICKS} ticks and the hit lands at tick
 * {@value ShrikeRules#LUNGE_HIT_TICK} if the target is still in reach (Art-Luna clip: hit window
 * 10-14). Re-paths every 10 ticks; no world scan. Pathing does not need sight: the shrike is blind.
 */
public class ShrikeLungeGoal extends Goal {

    private static final int REPATH_TICKS = 10;

    private final EchoShrike shrike;
    private int repath;
    private int lungeTick = -1;
    private int cooldown;

    public ShrikeLungeGoal(EchoShrike shrike) {
        this.shrike = shrike;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.shrike.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.lungeTick >= 0 || this.canUse();
    }

    @Override
    public void start() {
        this.repath = 0;
        this.lungeTick = -1;
    }

    @Override
    public void stop() {
        this.lungeTick = -1;
        this.shrike.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.shrike.getTarget();
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.lungeTick >= 0) {
            this.tickLunge(target);
            return;
        }
        if (target == null) {
            return;
        }
        this.shrike.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSqr = this.shrike.distanceToSqr(target);
        if (ShrikeRules.mayStartLunge(distSqr, this.cooldown)) {
            this.lungeTick = 0;
            this.shrike.getNavigation().stop();
            this.shrike.visuals().playPose(VisualPose.ABILITY, ShrikeRules.LUNGE_POSE_TICKS);
            return;
        }
        if (--this.repath <= 0) {
            this.repath = REPATH_TICKS;
            this.shrike.getNavigation().moveTo(target, 1.25D);
        }
    }

    private void tickLunge(LivingEntity target) {
        this.lungeTick++;
        if (this.lungeTick == ShrikeRules.LUNGE_LEAP_TICK && target != null) {
            Vec3 toward = target.position().subtract(this.shrike.position());
            Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
            if (flat.lengthSqr() > 1.0E-4D) {
                flat = flat.normalize().scale(0.45D);
                this.shrike.setDeltaMovement(flat.x, 0.25D, flat.z);
            }
        }
        if (this.lungeTick == ShrikeRules.LUNGE_HIT_TICK && target != null && target.isAlive()
                && this.shrike.level() instanceof ServerLevel level
                && ShrikeRules.inReach(this.shrike.distanceToSqr(target))) {
            this.shrike.doHurtTarget(level, target);
        }
        if (this.lungeTick >= ShrikeRules.LUNGE_POSE_TICKS) {
            this.lungeTick = -1;
            this.cooldown = ShrikeRules.LUNGE_COOLDOWN_TICKS;
        }
    }
}
